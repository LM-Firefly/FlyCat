package com.github.yumelira.yumebox.core.model

data class AppIdentity(
    val appKey: String,
    val packageName: String? = null,
    val appName: String,
)
