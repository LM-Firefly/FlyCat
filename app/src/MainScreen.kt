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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.NavigationEventInfo
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.feature.proxy.presentation.screen.ProxyPager
import com.github.yumelira.yumebox.platform.util.openUrl
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
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.webview.WebViewUtils
import com.github.yumelira.yumebox.presentation.webview.WebViewUtils.getPanelUrl
import com.github.yumelira.yumebox.screen.home.HomePager
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.moe.calculateHomeVisibility
import com.github.yumelira.yumebox.screen.moe.MoeHomePage
import com.github.yumelira.yumebox.screen.profiles.ProfilesPager
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import com.github.yumelira.yumebox.screen.settings.SettingPager
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(navigator: Navigator, initialPage: Int = 0) {
    val initialMainPage = initialPage.coerceIn(0, 3)
    val pagerState = rememberPagerState(initialPage = initialMainPage, pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pagerState)
    val hazeState = remember { HazeState() }

    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val featureStore = koinInject<FeatureStore>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val mainScreenSettings by appSettingsViewModel.mainScreenSettings.collectAsStateWithLifecycle()
    val selectedPanelType by featureStore.selectedPanelType.state.collectAsStateWithLifecycle()
    val panelOpenMode by featureStore.panelOpenMode.state.collectAsStateWithLifecycle()
    val bottomBarAutoHideEnabled by appSettingsViewModel.bottomBarAutoHide.state.collectAsStateWithLifecycle()
    val topBarBlurEnabled by appSettingsViewModel.topBarBlurEnabled.state.collectAsStateWithLifecycle()
    val classicHomeEnabled by appSettingsViewModel.classicHomeEnabled.state.collectAsStateWithLifecycle()
    val moeWallpaperUri by appSettingsViewModel.moeWallpaperUri.state.collectAsStateWithLifecycle()
    val moeWallpaperZoom by appSettingsViewModel.moeWallpaperZoom.state.collectAsStateWithLifecycle()
    val moeWallpaperBiasX by appSettingsViewModel.moeWallpaperBiasX.state.collectAsStateWithLifecycle()
    val moeWallpaperBiasY by appSettingsViewModel.moeWallpaperBiasY.state.collectAsStateWithLifecycle()
    val bottomBarScrollBehavior =
        rememberBottomBarScrollBehavior(autoHideEnabled = mainScreenSettings.bottomBarAutoHide)
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
                if (classicHomeEnabled) {
                    true
                } else if (mainPagerState.selectedPage == 0) {
                    false
                } else {
                    settledMainPage != 0
                }
            }
        }
    val bottomBarBackground =
        if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
            MiuixTheme.colorScheme.surface
        } else {
            MiuixTheme.colorScheme.background
        }
    val opacity = AppTheme.opacity
    val bottomBarHazeStyle =
        remember(bottomBarBackground) {
            HazeBlurStyle(
                backgroundColor = bottomBarBackground.copy(alpha = opacity.subtle),
                colorEffects = listOf(HazeColorEffect.tint(bottomBarBackground.copy(alpha = opacity.softOverlay))),
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
                val visibleBottomBarReservedHeight =
                    rememberBottomBarReservedHeight()
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
                            if (mainScreenSettings.topBarBlurEnabled) {
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
                        selectedPanelType = selectedPanelType,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    BottomBarContent(
                        isVisible = bottomBarVisible,
                    )
                }
            }
        }
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
    selectedPanelType: Int = 0,
) {
    when (page) {
        0 -> {
            if (classicHomeEnabled) {
                HomePager(mainInnerPadding = mainInnerPadding, isActive = selectedPage == 0)
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

        1 -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            ProxyPager(
                mainInnerPadding = mainInnerPadding,
                onNavigateToProviders = {
                    navigator.push(Route.Providers)
                },
                onOpenDashboard = {
                    val panelUrl = WebViewUtils.getPanelUrl(selectedPanelType)
                    if (panelUrl.isNotBlank()) {
                        WebViewActivity.start(context, panelUrl)
                    }
                },
                isActive = selectedPage == 1,
            )
        }

        2 -> ProfilesPager(mainInnerPadding)
        3 -> SettingPager(mainInnerPadding)
    }
}
