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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassMenuDivider
import com.github.lmfirefly.flycat.presentation.component.GlassMenuItem
import com.github.lmfirefly.flycat.presentation.component.GlassPopupHost
import com.github.lmfirefly.flycat.presentation.component.GlassPopupSubmenuItem
import com.github.lmfirefly.flycat.presentation.component.GlassSubmenuPageHeader
import com.github.lmfirefly.flycat.presentation.component.GlassTopActionAnchorIcon
import com.github.lmfirefly.flycat.presentation.component.rememberGlassSubmenuState
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowDownUp
import com.github.lmfirefly.flycat.presentation.icon.flycat.Folders
import com.github.lmfirefly.flycat.presentation.icon.flycat.LayoutPanelLeft
import com.github.lmfirefly.flycat.presentation.icon.flycat.ListChevronsUpDown
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor

internal val NodeSortModes = listOf(ProxySortMode.DEFAULT, ProxySortMode.BY_NAME, ProxySortMode.BY_LATENCY)

internal val NodeDisplayModes = listOf(ProxyDisplayMode.SINGLE_DETAILED, ProxyDisplayMode.DOUBLE_DETAILED)

private val ProxySortMode.displayName: String
    get() = when (this) {
        ProxySortMode.DEFAULT -> FlyTxt.Proxy.SortMode.Default
        ProxySortMode.BY_NAME -> FlyTxt.Proxy.SortMode.ByName
        ProxySortMode.BY_LATENCY -> FlyTxt.Proxy.SortMode.ByLatency
    }

/** 级联二级页：布局 / 排序。 */
private enum class NodeMenuPage {
    None,
    Layout,
    Sort,
}

/**
 * 代理页「更多」玻璃菜单：
 * 根面板 = 「布局」「排序」两个级联二级页；
 * 「布局」页 = 单列 / 双列（互斥）+ 标签页模式（独立开关，可选）；
 * 「排序」页 = 默认顺序 / 按名称排序 / 按延迟排序。
 */
@Composable
internal fun NodeSortPopup(
    show: Boolean,
    onDismiss: () -> Unit,
    anchor: GlassPopupAnchor,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    onDisplayModeSelected: (ProxyDisplayMode) -> Unit,
    onSortSelected: (ProxySortMode) -> Unit,
    /** 非空时在布局页展示「标签页模式」开关行；不支持该模式的宿主传 null 隐藏。 */
    tabsMode: Boolean? = null,
    onTabsModeChange: ((Boolean) -> Unit)? = null,
    /** 弹层标题行内的锚点场景传 false（面板放置贴锚点）；页面顶栏场景用默认 true（贴页右缘）。 */
    alignToPageEnd: Boolean = true,
) {
    val submenu = rememberGlassSubmenuState(show)
    var submenuPage by remember { mutableStateOf(NodeMenuPage.None) }
    val applyAndClose: (() -> Unit) -> Unit = { action ->
        action()
        submenu.close()
        onDismiss()
    }
    GlassPopupHost(
        show = show,
        onDismissRequest = onDismiss,
        anchor = anchor,
        alignToPageEnd = alignToPageEnd,
        anchorContent = { GlassTopActionAnchorIcon(FlyCat.ListChevronsUpDown) },
        submenu = submenu,
        submenuContent = {
            when (submenuPage) {
                NodeMenuPage.Layout -> {
                    GlassSubmenuPageHeader(
                        submenu,
                        FlyTxt.Proxy.Action.Layout,
                        anchor,
                        icon = FlyCat.LayoutPanelLeft,
                    )
                    GlassMenuItem(
                        text = FlyTxt.Proxy.DisplayMode.SingleDetailed,
                        selected = displayMode.isSingleColumn,
                        onClick = {
                            applyAndClose {
                                if (!displayMode.isSingleColumn) {
                                    onDisplayModeSelected(ProxyDisplayMode.SINGLE_DETAILED)
                                }
                            }
                        },
                    )
                    GlassMenuItem(
                        text = FlyTxt.Proxy.DisplayMode.DoubleDetailed,
                        selected = !displayMode.isSingleColumn,
                        onClick = {
                            applyAndClose {
                                if (displayMode.isSingleColumn) {
                                    onDisplayModeSelected(ProxyDisplayMode.DOUBLE_DETAILED)
                                }
                            }
                        },
                    )
                    if (tabsMode != null && onTabsModeChange != null) {
                        GlassMenuDivider()
                        GlassMenuItem(
                            text = FlyTxt.Proxy.Action.TabsMode,
                            selected = tabsMode,
                            onClick = { applyAndClose { onTabsModeChange(!tabsMode) } },
                        )
                    }
                }
                NodeMenuPage.Sort -> {
                    GlassSubmenuPageHeader(
                        submenu,
                        FlyTxt.Proxy.Action.Sort,
                        anchor,
                        icon = FlyCat.ArrowDownUp,
                    )
                    NodeSortModes.forEach { mode ->
                        GlassMenuItem(
                            text = mode.displayName,
                            selected = mode == sortMode,
                            onClick = {
                                applyAndClose {
                                    if (mode != sortMode) onSortSelected(mode)
                                }
                            },
                        )
                    }
                }
                NodeMenuPage.None -> Unit
            }
        },
    ) {
        GlassPopupSubmenuItem(
            text = FlyTxt.Proxy.Action.Layout,
            anchor = anchor,
            open = submenu.open && submenuPage == NodeMenuPage.Layout,
            onOpen = { bounds ->
                submenuPage = NodeMenuPage.Layout
                submenu.openAt(bounds)
            },
            icon = FlyCat.LayoutPanelLeft,
        )
        GlassPopupSubmenuItem(
            text = FlyTxt.Proxy.Action.Sort,
            anchor = anchor,
            open = submenu.open && submenuPage == NodeMenuPage.Sort,
            onOpen = { bounds ->
                submenuPage = NodeMenuPage.Sort
                submenu.openAt(bounds)
            },
            icon = FlyCat.ArrowDownUp,
        )
    }
}
