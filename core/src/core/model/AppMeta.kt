package com.github.yumelira.yumebox.core.model

data class AppIdentity(
    val appKey: String,
    val packageName: String? = null,
    val appName: String,
)

enum class ThemeMode {
    Auto,
    Light,
    Dark,
}

enum class AppLanguage {
    System,
    Zh,
    En,
}

enum class AppColorTheme {
    ClassicMonochrome
}
