/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.github.yumelira.yumebox.presentation.theme.UiDp

/**
 * Module-level master/detail layout.
 *
 * - [WindowLayoutMode.TwoPane]: master + detail side by side.
 * - [WindowLayoutMode.RailSingle]/[WindowLayoutMode.Compact]: one pane at a time.
 *   When [showDetail] is true the detail pane is shown, otherwise the master pane.
 */
@Composable
fun MasterDetailLayout(
    windowLayoutMode: WindowLayoutMode,
    showDetail: Boolean,
    masterMinWidth: Dp,
    masterMaxWidth: Dp,
    modifier: Modifier = Modifier,
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    if (windowLayoutMode.usesTwoPanes) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val preferred = masterMinWidth + (masterMaxWidth - masterMinWidth) * 0.5f
            val masterWidth = preferred.coerceIn(
                minimumValue = masterMinWidth.coerceAtMost(maxWidth * 0.45f),
                maximumValue = masterMaxWidth.coerceAtMost(maxWidth * 0.5f),
            )
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(masterWidth)
                        .fillMaxHeight()
                ) {
                    master()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    detail()
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            if (showDetail) {
                detail()
            } else {
                master()
            }
        }
    }
}

@Composable
fun PaneWidth(
    min: Dp,
    max: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .widthIn(min = min, max = max)
                .fillMaxHeight()
    ) {
        content()
    }
}

object PaneWidths {
    val HomeMasterMin: Dp = UiDp.dp300
    val HomeMasterMax: Dp = UiDp.dp420
    val ProxyMasterMin: Dp = UiDp.dp260
    val ProxyMasterMax: Dp = UiDp.dp340
    val ProfilesMasterMin: Dp = UiDp.dp300
    val ProfilesMasterMax: Dp = UiDp.dp380
    val SettingsMasterMin: Dp = UiDp.dp280
    val SettingsMasterMax: Dp = UiDp.dp360
    val NodeGridAdaptiveMin: Dp = UiDp.dp280
}
