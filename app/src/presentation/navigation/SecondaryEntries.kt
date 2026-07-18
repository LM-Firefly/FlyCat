/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.yumelira.yumebox.R
import com.github.yumelira.yumebox.feature.about.presentation.screen.AboutScreen
import com.github.yumelira.yumebox.feature.about.presentation.screen.OpenSourceLicensesScreen
import com.github.yumelira.yumebox.feature.log.presentation.screen.LogDetailScreen
import com.github.yumelira.yumebox.feature.log.presentation.screen.LogScreen
import com.github.yumelira.yumebox.feature.meta.presentation.screen.ConnectionScreen
import com.github.yumelira.yumebox.feature.meta.presentation.screen.CustomRoutingRoute
import com.github.yumelira.yumebox.feature.meta.presentation.screen.TrafficStatisticsContent
import com.github.yumelira.yumebox.feature.settings.presentation.screen.AccessControlScreen
import com.github.yumelira.yumebox.feature.settings.presentation.screen.AppSettingsScreen
import com.github.yumelira.yumebox.feature.settings.presentation.screen.MetaFeatureScreen
import com.github.yumelira.yumebox.feature.settings.presentation.screen.NetworkSettingsScreen
import com.github.yumelira.yumebox.presentation.component.KeyValueEditorScreen
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.StringListEditorScreen
import com.github.yumelira.yumebox.screen.navigation.FeatureScreen
import com.github.yumelira.yumebox.screen.navigation.OverrideConfigPreviewRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideScreen
import com.github.yumelira.yumebox.screen.navigation.ProviderFilePreviewRoute
import com.github.yumelira.yumebox.screen.navigation.ProvidersScreen

/**
 * Secondary destinations shared by the full-screen [AppNavContainer] stack and the tablet detail pane.
 *
 * Immersive flows such as [Route.MoeWallpaperCrop] stay full-screen only.
 */
fun EntryProviderScope<NavKey>.flycatSecondaryEntries(navigator: Navigator) {
    entry<Route.AppSettings> { AppSettingsScreen(navigator) }
    entry<Route.NetworkSettings> { NetworkSettingsScreen(navigator) }
    entry<Route.AccessControl> { AccessControlScreen(navigator) }
    entry<Route.MetaFeature> { MetaFeatureScreen(navigator) }
    entry<Route.Connection> { ConnectionScreen(navigator) }
    entry<Route.TrafficStatistics> { TrafficStatisticsContent(onBack = { navigator.pop() }) }
    entry<Route.Log> { LogScreen(navigator) }
    entry<Route.About> { AboutScreen(navigator, appIconResId = R.drawable.flycat) }
    entry<Route.OpenSourceLicenses> {
        OpenSourceLicensesScreen(navigator, librariesResId = R.raw.aboutlibraries)
    }
    entry<Route.Override> { OverrideScreen(navigator) }
    entry<Route.OverrideConfigPreview> { OverrideConfigPreviewRoute(navigator) }
    entry<Route.Providers> { ProvidersScreen(navigator) }
    entry<Route.ProviderFilePreview> { ProviderFilePreviewRoute(navigator) }
    entry<Route.Feature> { FeatureScreen(navigator) }
    entry<Route.CustomRouting> { CustomRoutingRoute(navigator) }
    entry<Route.StringListEditor> { StringListEditorScreen(navigator) }
    entry<Route.KeyValueEditor> { KeyValueEditorScreen(navigator) }
    entry<Route.LogDetail> { route -> LogDetailScreen(navigator, fileName = route.fileName) }
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
