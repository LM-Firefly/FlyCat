/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowLayoutMode {
    Compact,
    RailSingle,
    TwoPane;

    val usesNavigationRail: Boolean
        get() = this != Compact

    val usesTwoPanes: Boolean
        get() = this == TwoPane
}

@Composable
fun rememberWindowLayoutMode(): WindowLayoutMode {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp >= 700 -> WindowLayoutMode.TwoPane
            widthDp >= 600 -> WindowLayoutMode.RailSingle
            else -> WindowLayoutMode.Compact
        }
    }
}
