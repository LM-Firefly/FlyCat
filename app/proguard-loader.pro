-keep class com.github.yumelira.yumebox.App { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }

# language-textmate uses the native Oniguruma backend; Joni is an optional fallback.
-dontwarn org.joni.**
