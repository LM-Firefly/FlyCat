#include <jni.h>
#include <stdio.h>
#include <string.h>

#include "bridge_helper.h"
#include "jni_helper.h"
#include "trace.h"

extern "C" {

static jmethodID m_tun_interface_mark_socket;
static jmethodID m_tun_interface_query_socket_owner;
static jmethodID m_completable_complete;
static jmethodID m_completable_complete_exceptionally;
static jmethodID m_logcat_interface_received;
static jmethodID m_connection_close_interface_received;
static jmethodID m_connection_join_interface_received;
static jmethodID m_traffic_update_interface_received;
static jmethodID m_traffic_update_packed_interface_received;
static jmethodID m_clash_exception;
static jmethodID m_fetch_callback_report;
static jmethodID m_fetch_callback_complete;
static jmethodID m_open;
static jmethodID m_get_message;
static jclass c_clash_exception;
static jclass c_content;
static jobject o_unit;

static void call_tun_interface_mark_socket_impl(void *tun_interface, int fd) {
    TRACE_METHOD();

    ATTACH_JNI();

    env->CallVoidMethod((jobject) tun_interface,
                        (jmethodID) m_tun_interface_mark_socket,
                        (jint) fd);
}

static char *call_tun_interface_query_socket_owner_impl(void *tun_interface, int protocol,
                                                        const char *source, const char *target) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring source_string = new_string(source);
    jstring target_string = new_string(target);
    jstring result = (jstring) env->CallObjectMethod(
            (jobject) tun_interface,
            (jmethodID) m_tun_interface_query_socket_owner,
            (jint) protocol,
            source_string,
            target_string);

    if (source_string != NULL) {
        env->DeleteLocalRef(source_string);
    }
    if (target_string != NULL) {
        env->DeleteLocalRef(target_string);
    }

    if (jni_catch_exception(env) || result == NULL) {
        return NULL;
    }

    scoped_string value = get_string(result);
    env->DeleteLocalRef(result);
    return value == NULL ? NULL : strdup(value);
}

void call_completable_complete_impl(void *completable, const char *exception) {
    TRACE_METHOD();

    ATTACH_JNI();

    if (exception == NULL) {
        env->CallBooleanMethod(
                (jobject) completable,
                (jmethodID) m_completable_complete,
                (jobject) o_unit);
    } else {
        jstring exception_string = new_string(exception);
        jthrowable _exception = (jthrowable)
                env->NewObject(
                        (jclass) c_clash_exception,
                        (jmethodID) m_clash_exception,
                        exception_string
                );

        env->CallBooleanMethod(
                (jobject) completable,
                (jmethodID) m_completable_complete_exceptionally,
                (jobject) _exception);

        if (exception_string != NULL) {
            env->DeleteLocalRef(exception_string);
        }
        if (_exception != NULL) {
            env->DeleteLocalRef(_exception);
        }
    }
}

static void call_completable_complete_with_string_impl(void *completable, const char *result) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring result_string = new_string(result);
    env->CallBooleanMethod(
            (jobject) completable,
            (jmethodID) m_completable_complete,
            result_string);

    if (result_string != NULL) {
        env->DeleteLocalRef(result_string);
    }
}

static void call_fetch_callback_report_impl(void *fetch_callback, const char *status_json) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring _status_json = new_string(status_json);

    env->CallVoidMethod(
            (jobject) fetch_callback,
            (jmethodID) m_fetch_callback_report,
            (jstring) _status_json);

    if (_status_json != NULL) {
        env->DeleteLocalRef(_status_json);
    }
}

static void call_fetch_callback_complete_impl(void *fetch_callback, const char *error) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring _error = NULL;

    if (error != NULL)
        _error = new_string(error);

    env->CallVoidMethod(
            (jobject) fetch_callback,
            (jmethodID) m_fetch_callback_complete,
            (jstring) _error);

    if (_error != NULL) {
        env->DeleteLocalRef(_error);
    }
}

static int call_logcat_interface_received_impl(void *callback, const char *payload) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring payload_string = new_string(payload);
    env->CallVoidMethod(
            (jobject) callback,
            (jmethodID) m_logcat_interface_received,
            payload_string);

    if (payload_string != NULL) {
        env->DeleteLocalRef(payload_string);
    }

    if (jni_catch_exception(env)) {
        return 1;
    }

    return 0;
}

static int call_connection_close_interface_received_impl(void *callback, const char *payload) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring payload_string = new_string(payload);
    env->CallVoidMethod(
            (jobject) callback,
            (jmethodID) m_connection_close_interface_received,
            payload_string);

    if (payload_string != NULL) {
        env->DeleteLocalRef(payload_string);
    }

    if (jni_catch_exception(env)) {
        return 1;
    }

    return 0;
}

static int call_connection_join_interface_received_impl(void *callback, const char *payload) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring payload_string = new_string(payload);
    env->CallVoidMethod(
            (jobject) callback,
            (jmethodID) m_connection_join_interface_received,
            payload_string);

    if (payload_string != NULL) {
        env->DeleteLocalRef(payload_string);
    }

    if (jni_catch_exception(env)) {
        return 1;
    }

    return 0;
}

static int call_traffic_update_interface_received_impl(void *callback, const char *payload) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring payload_string = new_string(payload);
    env->CallVoidMethod(
            (jobject) callback,
            (jmethodID) m_traffic_update_interface_received,
            payload_string);

    if (payload_string != NULL) {
        env->DeleteLocalRef(payload_string);
    }

    if (jni_catch_exception(env)) {
        return 1;
    }

    return 0;
}

static int call_traffic_update_packed_interface_received_impl(void *callback,
                                                              long long upload_total,
                                                              long long download_total,
                                                              long long upload_speed,
                                                              long long download_speed) {
    TRACE_METHOD();

    ATTACH_JNI();

    env->CallVoidMethod(
            (jobject) callback,
            (jmethodID) m_traffic_update_packed_interface_received,
            (jlong) upload_total,
            (jlong) download_total,
            (jlong) upload_speed,
            (jlong) download_speed);

    if (jni_catch_exception(env)) {
        return 1;
    }

    return 0;
}

static void copy_error_message(char *error, int error_length, const char *message) {
    if (error == NULL || error_length <= 0) {
        return;
    }
    if (message == NULL) {
        message = "unknown";
    }
    snprintf(error, (size_t) error_length, "%s", message);
}

static int open_content_impl(const char *url, char *error, int error_length) {
    TRACE_METHOD();

    ATTACH_JNI();

    jstring url_string = new_string(url);
    int fd = env->CallStaticIntMethod(c_content, m_open, url_string);

    if (url_string != NULL) {
        env->DeleteLocalRef(url_string);
    }

    if (env->ExceptionCheck()) {
        jthrowable exception = env->ExceptionOccurred();

        env->ExceptionClear();

        jstring message = (jstring) env->CallObjectMethod(
                (jthrowable) exception,
                (jmethodID) m_get_message
        );

        if (message == NULL) {
            copy_error_message(error, error_length, "unknown");
        } else {
            scoped_string _message = get_string(message);

            copy_error_message(error, error_length, _message);
            env->DeleteLocalRef(message);
        }

        env->DeleteLocalRef(exception);

        return -1;
    }

    return fd;
}

void release_jni_object_impl(void *obj) {
    TRACE_METHOD();

    ATTACH_JNI();

    del_global((jobject) obj);
}

static void register_bridge_callbacks() {
    mark_socket_func = &call_tun_interface_mark_socket_impl;
    query_socket_owner_func = &call_tun_interface_query_socket_owner_impl;
    complete_func = &call_completable_complete_impl;
    complete_with_string_func = &call_completable_complete_with_string_impl;
    fetch_report_func = &call_fetch_callback_report_impl;
    fetch_complete_func = &call_fetch_callback_complete_impl;
    logcat_received_func = &call_logcat_interface_received_impl;
    connection_close_received_func = &call_connection_close_interface_received_impl;
    connection_join_received_func = &call_connection_join_interface_received_impl;
    traffic_update_received_func = &call_traffic_update_interface_received_impl;
    traffic_update_received_packed_func = &call_traffic_update_packed_interface_received_impl;
    open_content_func = &open_content_impl;
    release_object_func = &release_jni_object_impl;
}

// Resolves the Java classes/methods the bridge callbacks need and installs the
// callback function pointers. Must run once from JNI_OnLoad.
void init_bridge_callbacks(JNIEnv *env) {
    jclass c_tun_interface = find_class("com/github/yumelira/yumebox/core/bridge/TunInterface");
    jclass c_completable = find_class("kotlinx/coroutines/CompletableDeferred");
    jclass c_fetch_callback = find_class("com/github/yumelira/yumebox/core/bridge/FetchCallback");
    jclass c_logcat_interface = find_class("com/github/yumelira/yumebox/core/bridge/LogcatInterface");
    jclass c_connection_close_interface = find_class("com/github/yumelira/yumebox/core/bridge/ConnectionCloseInterface");
    jclass c_connection_join_interface = find_class("com/github/yumelira/yumebox/core/bridge/ConnectionJoinInterface");
    jclass c_traffic_update_interface = find_class("com/github/yumelira/yumebox/core/bridge/TrafficUpdateInterface");
    jclass c_traffic_update_packed_interface = find_class("com/github/yumelira/yumebox/core/bridge/TrafficUpdatePackedInterface");
    jclass _c_clash_exception = find_class("com/github/yumelira/yumebox/core/bridge/ClashException");
    jclass _c_content = find_class("com/github/yumelira/yumebox/core/bridge/Content");
    jclass c_throwable = find_class("java/lang/Throwable");
    jclass c_unit = find_class("kotlin/Unit");

    m_tun_interface_mark_socket = find_method(c_tun_interface, "markSocket",
                                              "(I)V");
    m_tun_interface_query_socket_owner = find_method(c_tun_interface, "querySocketOwner",
                                                     "(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    m_completable_complete = find_method(c_completable, "complete",
                                         "(Ljava/lang/Object;)Z");
    m_fetch_callback_report = find_method(c_fetch_callback, "report",
                                          "(Ljava/lang/String;)V");
    m_fetch_callback_complete = find_method(c_fetch_callback, "complete",
                                            "(Ljava/lang/String;)V");
    m_completable_complete_exceptionally = find_method(c_completable, "completeExceptionally",
                                                       "(Ljava/lang/Throwable;)Z");
    m_logcat_interface_received = find_method(c_logcat_interface, "received",
                                              "(Ljava/lang/String;)V");
    m_connection_close_interface_received = find_method(c_connection_close_interface, "received",
                                                        "(Ljava/lang/String;)V");
    m_connection_join_interface_received = find_method(c_connection_join_interface, "received",
                                                       "(Ljava/lang/String;)V");
    m_traffic_update_interface_received = find_method(c_traffic_update_interface, "received",
                                                       "(Ljava/lang/String;)V");
    m_traffic_update_packed_interface_received = find_method(c_traffic_update_packed_interface, "received",
                                                             "(JJJJ)V");
    m_clash_exception = find_method(_c_clash_exception, "<init>",
                                    "(Ljava/lang/String;)V");
    m_get_message = find_method(c_throwable, "getMessage",
                                "()Ljava/lang/String;");
    m_open = env->GetStaticMethodID(_c_content, "open",
                                    "(Ljava/lang/String;)I");

    o_unit = env->GetStaticObjectField(c_unit,
                                       env->GetStaticFieldID(c_unit, "INSTANCE",
                                                             "Lkotlin/Unit;"));

    c_clash_exception = (jclass) new_global(_c_clash_exception);
    c_content = (jclass) new_global(_c_content);
    o_unit = new_global(o_unit);

    register_bridge_callbacks();
}

} // extern "C"
