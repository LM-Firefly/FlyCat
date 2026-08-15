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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Dual-pane shell for tablet layout.
 *
 * Left pane holds the main pager at a phone-like width. Right pane hosts detail destinations.
 * The center divider may be shown and optionally dragged.
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
    var leftRatio by remember {
        mutableFloatStateOf(initialLeftFraction.coerceIn(0.2f, 0.8f))
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val dividerPx = with(density) { if (showDivider) dividerHitWidth.toPx() else 0f }
        val freeWidthPx = (containerWidthPx - dividerPx).coerceAtLeast(1f)

        val rawMinLeftPx = with(density) { minLeftWidth.toPx() }
        val rawMaxLeftPx = with(density) { maxLeftWidth.toPx() }
        val rawMinRightPx = with(density) { minRightWidth.toPx() }

        val minsTotal = rawMinLeftPx + rawMinRightPx
        val minScale =
            if (minsTotal > freeWidthPx && minsTotal > 0f) freeWidthPx / minsTotal else 1f
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
            Box(
                Modifier
                    .width(leftWidthDp)
                    .fillMaxHeight()
            ) {
                left()
            }

            if (showDivider) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(dividerHitWidth)
                            .then(
                                if (canDrag) {
                                    Modifier.draggable(
                                        state = dragState,
                                        orientation = Orientation.Horizontal,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.55f))
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                right()
            }
        }
    }
}
