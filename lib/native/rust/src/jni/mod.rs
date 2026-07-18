#[inline(always)]
fn as_local_ref<'a>(env: &mut Env<'a>, raw_obj: *mut c_void) -> jni::errors::Result<JObject<'a>> {
    let raw = raw_obj as jobject;
    if raw.is_null() {
        return Ok(JObject::null());
    }
    // SAFETY: raw_obj is a GlobalRef-backed jobject from Go bridge callbacks.
    // Create a local ref for safe method dispatch in current thread and avoid
    // taking ownership of the global handle here.
    let borrowed = unsafe { JObject::from_raw(env, raw) };
    let local = env.new_local_ref(&borrowed)?;
    #[allow(clippy::forget_non_drop)]
    std::mem::forget(borrowed);
    Ok(local)
}
// JNI surface bound to the Kotlin `Compiler` object.
//
// The exported symbol names are part of the app's contract — see
// core/src/core/bridge/Compiler.kt. Do not rename them.

use age::secrecy::ExposeSecret;
use jni::objects::{JObject, JString, JValue};
use jni::sys::jboolean;
use jni::sys::jobject;
use jni::sys::jlong;
use jni::sys::jstring;
use jni::{Env, EnvUnowned, JavaVM, jni_sig, jni_str};
use std::ffi::{c_char, c_int, c_void, CStr, CString};
use std::mem;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::OnceLock;
use std::sync::mpsc::{self, SyncSender, TrySendError};
use std::thread;

use crate::compiler::compile_request;
use crate::compiler::{
    compile_summary_and_config_json,
    compile_inspect_tun_route_exclude_address_json,
    compile_raw_request,
    encode_inspect_error,
    inspect_compiled_group_names_from_raw,
    inspect_compiled_groups_from_raw,
    summary_error_json_string,
};
use crate::compiler::result::{compile_error_json, encode_compile_result};
use crate::model::CompileRequest;

type QueryGroupNamesFn = unsafe extern "C" fn(c_int) -> *mut c_char;
type QueryGroupFn = unsafe extern "C" fn(*const c_char, *const c_char) -> *mut c_char;
type QueryGroupsBatchFn = unsafe extern "C" fn(*const c_char, *const c_char) -> *mut c_char;
type QueryTunnelStateFn = unsafe extern "C" fn() -> *mut c_char;
type QueryNowFn = unsafe extern "C" fn(*mut u64, *mut u64);
type QueryTotalFn = unsafe extern "C" fn(*mut u64, *mut u64);
type QueryConnectionsFn = unsafe extern "C" fn() -> *mut c_char;
type QueryConnectionsOverviewFn = unsafe extern "C" fn() -> *mut c_char;
type QueryConnectionGenerationFn = unsafe extern "C" fn() -> u64;
type QueryProxyGroupVersionFn = unsafe extern "C" fn() -> u64;
type QueryRulesFn = unsafe extern "C" fn() -> *mut c_char;
type QueryProvidersFn = unsafe extern "C" fn() -> *mut c_char;
type SetRuleDisabledFn = unsafe extern "C" fn(c_int, c_int) -> c_int;
type CloseConnectionFn = unsafe extern "C" fn(*const c_char) -> c_int;
type CloseAllConnectionsFn = unsafe extern "C" fn();
type PatchTunnelModeFn = unsafe extern "C" fn(*const c_char) -> c_int;
type SubscribeCallbackFn = unsafe extern "C" fn(*mut c_void);
type UnsubscribeCallbackFn = unsafe extern "C" fn();
type SetAgeSecretKeyFn = unsafe extern "C" fn(*const c_char);
type SetCustomUserAgentFn = unsafe extern "C" fn(*const c_char);
type ConvertMrsToTextFn = unsafe extern "C" fn(*const c_char) -> *mut c_char;
type LoadCompiledRawFn = unsafe extern "C" fn(*mut c_void, *mut c_char);
type LoadCompiledRawSyncFn = unsafe extern "C" fn(*mut c_char) -> *mut c_char;
type CompleteFn = unsafe extern "C" fn(*mut c_void, *mut c_char);
type ReleaseObjectFn = unsafe extern "C" fn(*mut c_void);
type ResetFn = unsafe extern "C" fn();
type ForceGcFn = unsafe extern "C" fn();
type HealthCheckFn = unsafe extern "C" fn(*mut c_void, *const c_char);
type HealthCheckAllFn = unsafe extern "C" fn();
type StartTunFn = unsafe extern "C" fn(c_int, *const c_char, *const c_char, *const c_char, *const c_char, *mut c_void) -> c_int;
type StopTunFn = unsafe extern "C" fn();
type StartRootTunFn = unsafe extern "C" fn(*const c_char) -> *mut c_char;
type StopRootTunFn = unsafe extern "C" fn();
type StartHttpFn = unsafe extern "C" fn(*const c_char) -> *mut c_char;
type StopHttpFn = unsafe extern "C" fn();
type HealthCheckProxyFn = unsafe extern "C" fn(*mut c_void, *const c_char);
type NotifyDnsChangedFn = unsafe extern "C" fn(*const c_char);
type NotifyTimeZoneChangedFn = unsafe extern "C" fn(*const c_char, c_int);
type PatchSelectorFn = unsafe extern "C" fn(*const c_char, *const c_char) -> c_int;
type PatchForceSelectorFn = unsafe extern "C" fn(*const c_char, *const c_char) -> c_int;
type FetchAndValidFn = unsafe extern "C" fn(*mut c_void, *const c_char, *const c_char, c_int);
type UpdateProviderFn = unsafe extern "C" fn(*mut c_void, *const c_char, *const c_char);
type GenKeyPairFn = unsafe extern "C" fn() -> *mut c_char;
type VerifyKeysFn = unsafe extern "C" fn(*const c_char) -> c_int;
type ToPublicKeysFn = unsafe extern "C" fn(*const c_char) -> *mut c_char;
type CoreInitFn = unsafe extern "C" fn(*const c_char, *const c_char, *const c_char, c_int);
type QueryCoreVersionFn = unsafe extern "C" fn() -> *mut c_char;
type CompleteCallback = unsafe extern "C" fn(*mut c_void, *const c_char);
type CompleteWithStringCallback = unsafe extern "C" fn(*mut c_void, *const c_char);
type ReleaseObjectCallback = unsafe extern "C" fn(*mut c_void);
type OpenContentCallback = unsafe extern "C" fn(*const c_char, *mut c_char, c_int) -> c_int;
type SetCompleteCallbackFn = unsafe extern "C" fn(CompleteCallback);
type SetCompleteWithStringCallbackFn = unsafe extern "C" fn(CompleteWithStringCallback);
type SetReleaseObjectCallbackFn = unsafe extern "C" fn(ReleaseObjectCallback);
type SetOpenContentCallbackFn = unsafe extern "C" fn(OpenContentCallback);
type FetchReportCallback = unsafe extern "C" fn(*mut c_void, *const c_char);
type FetchCompleteCallback = unsafe extern "C" fn(*mut c_void, *const c_char);
type SetFetchReportCallbackFn = unsafe extern "C" fn(FetchReportCallback);
type SetFetchCompleteCallbackFn = unsafe extern "C" fn(FetchCompleteCallback);
type StringReceivedCallback = unsafe extern "C" fn(*mut c_void, *const c_char) -> c_int;
type TrafficPackedReceivedCallback = unsafe extern "C" fn(*mut c_void, i64, i64, i64, i64) -> c_int;
type SetLogcatReceivedCallbackFn = unsafe extern "C" fn(StringReceivedCallback);
type SetConnectionCloseReceivedCallbackFn = unsafe extern "C" fn(StringReceivedCallback);
type SetConnectionJoinReceivedCallbackFn = unsafe extern "C" fn(StringReceivedCallback);
type SetTrafficUpdateReceivedCallbackFn = unsafe extern "C" fn(StringReceivedCallback);
type SetTrafficUpdateReceivedPackedCallbackFn = unsafe extern "C" fn(TrafficPackedReceivedCallback);
type MarkSocketCallback = unsafe extern "C" fn(*mut c_void, c_int);
type QuerySocketOwnerCallback = unsafe extern "C" fn(*mut c_void, c_int, *const c_char, *const c_char) -> *mut c_char;
type SetMarkSocketCallbackFn = unsafe extern "C" fn(MarkSocketCallback);
type SetQuerySocketOwnerCallbackFn = unsafe extern "C" fn(QuerySocketOwnerCallback);

struct MihomoQuerySymbols {
    query_group_names: QueryGroupNamesFn,
    query_group: QueryGroupFn,
    query_groups_batch: QueryGroupsBatchFn,
    query_tunnel_state: QueryTunnelStateFn,
    query_now: QueryNowFn,
    query_total: QueryTotalFn,
    query_connections: QueryConnectionsFn,
    query_connections_overview: QueryConnectionsOverviewFn,
    query_connection_generation: QueryConnectionGenerationFn,
    query_proxy_group_version: QueryProxyGroupVersionFn,
    query_rules: QueryRulesFn,
    query_providers: QueryProvidersFn,
    set_rule_disabled: SetRuleDisabledFn,
    close_connection: CloseConnectionFn,
    close_all_connections: CloseAllConnectionsFn,
    patch_tunnel_mode: PatchTunnelModeFn,
    subscribe_connection_close: SubscribeCallbackFn,
    unsubscribe_connection_close: UnsubscribeCallbackFn,
    subscribe_connection_join: SubscribeCallbackFn,
    unsubscribe_connection_join: UnsubscribeCallbackFn,
    subscribe_traffic_update_packed: SubscribeCallbackFn,
    unsubscribe_traffic_update: UnsubscribeCallbackFn,
    subscribe_logcat: SubscribeCallbackFn,
    unsubscribe_logcat: UnsubscribeCallbackFn,
    set_age_secret_key: SetAgeSecretKeyFn,
    set_custom_user_agent: SetCustomUserAgentFn,
    convert_mrs_to_text: ConvertMrsToTextFn,
    reset: ResetFn,
    force_gc: ForceGcFn,
    health_check: HealthCheckFn,
    health_check_all: HealthCheckAllFn,
    start_tun: StartTunFn,
    stop_tun: StopTunFn,
    start_root_tun: StartRootTunFn,
    stop_root_tun: StopRootTunFn,
    start_http: StartHttpFn,
    stop_http: StopHttpFn,
    health_check_proxy: HealthCheckProxyFn,
    notify_dns_changed: NotifyDnsChangedFn,
    notify_timezone_changed: NotifyTimeZoneChangedFn,
    patch_selector: PatchSelectorFn,
    patch_force_selector: PatchForceSelectorFn,
    fetch_and_valid: FetchAndValidFn,
    update_provider: UpdateProviderFn,
    gen_x25519_key_pair: GenKeyPairFn,
    gen_hybrid_key_pair: GenKeyPairFn,
    verify_secret_keys: VerifyKeysFn,
    to_public_keys: ToPublicKeysFn,
    verify_public_keys: VerifyKeysFn,
    core_init: CoreInitFn,
    set_complete_callback: SetCompleteCallbackFn,
    set_complete_with_string_callback: SetCompleteWithStringCallbackFn,
    set_release_object_callback: SetReleaseObjectCallbackFn,
    set_open_content_callback: SetOpenContentCallbackFn,
    set_fetch_report_callback: SetFetchReportCallbackFn,
    set_fetch_complete_callback: SetFetchCompleteCallbackFn,
    set_logcat_received_callback: SetLogcatReceivedCallbackFn,
    set_connection_close_received_callback: SetConnectionCloseReceivedCallbackFn,
    set_connection_join_received_callback: SetConnectionJoinReceivedCallbackFn,
    set_traffic_update_received_callback: SetTrafficUpdateReceivedCallbackFn,
    set_traffic_update_received_packed_callback: SetTrafficUpdateReceivedPackedCallbackFn,
    set_mark_socket_callback: SetMarkSocketCallbackFn,
    set_query_socket_owner_callback: SetQuerySocketOwnerCallbackFn,
}

struct MihomoBridgeSymbols {
    load_compiled_raw: LoadCompiledRawFn,
    load_compiled_raw_sync: Option<LoadCompiledRawSyncFn>,
    complete: CompleteFn,
    release_object: ReleaseObjectFn,
}

#[derive(Clone, Copy)]
struct TrafficQueryFastPath {
    query_now: QueryNowFn,
    query_total: QueryTotalFn,
}

static JVM_HANDLE: OnceLock<JavaVM> = OnceLock::new();
static CALLBACK_SENDER: OnceLock<SyncSender<CallbackDispatchMessage>> = OnceLock::new();
const CALLBACK_QUEUE_CAPACITY: usize = 1024;
static CALLBACK_DROPPED_MESSAGES: AtomicU64 = AtomicU64::new(0);

enum CallbackDispatchMessage {
    DroppableStringReceived {
        callback: usize,
        payload: String,
    },
    ReliableStringReceived {
        callback: usize,
        payload: String,
    },
    FetchReport {
        callback: usize,
        status_json: String,
    },
    FetchComplete {
        callback: usize,
        error: Option<String>,
    },
    PackedTraffic {
        callback: usize,
        upload_total: i64,
        download_total: i64,
        upload_speed: i64,
        download_speed: i64,
    },
}

const RTLD_NOW: c_int = 0x00002;
const RTLD_NOLOAD: c_int = 0x00004;

#[cfg(any(target_os = "linux", target_os = "android"))]
#[link(name = "dl")]
unsafe extern "C" {
    fn dlopen(filename: *const c_char, flags: c_int) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    fn free(ptr: *mut c_void);


}

fn resolve_mihomo_query_symbols() -> Result<&'static MihomoQuerySymbols, String> {
    static SYMBOLS: OnceLock<Result<MihomoQuerySymbols, String>> = OnceLock::new();
    SYMBOLS
        .get_or_init(|| {
            let lib_name = CString::new("libmihomo.so").map_err(|_| "invalid lib name".to_string())?;
            let q_names = CString::new("queryGroupNames").map_err(|_| "invalid symbol name".to_string())?;
            let q_group = CString::new("queryGroup").map_err(|_| "invalid symbol name".to_string())?;
            let q_groups_batch = CString::new("queryGroupsBatch").map_err(|_| "invalid symbol name".to_string())?;
            let q_tunnel_state = CString::new("queryTunnelState").map_err(|_| "invalid symbol name".to_string())?;
            let q_now = CString::new("queryNow").map_err(|_| "invalid symbol name".to_string())?;
            let q_total = CString::new("queryTotal").map_err(|_| "invalid symbol name".to_string())?;
            let q_connections = CString::new("queryConnections").map_err(|_| "invalid symbol name".to_string())?;
            let q_connections_overview = CString::new("queryConnectionsOverview").map_err(|_| "invalid symbol name".to_string())?;
            let q_connection_generation = CString::new("queryConnectionGeneration").map_err(|_| "invalid symbol name".to_string())?;
            let q_proxy_group_version = CString::new("queryProxyGroupVersion").map_err(|_| "invalid symbol name".to_string())?;
            let q_rules = CString::new("queryRules").map_err(|_| "invalid symbol name".to_string())?;
            let q_providers = CString::new("queryProviders").map_err(|_| "invalid symbol name".to_string())?;
            let s_rule_disabled = CString::new("setRuleDisabled").map_err(|_| "invalid symbol name".to_string())?;
            let c_connection = CString::new("closeConnection").map_err(|_| "invalid symbol name".to_string())?;
            let c_all_connections = CString::new("closeAllConnections").map_err(|_| "invalid symbol name".to_string())?;
            let p_tunnel_mode = CString::new("patchTunnelMode").map_err(|_| "invalid symbol name".to_string())?;
            let s_connection_close = CString::new("subscribeConnectionClose").map_err(|_| "invalid symbol name".to_string())?;
            let u_connection_close = CString::new("unsubscribeConnectionClose").map_err(|_| "invalid symbol name".to_string())?;
            let s_connection_join = CString::new("subscribeConnectionJoin").map_err(|_| "invalid symbol name".to_string())?;
            let u_connection_join = CString::new("unsubscribeConnectionJoin").map_err(|_| "invalid symbol name".to_string())?;
            let s_traffic_update_packed = CString::new("subscribeTrafficUpdatePacked").map_err(|_| "invalid symbol name".to_string())?;
            let u_traffic_update = CString::new("unsubscribeTrafficUpdate").map_err(|_| "invalid symbol name".to_string())?;
            let s_logcat = CString::new("subscribeLogcat").map_err(|_| "invalid symbol name".to_string())?;
            let u_logcat = CString::new("unsubscribeLogcat").map_err(|_| "invalid symbol name".to_string())?;
            let s_age_secret_key = CString::new("setAgeSecretKey").map_err(|_| "invalid symbol name".to_string())?;
            let s_custom_user_agent = CString::new("setCustomUserAgent").map_err(|_| "invalid symbol name".to_string())?;
            let c_mrs_to_text = CString::new("convertMrsToText").map_err(|_| "invalid symbol name".to_string())?;
            let reset_name = CString::new("reset").map_err(|_| "invalid symbol name".to_string())?;
            let force_gc_name = CString::new("forceGc").map_err(|_| "invalid symbol name".to_string())?;
            let health_check_name = CString::new("healthCheck").map_err(|_| "invalid symbol name".to_string())?;
            let health_check_all_name = CString::new("healthCheckAll").map_err(|_| "invalid symbol name".to_string())?;
            let start_tun_name = CString::new("startTun").map_err(|_| "invalid symbol name".to_string())?;
            let stop_tun_name = CString::new("stopTun").map_err(|_| "invalid symbol name".to_string())?;
            let start_root_tun_name = CString::new("startRootTun").map_err(|_| "invalid symbol name".to_string())?;
            let stop_root_tun_name = CString::new("stopRootTun").map_err(|_| "invalid symbol name".to_string())?;
            let start_http_name = CString::new("startHttp").map_err(|_| "invalid symbol name".to_string())?;
            let stop_http_name = CString::new("stopHttp").map_err(|_| "invalid symbol name".to_string())?;
            let health_check_proxy_name = CString::new("healthCheckProxy").map_err(|_| "invalid symbol name".to_string())?;
            let notify_dns_changed_name = CString::new("notifyDnsChanged").map_err(|_| "invalid symbol name".to_string())?;
            let notify_timezone_changed_name = CString::new("notifyTimeZoneChanged").map_err(|_| "invalid symbol name".to_string())?;
            let patch_selector_name = CString::new("patchSelector").map_err(|_| "invalid symbol name".to_string())?;
            let patch_force_selector_name = CString::new("patchForceSelector").map_err(|_| "invalid symbol name".to_string())?;
            let fetch_and_valid_name = CString::new("fetchAndValid").map_err(|_| "invalid symbol name".to_string())?;
            let update_provider_name = CString::new("updateProvider").map_err(|_| "invalid symbol name".to_string())?;
            let gen_x25519_key_pair_name = CString::new("genX25519KeyPair").map_err(|_| "invalid symbol name".to_string())?;
            let gen_hybrid_key_pair_name = CString::new("genHybridKeyPair").map_err(|_| "invalid symbol name".to_string())?;
            let verify_secret_keys_name = CString::new("verifySecretKeys").map_err(|_| "invalid symbol name".to_string())?;
            let to_public_keys_name = CString::new("toPublicKeys").map_err(|_| "invalid symbol name".to_string())?;
            let verify_public_keys_name = CString::new("verifyPublicKeys").map_err(|_| "invalid symbol name".to_string())?;
            let core_init_name = CString::new("coreInit").map_err(|_| "invalid symbol name".to_string())?;
            let set_complete_callback_name = CString::new("set_complete_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_complete_with_string_callback_name = CString::new("set_complete_with_string_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_release_object_callback_name = CString::new("set_release_object_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_open_content_callback_name = CString::new("set_open_content_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_fetch_report_callback_name = CString::new("set_fetch_report_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_fetch_complete_callback_name = CString::new("set_fetch_complete_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_logcat_received_callback_name = CString::new("set_logcat_received_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_connection_close_received_callback_name = CString::new("set_connection_close_received_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_connection_join_received_callback_name = CString::new("set_connection_join_received_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_traffic_update_received_callback_name = CString::new("set_traffic_update_received_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_traffic_update_received_packed_callback_name = CString::new("set_traffic_update_received_packed_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_mark_socket_callback_name = CString::new("set_mark_socket_callback").map_err(|_| "invalid symbol name".to_string())?;
            let set_query_socket_owner_callback_name = CString::new("set_query_socket_owner_callback").map_err(|_| "invalid symbol name".to_string())?;

            // SAFETY: dlopen with RTLD_NOLOAD checks if libmihomo.so is already loaded; fallback to RTLD_NOW loads it.
            // Handle is never closed — symbols are used for the entire process lifetime.
            let mut handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW | RTLD_NOLOAD) };
            if handle.is_null() {
                handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW) };
            }
            if handle.is_null() {
                return Err("open libmihomo.so failed".to_string());
            }

            // SAFETY: dlsym resolves C-exported symbols. Each result is null-checked before transmute. Symbol names are valid null-terminated CStrings.
            let names_ptr = unsafe { dlsym(handle, q_names.as_ptr()) };
            if names_ptr.is_null() {
                return Err("resolve queryGroupNames failed".to_string());
            }
            let group_ptr = unsafe { dlsym(handle, q_group.as_ptr()) };
            if group_ptr.is_null() {
                return Err("resolve queryGroup failed".to_string());
            }
            let groups_batch_ptr = unsafe { dlsym(handle, q_groups_batch.as_ptr()) };
            if groups_batch_ptr.is_null() {
                return Err("resolve queryGroupsBatch failed".to_string());
            }
            let tunnel_state_ptr = unsafe { dlsym(handle, q_tunnel_state.as_ptr()) };
            if tunnel_state_ptr.is_null() {
                return Err("resolve queryTunnelState failed".to_string());
            }
            let now_ptr = unsafe { dlsym(handle, q_now.as_ptr()) };
            if now_ptr.is_null() {
                return Err("resolve queryNow failed".to_string());
            }
            let total_ptr = unsafe { dlsym(handle, q_total.as_ptr()) };
            if total_ptr.is_null() {
                return Err("resolve queryTotal failed".to_string());
            }
            let connections_ptr = unsafe { dlsym(handle, q_connections.as_ptr()) };
            if connections_ptr.is_null() {
                return Err("resolve queryConnections failed".to_string());
            }
            let connections_overview_ptr = unsafe { dlsym(handle, q_connections_overview.as_ptr()) };
            if connections_overview_ptr.is_null() {
                return Err("resolve queryConnectionsOverview failed".to_string());
            }
            let connection_generation_ptr = unsafe { dlsym(handle, q_connection_generation.as_ptr()) };
            if connection_generation_ptr.is_null() {
                return Err("resolve queryConnectionGeneration failed".to_string());
            }
            let proxy_group_version_ptr = unsafe { dlsym(handle, q_proxy_group_version.as_ptr()) };
            if proxy_group_version_ptr.is_null() {
                return Err("resolve queryProxyGroupVersion failed".to_string());
            }
            let rules_ptr = unsafe { dlsym(handle, q_rules.as_ptr()) };
            if rules_ptr.is_null() {
                return Err("resolve queryRules failed".to_string());
            }
            let providers_ptr = unsafe { dlsym(handle, q_providers.as_ptr()) };
            if providers_ptr.is_null() {
                return Err("resolve queryProviders failed".to_string());
            }
            let set_rule_disabled_ptr = unsafe { dlsym(handle, s_rule_disabled.as_ptr()) };
            if set_rule_disabled_ptr.is_null() {
                return Err("resolve setRuleDisabled failed".to_string());
            }
            let close_connection_ptr = unsafe { dlsym(handle, c_connection.as_ptr()) };
            if close_connection_ptr.is_null() {
                return Err("resolve closeConnection failed".to_string());
            }
            let close_all_connections_ptr = unsafe { dlsym(handle, c_all_connections.as_ptr()) };
            if close_all_connections_ptr.is_null() {
                return Err("resolve closeAllConnections failed".to_string());
            }
            let patch_tunnel_mode_ptr = unsafe { dlsym(handle, p_tunnel_mode.as_ptr()) };
            if patch_tunnel_mode_ptr.is_null() {
                return Err("resolve patchTunnelMode failed".to_string());
            }
            let subscribe_connection_close_ptr = unsafe { dlsym(handle, s_connection_close.as_ptr()) };
            if subscribe_connection_close_ptr.is_null() {
                return Err("resolve subscribeConnectionClose failed".to_string());
            }
            let unsubscribe_connection_close_ptr = unsafe { dlsym(handle, u_connection_close.as_ptr()) };
            if unsubscribe_connection_close_ptr.is_null() {
                return Err("resolve unsubscribeConnectionClose failed".to_string());
            }
            let subscribe_connection_join_ptr = unsafe { dlsym(handle, s_connection_join.as_ptr()) };
            if subscribe_connection_join_ptr.is_null() {
                return Err("resolve subscribeConnectionJoin failed".to_string());
            }
            let unsubscribe_connection_join_ptr = unsafe { dlsym(handle, u_connection_join.as_ptr()) };
            if unsubscribe_connection_join_ptr.is_null() {
                return Err("resolve unsubscribeConnectionJoin failed".to_string());
            }
            let subscribe_traffic_update_packed_ptr = unsafe { dlsym(handle, s_traffic_update_packed.as_ptr()) };
            if subscribe_traffic_update_packed_ptr.is_null() {
                return Err("resolve subscribeTrafficUpdatePacked failed".to_string());
            }
            let unsubscribe_traffic_update_ptr = unsafe { dlsym(handle, u_traffic_update.as_ptr()) };
            if unsubscribe_traffic_update_ptr.is_null() {
                return Err("resolve unsubscribeTrafficUpdate failed".to_string());
            }
            let subscribe_logcat_ptr = unsafe { dlsym(handle, s_logcat.as_ptr()) };
            if subscribe_logcat_ptr.is_null() {
                return Err("resolve subscribeLogcat failed".to_string());
            }
            let unsubscribe_logcat_ptr = unsafe { dlsym(handle, u_logcat.as_ptr()) };
            if unsubscribe_logcat_ptr.is_null() {
                return Err("resolve unsubscribeLogcat failed".to_string());
            }
            let set_age_secret_key_ptr = unsafe { dlsym(handle, s_age_secret_key.as_ptr()) };
            if set_age_secret_key_ptr.is_null() {
                return Err("resolve setAgeSecretKey failed".to_string());
            }
            let set_custom_user_agent_ptr = unsafe { dlsym(handle, s_custom_user_agent.as_ptr()) };
            if set_custom_user_agent_ptr.is_null() {
                return Err("resolve setCustomUserAgent failed".to_string());
            }
            let convert_mrs_to_text_ptr = unsafe { dlsym(handle, c_mrs_to_text.as_ptr()) };
            if convert_mrs_to_text_ptr.is_null() {
                return Err("resolve convertMrsToText failed".to_string());
            }
            let reset_ptr = unsafe { dlsym(handle, reset_name.as_ptr()) };
            if reset_ptr.is_null() {
                return Err("resolve reset failed".to_string());
            }
            let force_gc_ptr = unsafe { dlsym(handle, force_gc_name.as_ptr()) };
            if force_gc_ptr.is_null() {
                return Err("resolve forceGc failed".to_string());
            }
            let health_check_ptr = unsafe { dlsym(handle, health_check_name.as_ptr()) };
            if health_check_ptr.is_null() {
                return Err("resolve healthCheck failed".to_string());
            }
            let health_check_all_ptr = unsafe { dlsym(handle, health_check_all_name.as_ptr()) };
            if health_check_all_ptr.is_null() {
                return Err("resolve healthCheckAll failed".to_string());
            }
            let start_tun_ptr = unsafe { dlsym(handle, start_tun_name.as_ptr()) };
            if start_tun_ptr.is_null() {
                return Err("resolve startTun failed".to_string());
            }
            let stop_tun_ptr = unsafe { dlsym(handle, stop_tun_name.as_ptr()) };
            if stop_tun_ptr.is_null() {
                return Err("resolve stopTun failed".to_string());
            }
            let start_root_tun_ptr = unsafe { dlsym(handle, start_root_tun_name.as_ptr()) };
            if start_root_tun_ptr.is_null() {
                return Err("resolve startRootTun failed".to_string());
            }
            let stop_root_tun_ptr = unsafe { dlsym(handle, stop_root_tun_name.as_ptr()) };
            if stop_root_tun_ptr.is_null() {
                return Err("resolve stopRootTun failed".to_string());
            }
            let start_http_ptr = unsafe { dlsym(handle, start_http_name.as_ptr()) };
            if start_http_ptr.is_null() {
                return Err("resolve startHttp failed".to_string());
            }
            let stop_http_ptr = unsafe { dlsym(handle, stop_http_name.as_ptr()) };
            if stop_http_ptr.is_null() {
                return Err("resolve stopHttp failed".to_string());
            }
            let health_check_proxy_ptr = unsafe { dlsym(handle, health_check_proxy_name.as_ptr()) };
            if health_check_proxy_ptr.is_null() {
                return Err("resolve healthCheckProxy failed".to_string());
            }
            let notify_dns_changed_ptr = unsafe { dlsym(handle, notify_dns_changed_name.as_ptr()) };
            if notify_dns_changed_ptr.is_null() {
                return Err("resolve notifyDnsChanged failed".to_string());
            }
            let notify_timezone_changed_ptr = unsafe { dlsym(handle, notify_timezone_changed_name.as_ptr()) };
            if notify_timezone_changed_ptr.is_null() {
                return Err("resolve notifyTimeZoneChanged failed".to_string());
            }
            let patch_selector_ptr = unsafe { dlsym(handle, patch_selector_name.as_ptr()) };
            if patch_selector_ptr.is_null() {
                return Err("resolve patchSelector failed".to_string());
            }
            let patch_force_selector_ptr = unsafe { dlsym(handle, patch_force_selector_name.as_ptr()) };
            if patch_force_selector_ptr.is_null() {
                return Err("resolve patchForceSelector failed".to_string());
            }
            let fetch_and_valid_ptr = unsafe { dlsym(handle, fetch_and_valid_name.as_ptr()) };
            if fetch_and_valid_ptr.is_null() {
                return Err("resolve fetchAndValid failed".to_string());
            }
            let update_provider_ptr = unsafe { dlsym(handle, update_provider_name.as_ptr()) };
            if update_provider_ptr.is_null() {
                return Err("resolve updateProvider failed".to_string());
            }
            let gen_x25519_key_pair_ptr = unsafe { dlsym(handle, gen_x25519_key_pair_name.as_ptr()) };
            if gen_x25519_key_pair_ptr.is_null() {
                return Err("resolve genX25519KeyPair failed".to_string());
            }
            let gen_hybrid_key_pair_ptr = unsafe { dlsym(handle, gen_hybrid_key_pair_name.as_ptr()) };
            if gen_hybrid_key_pair_ptr.is_null() {
                return Err("resolve genHybridKeyPair failed".to_string());
            }
            let verify_secret_keys_ptr = unsafe { dlsym(handle, verify_secret_keys_name.as_ptr()) };
            if verify_secret_keys_ptr.is_null() {
                return Err("resolve verifySecretKeys failed".to_string());
            }
            let to_public_keys_ptr = unsafe { dlsym(handle, to_public_keys_name.as_ptr()) };
            if to_public_keys_ptr.is_null() {
                return Err("resolve toPublicKeys failed".to_string());
            }
            let verify_public_keys_ptr = unsafe { dlsym(handle, verify_public_keys_name.as_ptr()) };
            if verify_public_keys_ptr.is_null() {
                return Err("resolve verifyPublicKeys failed".to_string());
            }
            let core_init_ptr = unsafe { dlsym(handle, core_init_name.as_ptr()) };
            if core_init_ptr.is_null() {
                return Err("resolve coreInit failed".to_string());
            }
            let set_complete_callback_ptr = unsafe { dlsym(handle, set_complete_callback_name.as_ptr()) };
            if set_complete_callback_ptr.is_null() {
                return Err("resolve set_complete_callback failed".to_string());
            }
            let set_complete_with_string_callback_ptr = unsafe { dlsym(handle, set_complete_with_string_callback_name.as_ptr()) };
            if set_complete_with_string_callback_ptr.is_null() {
                return Err("resolve set_complete_with_string_callback failed".to_string());
            }
            let set_release_object_callback_ptr = unsafe { dlsym(handle, set_release_object_callback_name.as_ptr()) };
            if set_release_object_callback_ptr.is_null() {
                return Err("resolve set_release_object_callback failed".to_string());
            }
            let set_open_content_callback_ptr = unsafe { dlsym(handle, set_open_content_callback_name.as_ptr()) };
            if set_open_content_callback_ptr.is_null() {
                return Err("resolve set_open_content_callback failed".to_string());
            }
            let set_fetch_report_callback_ptr = unsafe { dlsym(handle, set_fetch_report_callback_name.as_ptr()) };
            if set_fetch_report_callback_ptr.is_null() {
                return Err("resolve set_fetch_report_callback failed".to_string());
            }
            let set_fetch_complete_callback_ptr = unsafe { dlsym(handle, set_fetch_complete_callback_name.as_ptr()) };
            if set_fetch_complete_callback_ptr.is_null() {
                return Err("resolve set_fetch_complete_callback failed".to_string());
            }
            let set_logcat_received_callback_ptr = unsafe { dlsym(handle, set_logcat_received_callback_name.as_ptr()) };
            if set_logcat_received_callback_ptr.is_null() {
                return Err("resolve set_logcat_received_callback failed".to_string());
            }
            let set_connection_close_received_callback_ptr = unsafe { dlsym(handle, set_connection_close_received_callback_name.as_ptr()) };
            if set_connection_close_received_callback_ptr.is_null() {
                return Err("resolve set_connection_close_received_callback failed".to_string());
            }
            let set_connection_join_received_callback_ptr = unsafe { dlsym(handle, set_connection_join_received_callback_name.as_ptr()) };
            if set_connection_join_received_callback_ptr.is_null() {
                return Err("resolve set_connection_join_received_callback failed".to_string());
            }
            let set_traffic_update_received_callback_ptr = unsafe { dlsym(handle, set_traffic_update_received_callback_name.as_ptr()) };
            if set_traffic_update_received_callback_ptr.is_null() {
                return Err("resolve set_traffic_update_received_callback failed".to_string());
            }
            let set_traffic_update_received_packed_callback_ptr = unsafe { dlsym(handle, set_traffic_update_received_packed_callback_name.as_ptr()) };
            if set_traffic_update_received_packed_callback_ptr.is_null() {
                return Err("resolve set_traffic_update_received_packed_callback failed".to_string());
            }
            let set_mark_socket_callback_ptr = unsafe { dlsym(handle, set_mark_socket_callback_name.as_ptr()) };
            if set_mark_socket_callback_ptr.is_null() {
                return Err("resolve set_mark_socket_callback failed".to_string());
            }
            let set_query_socket_owner_callback_ptr = unsafe { dlsym(handle, set_query_socket_owner_callback_name.as_ptr()) };
            if set_query_socket_owner_callback_ptr.is_null() {
                return Err("resolve set_query_socket_owner_callback failed".to_string());
            }

            // SAFETY: All `dlsym` results above have been null-checked. `mem::transmute` casts `*mut c_void` → typed `unsafe extern "C" fn(...)` pointers.
            // This is sound only because libmihomo.so (Go/cgo) exports these symbols with matching C ABI signatures.
            // A signature mismatch on the Go side would cause undefined behavior.
            // The Go function signatures are maintained in sync via the mihomo bridge header.
            let query_group_names: QueryGroupNamesFn = unsafe { mem::transmute(names_ptr) };
            let query_group: QueryGroupFn = unsafe { mem::transmute(group_ptr) };
            let query_groups_batch: QueryGroupsBatchFn = unsafe { mem::transmute(groups_batch_ptr) };
            let query_tunnel_state: QueryTunnelStateFn = unsafe { mem::transmute(tunnel_state_ptr) };
            let query_now: QueryNowFn = unsafe { mem::transmute(now_ptr) };
            let query_total: QueryTotalFn = unsafe { mem::transmute(total_ptr) };
            let query_connections: QueryConnectionsFn = unsafe { mem::transmute(connections_ptr) };
            let query_connections_overview: QueryConnectionsOverviewFn = unsafe { mem::transmute(connections_overview_ptr) };
            let query_connection_generation: QueryConnectionGenerationFn = unsafe { mem::transmute(connection_generation_ptr) };
            let query_proxy_group_version: QueryProxyGroupVersionFn = unsafe { mem::transmute(proxy_group_version_ptr) };
            let query_rules: QueryRulesFn = unsafe { mem::transmute(rules_ptr) };
            let query_providers: QueryProvidersFn = unsafe { mem::transmute(providers_ptr) };
            let set_rule_disabled: SetRuleDisabledFn = unsafe { mem::transmute(set_rule_disabled_ptr) };
            let close_connection: CloseConnectionFn = unsafe { mem::transmute(close_connection_ptr) };
            let close_all_connections: CloseAllConnectionsFn = unsafe { mem::transmute(close_all_connections_ptr) };
            let patch_tunnel_mode: PatchTunnelModeFn = unsafe { mem::transmute(patch_tunnel_mode_ptr) };
            let subscribe_connection_close: SubscribeCallbackFn = unsafe { mem::transmute(subscribe_connection_close_ptr) };
            let unsubscribe_connection_close: UnsubscribeCallbackFn = unsafe { mem::transmute(unsubscribe_connection_close_ptr) };
            let subscribe_connection_join: SubscribeCallbackFn = unsafe { mem::transmute(subscribe_connection_join_ptr) };
            let unsubscribe_connection_join: UnsubscribeCallbackFn = unsafe { mem::transmute(unsubscribe_connection_join_ptr) };
            let subscribe_traffic_update_packed: SubscribeCallbackFn = unsafe { mem::transmute(subscribe_traffic_update_packed_ptr) };
            let unsubscribe_traffic_update: UnsubscribeCallbackFn = unsafe { mem::transmute(unsubscribe_traffic_update_ptr) };
            let subscribe_logcat: SubscribeCallbackFn = unsafe { mem::transmute(subscribe_logcat_ptr) };
            let unsubscribe_logcat: UnsubscribeCallbackFn = unsafe { mem::transmute(unsubscribe_logcat_ptr) };
            let set_age_secret_key: SetAgeSecretKeyFn = unsafe { mem::transmute(set_age_secret_key_ptr) };
            let set_custom_user_agent: SetCustomUserAgentFn = unsafe { mem::transmute(set_custom_user_agent_ptr) };
            let convert_mrs_to_text: ConvertMrsToTextFn = unsafe { mem::transmute(convert_mrs_to_text_ptr) };
            let reset: ResetFn = unsafe { mem::transmute(reset_ptr) };
            let force_gc: ForceGcFn = unsafe { mem::transmute(force_gc_ptr) };
            let health_check: HealthCheckFn = unsafe { mem::transmute(health_check_ptr) };
            let health_check_all: HealthCheckAllFn = unsafe { mem::transmute(health_check_all_ptr) };
            let start_tun: StartTunFn = unsafe { mem::transmute(start_tun_ptr) };
            let stop_tun: StopTunFn = unsafe { mem::transmute(stop_tun_ptr) };
            let start_root_tun: StartRootTunFn = unsafe { mem::transmute(start_root_tun_ptr) };
            let stop_root_tun: StopRootTunFn = unsafe { mem::transmute(stop_root_tun_ptr) };
            let start_http: StartHttpFn = unsafe { mem::transmute(start_http_ptr) };
            let stop_http: StopHttpFn = unsafe { mem::transmute(stop_http_ptr) };
            let health_check_proxy: HealthCheckProxyFn = unsafe { mem::transmute(health_check_proxy_ptr) };
            let notify_dns_changed: NotifyDnsChangedFn = unsafe { mem::transmute(notify_dns_changed_ptr) };
            let notify_timezone_changed: NotifyTimeZoneChangedFn = unsafe { mem::transmute(notify_timezone_changed_ptr) };
            let patch_selector: PatchSelectorFn = unsafe { mem::transmute(patch_selector_ptr) };
            let patch_force_selector: PatchForceSelectorFn = unsafe { mem::transmute(patch_force_selector_ptr) };
            let fetch_and_valid: FetchAndValidFn = unsafe { mem::transmute(fetch_and_valid_ptr) };
            let update_provider: UpdateProviderFn = unsafe { mem::transmute(update_provider_ptr) };
            let gen_x25519_key_pair: GenKeyPairFn = unsafe { mem::transmute(gen_x25519_key_pair_ptr) };
            let gen_hybrid_key_pair: GenKeyPairFn = unsafe { mem::transmute(gen_hybrid_key_pair_ptr) };
            let verify_secret_keys: VerifyKeysFn = unsafe { mem::transmute(verify_secret_keys_ptr) };
            let to_public_keys: ToPublicKeysFn = unsafe { mem::transmute(to_public_keys_ptr) };
            let verify_public_keys: VerifyKeysFn = unsafe { mem::transmute(verify_public_keys_ptr) };
            let core_init: CoreInitFn = unsafe { mem::transmute(core_init_ptr) };
            let set_complete_callback: SetCompleteCallbackFn = unsafe { mem::transmute(set_complete_callback_ptr) };
            let set_complete_with_string_callback: SetCompleteWithStringCallbackFn = unsafe { mem::transmute(set_complete_with_string_callback_ptr) };
            let set_release_object_callback: SetReleaseObjectCallbackFn = unsafe { mem::transmute(set_release_object_callback_ptr) };
            let set_open_content_callback: SetOpenContentCallbackFn = unsafe { mem::transmute(set_open_content_callback_ptr) };
            let set_fetch_report_callback: SetFetchReportCallbackFn = unsafe { mem::transmute(set_fetch_report_callback_ptr) };
            let set_fetch_complete_callback: SetFetchCompleteCallbackFn = unsafe { mem::transmute(set_fetch_complete_callback_ptr) };
            let set_logcat_received_callback: SetLogcatReceivedCallbackFn = unsafe { mem::transmute(set_logcat_received_callback_ptr) };
            let set_connection_close_received_callback: SetConnectionCloseReceivedCallbackFn = unsafe { mem::transmute(set_connection_close_received_callback_ptr) };
            let set_connection_join_received_callback: SetConnectionJoinReceivedCallbackFn = unsafe { mem::transmute(set_connection_join_received_callback_ptr) };
            let set_traffic_update_received_callback: SetTrafficUpdateReceivedCallbackFn = unsafe { mem::transmute(set_traffic_update_received_callback_ptr) };
            let set_traffic_update_received_packed_callback: SetTrafficUpdateReceivedPackedCallbackFn = unsafe { mem::transmute(set_traffic_update_received_packed_callback_ptr) };
            let set_mark_socket_callback: SetMarkSocketCallbackFn = unsafe { mem::transmute(set_mark_socket_callback_ptr) };
            let set_query_socket_owner_callback: SetQuerySocketOwnerCallbackFn = unsafe { mem::transmute(set_query_socket_owner_callback_ptr) };

            Ok(MihomoQuerySymbols {
                query_group_names,
                query_group,
                query_groups_batch,
                query_tunnel_state,
                query_now,
                query_total,
                query_connections,
                query_connections_overview,
                query_connection_generation,
                query_proxy_group_version,
                query_rules,
                query_providers,
                set_rule_disabled,
                close_connection,
                close_all_connections,
                patch_tunnel_mode,
                subscribe_connection_close,
                unsubscribe_connection_close,
                subscribe_connection_join,
                unsubscribe_connection_join,
                subscribe_traffic_update_packed,
                unsubscribe_traffic_update,
                subscribe_logcat,
                unsubscribe_logcat,
                set_age_secret_key,
                set_custom_user_agent,
                convert_mrs_to_text,
                reset,
                force_gc,
                health_check,
                health_check_all,
                start_tun,
                stop_tun,
                start_root_tun,
                stop_root_tun,
                start_http,
                stop_http,
                health_check_proxy,
                notify_dns_changed,
                notify_timezone_changed,
                patch_selector,
                patch_force_selector,
                fetch_and_valid,
                update_provider,
                gen_x25519_key_pair,
                gen_hybrid_key_pair,
                verify_secret_keys,
                to_public_keys,
                verify_public_keys,
                core_init,
                set_complete_callback,
                set_complete_with_string_callback,
                set_release_object_callback,
                set_open_content_callback,
                set_fetch_report_callback,
                set_fetch_complete_callback,
                set_logcat_received_callback,
                set_connection_close_received_callback,
                set_connection_join_received_callback,
                set_traffic_update_received_callback,
                set_traffic_update_received_packed_callback,
                set_mark_socket_callback,
                set_query_socket_owner_callback,
            })
        })
        .as_ref()
        .map_err(Clone::clone)
}

fn resolve_query_core_version() -> Result<QueryCoreVersionFn, String> {
    static SYMBOL: OnceLock<Result<QueryCoreVersionFn, String>> = OnceLock::new();
    SYMBOL
        .get_or_init(|| {
            let lib_name = CString::new("libmihomo.so").map_err(|_| "invalid lib name".to_string())?;
            let symbol_name = CString::new("queryCoreVersion").map_err(|_| "invalid symbol name".to_string())?;

            let mut handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW | RTLD_NOLOAD) };
            if handle.is_null() {
                handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW) };
            }
            if handle.is_null() {
                return Err("open libmihomo.so failed".to_string());
            }

            let ptr = unsafe { dlsym(handle, symbol_name.as_ptr()) };
            if ptr.is_null() {
                return Err("resolve queryCoreVersion failed".to_string());
            }

            // SAFETY: queryCoreVersion is optional — may not exist in older libmihomo builds.
            // The null check ensures we only transmute a valid symbol pointer.
            // Signature: `unsafe extern "C" fn() -> *mut c_char`.
            Ok(unsafe { mem::transmute::<*mut c_void, QueryCoreVersionFn>(ptr) })
        })
        .as_ref()
        .copied()
        .map_err(Clone::clone)
}

fn to_global_callback_ptr(env: &mut Env<'_>, callback: JObject<'_>) -> Option<*mut c_void> {
    let global = env.new_global_ref(&callback).ok()?;
    let ptr = global.as_obj().as_raw().cast::<c_void>();
    // SAFETY: Go must call rust_release_object_callback to drop this GlobalRef.
    std::mem::forget(global);
    Some(ptr)
}

fn call_json_query0(env: &mut Env<'_>, f: unsafe extern "C" fn() -> *mut c_char) -> jstring {
    let raw_ptr = unsafe { f() };
    if raw_ptr.is_null() {
        return std::ptr::null_mut();
    }
    let response = unsafe { CStr::from_ptr(raw_ptr) }
        .to_string_lossy()
        .into_owned();
    unsafe { free(raw_ptr.cast()) };
    new_java_string(env, response)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInit<'local>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    home: JString<'local>,
    version_name: JString<'local>,
    sdk_version: i32,
    kernel_git_version: JString<'local>,
) {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(()),
        };
        register_rust_bridge_callbacks(symbols);

        let home_s = match home.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };
        let version_s = match version_name.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };
        let kernel_s = match kernel_git_version.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };

        let home_c = CString::new(home_s).unwrap_or_default();
        let version_c = CString::new(version_s).unwrap_or_default();
        let kernel_c = CString::new(kernel_s).unwrap_or_default();
        let git_ptr = if kernel_c.as_bytes().is_empty() {
            version_c.as_ptr()
        } else {
            kernel_c.as_ptr()
        };

        unsafe {
            (symbols.core_init)(
                home_c.as_ptr(),
                version_c.as_ptr(),
                git_ptr,
                sdk_version as c_int,
            )
        };
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCoreVersion<'local>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let version_fn = match resolve_query_core_version() {
            Ok(f) => f,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        let version_ptr = unsafe { version_fn() };
        if version_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let version = unsafe { CStr::from_ptr(version_ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { free(version_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, version))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

const GB_THRESH: u64 = 1024u64 * 1024u64 * 1024u64;
const MB_THRESH: u64 = 1024u64 * 1024u64;
const KB_THRESH: u64 = 1024u64;
const SCALE: u64 = 100u64;
const VAL_MASK: u64 = 0x3FFF_FFFFu64;

pub(crate) fn down_scale_traffic(value: u64) -> u64 {
    if value > GB_THRESH {
        ((value * SCALE / 1024u64 / 1024u64 / 1024u64) & VAL_MASK) | (3u64 << 30)
    } else if value > MB_THRESH {
        ((value * SCALE / 1024u64 / 1024u64) & VAL_MASK) | (2u64 << 30)
    } else if value > KB_THRESH {
        ((value * SCALE / 1024u64) & VAL_MASK) | (1u64 << 30)
    } else {
        value & VAL_MASK
    }
}

#[inline(always)]
pub(crate) fn pack_traffic(upload: u64, download: u64) -> jlong {
    let packed = (down_scale_traffic(upload) << 32u64) | down_scale_traffic(download);
    packed as i64
}

fn resolve_traffic_query_fast_path() -> Result<TrafficQueryFastPath, String> {
    static FAST_PATH: OnceLock<Result<TrafficQueryFastPath, String>> = OnceLock::new();
    FAST_PATH
        .get_or_init(|| {
            let symbols = resolve_mihomo_query_symbols()?;
            Ok(TrafficQueryFastPath {
                query_now: symbols.query_now,
                query_total: symbols.query_total,
            })
        })
        .as_ref()
        .copied()
        .map_err(Clone::clone)
}

fn resolve_mihomo_bridge_symbols() -> Result<&'static MihomoBridgeSymbols, String> {
    static SYMBOLS: OnceLock<Result<MihomoBridgeSymbols, String>> = OnceLock::new();
    SYMBOLS
        .get_or_init(|| {
            let lib_name = CString::new("libmihomo.so").map_err(|_| "invalid lib name".to_string())?;
            let load_name = CString::new("loadCompiledRaw").map_err(|_| "invalid symbol name".to_string())?;
            let load_sync_name = CString::new("loadCompiledRawSync").map_err(|_| "invalid symbol name".to_string())?;
            let complete_name = CString::new("complete").map_err(|_| "invalid symbol name".to_string())?;
            let release_name = CString::new("release_object").map_err(|_| "invalid symbol name".to_string())?;

            let mut handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW | RTLD_NOLOAD) };
            if handle.is_null() {
                handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW) };
            }
            if handle.is_null() {
                return Err("open libmihomo.so failed".to_string());
            }

            let load_ptr = unsafe { dlsym(handle, load_name.as_ptr()) };
            if load_ptr.is_null() {
                return Err("resolve loadCompiledRaw failed".to_string());
            }
            let load_sync_ptr = unsafe { dlsym(handle, load_sync_name.as_ptr()) };
            let complete_ptr = unsafe { dlsym(handle, complete_name.as_ptr()) };
            if complete_ptr.is_null() {
                return Err("resolve complete failed".to_string());
            }
            let release_ptr = unsafe { dlsym(handle, release_name.as_ptr()) };
            if release_ptr.is_null() {
                return Err("resolve release_object failed".to_string());
            }

            let load_compiled_raw: LoadCompiledRawFn = unsafe { mem::transmute(load_ptr) };
            let load_compiled_raw_sync: Option<LoadCompiledRawSyncFn> = if load_sync_ptr.is_null() {
                None
            } else {
                Some(unsafe { mem::transmute::<*mut c_void, LoadCompiledRawSyncFn>(load_sync_ptr) })
            };
            let complete: CompleteFn = unsafe { mem::transmute(complete_ptr) };
            let release_object: ReleaseObjectFn = unsafe { mem::transmute(release_ptr) };

            Ok(MihomoBridgeSymbols {
                load_compiled_raw,
                load_compiled_raw_sync,
                complete,
                release_object,
            })
        })
        .as_ref()
        .map_err(Clone::clone)
}

// SAFETY: libc strdup — allocates via malloc. Used for C string exchange with Go/mihomo which calls free() on the returned pointer. Linked from libc on Android/Linux.
unsafe extern "C" {
    fn strdup(s: *const c_char) -> *mut c_char;
}

#[inline(always)]
fn with_attached_env<T>(f: impl FnOnce(&mut Env<'_>) -> jni::errors::Result<T>) -> Option<T> {
    if let Some(vm) = JVM_HANDLE.get() {
        return vm.attach_current_thread(f).ok();
    }

    let vm = JavaVM::singleton().ok()?;
    let _ = JVM_HANDLE.set(vm.clone());
    vm.attach_current_thread(f).ok()
}

fn callback_sender() -> Option<&'static SyncSender<CallbackDispatchMessage>> {
    CALLBACK_SENDER.get().or_else(|| {
        let vm = JVM_HANDLE.get()?.clone();
        let (sender, receiver) = mpsc::sync_channel::<CallbackDispatchMessage>(CALLBACK_QUEUE_CAPACITY);
        thread::Builder::new()
            .name("flycat-jni-callback".to_string())
            .spawn(move || {
                let _ = vm.attach_current_thread(|env| {
                    while let Ok(message) = receiver.recv() {
                        // Each dispatch creates 2-3 local refs (callback object + JNI string). with_local_frame ensures they are freed after each message, preventing local ref table overflow (JNI default limit: 512) in long-lived threads.
                        let _ = env.with_local_frame::<_, _, jni::errors::Error>(16, |env| {
                            match message {
                                CallbackDispatchMessage::DroppableStringReceived { callback, payload }
                                | CallbackDispatchMessage::ReliableStringReceived { callback, payload } => {
                                    let _ = dispatch_string_received(env, callback as *mut c_void, &payload);
                                }
                                CallbackDispatchMessage::FetchReport { callback, status_json } => {
                                    let _ = dispatch_fetch_report(env, callback as *mut c_void, &status_json);
                                }
                                CallbackDispatchMessage::FetchComplete { callback, error } => {
                                    let _ = dispatch_fetch_complete(env, callback as *mut c_void, error.as_deref());
                                }
                                CallbackDispatchMessage::PackedTraffic {
                                    callback,
                                    upload_total,
                                    download_total,
                                    upload_speed,
                                    download_speed,
                                } => {
                                    let _ = dispatch_packed_received(
                                        env,
                                        callback as *mut c_void,
                                        upload_total,
                                        download_total,
                                        upload_speed,
                                        download_speed,
                                    );
                                }
                            }
                            Ok(())
                        });
                    }
                    Ok::<_, jni::errors::Error>(())
                });
            })
            .ok()?;
        let _ = CALLBACK_SENDER.set(sender);
        CALLBACK_SENDER.get()
    })
}

fn dispatch_fetch_report(
    env: &mut Env<'_>,
    fetch_callback: *mut c_void,
    status_json: &str,
) -> jni::errors::Result<()> {
    if fetch_callback.is_null() {
        return Ok(());
    }
    let callback_obj = as_local_ref(env, fetch_callback)?;
    let status_j = env.new_string(status_json)?;
    if env
        .call_method(
            &callback_obj,
            jni_str!("report"),
            jni_sig!("(Ljava/lang/String;)V"),
            &[JValue::Object(&status_j)],
        )
        .is_err()
    {
        clear_exception(env);
        return Ok(());
    }
    let _ = has_and_clear_exception(env);
    Ok(())
}

fn dispatch_fetch_complete(
    env: &mut Env<'_>,
    fetch_callback: *mut c_void,
    error: Option<&str>,
) -> jni::errors::Result<()> {
    if fetch_callback.is_null() {
        return Ok(());
    }
    let callback_obj = as_local_ref(env, fetch_callback)?;
    let arg = if let Some(err) = error {
        JObject::from(env.new_string(err)?)
    } else {
        JObject::null()
    };
    if env
        .call_method(
            &callback_obj,
            jni_str!("complete"),
            jni_sig!("(Ljava/lang/String;)V"),
            &[JValue::Object(&arg)],
        )
        .is_err()
    {
        clear_exception(env);
        return Ok(());
    }
    let _ = has_and_clear_exception(env);
    Ok(())
}

fn cache_java_vm(env: &mut Env<'_>) {
    if JVM_HANDLE.get().is_some() {
        return;
    }
    if let Ok(vm) = env.get_java_vm() {
        let _ = JVM_HANDLE.set(vm);
    }
}

fn rust_string_received_direct(callback: *mut c_void, payload: *const c_char) -> c_int {
    let result = with_attached_env(|env| {
        dispatch_string_received(env, callback, &ptr_to_string(payload))
    });

    result.unwrap_or(1)
}

fn dispatch_string_received(
    env: &mut Env<'_>,
    callback: *mut c_void,
    payload_text: &str,
) -> jni::errors::Result<c_int> {
    if callback.is_null() {
        return Ok(1);
    }

    let cb_obj = as_local_ref(env, callback)?;
    let payload_j = match env.new_string(payload_text) {
        Ok(v) => v,
        Err(_) => return Ok(1),
    };

    if env
        .call_method(
            &cb_obj,
            jni_str!("received"),
            jni_sig!((arg: JString) -> void),
            &[JValue::Object(&payload_j)],
        )
        .is_err()
    {
        clear_exception(env);
        return Ok(1);
    }

    if has_and_clear_exception(env) {
        return Ok(1);
    }

    Ok(0)
}

fn rust_traffic_received_packed_direct(
    callback: *mut c_void,
    upload_total: i64,
    download_total: i64,
    upload_speed: i64,
    download_speed: i64,
) -> c_int {
    let result = with_attached_env(|env| {
        dispatch_packed_received(
            env,
            callback,
            upload_total,
            download_total,
            upload_speed,
            download_speed,
        )
    });

    result.unwrap_or(1)
}

fn dispatch_packed_received(
    env: &mut Env<'_>,
    callback: *mut c_void,
    upload_total: i64,
    download_total: i64,
    upload_speed: i64,
    download_speed: i64,
) -> jni::errors::Result<c_int> {
    if callback.is_null() {
        return Ok(1);
    }

    let cb_obj = as_local_ref(env, callback)?;
    if env
        .call_method(
            &cb_obj,
            jni_str!("received"),
            jni_sig!("(JJJJ)V"),
            &[
                JValue::Long(upload_total as jlong),
                JValue::Long(download_total as jlong),
                JValue::Long(upload_speed as jlong),
                JValue::Long(download_speed as jlong),
            ],
        )
        .is_err()
    {
        clear_exception(env);
        return Ok(1);
    }

    if has_and_clear_exception(env) {
        return Ok(1);
    }

    Ok(0)
}

fn dispatch_via_callback_thread(message: CallbackDispatchMessage) -> bool {
    match callback_sender() {
        Some(sender) => match sender.try_send(message) {
            Ok(()) => true,
            Err(TrySendError::Full(message)) => match message {
                CallbackDispatchMessage::PackedTraffic { .. }
                | CallbackDispatchMessage::ReliableStringReceived { .. }
                | CallbackDispatchMessage::FetchComplete { .. } => false,
                CallbackDispatchMessage::DroppableStringReceived { .. }
                | CallbackDispatchMessage::FetchReport { .. } => {
                    CALLBACK_DROPPED_MESSAGES.fetch_add(1, Ordering::Relaxed)
                        ;true
                }
            },
            Err(TrySendError::Disconnected(_)) => false,
        },
        None => false,
    }
}

fn rust_mark_socket_direct(tun_interface: *mut c_void, fd: c_int) {
    let _ = with_attached_env(|env| {
        if tun_interface.is_null() {
            return Ok(());
        }

        let tun_obj = as_local_ref(env, tun_interface)?;
        if env
            .call_method(
                &tun_obj,
                jni_str!("markSocket"),
                jni_sig!("(I)V"),
                &[JValue::Int(fd)],
            )
            .is_err()
        {
            clear_exception(env);
            return Ok(());
        }

        let _ = has_and_clear_exception(env);
        Ok(())
    });
}

fn rust_query_socket_owner_direct(
    tun_interface: *mut c_void,
    protocol: c_int,
    source: *const c_char,
    target: *const c_char,
) -> *mut c_char {
    let result = with_attached_env(|env| {
        if tun_interface.is_null() {
            return Ok(std::ptr::null_mut());
        }

        let source_text = if source.is_null() {
            "".to_string()
        } else {
            unsafe { CStr::from_ptr(source) }.to_string_lossy().into_owned()
        };
        let target_text = if target.is_null() {
            "".to_string()
        } else {
            unsafe { CStr::from_ptr(target) }.to_string_lossy().into_owned()
        };

        let tun_obj = as_local_ref(env, tun_interface)?;
        let source_j = env.new_string(source_text)?;
        let target_j = env.new_string(target_text)?;
        let value = env
            .call_method(
                &tun_obj,
                jni_str!("querySocketOwner"),
                jni_sig!("(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
                &[
                    JValue::Int(protocol),
                    JValue::Object(&source_j),
                    JValue::Object(&target_j),
                ],
            )
            .inspect_err(|_| {
                clear_exception(env);
            })?;

        if has_and_clear_exception(env) {
            return Ok(std::ptr::null_mut());
        }

        let obj = match value.l() {
            Ok(v) => v,
            Err(_) => return Ok(std::ptr::null_mut()),
        };
        if obj.as_raw().is_null() {
            return Ok(std::ptr::null_mut());
        }

        let result_j = unsafe { JString::from_raw(env, obj.as_raw() as jstring) };
        let result_str = match result_j.try_to_string(env) {
            Ok(v) => v,
            Err(_) => {
                clear_exception(env);
                return Ok(std::ptr::null_mut());
            }
        };

        Ok(c_strdup(&result_str))
    });

    result.unwrap_or(std::ptr::null_mut())
}

fn rust_open_content_direct(url: *const c_char, error: *mut c_char, error_length: c_int) -> c_int {
    let url_text = ptr_to_string(url);
    let result = with_attached_env(|env| {
        let content_cls = match env.find_class(jni_str!("com/github/yumelira/yumebox/core/bridge/Content")) {
            Ok(v) => v,
            Err(_) => {
                copy_error_buffer(error, error_length, "content class not found");
                return Ok(-1);
            }
        };

        let url_j = env.new_string(url_text)?;
        let fd = env
            .call_static_method(
                &content_cls,
                jni_str!("open"),
                jni_sig!("(Ljava/lang/String;)I"),
                &[JValue::Object(&url_j)],
            )
            .inspect_err(|_| {
                clear_exception(env);
            })?;

        if has_and_clear_exception(env) {
            copy_error_buffer(error, error_length, "content open failed");
            return Ok(-1);
        }

        Ok(fd.i().unwrap_or(-1))
    });

    result.unwrap_or_else(|| {
        copy_error_buffer(error, error_length, "jni unavailable");
        -1
    })
}

fn rust_fetch_report_direct(fetch_callback: *mut c_void, status_json: *const c_char) {
    let _ = with_attached_env(|env| {
        if fetch_callback.is_null() {
            return Ok(());
        }
        let status = ptr_to_string(status_json);
        let callback_obj = as_local_ref(env, fetch_callback)?;
        let status_j = env.new_string(status)?;
        if env
            .call_method(
                &callback_obj,
                jni_str!("report"),
                jni_sig!("(Ljava/lang/String;)V"),
                &[JValue::Object(&status_j)],
            )
            .is_err()
        {
            clear_exception(env);
            return Ok(());
        }
        let _ = has_and_clear_exception(env);
        Ok(())
    });
}

fn rust_fetch_complete_direct(fetch_callback: *mut c_void, error: *const c_char) {
    let _ = with_attached_env(|env| {
        if fetch_callback.is_null() {
            return Ok(());
        }
        let callback_obj = as_local_ref(env, fetch_callback)?;
        let arg = if error.is_null() {
            JObject::null()
        } else {
            JObject::from(env.new_string(ptr_to_string(error))?)
        };

        if env
            .call_method(
                &callback_obj,
                jni_str!("complete"),
                jni_sig!("(Ljava/lang/String;)V"),
                &[JValue::Object(&arg)],
            )
            .is_err()
        {
            clear_exception(env);
            return Ok(());
        }

        let _ = has_and_clear_exception(env);
        Ok(())
    });
}

fn rust_release_object_direct(obj: *mut c_void) {
    let _ = with_attached_env(|env| {
        if obj.is_null() {
            return Ok(());
        }
        let global = unsafe { env.global_from_raw::<JObject>(obj as jobject) };
        drop(global);
        Ok(())
    });
}

fn rust_complete_with_string_direct(completable: *mut c_void, result: *const c_char) {
    let _ = with_attached_env(|env| {
        if completable.is_null() {
            return Ok(());
        }

        let completable_obj = as_local_ref(env, completable)?;
        let result_j = env.new_string(ptr_to_string(result))?;
        if env
            .call_method(
                &completable_obj,
                jni_str!("complete"),
                jni_sig!("(Ljava/lang/Object;)Z"),
                &[JValue::Object(&result_j)],
            )
            .is_err()
        {
            clear_exception(env);
            return Ok(());
        }

        let _ = has_and_clear_exception(env);
        Ok(())
    });
}

fn rust_complete_direct(completable: *mut c_void, exception: *const c_char) {
    let _ = with_attached_env(|env| {
        if completable.is_null() {
            return Ok(());
        }

        let completable_obj = as_local_ref(env, completable)?;
        if exception.is_null() {
            let unit_cls = match env.find_class(jni_str!("kotlin/Unit")) {
                Ok(v) => v,
                Err(_) => {
                    clear_exception(env);
                    return Ok(());
                }
            };
            let unit = match env
                .get_static_field(&unit_cls, jni_str!("INSTANCE"), jni_sig!("Lkotlin/Unit;"))
                .and_then(|v| v.l())
            {
                Ok(v) => v,
                Err(_) => {
                    clear_exception(env);
                    return Ok(());
                }
            };

            if env
                .call_method(
                    &completable_obj,
                    jni_str!("complete"),
                    jni_sig!("(Ljava/lang/Object;)Z"),
                    &[JValue::Object(&unit)],
                )
                .is_err()
            {
                clear_exception(env);
                return Ok(());
            }
        } else {
            let msg = env.new_string(ptr_to_string(exception))?;
            let ex_cls = match env.find_class(jni_str!("com/github/yumelira/yumebox/core/bridge/ClashException")) {
                Ok(v) => v,
                Err(_) => {
                    clear_exception(env);
                    return Ok(());
                }
            };
            let throwable = match env.new_object(
                &ex_cls,
                jni_sig!("(Ljava/lang/String;)V"),
                &[JValue::Object(&msg)],
            ) {
                Ok(v) => v,
                Err(_) => {
                    clear_exception(env);
                    return Ok(());
                }
            };

            if env
                .call_method(
                    &completable_obj,
                    jni_str!("completeExceptionally"),
                    jni_sig!("(Ljava/lang/Throwable;)Z"),
                    &[JValue::Object(&throwable)],
                )
                .is_err()
            {
                clear_exception(env);
                return Ok(());
            }
        }

        let _ = has_and_clear_exception(env);
        Ok(())
    });
}

fn complete_deferred_local(
    env: &mut Env<'_>,
    completable: &JObject<'_>,
    error: Option<&str>,
) -> jni::errors::Result<()> {
    if let Some(message) = error {
        let msg = env.new_string(message)?;
        let ex_cls = env.find_class(jni_str!("com/github/yumelira/yumebox/core/bridge/ClashException"))?;
        let throwable = env.new_object(
            &ex_cls,
            jni_sig!("(Ljava/lang/String;)V"),
            &[JValue::Object(&msg)],
        )?;
        let _ = env.call_method(
            completable,
            jni_str!("completeExceptionally"),
            jni_sig!("(Ljava/lang/Throwable;)Z"),
            &[JValue::Object(&throwable)],
        )?;
    } else {
        let unit_cls = env.find_class(jni_str!("kotlin/Unit"))?;
        let unit = env
            .get_static_field(&unit_cls, jni_str!("INSTANCE"), jni_sig!("Lkotlin/Unit;"))?
            .l()?;
        let _ = env.call_method(
            completable,
            jni_str!("complete"),
            jni_sig!("(Ljava/lang/Object;)Z"),
            &[JValue::Object(&unit)],
        )?;
    }
    if env.exception_check() {
        env.exception_clear();
    }
    Ok(())
}

/// Allocates a C string via libc `strdup`. The caller (Go/mihomo) must `free()` the result.
///
/// # Cross-allocator contract
/// This uses libc `strdup` (→ `malloc`), which is compatible with Go's `C.free`.
/// Go's `C.CString` also uses `malloc` under the hood, so both sides share the same allocator.
/// Do NOT replace with Rust's `Box`/`Vec` — the Go side calls `free()` on the returned pointer.
#[inline(always)]
fn c_strdup(text: &str) -> *mut c_char {
    match CString::new(text) {
        // SAFETY: `text` is valid UTF-8 with no interior NULs (checked by CString::new).
        // `strdup` allocates a copy via libc malloc; Go will call `free()` to release it.
        Ok(c) => unsafe { strdup(c.as_ptr()) },
        Err(_) => std::ptr::null_mut(),
    }
}

/// Converts a C string pointer (from Go) to an owned Rust String.
/// Returns an empty string for null pointers. Uses lossy UTF-8 conversion.
#[inline(always)]
fn ptr_to_string(ptr: *const c_char) -> String {
    if ptr.is_null() {
        String::new()
    } else {
        // SAFETY: `ptr` is a null-terminated C string from Go/mihomo. The pointer is valid for the duration of this call. Go guarantees the string outlives the callback.
        unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned()
    }
}

fn copy_error_buffer(error: *mut c_char, error_length: c_int, message: &str) {
    if error.is_null() || error_length <= 0 {
        return;
    }
    let bytes = message.as_bytes();
    let max_len = (error_length as usize).saturating_sub(1);
    let n = bytes.len().min(max_len);
    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), error.cast::<u8>(), n);
        *error.add(n) = 0;
    }
}

#[inline(always)]
fn clear_exception(env: &mut Env<'_>) {
    env.exception_clear();
}

#[inline(always)]
fn has_and_clear_exception(env: &mut Env<'_>) -> bool {
    if env.exception_check() {
        clear_exception(env);
        return true;
    }
    false
}

#[inline(always)]
fn decode_jstring(env: &mut Env<'_>, value: JString<'_>) -> Option<String> {
    match value.try_to_string(env) {
        Ok(s) => Some(s),
        Err(_) => {
            clear_exception(env);
            None
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_complete_callback(completable: *mut c_void, exception: *const c_char) {
    rust_complete_direct(completable, exception);
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_complete_with_string_callback(completable: *mut c_void, result: *const c_char) {
    rust_complete_with_string_direct(completable, result);
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_release_object_callback(obj: *mut c_void) {
    rust_release_object_direct(obj);
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_open_content_callback(url: *const c_char, error: *mut c_char, error_length: c_int) -> c_int {
    rust_open_content_direct(url, error, error_length)
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_fetch_report_callback(fetch_callback: *mut c_void, status_json: *const c_char) {
    let status = ptr_to_string(status_json);
    if !dispatch_via_callback_thread(CallbackDispatchMessage::FetchReport {
        callback: fetch_callback as usize,
        status_json: status,
    }) {
        rust_fetch_report_direct(fetch_callback, status_json);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_fetch_complete_callback(fetch_callback: *mut c_void, error: *const c_char) {
    let err = if error.is_null() { None } else { Some(ptr_to_string(error)) };
    if !dispatch_via_callback_thread(CallbackDispatchMessage::FetchComplete {
        callback: fetch_callback as usize,
        error: err,
    }) {
        rust_fetch_complete_direct(fetch_callback, error);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_logcat_received_callback(callback: *mut c_void, payload: *const c_char) -> c_int {
    let payload_text = ptr_to_string(payload);
    if dispatch_via_callback_thread(CallbackDispatchMessage::DroppableStringReceived {
        callback: callback as usize,
        payload: payload_text,
    }) {
        0
    } else {
        rust_string_received_direct(callback, payload)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_connection_close_received_callback(callback: *mut c_void, payload: *const c_char) -> c_int {
    let payload_text = ptr_to_string(payload);
    if dispatch_via_callback_thread(CallbackDispatchMessage::ReliableStringReceived {
        callback: callback as usize,
        payload: payload_text,
    }) {
        0
    } else {
        rust_string_received_direct(callback, payload)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_connection_join_received_callback(callback: *mut c_void, payload: *const c_char) -> c_int {
    let payload_text = ptr_to_string(payload);
    if dispatch_via_callback_thread(CallbackDispatchMessage::ReliableStringReceived {
        callback: callback as usize,
        payload: payload_text,
    }) {
        0
    } else {
        rust_string_received_direct(callback, payload)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_traffic_update_received_callback(callback: *mut c_void, payload: *const c_char) -> c_int {
    let payload_text = ptr_to_string(payload);
    if dispatch_via_callback_thread(CallbackDispatchMessage::DroppableStringReceived {
        callback: callback as usize,
        payload: payload_text,
    }) {
        0
    } else {
        rust_string_received_direct(callback, payload)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_traffic_update_received_packed_callback(
    callback: *mut c_void,
    upload_total: i64,
    download_total: i64,
    upload_speed: i64,
    download_speed: i64,
) -> c_int {
    if dispatch_via_callback_thread(CallbackDispatchMessage::PackedTraffic {
        callback: callback as usize,
        upload_total,
        download_total,
        upload_speed,
        download_speed,
    }) {
        0
    } else {
        rust_traffic_received_packed_direct(
            callback,
            upload_total,
            download_total,
            upload_speed,
            download_speed,
        )
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_mark_socket_callback(tun_interface: *mut c_void, fd: c_int) {
    rust_mark_socket_direct(tun_interface, fd);
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_query_socket_owner_callback(
    tun_interface: *mut c_void,
    protocol: c_int,
    source: *const c_char,
    target: *const c_char,
) -> *mut c_char {
    rust_query_socket_owner_direct(tun_interface, protocol, source, target)
}

fn register_rust_bridge_callbacks(symbols: &MihomoQuerySymbols) {
    static REGISTERED: OnceLock<()> = OnceLock::new();
    REGISTERED.get_or_init(|| unsafe {
        let _ = callback_sender();
        (symbols.set_complete_callback)(rust_complete_callback);
        (symbols.set_complete_with_string_callback)(rust_complete_with_string_callback);
        (symbols.set_release_object_callback)(rust_release_object_callback);
        (symbols.set_open_content_callback)(rust_open_content_callback);
        (symbols.set_fetch_report_callback)(rust_fetch_report_callback);
        (symbols.set_fetch_complete_callback)(rust_fetch_complete_callback);
        (symbols.set_logcat_received_callback)(rust_logcat_received_callback);
        (symbols.set_connection_close_received_callback)(rust_connection_close_received_callback);
        (symbols.set_connection_join_received_callback)(rust_connection_join_received_callback);
        (symbols.set_traffic_update_received_callback)(rust_traffic_update_received_callback);
        (symbols.set_traffic_update_received_packed_callback)(rust_traffic_update_received_packed_callback);
        (symbols.set_mark_socket_callback)(rust_mark_socket_callback);
        (symbols.set_query_socket_owner_callback)(rust_query_socket_owner_callback);
    });
}

// Age x25519 keygen. Bound to the Kotlin `Compiler` object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeGenAgeKey<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _compiler: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let identity = age::x25519::Identity::generate();
        let secret = identity.to_string().expose_secret().to_string();
        let public = identity.to_public().to_string();
        let json = serde_json::json!({ "secretKey": secret, "publicKey": public }).to_string();
        Ok::<_, jni::errors::Error>(new_java_string(env, json))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

/// Derives the age public key for a secret key, or "" when it does not parse.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeAgePublicKey<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _compiler: JObject<'local>,
    secret: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let secret_str = match secret.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(new_java_string(env, String::new()));
            }
        };
        let public = secret_str
            .trim()
            .parse::<age::x25519::Identity>()
            .map(|identity| identity.to_public().to_string())
            .unwrap_or_default();
        Ok::<_, jni::errors::Error>(new_java_string(env, public))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

// Full compile (write_output = false): returns CompileResult. Bound to the Kotlin `Compiler` object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompile<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _compiler: JObject<'local>,
    request_json: JString<'local>,
) -> jstring {
    env.with_env(|env| Ok::<_, jni::errors::Error>(handle_compile_request(env, request_json)))
        .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryGroupNames<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    exclude_not_selectable: jboolean,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };

        let raw_ptr = unsafe {
            (symbols.query_group_names)(if exclude_not_selectable { 1 } else { 0 })
        };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }

        let response = unsafe { CStr::from_ptr(raw_ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndLoadConfigSummary<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    completable: JObject<'local>,
    request_json: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        cache_java_vm(env);

        // Defensive registration: compile-and-load must always have Rust callbacks ready,
        // even if nativeInit ordering is altered by process/startup lifecycle.
        let query_symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(err) => {
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    summary_error_json_string(err),
                ));
            }
        };
        register_rust_bridge_callbacks(query_symbols);

        let bridge_symbols = match resolve_mihomo_bridge_symbols() {
            Ok(s) => s,
            Err(err) => {
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    summary_error_json_string(err),
                ));
            }
        };

        let sync_mode = bridge_symbols.load_compiled_raw_sync.is_some();
        let raw_completable = if sync_mode {
            std::ptr::null_mut()
        } else {
            let global = match env.new_global_ref(&completable) {
                Ok(value) => value,
                Err(err) => {
                    return Ok::<_, jni::errors::Error>(new_java_string(
                        env,
                        summary_error_json_string(format!("create global completable failed: {err}")),
                    ));
                }
            };
            let ptr = global.as_obj().as_raw().cast::<c_void>();
            std::mem::forget(global);
            ptr
        };

        let request_json = match request_json.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                if sync_mode {
                    let _ = complete_deferred_local(
                        env,
                        &completable,
                        Some("read compile request: invalid utf-8"),
                    );
                } else {
                    unsafe {
                        (bridge_symbols.complete)(raw_completable, c_strdup("read compile request: invalid utf-8"));
                        (bridge_symbols.release_object)(raw_completable);
                    }
                }
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    summary_error_json_string("read compile request: invalid utf-8"),
                ));
            }
        };

        let request = match serde_json::from_str::<CompileRequest>(&request_json) {
            Ok(value) => value,
            Err(err) => {
                let msg = format!("decode compile request: {err}");
                if sync_mode {
                    let _ = complete_deferred_local(env, &completable, Some(&msg));
                } else {
                    unsafe {
                        (bridge_symbols.complete)(raw_completable, c_strdup(&msg));
                        (bridge_symbols.release_object)(raw_completable);
                    }
                }
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    summary_error_json_string(msg),
                ));
            }
        };

        let (summary_json, config_raw) = match compile_summary_and_config_json(request) {
            Ok(value) => value,
            Err(err) => {
                if sync_mode {
                    let _ = complete_deferred_local(env, &completable, Some(&err));
                } else {
                    unsafe {
                        (bridge_symbols.complete)(raw_completable, c_strdup(&err));
                        (bridge_symbols.release_object)(raw_completable);
                    }
                }
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    summary_error_json_string(err),
                ));
            }
        };

        let raw_config = c_strdup(&config_raw);
        if raw_config.is_null() {
            let msg = "compile raw config allocation failed";
            if sync_mode {
                let _ = complete_deferred_local(env, &completable, Some(msg));
            } else {
                unsafe {
                    (bridge_symbols.complete)(raw_completable, c_strdup(msg));
                    (bridge_symbols.release_object)(raw_completable);
                }
            }
            return Ok::<_, jni::errors::Error>(new_java_string(
                env,
                summary_error_json_string(msg),
            ));
        }

        if let Some(load_sync) = bridge_symbols.load_compiled_raw_sync {
            let err_ptr = unsafe { load_sync(raw_config) };
            let sync_error = if err_ptr.is_null() {
                None
            } else {
                let msg = unsafe { CStr::from_ptr(err_ptr) }.to_string_lossy().into_owned();
                unsafe { free(err_ptr.cast()) };
                Some(msg)
            };

            let _ = complete_deferred_local(env, &completable, sync_error.as_deref());

            // sync_mode does not create a global callback object.
        } else {
            unsafe { (bridge_symbols.load_compiled_raw)(raw_completable, raw_config) };
        }
        Ok::<_, jni::errors::Error>(new_java_string(env, summary_json))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryTunnelState<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        let raw_ptr = unsafe { (symbols.query_tunnel_state)() };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let response = unsafe { CStr::from_ptr(raw_ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryConnections<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        Ok::<_, jni::errors::Error>(call_json_query0(env, symbols.query_connections))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryConnectionsOverview<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        Ok::<_, jni::errors::Error>(call_json_query0(env, symbols.query_connections_overview))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryConnectionGeneration<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jlong {
    env.with_env(|_env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(0),
        };
        Ok::<_, jni::errors::Error>(unsafe { (symbols.query_connection_generation)() } as jlong)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryProxyGroupVersion<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jlong {
    env.with_env(|_env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(0),
        };
        Ok::<_, jni::errors::Error>(unsafe { (symbols.query_proxy_group_version)() } as jlong)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryRules<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        Ok::<_, jni::errors::Error>(call_json_query0(env, symbols.query_rules))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryProviders<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        Ok::<_, jni::errors::Error>(call_json_query0(env, symbols.query_providers))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSetRuleDisabled<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    index: i32,
    disabled: jboolean,
) -> jboolean {
    env.with_env(|_env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let result = unsafe {
            (symbols.set_rule_disabled)(index as c_int, if disabled { 1 } else { 0 })
        };
        Ok::<_, jni::errors::Error>((result != 0) as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCloseConnection<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    id: JString<'local>,
) -> jboolean {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let id = match id.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let id_c = CString::new(id).unwrap_or_default();
        let result = unsafe { (symbols.close_connection)(id_c.as_ptr()) };
        Ok::<_, jni::errors::Error>((result != 0) as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCloseAllConnections<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        cache_java_vm(_env);

        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.close_all_connections)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativePatchTunnelMode<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    mode: JString<'local>,
) -> jboolean {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let mode = match mode.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let mode_c = CString::new(mode).unwrap_or_default();
        let result = unsafe { (symbols.patch_tunnel_mode)(mode_c.as_ptr()) };
        Ok::<_, jni::errors::Error>((result != 0) as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeConnectionClose<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    callback: JObject<'local>,
) {
    env.with_env(|env| {
        if let (Ok(symbols), Some(ptr)) = (resolve_mihomo_query_symbols(), to_global_callback_ptr(env, callback)) {
            unsafe { (symbols.subscribe_connection_close)(ptr) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeConnectionClose<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.unsubscribe_connection_close)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeConnectionJoin<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    callback: JObject<'local>,
) {
    env.with_env(|env| {
        if let (Ok(symbols), Some(ptr)) = (resolve_mihomo_query_symbols(), to_global_callback_ptr(env, callback)) {
            unsafe { (symbols.subscribe_connection_join)(ptr) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeConnectionJoin<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.unsubscribe_connection_join)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeTrafficUpdatePacked<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    callback: JObject<'local>,
) {
    env.with_env(|env| {
        if let (Ok(symbols), Some(ptr)) = (resolve_mihomo_query_symbols(), to_global_callback_ptr(env, callback)) {
            unsafe { (symbols.subscribe_traffic_update_packed)(ptr) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeTrafficUpdate<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.unsubscribe_traffic_update)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeLogcat<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    callback: JObject<'local>,
) {
    env.with_env(|env| {
        if let (Ok(symbols), Some(ptr)) = (resolve_mihomo_query_symbols(), to_global_callback_ptr(env, callback)) {
            unsafe { (symbols.subscribe_logcat)(ptr) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeLogcat<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.unsubscribe_logcat)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSetAgeSecretKey<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    key: JString<'local>,
) {
    env.with_env(|env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            if key.as_raw().is_null() {
                unsafe { (symbols.set_age_secret_key)(std::ptr::null()) };
            } else {
                match key.try_to_string(env) {
                    Ok(value) => {
                        let key_c = CString::new(value).unwrap_or_default();
                        unsafe { (symbols.set_age_secret_key)(key_c.as_ptr()) };
                    }
                    Err(_) => {
                        env.exception_clear();
                    }
                }
            }
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSetCustomUserAgent<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    user_agent: JString<'local>,
) {
    env.with_env(|env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            match user_agent.try_to_string(env) {
                Ok(value) => {
                    let ua_c = CString::new(value).unwrap_or_default();
                    unsafe { (symbols.set_custom_user_agent)(ua_c.as_ptr()) };
                }
                Err(_) => {
                    env.exception_clear();
                }
            }
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeConvertMrsToText<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    file_path: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };

        let path = match file_path.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };
        let path_c = CString::new(path).unwrap_or_default();
        let raw_ptr = unsafe { (symbols.convert_mrs_to_text)(path_c.as_ptr()) };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }

        let response = unsafe { CStr::from_ptr(raw_ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeReset<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.reset)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeForceGc<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.force_gc)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeHealthCheck<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    completable: JObject<'local>,
    name: JString<'local>,
) {
    env.with_env(|env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            let Some(completable_ptr) = to_global_callback_ptr(env, completable) else {
                return Ok::<_, jni::errors::Error>(());
            };
            let Some(name_s) = decode_jstring(env, name) else {
                return Ok::<_, jni::errors::Error>(());
            };
            let name_c = CString::new(name_s).unwrap_or_default();
            unsafe { (symbols.health_check)(completable_ptr, name_c.as_ptr()) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeHealthCheckAll<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.health_check_all)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStartTun<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    fd: i32,
    stack: JString<'local>,
    gateway: JString<'local>,
    portal: JString<'local>,
    dns: JString<'local>,
    cb: JObject<'local>,
) {
    env.with_env(|env| {
        let Ok(symbols) = resolve_mihomo_query_symbols() else {
            return Ok::<_, jni::errors::Error>(());
        };
        let Some(interface_ptr) = to_global_callback_ptr(env, cb) else {
            return Ok::<_, jni::errors::Error>(());
        };

        let Some(stack_s) = decode_jstring(env, stack) else {
            return Ok::<_, jni::errors::Error>(());
        };
        let Some(gateway_s) = decode_jstring(env, gateway) else {
            return Ok::<_, jni::errors::Error>(());
        };
        let Some(portal_s) = decode_jstring(env, portal) else {
            return Ok::<_, jni::errors::Error>(());
        };
        let Some(dns_s) = decode_jstring(env, dns) else {
            return Ok::<_, jni::errors::Error>(());
        };

        let stack_c = CString::new(stack_s).unwrap_or_default();
        let gateway_c = CString::new(gateway_s).unwrap_or_default();
        let portal_c = CString::new(portal_s).unwrap_or_default();
        let dns_c = CString::new(dns_s).unwrap_or_default();

        unsafe {
            (symbols.start_tun)(
                fd as c_int,
                stack_c.as_ptr(),
                gateway_c.as_ptr(),
                portal_c.as_ptr(),
                dns_c.as_ptr(),
                interface_ptr,
            );
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStopTun<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.stop_tun)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStartRootTun<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    config_yaml: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let Ok(symbols) = resolve_mihomo_query_symbols() else {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        };
        let Some(yaml_s) = decode_jstring(env, config_yaml) else {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        };
        let yaml_c = CString::new(yaml_s).unwrap_or_default();
        let raw_ptr = unsafe { (symbols.start_root_tun)(yaml_c.as_ptr()) };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let err = unsafe { CStr::from_ptr(raw_ptr) }.to_string_lossy().into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, err))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStopRootTun<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.stop_root_tun)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStartHttp<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    listen_at: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let Ok(symbols) = resolve_mihomo_query_symbols() else {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        };
        let listen_s = match listen_at.try_to_string(env) {
            Ok(v) => v,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };
        let listen_c = CString::new(listen_s).unwrap_or_default();
        let raw_ptr = unsafe { (symbols.start_http)(listen_c.as_ptr()) };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let listened = unsafe { CStr::from_ptr(raw_ptr) }.to_string_lossy().into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, listened))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStopHttp<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) {
    env.with_env(|_env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            unsafe { (symbols.stop_http)() };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeHealthCheckProxy<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    completable: JObject<'local>,
    proxy_name: JString<'local>,
) {
    env.with_env(|env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            let Some(completable_ptr) = to_global_callback_ptr(env, completable) else {
                return Ok::<_, jni::errors::Error>(());
            };
            let name_s = match proxy_name.try_to_string(env) {
                Ok(s) => s,
                Err(_) => {
                    env.exception_clear();
                    return Ok::<_, jni::errors::Error>(());
                }
            };
            let name_c = CString::new(name_s).unwrap_or_default();
            unsafe { (symbols.health_check_proxy)(completable_ptr, name_c.as_ptr()) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeNotifyDnsChanged<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    dns_list: JString<'local>,
) {
    env.with_env(|env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            let dns_list_s = match dns_list.try_to_string(env) {
                Ok(s) => s,
                Err(_) => {
                    env.exception_clear();
                    return Ok::<_, jni::errors::Error>(());
                }
            };
            let dns_list_c = CString::new(dns_list_s).unwrap_or_default();
            unsafe { (symbols.notify_dns_changed)(dns_list_c.as_ptr()) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeNotifyTimeZoneChanged<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    name: JString<'local>,
    offset: i32,
) {
    env.with_env(|env| {
        if let Ok(symbols) = resolve_mihomo_query_symbols() {
            let name_s = match name.try_to_string(env) {
                Ok(s) => s,
                Err(_) => {
                    env.exception_clear();
                    return Ok::<_, jni::errors::Error>(());
                }
            };
            let name_c = CString::new(name_s).unwrap_or_default();
            unsafe { (symbols.notify_timezone_changed)(name_c.as_ptr(), offset as c_int) };
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativePatchSelector<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    selector: JString<'local>,
    name: JString<'local>,
) -> jboolean {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let selector_s = match selector.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let name_s = match name.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let selector_c = CString::new(selector_s).unwrap_or_default();
        let name_c = CString::new(name_s).unwrap_or_default();
        let ok = unsafe { (symbols.patch_selector)(selector_c.as_ptr(), name_c.as_ptr()) != 0 };
        Ok::<_, jni::errors::Error>(ok as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeForcePatchSelector<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    selector: JString<'local>,
    name: JString<'local>,
) -> jboolean {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let selector_s = match selector.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let name_s = match name.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let selector_c = CString::new(selector_s).unwrap_or_default();
        let name_c = CString::new(name_s).unwrap_or_default();
        let ok = unsafe { (symbols.patch_force_selector)(selector_c.as_ptr(), name_c.as_ptr()) != 0 };
        Ok::<_, jni::errors::Error>(ok as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeFetchAndValid<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    callback: JObject<'local>,
    path: JString<'local>,
    url: JString<'local>,
    force: jboolean,
) {
    env.with_env(|env| {
        let Ok(symbols) = resolve_mihomo_query_symbols() else {
            return Ok::<_, jni::errors::Error>(());
        };
        let Some(callback_ptr) = to_global_callback_ptr(env, callback) else {
            return Ok::<_, jni::errors::Error>(());
        };
        let path_s = match path.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };
        let url_s = match url.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };
        let path_c = CString::new(path_s).unwrap_or_default();
        let url_c = CString::new(url_s).unwrap_or_default();
        unsafe {
            (symbols.fetch_and_valid)(
                callback_ptr,
                path_c.as_ptr(),
                url_c.as_ptr(),
                if force { 1 } else { 0 },
            );
        }
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUpdateProvider<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    completable: JObject<'local>,
    p_type: JString<'local>,
    name: JString<'local>,
) {
    env.with_env(|env| {
        let Ok(symbols) = resolve_mihomo_query_symbols() else {
            return Ok::<_, jni::errors::Error>(());
        };
        let Some(completable_ptr) = to_global_callback_ptr(env, completable) else {
            return Ok::<_, jni::errors::Error>(());
        };
        let p_type_s = match p_type.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };
        let name_s = match name.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(());
            }
        };
        let p_type_c = CString::new(p_type_s).unwrap_or_default();
        let name_c = CString::new(name_s).unwrap_or_default();
        unsafe { (symbols.update_provider)(completable_ptr, p_type_c.as_ptr(), name_c.as_ptr()) };
        Ok::<_, jni::errors::Error>(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeGenX25519KeyPair<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        let raw_ptr = unsafe { (symbols.gen_x25519_key_pair)() };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let response = unsafe { CStr::from_ptr(raw_ptr) }.to_string_lossy().into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeGenHybridKeyPair<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        let raw_ptr = unsafe { (symbols.gen_hybrid_key_pair)() };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let response = unsafe { CStr::from_ptr(raw_ptr) }.to_string_lossy().into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeVerifySecretKeys<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    secret_keys: JString<'local>,
) -> jboolean {
    env.with_env(|env| {
        if secret_keys.as_raw().is_null() {
            return Ok::<_, jni::errors::Error>(false as jboolean);
        }
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let secret_keys_s = match secret_keys.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let secret_keys_c = CString::new(secret_keys_s).unwrap_or_default();
        let ok = unsafe { (symbols.verify_secret_keys)(secret_keys_c.as_ptr()) != 0 };
        Ok::<_, jni::errors::Error>(ok as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeToPublicKeys<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    secret_keys: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        if secret_keys.as_raw().is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };
        let secret_keys_s = match secret_keys.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };
        let secret_keys_c = CString::new(secret_keys_s).unwrap_or_default();
        let raw_ptr = unsafe { (symbols.to_public_keys)(secret_keys_c.as_ptr()) };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }
        let response = unsafe { CStr::from_ptr(raw_ptr) }.to_string_lossy().into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeVerifyPublicKeys<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    public_keys: JString<'local>,
) -> jboolean {
    env.with_env(|env| {
        if public_keys.as_raw().is_null() {
            return Ok::<_, jni::errors::Error>(false as jboolean);
        }
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(false as jboolean),
        };
        let public_keys_s = match public_keys.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(false as jboolean);
            }
        };
        let public_keys_c = CString::new(public_keys_s).unwrap_or_default();
        let ok = unsafe { (symbols.verify_public_keys)(public_keys_c.as_ptr()) != 0 };
        Ok::<_, jni::errors::Error>(ok as jboolean)
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryTrafficNow<
    'local,
>(
    _env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jlong {
    let query_now = match resolve_traffic_query_fast_path() {
        Ok(path) => path.query_now,
        Err(_) => return 0i64,
    };
    let mut upload = 0u64;
    let mut download = 0u64;
    unsafe { query_now(&mut upload as *mut u64, &mut download as *mut u64) };
    pack_traffic(upload, download)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryTrafficTotal<
    'local,
>(
    _env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
) -> jlong {
    let query_total = match resolve_traffic_query_fast_path() {
        Ok(path) => path.query_total,
        Err(_) => return 0i64,
    };
    let mut upload = 0u64;
    let mut download = 0u64;
    unsafe { query_total(&mut upload as *mut u64, &mut download as *mut u64) };
    pack_traffic(upload, download)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryGroup<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    name: JString<'local>,
    mode: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };

        let name_s = match name.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };
        let mode_s = match mode.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };

        let name_c = CString::new(name_s).unwrap_or_default();
        let mode_c = CString::new(mode_s).unwrap_or_default();
        let raw_ptr = unsafe {
            (symbols.query_group)(name_c.as_ptr(), mode_c.as_ptr())
        };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }

        let response = unsafe { CStr::from_ptr(raw_ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryGroupsBatch<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    names_json: JString<'local>,
    mode: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let symbols = match resolve_mihomo_query_symbols() {
            Ok(s) => s,
            Err(_) => return Ok::<_, jni::errors::Error>(std::ptr::null_mut()),
        };

        let names_s = match names_json.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };
        let mode_s = match mode.try_to_string(env) {
            Ok(s) => s,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };

        let names_c = CString::new(names_s).unwrap_or_default();
        let mode_c = CString::new(mode_s).unwrap_or_default();
        let raw_ptr = unsafe {
            (symbols.query_groups_batch)(names_c.as_ptr(), mode_c.as_ptr())
        };
        if raw_ptr.is_null() {
            return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
        }

        let response = unsafe { CStr::from_ptr(raw_ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { free(raw_ptr.cast()) };
        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInspectCompiledGroupNames<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    config_raw_json: JString<'local>,
    exclude_not_selectable: jboolean,
) -> jstring {
    env.with_env(|env| {
        let config_raw = match config_raw_json.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };

        let response = inspect_compiled_group_names_from_raw(
            &config_raw,
            exclude_not_selectable,
        );

        Ok::<_, jni::errors::Error>(match response {
            Some(value) => new_java_string(env, value),
            None => std::ptr::null_mut(),
        })
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInspectCompiledGroups<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    config_raw_json: JString<'local>,
    profile_dir: JString<'local>,
    exclude_not_selectable: jboolean,
) -> jstring {
    env.with_env(|env| {
        let config_raw = match config_raw_json.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };
        let profile_dir = match profile_dir.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(std::ptr::null_mut());
            }
        };

        let response = inspect_compiled_groups_from_raw(
            &config_raw,
            &profile_dir,
            exclude_not_selectable,
        );

        Ok::<_, jni::errors::Error>(match response {
            Some(value) => new_java_string(env, value),
            None => std::ptr::null_mut(),
        })
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndInspectGroups<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    request_json: JString<'local>,
    profile_dir: JString<'local>,
    exclude_not_selectable: jboolean,
) -> jstring {
    env.with_env(|env| {
        let request_json = match request_json.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    encode_inspect_error("read compile request: invalid utf-8"),
                ));
            }
        };
        let profile_dir = match profile_dir.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    encode_inspect_error("read profile dir: invalid utf-8"),
                ));
            }
        };

        let request = match serde_json::from_str::<CompileRequest>(&request_json) {
            Ok(value) => value,
            Err(err) => {
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    encode_inspect_error(format!("decode compile request: {err}")),
                ));
            }
        };

        let compiled = match compile_raw_request(request) {
            Ok(value) => value,
            Err(err) => {
                return Ok::<_, jni::errors::Error>(new_java_string(env, encode_inspect_error(err)));
            }
        };

        let response = inspect_compiled_groups_from_raw(
            &compiled.config_raw,
            &profile_dir,
            exclude_not_selectable,
        )
        .unwrap_or_else(|| encode_inspect_error("inspect compiled groups failed"));

        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndInspectTunRouteExcludeAddress<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _bridge: JObject<'local>,
    request_json: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let request_json = match request_json.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    encode_inspect_error("read compile request: invalid utf-8"),
                ));
            }
        };

        let request = match serde_json::from_str::<CompileRequest>(&request_json) {
            Ok(value) => value,
            Err(err) => {
                return Ok::<_, jni::errors::Error>(new_java_string(
                    env,
                    encode_inspect_error(format!("decode compile request: {err}")),
                ));
            }
        };

        let response = match compile_inspect_tun_route_exclude_address_json(request) {
            Ok(value) => value,
            Err(err) => encode_inspect_error(err),
        };

        Ok::<_, jni::errors::Error>(new_java_string(env, response))
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

fn handle_compile_request(env: &mut Env, request_json: JString) -> jstring {
    let payload = match request_json.try_to_string(env) {
        Ok(value) => value,
        Err(err) => {
            env.exception_clear();
            return new_java_string(env, compile_error_json(format!("read JNI request: {err}")));
        }
    };

    let result = match serde_json::from_str::<CompileRequest>(&payload) {
        Ok(request) => compile_request(request, false),
        Err(err) => Err(format!("decode override request: {err}")),
    };

    let response_json = match result {
        Ok(result) => encode_compile_result(result),
        Err(err) => compile_error_json(err),
    };
    new_java_string(env, response_json)
}

fn new_java_string(env: &mut Env, content: String) -> jstring {
    match env.new_string(content) {
        Ok(s) => s.into_raw(),
        Err(_) => {
            // JNI ref table exhaustion or OOM — return null to avoid process abort.
            // Callers receive a null jstring which propagates as null to Kotlin.
            env.exception_clear();
            std::ptr::null_mut()
        }
    }
}
