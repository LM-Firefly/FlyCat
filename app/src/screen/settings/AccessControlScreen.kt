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

package com.github.yumelira.yumebox.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.feature.override.presentation.component.OverrideAnimatedFab
import com.github.yumelira.yumebox.feature.override.presentation.component.rememberOverrideFabController
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.SearchPager
import com.github.yumelira.yumebox.presentation.component.SearchStatus
import com.github.yumelira.yumebox.presentation.component.TopAppBarAnim
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Settings-2`
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.AppTheme.spacing
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccessControlScreen(navigator: Navigator) {
    val density = LocalDensity.current
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = spacing
    val mainLikePadding = rememberStandalonePageMainPadding()
    val viewModel = koinViewModel<AccessControlViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val settingsFabController = rememberOverrideFabController()

    var showSortMenu by remember { mutableStateOf(false) }
    var showOpsMenu by remember { mutableStateOf(false) }
    val mainListState = rememberLazyListState()
    var searchStatus by remember {
        mutableStateOf(
            SearchStatus(
                label = MLang.AccessControl.Search.Placeholder,
                searchText = uiState.searchQuery,
            )
        )
    }
    val dynamicTopPadding by remember {
        derivedStateOf { spacing.space12 * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val listStartPadding = spacing.screenHorizontal
    val listEndPadding = spacing.screenHorizontal
    val currentSearchStatus =
        remember(searchStatus, filteredApps) {
            searchStatus.copy(
                resultStatus =
                    when {
                        searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                        filteredApps.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                        else -> SearchStatus.ResultStatus.SHOW
                    }
            )
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    viewModel.onPermissionResult()
                }
            },
        )

    LaunchedEffect(uiState.needsMiuiPermission) {
        if (uiState.needsMiuiPermission) {
            permissionLauncher.launch("com.android.permission.GET_INSTALLED_APPS")
        }
    }

    LaunchedEffect(searchStatus.searchText) {
        if (searchStatus.searchText != uiState.searchQuery) {
            viewModel.onSearchQueryChange(searchStatus.searchText)
        }
    }

    LaunchedEffect(uiState.searchQuery) {
        if (searchStatus.searchText != uiState.searchQuery) {
            searchStatus = searchStatus.copy(searchText = uiState.searchQuery)
        }
    }

    // Jump the main list back to the top whenever the order changes (sort mode or selected-first),
    // otherwise the previous scroll offset is kept against a freshly reordered list.
    LaunchedEffect(uiState.sortMode, uiState.selectedFirst) {
        mainListState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                currentSearchStatus.TopAppBarAnim {
                    TopBar(
                        title = MLang.AccessControl.Title,
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 0.dp,
                        navigationIcon = { NavigationBackIcon(navigator = navigator) },
                        actions = {
                            Box {
                                IconButton(
                                    modifier = Modifier.padding(end = spacing.space12),
                                    onClick = { showSortMenu = true },
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        contentDescription = MLang.AccessControl.Settings.SortMode,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                AccessControlSortMenu(
                                    show = showSortMenu,
                                    sortMode = uiState.sortMode,
                                    onDismiss = { showSortMenu = false },
                                    onSortModeChange = viewModel::onSortModeChange,
                                )
                            }
                            Box {
                                IconButton(onClick = { showOpsMenu = true }) {
                                    Icon(
                                        imageVector = Yume.`Settings-2`,
                                        contentDescription = MLang.AccessControl.Settings.Title,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                AccessControlOperationsMenu(
                                    show = showOpsMenu,
                                    uiState = uiState,
                                    onDismiss = { showOpsMenu = false },
                                    onShowSystemAppsChange = viewModel::onShowSystemAppsChange,
                                    onSelectedFirstChange = viewModel::onSelectedFirstChange,
                                    onSelectAll = viewModel::selectAll,
                                    onDeselectAll = viewModel::deselectAll,
                                    onInvertSelection = viewModel::invertSelection,
                                    onSelectChinaApps = viewModel::selectChinaAppsInCurrentList,
                                    onSelectNonChinaApps = viewModel::selectNonChinaAppsInCurrentList,
                                    onImportPackages = viewModel::importPackages,
                                    onExportPackages = viewModel::exportPackages,
                                )
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
                                AccessControlCollapsedSearchBar(
                                    label = currentSearchStatus.label,
                                    topPadding = dynamicTopPadding,
                                    startPadding = listStartPadding,
                                    endPadding = listEndPadding,
                                )
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            val combinedInnerPadding = combinePaddingValues(innerPadding, mainLikePadding)
            val listBottomPadding = combinedInnerPadding.calculateBottomPadding()

            if (uiState.isLoading) {
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(
                                top = combinedInnerPadding.calculateTopPadding(),
                                start = listStartPadding,
                                end = listEndPadding,
                                bottom = listBottomPadding,
                            ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    InfiniteProgressIndicator(color = MiuixTheme.colorScheme.onSurface)
                }
            } else if (currentSearchStatus.shouldCollapse()) {
                // Render the list during COLLAPSING too (not just at the COLLAPSED end state) so it is
                // already settled underneath the fading search overlay — otherwise it pops in on the
                // final frame and looks like a refresh flash.
                ScreenLazyColumn(
                    scrollBehavior = scrollBehavior,
                    innerPadding = combinedInnerPadding,
                    lazyListState = mainListState,
                    contentPadding =
                        PaddingValues(
                            top = combinedInnerPadding.calculateTopPadding() + spacing.space6,
                            bottom = listBottomPadding,
                            start = listStartPadding,
                            end = listEndPadding,
                        ),
                ) {
                    items(items = filteredApps, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            selected = app.packageName in uiState.selectedPackages,
                            onSelectionChange = { checked ->
                                viewModel.onAppSelectionChange(app.packageName, checked)
                            },
                            onClick = {
                                viewModel.onAppSelectionChange(
                                    app.packageName,
                                    app.packageName !in uiState.selectedPackages,
                                )
                            },
                        )
                    }
                }
            }

        }

        currentSearchStatus.SearchPager(
            onSearchStatusChange = { searchStatus = it },
            searchBarTopPadding = dynamicTopPadding,
            startPadding = listStartPadding,
            endPadding = listEndPadding,
            emptyResult = {
                SearchEmptyState(
                    text = MLang.AccessControl.Search.Empty,
                    modifier = Modifier.padding(bottom = mainLikePadding.calculateBottomPadding()),
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
                        bottom = maxOf(mainLikePadding.calculateBottomPadding(), imeBottomPadding),
                    ),
            ) {
                items(items = filteredApps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        selected = app.packageName in uiState.selectedPackages,
                        onSelectionChange = { checked ->
                            viewModel.onAppSelectionChange(app.packageName, checked)
                        },
                        onClick = {
                            viewModel.onAppSelectionChange(
                                app.packageName,
                                app.packageName !in uiState.selectedPackages,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessControlCollapsedSearchBar(
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    InputField(
        query = "",
        onQueryChange = {},
        label = label,
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = MLang.Component.Editor.Action.Search,
                modifier =
                    Modifier.size(AppTheme.sizes.searchIconTouchTarget)
                        .padding(start = spacing.space16, end = spacing.space8),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = startPadding, end = endPadding)
                .padding(top = topPadding, bottom = AppTheme.sizes.searchBarBottomPadding),
        onSearch = {},
        enabled = false,
        expanded = false,
        onExpandedChange = {},
    )
}

@Composable
private fun AccessControlSortMenu(
    show: Boolean,
    sortMode: AccessControlViewModel.SortMode,
    onDismiss: () -> Unit,
    onSortModeChange: (AccessControlViewModel.SortMode) -> Unit,
) {
    val entries =
        listOf(
            DropdownEntry(
                items =
                    AccessControlViewModel.SortMode.entries.map { mode ->
                        DropdownItem(
                            text = mode.displayName,
                            selected = mode == sortMode,
                            onClick = { onSortModeChange(mode) },
                        )
                    }
            )
        )

    // Picking a sort mode dismisses the popup (collapseOnSelection defaults to true).
    OverlayCascadingListPopup(
        show = show,
        entries = entries,
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun AccessControlOperationsMenu(
    show: Boolean,
    uiState: AccessControlViewModel.UiState,
    onDismiss: () -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onSelectedFirstChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectChinaApps: () -> Int,
    onSelectNonChinaApps: () -> Int,
    onImportPackages: (String) -> Int,
    onExportPackages: () -> String,
) {
    val context = LocalContext.current
    val clipboardManager =
        remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
    val settings = MLang.AccessControl.Settings

    val entries =
        listOf(
            DropdownEntry(
                items =
                    listOf(
                        DropdownItem(
                            text = settings.ShowSystemApps,
                            selected = uiState.showSystemApps,
                            onClick = { onShowSystemAppsChange(!uiState.showSystemApps) },
                        ),
                        DropdownItem(
                            text = settings.SelectedFirst,
                            selected = uiState.selectedFirst,
                            onClick = { onSelectedFirstChange(!uiState.selectedFirst) },
                        ),
                    )
            ),
            DropdownEntry(
                items =
                    listOf(
                        DropdownItem(
                            text = settings.BatchOperation,
                            children =
                                listOf(
                                    DropdownItem(text = settings.SelectAll, onClick = onSelectAll),
                                    DropdownItem(
                                        text = settings.DeselectAll,
                                        onClick = onDeselectAll,
                                    ),
                                    DropdownItem(
                                        text = settings.Invert,
                                        onClick = onInvertSelection,
                                    ),
                                ),
                        ),
                        DropdownItem(
                            text = settings.RegionQuickSelect,
                            children =
                                listOf(
                                    DropdownItem(
                                        text = settings.ChinaApps,
                                        onClick = { onSelectChinaApps() },
                                    ),
                                    DropdownItem(
                                        text = settings.OverseasApps,
                                        onClick = { onSelectNonChinaApps() },
                                    ),
                                ),
                        ),
                        DropdownItem(
                            text = settings.ImportExport,
                            children =
                                listOf(
                                    DropdownItem(
                                        text = settings.Import,
                                        onClick = {
                                            val text =
                                                clipboardManager.primaryClip
                                                    ?.takeIf { it.itemCount > 0 }
                                                    ?.getItemAt(0)
                                                    ?.text
                                                    ?.toString()
                                                    .orEmpty()
                                            if (text.isNotEmpty()) {
                                                onImportPackages(text)
                                            }
                                        },
                                    ),
                                    DropdownItem(
                                        text = settings.Export,
                                        onClick = {
                                            clipboardManager.setPrimaryClip(
                                                ClipData.newPlainText(
                                                    "packages",
                                                    onExportPackages(),
                                                )
                                            )
                                        },
                                    ),
                                ),
                        ),
                    )
            ),
        )

    // Selecting any item (including inside a 2nd-level submenu) collapses and dismisses the popup —
    // collapseOnSelection defaults to true. The submenu can also be backed out via the system back
    // gesture or by tapping outside the popup (both handled by the Miuix cascading layout).
    OverlayCascadingListPopup(
        show = show,
        entries = entries,
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun AppCard(
    app: AccessControlViewModel.AppInfo,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val spacing = spacing
    val componentSizes = AppTheme.sizes

    Card(modifier = Modifier.padding(vertical = spacing.space4), applyHorizontalPadding = false) {
        BasicComponent(
            // Reduce the default 16dp vertical inside-margin a little for a tighter row, keep
            // horizontal at 16dp so the card width is unchanged.
            insideMargin = PaddingValues(horizontal = spacing.space16, vertical = spacing.space12),
            startAction = {
                AppIcon(
                    packageName = app.packageName,
                    contentDescription = app.label,
                    imageSize = componentSizes.iconBadgeMedium,
                    bitmapSize = 80,
                    modifier = Modifier.padding(end = spacing.space12),
                )
            },
            endActions = {
                Checkbox(
                    state = ToggleableState(selected),
                    onClick = { onSelectionChange(!selected) },
                )
            },
            onClick = onClick,
        ) {
            Text(
                text = app.label,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    contentDescription: String,
    imageSize: androidx.compose.ui.unit.Dp,
    bitmapSize: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconBitmap by
        produceState<ImageBitmap?>(initialValue = null, key1 = packageName, key2 = bitmapSize) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.packageManager
                                .getApplicationIcon(packageName)
                                .toBitmap(width = bitmapSize, height = bitmapSize)
                                .asImageBitmap()
                        }
                        .getOrNull()
                }
        }

    val bitmap = iconBitmap ?: return
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier.size(imageSize),
    )
}

@Composable
private fun SearchEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
