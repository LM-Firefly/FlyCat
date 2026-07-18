# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in core/consumer-rules.pro
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI bridge callbacks use FindClass/GetMethodID with these exact names.
-keep class com.github.lmfirefly.flycat.core.bridge.** { *; }

# JNI in lib/native/cpp/bridge_callbacks.cpp resolves these exact symbols.
-keep class kotlin.Unit {
    public static final kotlin.Unit INSTANCE;
}
-keep,allowoptimization interface kotlinx.coroutines.CompletableDeferred {
    boolean complete(java.lang.Object);
    boolean completeExceptionally(java.lang.Throwable);
}
