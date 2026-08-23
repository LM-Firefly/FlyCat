/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.androidPredictiveBackAnimatableV1
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.essenty.backhandler.BackHandler
import com.github.lmfirefly.flycat.R
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.feature.about.presentation.screen.AboutScreen
import com.github.lmfirefly.flycat.feature.about.presentation.screen.OpenSourceLicensesScreen
import com.github.lmfirefly.flycat.feature.dashboard.presentation.screen.ConnectionScreen
import com.github.lmfirefly.flycat.feature.dashboard.presentation.screen.CustomRoutingRoute
import com.github.lmfirefly.flycat.feature.dashboard.presentation.screen.RulesScreen
import com.github.lmfirefly.flycat.feature.dashboard.presentation.screen.TrafficStatisticsContent
import com.github.lmfirefly.flycat.feature.log.presentation.screen.LogDetailScreen
import com.github.lmfirefly.flycat.feature.log.presentation.screen.LogScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.AccessControlScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.AppSettingsScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.EbpfServiceOptionsScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.MetaFeatureScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.MoeWallpaperCropScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.NetworkSettingsScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.TunServiceOptionsScreen
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.VpnServiceOptionsScreen
import com.github.lmfirefly.flycat.presentation.component.layout.KeyValueEditorScreen
import com.github.lmfirefly.flycat.presentation.component.layout.StringListEditorScreen
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalNavigator
import com.github.lmfirefly.flycat.presentation.screen.MainScreen
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.util.rememberWindowLayoutMode
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

@Composable
fun RouteContent(route: Route, navigator: Navigator, mainPage: Int = 0) {
    when (route) {
        is Route.AppStart -> MainScreen(navigator, initialPage = 0)
        is Route.Main -> MainScreen(navigator, initialPage = mainPage)
        is Route.MoeWallpaperCrop -> MoeWallpaperCropScreen(
            navigator = navigator,
            wallpaperUri = route.wallpaperUri,
            initialZoom = route.initialZoom,
            initialBiasX = route.initialBiasX,
            initialBiasY = route.initialBiasY,
        )
        Route.AppSettings -> AppSettingsScreen(navigator)
        Route.NetworkSettings -> NetworkSettingsScreen(navigator)
        Route.VpnServiceOptions -> VpnServiceOptionsScreen(navigator)
        Route.TunServiceOptions -> TunServiceOptionsScreen(navigator)
        Route.EbpfServiceOptions -> EbpfServiceOptionsScreen(navigator)
        Route.AccessControl -> AccessControlScreen(navigator)
        Route.MetaFeature -> MetaFeatureScreen(navigator)
        Route.Connection -> ConnectionScreen(navigator)
        Route.TrafficStatistics -> TrafficStatisticsContent(onBack = { navigator.pop() })
        Route.Log -> LogScreen(navigator)
        Route.Rules -> RulesScreen(navigator)
        Route.About -> AboutScreen(navigator, appIconResId = R.drawable.flycat)
        Route.OpenSourceLicenses -> OpenSourceLicensesScreen(navigator, librariesResId = R.raw.aboutlibraries)
        Route.Override -> OverrideScreen(navigator)
        Route.OverrideConfigPreview -> OverrideConfigPreviewRoute(navigator)
        Route.Providers -> ProvidersScreen(navigator)
        Route.ProviderFilePreview -> ProviderFilePreviewRoute(navigator)
        Route.Feature -> FeatureScreen(navigator)
        Route.CustomRouting -> CustomRoutingRoute(navigator)
        Route.StringListEditor -> StringListEditorScreen(navigator)
        Route.KeyValueEditor -> KeyValueEditorScreen(navigator)
        is Route.LogDetail -> LogDetailScreen(navigator, fileName = route.fileName)
    }
}

// ── Decompose animation (matches FlyCat: fade 300 + slide 400 + scale 500) ──

@OptIn(ExperimentalDecomposeApi::class)
private fun <T : Any> yumeAnimation(
    backHandler: BackHandler,
    onBack: () -> Unit,
): StackAnimation<Any, T> =
    predictiveBackAnimation(
        backHandler = backHandler,
        onBack = onBack,
        fallbackAnimation = stackAnimation(fade(tween(AnimationSpecs.DURATION_NAV_FADE)) + slide(tween(AnimationSpecs.DURATION_NAV_SLIDE)) + scale(tween(AnimationSpecs.DURATION_NAV_SCALE))),
        selector = { event, _, _ -> androidPredictiveBackAnimatableV1(event) },
    )

// ── AppNavigationComponent ──

class AppNavigationComponent(
    val componentContext: ComponentContext,
    val predictiveBackEnabledAtLaunch: Boolean,
) {
    private val mainPageState = mutableIntStateOf(0)
    private var boundMainRoute: Route.Main? = null
    val navigator = Navigator(mutableStateListOf<Any>(Route.AppStart))
    // 横竖屏切换时暂存待迁移到 detailNavigator 的二级路由
    var pendingDetailRoute by mutableStateOf<Route?>(null)
    internal val childStack =
        componentContext.childStack(
            source = navigator.navigation,
            initialConfiguration = Route.AppStart,
            serializer = null,
            handleBackButton = false,
        ) { rawRoute, _ ->
            RouteChild(rawRoute as Route, navigator, this)
        }
    internal fun bindMainRoute(route: Route.Main) {
        if (boundMainRoute != route) {
            mainPageState.intValue = route.initialPage
            boundMainRoute = route
        }
    }
    internal fun updateMainPage(page: Int) {
        mainPageState.intValue = page
    }
    internal val mainPage: Int get() = mainPageState.intValue
}

// ── RouteChild ──

internal class RouteChild(
    private val route: Route,
    private val navigator: Navigator,
    private val navigationComponent: AppNavigationComponent,
) {
    init {
        if (route is Route.Main) navigationComponent.bindMainRoute(route)
    }
    @Composable
    fun Content() {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            when (route) {
                is Route.Main, is Route.AppStart -> MainScreen(
                    navigator = navigator,
                    initialPage = if (route is Route.Main) navigationComponent.mainPage else 0,
                    pendingDetailRoute = navigationComponent.pendingDetailRoute,
                    onConsumePendingDetailRoute = { navigationComponent.pendingDetailRoute = null },
                )
                is Route.MoeWallpaperCrop -> MoeWallpaperCropScreen(
                    navigator = navigator,
                    wallpaperUri = route.wallpaperUri,
                    initialZoom = route.initialZoom,
                    initialBiasX = route.initialBiasX,
                    initialBiasY = route.initialBiasY,
                )
                else -> RouteContent(route, navigator, navigationComponent.mainPage)
            }
        }
    }
}

// ── AppNavContainer (main navigation host) ──

@Composable
fun AppNavContainer(component: AppNavigationComponent) {
    val navigator = component.navigator
    val predictiveBackEnabled = component.predictiveBackEnabledAtLaunch
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_EXPRESSION")
    val commitBack: () -> Unit = remember(navigator, scope) {
        {
            scope.launch {
                withFrameNanos { }
                navigator.pop()
            }
            Unit
        }
    }
    val stack by component.childStack.subscribeAsState()
    SideEffect {
        navigator.syncBackStack(stack.items.map { it.configuration })
    }

    // 横竖屏切换时自动迁移二级路由：Split 模式下根 navigator 有二级路由时迁移到 detailNavigator
    val windowLayoutMode = rememberWindowLayoutMode()
    LaunchedEffect(windowLayoutMode) {
        if (windowLayoutMode.usesSplitShell && navigator.backStack.size > 1) {
            val topRoute = navigator.backStack.lastOrNull()
            if (topRoute is Route && topRoute !is Route.AppStart && topRoute !is Route.Main) {
                component.pendingDetailRoute = topRoute
                // 弹出所有二级路由，使 MainScreen 回到栈顶
                repeat(navigator.backStack.size - 1) { navigator.pop() }
            }
        }
    }

    if (!predictiveBackEnabled) {
        BackHandler(enabled = stack.backStack.isNotEmpty(), onBack = commitBack)
    }
    val animation: StackAnimation<Any, RouteChild> = remember(component) {
        if (predictiveBackEnabled) {
            yumeAnimation(component.componentContext.backHandler, commitBack)
        } else {
            stackAnimation(fade(tween(AnimationSpecs.DURATION_NAV_FADE)) + slide(tween(AnimationSpecs.DURATION_NAV_SLIDE)) + scale(tween(AnimationSpecs.DURATION_NAV_SCALE)))
        }
    }
    Children(
        stack = stack,
        modifier = Modifier.fillMaxSize(),
        animation = animation,
    ) { child ->
        child.instance.Content()
    }
}
