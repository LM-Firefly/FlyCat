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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.lmfirefly.flycat.presentation.component.NoOpNestedScrollConnection
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalPagerState
import com.github.lmfirefly.flycat.presentation.component.rememberNestedPagerHandoffConnection
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 标签页顶栏动作与当前滚动页状态的句柄。 */
internal class ProxyTabsPageActions {
    var scrollTop: (() -> Unit)? = null
    var locateCurrent: (() -> Unit)? = null
    var activeGroupName by mutableStateOf<String?>(null)
    var activeListState by mutableStateOf<LazyListState?>(null)
    var titleCollapsed by mutableStateOf(false)
}

/**
 * 「标签页模式」的组页容器，组名 tab 条由 [ProxyTopBar] 的 tabContent 承载，本组件仅托管 HorizontalPager。
 * 与钻取式「组列表 ↔ 节点列表」互斥（由 [ProxyPagerContent] 分支选择）；pager 按需组合组页，翻页即换组，每组通过 rememberSaveable 保留列表滚动位置。
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
    pagerState: PagerState,
    pageActions: ProxyTabsPageActions,
    searchQuery: String = "",
    searchVisible: Boolean = false,
    onSearchVisibleChange: (Boolean) -> Unit = {},
    testProgress: ProxyViewModel.DelayTestProgress? = null,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val nestedHandoff = rememberNestedPagerHandoffConnection(parentState = LocalPagerState.current, childState = pagerState)
    // 按组名登记各组页的滚动面状态；顶栏动作的实际寻址走 [ProxyTabsPageActions]。
    val groupListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val activeListState = groups.getOrNull(pagerState.currentPage)?.let { groupListStates[it.name] }
    LaunchedEffect(activeListState) { snapshotFlow { activeListState?.isProxyTitleCollapsed() == true }.collect { collapsed -> pageActions.titleCollapsed = collapsed } }
    DisposableEffect(pageActions) {
        onDispose {
            pageActions.activeGroupName = null
            pageActions.activeListState = null
            pageActions.titleCollapsed = false
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val pagePadding = PaddingValues(
        top = outerInnerPadding.calculateTopPadding(),
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
                    pageActions.activeGroupName = group.name
                    pageActions.activeListState = listState
                    pageActions.scrollTop = { coroutineScope.launch { if (listState.isScrolledFromTop()) { listState.animateScrollToItem(0) } } }
                    pageActions.locateCurrent = {
                        val proxyIndex = group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                        if (proxyIndex >= 0) {
                            // 搜索栏固定在滚动列表外，列表无头部偏移；双列每行两个节点。
                            val listItemIndex = if (displayMode.isSingleColumn) proxyIndex else proxyIndex / 2
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
                searchVisible = searchVisible,
                onSearchVisibleChange = onSearchVisibleChange,
                testProgress = testProgress,
            )
        }
    }
}
