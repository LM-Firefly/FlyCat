/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.feature.proxy.presentation.screen

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.feature.proxy.presentation.screen.node.NodeSortPopup
import com.github.yumelira.yumebox.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Speed
import com.github.yumelira.yumebox.presentation.theme.UiDp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Right-pane node detail for the tablet dual-pane shell.
 * Shares the same [ProxyViewModel] (Koin singleton) as the left-pane [ProxyPager], synchronised via [ProxyViewModel.uiSelectedGroupName].
 *
 * TopBar and FAB are extracted into stable sub-composables so that frequent [testingGroupNames]/[testingProxyNames] updates only trigger recomposition in the node list, not the entire Scaffold chrome.
 */
@Composable
fun ProxyShellNodeDetail(mainInnerPadding: PaddingValues, onNavigateToProviders: (() -> Unit)? = null, onOpenPanel: (() -> Unit)? = null) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsStateWithLifecycle()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsStateWithLifecycle()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsStateWithLifecycle()
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
    var fabHidden by rememberSaveable { mutableStateOf(false) }
    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    val nodeListState = rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(proxyViewModel) { proxyViewModel.ensureCoreLoaded(true, source = "proxy_detail") }
    DisposableEffect(proxyViewModel) { onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_detail") } }
    val requestDelayTest = remember(coroutineScope, nodeListState, selectedGroupName, proxyViewModel) {
        {
            val groupName = selectedGroupName ?: return@remember
            coroutineScope.launch {
                if (nodeListState.firstVisibleItemIndex > 0 || nodeListState.firstVisibleItemScrollOffset > 0) {
                    nodeListState.animateScrollToItem(0)
                }
                proxyViewModel.testDelay(groupName)
            }
        }
    }
    val locateCurrentProxy =
        remember(coroutineScope, currentGroup, nodeListState, selectedGroupName, displayMode) {
            if (selectedGroupName == null || currentGroup == null) {
                null
            } else {
                fun() {
                    val proxyIndex = currentGroup.proxies.indexOfFirst { proxy -> proxy.name == currentGroup.now }
                    if (proxyIndex < 0) return
                    val listItemIndex =
                        if (displayMode.isSingleColumn) proxyIndex + 1
                        else proxyIndex / 2 + 1
                    coroutineScope.launch {
                        nodeListState.animateLocateToItem(listItemIndex)
                    }
                }
            }
        }
    Scaffold(
        floatingActionButton = {
            DetailFab(
                visible = currentGroupName != null && !fabHidden && currentGroupName !in testingGroupNames,
                onClick = { requestDelayTest() },
            )
        },
        topBar = {
            DetailTopBar(
                title = currentGroupName ?: FlyTxt.Proxy.Title,
                scrollBehavior = scrollBehavior,
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
            )
        },
    ) { scaffoldPadding ->
        if (currentGroup == null) {
            CenteredText(
                firstLine = FlyTxt.Proxy.Empty.NoNodes,
                secondLine = FlyTxt.Proxy.Empty.Hint,
                showEmptyResourceIllustration = true,
            )
        } else {
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
                onScrollDirectionChanged = { hidden -> fabHidden = hidden },
            )
        }
    }
}

/** Stable FAB sub-composable — only recomposes when [visible] or [onClick] changes. */
@Composable
private fun DetailFab(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = scaleIn(), exit = scaleOut()) {
        FloatingActionButton(modifier = Modifier.padding(end = UiDp.dp20, bottom = UiDp.dp24), onClick = onClick) {
            Icon(
                imageVector = Yume.Speed,
                contentDescription = FlyTxt.Proxy.Action.Test,
                tint = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Stable TopBar sub-composable — only recomposes when title/sort/displayMode change. */
@Composable
private fun DetailTopBar(
    title: String,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    onNavigateToProviders: (() -> Unit)?,
    onOpenPanel: (() -> Unit)?,
    locateCurrentProxy: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    displayMode: com.github.yumelira.yumebox.core.model.ProxyDisplayMode,
    sortMode: com.github.yumelira.yumebox.core.model.ProxySortMode,
    onDisplayModeSelected: (com.github.yumelira.yumebox.core.model.ProxyDisplayMode) -> Unit,
    onSortSelected: (com.github.yumelira.yumebox.core.model.ProxySortMode) -> Unit,
    onTestAllDelay: () -> Unit,
) {
    ProxyTopBar(
        title = title,
        scrollBehavior = scrollBehavior,
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
    )
}
