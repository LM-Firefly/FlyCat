-keep class com.github.lmfirefly.flycat.App { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }

# language-textmate uses the native Oniguruma backend; Joni is an optional fallback.
-dontwarn org.joni.**

# Shizuku / Sui: the API uses hidden-API reflection and the UserService is instantiated by name.
# moe.shizuku.* holds the AIDL interfaces; renaming them rewrites the binder descriptors (via
# -adaptclassstrings) and every service call then fails interface checks.
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.github.lmfirefly.flycat.runtime.service.privilege.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**
-dontwarn moe.shizuku.**

# priv-kit: the Privileged Server / UserService mains are launched by class name from the native
# starter, and the binder handshake matches protocol descriptors by string. Renaming breaks the
# cross-process interface checks (same constraint as the old -adaptclassstrings note).
-keep class priv.kit.** { *; }
-dontwarn priv.kit.**
# ========================================
# R8 Configuration: Shrink, Optimize, Obfuscate
# ========================================
-repackageclasses
-adaptclassstrings

# ========================================
# WorkManager (Room-based internal DB)
# ========================================
# WorkManager 2.10+ uses Room for WorkDatabase; R8 strips the no-arg constructor that Room instantiates via reflection.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# ========================================
# Native / Android Core
# ========================================
# JNI keep rules are exported by :core, which owns the bridge contract.

# Reflection bridge: LogRecordGateway.writeRuntimeLog calls this via reflection
-keep class com.github.lmfirefly.flycat.runtime.service.LogRecordService {
    public static void writeLog(java.lang.String);
}

# Reflection bridge: createLogRecordGateway() instantiates this implementation by class name
-keep class com.github.lmfirefly.flycat.runtime.service.LogRecordServiceGateway {
    public <init>();
    *;
}

# Reflection bridge: ServiceClient.connect() instantiates these by class name via Class.forName()
-keep class com.github.lmfirefly.flycat.runtime.service.ClashManager {
    public <init>(android.content.Context);
    *;
}

-keep class com.github.lmfirefly.flycat.runtime.service.profile.ProfileManager {
    public <init>(android.content.Context);
    *;
}

# ========================================
# Kotlin / Serialization (targeted)
# ========================================
# kotlinx.serialization and the Android optimized default configuration supply the required serializer and attribute rules. Keep only the unrelated warning suppression required by animal-sniffer's optional JRE annotation.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Optional micro-optimization: strip Kotlin runtime null-check helpers
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
}

# Keep Intrinsics class itself — R8 would otherwise remove it after
# -assumenosideeffects strips most of its call sites.
-keep class kotlin.jvm.internal.Intrinsics { *; }

# ========================================
# Shizuku UserService
# ========================================
# Instantiated by ComponentName(packageName, className); Shizuku API classes use reflection.
# moe.shizuku.* holds the AIDL interfaces; renaming rewrites the binder descriptors (via
# -adaptclassstrings) and Shizuku calls fail their interface checks.
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.github.lmfirefly.flycat.runtime.service.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**
-dontwarn moe.shizuku.**

# ========================================
# priv-kit privileged runtime
# ========================================
# The Privileged Server / UserService mains are launched by class name from the native starter,
# and the binder handshake matches protocol descriptors by string. Renaming breaks the
# cross-process interface checks (same constraint as the old -adaptclassstrings note).
-keep class priv.kit.** { *; }
-dontwarn priv.kit.**

# Keep Timber log calls so they are visible in logcat for release builds
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }
-keep class timber.log.Timber$DebugTree { *; }
-keep class * extends timber.log.Timber$Tree { *; }

# ========================================
# Javet / Native JS
# ========================================
-keep class com.caoccao.javet.** { *; }
-keepclassmembers class * {
    @com.caoccao.javet.annotations.V8Function <methods>;
    @com.caoccao.javet.annotations.V8Property <methods>;
}

# JMX classes not available on Android
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.management.NotificationListener

# Compression / parsing optional classes
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**
-dontwarn org.brotli.dec.**

# Misc missing classes on Android / desugared env
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.lang.model.element.Modifier

# SnakeYAML on Android may reference JavaBeans introspection classes which are absent on Android.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

-keepclassmembernames class **.R$* { *; }
-keepclassmembernames class **.R { *; }

# ========================================
# ML Kit (Google) - Component registration and native barcode bridge
# ========================================
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep @com.google.firebase.components.ComponentRegistrar class * { *; }
-keep class com.google.mlkit.vision.barcode.BarcodeScannerOptions { *; }
-keep class com.google.mlkit.vision.barcode.common.Barcode { *; }
-keep class com.google.android.libraries.barhopper.** { *; }

# ========================================
# Koin Dependency Injection
# ========================================
-keepnames class * extends org.koin.core.component.KoinComponent
-keepclassmembers class * {
    @org.koin.core.inject *** inject(...);
}
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

# JNI in lib/native/rust/src/jni/mod.rs resolves these exact symbols.
-keep class kotlin.Unit {
    public static final kotlin.Unit INSTANCE;
}
-keep,allowoptimization interface kotlinx.coroutines.CompletableDeferred {
    boolean complete(java.lang.Object);
    boolean completeExceptionally(java.lang.Throwable);
}
