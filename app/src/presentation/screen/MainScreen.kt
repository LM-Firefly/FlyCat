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

package com.github.lmfirefly.flycat.presentation.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.MainActivity
import com.github.lmfirefly.flycat.WebViewActivity
import com.github.lmfirefly.flycat.core.contract.SubStoreSettings
import com.github.lmfirefly.flycat.feature.home.presentation.screen.HomePager
import com.github.lmfirefly.flycat.feature.home.presentation.screen.moe.MoeHomePage
import com.github.lmfirefly.flycat.feature.home.presentation.screen.moe.calculateHomeVisibility
import com.github.lmfirefly.flycat.feature.home.presentation.viewmodel.HomeViewModel
import com.github.lmfirefly.flycat.feature.profiles.presentation.screen.ProfilesPager
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.ProxyPager
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.ProxyShellNodeDetail
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.SettingPager
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.presentation.component.layout.DualPaneLayout
import com.github.lmfirefly.flycat.presentation.component.navigation.BottomBarContent
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalBottomBarHazeState
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalBottomBarHazeStyle
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalDetailNavigator
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalHandlePageChange
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalMainPagerState
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalNavigator
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalPagerState
import com.github.lmfirefly.flycat.presentation.component.navigation.MainPagerState
import com.github.lmfirefly.flycat.presentation.component.navigation.rememberBottomBarReservedHeight
import com.github.lmfirefly.flycat.presentation.component.navigation.rememberMainPagerFlingBehavior
import com.github.lmfirefly.flycat.presentation.component.navigation.rememberMainPagerState
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.navigation.SecondaryDetailHost
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.LocalBottomBarScrollBehavior
import com.github.lmfirefly.flycat.presentation.util.WindowLayoutMode
import com.github.lmfirefly.flycat.presentation.util.rememberBottomBarScrollBehavior
import com.github.lmfirefly.flycat.presentation.util.rememberWindowLayoutMode
import com.github.lmfirefly.flycat.presentation.webview.WebViewUtils
import com.github.lmfirefly.flycat.presentation.webview.WebViewUtils.getPanelUrl
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(
    navigator: Navigator,
    initialPage: Int = 0,
    pendingDetailRoute: Route? = null,
    onConsumePendingDetailRoute: () -> Unit = {},
) {
    val initialMainPage = initialPage.coerceIn(0, 3)
    val pagerState = rememberPagerState(initialPage = initialMainPage, pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pagerState)
    val hazeState = remember { HazeState() }
    val windowLayoutMode = rememberWindowLayoutMode()
    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val featureStore = koinInject<SubStoreSettings>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val mainScreenSettings by appSettingsViewModel.mainScreenSettings.collectAsStateWithLifecycle()
    val selectedPanelType by featureStore.selectedPanelType.state.collectAsStateWithLifecycle()
    val panelOpenMode by featureStore.panelOpenMode.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val openNetworkPanel: () -> Unit = {
        val panelUrl = WebViewUtils.getPanelUrl(selectedPanelType)
        if (panelUrl.isNotBlank()) {
            WebViewActivity.start(context, panelUrl)
        }
    }
    val bottomBarScrollBehavior = rememberBottomBarScrollBehavior(autoHideEnabled = mainScreenSettings.bottomBarAutoHide)
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
    // Floating nav bar (with the proxy FAB) shows on the classic home and every other page; the default home has its own chrome, so it stays hidden there.
    val bottomBarVisible by
        remember(mainScreenSettings.moeMainUiEnabled, settledMainPage, mainPagerState.selectedPage) {
            derivedStateOf {
                when {
                    mainScreenSettings.moeMainUiEnabled -> true
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
    // Key off the actual theme background, not isSystemInDarkTheme(): the in-app theme can disagree with the system setting, and a white scrim on a dark UI is glaring.
    val bottomBarScrimColor =
        if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
            bottomBarBackground
        } else {
            Color.White
        }
    val opacity = AppTheme.opacity
    val bottomBarHazeStyle =
        remember(bottomBarBackground) {
            HazeBlurStyle {
                backgroundColor(bottomBarBackground.copy(alpha = opacity.subtle))
                colorEffects(listOf(HazeColorEffect.tint(bottomBarBackground.copy(alpha = opacity.softOverlay))))
            }
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

    val handlePageChange: (Int) -> Unit = remember(mainPagerState) { { targetPage -> mainPagerState.animateToPage(targetPage) } }
    val usesSplitShell = windowLayoutMode.usesSplitShell
    val detailNavigator = remember { Navigator(listOf(Route.About)) }
    val detailBackStack = detailNavigator.backStack

    // 横竖屏切换时消费从根 navigator 迁移过来的二级路由
    LaunchedEffect(pendingDetailRoute) {
        if (pendingDetailRoute != null && usesSplitShell) {
            detailNavigator.replaceAll(listOf(pendingDetailRoute))
            onConsumePendingDetailRoute()
        }
    }

    val pendingDeepLink by MainActivity.pendingDeepLink.collectAsStateWithLifecycle()
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
                route?.let {
                    if (usesSplitShell) { detailNavigator.replaceAll(listOf(it)) } else { navigator.push(it) }
                }
            }
        }
        MainActivity.clearPendingDeepLink()
    }

    // 仅在从其他标签页切换到代理标签页时重置右栏，避免在代理 tab 内导航到 Providers 时被意外清栈。
    var lastSettledPage by remember { mutableIntStateOf(initialMainPage) }
    LaunchedEffect(settledMainPage) {
        val enteringProxyFromOtherTab = settledMainPage == 1 && lastSettledPage != 1
        lastSettledPage = settledMainPage
        if (enteringProxyFromOtherTab) {
            detailNavigator.replaceAll(listOf(Route.About))
        }
    }

    MainScreenBackHandler(mainPagerState = mainPagerState)

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalDetailNavigator provides if (usesSplitShell) detailNavigator else null,
        LocalPagerState provides mainPagerState.pagerState,
        LocalMainPagerState provides mainPagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalBottomBarScrollBehavior provides bottomBarScrollBehavior,
        LocalBottomBarHazeState provides if (mainScreenSettings.topBarBlurEnabled) hazeState else null,
        LocalBottomBarHazeStyle provides if (mainScreenSettings.topBarBlurEnabled) bottomBarHazeStyle else null,
    ) {
        val layoutDirection = LocalLayoutDirection.current
        @Composable
        fun mainPagerContent(innerPadding: PaddingValues, reserveBottomBar: Boolean) {
            val visibleBottomBarReservedHeight = rememberBottomBarReservedHeight()
            val bottomBarReservedHeight by
                animateDpAsState(
                    targetValue =
                        if (bottomBarVisible && reserveBottomBar) visibleBottomBarReservedHeight else UiDp.dp0,
                    animationSpec = tween(durationMillis = AnimationSpecs.DURATION_SLOW, easing = AnimationSpecs.StandardEasing),
                    label = "main_bottom_bar_reserved_height",
                )
            val mainInnerPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + bottomBarReservedHeight,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
            Box(Modifier.fillMaxSize()) {
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
                        mainScreenSettings = mainScreenSettings,
                        navigator = navigator,
                        homePageProgress = homeVisibility,
                        selectedPage = settledMainPage,
                        selectedPanelType = selectedPanelType,
                    )
                }
                if (reserveBottomBar) {
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
        if (usesSplitShell) {
            DualPaneLayout(
                left = {
                    Scaffold { leftPadding ->
                        // 仅对顶部和左右施加内边距；底部内边距交给 mainPagerContent 处理，使其 Box 延伸到手势条区域后面，BottomEdgeScrim 才能覆盖到屏幕最底部实现沉浸效果。
                        val splitPadding = PaddingValues(
                            top = leftPadding.calculateTopPadding(),
                            start = UiDp.dp0,
                            end = UiDp.dp0,
                        )
                        val bottomPadding = PaddingValues(bottom = leftPadding.calculateBottomPadding())
                        // 消费全部 systemBars 和 displayCutout，防止内部子 Scaffold 和 TopAppBar 在已由 splitPadding 处理的各侧重复叠加内边距。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(splitPadding)
                                .consumeWindowInsets(WindowInsets.systemBars)
                                .consumeWindowInsets(WindowInsets.displayCutout)
                        ) {
                            mainPagerContent(bottomPadding, reserveBottomBar = true)
                        }
                    }
                },
                right = {
                    Scaffold { rightPadding ->
                        // 顶部和右侧施加内边距；底部交给内部内容处理，保持手势条区域沉浸。
                        val splitPadding = PaddingValues(
                            top = rightPadding.calculateTopPadding(),
                            start = UiDp.dp0, // 分隔线在左侧，不是屏幕边缘
                            end = rightPadding.calculateEndPadding(layoutDirection),
                        )
                        // 消费全部 systemBars 和 displayCutout，防止内部子 Scaffold 和 TopAppBar 在已由 splitPadding 处理的各侧重复叠加内边距。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(splitPadding)
                                .consumeWindowInsets(WindowInsets.systemBars)
                                .consumeWindowInsets(WindowInsets.displayCutout)
                        ) {
                            SecondaryDetailHost(
                                navigator = detailNavigator,
                                placeholderContent = if (settledMainPage == 1) {
                                    {
                                        ProxyShellNodeDetail(
                                            mainInnerPadding = PaddingValues(),
                                            onNavigateToProviders = {
                                                detailNavigator.push(Route.Providers)
                                            },
                                            onOpenPanel = openNetworkPanel,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
                initialLeftFraction = 0.42f,
                minLeftWidth = 280.dp,
                maxLeftWidth = 420.dp,
                minRightWidth = 320.dp,
                showDivider = true,
                dividerDraggable = true,
            )
        } else {
            Scaffold { innerPadding -> mainPagerContent(innerPadding, reserveBottomBar = true) }
        }
    }
}

// ── Bottom edge gradient scrim for pager bottom bar ──
// Transparent at the top, opaque at the screen edge; purely decorative, so it never intercepts touch input.

@Composable
internal fun BoxScope.BottomEdgeScrim(color: Color, visible: Boolean, height: Dp) {
    val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = AnimationSpecs.DURATION_SLOW, easing = AnimationSpecs.StandardEasing),
            label = "main_bottom_edge_scrim",
        )
    if (alpha <= 0f) return
    Box(modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(height)
    .graphicsLayer {
        this.alpha = alpha
    }.background(
            Brush.verticalGradient(
                0f to color.copy(alpha = 0f),
                0.25f to color.copy(alpha = 0.55f),
                0.55f to color.copy(alpha = 0.88f),
                1f to color,
            )
        )
    )
}

// ── MainScreen page router ──

@Composable
internal fun MainScreenBackHandler(mainPagerState: MainPagerState) {
    val isPagerBackHandlerEnabled by
        remember(mainPagerState) { derivedStateOf { mainPagerState.selectedPage != 0 } }
    BackHandler(enabled = isPagerBackHandlerEnabled) { mainPagerState.animateToPage(0) }
}

@Composable
internal fun MainRootPageContent(
    page: Int,
    mainInnerPadding: PaddingValues,
    mainScreenSettings: AppSettingsViewModel.MainScreenSettings,
    navigator: Navigator,
    homePageProgress: Float,
    selectedPage: Int,
    selectedPanelType: Int = 0,
) {
    val detailNavigator = LocalDetailNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val openNetworkPanel: () -> Unit = {
        val panelUrl = WebViewUtils.getPanelUrl(selectedPanelType)
        if (panelUrl.isNotBlank()) {
            WebViewActivity.start(context, panelUrl)
        }
    }
    val openSecondary: (Route) -> Unit = { route ->
        if (detailNavigator != null) {
            detailNavigator.replaceAll(listOf(route))
        } else {
            navigator.push(route)
        }
    }
    when (page) {
        0 -> {
            if (mainScreenSettings.moeMainUiEnabled) {
                HomePager(
                    mainInnerPadding = mainInnerPadding,
                    onOpenDashboard = openNetworkPanel,
                    isActive = selectedPage == 0,
                )
            } else {
                MoeHomePage(
                    mainInnerPadding = mainInnerPadding,
                    wallpaperUri = mainScreenSettings.moeWallpaperUri,
                    wallpaperZoom = mainScreenSettings.moeWallpaperZoom,
                    wallpaperBiasX = mainScreenSettings.moeWallpaperBiasX,
                    wallpaperBiasY = mainScreenSettings.moeWallpaperBiasY,
                    isActive = selectedPage == 0,
                    onOpenDashboard = openNetworkPanel,
                    pageProgress = homePageProgress,
                )
            }
        }
        1 -> {
            ProxyPager(
                mainInnerPadding = mainInnerPadding,
                onNavigateToProviders = {
                    openSecondary(Route.Providers)
                },
                isActive = selectedPage == 1,
            )
        }
        2 -> ProfilesPager(mainInnerPadding)
        3 -> SettingPager(mainInnerPadding)
    }
}
