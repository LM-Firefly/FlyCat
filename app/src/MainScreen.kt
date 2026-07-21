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

package com.github.yumelira.yumebox

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.net.toUri
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumelira.yumebox.presentation.component.BottomBarDestination
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarHazeState
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarHazeStyle
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarScrollBehavior
import com.github.yumelira.yumebox.presentation.component.LocalHandlePageChange
import com.github.yumelira.yumebox.presentation.component.LocalMainPagerState
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.LocalPagerState
import com.github.yumelira.yumebox.presentation.component.MainPagerState
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.LocalDetailNavigator
import com.github.yumelira.yumebox.presentation.component.WindowLayoutMode
import com.github.yumelira.yumebox.presentation.component.rememberBottomBarScrollBehavior
import com.github.yumelira.yumebox.presentation.component.rememberMainPagerState
import com.github.yumelira.yumebox.presentation.component.rememberWindowLayoutMode
import com.github.yumelira.yumebox.presentation.navigation.Route
import androidx.navigation3.runtime.rememberNavBackStack
import com.github.yumelira.yumebox.presentation.screen.ProxyPager
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.screen.home.HomePager
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.moe.MoeHomePage
import com.github.yumelira.yumebox.screen.moe.calculateHomeVisibility
import com.github.yumelira.yumebox.screen.profiles.ProfilesPager
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import com.github.yumelira.yumebox.screen.settings.SettingPager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(navigator: Navigator, initialPage: Int = 0) {
    val windowLayoutMode = rememberWindowLayoutMode()
    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val runtimeSnapshot by homeViewModel.runtimeSnapshot.collectAsState()
    val isConfigReloading by homeViewModel.isConfigReloading.collectAsState()
    val isRemoteControllerMode by homeViewModel.isRemoteControllerMode.collectAsState()
    val showProxyDestination =
        isRemoteControllerMode ||
            isConfigReloading ||
            runtimeSnapshot.phase == RuntimePhase.Starting ||
            runtimeSnapshot.phase == RuntimePhase.Running ||
            runtimeSnapshot.phase == RuntimePhase.Stopping
    val visibleDestinations =
        remember(showProxyDestination) {
            BottomBarDestination.entries.filter { destination ->
                destination != BottomBarDestination.Proxy || showProxyDestination
            }
        }
    val initialDestination =
        BottomBarDestination.entries.getOrElse(initialPage.coerceIn(0, 3)) {
            BottomBarDestination.Home
        }
    val initialMainPage = visibleDestinations.indexOf(initialDestination).takeIf { it >= 0 } ?: 0
    val pagerState =
        rememberPagerState(
            initialPage = initialMainPage,
            pageCount = { visibleDestinations.size },
        )
    val mainPagerState = rememberMainPagerState(pagerState)
    val hazeState = remember { HazeState() }
    var previousDestinations by remember { mutableStateOf(visibleDestinations) }
    var settledDestination by remember { mutableStateOf(visibleDestinations[initialMainPage]) }

    val bottomBarAutoHideEnabled by appSettingsViewModel.bottomBarAutoHide.state.collectAsState()
    val topBarBlurEnabled by appSettingsViewModel.topBarBlurEnabled.state.collectAsState()
    val classicHomeEnabled by appSettingsViewModel.classicHomeEnabled.state.collectAsState()
    val moeWallpaperUri by appSettingsViewModel.moeWallpaperUri.state.collectAsState()
    val moeWallpaperZoom by appSettingsViewModel.moeWallpaperZoom.state.collectAsState()
    val moeWallpaperBiasX by appSettingsViewModel.moeWallpaperBiasX.state.collectAsState()
    val moeWallpaperBiasY by appSettingsViewModel.moeWallpaperBiasY.state.collectAsState()
    val bottomBarScrollBehavior =
        rememberBottomBarScrollBehavior(autoHideEnabled = bottomBarAutoHideEnabled)
    val selectedDestination by
        remember(mainPagerState, visibleDestinations) {
            derivedStateOf {
                if (previousDestinations != visibleDestinations) {
                    previousDestinations.getOrNull(mainPagerState.selectedPage)
                        ?: settledDestination
                } else {
                    visibleDestinations.getOrElse(mainPagerState.selectedPage) {
                        BottomBarDestination.Home
                    }
                }
            }
        }
    val homeVisibility by
        remember(mainPagerState) {
            derivedStateOf {
                calculateHomeVisibility(
                    currentPage = mainPagerState.pagerState.currentPage,
                    currentPageOffsetFraction = mainPagerState.pagerState.currentPageOffsetFraction,
                )
            }
        }
    // Floating nav bar (with the proxy FAB) shows on the classic home and every other page; the
    // default home has its own chrome, so it stays hidden there.
    val bottomBarVisible by
        remember(classicHomeEnabled, settledDestination, selectedDestination) {
            derivedStateOf {
                when {
                    // Dual-pane shell: still float the left-pane bottom bar when not on Moe home.
                    classicHomeEnabled -> true
                    selectedDestination == BottomBarDestination.Home -> false
                    settledDestination != BottomBarDestination.Home -> true
                    else -> false
                }
            }
        }
    val bottomBarBackground =
        MiuixTheme.colorScheme.run {
            surface.takeIf { background.luminance() < 0.5f } ?: background
        }
    // Key off the actual theme background, not isSystemInDarkTheme(): the in-app theme can
    // disagree with the system setting, and a white scrim on a dark UI is glaring.
    val bottomBarScrimColor =
        if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
            bottomBarBackground
        } else {
            Color.White
        }
    val opacity = AppTheme.opacity
    val bottomBarHazeStyle =
        remember(bottomBarBackground) {
            HazeStyle(
                backgroundColor = bottomBarBackground.copy(alpha = opacity.subtle),
                tint = HazeTint(bottomBarBackground.copy(alpha = opacity.softOverlay)),
            )
        }

    LaunchedEffect(visibleDestinations) {
        // Runtime transitions insert/remove the proxy page and move Config/Setting between physical
        // pager slots, so preserve the semantic page.
        val currentDestination =
            previousDestinations.getOrNull(mainPagerState.pagerState.currentPage) ?: settledDestination
        val targetDestination =
            currentDestination.takeIf { it in visibleDestinations } ?: BottomBarDestination.Config
        val targetPage = visibleDestinations.indexOf(targetDestination)
        if (mainPagerState.pagerState.currentPage != targetPage) {
            mainPagerState.pagerState.requestScrollToPage(targetPage)
        }
        mainPagerState.syncPage()
        settledDestination = targetDestination
        previousDestinations = visibleDestinations
    }

    LaunchedEffect(mainPagerState.pagerState.currentPage) { mainPagerState.syncPage() }

    LaunchedEffect(
        mainPagerState.pagerState.currentPage,
        mainPagerState.pagerState.isScrollInProgress,
    ) {
        if (!mainPagerState.pagerState.isScrollInProgress) {
            settledDestination =
                visibleDestinations.getOrElse(mainPagerState.pagerState.currentPage) {
                    BottomBarDestination.Home
                }
        }
    }

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            homeViewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
        }

    LaunchedEffect(homeViewModel) {
        homeViewModel.vpnPrepareIntent.collect { intent -> vpnPermissionLauncher.launch(intent) }
    }

    val handlePageChange: (Int) -> Unit =
        remember(mainPagerState, visibleDestinations) {
            { targetPage ->
                val destination =
                    BottomBarDestination.entries
                        .getOrElse(targetPage.coerceIn(0, BottomBarDestination.entries.lastIndex)) {
                            BottomBarDestination.Home
                        }
                val targetDestination =
                    destination.takeIf { it in visibleDestinations } ?: BottomBarDestination.Config
                mainPagerState.animateToPage(visibleDestinations.indexOf(targetDestination))
            }
        }

    val usesSplitShell = windowLayoutMode.usesSplitShell
    val detailBackStack = rememberNavBackStack(Route.About)
    val detailNavigator = remember(detailBackStack) { Navigator(detailBackStack) }

    // Leaving the proxy tab should not leave Providers stuck on the right pane.
    LaunchedEffect(settledDestination) {
        if (
            settledDestination != BottomBarDestination.Proxy &&
                detailBackStack.lastOrNull() is Route.Providers
        ) {
            detailNavigator.replaceAll(listOf(Route.About))
        }
    }

    val pendingDeepLink by MainActivity.pendingDeepLink.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val uri = pendingDeepLink?.toUri() ?: return@LaunchedEffect
        when (uri.host) {
            "page" ->
                when (uri.lastPathSegment) {
                    "home" -> handlePageChange(0)
                    "proxy" -> handlePageChange(1)
                    "profiles" -> handlePageChange(2)
                    "settings" -> handlePageChange(3)
                }
            "screen" -> {
                val route: Route? =
                    when (uri.lastPathSegment) {
                        "appsettings" -> Route.AppSettings
                        "network" -> Route.NetworkSettings
                        "about" -> Route.About
                        "access" -> Route.AccessControl
                        "traffic" -> Route.TrafficStatistics
                        "connection" -> Route.Connection
                        "log" -> Route.Log
                        "override" -> Route.Override
                        "providers" -> Route.Providers
                        else -> null
                    }
                route?.let { target ->
                    if (usesSplitShell) {
                        detailNavigator.replaceAll(listOf(target))
                    } else {
                        navigator.push(target)
                    }
                }
            }
        }
        MainActivity.clearPendingDeepLink()
    }

    MainScreenBackHandler(mainPagerState = mainPagerState)

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalDetailNavigator provides if (usesSplitShell) detailNavigator else null,
        LocalPagerState provides mainPagerState.pagerState,
        LocalMainPagerState provides mainPagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalBottomBarScrollBehavior provides bottomBarScrollBehavior,
        LocalBottomBarHazeState provides if (topBarBlurEnabled) hazeState else null,
        LocalBottomBarHazeStyle provides if (topBarBlurEnabled) bottomBarHazeStyle else null,
    ) {
        MainContentHost(
            usesSplitShell = usesSplitShell,
            windowLayoutMode = windowLayoutMode,
            mainPagerState = mainPagerState,
            visibleDestinations = visibleDestinations,
            previousDestinations = previousDestinations,
            settledDestination = settledDestination,
            bottomBarVisible = bottomBarVisible,
            topBarBlurEnabled = topBarBlurEnabled,
            hazeState = hazeState,
            bottomBarScrimColor = bottomBarScrimColor,
            classicHomeEnabled = classicHomeEnabled,
            moeWallpaperUri = moeWallpaperUri,
            moeWallpaperZoom = moeWallpaperZoom,
            moeWallpaperBiasX = moeWallpaperBiasX,
            moeWallpaperBiasY = moeWallpaperBiasY,
            homeVisibility = homeVisibility,
            navigator = navigator,
            detailBackStack = detailBackStack,
            detailNavigator = detailNavigator,
        )
    }
}

@Composable
private fun MainScreenBackHandler(mainPagerState: MainPagerState) {
    val isPagerBackHandlerEnabled by
        remember(mainPagerState) { derivedStateOf { mainPagerState.selectedPage != 0 } }
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )
}

@Composable
internal fun MainRootPageContent(
    destination: BottomBarDestination,
    mainInnerPadding: PaddingValues,
    classicHomeEnabled: Boolean,
    moeWallpaperUri: String,
    moeWallpaperZoom: Float,
    moeWallpaperBiasX: Float,
    moeWallpaperBiasY: Float,
    navigator: Navigator,
    homePageProgress: Float,
    selectedDestination: BottomBarDestination,
    windowLayoutMode: WindowLayoutMode,
) {
    val detailNavigator = LocalDetailNavigator.current
    val openSecondary: (Route) -> Unit = { route ->
        if (detailNavigator != null) {
            detailNavigator.replaceAll(listOf(route))
        } else {
            navigator.push(route)
        }
    }
    when (destination) {
        BottomBarDestination.Home -> {
            if (classicHomeEnabled) {
                HomePager(
                    mainInnerPadding = mainInnerPadding,
                    isActive = selectedDestination == BottomBarDestination.Home,
                )
            } else {
                MoeHomePage(
                    mainInnerPadding = mainInnerPadding,
                    wallpaperUri = moeWallpaperUri,
                    wallpaperZoom = moeWallpaperZoom,
                    wallpaperBiasX = moeWallpaperBiasX,
                    wallpaperBiasY = moeWallpaperBiasY,
                    isActive = selectedDestination == BottomBarDestination.Home,
                    pageProgress = homePageProgress,
                    windowLayoutMode = windowLayoutMode,
                )
            }
        }

        BottomBarDestination.Proxy ->
            ProxyPager(
                mainInnerPadding = mainInnerPadding,
                onNavigateToProviders = {
                    openSecondary(Route.Providers)
                },
                isActive = selectedDestination == BottomBarDestination.Proxy,
                windowLayoutMode = windowLayoutMode,
            )

        BottomBarDestination.Config -> ProfilesPager(mainInnerPadding, windowLayoutMode)
        BottomBarDestination.Setting -> SettingPager(mainInnerPadding, windowLayoutMode)
    }
}
