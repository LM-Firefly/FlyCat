/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavKey
import com.github.yumelira.yumebox.presentation.component.BottomBarContent
import com.github.yumelira.yumebox.presentation.component.BottomBarDestination
import com.github.yumelira.yumebox.presentation.component.DualPaneLayout
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarScrollBehavior
import com.github.yumelira.yumebox.presentation.component.MainPagerState
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.WindowLayoutMode
import com.github.yumelira.yumebox.presentation.component.rememberBottomBarReservedHeight
import com.github.yumelira.yumebox.presentation.component.rememberMainPagerFlingBehavior
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.navigation.SecondaryDetailHost
import com.github.yumelira.yumebox.presentation.screen.ProxyShellNodeDetail
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * Hosts the main pager (and optional tablet dual-pane shell).
 *
 * Split shell: each pane owns a Scaffold root so sheets/dialogs stay pane-local and can cover
 * the floating bottom bar on the left without spanning the full window.
 */
@Composable
internal fun MainContentHost(
    usesSplitShell: Boolean,
    windowLayoutMode: WindowLayoutMode,
    mainPagerState: MainPagerState,
    visibleDestinations: List<BottomBarDestination>,
    previousDestinations: List<BottomBarDestination>,
    settledDestination: BottomBarDestination,
    bottomBarVisible: Boolean,
    topBarBlurEnabled: Boolean,
    hazeState: HazeState,
    bottomBarScrimColor: Color,
    classicHomeEnabled: Boolean,
    moeWallpaperUri: String,
    moeWallpaperZoom: Float,
    moeWallpaperBiasX: Float,
    moeWallpaperBiasY: Float,
    homeVisibility: Float,
    navigator: Navigator,
    detailBackStack: MutableList<NavKey>,
    detailNavigator: Navigator,
) {
    val layoutDirection = LocalLayoutDirection.current
    val visibleBottomBarReservedHeight = rememberBottomBarReservedHeight()
    val bottomBarReservedHeight by
        animateDpAsState(
            targetValue = if (bottomBarVisible) visibleBottomBarReservedHeight else UiDp.dp0,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "main_bottom_bar_reserved_height",
        )
    val pagerFlingBehavior = rememberMainPagerFlingBehavior(mainPagerState.pagerState)
    val leftLayoutMode = WindowLayoutMode.Compact

    @Composable
    fun paneInnerPadding(scaffoldPadding: PaddingValues, reserveBottomBar: Boolean): PaddingValues {
        val bottomExtra = if (reserveBottomBar) bottomBarReservedHeight else UiDp.dp0
        val systemBars = WindowInsets.systemBars.asPaddingValues()
        return PaddingValues(
            top = scaffoldPadding.calculateTopPadding(),
            bottom = scaffoldPadding.calculateBottomPadding() + bottomExtra,
            start = systemBars.calculateStartPadding(layoutDirection),
            end = systemBars.calculateEndPadding(layoutDirection),
        )
    }

    if (usesSplitShell) {
        DualPaneLayout(
            left = {
                Scaffold { leftPadding ->
                    MainPagerHost(
                        layoutMode = leftLayoutMode,
                        enableUserScroll = true,
                        mainInnerPadding = paneInnerPadding(leftPadding, reserveBottomBar = true),
                        mainPagerState = mainPagerState,
                        pagerFlingBehavior = pagerFlingBehavior,
                        visibleDestinations = visibleDestinations,
                        previousDestinations = previousDestinations,
                        settledDestination = settledDestination,
                        bottomBarVisible = bottomBarVisible,
                        topBarBlurEnabled = topBarBlurEnabled,
                        hazeState = hazeState,
                        bottomBarScrimColor = bottomBarScrimColor,
                        visibleBottomBarReservedHeight = visibleBottomBarReservedHeight,
                        classicHomeEnabled = classicHomeEnabled,
                        moeWallpaperUri = moeWallpaperUri,
                        moeWallpaperZoom = moeWallpaperZoom,
                        moeWallpaperBiasX = moeWallpaperBiasX,
                        moeWallpaperBiasY = moeWallpaperBiasY,
                        homeVisibility = homeVisibility,
                        navigator = navigator,
                    )
                }
            },
            right = {
                Scaffold { rightPadding ->
                    val rightInnerPadding = paneInnerPadding(rightPadding, reserveBottomBar = false)
                    val showProxyNodes =
                        settledDestination == BottomBarDestination.Proxy &&
                            detailBackStack.lastOrNull() !is Route.Providers
                    if (showProxyNodes) {
                        ProxyShellNodeDetail(
                            mainInnerPadding = rightInnerPadding,
                            onNavigateToProviders = {
                                detailNavigator.replaceAll(listOf(Route.Providers))
                            },
                        )
                    } else {
                        SecondaryDetailHost(
                            backStack = detailBackStack,
                            navigator = detailNavigator,
                        )
                    }
                }
            },
            initialLeftFraction = 0.42f,
            minLeftFraction = 0.34f,
            maxLeftFraction = 0.50f,
            showDivider = true,
            dividerDraggable = true,
            maxLeftWidth = UiDp.dp420,
        )
    } else {
        Scaffold { innerPadding ->
            MainPagerHost(
                layoutMode = windowLayoutMode,
                enableUserScroll = true,
                mainInnerPadding = paneInnerPadding(innerPadding, reserveBottomBar = true),
                mainPagerState = mainPagerState,
                pagerFlingBehavior = pagerFlingBehavior,
                visibleDestinations = visibleDestinations,
                previousDestinations = previousDestinations,
                settledDestination = settledDestination,
                bottomBarVisible = bottomBarVisible,
                topBarBlurEnabled = topBarBlurEnabled,
                hazeState = hazeState,
                bottomBarScrimColor = bottomBarScrimColor,
                visibleBottomBarReservedHeight = visibleBottomBarReservedHeight,
                classicHomeEnabled = classicHomeEnabled,
                moeWallpaperUri = moeWallpaperUri,
                moeWallpaperZoom = moeWallpaperZoom,
                moeWallpaperBiasX = moeWallpaperBiasX,
                moeWallpaperBiasY = moeWallpaperBiasY,
                homeVisibility = homeVisibility,
                navigator = navigator,
            )
        }
    }
}

@Composable
private fun MainPagerHost(
    layoutMode: WindowLayoutMode,
    enableUserScroll: Boolean,
    mainInnerPadding: PaddingValues,
    mainPagerState: MainPagerState,
    pagerFlingBehavior: TargetedFlingBehavior,
    visibleDestinations: List<BottomBarDestination>,
    previousDestinations: List<BottomBarDestination>,
    settledDestination: BottomBarDestination,
    bottomBarVisible: Boolean,
    topBarBlurEnabled: Boolean,
    hazeState: HazeState,
    bottomBarScrimColor: Color,
    visibleBottomBarReservedHeight: Dp,
    classicHomeEnabled: Boolean,
    moeWallpaperUri: String,
    moeWallpaperZoom: Float,
    moeWallpaperBiasX: Float,
    moeWallpaperBiasY: Float,
    homeVisibility: Float,
    navigator: Navigator,
) {
    val bottomBarScrollBehavior = LocalBottomBarScrollBehavior.current
    Box(Modifier.fillMaxSize()) {
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
            userScrollEnabled = enableUserScroll,
            overscrollEffect = null,
            pageNestedScrollConnection =
                PagerDefaults.pageNestedScrollConnection(
                    state = mainPagerState.pagerState,
                    orientation = Orientation.Horizontal,
                ),
        ) { page ->
            val destination =
                if (
                    previousDestinations != visibleDestinations &&
                        page == mainPagerState.pagerState.currentPage
                ) {
                    previousDestinations.getOrNull(page) ?: settledDestination
                } else {
                    visibleDestinations.getOrNull(page)
                        ?: previousDestinations.getOrNull(page)
                        ?: settledDestination
                }
            MainRootPageContent(
                destination = destination,
                mainInnerPadding = mainInnerPadding,
                classicHomeEnabled = classicHomeEnabled,
                moeWallpaperUri = moeWallpaperUri,
                moeWallpaperZoom = moeWallpaperZoom,
                moeWallpaperBiasX = moeWallpaperBiasX,
                moeWallpaperBiasY = moeWallpaperBiasY,
                navigator = navigator,
                homePageProgress = homeVisibility,
                selectedDestination = settledDestination,
                windowLayoutMode = layoutMode,
            )
        }

        BottomEdgeScrim(
            color = bottomBarScrimColor,
            visible = bottomBarVisible && (bottomBarScrollBehavior?.isBottomBarVisible ?: true),
            height = visibleBottomBarReservedHeight + UiDp.dp28,
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            BottomBarContent(
                isVisible = bottomBarVisible,
                destinations = visibleDestinations,
            )
        }
    }
}

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
