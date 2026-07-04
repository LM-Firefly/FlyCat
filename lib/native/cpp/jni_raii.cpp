#include "jni_raii.hpp"

namespace jni {

namespace {
    JavaVM* g_vm = nullptr;
}

ScopedJString::ScopedJString(JNIEnv* env, jstring str) {
    if (!env || !str) return;

    jclass cls = env->FindClass("java/lang/String");
    if (!cls) { env->ExceptionClear(); return; }

    jmethodID mid = env->GetMethodID(cls, "getBytes", "()[B");
    env->DeleteLocalRef(cls);
    if (!mid) { env->ExceptionClear(); return; }

    jbyteArray arr = (jbyteArray)env->CallObjectMethod(str, mid);
    if (!arr) { env->ExceptionClear(); return; }

    jsize len = env->GetArrayLength(arr);
    if (len < 0) { env->DeleteLocalRef(arr); return; }

    // std::make_unique throws std::bad_alloc on failure; it never returns null.
    data_ = std::make_unique<char[]>(len + 1);

    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(data_.get()));
    data_[len] = '\0';
    length_ = static_cast<size_t>(len);
    env->DeleteLocalRef(arr);
}

ScopedGlobalRef::ScopedGlobalRef(JNIEnv* env, jobject obj) : env_(env) {
    if (env && obj) ref_ = env->NewGlobalRef(obj);
}

void ScopedGlobalRef::reset() noexcept {
    if (env_ && ref_) { env_->DeleteGlobalRef(ref_); ref_ = nullptr; }
}

ScopedThreadAttachment::ScopedThreadAttachment(JavaVM* vm) : vm_(vm) {
    if (!vm_) return;
    if (vm_->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6) == JNI_OK) {
        attached_ = false;
        return;
    }
    if (vm_->AttachCurrentThread(&env_, nullptr) == JNI_OK) attached_ = true;
}

ScopedThreadAttachment::~ScopedThreadAttachment() {
    if (attached_ && vm_) vm_->DetachCurrentThread();
}

JavaVM* get_global_vm() noexcept { return g_vm; }
void initialize_global_vm(JavaVM* vm) noexcept { g_vm = vm; }

} // namespace jni