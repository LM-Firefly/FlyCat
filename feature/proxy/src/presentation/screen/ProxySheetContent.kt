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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeSearchToolbar
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeSheetContent
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeSortPopup
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeTestPullToRefresh
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.rememberNodeSheetHeight
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.rememberProxyGroupSelectionState
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetIconAction
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ListChevronsUpDown
import com.github.lmfirefly.flycat.presentation.icon.flycat.Search
import com.github.lmfirefly.flycat.presentation.icon.flycat.Speed
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private const val NOTIFICATION_PROXY_SHEET_HEIGHT_FRACTION = 0.55f

private fun proxySheetTransition(
    enterSlideMillis: Int, exitSlideMillis: Int,
    enterOffsetX: (Int) -> Int,
    exitOffsetX: (Int) -> Int,
    enterFadeMillis: Int = AnimationSpecs.Proxy.SheetFadeInDuration
): ContentTransform = (slideInHorizontally(
    animationSpec = tween(durationMillis = enterSlideMillis, easing = AnimationSpecs.Legacy),
    initialOffsetX = enterOffsetX,
) + fadeIn(animationSpec = tween(durationMillis = enterFadeMillis))) togetherWith
    (slideOutHorizontally(
        animationSpec = tween(durationMillis = exitSlideMillis, easing = AnimationSpecs.Legacy),
        targetOffsetX = exitOffsetX,
    ) + fadeOut(animationSpec = tween(durationMillis = AnimationSpecs.Proxy.SheetFadeOutDuration)))

private fun LazyListState.isScrolledFromTop(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

@Composable
fun ProxySheetContent(onDismiss: () -> Unit, proxyViewModel: ProxyViewModel = koinViewModel()) {
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsStateWithLifecycle()
    val sortMode by proxyViewModel.sortMode.collectAsStateWithLifecycle()
    val displayMode by proxyViewModel.displayMode.collectAsStateWithLifecycle()
    val showSheet = remember { mutableStateOf(true) }
    val showSortPopup = remember { mutableStateOf(false) }
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
    WindowBottomSheet(
        show = showSheet.value,
        title = selectedGroup?.name ?: FlyTxt.Proxy.Title,
        backgroundColor = MiuixTheme.colorScheme.surface,
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
                if (selectedGroup != null) {
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
            AnimatedContent(
                targetState = selectedGroup != null,
                transitionSpec = {
                    val motion = AnimationSpecs.Proxy
                    val forward = targetState
                    proxySheetTransition(
                        enterSlideMillis = if (forward) motion.SheetSlideInDuration else motion.SheetSlideOutDuration,
                        exitSlideMillis = motion.SheetSlideOutDuration,
                        enterOffsetX = { width -> if (forward) width / 3 else -width / 3 },
                        exitOffsetX = { width -> if (forward) -width / 3 else width / 3 },
                    )
                },
                label = "notification_node_sheet_end_action",
            ) { showBackAction ->
                if (showBackAction) {
                    AppBottomSheetIconAction(
                        action = AppBottomSheetAction(
                            icon = MiuixIcons.Back,
                            contentDescription = FlyTxt.Component.Navigation.Back,
                            onClick = groupSelection.clearSelection,
                        )
                    )
                } else {
                    Box {
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
                            displayMode = displayMode,
                            sortMode = sortMode,
                            alignment = PopupPositionProvider.Align.BottomEnd,
                            onDisplayModeSelected = proxyViewModel::setDisplayMode,
                            onSortSelected = proxyViewModel::setSortMode,
                        )
                    }
                }
            }
        },
        onDismissRequest = { dismissSheet() },
        onDismissFinished = onDismiss,
        enableWindowDim = true,
        insideMargin = DpSize(UiDp.dp16, UiDp.dp16),
        enableNestedScroll = false,
    ) {
        AnimatedContent(
            targetState = selectedGroupName,
            transitionSpec = {
                val motion = AnimationSpecs.Proxy
                if (targetState != null) {
                    proxySheetTransition(
                        enterSlideMillis = motion.SheetSlideInDuration,
                        exitSlideMillis = motion.SheetSlideOutDuration,
                        enterOffsetX = { fullWidth -> fullWidth },
                        exitOffsetX = { fullWidth -> -fullWidth / 3 },
                    )
                } else {
                    proxySheetTransition(
                        enterSlideMillis = motion.SheetSlideOutDuration,
                        exitSlideMillis = motion.SheetSlideInDuration - 20,
                        enterOffsetX = { fullWidth -> -fullWidth / 3 },
                        exitOffsetX = { fullWidth -> fullWidth },
                        enterFadeMillis = motion.SheetFadeInDuration - 20,
                    )
                }
            },
            label = "notification_node_sheet_content",
        ) { targetGroupName ->
            val targetGroup = targetGroupName?.let { name ->
                proxyGroups.firstOrNull { group -> group.name == name }
            }
            if (targetGroup == null) {
                val testingGroupNames by proxyViewModel.testingGroupNames.collectAsStateWithLifecycle()
                NodeGroupSheetContent(
                    groups = proxyGroups,
                    displayMode = displayMode,
                    onGroupClick = groupSelection.selectGroup,
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
    val sheetHeight = rememberNodeSheetHeight(sheetHeightFraction)
    val delayTestProgress by proxyViewModel.delayTestProgress.collectAsStateWithLifecycle()
    Column {
        if (group.chainPath.isNotEmpty()) {
            ProxyChainIndicator(
                chain = group.chainPath,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (searchVisible) {
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }
                    .collect { scrolling -> if (scrolling) onSearchVisibleChange(false) }
            }
        }
        AnimatedVisibility(visible = searchVisible) {
            NodeSearchToolbar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(top = UiDp.dp12, bottom = UiDp.dp4),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(sheetHeight).clipToBounds()) {
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
                )
            }
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
