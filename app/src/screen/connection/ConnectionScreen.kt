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

package com.github.yumelira.yumebox.screen.connection

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import com.github.yumelira.yumebox.core.model.ConnectionInfo
import com.github.yumelira.yumebox.feature.meta.presentation.component.ConnectionCard
import com.github.yumelira.yumebox.feature.meta.presentation.component.ConnectionDetailSheet
import com.github.yumelira.yumebox.feature.meta.presentation.component.TabRowWithContour
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.ConnectionSort
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.ConnectionTab
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.ConnectionViewModel
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.CollapsedSearchBar
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.SearchBarPadding
import com.github.yumelira.yumebox.presentation.component.SearchPager
import com.github.yumelira.yumebox.presentation.component.SearchStatus
import com.github.yumelira.yumebox.presentation.component.TopAppBarAnim
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SortModes =
    listOf(ConnectionSort.Time, ConnectionSort.Upload, ConnectionSort.Download, ConnectionSort.Host)

private fun ConnectionSort.getDisplayName(): String =
    when (this) {
        ConnectionSort.Time -> YumeTxt.Connection.Sort.Time
        ConnectionSort.Upload -> YumeTxt.Connection.Sort.Upload
        ConnectionSort.Download -> YumeTxt.Connection.Sort.Download
        ConnectionSort.Host -> YumeTxt.Connection.Sort.Host
    }

@Composable
fun ConnectionScreen(navigator: Navigator) {
    val density = LocalDensity.current
    val viewModel = koinViewModel<ConnectionViewModel>()
    val state by viewModel.state.collectAsState()
    val filteredConnections by viewModel.filteredConnections.collectAsState()
    val spacing = AppTheme.spacing
    val mainLikePadding = rememberStandalonePageMainPadding()

    val scrollBehavior = MiuixScrollBehavior()
    var showSortPopup by remember { mutableStateOf(false) }

    var selectedConnection by remember { mutableStateOf<ConnectionInfo?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }

    val tabs = listOf(YumeTxt.Connection.Tab.Active, YumeTxt.Connection.Tab.Closed)
    var selectedTabIndex by
        rememberSaveable(state.selectedTab) {
            mutableIntStateOf(
                when (state.selectedTab) {
                    ConnectionTab.ACTIVE -> 0
                    ConnectionTab.CLOSED -> 1
                }
            )
        }
    val selectedSortIndex =
        remember(state.sortBy) { SortModes.indexOf(state.sortBy).coerceAtLeast(0) }
    val emptyStateText =
        when {
            state.isLoading -> YumeTxt.Connection.Loading
            state.searchQuery.isNotEmpty() -> YumeTxt.Connection.NoResults
            else -> YumeTxt.Connection.Empty
        }

    val mainListState = rememberLazyListState()
    var searchStatus by remember {
        mutableStateOf(
            SearchStatus(
                label = YumeTxt.Connection.SearchHint,
                searchText = state.searchQuery,
            )
        )
    }
    val dynamicTopPadding by remember {
        derivedStateOf { spacing.space12 * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val listStartPadding = spacing.screenHorizontal
    val listEndPadding = spacing.screenHorizontal
    val currentSearchStatus =
        remember(searchStatus, filteredConnections, state.searchQuery) {
            searchStatus.copy(
                resultStatus =
                    when {
                        searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                        filteredConnections.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                        else -> SearchStatus.ResultStatus.SHOW
                    }
            )
        }

    LaunchedEffect(selectedTabIndex) {
        val tab = if (selectedTabIndex == 0) ConnectionTab.ACTIVE else ConnectionTab.CLOSED
        viewModel.setTab(tab)
    }

    LaunchedEffect(searchStatus.searchText) {
        if (searchStatus.searchText != state.searchQuery) {
            viewModel.setSearchQuery(searchStatus.searchText)
        }
    }

    LaunchedEffect(state.searchQuery) {
        if (searchStatus.searchText != state.searchQuery) {
            searchStatus = searchStatus.copy(searchText = state.searchQuery)
        }
    }

    LaunchedEffect(showDetailSheet, selectedConnection) {
        if (showDetailSheet && selectedConnection == null) {
            showDetailSheet = false
        }
    }
    LaunchedEffect(state.snapshot, selectedConnection?.id) {
        val selectedId = selectedConnection?.id ?: return@LaunchedEffect
        state.snapshot?.connections?.firstOrNull { it.id == selectedId }?.let {
            selectedConnection = it
        }
    }
    LaunchedEffect(Unit) { viewModel.startPolling() }

    // Same shell as AccessControl: outer Box, Scaffold, SearchPager always composed.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                currentSearchStatus.TopAppBarAnim {
                    TopBar(
                        title = YumeTxt.Connection.Title,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            Box {
                                IconButton(
                                    modifier = Modifier.padding(end = spacing.space12),
                                    onClick = { showSortPopup = true },
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        contentDescription =
                                            YumeTxt.Connection.SortBy.trimEnd(':', '：'),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                OverlayListPopup(
                                    show = showSortPopup,
                                    alignment = PopupPositionProvider.Align.BottomEnd,
                                    onDismissRequest = { showSortPopup = false },
                                ) {
                                    ListPopupColumn {
                                        SortModes.forEachIndexed { index, mode ->
                                            DropdownImpl(
                                                text = mode.getDisplayName(),
                                                optionSize = SortModes.size,
                                                isSelected = selectedSortIndex == index,
                                                onSelectedIndexChange = {
                                                    if (mode != state.sortBy) {
                                                        viewModel.setSortBy(mode)
                                                    }
                                                    showSortPopup = false
                                                },
                                                index = index,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        bottomContent = {
                            Box(
                                modifier =
                                    Modifier.alpha(
                                            if (currentSearchStatus.isCollapsed()) 1f else 0f
                                        )
                                        .onGloballyPositioned { coordinates ->
                                            with(density) {
                                                val collapsedBarOffset =
                                                    coordinates.positionInWindow().y.toDp()
                                                if (
                                                    currentSearchStatus.offsetY !=
                                                        collapsedBarOffset
                                                ) {
                                                    searchStatus =
                                                        currentSearchStatus.copy(
                                                            offsetY = collapsedBarOffset
                                                        )
                                                }
                                            }
                                        }
                                        .then(
                                            if (currentSearchStatus.isCollapsed()) {
                                                Modifier.pointerInput(currentSearchStatus.current) {
                                                    detectTapGestures {
                                                        searchStatus =
                                                            currentSearchStatus.copy(
                                                                current =
                                                                    SearchStatus.Status.EXPANDING
                                                            )
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                            ) {
                                CollapsedSearchBar(
                                    label = currentSearchStatus.label,
                                    topPadding = dynamicTopPadding,
                                    startPadding = listStartPadding,
                                    endPadding = listEndPadding,
                                )
                            }
                        },
                    )
                }
            }
        ) { innerPadding ->
            if (currentSearchStatus.shouldCollapse()) {
                val combinedInnerPadding = combinePaddingValues(innerPadding, mainLikePadding)
                ScreenLazyColumn(
                    scrollBehavior = scrollBehavior,
                    innerPadding = combinedInnerPadding,
                    lazyListState = mainListState,
                    contentPadding =
                        PaddingValues(
                            start = listStartPadding,
                            end = listEndPadding,
                            top = combinedInnerPadding.calculateTopPadding(),
                            bottom = combinedInnerPadding.calculateBottomPadding() + spacing.space12,
                        ),
                ) {
                    item {
                        TabRowWithContour(
                            tabs = tabs,
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                        )
                    }

                    if (filteredConnections.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(spacing.space32),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emptyStateText,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredConnections,
                            key = { it.id },
                            contentType = { "connection" },
                        ) { connection ->
                            ConnectionCard(
                                connectionInfo = connection,
                                onClick = {
                                    selectedConnection = connection
                                    showDetailSheet = true
                                },
                                modifier = Modifier.padding(vertical = spacing.space6),
                            )
                        }
                    }
                }
            }
        }

        // Always composed (AccessControl pattern). Gating with !isCollapsed remounts the pager
        // and breaks expand/collapse continuity.
        currentSearchStatus.SearchPager(
            onSearchStatusChange = { searchStatus = it },
            padding =
                SearchBarPadding(
                    top = dynamicTopPadding,
                    start = listStartPadding,
                    end = listEndPadding,
                ),
            emptyResult = {
                ConnectionSearchEmptyState(
                    text = YumeTxt.Connection.NoResults,
                    modifier =
                        Modifier.padding(bottom = mainLikePadding.calculateBottomPadding()),
                )
            },
        ) {
            val searchListState = rememberLazyListState()
            val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

            LaunchedEffect(currentSearchStatus.searchText) {
                if (currentSearchStatus.searchText.isNotBlank()) {
                    searchListState.scrollToItem(0)
                }
            }

            LazyColumn(
                state = searchListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = listStartPadding,
                        end = listEndPadding,
                        top = spacing.space6,
                        bottom =
                            maxOf(
                                mainLikePadding.calculateBottomPadding(),
                                imeBottomPadding,
                            ),
                    ),
            ) {
                items(
                    items = filteredConnections,
                    key = { it.id },
                    contentType = { "connection" },
                ) { connection ->
                    ConnectionCard(
                        connectionInfo = connection,
                        onClick = {
                            selectedConnection = connection
                            showDetailSheet = true
                        },
                        modifier = Modifier.padding(vertical = spacing.space6),
                    )
                }
            }
        }

        ConnectionDetailSheet(
            show = showDetailSheet,
            connectionInfo = selectedConnection,
            canInterrupt = state.selectedTab == ConnectionTab.ACTIVE,
            onInterruptConnection = { id -> viewModel.closeConnection(id) },
            onDismiss = { showDetailSheet = false },
            onDismissFinished = { selectedConnection = null },
        )
    }
}

@Composable
private fun ConnectionSearchEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(AppTheme.spacing.space32),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
