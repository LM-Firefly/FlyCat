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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.ProxyChainIndicator
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeGroupSheetContent
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeSheetContent
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeSortPopup
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeTabs
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeTestPullToRefresh
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.ProvideProxyDelayFlows
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.rememberNodeSheetHeight
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.rememberProxyGroupSelectionState
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.NoOpNestedScrollConnection
import com.github.lmfirefly.flycat.presentation.component.SearchInputToolbar
import com.github.lmfirefly.flycat.presentation.component.card.glassCardMaterial
import com.github.lmfirefly.flycat.presentation.component.GlassTopActionDefaults
import com.github.lmfirefly.flycat.presentation.component.rememberGlassSubmenuState
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetIconAction
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import com.github.lmfirefly.flycat.presentation.icon.flycat.ListChevronsUpDown
import com.github.lmfirefly.flycat.presentation.icon.flycat.Search
import com.github.lmfirefly.flycat.presentation.icon.flycat.Speed
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.Glass
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private const val NOTIFICATION_PROXY_SHEET_HEIGHT_FRACTION = 0.55f

@Composable
fun ProxySheetContent(onDismiss: () -> Unit, proxyViewModel: ProxyViewModel = koinViewModel(), onSheetTopPositioned: ((Float) -> Unit)? = null) {
    ProvideProxyDelayFlows(proxyViewModel) {
        ProxySheetContentInner(onDismiss, proxyViewModel, onSheetTopPositioned)
    }
}

@Composable
private fun ProxySheetContentInner(onDismiss: () -> Unit, proxyViewModel: ProxyViewModel, onSheetTopPositioned: ((Float) -> Unit)? = null) {
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsStateWithLifecycle()
    val sortMode by proxyViewModel.sortMode.collectAsStateWithLifecycle()
    val displayMode by proxyViewModel.displayMode.collectAsStateWithLifecycle()
    val showSheet = remember { mutableStateOf(true) }
    val showSortPopup = remember { mutableStateOf(false) }
    // 「标签页模式」：与代理页共享存储级状态（ProxyDisplaySettingsStore），跨界面双向同步。
    val groupTabsMode by proxyViewModel.groupTabsMode.collectAsStateWithLifecycle()
    val groupSelection = rememberProxyGroupSelectionState(
        proxyGroups = proxyGroups,
        onRefreshGroup = proxyViewModel::refreshGroup,
        retainLastKnownGroup = false,
    )
    val selectedGroupName = groupSelection.selectedGroupName
    val selectedGroup = groupSelection.selectedGroup
    val coroutineScope = rememberCoroutineScope()
    val groupListState = rememberLazyListState()
    val nodeListState = rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }
    var nodeSearchQuery by rememberSaveable(selectedGroupName) { mutableStateOf("") }
    var nodeSearchVisible by rememberSaveable(selectedGroupName) { mutableStateOf(false) }
    DisposableEffect(Unit) {
        proxyViewModel.ensureCoreLoaded(true, source = "proxy_sheet")
        onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_sheet") }
    }
    val dismissSheet = remember {
        {
            showSortPopup.value = false
            showSheet.value = false
        }
    }
    val triggerTopDelayTest = remember(coroutineScope, groupListState, proxyViewModel) {
        {
            coroutineScope.launch {
                if (groupListState.isScrolledFromTop()) {
                    groupListState.animateScrollToItem(0)
                }
                proxyViewModel.testDelay()
            }
        }
    }
    val triggerSelectedGroupDelayTest = remember(coroutineScope, nodeListState, proxyViewModel, selectedGroupName) {
        {
            val groupName = selectedGroupName ?: return@remember
            coroutineScope.launch {
                if (nodeListState.isScrolledFromTop()) {
                    nodeListState.animateScrollToItem(0)
                }
                proxyViewModel.testDelay(groupName)
            }
        }
    }
    // 与全应用弹层同宿主（AppActionBottomSheet）：浮动玻璃标题带的按钮真实采样内容录制层；
    // 且玻璃弹窗经 Scaffold 弹窗宿主渲染——此前 WindowBottomSheet 的独立 Dialog 窗口使排序弹窗登记后无人渲染。
    val sortMenuAnchor = rememberGlassPopupAnchor()
    // 选中态变化后强制整窗重绘：弹层多层玻璃各自是独立 RenderNode，部分提交时会出现"标题已新/按钮旧"的混合帧；整窗失效促使一次完整合成。
    val windowView = androidx.compose.ui.platform.LocalView.current.rootView
    val selectGroupAndRedraw: (com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo) -> Unit = remember(groupSelection) {
        { group -> groupSelection.selectGroup(group); windowView.invalidate() }
    }
    val clearSelectionAndRedraw: () -> Unit = remember(groupSelection) {
        { groupSelection.clearSelection(); windowView.invalidate() }
    }
    // 「标签页模式」变更（含代理页远程切换）：开启时退出组选中，并强制整窗重绘避免玻璃混合帧。
    LaunchedEffect(groupTabsMode) {
        if (groupTabsMode) groupSelection.clearSelection()
        windowView.invalidate()
    }
    // key 包裹整张弹层：每次导航重建子树，杜绝"标题新/按钮旧/网格旧"的陈旧作用域混合态。
    key(selectedGroupName) {
        AppActionBottomSheet(
            show = showSheet.value,
            onSheetTopPositioned = onSheetTopPositioned,
            title = selectedGroup?.name ?: FlyTxt.Proxy.Title,
            startAction = {
                Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                    AppBottomSheetIconAction(
                        action = AppBottomSheetAction(
                            icon = FlyCat.Speed,
                            contentDescription = FlyTxt.Proxy.Action.Test,
                            onClick = {
                                if (selectedGroup == null) {
                                    triggerTopDelayTest()
                                } else {
                                    triggerSelectedGroupDelayTest()
                                }
                            },
                        )
                    )
                    if (selectedGroup != null || groupTabsMode) {
                        AppBottomSheetIconAction(
                            action = AppBottomSheetAction(
                                icon = FlyCat.Search,
                                contentDescription = FlyTxt.Component.Editor.Action.Search,
                                onClick = {
                                    nodeSearchVisible = !nodeSearchVisible
                                    if (!nodeSearchVisible) nodeSearchQuery = ""
                                },
                            )
                        )
                    }
                }
            },
            endAction = {
                if (selectedGroup != null) {
                    AppBottomSheetIconAction(
                        action = AppBottomSheetAction(
                            icon = MiuixIcons.Glass.Back,
                            contentDescription = FlyTxt.Component.Navigation.Back,
                            onClick = clearSelectionAndRedraw,
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier.glassPopupAnchor(
                            anchor = sortMenuAnchor,
                            cornerRadius = GlassTopActionDefaults.ContainerSize / 2,
                            floating = false,
                        ),
                    ) {
                        AppBottomSheetIconAction(
                            action = AppBottomSheetAction(
                                icon = FlyCat.ListChevronsUpDown,
                                contentDescription = FlyTxt.Proxy.Action.Sort,
                                onClick = { showSortPopup.value = true },
                            )
                        )
                        NodeSortPopup(
                            show = showSortPopup.value,
                            onDismiss = { showSortPopup.value = false },
                            anchor = sortMenuAnchor,
                            displayMode = displayMode,
                            sortMode = sortMode,
                            onDisplayModeSelected = proxyViewModel::setDisplayMode,
                            onSortSelected = proxyViewModel::setSortMode,
                            tabsMode = groupTabsMode,
                            onTabsModeChange = { enabled -> proxyViewModel.setGroupTabsMode(enabled) },
                            alignToPageEnd = false,
                        )
                    }
                }
            },
            onDismissRequest = { dismissSheet() },
            onDismissFinished = onDismiss,
            enableWindowDim = true,
            insideMargin = DpSize(UiDp.dp16, UiDp.dp16),
            enableNestedScroll = false,
            renderInRootScaffold = false,
            // 面板采用玻璃卡片同配方（glassCardMaterial）：与代理页策略组/节点卡片一致的磨砂玻璃观感。
            material = glassCardMaterial(),
        ) {
            // 即时切换（不走 AnimatedContent）：过渡帧会同时组合新旧两棵卡片树，软渲染下整帧重录制可达数百毫秒，表现为"第一次点击不生效"。
            val targetGroup = selectedGroupName?.let { name -> proxyGroups.firstOrNull { group -> group.name == name } }
            if (groupTabsMode && proxyGroups.isNotEmpty()) {
                ProxySheetTabsContent(
                    proxyViewModel = proxyViewModel,
                    groups = proxyGroups,
                    displayMode = displayMode,
                    groupIconLookup = { name -> proxyGroups.firstOrNull { it.name == name }?.icon },
                    searchQuery = nodeSearchQuery,
                    onSearchQueryChange = { nodeSearchQuery = it },
                    searchVisible = nodeSearchVisible,
                    onSearchVisibleChange = { nodeSearchVisible = it },
                )
            } else if (targetGroup == null) {
                val testingGroupNames by proxyViewModel.testingGroupNames.collectAsStateWithLifecycle()
                NodeGroupSheetContent(
                    groups = proxyGroups,
                    displayMode = displayMode,
                    onGroupClick = selectGroupAndRedraw,
                    testingGroupNames = testingGroupNames,
                    sheetHeightFraction = NOTIFICATION_PROXY_SHEET_HEIGHT_FRACTION,
                    listState = groupListState,
                )
            } else {
                ProxySheetNodeContent(
                    proxyViewModel = proxyViewModel,
                    group = targetGroup,
                    displayMode = displayMode,
                    onTestDelay = triggerSelectedGroupDelayTest,
                    sheetHeightFraction = NOTIFICATION_PROXY_SHEET_HEIGHT_FRACTION,
                    listState = nodeListState,
                    groupIconLookup = { name -> proxyGroups.firstOrNull { it.name == name }?.icon },
                    searchQuery = nodeSearchQuery,
                    onSearchQueryChange = { nodeSearchQuery = it },
                    searchVisible = nodeSearchVisible,
                    onSearchVisibleChange = { nodeSearchVisible = it },
                )
            }
        }
    }
}

@Composable
private fun ProxySheetNodeContent(
    proxyViewModel: ProxyViewModel,
    group: com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo,
    displayMode: ProxyDisplayMode,
    onTestDelay: () -> Unit,
    sheetHeightFraction: Float,
    listState: LazyListState,
    // 各视图弹窗高度恒定（默认与分组视图同高），切换不触发壁纸层重录制/全表面重采样；标签页模式的 pager 页槽传 fillMaxSize 由槽位定高。
    modifier: Modifier = Modifier.fillMaxWidth().height(rememberNodeSheetHeight(sheetHeightFraction)),
    groupIconLookup: ((String) -> String?)? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchVisible: Boolean = false,
    onSearchVisibleChange: (Boolean) -> Unit = {},
) {
    val groupProxyNames = remember(group.proxies) { group.proxies.mapTo(linkedSetOf()) { it.name } }
    val filteredGroup = remember(group, searchQuery) {
        if (searchQuery.isBlank()) group
        else group.copy(proxies = group.filterNodes(searchQuery))
    }
    val isDelayTesting by remember(group.name, proxyViewModel) {
        proxyViewModel.testingGroupNames.map { testingGroupNames -> testingGroupNames.contains(group.name) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val testingProxyNames by remember(group.name, groupProxyNames, proxyViewModel) {
        if (groupProxyNames.isEmpty()) {
            flowOf(emptySet<String>())
        } else {
            proxyViewModel.testingProxyNames.map { names ->
                names.filterTo(linkedSetOf()) { proxyName ->
                    proxyName in groupProxyNames
                }
            }.distinctUntilChanged()
        }
    }
    .collectAsStateWithLifecycle(initialValue = emptySet())
    val onSelectProxy = remember(group.name, group.type, proxyViewModel, onTestDelay) {
        { proxyName: String ->
            if (group.type == Proxy.Type.Selector) {
                proxyViewModel.selectProxy(group.name, proxyName)
            } else {
                onTestDelay()
            }
        }
    }
    val onForceSelectProxy = remember(group.name, proxyViewModel) { { proxyName: String -> proxyViewModel.forceSelectProxy(group.name, proxyName) } }
    val onSingleNodeTestClick = remember(group.name, proxyViewModel) { { proxyName: String -> proxyViewModel.testProxyDelay(group.name, proxyName) } }
    val delayTestProgress by proxyViewModel.delayTestProgress.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        if (group.chainPath.isNotEmpty()) {
            ProxyChainIndicator(
                chain = group.chainPath,
                // 与 Proxy.kt 同节奏：下缘外距补偿行盒下落白。
                modifier = Modifier.fillMaxWidth().padding(bottom = UiDp.dp7),
                groupIconLookup = groupIconLookup,
                // 弹层内容列已带 16dp 内缩、节点网格无额外内缩：关闭 Card 的屏幕级水平内缩，链路条与卡片网格左右对齐。
                applyHorizontalPadding = false,
            )
        }
        if (searchVisible) {
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }
                    .collect { scrolling -> if (scrolling) onSearchVisibleChange(false) }
            }
        }
        AnimatedVisibility(visible = searchVisible) {
            SearchInputToolbar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(
                    top = if (group.chainPath.isNotEmpty()) UiDp.dp5 else UiDp.dp12,
                    bottom = UiDp.dp4,
                ),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            NodeTestPullToRefresh(
                isRefreshing = isDelayTesting,
                onRefresh = onTestDelay,
                tested = delayTestProgress?.tested ?: 0,
                total = delayTestProgress?.total ?: 0,
                modifier = Modifier.fillMaxSize(),
            ) {
                NodeSheetContent(
                    group = filteredGroup,
                    displayMode = displayMode,
                    isDelayTesting = isDelayTesting,
                    testingProxyNames = testingProxyNames,
                    onSelectProxy = onSelectProxy,
                    onForceSelectProxy = onForceSelectProxy,
                    onTestDelay = onTestDelay,
                    onTestProxyDelay = onSingleNodeTestClick,
                    listState = listState,
                    pinnedProxyName = group.fixed,
                    groupIconLookup = groupIconLookup,
                )
            }
        }
    }
}

/**
 * 「标签页模式」弹层内容：组名 tab 条 + 每组一页节点列表，与钻取式「组列表 ↔ 节点列表」互斥（由调用方分支选择）。
 * 每页复用 [ProxySheetNodeContent]（链路条/搜索/下拉测速/节点网格），搜索状态跨页共享。
 */
@Composable
private fun ProxySheetTabsContent(
    proxyViewModel: ProxyViewModel,
    groups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    groupIconLookup: ((String) -> String?)? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchVisible: Boolean = false,
    onSearchVisibleChange: (Boolean) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState { groups.size }
    // 总高与分组/节点视图同为 sheetHeight（tab 条 + pager 页占余量）：各视图弹窗高度恒定，切换不触发壁纸层重录制/全表面重采样。
    val sheetHeight = rememberNodeSheetHeight(NOTIFICATION_PROXY_SHEET_HEIGHT_FRACTION)
    Column(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
        NodeTabs(
            groups = groups,
            selectedIndex = pagerState.currentPage,
            onSelect = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            // 弹层内容列已带 16dp 内缩、节点网格无额外内缩：tab 条去除非对称横内缩，与卡片网格左右对齐。
            modifier = Modifier.fillMaxWidth().padding(vertical = UiDp.dp8),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 0,
            // 与 ProxyGroupTabsPane 同款：禁用 pager 拉伸过滚动；其嵌套滚动连接会吞掉子列表的竖向滚动，导致节点列表在 pager 内无法滚动。
            overscrollEffect = null,
            pageNestedScrollConnection = NoOpNestedScrollConnection,
        ) { page ->
            val group = groups[page]
            val listState = rememberSaveable(group.name, saver = LazyListState.Saver) { LazyListState() }
            ProxySheetNodeContent(
                proxyViewModel = proxyViewModel,
                group = group,
                displayMode = displayMode,
                onTestDelay = {
                    coroutineScope.launch {
                        if (listState.isScrolledFromTop()) listState.animateScrollToItem(0)
                    }
                    proxyViewModel.testDelay(group.name)
                },
                sheetHeightFraction = NOTIFICATION_PROXY_SHEET_HEIGHT_FRACTION,
                listState = listState,
                modifier = Modifier.fillMaxSize(),
                groupIconLookup = groupIconLookup,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchVisible = searchVisible,
                onSearchVisibleChange = onSearchVisibleChange,
            )
        }
    }
}

private fun ProxyGroupInfo.filterNodes(query: String): List<Proxy> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return proxies
    return proxies.filter { proxy ->
        proxy.name.contains(normalizedQuery, ignoreCase = true) ||
            proxy.title.contains(normalizedQuery, ignoreCase = true) ||
            proxy.subtitle.contains(normalizedQuery, ignoreCase = true)
    }
}
