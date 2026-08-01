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
#include "libmihomo.h"

extern "C" {
void unsubscribeConnectionClose(void);
void unsubscribeConnectionJoin(void);
void subscribeTrafficUpdatePacked(void *remote);
void unsubscribeTrafficUpdate(void);
}

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

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryConnectionsOverview(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryConnectionsOverview();

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeQueryRules(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryRules();

    return new_string(response);
}

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSetRuleDisabled(JNIEnv *env, jobject thiz,
                                                                     jint index,
                                                                     jboolean disabled) {
    TRACE_METHOD();

    return (jboolean) setRuleDisabled((int) index, (int) disabled);
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

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativePatchTunnelMode(JNIEnv *env, jobject thiz,
                                                                      jstring mode) {
    TRACE_METHOD();

    scoped_string _mode = get_string(mode);

    return (jboolean) patchTunnelMode(_mode);
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

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeInspectCompiledGroupNames(JNIEnv *env, jobject thiz,
                                                                                jstring yaml_text,
                                                                                jboolean exclude_not_selectable) {
    TRACE_METHOD();

    scoped_string _yaml_text = get_string(yaml_text);

    scoped_string response = inspectCompiledGroupNames(_yaml_text, (int) exclude_not_selectable);

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

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeLogcat(JNIEnv *env, jobject thiz,
                                                                     jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeLogcat(_callback);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeConnectionClose(JNIEnv *env, jobject thiz,
                                                                               jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeConnectionClose(_callback);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeConnectionClose(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    unsubscribeConnectionClose();
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeConnectionJoin(JNIEnv *env, jobject thiz,
                                                                              jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeConnectionJoin(_callback);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeConnectionJoin(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    unsubscribeConnectionJoin();
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeTrafficUpdate(JNIEnv *env, jobject thiz,
                                                                              jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeTrafficUpdate(_callback);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeSubscribeTrafficUpdatePacked(JNIEnv *env, jobject thiz,
                                                                                    jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeTrafficUpdatePacked(_callback);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeUnsubscribeTrafficUpdate(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    unsubscribeTrafficUpdate();
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

JNIEXPORT jstring JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeConvertMrsToText(JNIEnv *env, jobject thiz, jstring filePath) {
    TRACE_METHOD();

    scoped_string path = get_string(filePath);
    scoped_string result = convertMrsToText(path);

    return new_string(result);
}


} // extern "C"
