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

package com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.locale.FlyTxt
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
        ProxyDisplayMode.SINGLE_DETAILED -> FlyTxt.Proxy.DisplayMode.SingleDetailed
        ProxyDisplayMode.SINGLE_SIMPLE -> FlyTxt.Proxy.DisplayMode.SingleSimple
        ProxyDisplayMode.DOUBLE_DETAILED -> FlyTxt.Proxy.DisplayMode.DoubleDetailed
        ProxyDisplayMode.DOUBLE_SIMPLE -> FlyTxt.Proxy.DisplayMode.DoubleSimple
    }

private val ProxySortMode.displayName: String
    get() = when (this) {
        ProxySortMode.DEFAULT -> FlyTxt.Proxy.SortMode.Default
        ProxySortMode.BY_NAME -> FlyTxt.Proxy.SortMode.ByName
        ProxySortMode.BY_LATENCY -> FlyTxt.Proxy.SortMode.ByLatency
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
