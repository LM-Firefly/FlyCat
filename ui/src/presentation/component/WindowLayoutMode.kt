/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("UnusedSymbol")

package com.github.yumeyucca.yumebox.presentation.component


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Window size class for adaptive layout.
 *
 * Product rule (main tabs):
 * - [Compact]: phone single stack; secondary screens push on the root navigator.
 * - [RailSingle]/[TwoPane]: tablet dual-pane shell (left main pager + right detail host).
 *
 * Module-level master/detail inside a tab is intentionally not used anymore; tablet detail always
 * goes through [LocalDetailNavigator] in the shell right pane.
 */
enum class WindowLayoutMode {
    Compact,
    RailSingle,
    TwoPane;

    /** Tablet dual-pane shell should be used (left pager + right detail). */
    val usesSplitShell: Boolean
        get() = this != Compact

    /** @deprecated Prefer [usesSplitShell]; kept for existing call sites. */
    val usesNavigationRail: Boolean
        get() = usesSplitShell

    val usesTwoPanes: Boolean
        get() = this == TwoPane
}

@Composable
fun rememberWindowLayoutMode(): WindowLayoutMode {
    val configuration = LocalConfiguration.current
    // Judge by the landscape (longer) side so a tablet keeps the dual-pane shell in portrait —
    // an 8.8" slate in portrait is ~668dp wide but ~1069dp long, which is still a tablet.
    val longestSideDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
    return remember(longestSideDp) {
        when {
            longestSideDp >= 700 -> WindowLayoutMode.TwoPane
            longestSideDp >= 600 -> WindowLayoutMode.RailSingle
            else -> WindowLayoutMode.Compact
        }
    }
}
