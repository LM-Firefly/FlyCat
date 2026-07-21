/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.component

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
 * NetEase / YumeFuwa style dual-pane shell.
 *
 * Left pane holds the main pager at a phone-like width fraction so Moe home is not stretched.
 * Right pane hosts detail destinations. The center divider may be shown and optionally dragged.
 */
@Composable
fun DualPaneLayout(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialLeftFraction: Float = 0.42f,
    minLeftFraction: Float = 0.32f,
    maxLeftFraction: Float = 0.52f,
    showDivider: Boolean = true,
    dividerDraggable: Boolean = true,
    dividerHitWidth: Dp = 12.dp,
    maxLeftWidth: Dp = 440.dp,
) {
    var leftFraction by remember { mutableFloatStateOf(initialLeftFraction.coerceIn(minLeftFraction, maxLeftFraction)) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val maxLeftWidthPx = with(density) { maxLeftWidth.toPx() }
        val fractionCap = (maxLeftWidthPx / containerWidthPx).coerceIn(minLeftFraction, maxLeftFraction)
        val effectiveMaxFraction = minOf(maxLeftFraction, fractionCap)
        val clampedFraction = leftFraction.coerceIn(minLeftFraction, effectiveMaxFraction)

        val dragState =
            rememberDraggableState { deltaPx ->
                if (!dividerDraggable) return@rememberDraggableState
                val signed =
                    if (layoutDirection == LayoutDirection.Rtl) -deltaPx else deltaPx
                leftFraction =
                    (leftFraction + signed / containerWidthPx).coerceIn(minLeftFraction, effectiveMaxFraction)
            }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(clampedFraction)
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
                                if (dividerDraggable) {
                                    Modifier.draggable(state = dragState, orientation = Orientation.Horizontal)
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
                    .weight((1f - clampedFraction).coerceAtLeast(0.01f))
                    .fillMaxHeight()
            ) {
                right()
            }
        }
    }
}
