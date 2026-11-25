/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 用于平板布局的双栏外壳。
 * 左栏以手机宽度的尺寸承载主分页器。右栏承载详情页面。
 * 中间的分隔线可以显示，并支持可选拖动。
 */
@Composable
fun DualPaneLayout(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialLeftFraction: Float = 0.42f,
    minLeftWidth: Dp = 280.dp,
    maxLeftWidth: Dp = 420.dp,
    minRightWidth: Dp = 320.dp,
    showDivider: Boolean = true,
    dividerDraggable: Boolean = true,
    dividerHitWidth: Dp = 16.dp,
) {
    var leftRatio by rememberSaveable { mutableFloatStateOf(initialLeftFraction.coerceIn(0.2f, 0.8f)) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val dividerPx = with(density) { if (showDivider) dividerHitWidth.toPx() else 0f }
        val freeWidthPx = containerWidthPx
        val rawMinLeftPx = with(density) { minLeftWidth.toPx() }
        val rawMaxLeftPx = with(density) { maxLeftWidth.toPx() }
        val rawMinRightPx = with(density) { minRightWidth.toPx() }
        val minsTotal = rawMinLeftPx + rawMinRightPx
        val minScale = if (minsTotal > freeWidthPx && minsTotal > 0f) freeWidthPx / minsTotal else 1f
        val minLeftPx = rawMinLeftPx * minScale
        val minRightPx = rawMinRightPx * minScale
        val minBoundPx = minLeftPx.coerceIn(0f, freeWidthPx)
        val maxFromRight = (freeWidthPx - minRightPx).coerceAtLeast(0f)
        val maxBoundPx = maxOf(minBoundPx, minOf(rawMaxLeftPx, maxFromRight, freeWidthPx))
        val desiredLeftPx = (leftRatio * freeWidthPx).coerceIn(minBoundPx, maxBoundPx)
        val leftWidthDp = with(density) { desiredLeftPx.toDp() }
        val canDrag = dividerDraggable && maxBoundPx > minBoundPx + 0.5f
        val dragState = rememberDraggableState { deltaPx ->
            if (!canDrag) return@rememberDraggableState
            val signed = if (layoutDirection == LayoutDirection.Rtl) -deltaPx else deltaPx
            val nextLeft = (leftRatio * freeWidthPx + signed).coerceIn(minBoundPx, maxBoundPx)
            leftRatio = (nextLeft / freeWidthPx).coerceIn(0.05f, 0.95f)
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.width(leftWidthDp).fillMaxHeight()) { left() }
            Box(Modifier.weight(1f).fillMaxHeight()) { right() }
        }
        if (showDivider) {
            val dividerCenter = if (layoutDirection == LayoutDirection.Rtl) containerWidthPx - desiredLeftPx else desiredLeftPx
            val dividerOffset = dividerCenter - dividerPx / 2f
            Box(
                modifier = Modifier.offset { IntOffset(dividerOffset.toInt(), 0) }.fillMaxHeight().width(dividerHitWidth)
                    .then(if (canDrag) { Modifier.draggable(state = dragState, orientation = Orientation.Horizontal) } else { Modifier }),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = 0.55f)))
            }
        }
    }
}
