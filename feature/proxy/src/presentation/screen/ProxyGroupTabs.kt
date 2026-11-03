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

package com.github.lmfirefly.flycat.feature.proxy.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeTabs
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.lmfirefly.flycat.presentation.component.NoOpNestedScrollConnection
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalPagerState
import com.github.lmfirefly.flycat.presentation.component.rememberNestedPagerHandoffConnection
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 组名 tab 条（含上下留白）占用的内容顶部高度；节点页内容以此让位。 */
internal val ProxyTabBandInset: Dp = UiDp.dp56

/**
 * 顶栏动作（标题回顶/定位当前节点）对标签页模式当前页滚动面的寻址句柄。
 * 当前页每次重组时刷新指向，保证动作作用在用户正在看的那一页。
 */
internal class ProxyTabsPageActions {
    var scrollTop: (() -> Unit)? = null
    var locateCurrent: (() -> Unit)? = null
}

/**
 * 「标签页模式」：一个可横滑的组名 tab 条 + 每组一页 HorizontalPager。
 * 与钻取式「组列表 ↔ 节点列表」互斥（由 [ProxyPagerContent] 分支选择）；本模式下直接铺开全部分组，翻页即换组，每组保留各自的滚动位置。
 * 子 pager 只有滑到边界才把横滑手势交给主 pager（[rememberNestedPagerHandoffConnection]），避免误翻主页。
 */
@Composable
internal fun ProxyGroupTabsPane(
    groups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    testingGroupNames: Set<String>,
    testingProxyNames: Set<String>,
    mainInnerPadding: PaddingValues,
    outerInnerPadding: PaddingValues,
    // 顶栏的 ScrollBehavior，必须与顶栏共用同一实例：孤儿行为 heightOffsetLimit=-MAX 会在 onPreScroll 吞掉全部上滑手势，列表无法滚动。
    scrollBehavior: ScrollBehavior,
    onTestGroupDelay: (String) -> Unit,
    onTestProxyDelay: (groupName: String, proxyName: String) -> Unit,
    onSelectProxy: (groupName: String, proxyName: String) -> Unit,
    onForceSelectProxy: (groupName: String, proxyName: String) -> Unit,
    pageActions: ProxyTabsPageActions,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchVisible: Boolean = false,
    onSearchVisibleChange: (Boolean) -> Unit = {},
    testProgress: ProxyViewModel.DelayTestProgress? = null,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState { groups.size }
    val nestedHandoff = rememberNestedPagerHandoffConnection(parentState = LocalPagerState.current, childState = pagerState)
    // 按组名登记各组页的滚动面状态；顶栏动作的实际寻址走 [ProxyTabsPageActions]。
    val groupListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val layoutDirection = LocalLayoutDirection.current
    val pagePadding = PaddingValues(
        top = outerInnerPadding.calculateTopPadding() + ProxyTabBandInset,
        bottom = outerInnerPadding.calculateBottomPadding(),
        start = outerInnerPadding.calculateStartPadding(layoutDirection),
        end = outerInnerPadding.calculateEndPadding(layoutDirection),
    )
    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().nestedScroll(nestedHandoff),
            beyondViewportPageCount = 0,
            // 与 ScreenLazyColumn 同款：禁用 pager 的拉伸过滚动效果——其嵌套滚动连接会吞掉子列表的竖向滚动，导致节点列表在 pager 内无法滚动。
            overscrollEffect = null,
            pageNestedScrollConnection = NoOpNestedScrollConnection,
        ) { page ->
            val group = groups.getOrNull(page) ?: return@HorizontalPager
            val listState = rememberSaveable(group.name, saver = LazyListState.Saver) { LazyListState() }
            SideEffect { groupListStates[group.name] = listState }
            // 当前页每次重组刷新顶栏动作指向：回顶/定位只作用在用户正在看的那一页。
            if (page == pagerState.currentPage) {
                SideEffect {
                    pageActions.scrollTop = { coroutineScope.launch { if (listState.isScrolledFromTop()) { listState.animateScrollToItem(0) } } }
                    pageActions.locateCurrent = {
                        val proxyIndex = group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                        if (proxyIndex >= 0) {
                            // 搜索栏固定在滚动列表外，列表无头部偏移；双列每行两个节点。
                            val listItemIndex = if (displayMode.isSingleColumn) proxyIndex
                                else proxyIndex / 2
                            coroutineScope.launch { listState.animateLocateToItem(listItemIndex) }
                        }
                    }
                }
            }
            NodeListPage(
                group = group,
                allGroups = groups,
                displayMode = displayMode,
                sortMode = sortMode,
                testingGroupNames = testingGroupNames,
                testingProxyNames = testingProxyNames,
                mainInnerPadding = mainInnerPadding,
                outerInnerPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                listState = listState,
                onSelectProxy = onSelectProxy,
                onForceSelectProxy = onForceSelectProxy,
                onTestDelay = {
                    coroutineScope.launch { if (listState.isScrolledFromTop()) { listState.scrollToItem(0) } }
                    onTestGroupDelay(group.name)
                },
                onTestProxyDelay = { proxyName -> onTestProxyDelay(group.name, proxyName) },
                onScrollDirectionChanged = {},
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchVisible = searchVisible,
                onSearchVisibleChange = onSearchVisibleChange,
                testProgress = testProgress,
            )
        }
        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = outerInnerPadding.calculateTopPadding())) {
            NodeTabs(
                groups = groups,
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }
    }
}
