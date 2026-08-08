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

@file:Suppress("DuplicatedCode", "FunctionName", "UnnecessaryVariable")

package com.github.yumeyucca.yumebox.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.github.yumeyucca.yumebox.data.model.ProxySortMode
import com.github.yumeyucca.yumebox.domain.model.ProxyGroupInfo
import com.github.yumeyucca.yumebox.presentation.component.*
import com.github.yumeyucca.yumebox.presentation.icon.Yume
import com.github.yumeyucca.yumebox.presentation.icon.yume.*
import com.github.yumeyucca.yumebox.presentation.screen.node.NodeSortPopup
import com.github.yumeyucca.yumebox.presentation.screen.node.nodeGroupItems
import com.github.yumeyucca.yumebox.presentation.theme.*
import com.github.yumeyucca.yumebox.presentation.viewmodel.ProxyViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ProxyScreenVmState(
    val proxyGroups: List<ProxyGroupInfo>,
    val testingGroupNames: Set<String>,
    val sortMode: ProxySortMode,
    val uiSelectedGroupName: String?,
)

@Composable
private fun rememberProxyScreenVmState(proxyViewModel: ProxyViewModel): ProxyScreenVmState {
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsState()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsState()
    val sortMode by proxyViewModel.sortMode.collectAsState()
    val uiSelectedGroupName by proxyViewModel.uiSelectedGroupName.collectAsState()
    return remember(
        proxyGroups,
        testingGroupNames,
        sortMode,
        uiSelectedGroupName,
    ) {
        ProxyScreenVmState(
            proxyGroups = proxyGroups,
            testingGroupNames = testingGroupNames,
            sortMode = sortMode,
            uiSelectedGroupName = uiSelectedGroupName,
        )
    }
}

@Composable
fun ProxyPager(
    mainInnerPadding: PaddingValues,
    onNavigateToProviders: (() -> Unit)?,
    isActive: Boolean,
    @Suppress("UNUSED_PARAMETER") windowLayoutMode: WindowLayoutMode = WindowLayoutMode.Compact,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    val screen = rememberProxyScreenVmState(proxyViewModel)
    val proxyGroups = screen.proxyGroups
    val testingGroupNames = screen.testingGroupNames
    val sortMode = screen.sortMode
    val uiSelectedGroupName = screen.uiSelectedGroupName
    val groupScrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)
    val topBarHazeState = LocalTopBarHazeState.current

    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    val inSplitShell = LocalDetailNavigator.current.isSplitShell
    val groupSelection = rememberProxyGroupSelectionState(
        proxyGroups = proxyGroups,
        onRefreshGroup = proxyViewModel::refreshGroup,
        retainLastKnownGroup = true,
        controlledSelectedGroupName = if (inSplitShell) uiSelectedGroupName else null,
        onControlledSelectedGroupNameChange = if (inSplitShell) proxyViewModel::selectUiGroup else null,
    )
    val selectedGroupName = groupSelection.selectedGroupName
    val displayGroup = groupSelection.displayGroup

    LaunchedEffect(inSplitShell, proxyGroups, selectedGroupName) {
        if (!inSplitShell || proxyGroups.isEmpty()) return@LaunchedEffect
        if (selectedGroupName == null || proxyGroups.none { it.name == selectedGroupName }) {
            groupSelection.selectGroup(proxyGroups.first())
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val groupListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val nodeListState = rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }

    val requestSelectedGroupDelayTest = remember(coroutineScope, nodeListState, selectedGroupName, proxyViewModel) {
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
    val locateCurrentProxy = remember(coroutineScope, displayGroup, nodeListState, selectedGroupName) {
        if (selectedGroupName == null) {
            null
        } else {
            displayGroup?.takeIf { group -> group.name == selectedGroupName }?.let { group ->
                fun() {
                    val proxyIndex = group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                    if (proxyIndex < 0) return
                    coroutineScope.launch {
                        nodeListState.animateLocateToItem(proxyIndex + 1)
                    }
                }
            }
        }
    }

    BackHandler(enabled = !inSplitShell && selectedGroupName != null) {
        groupSelection.clearSelection()
    }

    LaunchedEffect(isActive) { proxyViewModel.ensureCoreLoaded(isActive, source = "proxy_page") }

    DisposableEffect(proxyViewModel) {
        onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_page") }
    }

    Scaffold(
        topBar = {
            ProxyTopBar(
                title = YumeTxt.Proxy.Title,
                scrollBehavior = groupScrollBehavior,
                showBack = false,
                onBack = {},
                onNavigateToProviders = onNavigateToProviders,
                onLocateCurrentProxy = locateCurrentProxy,
                showSortPopup = showSortPopup,
                onShowSortPopupChange = { showSortPopup = it },
                sortMode = sortMode,
                onSortSelected = proxyViewModel::setSortMode,
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier.fillMaxSize().let { mod ->
                if (topBarHazeState != null) mod.hazeSource(state = topBarHazeState) else mod
            }) {
            if (inSplitShell) {
                if (proxyGroups.isEmpty()) {
                    CenteredText(
                        firstLine = YumeTxt.Proxy.Empty.NoNodes,
                        secondLine = YumeTxt.Proxy.Empty.Hint,
                        showEmptyResourceIllustration = true,
                    )
                } else {
                    ProxyContent(
                        proxyGroups = proxyGroups,
                        scrollBehavior = groupScrollBehavior,
                        innerPadding = scaffoldPadding,
                        mainInnerPadding = mainInnerPadding,
                        testingGroupNames = testingGroupNames,
                        onGroupClick = groupSelection.selectGroup,
                        onGroupTest = { group -> proxyViewModel.testDelay(group.name) },
                        listState = groupListState,
                    )
                }
            } else {
                AnimatedContent(
                    targetState = selectedGroupName,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = 340, easing = AnimationSpecs.Legacy),
                                initialOffsetX = { it },
                            ) + fadeIn(animationSpec = tween(durationMillis = 140))) togetherWith (slideOutHorizontally(
                                animationSpec = tween(durationMillis = 300, easing = AnimationSpecs.Legacy),
                                targetOffsetX = { -it / 3 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 140)))
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = 300, easing = AnimationSpecs.Legacy),
                                initialOffsetX = { -it / 3 },
                            ) + fadeIn(animationSpec = tween(durationMillis = 140))) togetherWith (slideOutHorizontally(
                                animationSpec = tween(durationMillis = 340, easing = AnimationSpecs.Legacy),
                                targetOffsetX = { it },
                            ) + fadeOut(animationSpec = tween(durationMillis = 140)))
                        }
                    },
                    label = "proxy_content_slide",
                ) { targetGroupName ->
                    if (targetGroupName == null) {
                        if (proxyGroups.isEmpty()) {
                            CenteredText(
                                firstLine = YumeTxt.Proxy.Empty.NoNodes,
                                secondLine = YumeTxt.Proxy.Empty.Hint,
                                showEmptyResourceIllustration = true,
                            )
                        } else {
                            ProxyContent(
                                proxyGroups = proxyGroups,
                                scrollBehavior = groupScrollBehavior,
                                innerPadding = scaffoldPadding,
                                mainInnerPadding = mainInnerPadding,
                                testingGroupNames = testingGroupNames,
                                onGroupClick = groupSelection.selectGroup,
                                onGroupTest = { group -> proxyViewModel.testDelay(group.name) },
                                listState = groupListState,
                            )
                        }
                    } else {
                        val currentGroup = groupSelection.selectedGroup ?: displayGroup
                        NodeListPage(
                            group = currentGroup,
                            sortMode = sortMode,
                            testingGroupNames = testingGroupNames,
                            mainInnerPadding = mainInnerPadding,
                            outerInnerPadding = scaffoldPadding,
                            scrollBehavior = groupScrollBehavior,
                            listState = nodeListState,
                            onSelectProxy = { groupName, proxyName ->
                                proxyViewModel.selectProxy(groupName, proxyName)
                            },
                            onTestProxy = { groupName, proxyName ->
                                proxyViewModel.testProxyDelay(groupName, proxyName)
                            },
                            onTestDelay = requestSelectedGroupDelayTest,
                            onScrollDirectionChanged = {},
                            useAdaptiveGrid = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyContent(
    proxyGroups: List<ProxyGroupInfo>,
    scrollBehavior: ScrollBehavior,
    innerPadding: PaddingValues,
    mainInnerPadding: PaddingValues,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    onGroupTest: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String>,
    listState: LazyListState,
) {
    val spacing = LocalSpacing.current
    ScreenLazyColumn(
        lazyListState = listState,
        scrollBehavior = scrollBehavior,
        innerPadding = innerPadding,
        enableGlobalScroll = true,
        contentPadding = PaddingValues(
            start = UiDp.dp12,
            end = UiDp.dp12,
            top = innerPadding.calculateTopPadding() + UiDp.dp14,
            bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
        ),
    ) {
        nodeGroupItems(
            groups = proxyGroups,
            onGroupClick = onGroupClick,
            onGroupTest = onGroupTest,
            testingGroupNames = testingGroupNames,
            itemVerticalPadding = UiDp.dp6,
        )
    }
}

@Composable
internal fun ProxyShellNodeDetailContent(
    mainInnerPadding: PaddingValues,
    onNavigateToProviders: (() -> Unit)? = null,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    val screen = rememberProxyScreenVmState(proxyViewModel)
    val proxyGroups = screen.proxyGroups
    val testingGroupNames = screen.testingGroupNames
    val sortMode = screen.sortMode
    val uiSelectedGroupName = screen.uiSelectedGroupName
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
    var showSortPopup by rememberSaveable { mutableStateOf(false) }

    // The tablet detail pane can outlive the left pager during a destination transition. Keep a
    // dedicated sync owner so a cold local core is queried even when the left page is not resumed.
    LaunchedEffect(proxyViewModel) {
        proxyViewModel.ensureCoreLoaded(true, source = "proxy_detail")
    }

    DisposableEffect(proxyViewModel) {
        onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_detail") }
    }

    LaunchedEffect(proxyGroups, selectedGroupName) {
        if (proxyGroups.isEmpty()) return@LaunchedEffect
        if (selectedGroupName == null || proxyGroups.none { it.name == selectedGroupName }) {
            groupSelection.selectGroup(proxyGroups.first())
        }
    }

    val activeGroupName = selectedGroupName ?: currentGroup?.name

    AnimatedContent(
        targetState = activeGroupName,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val fromIndex = initialState?.let { name -> proxyGroups.indexOfFirst { it.name == name } } ?: -1
            val toIndex = targetState?.let { name -> proxyGroups.indexOfFirst { it.name == name } } ?: -1
            verticalBounceContentTransform(forward = toIndex >= fromIndex)
        },
        label = "proxy_shell_group_switch",
    ) { groupName ->
        val pageGroup = groupName?.let { name -> proxyGroups.firstOrNull { it.name == name } } ?: currentGroup
        // Adaptive grid owns the real scroll surface on the shell right pane.
        val nodeGridState = rememberSaveable(groupName, saver = LazyGridState.Saver) { LazyGridState() }
        val requestSelectedGroupDelayTest = remember(coroutineScope, nodeGridState, groupName, proxyViewModel) {
            {
                val targetGroupName = groupName ?: return@remember
                coroutineScope.launch {
                    if (nodeGridState.isScrolledFromTop()) {
                        nodeGridState.animateScrollToItem(0)
                    }
                    proxyViewModel.testDelay(targetGroupName)
                }
            }
        }
        val locateCurrentProxy = remember(coroutineScope, pageGroup, nodeGridState, groupName) {
            if (groupName == null || pageGroup == null) {
                null
            } else {
                pageGroup.takeIf { group -> group.name == groupName }?.let { group ->
                    fun() {
                        val proxyIndex = group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                        if (proxyIndex < 0) return
                        coroutineScope.launch {
                            nodeGridState.animateLocateToItem(proxyIndex + 1)
                        }
                    }
                }
            }
        }
        Scaffold(
            topBar = {
                ProxyTopBar(
                    title = pageGroup?.name ?: YumeTxt.Proxy.Title,
                    scrollBehavior = scrollBehavior,
                    showBack = false,
                    onBack = {},
                    onNavigateToProviders = onNavigateToProviders,
                    onLocateCurrentProxy = locateCurrentProxy,
                    showSortPopup = showSortPopup,
                    onShowSortPopupChange = { showSortPopup = it },
                    sortMode = sortMode,
                    onSortSelected = proxyViewModel::setSortMode,
                )
            },
        ) { scaffoldPadding ->
            if (pageGroup == null) {
                CenteredText(
                    firstLine = YumeTxt.Proxy.Empty.NoNodes,
                    secondLine = YumeTxt.Proxy.Empty.Hint,
                    showEmptyResourceIllustration = true,
                )
            } else {
                NodeListPage(
                    group = pageGroup,
                    sortMode = sortMode,
                    testingGroupNames = testingGroupNames,
                    mainInnerPadding = mainInnerPadding,
                    outerInnerPadding = scaffoldPadding,
                    scrollBehavior = scrollBehavior,
                    listState = remember { LazyListState() },
                    gridState = nodeGridState,
                    onSelectProxy = { selectedGroup, proxyName ->
                        proxyViewModel.selectProxy(selectedGroup, proxyName)
                    },
                    onTestProxy = { selectedGroup, proxyName ->
                        proxyViewModel.testProxyDelay(selectedGroup, proxyName)
                    },
                    onTestDelay = requestSelectedGroupDelayTest,
                    onScrollDirectionChanged = {},
                    useAdaptiveGrid = true,
                )
            }
        }
    }
}

@Composable
private fun ProxyTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    showBack: Boolean,
    onBack: () -> Unit,
    onNavigateToProviders: (() -> Unit)?,
    onLocateCurrentProxy: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    sortMode: ProxySortMode,
    onSortSelected: (ProxySortMode) -> Unit,
) {
    val spacing = AppTheme.spacing

    TopBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIconPadding = UiDp.dp24,
        actionIconPadding = UiDp.dp24,
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        MiuixIcons.Back,
                        contentDescription = YumeTxt.Component.Navigation.Back,
                    )
                }
            }
        },
        actions = {
            if (onLocateCurrentProxy != null) {
                IconButton(
                    modifier = Modifier.padding(end = spacing.space12),
                    onClick = onLocateCurrentProxy,
                ) {
                    Icon(Yume.Eye, contentDescription = YumeTxt.Proxy.Action.LocateCurrent)
                }
            }
            Box {
                IconButton(onClick = { onShowSortPopupChange(true) }) {
                    Icon(
                        Yume.ListChevronsUpDown,
                        contentDescription = YumeTxt.Proxy.Action.Sort,
                    )
                }
                NodeSortPopup(
                    show = showSortPopup,
                    onDismiss = { onShowSortPopupChange(false) },
                    sortMode = sortMode,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onNavigateToProviders = onNavigateToProviders,
                    onSortSelected = onSortSelected,
                )
            }
        },
    )
}
