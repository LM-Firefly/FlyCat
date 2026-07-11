#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#include "bridge_helper.h"
#include "jni_helper.h"
#include "trace.h"

#include "version.h"

#include <dlfcn.h>
#include <mutex>
#include "libclash.h"

extern "C" int patchTunnelMode(c_string mode);

// Bridge callback implementations defined in bridge_callbacks.cpp.
extern "C" void call_completable_complete_impl(void *completable, const char *exception);
extern "C" void release_jni_object_impl(void *obj);

extern "C" {

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInit(JNIEnv *env, jobject thiz,
                                                          jstring home,
                                                          jstring version_name, jint sdk_version) {
    TRACE_METHOD();

    scoped_string _home = get_string(home);
    scoped_string _version_name = get_string(version_name);
    const char* _git_version = make_String(GIT_VERSION);

    coreInit(_home, _version_name, _git_version, sdk_version);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeReset(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    reset();
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeForceGc(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    forceGc();
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSuspend(JNIEnv *env, jobject thiz,
                                                             jboolean suspended) {
    TRACE_METHOD();

    suspend((int) suspended);
}


JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryTunnelState(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryTunnelState();

    return new_string(response);
}

JNIEXPORT jlong JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryTrafficNow(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    uint64_t upload = 0l, download = 0l;

    queryNow(&upload, &download);

    return (jlong) (down_scale_traffic(upload) << 32u | down_scale_traffic(download));
}

JNIEXPORT jlong JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryTrafficTotal(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    uint64_t upload = 0l, download = 0l;

    queryTotal(&upload, &download);

    return (jlong) (down_scale_traffic(upload) << 32u | down_scale_traffic(download));
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryConnections(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryConnections();

    return new_string(response);
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCloseConnection(JNIEnv *env, jobject thiz,
                                                                     jstring id) {
    TRACE_METHOD();

    scoped_string _id = get_string(id);

    return (jboolean) closeConnection(_id);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCloseAllConnections(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    closeAllConnections();
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeNotifyDnsChanged(JNIEnv *env, jobject thiz,
                                                                      jstring dns_list) {
    TRACE_METHOD();

    scoped_string _dns_list = get_string(dns_list);

    notifyDnsChanged(_dns_list);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeNotifyTimeZoneChanged(JNIEnv *env, jobject thiz,
                                                                           jstring name, jint offset) {
    TRACE_METHOD();

    scoped_string _name = get_string(name);

    notifyTimeZoneChanged(_name, offset);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStartTun(JNIEnv *env, jobject thiz,
                                                              jint fd,
                                                              jstring stack,
                                                              jstring gateway,
                                                              jstring portal,
                                                              jstring dns,
                                                              jobject cb) {
    TRACE_METHOD();

    scoped_string _stack = get_string(stack);
    scoped_string _gateway = get_string(gateway);
    scoped_string _portal = get_string(portal);
    scoped_string _dns = get_string(dns);
    jobject _interface = new_global(cb);

    startTun(fd, _stack, _gateway, _portal, _dns, _interface);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStopTun(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    stopTun();
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStartRootTun(JNIEnv *env, jobject thiz,
                                                                  jstring config_yaml) {
    TRACE_METHOD();

    scoped_string _config_yaml = get_string(config_yaml);
    scoped_string error = startRootTun(_config_yaml);

    if (error == NULL)
        return NULL;

    return new_string(error);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStopRootTun(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    stopRootTun();
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStartHttp(JNIEnv *env, jobject thiz,
                                                               jstring listen_at) {
    TRACE_METHOD();

    scoped_string _listen_at = get_string(listen_at);

    scoped_string listened = startHttp(_listen_at);

    if (listened == NULL)
        return NULL;

    return new_string(listened);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeStopHttp(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    stopHttp();
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryGroupNames(JNIEnv *env, jobject thiz,
                                                                      jboolean exclude_not_selectable) {
    TRACE_METHOD();

    scoped_string response = queryGroupNames((int) exclude_not_selectable);

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryGroup(JNIEnv *env, jobject thiz,
                                                                 jstring name, jstring mode) {
    TRACE_METHOD();

    scoped_string _name = get_string(name);
    scoped_string _mode = get_string(mode);

    scoped_string response = queryGroup(_name, _mode);

    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeHealthCheck(JNIEnv *env, jobject thiz,
                                                                 jobject completable,
                                                                 jstring name) {
    TRACE_METHOD();

    jobject _completable = new_global(completable);
    scoped_string _name = get_string(name);

    healthCheck(_completable, _name);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeHealthCheckAll(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    healthCheckAll();
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativePatchTunnelMode(JNIEnv *env, jobject thiz,
                                                                     jstring mode) {
    TRACE_METHOD();

    scoped_string _mode = get_string(mode);

    return (jboolean) patchTunnelMode(_mode);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeHealthCheckProxy(JNIEnv *env, jobject thiz,
jobject completable,
jstring proxy_name) {
TRACE_METHOD();

jobject _completable = new_global(completable);
scoped_string _proxy_name = get_string(proxy_name);

healthCheckProxy(_completable, _proxy_name);
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativePatchSelector(JNIEnv *env, jobject thiz,
                                                                   jstring selector, jstring name) {
    TRACE_METHOD();

    scoped_string _selector = get_string(selector);
    scoped_string _name = get_string(name);

    return (jboolean) patchSelector(_selector, _name);
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeForcePatchSelector(JNIEnv *env, jobject thiz,
                                                                        jstring selector,
                                                                        jstring name) {
    TRACE_METHOD();

    scoped_string _selector = get_string(selector);
    scoped_string _name = get_string(name);

    return (jboolean) patchForceSelector(_selector, _name);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeFetchAndValid(JNIEnv *env, jobject thiz,
                                                                   jobject callback,
                                                                   jstring path,
                                                                   jstring url, jboolean force) {
    TRACE_METHOD();

    jobject _completable = new_global(callback);
    scoped_string _path = get_string(path);
    scoped_string _url = get_string(url);

    fetchAndValid(_completable, _path, _url, force);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSetAgeSecretKey(JNIEnv *env, jobject thiz,
                                                                      jstring key) {
    TRACE_METHOD();

    if (key == NULL) {
        setAgeSecretKey(NULL);
        return;
    }

    scoped_string _key = get_string(key);
    setAgeSecretKey(_key);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeGenX25519KeyPair(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = genX25519KeyPair();
    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeGenHybridKeyPair(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = genHybridKeyPair();
    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeVerifySecretKeys(JNIEnv *env, jobject thiz,
                                                                       jstring secret_keys) {
    TRACE_METHOD();

    if (secret_keys == NULL)
        return JNI_FALSE;

    scoped_string _secret_keys = get_string(secret_keys);
    return (jboolean) verifySecretKeys(_secret_keys);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeToPublicKeys(JNIEnv *env, jobject thiz,
                                                                   jstring secret_keys) {
    TRACE_METHOD();

    if (secret_keys == NULL)
        return NULL;

    scoped_string _secret_keys = get_string(secret_keys);
    scoped_string response = toPublicKeys(_secret_keys);
    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeVerifyPublicKeys(JNIEnv *env, jobject thiz,
                                                                       jstring public_keys) {
    TRACE_METHOD();

    if (public_keys == NULL)
        return JNI_FALSE;

    scoped_string _public_keys = get_string(public_keys);
    return (jboolean) verifyPublicKeys(_public_keys);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryProviders(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryProviders();

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInspectCompiledGroups(JNIEnv *env, jobject thiz,
                                                                            jstring yaml_text,
                                                                            jstring profile_dir,
                                                                            jboolean exclude_not_selectable) {
    TRACE_METHOD();
    scoped_string _yaml_text = get_string(yaml_text);
    scoped_string _profile_dir = get_string(profile_dir);
    scoped_string groups_yaml = inspectCompiledGroupsResult(_yaml_text, _profile_dir, (int) exclude_not_selectable);
    if (groups_yaml == NULL) {
        scoped_string error_result = inspectErrorResult("inspect compiled groups failed");
        return new_string(error_result);
    }
    return new_string(groups_yaml);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUpdateProvider(JNIEnv *env, jobject thiz,
                                                                    jobject completable,
                                                                    jstring type,
                                                                    jstring name) {
    TRACE_METHOD();

    jobject _completable = new_global(completable);
    scoped_string _type = get_string(type);
    scoped_string _name = get_string(name);

    updateProvider(_completable, _type, _name);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryConfiguration(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryConfiguration();

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeLogcat(JNIEnv *env, jobject thiz,
                                                                     jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeLogcat(_callback);
}


void init_bridge_callbacks(JNIEnv *env);

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    TRACE_METHOD();

    JNIEnv *env = NULL;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    initialize_jni(vm, env);

    init_bridge_callbacks(env);

    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCoreVersion(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    const char* Version = make_String(GIT_VERSION);

    return new_string(Version);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSetCustomUserAgent(JNIEnv *env, jobject thiz, jstring user_agent) {
    TRACE_METHOD();

    scoped_string ua = get_string(user_agent);

    setCustomUserAgent(ua);
}


typedef char* (*override_compile_raw_fn)(const char*);
typedef void (*override_free_string_fn)(char*);

extern char* compiledRawResultSummary(const char*);
extern char* compiledRawFallbackSummary(const char*);
extern char* inspectErrorResult(const char*);
extern char* inspectCompiledGroupsResult(const char*, const char*, int);
extern char* inspectCompiledTunRouteExcludeAddressResult(const char*);

struct override_symbols {
    override_compile_raw_fn compile_raw;
    override_free_string_fn free_string;
};

static override_symbols resolve_override_symbols() {
    static override_symbols symbols = {nullptr, nullptr};
    static std::once_flag resolve_once;

    std::call_once(resolve_once, []() {
        // liboverride.so is loaded via System.loadLibrary with LOCAL symbol visibility, so its
        // exports are NOT in libbridge's RTLD_DEFAULT scope (it is not a DT_NEEDED dependency).
        // Resolve against an explicit handle to the already-loaded library; dlsym(handle) is
        // unaffected by RTLD_LOCAL. Fall back to RTLD_DEFAULT if the handle cannot be obtained.
        void* handle = dlopen("liboverride.so", RTLD_NOW | RTLD_NOLOAD);
        if (handle == nullptr) {
            handle = dlopen("liboverride.so", RTLD_NOW);
        }
        void* scope = handle != nullptr ? handle : RTLD_DEFAULT;
        symbols.compile_raw = (override_compile_raw_fn)dlsym(scope, "override_compile_raw");
        symbols.free_string = (override_free_string_fn)dlsym(scope, "override_free_string");
    });

    return symbols;
}

static char* compile_override_raw_result(const char* request_json, override_symbols symbols) {
    if (!symbols.compile_raw || !symbols.free_string) {
        return NULL;
    }
    return symbols.compile_raw(request_json);
}

struct raw_compile_payload {
    char* config_raw;
    char* summary_json;
    char* error;
};

static void free_raw_compile_payload(raw_compile_payload* payload) {
    if (payload == NULL) {
        return;
    }
    free(payload->config_raw);
    free(payload->summary_json);
    free(payload->error);
    payload->config_raw = NULL;
    payload->summary_json = NULL;
    payload->error = NULL;
}

static const char* raw_compile_error_or_default(raw_compile_payload* payload, const char* fallback) {
    if (payload != NULL && payload->error != NULL) {
        return payload->error;
    }
    return fallback;
}

static char* raw_compile_summary_or_fallback(raw_compile_payload* payload, const char* fallback_error) {
    if (payload != NULL && payload->summary_json != NULL) {
        return strdup(payload->summary_json);
    }
    return compiledRawFallbackSummary(fallback_error);
}

static raw_compile_payload compile_override_raw_payload(const char* request_json, override_symbols symbols) {
    raw_compile_payload payload = {NULL, NULL, NULL};
    char* result_json = compile_override_raw_result(request_json, symbols);
    if (result_json == NULL) {
        payload.error = strdup("compile raw config failed");
        return payload;
    }

    payload.summary_json = compiledRawResultSummary(result_json);
    char* result_error = compiledRawResultError(result_json);
    if (result_error != NULL) {
        payload.error = strdup(result_error);
        free(result_error);
        symbols.free_string(result_json);
        return payload;
    }

    payload.config_raw = compiledRawResultConfigRaw(result_json);
    symbols.free_string(result_json);
    if (payload.config_raw == NULL) {
        payload.error = strdup("compile raw config returned empty configRaw");
    }
    return payload;
}

static char* compile_override_raw_config(const char* request_json, override_symbols symbols, char** error) {
    if (error != NULL) {
        *error = NULL;
    }
    raw_compile_payload payload = compile_override_raw_payload(request_json, symbols);
    char* config_raw = payload.config_raw;
    payload.config_raw = NULL;
    if (config_raw == NULL && error != NULL && payload.error != NULL) {
        *error = strdup(payload.error);
    }
    free_raw_compile_payload(&payload);
    return config_raw;
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndLoadConfig(JNIEnv *env, jobject thiz,
                                                                         jobject completable,
                                                                         jstring request_json) {
    TRACE_METHOD();

    override_symbols symbols = resolve_override_symbols();

    if (!symbols.compile_raw || !symbols.free_string) {
        jobject _completable = new_global(completable);
        call_completable_complete_impl(_completable, "override library symbols not found");
        release_jni_object_impl(_completable);
        return;
    }

    jobject _completable = new_global(completable);
    scoped_string _request_json = get_string(request_json);

    // Call Rust to compile the source to RawConfig JSON in native memory.
    scoped_string compile_error = NULL;
    char* config_raw = compile_override_raw_config(_request_json, symbols, &compile_error);

    if (config_raw == NULL) {
        call_completable_complete_impl(_completable, compile_error != NULL ? compile_error : "compile raw config failed");
        release_jni_object_impl(_completable);
        return;
    }

    // Call Go to load the raw config (async, spawns goroutine, calls complete)
    loadCompiledRaw(_completable, config_raw);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompilePreview(JNIEnv *env, jobject thiz,
                                                                    jstring request_json) {
    TRACE_METHOD();

    override_symbols symbols = resolve_override_symbols();

    if (!symbols.compile_raw || !symbols.free_string) {
        return new_string("{\"success\":false,\"error\":\"override library symbols not found\"}");
    }

    scoped_string _request_json = get_string(request_json);
    raw_compile_payload payload = compile_override_raw_payload(_request_json, symbols);

    if (payload.config_raw == NULL) {
        const char* err = raw_compile_error_or_default(&payload, "compile raw config failed");
        // Build CompileResult JSON: {success:false, error:"..."}
        size_t len = strlen(err) + 64;
        char* result = (char*)malloc(len);
        snprintf(result, len, "{\"success\":false,\"error\":\"%s\"}", err);
        free_raw_compile_payload(&payload);
        return new_string(result);
    }

    // Build CompileResult JSON from summary_json + finalYaml
    // summary_json has: {success, fingerprint, warnings, error}
    // We need: {success, fingerprint, finalYaml, warnings, error}
    if (payload.summary_json != NULL) {
        // Insert "finalYaml":"<yaml>" into the summary JSON before the closing brace
        size_t summary_len = strlen(payload.summary_json);
        size_t yaml_len = strlen(payload.config_raw);
        // Escape config_raw for JSON (basic escaping of backslash and quote)
        size_t escaped_yaml_cap = yaml_len * 2 + 1;
        char* escaped_yaml = (char*)malloc(escaped_yaml_cap);
        size_t ei = 0;
        for (size_t i = 0; i < yaml_len; i++) {
            char c = payload.config_raw[i];
            if (c == '\\' || c == '"') {
                escaped_yaml[ei++] = '\\';
                escaped_yaml[ei++] = c;
            } else if (c == '\n') {
                escaped_yaml[ei++] = '\\';
                escaped_yaml[ei++] = 'n';
            } else if (c == '\r') {
                escaped_yaml[ei++] = '\\';
                escaped_yaml[ei++] = 'r';
            } else if (c == '\t') {
                escaped_yaml[ei++] = '\\';
                escaped_yaml[ei++] = 't';
            } else {
                escaped_yaml[ei++] = c;
            }
        }
        escaped_yaml[ei] = '\0';

        // Build: {summary fields without }, "finalYaml":"<escaped>"}
        // Strip trailing } from summary, add finalYaml, then }
        size_t result_len = summary_len + strlen(escaped_yaml) + 32;
        char* result = (char*)malloc(result_len);
        if (summary_len > 1 && payload.summary_json[summary_len - 1] == '}') {
            // Remove trailing } and append ,\"finalYaml\":\"...\"}
            memcpy(result, payload.summary_json, summary_len - 1);
            result[summary_len - 1] = '\0';
            strcat(result, ",\"finalYaml\":\"");
            strcat(result, escaped_yaml);
            strcat(result, "\"}");
        } else {
            snprintf(result, result_len, "{\"success\":true,\"finalYaml\":\"%s\"}", escaped_yaml);
        }
        free(escaped_yaml);
        free_raw_compile_payload(&payload);
        return new_string(result);
    }

    // Fallback: just return success with the yaml
    size_t yaml_len = strlen(payload.config_raw);
    size_t result_len = yaml_len + 64;
    char* result = (char*)malloc(result_len);
    snprintf(result, result_len, "{\"success\":true,\"finalYaml\":\"%s\"}", payload.config_raw);
    free_raw_compile_payload(&payload);
    return new_string(result);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInspectCompiledGroupNames(JNIEnv *env, jobject thiz,
                                                                                jstring yaml_text,
                                                                                jboolean exclude_not_selectable) {
    TRACE_METHOD();
    scoped_string _yaml_text = get_string(yaml_text);
    scoped_string names_json = inspectCompiledGroupNames(_yaml_text, (int) exclude_not_selectable);
    if (names_json == NULL) {
        return NULL;
    }
    return new_string(names_json);
}

} // extern "C"
