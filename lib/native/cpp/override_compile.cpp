#include <jni.h>
#include <string.h>
#include <stdlib.h>

#include "jni_helper.h"
#include "trace.h"

#include <dlfcn.h>
#include <mutex>
#include "libclash.h"

extern "C" {

// Bridge callback implementations defined in main.cpp.
void call_completable_complete_impl(void *completable, const char *exception);
void release_jni_object_impl(void *obj);

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
        symbols.compile_raw = reinterpret_cast<override_compile_raw_fn>(dlsym(scope, "override_compile_raw"));
        symbols.free_string = reinterpret_cast<override_free_string_fn>(dlsym(scope, "override_free_string"));
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

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndLoadConfigSummary(JNIEnv *env,
                                                                                      jobject thiz,
                                                                                      jobject completable,
                                                                                      jstring request_json) {
    TRACE_METHOD();

    override_symbols symbols = resolve_override_symbols();

    if (!symbols.compile_raw || !symbols.free_string) {
        jobject _completable = new_global(completable);
        scoped_string summary_json = compiledRawFallbackSummary("override library symbols not found");
        call_completable_complete_impl(_completable, "override library symbols not found");
        release_jni_object_impl(_completable);
        return new_string(summary_json);
    }

    jobject _completable = new_global(completable);
    scoped_string _request_json = get_string(request_json);
    raw_compile_payload payload = compile_override_raw_payload(_request_json, symbols);
    if (payload.config_raw == NULL) {
        const char* compile_error = raw_compile_error_or_default(&payload, "compile raw config failed");
        scoped_string summary_json = raw_compile_summary_or_fallback(&payload, compile_error);
        call_completable_complete_impl(_completable, compile_error);
        free_raw_compile_payload(&payload);
        release_jni_object_impl(_completable);
        return new_string(summary_json);
    }

    if (payload.summary_json == NULL) {
        scoped_string summary_json = compiledRawFallbackSummary("compile raw summary failed");
        free_raw_compile_payload(&payload);
        call_completable_complete_impl(_completable, "compile raw summary failed");
        release_jni_object_impl(_completable);
        return new_string(summary_json);
    }

    char* config_raw = payload.config_raw;
    payload.config_raw = NULL;
    jstring summary = new_string(payload.summary_json);
    free_raw_compile_payload(&payload);
    loadCompiledRaw(_completable, config_raw);
    return summary;
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndInspectGroups(JNIEnv *env, jobject thiz,
                                                                              jstring request_json,
                                                                              jstring profile_dir,
                                                                              jboolean exclude_not_selectable) {
    TRACE_METHOD();

    override_symbols symbols = resolve_override_symbols();

    if (!symbols.compile_raw || !symbols.free_string) {
        scoped_string error_result = inspectErrorResult("override library symbols not found");
        return new_string(error_result);
    }

    scoped_string _request_json = get_string(request_json);
    scoped_string _profile_dir = get_string(profile_dir);

    // Compile encrypted source to RawConfig JSON in native memory.
    scoped_string compile_error = NULL;
    char* config_raw = compile_override_raw_config(_request_json, symbols, &compile_error);
    if (config_raw == NULL) {
        scoped_string error_result = inspectErrorResult(compile_error != NULL ? compile_error : "compile raw config failed");
        return new_string(error_result);
    }

    // Inspect groups via Go (returns YAML of group list)
    scoped_string groups_yaml = inspectCompiledGroupsResult(config_raw, _profile_dir, (int) exclude_not_selectable);

    free(config_raw);

    if (groups_yaml == NULL) {
        scoped_string error_result = inspectErrorResult("inspect compiled groups failed");
        return new_string(error_result);
    }

    return new_string(groups_yaml);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompileAndInspectTunRouteExcludeAddress(JNIEnv *env,
                                                                                                  jobject thiz,
                                                                                                  jstring request_json) {
    TRACE_METHOD();

    override_symbols symbols = resolve_override_symbols();

    if (!symbols.compile_raw || !symbols.free_string) {
        scoped_string error_result = inspectErrorResult("override library symbols not found");
        return new_string(error_result);
    }

    scoped_string _request_json = get_string(request_json);

    scoped_string compile_error = NULL;
    char* config_raw = compile_override_raw_config(_request_json, symbols, &compile_error);
    if (config_raw == NULL) {
        scoped_string error_result = inspectErrorResult(compile_error != NULL ? compile_error : "compile raw config failed");
        return new_string(error_result);
    }

    scoped_string route_exclude_address_json = inspectCompiledTunRouteExcludeAddressResult(config_raw);

    free(config_raw);

    if (route_exclude_address_json == NULL) {
        scoped_string error_result = inspectErrorResult("inspect compiled tun route-exclude-address failed");
        return new_string(error_result);
    }

    return new_string(route_exclude_address_json);
}

} // extern "C"
