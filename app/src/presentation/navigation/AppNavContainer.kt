/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumelira.yumebox.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumelira.yumebox.MainScreen
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.screen.about.AboutScreen
import com.github.yumelira.yumebox.screen.about.OpenSourceLicensesScreen
import com.github.yumelira.yumebox.screen.connection.ConnectionScreen
import com.github.yumelira.yumebox.screen.log.LogDetailScreen
import com.github.yumelira.yumebox.screen.log.LogScreen
import com.github.yumelira.yumebox.screen.navigation.CustomRoutingRoute
import com.github.yumelira.yumebox.screen.navigation.FeatureScreen
import com.github.yumelira.yumebox.screen.navigation.KeyValueEditorScreen
import com.github.yumelira.yumebox.screen.navigation.OverrideConfigPreviewRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideScreen
import com.github.yumelira.yumebox.screen.navigation.ProvidersScreen
import com.github.yumelira.yumebox.screen.navigation.StringListEditorScreen
import com.github.yumelira.yumebox.screen.settings.AccessControlScreen
import com.github.yumelira.yumebox.screen.settings.AppSettingsScreen
import com.github.yumelira.yumebox.screen.settings.MetaFeatureScreen
import com.github.yumelira.yumebox.screen.settings.MoeWallpaperCropScreen
import com.github.yumelira.yumebox.screen.settings.NetworkSettingsScreen
import com.github.yumelira.yumebox.screen.traffic.TrafficStatisticsScreen

private const val DURATION = 340
private const val FADE_DURATION = 140
private val slideEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

private fun slideEnter(offset: (Int) -> Int): EnterTransition =
    slideInHorizontally(animationSpec = tween(DURATION, easing = slideEasing), initialOffsetX = offset) +
        fadeIn(animationSpec = tween(FADE_DURATION, easing = LinearEasing))

private fun slideExit(offset: (Int) -> Int): ExitTransition =
    slideOutHorizontally(animationSpec = tween(DURATION, easing = slideEasing), targetOffsetX = offset) +
        fadeOut(animationSpec = tween(FADE_DURATION, easing = LinearEasing))

/**
 * The app's navigation3 host. Renders the back stack through [NavDisplay] using FlyCat's original
 * horizontal slide + fade transitions (the AOSP predictive-back animation was dropped). The system
 * predictive-back gesture scrubs [NavDisplay]'s pop transition, i.e. the default slide.
 */
@Composable
fun AppNavContainer() {
    val backStack = rememberNavBackStack(Route.AppStart)
    val navigator = remember(backStack) { Navigator(backStack) }

    val entries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    NavEntryDecorator { content ->
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            content.Content()
                        }
                    },
                ),
            entryProvider =
                entryProvider {
                    entry<Route.AppStart> { AppStartScreen(navigator) }
                    entry<Route.Main> { route -> MainScreen(navigator, initialPage = route.initialPage) }
                    entry<Route.MoeWallpaperCrop> { route ->
                        MoeWallpaperCropScreen(
                            navigator = navigator,
                            wallpaperUri = route.wallpaperUri,
                            initialZoom = route.initialZoom,
                            initialBiasX = route.initialBiasX,
                            initialBiasY = route.initialBiasY,
                        )
                    }
                    entry<Route.AppSettings> { AppSettingsScreen(navigator) }
                    entry<Route.NetworkSettings> { NetworkSettingsScreen(navigator) }
                    entry<Route.AccessControl> { AccessControlScreen(navigator) }
                    entry<Route.MetaFeature> { MetaFeatureScreen(navigator) }
                    entry<Route.Connection> { ConnectionScreen(navigator) }
                    entry<Route.TrafficStatistics> { TrafficStatisticsScreen() }
                    entry<Route.Log> { LogScreen(navigator) }
                    entry<Route.About> { AboutScreen(navigator) }
                    entry<Route.OpenSourceLicenses> { OpenSourceLicensesScreen(navigator) }
                    entry<Route.Override> { OverrideScreen(navigator) }
                    entry<Route.OverrideConfigPreview> { OverrideConfigPreviewRoute(navigator) }
                    entry<Route.Providers> { ProvidersScreen(navigator) }
                    entry<Route.Feature> { FeatureScreen(navigator) }
                    entry<Route.CustomRouting> { CustomRoutingRoute(navigator) }
                    entry<Route.StringListEditor> { StringListEditorScreen(navigator) }
                    entry<Route.KeyValueEditor> { KeyValueEditorScreen(navigator) }
                    entry<Route.LogDetail> { route -> LogDetailScreen(navigator, fileName = route.fileName) }
                },
        )

    val sceneState =
        rememberSceneState(
            entries = entries,
            sceneStrategies = listOf(SinglePaneSceneStrategy()),
            sceneDecoratorStrategies = emptyList(),
            sharedTransitionScope = null,
            onBack = { navigator.pop() },
        )
    val scene = sceneState.currentScene

    val gestureState =
        rememberNavigationEventState(
            currentInfo = SceneInfo(scene),
            backInfo = sceneState.previousScenes.map { SceneInfo(it) },
        )

    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = scene.previousEntries.isNotEmpty(),
        onBackCancelled = {},
        onBackCompleted = { navigator.pop() },
    )

    NavDisplay(
        sceneState = sceneState,
        navigationEventState = gestureState,
        contentAlignment = Alignment.TopStart,
        sizeTransform = null,
        transitionSpec = {
            ContentTransform(slideEnter { it }, slideExit { -it }, sizeTransform = null)
        },
        popTransitionSpec = {
            ContentTransform(slideEnter { -it }, slideExit { it }, sizeTransform = null)
        },
        predictivePopTransitionSpec = { _ ->
            ContentTransform(slideEnter { -it }, slideExit { it }, sizeTransform = null)
        },
    )
}
