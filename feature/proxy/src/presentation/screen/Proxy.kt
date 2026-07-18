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

package com.github.yumelira.yumebox.feature.proxy.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.core.model.ProxyGroupInfo
import com.github.yumelira.yumebox.core.model.ProxySortMode
import com.github.yumelira.yumebox.feature.proxy.presentation.screen.node.nodeGridItems
import com.github.yumelira.yumebox.feature.proxy.presentation.screen.node.nodeGroupItems
import com.github.yumelira.yumebox.feature.proxy.presentation.screen.node.NodeSortPopup
import com.github.yumelira.yumebox.feature.proxy.presentation.util.KeepLazyListTopAnchorOnReorder
import com.github.yumelira.yumebox.feature.proxy.presentation.viewmodel.ProxyViewModel
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.LocalPagerState
import com.github.yumelira.yumebox.presentation.component.LocalTopBarHazeState
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`List-chevrons-up-down`
import com.github.yumelira.yumebox.presentation.icon.yume.Chromium
import com.github.yumelira.yumebox.presentation.icon.yume.Eye
import com.github.yumelira.yumebox.presentation.icon.yume.Folders
import com.github.yumelira.yumebox.presentation.icon.yume.Speed
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.LocalSpacing
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.chrisbanes.haze.hazeSource
import dev.oom_wg.purejoy.mlang.MLang
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun LazyListState.isScrolledFromTop(): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

// animateScrollToItem races across arbitrary distances at full speed, so locating a far-away
// node reads as a blink. Cap the animated stretch at roughly one viewport: snap silently to
// just outside it, then glide the remainder in with a fixed-duration decelerating tween.
private suspend fun LazyListState.animateLocateToItem(targetIndex: Int) {
    if (layoutInfo.visibleItemsInfo.isEmpty()) {
        scrollToItem(targetIndex)
        return
    }
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val visible = layoutInfo.visibleItemsInfo
    val avgItemSize =
        (visible.sumOf { it.size } / visible.size + layoutInfo.mainAxisItemSpacing).coerceAtLeast(1)
    val approachItems = viewport / avgItemSize + 1
    val distanceItems = targetIndex - firstVisibleItemIndex
    if (abs(distanceItems) > approachItems) {
        val preIndex =
            if (distanceItems > 0) targetIndex - approachItems else targetIndex + approachItems
        scrollToItem(preIndex.coerceAtLeast(0))
    }
    val remaining =
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset?.toFloat()
            ?: ((targetIndex - firstVisibleItemIndex) * avgItemSize.toFloat() -
                firstVisibleItemScrollOffset)
    animateScrollBy(
        value = remaining,
        animationSpec = tween(durationMillis = 650, easing = AnimationSpecs.EmphasizedDecelerate),
    )
    // Node cards are uniform so the estimate lands exactly; settle any residual drift quietly.
    val residual = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset
    when {
        residual == null -> scrollToItem(targetIndex)
        residual != 0 -> scrollBy(residual.toFloat())
    }
}

@Composable
fun ProxyPager(
    mainInnerPadding: PaddingValues,
    onNavigateToProviders: (() -> Unit)?,
    onOpenDashboard: () -> Unit = {},
    isActive: Boolean,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()

    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsStateWithLifecycle()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsStateWithLifecycle()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsStateWithLifecycle()
    val sortMode by proxyViewModel.sortMode.collectAsStateWithLifecycle()
    val singleNodeTest by proxyViewModel.singleNodeTest.collectAsStateWithLifecycle()
    val displayMode by proxyViewModel.displayMode.collectAsStateWithLifecycle()
    val groupScrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)
    val pagerState = LocalPagerState.current
    val topBarHazeState = LocalTopBarHazeState.current

    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    val groupSelection =
        rememberProxyGroupSelectionState(
            proxyGroups = proxyGroups,
            onRefreshGroup = proxyViewModel::refreshGroup,
            retainLastKnownGroup = true,
        )
    val selectedGroupName = groupSelection.selectedGroupName
    val displayGroup = groupSelection.displayGroup
    val fabGroup = displayGroup
    val isFabTesting = fabGroup?.name?.let(testingGroupNames::contains) == true
    val coroutineScope = rememberCoroutineScope()
    val groupListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val nodeListState =
        rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }

    var fabHidden by rememberSaveable { mutableStateOf(false) }

    val requestSelectedGroupDelayTest =
        remember(coroutineScope, nodeListState, selectedGroupName, proxyViewModel) {
            {
                val groupName = selectedGroupName ?: return@remember
                coroutineScope.launch {
                    if (nodeListState.isScrolledFromTop()) {
                        nodeListState.scrollToItem(0)
                    }
                    proxyViewModel.testDelay(groupName)
                }
            }
        }
    val locateCurrentProxy =
        remember(coroutineScope, displayGroup, nodeListState, selectedGroupName, displayMode) {
            if (selectedGroupName == null) {
                null
            } else {
                displayGroup?.takeIf { group -> group.name == selectedGroupName }?.let { group ->
                    fun() {
                        val proxyIndex =
                            group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                        if (proxyIndex < 0) return
                        // +1 accounts for the __refresh_indicator__ header item at index 0.
                        // In dual-column mode each row holds 2 proxies, so divide by 2.
                        val listItemIndex =
                            if (displayMode.isSingleColumn) proxyIndex + 1
                            else proxyIndex / 2 + 1
                        coroutineScope.launch {
                            nodeListState.animateLocateToItem(listItemIndex)
                        }
                    }
                }
            }
        }

    BackHandler(enabled = selectedGroupName != null) { groupSelection.clearSelection() }

    LaunchedEffect(isActive) { proxyViewModel.ensureCoreLoaded(isActive, source = "proxy_page") }

    DisposableEffect(proxyViewModel) {
        onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_page") }
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible =
                    selectedGroupName != null &&
                        fabGroup != null &&
                        !fabHidden &&
                        !isFabTesting &&
                        !pagerState.isScrollInProgress,
                enter = scaleIn(),
                exit = scaleOut(),
                label = "proxy_test_fab_visibility",
            ) {
                FloatingActionButton(
                    modifier = Modifier.padding(end = UiDp.dp20, bottom = UiDp.dp85),
                    onClick = {
                        if (fabGroup == null) return@FloatingActionButton
                        requestSelectedGroupDelayTest()
                    },
                ) {
                    Icon(
                        imageVector = Yume.Speed,
                        contentDescription = MLang.Proxy.Action.Test,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        topBar = {
            ProxyTopBar(
                title = MLang.Proxy.Title,
                scrollBehavior = groupScrollBehavior,
                showBack = false,
                onBack = {},
                onNavigateToProviders = onNavigateToProviders,
                onOpenDashboard = onOpenDashboard,
                onTestAllDelay = { proxyViewModel.testDelay() },
                onLocateCurrentProxy = locateCurrentProxy,
                showSortPopup = showSortPopup,
                onShowSortPopupChange = { showSortPopup = it },
                displayMode = displayMode,
                sortMode = sortMode,
                onDisplayModeSelected = proxyViewModel::setDisplayMode,
                onSortSelected = proxyViewModel::setSortMode,
            )
        },
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize().let { mod ->
                    if (topBarHazeState != null) mod.hazeSource(state = topBarHazeState) else mod
                }
        ) {
            AnimatedContent(
                targetState = selectedGroupName,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(
                            animationSpec =
                                tween(durationMillis = 340, easing = AnimationSpecs.Legacy),
                            initialOffsetX = { it },
                        ) + fadeIn(animationSpec = tween(durationMillis = 140))) togetherWith
                            (slideOutHorizontally(
                                animationSpec =
                                    tween(durationMillis = 300, easing = AnimationSpecs.Legacy),
                                targetOffsetX = { -it / 3 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 140)))
                    } else {
                        (slideInHorizontally(
                            animationSpec =
                                tween(durationMillis = 300, easing = AnimationSpecs.Legacy),
                            initialOffsetX = { -it / 3 },
                        ) + fadeIn(animationSpec = tween(durationMillis = 140))) togetherWith
                            (slideOutHorizontally(
                                animationSpec =
                                    tween(durationMillis = 340, easing = AnimationSpecs.Legacy),
                                targetOffsetX = { it },
                            ) + fadeOut(animationSpec = tween(durationMillis = 140)))
                    }
                },
                label = "proxy_content_slide",
            ) { targetGroupName ->
                if (targetGroupName == null) {
                    if (proxyGroups.isEmpty()) {
                        CenteredText(
                            firstLine = MLang.Proxy.Empty.NoNodes,
                            secondLine = MLang.Proxy.Empty.Hint,
                            showEmptyResourceIllustration = true,
                        )
                    } else {
                        ProxyContent(
                            proxyGroups = proxyGroups,
                            displayMode = displayMode,
                            scrollBehavior = groupScrollBehavior,
                            listState = groupListState,
                            innerPadding = it,
                            mainInnerPadding = mainInnerPadding,
                            testingGroupNames = testingGroupNames,
                            onGroupClick = groupSelection.selectGroup,
                            onGroupDelayTestClick = { group -> proxyViewModel.testDelay(group.name) },
                            onGroupBoundsChanged = { _, _ -> },
                        )
                    }
                } else {
                    val currentGroup = groupSelection.selectedGroup ?: displayGroup
                    NodeListPage(
                        group = currentGroup,
                        allGroups = proxyGroups,
                        displayMode = displayMode,
                        sortMode = sortMode,
                        testingGroupNames = testingGroupNames,
                        testingProxyNames = testingProxyNames,
                        mainInnerPadding = mainInnerPadding,
                        outerInnerPadding = it,
                        scrollBehavior = groupScrollBehavior,
                        listState = nodeListState,
                        onSelectProxy = { groupName, proxyName ->
                            proxyViewModel.selectProxy(groupName, proxyName)
                        },
                        onForceSelectProxy = { groupName, proxyName ->
                            proxyViewModel.forceSelectProxy(groupName, proxyName)
                        },
                        onTestDelay = requestSelectedGroupDelayTest,
                        onTestProxyDelay = { proxyName ->
                            currentGroup?.name?.let { groupName ->
                                proxyViewModel.testProxyDelay(groupName, proxyName)
                            }
                        },
                        onScrollDirectionChanged = { hidden -> fabHidden = hidden },
                        singleNodeTestEnabled = singleNodeTest,
                    )
                }
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
    onOpenDashboard: () -> Unit = {},
    onTestAllDelay: () -> Unit = {},
    onLocateCurrentProxy: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    onDisplayModeSelected: (ProxyDisplayMode) -> Unit,
    onSortSelected: (ProxySortMode) -> Unit,
) {
    val spacing = AppTheme.spacing

    TopBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIconPadding = UiDp.dp24,
        actionIconPadding = UiDp.dp24,
        navigationIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = MLang.Component.Navigation.Back)
                    }
                } else {
                    if (onNavigateToProviders != null) {
                        IconButton(onClick = onNavigateToProviders) {
                            Icon(Yume.Folders, contentDescription = MLang.Providers.Title)
                        }
                    }
                }
            }
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                if (onLocateCurrentProxy != null) {
                    IconButton(
                        modifier = Modifier.padding(end = spacing.space12),
                        onClick = onLocateCurrentProxy,
                    ) {
                        Icon(Yume.Eye, contentDescription = "Eye current proxy")
                    }
                }
                IconButton(onClick = onOpenDashboard) {
                    Icon(Yume.Chromium, contentDescription = "Dashboard")
                }
                IconButton(onClick = onTestAllDelay) {
                    Icon(Yume.Speed, contentDescription = MLang.Proxy.Action.Test)
                }
                Box {
                    IconButton(onClick = { onShowSortPopupChange(true) }) {
                        Icon(Yume.`List-chevrons-up-down`, contentDescription = MLang.Proxy.Action.Sort)
                    }
                    NodeSortPopup(
                        show = showSortPopup,
                        onDismiss = { onShowSortPopupChange(false) },
                        displayMode = displayMode,
                        sortMode = sortMode,
                        alignment = PopupPositionProvider.Align.BottomEnd,
                        onDisplayModeSelected = onDisplayModeSelected,
                        onSortSelected = onSortSelected,
                    )
                }
            }
        },
    )
}

@Composable
private fun NodeListPage(
    group: ProxyGroupInfo?,
    allGroups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    sortMode: ProxySortMode,
    testingGroupNames: Set<String>,
    testingProxyNames: Set<String>,
    mainInnerPadding: PaddingValues,
    outerInnerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    onSelectProxy: (groupName: String, proxyName: String) -> Unit,
    onForceSelectProxy: (groupName: String, proxyName: String) -> Unit,
    onTestDelay: () -> Unit,
    onTestProxyDelay: (proxyName: String) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit,
    singleNodeTestEnabled: Boolean = true,
) {
    if (group == null) {
        CenteredText(
            firstLine = MLang.Proxy.Empty.NoNodes,
            secondLine = MLang.Proxy.Empty.Hint,
            showEmptyResourceIllustration = true,
        )
        return
    }
    val spacing = LocalSpacing.current
    val isTesting = testingGroupNames.contains(group.name)
    val listItemKeys = remember(group.proxies) { group.proxies.map { it.name } }
    val groupMap = remember(allGroups) { allGroups.associateBy { it.name } }
    val resolveChildNodeName = remember(groupMap) {
        { proxy: Proxy ->
            val childGroup = groupMap[proxy.name]
            if (childGroup == null || childGroup.type !in Proxy.Type.groupTypes || childGroup.now.isBlank()) {
                null
            } else {
                val currentProxy = childGroup.proxies.firstOrNull { it.name == childGroup.now }
                (currentProxy?.name ?: childGroup.now)
                    .trim()
                    .ifBlank { MLang.Proxy.Mode.Direct }
                    .takeIf { it.isNotBlank() && it != proxy.name.trim() }
            }
        }
    }

    KeepLazyListTopAnchorOnReorder(
        listState = listState,
        itemKeys = listItemKeys,
        enabled = sortMode == ProxySortMode.BY_LATENCY,
        scrollToTopOnEnabled = true,
    )

    LaunchedEffect(isTesting) {
        if (isTesting && listState.isScrolledFromTop()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = outerInnerPadding.calculateTopPadding()),
    ) {
        if (group.chainPath.isNotEmpty()) {
            ProxyChainIndicator(
                chain = group.chainPath,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
        ScreenLazyColumn(
            modifier = Modifier.weight(1f),
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = outerInnerPadding,
            enableGlobalScroll = true,
            onScrollDirectionChanged = onScrollDirectionChanged,
            contentPadding =
                PaddingValues(
                    start = UiDp.dp12,
                    end = UiDp.dp12,
                    top = if (group.chainPath.isNotEmpty()) UiDp.dp6 else outerInnerPadding.calculateTopPadding() + UiDp.dp12,
                bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
            ),
        ) {
            item(key = "__refresh_indicator__") {
                AnimatedVisibility(
                    visible = isTesting,
                    enter =
                        expandVertically(
                            animationSpec =
                                tween(durationMillis = AnimationSpecs.Proxy.RefreshIndicatorDuration),
                            expandFrom = Alignment.Top,
                        ) +
                            fadeIn(
                                animationSpec =
                                    tween(
                                        durationMillis =
                                            AnimationSpecs.Proxy.RefreshIndicatorFadeDuration
                                    )
                            ),
                    exit =
                        shrinkVertically(
                            animationSpec =
                                tween(durationMillis = AnimationSpecs.Proxy.RefreshIndicatorDuration),
                            shrinkTowards = Alignment.Top,
                        ) +
                            fadeOut(
                                animationSpec =
                                    tween(
                                        durationMillis =
                                            AnimationSpecs.Proxy.RefreshIndicatorFadeDuration
                                    )
                            ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = UiDp.dp12),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(UiDp.dp6),
                    ) {
                        InfiniteProgressIndicator(modifier = Modifier.size(UiDp.dp24))
                        Text(
                            text = MLang.Proxy.Testing.InProgress,
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            nodeGridItems(
                proxies = group.proxies,
                selectedProxyName = group.now,
                pinnedProxyName = group.fixed,
                displayMode = displayMode,
                onProxyClick = { proxyName ->
                    if (group.type == Proxy.Type.Selector) {
                        onSelectProxy(group.name, proxyName)
                    } else if (
                        group.type == Proxy.Type.URLTest ||
                        group.type == Proxy.Type.Fallback
                    ) {
                        val target = if (proxyName == group.fixed) "" else proxyName
                        onForceSelectProxy(group.name, target)
                    } else {
                        onTestDelay()
                    }
                },
                isDelayTesting = isTesting,
                testingProxyNames = testingProxyNames,
                onSingleNodeTestClick = { onTestProxyDelay(it) },
                resolveChildNodeName = resolveChildNodeName,
                outerHorizontalPadding = UiDp.dp0,
                itemVerticalPadding = UiDp.dp6,
                singleNodeTestEnabled = singleNodeTestEnabled,
            )
        }
    }
}

@Composable
private fun ProxyContent(
    proxyGroups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    innerPadding: PaddingValues,
    mainInnerPadding: PaddingValues,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    onGroupDelayTestClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String>,
    onGroupBoundsChanged: ((String, Rect) -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    ScreenLazyColumn(
        scrollBehavior = scrollBehavior,
        lazyListState = listState,
        innerPadding = innerPadding,
        enableGlobalScroll = true,
        contentPadding =
            PaddingValues(
                start = UiDp.dp12,
                end = UiDp.dp12,
                // dp14 (not dp20) because nodeGroupItems adds dp6 above the first card; dp14 + dp6
                // = dp20, keeping the top card flush with the Profiles page cards.
                top = innerPadding.calculateTopPadding() + UiDp.dp14,
                bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
            ),
    ) {
        nodeGroupItems(
            groups = proxyGroups,
            displayMode = displayMode,
            onGroupClick = onGroupClick,
            testingGroupNames = testingGroupNames,
            onGroupDelayTestClick = onGroupDelayTestClick,
            onGroupBoundsChanged = onGroupBoundsChanged,
            itemVerticalPadding = UiDp.dp6,
        )
    }
}
