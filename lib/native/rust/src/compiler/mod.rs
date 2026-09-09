pub mod normalize;
pub mod patch;
pub mod result;
pub mod schema;

use serde_json::{Map as JsonMap, Value as JsonValue};
use sha2::{Digest, Sha256};
use std::fs;
use std::path::Path;
use std::sync::Mutex;

use crate::engine;
use crate::io::{load_overrides, write_atomic};
use crate::model::{CompileRawResult, CompileRequest, CompileResult, RunMode, REQUEST_SCHEMA_VERSION};

struct CompiledRoot {
    root: JsonValue,
    warnings: Vec<String>,
}

// Boa uses interior mutability while constructing and collecting a JS realm. Compiles can be
// requested concurrently by start/reload, config preview, and group inspection; serialize the
// full transaction so separate JNI/ABI callers never enter the engine at the same time.
static COMPILER_LOCK: Mutex<()> = Mutex::new(());

fn with_compile_lock<T>(compile: impl FnOnce() -> Result<T, String>) -> Result<T, String> {
    // A caught panic poisons std::sync::Mutex. The failed context is dropped during unwinding, so
    // later compiles can safely acquire the lock and report their own result.
    let _guard = COMPILER_LOCK
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    compile()
}

pub fn compile_request(
    request: CompileRequest,
    write_output: bool,
) -> Result<CompileResult, String> {
    with_compile_lock(|| compile_request_unlocked(request, write_output))
}

fn compile_request_unlocked(
    request: CompileRequest,
    write_output: bool,
) -> Result<CompileResult, String> {
    if request_source_is_age_encrypted(&request)? {
        return Err(
            "encrypted profiles must use native compile raw output; YAML output is disabled"
                .to_string(),
        );
    }

    let compiled = compile_root(&request)?;

    let final_yaml = serde_yaml::to_string(&normalize::normalize_root(compiled.root))
        .map_err(|err| format!("encode final yaml: {err}"))?;
    let fingerprint = fingerprint_for(request.profile_uuid.as_bytes(), final_yaml.as_bytes());

    if write_output {
        let output_path = request.output_path.trim();
        if output_path.is_empty() {
            return Err("compile mode requires outputPath".to_string());
        }
        write_atomic(Path::new(&output_path), final_yaml.as_bytes())
            .map_err(|err| format!("write runtime yaml: {err}"))?;
    }

    Ok(CompileResult {
        success: true,
        fingerprint,
        final_yaml,
        warnings: compiled.warnings,
        error: None,
    })
}

pub fn compile_raw_request(request: CompileRequest) -> Result<CompileRawResult, String> {
    with_compile_lock(|| compile_raw_request_unlocked(request))
}

fn compile_raw_request_unlocked(request: CompileRequest) -> Result<CompileRawResult, String> {
    let compiled = compile_root(&request)?;
    let config_raw = serde_json::to_string(&compiled.root)
        .map_err(|err| format!("encode raw config json: {err}"))?;
    let fingerprint = fingerprint_for(request.profile_uuid.as_bytes(), config_raw.as_bytes());
    Ok(CompileRawResult {
        success: true,
        fingerprint,
        config_raw,
        warnings: compiled.warnings,
        error: None,
    })
}

fn compile_root(request: &CompileRequest) -> Result<CompiledRoot, String> {
    if request.schema_version != REQUEST_SCHEMA_VERSION {
        return Err(format!(
            "unsupported schema version: {}",
            request.schema_version
        ));
    }

    let (source_yaml, encrypted) = load_source_yaml(request)?;
    let mut root: JsonValue = engine::yaml::yaml_to_json(&source_yaml)
        .map_err(|err| format!("parse source yaml: {err}"))?;
    drop(source_yaml);

    let loaded_overrides = load_overrides(&request.overrides, encrypted)?;
    let mut warnings = loaded_overrides.warnings;
    let apply_result = engine::apply_overrides(root, &loaded_overrides.items, encrypted)?;
    root = apply_result.root;
    warnings.extend(apply_result.warnings);

    let profile_dir = Path::new(&request.profile_dir);
    if !root.is_object() {
        return Err("compiled root config must be an object".to_string());
    }
    // Native eBPF is profile-owned: never inject the VPN/Tun runtime patch set.
    let apply_runtime_patches = !request.skip_runtime_patches && request.run_mode != RunMode::Ebpf;
    if apply_runtime_patches {
        patch::patch_static_runtime(&mut root, profile_dir, request.run_mode);
    }
    // Native eBPF and mihomo Tun cannot attach at the same time.
    if request.run_mode == RunMode::Ebpf {
        patch::disable_ebpf_tun_entrypoint(&mut root);
    }
    // Preview: strip every traffic-facing entry point for inspect-only cores.
    if request.preview {
        patch::patch_preview_runtime(&mut root);
    }

    let object = root
        .as_object_mut()
        .ok_or_else(|| "compiled root config must be an object".to_string())?;
    validate_root_config(object)?;
    if apply_runtime_patches {
        patch::validate_provider_paths(object, profile_dir)?;
    }

    Ok(CompiledRoot { root, warnings })
}

fn load_source_yaml(request: &CompileRequest) -> Result<(String, bool), String> {
    let source_bytes =
        fs::read(&request.profile_path).map_err(|err| format!("read profile yaml: {err}"))?;
    let encrypted = is_age_encrypted(&source_bytes);
    let plaintext = if encrypted {
        decrypt_age_source(&source_bytes, request.age_secret_key.as_deref())?
    } else {
        source_bytes
    };
    let source_yaml =
        String::from_utf8(plaintext).map_err(|err| format!("source yaml is not utf-8: {err}"))?;
    Ok((source_yaml, encrypted))
}

fn request_source_is_age_encrypted(request: &CompileRequest) -> Result<bool, String> {
    let source_bytes =
        fs::read(&request.profile_path).map_err(|err| format!("read profile yaml: {err}"))?;
    Ok(is_age_encrypted(&source_bytes))
}

fn is_age_encrypted(bytes: &[u8]) -> bool {
    bytes.starts_with(b"age-encryption.org/v1")
        || bytes.starts_with(b"-----BEGIN AGE ENCRYPTED FILE-----")
}

fn decrypt_age_source(ciphertext: &[u8], secret_key: Option<&str>) -> Result<Vec<u8>, String> {
    let secret_key = secret_key
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "age encrypted profile requires ageSecretKey".to_string())?;
    let identities = parse_age_identities(secret_key)?;
    let mut last_error = String::new();
    for identity in identities {
        match age::decrypt(&identity, ciphertext) {
            Ok(plaintext) => return Ok(plaintext),
            Err(err) => last_error = err.to_string(),
        }
    }
    if last_error.is_empty() {
        last_error = "no matching age identity".to_string();
    }
    Err(format!("decrypt age profile: {last_error}"))
}

fn parse_age_identities(secret_keys: &str) -> Result<Vec<age::x25519::Identity>, String> {
    let mut identities = Vec::new();
    for (index, raw_line) in secret_keys.lines().enumerate() {
        let line = raw_line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if line.starts_with("AGE-SECRET-KEY-PQ-1") {
            return Err(
                "hybrid age secret keys are not supported by the Rust override decryptor yet"
                    .to_string(),
            );
        }
        let identity = line
            .parse::<age::x25519::Identity>()
            .map_err(|err| format!("parse age secret key at line {}: {err}", index + 1))?;
        identities.push(identity);
    }
    if identities.is_empty() {
        return Err("no supported age secret keys found".to_string());
    }
    Ok(identities)
}

fn fingerprint_for(profile_uuid: &[u8], payload: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(profile_uuid);
    hasher.update(payload);
    hex_lower(&hasher.finalize())
}

const HEX_DIGITS: &[u8; 16] = b"0123456789abcdef";

/// Lowercase hex, without the per-byte `format!` allocation.
pub(crate) fn hex_lower(bytes: &[u8]) -> String {
    let mut encoded = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        encoded.push(HEX_DIGITS[usize::from(byte >> 4)] as char);
        encoded.push(HEX_DIGITS[usize::from(byte & 0x0f)] as char);
    }
    encoded
}

fn validate_root_config(object: &JsonMap<String, JsonValue>) -> Result<(), String> {
    validate_geosite_matcher(object)?;
    Ok(())
}

fn validate_geosite_matcher(object: &JsonMap<String, JsonValue>) -> Result<(), String> {
    let Some(value) = object.get("geosite-matcher") else {
        return Ok(());
    };
    let Some(value) = value.as_str() else {
        return Err(
            "geosite-matcher must be a string (supported values: mph, succinct)".to_string(),
        );
    };
    if matches!(value, "mph" | "succinct") {
        return Ok(());
    }
    Err(format!(
        "geosite-matcher must be one of: mph, succinct (got {value})"
    ))
}

// C ABI exports for cross-library calls from C++ bridge
use std::ffi::{c_char, CStr, CString};
#[cfg(any(target_os = "linux", target_os = "android"))]
use std::os::raw::c_int;
#[cfg(any(target_os = "linux", target_os = "android"))]
use std::mem;
#[cfg(any(target_os = "linux", target_os = "android"))]
use std::os::raw::c_void;
#[cfg(any(target_os = "linux", target_os = "android"))]
use std::sync::OnceLock;

use crate::compiler::result::{compile_raw_error_json};

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct CompileRawSummary {
    success: bool,
    fingerprint: String,
    warnings: Vec<String>,
    error: Option<String>,
    tun_include_package: Vec<String>,
    tun_exclude_package: Vec<String>,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct NativeInspectResult {
    success: bool,
    payload: String,
    error: Option<String>,
}

#[cfg(any(target_os = "linux", target_os = "android"))]
type InspectCompiledGroupsResultFn = unsafe extern "C" fn(*const c_char, *const c_char, c_int) -> *mut c_char;
#[cfg(any(target_os = "linux", target_os = "android"))]
type InspectCompiledGroupNamesFn = unsafe extern "C" fn(*const c_char, c_int) -> *mut c_char;

#[cfg(any(target_os = "linux", target_os = "android"))]
struct MihomoInspectSymbols {
    inspect_compiled_groups_result: InspectCompiledGroupsResultFn,
    inspect_compiled_group_names: InspectCompiledGroupNamesFn,
}

#[cfg(any(target_os = "linux", target_os = "android"))]
const RTLD_NOW: c_int = 0x00002;
#[cfg(any(target_os = "linux", target_os = "android"))]
const RTLD_NOLOAD: c_int = 0x00004;

#[cfg(any(target_os = "linux", target_os = "android"))]
#[link(name = "dl")]
unsafe extern "C" {
    fn dlopen(filename: *const c_char, flags: c_int) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    fn free(ptr: *mut c_void);
}

#[cfg(any(target_os = "linux", target_os = "android"))]
fn resolve_mihomo_inspect_symbols() -> Result<&'static MihomoInspectSymbols, String> {
    static SYMBOLS: OnceLock<Result<MihomoInspectSymbols, String>> = OnceLock::new();
    SYMBOLS
        .get_or_init(|| {
            let lib_name = CString::new("libmihomo.so").map_err(|_| "invalid lib name".to_string())?;
            let symbol_name = CString::new("inspectCompiledGroupsResult")
                .map_err(|_| "invalid symbol name".to_string())?;
            let symbol_group_names = CString::new("inspectCompiledGroupNames")
                .map_err(|_| "invalid symbol name".to_string())?;

            // Try NOLOAD first (already loaded by runtime), then fallback to normal open.
            let mut handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW | RTLD_NOLOAD) };
            if handle.is_null() {
                handle = unsafe { dlopen(lib_name.as_ptr(), RTLD_NOW) };
            }
            if handle.is_null() {
                return Err("open libmihomo.so failed".to_string());
            }

            let fn_ptr = unsafe { dlsym(handle, symbol_name.as_ptr()) };
            if fn_ptr.is_null() {
                return Err("resolve inspectCompiledGroupsResult failed".to_string());
            }
            let fn_names_ptr = unsafe { dlsym(handle, symbol_group_names.as_ptr()) };
            if fn_names_ptr.is_null() {
                return Err("resolve inspectCompiledGroupNames failed".to_string());
            }

            let inspect_compiled_groups_result: InspectCompiledGroupsResultFn = unsafe { mem::transmute(fn_ptr) };
            let inspect_compiled_group_names: InspectCompiledGroupNamesFn = unsafe { mem::transmute(fn_names_ptr) };

            Ok(MihomoInspectSymbols {
                inspect_compiled_groups_result,
                inspect_compiled_group_names,
            })
        })
        .as_ref()
        .map_err(Clone::clone)
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub(crate) fn inspect_compiled_group_names_from_raw(
    config_raw_json: &str,
    exclude_not_selectable: bool,
) -> Option<String> {
    let symbols = resolve_mihomo_inspect_symbols().ok()?;
    let config_raw = CString::new(config_raw_json).ok()?;
    let raw_ptr = unsafe {
        (symbols.inspect_compiled_group_names)(
            config_raw.as_ptr(),
            if exclude_not_selectable { 1 } else { 0 },
        )
    };
    if raw_ptr.is_null() {
        return None;
    }
    let response = unsafe { CStr::from_ptr(raw_ptr) }
        .to_string_lossy()
        .into_owned();
    unsafe { free(raw_ptr.cast()) };
    Some(response)
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub(crate) fn inspect_compiled_groups_from_raw(
    config_raw_json: &str,
    profile_dir: &str,
    exclude_not_selectable: bool,
) -> Option<String> {
    let symbols = resolve_mihomo_inspect_symbols().ok()?;
    let config_raw = CString::new(config_raw_json).ok()?;
    let profile_dir_c = CString::new(profile_dir).ok()?;
    let raw_ptr = unsafe {
        (symbols.inspect_compiled_groups_result)(
            config_raw.as_ptr(),
            profile_dir_c.as_ptr(),
            if exclude_not_selectable { 1 } else { 0 },
        )
    };
    if raw_ptr.is_null() {
        return None;
    }
    let response = unsafe { CStr::from_ptr(raw_ptr) }
        .to_string_lossy()
        .into_owned();
    unsafe { free(raw_ptr.cast()) };
    Some(response)
}

/// # Safety
/// Caller must pass a valid null-terminated UTF-8 JSON string.
/// Returns a CompileRawResult JSON string as a Rust-allocated CString that must
/// be freed with override_free_string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_raw(request_json: *const c_char) -> *mut c_char {
    if request_json.is_null() {
        return error_cstring("read raw compile request: null pointer").into_raw();
    }
    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(s) => s,
        Err(err) => {
            return error_cstring(format!("read raw compile request: {err}")).into_raw()
        }
    };
    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(r) => r,
        Err(err) => {
            return error_cstring(format!("decode raw compile request: {err}"))
                .into_raw()
        }
    };
    let response = match compile_raw_request(request) {
        Ok(result) => serde_json::to_string(&result)
            .unwrap_or_else(|_| compile_raw_error_json("raw compile result encode failed")),
        Err(err) => compile_raw_error_json(err),
    };
    CString::new(response).unwrap_or_default().into_raw()
}

/// # Safety
/// Caller must pass a valid null-terminated UTF-8 JSON string.
/// `error_out` may be null. When non-null and an error occurs, this function stores
/// a Rust-allocated C string in `*error_out` that must be released by
/// `override_free_string`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_config_raw(
    request_json: *const c_char,
    error_out: *mut *mut c_char,
) -> *mut c_char {
    if !error_out.is_null() {
        unsafe { *error_out = std::ptr::null_mut() };
    }

    if request_json.is_null() {
        set_error_out(error_out, "read raw compile request: null pointer");
        return std::ptr::null_mut();
    }

    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(s) => s,
        Err(err) => {
            set_error_out(error_out, format!("read raw compile request: {err}"));
            return std::ptr::null_mut();
        }
    };

    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(r) => r,
        Err(err) => {
            set_error_out(error_out, format!("decode raw compile request: {err}"));
            return std::ptr::null_mut();
        }
    };

    match compile_raw_request(request) {
        Ok(result) => CString::new(result.config_raw).unwrap_or_default().into_raw(),
        Err(err) => {
            set_error_out(error_out, err);
            std::ptr::null_mut()
        }
    }
}

/// # Safety
/// Caller must pass a valid null-terminated UTF-8 JSON string.
/// `config_raw_out` / `error_out` may be null; when non-null this function writes
/// Rust-allocated strings that must be released with `override_free_string`.
///
/// Returns a Rust-allocated summary JSON string (never null on normal code paths).
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_summary_and_config(
    request_json: *const c_char,
    config_raw_out: *mut *mut c_char,
    error_out: *mut *mut c_char,
) -> *mut c_char {
    if !config_raw_out.is_null() {
        unsafe { *config_raw_out = std::ptr::null_mut() };
    }
    if !error_out.is_null() {
        unsafe { *error_out = std::ptr::null_mut() };
    }

    if request_json.is_null() {
        let message = "read raw compile request: null pointer".to_string();
        set_error_out(error_out, &message);
        return summary_error_json(message).into_raw();
    }

    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(s) => s,
        Err(err) => {
            let message = format!("read raw compile request: {err}");
            set_error_out(error_out, &message);
            return summary_error_json(message).into_raw();
        }
    };

    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(r) => r,
        Err(err) => {
            let message = format!("decode raw compile request: {err}");
            set_error_out(error_out, &message);
            return summary_error_json(message).into_raw();
        }
    };

    match compile_raw_summary_and_config(request) {
        Ok((summary_json, config_raw, warnings)) => {
            if !config_raw_out.is_null() {
                unsafe {
                    *config_raw_out = CString::new(config_raw).unwrap_or_default().into_raw();
                }
            }
            let mut summary = summary_json;
            if !warnings.is_empty() {
                // Append best-effort warnings without changing success semantics.
                if let Ok(mut value) = serde_json::from_str::<JsonValue>(&summary) {
                    if let Some(obj) = value.as_object_mut()
                        && let Some(existing) = obj.get_mut("warnings").and_then(|v| v.as_array_mut()) {
                            for warning in warnings {
                                existing.push(JsonValue::String(warning));
                            }
                        }
                    if let Ok(encoded) = serde_json::to_string(&value) {
                        summary = encoded;
                    }
                }
            }
            CString::new(summary).unwrap_or_default().into_raw()
        }
        Err(message) => {
            set_error_out(error_out, &message);
            summary_error_json(message).into_raw()
        }
    }
}

/// # Safety
/// Caller must pass a valid null-terminated UTF-8 JSON string.
/// Returns a Rust-allocated `NativeInspectResult` JSON string that must be
/// released with `override_free_string`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_inspect_tun_route_exclude_address(
    request_json: *const c_char,
) -> *mut c_char {
    if request_json.is_null() {
        return CString::new(encode_inspect_error(
            "read raw compile request: null pointer",
        ))
        .unwrap_or_default()
        .into_raw();
    }

    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(s) => s,
        Err(err) => {
            return CString::new(encode_inspect_error(format!(
                "read raw compile request: {err}"
            )))
            .unwrap_or_default()
            .into_raw();
        }
    };

    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(r) => r,
        Err(err) => {
            return CString::new(encode_inspect_error(format!(
                "decode raw compile request: {err}"
            )))
            .unwrap_or_default()
            .into_raw();
        }
    };

    let result_json = match compile_inspect_tun_route_exclude_address_json(request) {
        Ok(payload) => payload,
        Err(err) => encode_inspect_error(err),
    };

    CString::new(result_json).unwrap_or_default().into_raw()
}

/// # Safety
/// Caller must pass valid UTF-8 C strings for request/profile_dir and release the returned
/// string with `override_free_string`.
#[cfg(any(target_os = "linux", target_os = "android"))]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_and_inspect_groups(
    request_json: *const c_char,
    profile_dir: *const c_char,
    exclude_not_selectable: c_int,
) -> *mut c_char {
    if request_json.is_null() {
        return CString::new(encode_inspect_error(
            "read raw compile request: null pointer",
        ))
        .unwrap_or_default()
        .into_raw();
    }

    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(s) => s,
        Err(err) => {
            return CString::new(encode_inspect_error(format!(
                "read raw compile request: {err}"
            )))
            .unwrap_or_default()
            .into_raw();
        }
    };

    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(r) => r,
        Err(err) => {
            return CString::new(encode_inspect_error(format!(
                "decode raw compile request: {err}"
            )))
            .unwrap_or_default()
            .into_raw();
        }
    };

    let compile = match compile_raw_request(request) {
        Ok(result) => result,
        Err(err) => {
            return CString::new(encode_inspect_error(err))
                .unwrap_or_default()
                .into_raw();
        }
    };

    let symbols = match resolve_mihomo_inspect_symbols() {
        Ok(s) => s,
        Err(err) => {
            return CString::new(encode_inspect_error(err))
                .unwrap_or_default()
                .into_raw();
        }
    };

    let config_raw = CString::new(compile.config_raw)
        .unwrap_or_else(|_| CString::new("{}").unwrap_or_default());
    let profile_dir_c = if profile_dir.is_null() {
        CString::new("").unwrap_or_default()
    } else {
        match unsafe { CStr::from_ptr(profile_dir) }.to_str() {
            Ok(s) => CString::new(s).unwrap_or_default(),
            Err(_) => CString::new("").unwrap_or_default(),
        }
    };

    let raw_ptr = unsafe {
        (symbols.inspect_compiled_groups_result)(
            config_raw.as_ptr(),
            profile_dir_c.as_ptr(),
            exclude_not_selectable,
        )
    };

    if raw_ptr.is_null() {
        return CString::new(encode_inspect_error("inspect compiled groups failed"))
            .unwrap_or_default()
            .into_raw();
    }

    let response = unsafe { CStr::from_ptr(raw_ptr) }
        .to_string_lossy()
        .into_owned();
    unsafe { free(raw_ptr.cast()) };

    CString::new(response).unwrap_or_default().into_raw()
}

/// # Safety
/// Caller must pass valid UTF-8 request string and release returned string with
/// `override_free_string`.
#[cfg(any(target_os = "linux", target_os = "android"))]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_and_inspect_group_names(
    request_json: *const c_char,
    exclude_not_selectable: c_int,
) -> *mut c_char {
    if request_json.is_null() {
        return std::ptr::null_mut();
    }

    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(r) => r,
        Err(_) => return std::ptr::null_mut(),
    };

    let compile = match compile_raw_request(request) {
        Ok(result) => result,
        Err(_) => return std::ptr::null_mut(),
    };

    let symbols = match resolve_mihomo_inspect_symbols() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let config_raw = CString::new(compile.config_raw)
        .unwrap_or_else(|_| CString::new("{}").unwrap_or_default());

    let raw_ptr = unsafe {
        (symbols.inspect_compiled_group_names)(
            config_raw.as_ptr(),
            exclude_not_selectable,
        )
    };
    if raw_ptr.is_null() {
        return std::ptr::null_mut();
    }

    let response = unsafe { CStr::from_ptr(raw_ptr) }
        .to_string_lossy()
        .into_owned();
    unsafe { free(raw_ptr.cast()) };
    CString::new(response).unwrap_or_default().into_raw()
}

/// # Safety
/// Caller must pass valid UTF-8 compiled configRaw JSON and release returned string
/// with `override_free_string`.
#[cfg(any(target_os = "linux", target_os = "android"))]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_inspect_compiled_group_names(
    config_raw_json: *const c_char,
    exclude_not_selectable: c_int,
) -> *mut c_char {
    if config_raw_json.is_null() {
        return std::ptr::null_mut();
    }

    let config_raw = match unsafe { CStr::from_ptr(config_raw_json) }.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    inspect_compiled_group_names_from_raw(config_raw, exclude_not_selectable != 0)
        .and_then(|s| CString::new(s).ok().map(CString::into_raw))
        .unwrap_or(std::ptr::null_mut())
}

/// # Safety
/// Caller must pass valid UTF-8 compiled configRaw JSON and release returned string
/// with `override_free_string`.
#[cfg(any(target_os = "linux", target_os = "android"))]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_inspect_compiled_groups(
    config_raw_json: *const c_char,
    profile_dir: *const c_char,
    exclude_not_selectable: c_int,
) -> *mut c_char {
    if config_raw_json.is_null() {
        return std::ptr::null_mut();
    }

    let config_raw = match unsafe { CStr::from_ptr(config_raw_json) }.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let profile_dir_str = if profile_dir.is_null() {
        ""
    } else {
        unsafe { CStr::from_ptr(profile_dir) }.to_str().unwrap_or_default()
    };
    inspect_compiled_groups_from_raw(config_raw, profile_dir_str, exclude_not_selectable != 0)
        .and_then(|s| CString::new(s).ok().map(CString::into_raw))
        .unwrap_or(std::ptr::null_mut())
}

fn error_cstring(message: impl Into<String>) -> CString {
    CString::new(compile_raw_error_json(message)).unwrap_or_default()
}

fn compile_raw_summary_and_config(
    request: CompileRequest,
) -> Result<(String, String, Vec<String>), String> {
    let compiled = compile_root(&request)?;
    let mut warnings = compiled.warnings;
    let config_raw = serde_json::to_string(&compiled.root)
        .map_err(|err| format!("encode raw config json: {err}"))?;
    let fingerprint = fingerprint_for(request.profile_uuid.as_bytes(), config_raw.as_bytes());
    let (tun_include_package, tun_exclude_package, extract_warnings) =
        extract_tun_packages(&compiled.root);
    warnings.extend(extract_warnings);

    let summary = CompileRawSummary {
        success: true,
        fingerprint,
        warnings,
        error: None,
        tun_include_package,
        tun_exclude_package,
    };

    let summary_json = serde_json::to_string(&summary)
        .unwrap_or_else(|_| summary_error_json_string("compile raw summary encode failed"));
    Ok((summary_json, config_raw, Vec::new()))
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub(crate) fn compile_summary_and_config_json(
    request: CompileRequest,
) -> Result<(String, String), String> {
    let (summary, config_raw, _warnings) = compile_raw_summary_and_config(request)?;
    Ok((summary, config_raw))
}

pub(crate) fn compile_inspect_tun_route_exclude_address_json(
    request: CompileRequest,
) -> Result<String, String> {
    let compiled = compile_root(&request)?;
    let addresses = extract_tun_route_exclude_address(&compiled.root)?;
    let payload = serde_json::to_string(&addresses)
        .map_err(|err| format!("encode tun route-exclude-address payload: {err}"))?;
    let result = NativeInspectResult {
        success: true,
        payload,
        error: None,
    };
    serde_json::to_string(&result).map_err(|err| format!("encode inspect result: {err}"))
}

fn extract_tun_packages(root: &JsonValue) -> (Vec<String>, Vec<String>, Vec<String>) {
    let mut warnings = Vec::new();
    let mut include = Vec::new();
    let mut exclude = Vec::new();

    let Some(tun) = root.get("tun") else {
        return (include, exclude, warnings);
    };
    let Some(tun_obj) = tun.as_object() else {
        warnings.push("inspect tun packages failed: tun is not an object".to_string());
        return (include, exclude, warnings);
    };

    collect_package_list(tun_obj, "include-package", &mut include, &mut warnings);
    collect_package_list(tun_obj, "exclude-package", &mut exclude, &mut warnings);

    (include, exclude, warnings)
}

fn collect_package_list(
    tun_obj: &JsonMap<String, JsonValue>,
    key: &str,
    out: &mut Vec<String>,
    warnings: &mut Vec<String>,
) {
    let Some(value) = tun_obj.get(key) else {
        return;
    };
    match value {
        JsonValue::Array(items) => {
            for item in items {
                if let Some(s) = item.as_str() {
                    if !s.trim().is_empty() {
                        out.push(s.to_string());
                    }
                } else {
                    warnings.push(format!(
                        "inspect tun packages failed: {key} contains non-string value"
                    ));
                }
            }
        }
        JsonValue::String(s) => {
            if !s.trim().is_empty() {
                out.push(s.to_string());
            }
        }
        _ => warnings.push(format!(
            "inspect tun packages failed: {key} is not a list or string"
        )),
    }
}

fn extract_tun_route_exclude_address(root: &JsonValue) -> Result<Vec<String>, String> {
    let Some(tun) = root.get("tun") else {
        return Ok(Vec::new());
    };
    let tun_obj = tun
        .as_object()
        .ok_or_else(|| "tun is not an object".to_string())?;
    let Some(value) = tun_obj.get("route-exclude-address") else {
        return Ok(Vec::new());
    };

    let mut result = Vec::new();
    match value {
        JsonValue::Array(items) => {
            for item in items {
                let s = item
                    .as_str()
                    .ok_or_else(|| "route-exclude-address contains non-string value".to_string())?;
                if !s.trim().is_empty() {
                    result.push(s.to_string());
                }
            }
        }
        JsonValue::String(s) => {
            if !s.trim().is_empty() {
                result.push(s.to_string());
            }
        }
        _ => {
            return Err("route-exclude-address is not a list or string".to_string());
        }
    }
    Ok(result)
}

pub(crate) fn encode_inspect_error(message: impl Into<String>) -> String {
    let result = NativeInspectResult {
        success: false,
        payload: String::new(),
        error: Some(message.into()),
    };
    serde_json::to_string(&result)
        .unwrap_or_else(|_| "{\"success\":false,\"payload\":\"\",\"error\":\"native inspect failed\"}".to_string())
}

fn summary_error_json(message: impl Into<String>) -> CString {
    CString::new(summary_error_json_string(message)).unwrap_or_default()
}

pub(crate) fn summary_error_json_string(message: impl Into<String>) -> String {
    let summary = CompileRawSummary {
        success: false,
        fingerprint: String::new(),
        warnings: Vec::new(),
        error: Some(message.into()),
        tun_include_package: Vec::new(),
        tun_exclude_package: Vec::new(),
    };
    serde_json::to_string(&summary).unwrap_or_else(|_| {
        "{\"success\":false,\"fingerprint\":\"\",\"warnings\":[],\"error\":\"compile raw config failed\",\"tunIncludePackage\":[],\"tunExcludePackage\":[]}".to_string()
    })
}

fn set_error_out(error_out: *mut *mut c_char, message: impl Into<String>) {
    if error_out.is_null() {
        return;
    }
    let msg = CString::new(message.into()).unwrap_or_default().into_raw();
    unsafe {
        *error_out = msg;
    }
}

/// # Safety
/// Caller must pass a pointer previously returned by override_compile_raw.
/// Passing any other pointer or a null pointer is undefined behavior.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_free_string(s: *mut c_char) {
    if !s.is_null() {
        drop(unsafe { CString::from_raw(s) });
    }
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::{Arc, Barrier};
    use std::thread;
    use std::time::Duration;

    use super::with_compile_lock;

    #[test]
    fn compile_lock_serializes_concurrent_requests() {
        let ready = Arc::new(Barrier::new(8));
        let active = Arc::new(AtomicUsize::new(0));
        let peak = Arc::new(AtomicUsize::new(0));
        let mut workers = Vec::new();

        for _ in 0..8 {
            let ready = Arc::clone(&ready);
            let active = Arc::clone(&active);
            let peak = Arc::clone(&peak);
            workers.push(thread::spawn(move || {
                ready.wait();
                with_compile_lock(|| {
                    let current = active.fetch_add(1, Ordering::SeqCst) + 1;
                    peak.fetch_max(current, Ordering::SeqCst);
                    thread::sleep(Duration::from_millis(5));
                    active.fetch_sub(1, Ordering::SeqCst);
                    Ok(())
                })
                .expect("serialized compile work");
            }));
        }

        for worker in workers {
            worker.join().expect("join compile worker");
        }
        assert_eq!(peak.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn compile_lock_recovers_after_panic() {
        let panic = std::panic::catch_unwind(|| {
            let _ = with_compile_lock::<()>(|| panic!("compile panic"));
        });
        assert!(panic.is_err());
        assert_eq!(with_compile_lock(|| Ok("recovered")), Ok("recovered"));
    }
}
