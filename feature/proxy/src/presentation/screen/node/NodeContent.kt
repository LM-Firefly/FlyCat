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

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.core.model.proxy.normalizeProxySheetHeightFraction
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.SegmentedTabRow
import com.github.lmfirefly.flycat.presentation.component.misc.GroupIcon
import com.github.lmfirefly.flycat.presentation.component.misc.normalizeGroupIconUri
import com.github.lmfirefly.flycat.presentation.theme.SheetLeadMillis
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.rememberRowReveal
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.utils.overScrollVertical

val NodeSheetContentPadding = PaddingValues(start = UiDp.dp0, end = UiDp.dp0, top = UiDp.dp8, bottom = UiDp.dp16)

private fun LazyListState.isScrolledFromTop(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** 「标签页模式」组名 tab 条：玻璃分段轨道 + 滑动指示器（[SegmentedTabRow] 的 scrollable 形态），组多时轨道内横滑，选中组自动居中；tab 文字前拼接策略组图标（[GroupIcon]，加载成功才渲染）。 */
@Composable
internal fun NodeTabs(
    groups: List<ProxyGroupInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = UiDp.dp12, vertical = UiDp.dp8),
) {
    val tabs = remember(groups) { groups.map { it.name } }
    val tabIconUris = remember(groups) { groups.map { group -> group.icon?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeGroupIconUri) } }
    SegmentedTabRow(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier,
        scrollable = true,
        tabIcon = { index ->
            val iconUri = tabIconUris.getOrNull(index)
            if (iconUri != null) {
                GroupIcon(iconUri = iconUri, width = UiDp.dp16)
            }
        },
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun rememberNodeSheetHeight(sheetHeightFraction: Float): Dp {
    val normalized = normalizeProxySheetHeightFraction(sheetHeightFraction)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return remember(screenHeightDp, normalized) { screenHeightDp.dp * normalized }
}

@Composable
internal fun NodeGroupSheetContent(
    groups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    testingGroupNames: Set<String>,
    sheetHeightFraction: Float,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val sheetHeight = rememberNodeSheetHeight(sheetHeightFraction)
    val revealCount = rememberRowReveal(itemCount = groups.size, listState = listState, leadMillis = SheetLeadMillis)

    LaunchedEffect(testingGroupNames) {
        if (testingGroupNames.isNotEmpty() && listState.isScrolledFromTop()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(sheetHeight).overScrollVertical(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(UiDp.dp12),
        contentPadding = NodeSheetContentPadding,
        overscrollEffect = null,
    ) {
        nodeGroupItems(
            groups = groups,
            displayMode = displayMode,
            onGroupClick = onGroupClick,
            testingGroupNames = testingGroupNames,
            itemVerticalPadding = UiDp.dp0,
            revealCount = revealCount,
        )
    }
}

@Composable
fun NodeSheetContent(
    group: ProxyGroupInfo,
    displayMode: ProxyDisplayMode = ProxyDisplayMode.DOUBLE_DETAILED,
    onSelectProxy: (String) -> Unit,
    onForceSelectProxy: ((String) -> Unit)? = null,
    isDelayTesting: Boolean,
    testingProxyNames: Set<String>,
    onTestDelay: () -> Unit,
    onTestProxyDelay: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    pinnedProxyName: String = "",
    groupIconLookup: ((String) -> String?)? = null,
) {
    val revealCount = rememberRowReveal(itemCount = group.proxies.size, replayKey = group.name, listState = listState, leadMillis = SheetLeadMillis)
    LaunchedEffect(isDelayTesting) {
        if (isDelayTesting && listState.isScrolledFromTop()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().overScrollVertical(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(UiDp.dp12),
        contentPadding = NodeSheetContentPadding,
        overscrollEffect = null,
    ) {
        nodeGridItems(
            proxies = group.proxies,
            selectedProxyName = group.now,
            pinnedProxyName = pinnedProxyName,
            displayMode = displayMode,
            groupIconLookup = groupIconLookup,
            onProxyClick = { proxyName ->
                if (group.type == Proxy.Type.Selector) {
                    onSelectProxy(proxyName)
                } else if (
                    (group.type == Proxy.Type.URLTest || group.type == Proxy.Type.Fallback) &&
                    onForceSelectProxy != null
                ) {
                    val target = if (proxyName == group.fixed) "" else proxyName
                    onForceSelectProxy(target)
                } else {
                    onTestDelay()
                }
            },
            isDelayTesting = isDelayTesting,
            testingProxyNames = testingProxyNames,
            onSingleNodeTestClick = onTestProxyDelay,
            revealCount = revealCount,
        )
    }
}

/**
 * 节点页统一的下拉测试容器：下拉触发当前策略组延迟测试，顶部指示动画在测试期间显示 "正在测试 x/xxx" 进度。
 */
@Composable
internal fun NodeTestPullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, tested: Int, total: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val refreshTexts = listOf(
        FlyTxt.Proxy.Testing.Pull,
        FlyTxt.Proxy.Testing.Release,
        if (total > 0) {
            FlyTxt.Proxy.Testing.Progress.format(tested, total)
        } else {
            FlyTxt.Proxy.Testing.InProgress
        },
        FlyTxt.Proxy.Testing.Complete,
    )
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        refreshTexts = refreshTexts,
        modifier = modifier,
        content = content,
    )
}
