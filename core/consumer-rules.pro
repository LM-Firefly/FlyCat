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
-keep class com.github.yumelira.yumebox.core.bridge.** { *; }

# JNI in lib/native/compat/libcompat.c resolves these exact symbols.
-keep class kotlin.Unit {
    public static final kotlin.Unit INSTANCE;
}
# Keep the whole interface: member signatures vary across coroutines versions (R8 unmatched-member noise).
-keep,allowoptimization interface kotlinx.coroutines.CompletableDeferred { *; }
-keepclassmembers class * implements kotlinx.coroutines.CompletableDeferred {
    public boolean complete(java.lang.Object);
    public boolean completeExceptionally(java.lang.Throwable);
}
