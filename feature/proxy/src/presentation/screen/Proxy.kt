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

package com.github.yumelira.yumebox.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.data.model.ProxySortMode
import com.github.yumelira.yumebox.domain.model.ProxyGroupInfo
import com.github.yumelira.yumebox.domain.model.isSelectable
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Eye
import com.github.yumelira.yumebox.presentation.icon.yume.Folders
import com.github.yumelira.yumebox.presentation.icon.yume.`List-chevrons-up-down`
import com.github.yumelira.yumebox.presentation.icon.yume.Speed
import com.github.yumelira.yumebox.presentation.screen.node.NodeSortPopup
import com.github.yumelira.yumebox.presentation.screen.node.NodeCard
import com.github.yumelira.yumebox.presentation.screen.node.nodeGridItems
import com.github.yumelira.yumebox.presentation.screen.node.nodeGroupItems
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.verticalBounceContentTransform
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.LocalSpacing
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.util.KeepLazyListTopAnchorOnReorder
import com.github.yumelira.yumebox.presentation.viewmodel.ProxyViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private fun LazyListState.isScrolledFromTop(): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

private fun LazyGridState.isScrolledFromTop(): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/**
 * Shared locate motion:
 * 1) If far, snap onto the target then pull back ~¾ viewport so the glide is always short.
 * 2) Glide past the resting line (content-padding aware) with a decelerate tween.
 * 3) Spring back over the overshoot for a light rubber-band bounce.
 * 4) [scrollToItem] settles any residual pixel drift so cards never land half-offset.
 *
 * Resting offset is [beforeContentPadding], not 0 - aiming at 0 was the source of the
 * post-locate misalignment under large top bar + content padding.
 */
private suspend fun animateLocateScroll(
    targetIndex: Int,
    isEmpty: Boolean,
    viewportSize: Int,
    beforeContentPadding: Int,
    firstVisibleIndex: Int,
    targetMainAxisOffset: () -> Int?,
    scrollTo: suspend (index: Int) -> Unit,
    scrollByDelta: suspend (delta: Float) -> Float,
    animateBy: suspend (delta: Float, anim: AnimationSpec<Float>) -> Float,
) {
    if (isEmpty) {
        scrollTo(targetIndex)
        return
    }

    val viewport = viewportSize.coerceAtLeast(1).toFloat()
    val pad = beforeContentPadding.toFloat()
    val goingDown = targetIndex > firstVisibleIndex
    val alreadyOnScreen = targetMainAxisOffset() != null

    if (!alreadyOnScreen) {
        // Land on the target first (padding-correct), then pull back so the glide has room.
        scrollTo(targetIndex)
        val pull = viewport * 0.72f * if (goingDown) -1f else 1f
        scrollByDelta(pull)
    }

    val current = targetMainAxisOffset()?.toFloat()
    if (current == null) {
        scrollTo(targetIndex)
        return
    }

    // Delta that places the item on its natural resting line under content padding.
    val remaining = current - pad
    if (abs(remaining) < 0.5f) {
        // Already parked - tiny bounce so the eye action still feels alive.
        val tick = 28f
        animateBy(tick, tween(durationMillis = 110, easing = AnimationSpecs.EmphasizedAccelerate))
        animateBy(
            -tick,
            spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium),
        )
        scrollTo(targetIndex)
        return
    }

    val direction = if (remaining > 0f) 1f else -1f
    val overshoot = (abs(remaining) * 0.14f).coerceIn(28f, 88f) * direction

    animateBy(
        remaining + overshoot,
        tween(durationMillis = 520, easing = AnimationSpecs.EmphasizedDecelerate),
    )
    animateBy(
        -overshoot,
        spring(dampingRatio = 0.52f, stiffness = 360f),
    )
    // Exact settle - removes multi-column estimate drift and padding misalignment.
    scrollTo(targetIndex)
}

// animateScrollToItem races across arbitrary distances at full speed, so locating a far-away
// node reads as a blink. Cap the animated stretch at roughly one viewport: snap silently near
// it, then glide + bounce the remainder.
private suspend fun LazyListState.animateLocateToItem(targetIndex: Int) {
    animateLocateScroll(
        targetIndex = targetIndex,
        isEmpty = layoutInfo.visibleItemsInfo.isEmpty(),
        viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
        beforeContentPadding = layoutInfo.beforeContentPadding,
        firstVisibleIndex = firstVisibleItemIndex,
        targetMainAxisOffset = {
            layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset
        },
        scrollTo = { index -> scrollToItem(index) },
        scrollByDelta = { delta -> scrollBy(delta) },
        animateBy = { delta, anim -> animateScrollBy(delta, anim) },
    )
}

private suspend fun LazyGridState.animateLocateToItem(targetIndex: Int) {
    animateLocateScroll(
        targetIndex = targetIndex,
        isEmpty = layoutInfo.visibleItemsInfo.isEmpty(),
        viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
        beforeContentPadding = layoutInfo.beforeContentPadding,
        firstVisibleIndex = firstVisibleItemIndex,
        targetMainAxisOffset = {
            layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset?.y
        },
        scrollTo = { index -> scrollToItem(index) },
        scrollByDelta = { delta -> scrollBy(delta) },
        animateBy = { delta, anim -> animateScrollBy(delta, anim) },
    )
}


private data class ProxyScreenVmState(
    val proxyGroups: List<ProxyGroupInfo>,
    val testingGroupNames: Set<String>,
    val testingProxyNames: Set<String>,
    val sortMode: ProxySortMode,
    val singleNodeTest: Boolean,
    val uiSelectedGroupName: String?,
)

@Composable
private fun rememberProxyScreenVmState(proxyViewModel: ProxyViewModel): ProxyScreenVmState {
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsState()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsState()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsState()
    val sortMode by proxyViewModel.sortMode.collectAsState()
    val singleNodeTest by proxyViewModel.singleNodeTest.collectAsState()
    val uiSelectedGroupName by proxyViewModel.uiSelectedGroupName.collectAsState()
    return remember(
        proxyGroups,
        testingGroupNames,
        testingProxyNames,
        sortMode,
        singleNodeTest,
        uiSelectedGroupName,
    ) {
        ProxyScreenVmState(
            proxyGroups = proxyGroups,
            testingGroupNames = testingGroupNames,
            testingProxyNames = testingProxyNames,
            sortMode = sortMode,
            singleNodeTest = singleNodeTest,
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
    val testingProxyNames = screen.testingProxyNames
    val sortMode = screen.sortMode
    val singleNodeTest = screen.singleNodeTest
    val uiSelectedGroupName = screen.uiSelectedGroupName
    val groupScrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)
    val pagerState = LocalPagerState.current
    val topBarHazeState = LocalTopBarHazeState.current

    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    // Dual-pane shell: left list + right nodes share selection via ViewModel.
    val inSplitShell = LocalDetailNavigator.current.isSplitShell
    val groupSelection =
        rememberProxyGroupSelectionState(
            proxyGroups = proxyGroups,
            onRefreshGroup = proxyViewModel::refreshGroup,
            retainLastKnownGroup = true,
            controlledSelectedGroupName = if (inSplitShell) uiSelectedGroupName else null,
            onControlledSelectedGroupNameChange =
                if (inSplitShell) proxyViewModel::selectUiGroup else null,
        )
    val selectedGroupName = groupSelection.selectedGroupName
    val displayGroup = groupSelection.displayGroup

    // Keep a group selected so the shell detail pane always has content.
    LaunchedEffect(inSplitShell, proxyGroups, selectedGroupName) {
        if (!inSplitShell || proxyGroups.isEmpty()) return@LaunchedEffect
        if (selectedGroupName == null || proxyGroups.none { it.name == selectedGroupName }) {
            groupSelection.selectGroup(proxyGroups.first())
        }
    }
    val fabGroup = displayGroup
    val isFabTesting = fabGroup?.name?.let(testingGroupNames::contains) == true
    val coroutineScope = rememberCoroutineScope()
    val nodeListState =
        rememberSaveable(selectedGroupName, saver = LazyListState.Saver) { LazyListState() }

    var fabHidden by rememberSaveable { mutableStateOf(false) }

    val requestSelectedGroupDelayTest =
        remember(coroutineScope, nodeListState, selectedGroupName, proxyViewModel) {
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
    val locateCurrentProxy =
        remember(coroutineScope, displayGroup, nodeListState, selectedGroupName) {
            if (selectedGroupName == null) {
                null
            } else {
                displayGroup?.takeIf { group -> group.name == selectedGroupName }?.let { group ->
                    fun() {
                        val proxyIndex =
                            group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                        if (proxyIndex < 0) return
                        coroutineScope.launch {
                            nodeListState.animateLocateToItem(proxyIndex + 1)
                        }
                    }
                }
            }
        }

    BackHandler(enabled = !inSplitShell && selectedGroupName != null) { groupSelection.clearSelection() }

    LaunchedEffect(isActive) { proxyViewModel.ensureCoreLoaded(isActive, source = "proxy_page") }

    DisposableEffect(proxyViewModel) {
        onDispose { proxyViewModel.ensureCoreLoaded(false, source = "proxy_page") }
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible =
                    !inSplitShell &&
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
                        contentDescription = YumeTxt.Proxy.Action.Test,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
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
            modifier =
                Modifier.fillMaxSize().let { mod ->
                    if (topBarHazeState != null) mod.hazeSource(state = topBarHazeState) else mod
                }
        ) {
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
                    )
                }
            } else {
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
                            )
                        }
                    } else {
                        val currentGroup = groupSelection.selectedGroup ?: displayGroup
                        NodeListPage(
                            group = currentGroup,
                            sortMode = sortMode,
                            testingGroupNames = testingGroupNames,
                            testingProxyNames = testingProxyNames,
                            mainInnerPadding = mainInnerPadding,
                            outerInnerPadding = scaffoldPadding,
                            scrollBehavior = groupScrollBehavior,
                            listState = nodeListState,
                            onSelectProxy = { groupName, proxyName ->
                                proxyViewModel.selectProxy(groupName, proxyName)
                            },
                            onTestDelay = requestSelectedGroupDelayTest,
                            onTestProxyDelay = { proxyName ->
                                currentGroup?.name?.let { groupName ->
                                    proxyViewModel.testProxyDelay(groupName, proxyName)
                                }
                            },
                            onScrollDirectionChanged = { hidden -> fabHidden = hidden },
                            singleNodeTestEnabled = singleNodeTest,
                            useAdaptiveGrid = false,
                        )
                    }
                }
            }
        }
    }
}


/**
 * Right-pane node list for the tablet dual-pane shell.
 * Selection is shared with [ProxyPager] via [ProxyViewModel.uiSelectedGroupName].
 */
@Composable
fun ProxyShellNodeDetail(
    mainInnerPadding: PaddingValues,
    onNavigateToProviders: (() -> Unit)? = null,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    val screen = rememberProxyScreenVmState(proxyViewModel)
    val proxyGroups = screen.proxyGroups
    val testingGroupNames = screen.testingGroupNames
    val testingProxyNames = screen.testingProxyNames
    val sortMode = screen.sortMode
    val singleNodeTest = screen.singleNodeTest
    val uiSelectedGroupName = screen.uiSelectedGroupName
    val scrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)
    val coroutineScope = rememberCoroutineScope()
    val groupSelection =
        rememberProxyGroupSelectionState(
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
    var fabHidden by rememberSaveable { mutableStateOf(false) }

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
            val fromIndex =
                initialState?.let { name -> proxyGroups.indexOfFirst { it.name == name } } ?: -1
            val toIndex =
                targetState?.let { name -> proxyGroups.indexOfFirst { it.name == name } } ?: -1
            verticalBounceContentTransform(forward = toIndex >= fromIndex)
        },
        label = "proxy_shell_group_switch",
    ) { groupName ->
        val pageGroup =
            groupName?.let { name -> proxyGroups.firstOrNull { it.name == name } } ?: currentGroup
        // Adaptive grid owns the real scroll surface on the shell right pane.
        val nodeGridState =
            rememberSaveable(groupName, saver = LazyGridState.Saver) { LazyGridState() }
        val requestSelectedGroupDelayTest =
            remember(coroutineScope, nodeGridState, groupName, proxyViewModel) {
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
        val locateCurrentProxy =
            remember(coroutineScope, pageGroup, nodeGridState, groupName) {
                if (groupName == null || pageGroup == null) {
                    null
                } else {
                    pageGroup.takeIf { group -> group.name == groupName }?.let { group ->
                        fun() {
                            val proxyIndex =
                                group.proxies.indexOfFirst { proxy -> proxy.name == group.now }
                            if (proxyIndex < 0) return
                            // Grid keeps a reserved refresh row at index 0 (collapsed when idle).
                            coroutineScope.launch {
                                nodeGridState.animateLocateToItem(proxyIndex + 1)
                            }
                        }
                    }
                }
            }
        val isFabTesting = pageGroup?.name?.let(testingGroupNames::contains) == true

        Scaffold(
            floatingActionButton = {
                AnimatedVisibility(
                    visible =
                        groupName != null &&
                            pageGroup != null &&
                            !fabHidden &&
                            !isFabTesting,
                    enter = scaleIn(),
                    exit = scaleOut(),
                    label = "proxy_shell_test_fab_visibility",
                ) {
                    FloatingActionButton(
                        modifier = Modifier.padding(end = UiDp.dp20, bottom = UiDp.dp24),
                        onClick = {
                            if (pageGroup == null) return@FloatingActionButton
                            requestSelectedGroupDelayTest()
                        },
                    ) {
                        Icon(
                            imageVector = Yume.Speed,
                            contentDescription = YumeTxt.Proxy.Action.Test,
                            tint = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            },
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
                    testingProxyNames = testingProxyNames,
                    mainInnerPadding = mainInnerPadding,
                    outerInnerPadding = scaffoldPadding,
                    scrollBehavior = scrollBehavior,
                    listState = remember { LazyListState() },
                    gridState = nodeGridState,
                    onSelectProxy = { selectedGroup, proxyName ->
                        proxyViewModel.selectProxy(selectedGroup, proxyName)
                    },
                    onTestDelay = requestSelectedGroupDelayTest,
                    onTestProxyDelay = { proxyName ->
                        pageGroup.name.let { selectedGroup ->
                            proxyViewModel.testProxyDelay(selectedGroup, proxyName)
                        }
                    },
                    onScrollDirectionChanged = { hidden -> fabHidden = hidden },
                    singleNodeTestEnabled = singleNodeTest,
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
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = YumeTxt.Component.Navigation.Back)
                    }
                } else {
                    if (onNavigateToProviders != null) {
                        IconButton(onClick = onNavigateToProviders) {
                            Icon(Yume.Folders, contentDescription = YumeTxt.Providers.Title)
                        }
                    }
                }
            }
        },
        actions = {
            if (onLocateCurrentProxy != null) {
                IconButton(
                    modifier = Modifier.padding(end = spacing.space12),
                    onClick = onLocateCurrentProxy,
                ) {
                    Icon(Yume.Eye, contentDescription = "Eye current proxy")
                }
            }
            Box {
                IconButton(onClick = { onShowSortPopupChange(true) }) {
                    Icon(Yume.`List-chevrons-up-down`, contentDescription = YumeTxt.Proxy.Action.Sort)
                }
                NodeSortPopup(
                    show = showSortPopup,
                    onDismiss = { onShowSortPopupChange(false) },
                    sortMode = sortMode,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onSortSelected = onSortSelected,
                )
            }
        },
    )
}

@Composable
private fun NodeListPage(
    group: ProxyGroupInfo?,
    sortMode: ProxySortMode,
    testingGroupNames: Set<String>,
    testingProxyNames: Set<String>,
    mainInnerPadding: PaddingValues,
    outerInnerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    onSelectProxy: (groupName: String, proxyName: String) -> Unit,
    onTestDelay: () -> Unit,
    onTestProxyDelay: (proxyName: String) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit,
    singleNodeTestEnabled: Boolean = true,
    useAdaptiveGrid: Boolean = false,
    gridState: LazyGridState? = null,
) {
    if (group == null) {
        CenteredText(
            firstLine = YumeTxt.Proxy.Empty.NoNodes,
            secondLine = YumeTxt.Proxy.Empty.Hint,
            showEmptyResourceIllustration = true,
        )
        return
    }
    val spacing = LocalSpacing.current
    val isTesting = testingGroupNames.contains(group.name)
    val listItemKeys = remember(group.proxies) { group.proxies.map { it.name } }

    KeepLazyListTopAnchorOnReorder(
        listState = listState,
        itemKeys = listItemKeys,
        enabled = sortMode == ProxySortMode.BY_LATENCY && !useAdaptiveGrid,
        scrollToTopOnEnabled = true,
    )

    LaunchedEffect(isTesting, useAdaptiveGrid, gridState) {
        if (!isTesting) return@LaunchedEffect
        if (useAdaptiveGrid) {
            val state = gridState ?: return@LaunchedEffect
            if (state.isScrolledFromTop()) {
                state.animateScrollToItem(0)
            }
        } else if (listState.isScrolledFromTop()) {
            listState.animateScrollToItem(0)
        }
    }

    val contentPadding =
        PaddingValues(
            start = UiDp.dp12,
            end = UiDp.dp12,
            top = outerInnerPadding.calculateTopPadding() + UiDp.dp20,
            bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
        )

    if (useAdaptiveGrid) {
        val resolvedGridState = gridState ?: rememberLazyGridState()
        val latestScrollDirectionCallback by rememberUpdatedState(onScrollDirectionChanged)
        var lastHiddenState by remember(resolvedGridState) { mutableStateOf(false) }
        val fabScrollObserver =
            remember(resolvedGridState) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        val hiddenState =
                            when {
                                available.y < -1f -> true
                                available.y > 1f -> false
                                else -> return Offset.Zero
                            }
                        if (hiddenState != lastHiddenState) {
                            lastHiddenState = hiddenState
                            latestScrollDirectionCallback(hiddenState)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity,
                    ): Velocity {
                        if (consumed.y > 1f || available.y > 1f) {
                            latestScrollDirectionCallback(false)
                            lastHiddenState = false
                        }
                        return Velocity.Zero
                    }
                }
            }
        LaunchedEffect(resolvedGridState) {
            latestScrollDirectionCallback(false)
            lastHiddenState = false
        }
        Box(Modifier.fillMaxSize().nestedScroll(fabScrollObserver)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = PaneWidths.NodeGridAdaptiveMin),
                state = resolvedGridState,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
                verticalArrangement = Arrangement.spacedBy(UiDp.dp6),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Always reserve index 0 so locate can use proxyIndex + 1, matching the list path.
                item(key = "__refresh_indicator__", span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedVisibility(
                        visible = isTesting,
                        enter =
                            expandVertically(
                                animationSpec =
                                    tween(
                                        durationMillis =
                                            AnimationSpecs.Proxy.RefreshIndicatorDuration
                                    ),
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
                                    tween(
                                        durationMillis =
                                            AnimationSpecs.Proxy.RefreshIndicatorDuration
                                    ),
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
                                text = YumeTxt.Proxy.Testing.InProgress,
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                items(items = group.proxies, key = { it.name }) { proxy ->
                    NodeCard(
                        proxy = proxy,
                        isSelected = proxy.name == group.now,
                        onClick = { proxyName ->
                            if (group.isSelectable) {
                                onSelectProxy(group.name, proxyName)
                            } else {
                                onTestDelay()
                            }
                        },
                        isDelayTesting = isTesting,
                        isThisProxyTesting = proxy.name in testingProxyNames,
                        onSingleNodeTestClick = { onTestProxyDelay(it) },
                        showCountryFlag = true,
                        singleNodeTestEnabled = singleNodeTestEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    ScreenLazyColumn(
        lazyListState = listState,
        scrollBehavior = scrollBehavior,
        innerPadding = outerInnerPadding,
        enableGlobalScroll = true,
        onScrollDirectionChanged = onScrollDirectionChanged,
        contentPadding = contentPadding,
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
                        text = YumeTxt.Proxy.Testing.InProgress,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        nodeGridItems(
            proxies = group.proxies,
            selectedProxyName = group.now,
            onProxyClick = { proxyName ->
                if (group.isSelectable) {
                    onSelectProxy(group.name, proxyName)
                } else {
                    onTestDelay()
                }
            },
            isDelayTesting = isTesting,
            testingProxyNames = testingProxyNames,
            onSingleNodeTestClick = { onTestProxyDelay(it) },
            outerHorizontalPadding = UiDp.dp0,
            itemVerticalPadding = UiDp.dp6,
            singleNodeTestEnabled = singleNodeTestEnabled,
        )
    }
}

@Composable
private fun ProxyContent(
    proxyGroups: List<ProxyGroupInfo>,
    scrollBehavior: ScrollBehavior,
    innerPadding: PaddingValues,
    mainInnerPadding: PaddingValues,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String>,
) {
    val spacing = LocalSpacing.current
    ScreenLazyColumn(
        scrollBehavior = scrollBehavior,
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
            onGroupClick = onGroupClick,
            testingGroupNames = testingGroupNames,
            itemVerticalPadding = UiDp.dp6,
        )
    }
}
