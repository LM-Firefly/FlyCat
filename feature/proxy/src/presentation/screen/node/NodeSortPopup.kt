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

package com.github.yumelira.yumebox.feature.proxy.presentation.screen.node

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.core.model.ProxySortMode
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.window.WindowListPopup

internal val NodeSortModes =
    listOf(ProxySortMode.DEFAULT, ProxySortMode.BY_NAME, ProxySortMode.BY_LATENCY)

internal val NodeDisplayModes = listOf(
    ProxyDisplayMode.SINGLE_DETAILED,
    ProxyDisplayMode.DOUBLE_DETAILED,
)

private val ProxyDisplayMode.displayName: String
    get() = when (this) {
        ProxyDisplayMode.SINGLE_DETAILED -> MLang.Proxy.DisplayMode.SingleDetailed
        ProxyDisplayMode.SINGLE_SIMPLE -> MLang.Proxy.DisplayMode.SingleSimple
        ProxyDisplayMode.DOUBLE_DETAILED -> MLang.Proxy.DisplayMode.DoubleDetailed
        ProxyDisplayMode.DOUBLE_SIMPLE -> MLang.Proxy.DisplayMode.DoubleSimple
    }

private val ProxySortMode.displayName: String
    get() = when (this) {
        ProxySortMode.DEFAULT -> MLang.Proxy.SortMode.Default
        ProxySortMode.BY_NAME -> MLang.Proxy.SortMode.ByName
        ProxySortMode.BY_LATENCY -> MLang.Proxy.SortMode.ByLatency
    }

@Composable
internal fun NodeSortPopup(
    show: Boolean,
    onDismiss: () -> Unit,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    onDisplayModeSelected: (ProxyDisplayMode) -> Unit,
    onSortSelected: (ProxySortMode) -> Unit,
) {
    val selectedDisplayIndex = when (displayMode) {
        ProxyDisplayMode.SINGLE_DETAILED,
        ProxyDisplayMode.SINGLE_SIMPLE,
        -> 0
        ProxyDisplayMode.DOUBLE_DETAILED,
        ProxyDisplayMode.DOUBLE_SIMPLE,
        -> 1
    }
    val selectedSortIndex = NodeSortModes.indexOf(sortMode).coerceAtLeast(0)
    WindowListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = alignment,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            NodeDisplayModes.forEachIndexed { index, mode ->
                DropdownImpl(
                    text = mode.displayName,
                    optionSize = NodeDisplayModes.size,
                    isSelected = selectedDisplayIndex == index,
                    onSelectedIndexChange = {
                        if (selectedDisplayIndex != index) onDisplayModeSelected(mode)
                        onDismiss()
                    },
                    index = index,
                )
            }
            Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
            NodeSortModes.forEachIndexed { index, mode ->
                DropdownImpl(
                    text = mode.displayName,
                    optionSize = NodeSortModes.size,
                    isSelected = selectedSortIndex == index,
                    onSelectedIndexChange = {
                        if (mode != sortMode) onSortSelected(mode)
                        onDismiss()
                    },
                    index = index,
                )
            }
        }
    }
}
