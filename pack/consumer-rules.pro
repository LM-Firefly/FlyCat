# Loader classes run before the main classloader is set up.
# Keep all loader classes and their Kotlin runtime dependencies.
-keep class dev.flycat.loader.** { *; }

# Ensure kotlin.jvm.internal.Intrinsics is not stripped by R8 full mode
# even when -assumenosideeffects removes its method calls.
-keep class kotlin.jvm.internal.Intrinsics { *; }
