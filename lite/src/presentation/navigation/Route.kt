package com.github.yumelira.yumebox.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Nodes : Route

    @Serializable
    data object Providers : Route

    @Serializable
    data class ImportConfig(val prefillUrl: String = "") : Route

    @Serializable
    data object VpnSettings : Route

    @Serializable
    data object AccessControl : Route

    @Serializable
    data object Log : Route

    @Serializable
    data object About : Route
}
