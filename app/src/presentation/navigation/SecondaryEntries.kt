/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.screen.about.AboutScreen
import com.github.yumelira.yumebox.screen.about.OpenSourceLicensesScreen
import com.github.yumelira.yumebox.screen.connection.ConnectionDetailScreen
import com.github.yumelira.yumebox.screen.connection.ConnectionScreen
import com.github.yumelira.yumebox.screen.log.LogScreen
import com.github.yumelira.yumebox.screen.navigation.CustomRoutingRoute
import com.github.yumelira.yumebox.screen.navigation.FeatureScreen
import com.github.yumelira.yumebox.screen.navigation.KeyValueEditorScreen
import com.github.yumelira.yumebox.screen.navigation.OverrideConfigPreviewRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideScreen
import com.github.yumelira.yumebox.screen.navigation.ProvidersScreen
import com.github.yumelira.yumebox.screen.navigation.StringListEditorScreen
import com.github.yumelira.yumebox.screen.rules.RulesScreen
import com.github.yumelira.yumebox.screen.settings.AccessControlScreen
import com.github.yumelira.yumebox.screen.settings.AppSettingsScreen
import com.github.yumelira.yumebox.screen.settings.MetaFeatureScreen
import com.github.yumelira.yumebox.screen.settings.NetworkSettingsScreen
import com.github.yumelira.yumebox.screen.settings.TproxyServiceOptionsScreen
import com.github.yumelira.yumebox.screen.settings.TunServiceOptionsScreen
import com.github.yumelira.yumebox.screen.settings.VpnServiceOptionsScreen
import com.github.yumelira.yumebox.screen.traffic.TrafficStatisticsScreen

/**
 * Secondary destinations shared by the full-screen [AppNavContainer] stack and the tablet
 * settings detail pane.
 *
 * Immersive flows such as [Route.MoeWallpaperCrop] stay full-screen only.
 */
fun EntryProviderScope<NavKey>.yumeSecondaryEntries(navigator: Navigator) {
    entry<Route.AppSettings> { AppSettingsScreen() }
    entry<Route.NetworkSettings> { NetworkSettingsScreen(navigator) }
    entry<Route.VpnServiceOptions> { VpnServiceOptionsScreen() }
    entry<Route.TunServiceOptions> { TunServiceOptionsScreen() }
    entry<Route.TproxyServiceOptions> { TproxyServiceOptionsScreen() }
    entry<Route.AccessControl> { AccessControlScreen(navigator) }
    entry<Route.MetaFeature> { MetaFeatureScreen(navigator) }
    entry<Route.Connection> { ConnectionScreen(navigator) }
    entry<Route.ConnectionDetail> { route ->
        ConnectionDetailScreen(
            navigator = navigator,
            connectionId = route.connectionId,
        )
    }
    entry<Route.TrafficStatistics> { TrafficStatisticsScreen() }
    entry<Route.Log> { LogScreen(navigator) }
    entry<Route.Rules> { RulesScreen(navigator) }
    entry<Route.About> { AboutScreen(navigator) }
    entry<Route.OpenSourceLicenses> { OpenSourceLicensesScreen(navigator) }
    entry<Route.Override> { OverrideScreen(navigator) }
    entry<Route.OverrideConfigPreview> { OverrideConfigPreviewRoute(navigator) }
    entry<Route.Providers> { ProvidersScreen(navigator) }
    entry<Route.Feature> { FeatureScreen(navigator) }
    entry<Route.CustomRouting> { CustomRoutingRoute(navigator) }
    entry<Route.StringListEditor> { StringListEditorScreen(navigator) }
    entry<Route.KeyValueEditor> { KeyValueEditorScreen(navigator) }
}

/** First-level settings destinations that replace the tablet detail root. */
val SettingsDetailRootRoutes: Set<Route> =
    setOf(
        Route.AppSettings,
        Route.NetworkSettings,
        Route.Override,
        Route.MetaFeature,
        Route.Feature,
        Route.About,
    )
