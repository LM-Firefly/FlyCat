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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import com.github.yumelira.yumebox.presentation.screen.node.nodeGridItems
import com.github.yumelira.yumebox.presentation.screen.node.nodeGroupItems
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
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
    isActive: Boolean,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()

    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsState()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsState()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsState()
    val sortMode by proxyViewModel.sortMode.collectAsState()
    val singleNodeTest by proxyViewModel.singleNodeTest.collectAsState()
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
                            firstLine = YumeTxt.Proxy.Empty.NoNodes,
                            secondLine = YumeTxt.Proxy.Empty.Hint,
                            showEmptyResourceIllustration = true,
                        )
                    } else {
                        ProxyContent(
                            proxyGroups = proxyGroups,
                            scrollBehavior = groupScrollBehavior,
                            innerPadding = it,
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
                        outerInnerPadding = it,
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
        enabled = sortMode == ProxySortMode.BY_LATENCY,
        scrollToTopOnEnabled = true,
    )

    LaunchedEffect(isTesting) {
        if (isTesting && listState.isScrolledFromTop()) {
            listState.animateScrollToItem(0)
        }
    }

    ScreenLazyColumn(
        lazyListState = listState,
        scrollBehavior = scrollBehavior,
        innerPadding = outerInnerPadding,
        enableGlobalScroll = true,
        onScrollDirectionChanged = onScrollDirectionChanged,
        contentPadding =
            PaddingValues(
                start = UiDp.dp12,
                end = UiDp.dp12,
                top = outerInnerPadding.calculateTopPadding() + UiDp.dp20,
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
