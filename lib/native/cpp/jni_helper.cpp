#include "jni_helper.h"
#include <cstring>
#include <cstdlib>

namespace {
    jclass c_string = nullptr;
    jmethodID m_new_string = nullptr;
    jmethodID m_get_bytes = nullptr;
}

void initialize_jni(JavaVM* vm, JNIEnv* env) {
    jni::initialize_global_vm(vm);

    jclass cls = env->FindClass("java/lang/String");
    c_string = (jclass)env->NewGlobalRef(cls);
    env->DeleteLocalRef(cls);

    m_new_string = env->GetMethodID(c_string, "<init>", "([B)V");
    m_get_bytes = env->GetMethodID(c_string, "getBytes", "()[B");
}

char* jni_get_string(JNIEnv* env, jstring str) {
    if (!env || !str) return nullptr;

    jbyteArray arr = (jbyteArray)env->CallObjectMethod(str, m_get_bytes);
    if (!arr) { env->ExceptionClear(); return nullptr; }

    jsize len = env->GetArrayLength(arr);
    char* content = static_cast<char*>(malloc(len + 1));
    if (!content) { env->DeleteLocalRef(arr); return nullptr; }

    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(content));
    content[len] = '\0';
    env->DeleteLocalRef(arr);
    return content;
}

jstring jni_new_string(JNIEnv* env, const char* str) {
    if (!env || !str) return nullptr;

    size_t len = strlen(str); /* flawfinder: ignore - str is a NUL-terminated C string by contract (Go core / literal callers) */
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(len));
    if (!arr) return nullptr;

    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(str));
    jstring result = (jstring)env->NewObject(c_string, m_new_string, arr);
    env->DeleteLocalRef(arr);
    return result;
}

int jni_catch_exception(JNIEnv* env) {
    if (!env) return 0;
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return 1;
    }
    return 0;
}

void jni_attach_thread(struct _scoped_jni* jni) {
    JavaVM* vm = jni::get_global_vm();
    if (!vm || !jni) return;

    if (vm->GetEnv(reinterpret_cast<void**>(&jni->env), JNI_VERSION_1_6) == JNI_OK) {
        jni->require_release = 0;
        return;
    }

    if (vm->AttachCurrentThread(&jni->env, nullptr) == JNI_OK) {
        jni->require_release = 1;
    }
}

void jni_detach_thread(struct _scoped_jni* jni) {
    if (!jni || !jni->require_release) return;
    JavaVM* vm = jni::get_global_vm();
    if (vm) vm->DetachCurrentThread();
}

void release_string(char** str) {
    if (str && *str) { free(*str); *str = nullptr; }
}