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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumelira.yumebox.presentation.component.BottomBarContent
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarHazeState
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarHazeStyle
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarScrollBehavior
import com.github.yumelira.yumebox.presentation.component.LocalHandlePageChange
import com.github.yumelira.yumebox.presentation.component.LocalMainPagerState
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.LocalPagerState
import com.github.yumelira.yumebox.presentation.component.MainPagerState
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.rememberBottomBarReservedHeight
import com.github.yumelira.yumebox.presentation.component.rememberBottomBarScrollBehavior
import com.github.yumelira.yumebox.presentation.component.rememberMainPagerFlingBehavior
import com.github.yumelira.yumebox.presentation.component.rememberMainPagerState
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.screen.ProxyPager
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
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
import dev.chrisbanes.haze.hazeSource
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(navigator: Navigator, initialPage: Int = 0) {
    val initialMainPage = initialPage.coerceIn(0, 3)
    val pagerState = rememberPagerState(initialPage = initialMainPage, pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pagerState)
    val hazeState = remember { HazeState() }

    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val bottomBarAutoHideEnabled by appSettingsViewModel.bottomBarAutoHide.state.collectAsState()
    val topBarBlurEnabled by appSettingsViewModel.topBarBlurEnabled.state.collectAsState()
    val classicHomeEnabled by appSettingsViewModel.classicHomeEnabled.state.collectAsState()
    val moeWallpaperUri by appSettingsViewModel.moeWallpaperUri.state.collectAsState()
    val moeWallpaperZoom by appSettingsViewModel.moeWallpaperZoom.state.collectAsState()
    val moeWallpaperBiasX by appSettingsViewModel.moeWallpaperBiasX.state.collectAsState()
    val moeWallpaperBiasY by appSettingsViewModel.moeWallpaperBiasY.state.collectAsState()
    val bottomBarScrollBehavior =
        rememberBottomBarScrollBehavior(autoHideEnabled = bottomBarAutoHideEnabled)
    val pagerFlingBehavior = rememberMainPagerFlingBehavior(mainPagerState.pagerState)
    var settledMainPage by remember { mutableIntStateOf(initialMainPage) }
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
        remember(classicHomeEnabled, settledMainPage, mainPagerState.selectedPage) {
            derivedStateOf {
                when {
                    classicHomeEnabled -> true
                    mainPagerState.selectedPage == 0 -> false
                    settledMainPage != 0 -> true
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

    LaunchedEffect(mainPagerState.pagerState.currentPage) { mainPagerState.syncPage() }

    LaunchedEffect(
        mainPagerState.pagerState.currentPage,
        mainPagerState.pagerState.isScrollInProgress,
    ) {
        if (!mainPagerState.pagerState.isScrollInProgress) {
            settledMainPage = mainPagerState.pagerState.currentPage
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
        remember(mainPagerState) { { targetPage -> mainPagerState.animateToPage(targetPage) } }

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
                route?.let { navigator.push(it) }
            }
        }
        MainActivity.clearPendingDeepLink()
    }

    MainScreenBackHandler(mainPagerState = mainPagerState)

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalPagerState provides mainPagerState.pagerState,
        LocalMainPagerState provides mainPagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalBottomBarScrollBehavior provides bottomBarScrollBehavior,
        LocalBottomBarHazeState provides if (topBarBlurEnabled) hazeState else null,
        LocalBottomBarHazeStyle provides if (topBarBlurEnabled) bottomBarHazeStyle else null,
    ) {
        Scaffold { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                val layoutDirection = LocalLayoutDirection.current
                val visibleBottomBarReservedHeight = rememberBottomBarReservedHeight()
                val bottomBarReservedHeight by
                    animateDpAsState(
                        targetValue =
                            if (bottomBarVisible) visibleBottomBarReservedHeight else UiDp.dp0,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        label = "main_bottom_bar_reserved_height",
                    )
                val mainInnerPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + bottomBarReservedHeight,
                        start =
                            WindowInsets.systemBars
                                .asPaddingValues()
                                .calculateStartPadding(layoutDirection),
                        end =
                            WindowInsets.systemBars
                                .asPaddingValues()
                                .calculateEndPadding(layoutDirection),
                    )
                HorizontalPager(
                    modifier =
                        Modifier.fillMaxSize().let { modifier ->
                            if (topBarBlurEnabled) {
                                modifier.hazeSource(state = hazeState)
                            } else {
                                modifier
                            }
                        },
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = 2,
                    flingBehavior = pagerFlingBehavior,
                    userScrollEnabled = true,
                    overscrollEffect = null,
                    pageNestedScrollConnection =
                        PagerDefaults.pageNestedScrollConnection(
                            state = mainPagerState.pagerState,
                            orientation = Orientation.Horizontal,
                        ),
                ) { page ->
                    MainRootPageContent(
                        page = page,
                        mainInnerPadding = mainInnerPadding,
                        classicHomeEnabled = classicHomeEnabled,
                        moeWallpaperUri = moeWallpaperUri,
                        moeWallpaperZoom = moeWallpaperZoom,
                        moeWallpaperBiasX = moeWallpaperBiasX,
                        moeWallpaperBiasY = moeWallpaperBiasY,
                        navigator = navigator,
                        homePageProgress = homeVisibility,
                        selectedPage = settledMainPage,
                    )
                }

                BottomEdgeScrim(
                    color = bottomBarScrimColor,
                    visible = bottomBarVisible && bottomBarScrollBehavior.isBottomBarVisible,
                    height = visibleBottomBarReservedHeight + UiDp.dp28,
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    BottomBarContent(isVisible = bottomBarVisible)
                }
            }
        }
    }
}

// Edge fade behind the floating nav bar: page content scrolling into the bottom of the screen
// dissolves into the page background (white in light theme) instead of colliding with the bar
// and the system navigation area. Transparent at the top, opaque at the screen edge; purely
// decorative, so it never intercepts touch input.
@Composable
private fun BoxScope.BottomEdgeScrim(color: Color, visible: Boolean, height: Dp) {
    val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "main_bottom_edge_scrim",
        )
    if (alpha <= 0f) return
    Box(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.verticalGradient(
                        0f to color.copy(alpha = 0f),
                        0.25f to color.copy(alpha = 0.55f),
                        0.55f to color.copy(alpha = 0.88f),
                        1f to color,
                    )
                )
    )
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
private fun MainRootPageContent(
    page: Int,
    mainInnerPadding: PaddingValues,
    classicHomeEnabled: Boolean,
    moeWallpaperUri: String,
    moeWallpaperZoom: Float,
    moeWallpaperBiasX: Float,
    moeWallpaperBiasY: Float,
    navigator: Navigator,
    homePageProgress: Float,
    selectedPage: Int,
) {
    when (page) {
        0 -> {
            if (classicHomeEnabled) {
                HomePager(
                    mainInnerPadding = mainInnerPadding,
                    isActive = selectedPage == 0,
                )
            } else {
                MoeHomePage(
                    mainInnerPadding = mainInnerPadding,
                    wallpaperUri = moeWallpaperUri,
                    wallpaperZoom = moeWallpaperZoom,
                    wallpaperBiasX = moeWallpaperBiasX,
                    wallpaperBiasY = moeWallpaperBiasY,
                    isActive = selectedPage == 0,
                    pageProgress = homePageProgress,
                )
            }
        }

        1 ->
            ProxyPager(
                mainInnerPadding = mainInnerPadding,
                onNavigateToProviders = {
                    navigator.push(Route.Providers)
                },
                isActive = selectedPage == 1,
            )

        2 -> ProfilesPager(mainInnerPadding)
        3 -> SettingPager(mainInnerPadding)
    }
}
