/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumelira.yumebox.screen.moe

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.domain.model.TrafficData
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.screen.home.HomeProxyControlState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getRoundedCorner

internal data class MoeHomeLayoutState(
    val wallpaperUri: String,
    val wallpaperZoom: Float,
    val wallpaperBiasX: Float,
    val wallpaperBiasY: Float,
    val statusBarTop: Dp,
    val pageProgress: Float,
    val sidebarProgress: Float,
    val sidebarToggleProgress: Float,
    val duration: MoeDurationPair,
    val batteryPercent: Int?,
    val sidebarIcons: List<MoeSidebarIconItem>,
    val contentSurface: Color,
    val isRunning: Boolean,
    val traffic: TrafficData,
    val selectedServerName: String?,
    val selectedServerPing: Int?,
    val now: Long,
    val quote: String,
    val controlState: HomeProxyControlState,
    val canLaunch: Boolean,
    val isRemoteController: Boolean,
)

internal class MoeHomeActions(
    val toggleSidebar: () -> Unit,
    val pickWallpaper: () -> Unit,
    val openSettings: () -> Unit,
    val toggleProxy: () -> Unit,
)

context(actions: MoeHomeActions)
@Composable
internal fun MoeHomeLayout(state: MoeHomeLayoutState) {
    val density = LocalDensity.current
    val backdrop = rememberLayerBackdrop()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidebarWidth = maxWidth * MoeUi.Sidebar.fraction
        val contentStart = (sidebarWidth - MoeUi.Sidebar.contentOverlap).coerceAtLeast(UiDp.dp0)
        val page = state.pageProgress.coerceIn(0f, 1f)
        val sidebar = state.sidebarProgress.coerceIn(0f, 1f) * state.sidebarToggleProgress
        val sidebarWidthVisible = lerpDp(MoeUi.Sidebar.collapsedVisibleWidth, contentStart, sidebar)
        val heroHeight = (maxHeight - state.statusBarTop).coerceAtLeast(UiDp.dp0) * MoeUi.Hero.heightFraction
        val blurReady by
            remember(sidebar) {
                derivedStateOf {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sidebar > 0.03f
                }
            }
        MoeWallpaperBackground(
            wallpaperUri = state.wallpaperUri,
            wallpaperZoom = state.wallpaperZoom,
            wallpaperBiasX = state.wallpaperBiasX,
            wallpaperBiasY = state.wallpaperBiasY,
            qualityMode = MoeWallpaperQualityMode.BackgroundBlur,
            modifier = Modifier.matchParentSize().layerBackdrop(backdrop),
        )
        MoeSidebarDecoration(
            backdrop = backdrop,
            blurEnabled = blurReady,
            blurProgress = sidebar,
            modifier = Modifier.align(Alignment.CenterStart).width(sidebarWidth).fillMaxHeight().graphicsLayer {
                translationX = with(density) { lerpDp((-56).dp, UiDp.dp0, sidebar).toPx() }
                alpha = lerpFloat(0.78f, 1f, sidebar) * page
            },
        ) {
            MoeSidebarContent(
                topValue = state.duration.top,
                bottomValue = state.duration.bottom,
                batteryPercent = state.batteryPercent,
                icons = state.sidebarIcons,
                visibleWidth = sidebarWidthVisible,
            )
        }
        MoeHomePanel(state, contentStart, heroHeight, sidebar)
    }
}

context(actions: MoeHomeActions)
@Composable
private fun MoeHomePanel(
    state: MoeHomeLayoutState,
    contentStart: Dp,
    heroHeight: Dp,
    sidebar: Float,
) {
    val screenCorner = getRoundedCorner()
    val corner = lerpDp(UiDp.dp0, screenCorner, sidebar)
    val heroScale =
        if (state.pageProgress >= 0.999f) 1f
        else lerpFloat(1f, 0.965f, FastOutSlowInEasing.transform(1f - state.pageProgress.coerceIn(0f, 1f)))
    Box(
        Modifier.fillMaxSize()
            .padding(start = lerpDp(UiDp.dp0, contentStart, sidebar))
            .graphicsLayer {
                shape =
                    RoundedCornerShape(
                        topStart = corner,
                        bottomStart = corner,
                    )
                clip = true
            }
            .background(state.contentSurface)
    ) {
        MoeHero(state, heroScale)
        MoeHomeCopy(state, heroHeight)
    }
}

context(actions: MoeHomeActions)
@Composable
private fun BoxScope.MoeHero(state: MoeHomeLayoutState, scale: Float) {
    Box(
        Modifier.align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(
                start = MoeUi.Hero.containerHorizontalInset,
                end = MoeUi.Hero.containerHorizontalInset,
                top = state.statusBarTop,
            )
            .fillMaxHeight(MoeUi.Hero.heightFraction)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { actions.toggleSidebar() },
                    onLongPress = { actions.pickWallpaper() },
                )
            }
            .graphicsLayer {
                shape = MoeUi.Shape.hero
                clip = true
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = scale
                scaleY = scale
            }
    ) {
        MoeWallpaperBackground(
            wallpaperUri = state.wallpaperUri,
            wallpaperZoom = state.wallpaperZoom,
            wallpaperBiasX = state.wallpaperBiasX,
            wallpaperBiasY = state.wallpaperBiasY,
            qualityMode = MoeWallpaperQualityMode.Foreground,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.64f to Color.Transparent,
                    0.80f to state.contentSurface.copy(alpha = 0.90f),
                    1f to state.contentSurface,
                )
            )
        )
        AnimatedVisibility(
            visible = state.isRunning,
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = MoeUi.Hero.contentHorizontalInset,
                        end = MoeUi.Hero.contentHorizontalInset,
                        bottom = MoeUi.Hero.trafficBottomInset,
                    ),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.runtimeInfoTopGap)) {
                MoeTrafficStrip(state.traffic.download, state.traffic.upload)
                MoeHomeInfoPanel(
                    serverName = state.selectedServerName.takeIf { state.isRunning },
                    serverPing = state.selectedServerPing.takeIf { state.isRunning },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

context(actions: MoeHomeActions)
@Composable
private fun BoxScope.MoeHomeCopy(state: MoeHomeLayoutState, heroHeight: Dp) {
    Column(
        modifier =
            Modifier.align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset,
                    end = MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset,
                    top = state.statusBarTop + heroHeight + MoeUi.Hero.belowHeroTopGap,
                ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.belowHeroContentGap),
    ) {
        MoeHomeCopyBlock(
            nowMillis = state.now,
            quoteText = state.quote,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MoeLaunchControls(
                controlState = state.controlState,
                enabled = state.canLaunch && state.controlState.canInteract,
                isRemoteController = state.isRemoteController,
                surfaceColor = state.contentSurface,
                modifier = Modifier.fillMaxWidth(),
                onSettingsClick = actions.openSettings,
                onLaunchClick = actions.toggleProxy,
            )
        }
    }
}
