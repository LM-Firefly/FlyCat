/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.lmfirefly.flycat.feature.proxy.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.NodeSortPopup
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node.ProvideProxyDelayFlows
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassScaffold
import com.github.lmfirefly.flycat.presentation.component.misc.CenteredText
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Speed
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 平板双栏右栏节点详情，与左栏 [ProxyPager] 共享 [ProxyViewModel] 并通过 [ProxyViewModel.uiSelectedGroupName] 同步。 */
@Composable
fun ProxyShellNodeDetail(mainInnerPadding: PaddingValues, onNavigateToProviders: (() -> Unit)? = null, onOpenPanel: (() -> Unit)? = null) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    ProvideProxyDelayFlows(proxyViewModel) { ProxyShellNodeDetailContent(mainInnerPadding, onNavigateToProviders, onOpenPanel, proxyViewModel) }
}

@Composable
private fun ProxyShellNodeDetailContent(mainInnerPadding: PaddingValues, onNavigateToProviders: (() -> Unit)?, onOpenPanel: (() -> Unit)?, proxyViewModel: ProxyViewModel) {
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsStateWithLifecycle()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsStateWithLifecycle()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsStateWithLifecycle()
    val delayTestProgress by proxyViewModel.delayTestProgress.collectAsStateWithLifecycle()
    val sortMode by proxyViewModel.sortMode.collectAsStateWithLifecycle()
    val uiSelectedGroupName by proxyViewModel.uiSelectedGroupName.collectAsStateWithLifecycle()
    val displayMode by proxyViewModel.displayMode.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)
    val coroutineScope = rememberCoroutineScope()
    val groupSelection = rememberProxyGroupSelectionState(
        proxyGroups = proxyGroups,
        onRefreshGroup = proxyViewModel::refreshGroup,
        retainLastKnownGroup = true,
        controlledSelectedGroupName = uiSelectedGroupName,
        onControlledSelectedGroupNameChange = proxyViewModel::selectUiGroup,
    )
    val selectedGroupName = groupSelection.selectedGroupName
    val displayGroup = groupSelection.displayGroup
    val currentGroup = groupSelection.selectedGroup ?: displayGroup ?: proxyGroups.firstOrNull()
    val currentGroupName = currentGroup?.name
    val proxyGroupMap = remember(proxyGroups) { proxyGroups.associateBy { it.name } }
    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    var nodeSearchQuery by rememberSaveable(selectedGroupName) { mutableStateOf("") }
    var nodeSearchVisible by rememberSaveable(selectedGroupName) { mutableStateOf(false) }
    val nodeListState = rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(proxyViewModel) { proxyViewModel.ensureCoreLoaded(true, source = "proxy_detail") }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { proxyViewModel.onForegroundResume() }
    DisposableEffect(proxyViewModel) { onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_detail") } }
    val requestDelayTest = remember(coroutineScope, nodeListState, selectedGroupName, currentGroupName, proxyViewModel) {
        {
            val groupName = selectedGroupName ?: currentGroupName ?: return@remember
            coroutineScope.launch {
                if (nodeListState.firstVisibleItemIndex > 0 || nodeListState.firstVisibleItemScrollOffset > 0) { nodeListState.animateScrollToItem(0) }
                proxyViewModel.testDelay(groupName)
            }
        }
    }
    val locateCurrentProxy = remember(coroutineScope, currentGroup, nodeListState, selectedGroupName, displayMode) {
        if (selectedGroupName == null || currentGroup == null) { null } else {
            fun() {
                val proxyIndex = currentGroup.proxies.indexOfFirst { proxy -> proxy.name == currentGroup.now }
                if (proxyIndex < 0) return
                // 搜索栏固定在滚动列表外，列表无头部偏移。
                val listItemIndex = if (displayMode.isSingleColumn) proxyIndex else proxyIndex / 2
                coroutineScope.launch { nodeListState.animateLocateToItem(listItemIndex) }
            }
        }
    }
    GlassScaffold(
        topBar = {
            DetailTopBar(
                title = currentGroupName ?: FlyTxt.Proxy.Title,
                chain = currentGroup?.chainPath.orEmpty(),
                groupIconLookup = { name -> proxyGroupMap[name]?.icon },
                scrollBehavior = scrollBehavior,
                titleCollapsed = nodeListState.isProxyTitleCollapsed(),
                isContentScrolled = nodeListState.isScrolledFromTop(),
                onNavigateToProviders = onNavigateToProviders,
                onOpenPanel = onOpenPanel,
                locateCurrentProxy = locateCurrentProxy,
                showSortPopup = showSortPopup,
                onShowSortPopupChange = { showSortPopup = it },
                displayMode = displayMode,
                sortMode = sortMode,
                onDisplayModeSelected = proxyViewModel::setDisplayMode,
                onSortSelected = proxyViewModel::setSortMode,
                onTestAllDelay = { currentGroupName?.let { proxyViewModel.testDelay(it) } },
                searchVisible = nodeSearchVisible,
                searchQuery = nodeSearchQuery,
                onSearchQueryChange = { nodeSearchQuery = it },
                onSearchToggle = {
                    nodeSearchVisible = !nodeSearchVisible
                    if (!nodeSearchVisible) nodeSearchQuery = ""
                },
                onTitleScrollTop = {
                    coroutineScope.launch {
                        if (nodeListState.firstVisibleItemIndex > 0 || nodeListState.firstVisibleItemScrollOffset > 0) { nodeListState.animateScrollToItem(0) }
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        if (currentGroup == null) { CenteredText(firstLine = FlyTxt.Proxy.Empty.NoNodes, secondLine = FlyTxt.Proxy.Empty.Hint, showEmptyResourceIllustration = true) } else {
            NodeListPage(
                group = currentGroup,
                allGroups = proxyGroups,
                displayMode = displayMode,
                sortMode = sortMode,
                testingGroupNames = testingGroupNames,
                testingProxyNames = testingProxyNames,
                mainInnerPadding = mainInnerPadding,
                outerInnerPadding = scaffoldPadding,
                scrollBehavior = scrollBehavior,
                listState = nodeListState,
                onSelectProxy = { groupName, proxyName -> proxyViewModel.selectProxy(groupName, proxyName) },
                onForceSelectProxy = { groupName, proxyName -> proxyViewModel.forceSelectProxy(groupName, proxyName) },
                onTestDelay = requestDelayTest,
                onTestProxyDelay = { proxyName -> currentGroup.name.let { groupName -> proxyViewModel.testProxyDelay(groupName, proxyName) } },
                onScrollDirectionChanged = {},
                searchQuery = nodeSearchQuery,
                searchVisible = nodeSearchVisible,
                onSearchVisibleChange = { nodeSearchVisible = it },
                testProgress = delayTestProgress,
            )
        }
    }
}

@Composable
private fun DetailFab(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = scaleIn(), exit = scaleOut()) {
        FloatingActionButton(modifier = Modifier.padding(end = UiDp.dp20, bottom = UiDp.dp24), onClick = onClick) {
            Icon(imageVector = FlyCat.Speed, contentDescription = FlyTxt.Proxy.Action.Test, tint = MiuixTheme.colorScheme.onPrimary)
        }
    }
}

/** 双栏右栏顶栏参数适配，负责标题、链路与搜索状态转发。 */
@Composable
private fun DetailTopBar(
    title: String,
    chain: List<String>,
    groupIconLookup: ((String) -> String?)?,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    titleCollapsed: Boolean,
    isContentScrolled: Boolean,
    onNavigateToProviders: (() -> Unit)?,
    onOpenPanel: (() -> Unit)?,
    locateCurrentProxy: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    displayMode: com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode,
    sortMode: com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode,
    onDisplayModeSelected: (com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode) -> Unit,
    onSortSelected: (com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode) -> Unit,
    onTestAllDelay: () -> Unit,
    onTitleScrollTop: () -> Unit = {},
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchToggle: (() -> Unit)? = null,
) {
    // 双栏右栏标题即组名，单段可点滚到节点列表顶。
    ProxyTopBar(
        title = title,
        chain = chain,
        groupIconLookup = groupIconLookup,
        titleRootLabel = title,
        onTitleRootClick = onTitleScrollTop,
        scrollBehavior = scrollBehavior,
        titleCollapsed = titleCollapsed,
        isContentScrolled = isContentScrolled,
        showBack = false,
        onBack = {},
        onNavigateToProviders = onNavigateToProviders,
        onTestAllDelay = onTestAllDelay,
        onLocateCurrentProxy = locateCurrentProxy,
        showSortPopup = showSortPopup,
        onShowSortPopupChange = onShowSortPopupChange,
        displayMode = displayMode,
        sortMode = sortMode,
        onDisplayModeSelected = onDisplayModeSelected,
        onSortSelected = onSortSelected,
        searchVisible = searchVisible,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchToggle = onSearchToggle,
    )
}
