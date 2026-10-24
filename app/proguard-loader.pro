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
