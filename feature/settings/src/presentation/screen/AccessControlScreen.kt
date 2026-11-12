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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.AccessControlImportStrategy
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AccessControlViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassMenuDivider
import com.github.lmfirefly.flycat.presentation.component.GlassMenuItem
import com.github.lmfirefly.flycat.presentation.component.GlassPopupHost
import com.github.lmfirefly.flycat.presentation.component.GlassPopupSubmenuItem
import com.github.lmfirefly.flycat.presentation.component.GlassScaffold
import com.github.lmfirefly.flycat.presentation.component.GlassSubmenuPageHeader
import com.github.lmfirefly.flycat.presentation.component.GlassTopAction
import com.github.lmfirefly.flycat.presentation.component.GlassTopActionAnchorIcon
import com.github.lmfirefly.flycat.presentation.component.GlassTopActionPill
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.component.navigation.rememberScrollAwareFabController
import com.github.lmfirefly.flycat.presentation.component.rememberAppIconBitmap
import com.github.lmfirefly.flycat.presentation.component.rememberGlassSubmenuState
import com.github.lmfirefly.flycat.presentation.component.state.SearchStatus
import com.github.lmfirefly.flycat.presentation.component.state.TopAppBarAnim
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Bolt
import com.github.lmfirefly.flycat.presentation.icon.flycat.ListChevronsUpDown
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.AppTheme.spacing
import com.github.lmfirefly.flycat.presentation.theme.LocalTopBarBackdrop
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccessControlScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = spacing
    val mainLikePadding = rememberStandalonePageMainPadding()
    val viewModel = koinViewModel<AccessControlViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importStrategy by viewModel.importStrategy.state.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val settingsFabController = rememberScrollAwareFabController()
    var showSortMenu by remember { mutableStateOf(false) }
    var showOpsMenu by remember { mutableStateOf(false) }
    val sortAnchor = rememberGlassPopupAnchor()
    val opsAnchor = rememberGlassPopupAnchor()
    val mainListState = rememberLazyListState()
    var searchStatus by remember {
        mutableStateOf(
            SearchStatus(
                label = FlyTxt.AccessControl.Search.Placeholder,
                searchText = uiState.searchQuery,
            )
        )
    }
    val dynamicTopPadding by remember { derivedStateOf { spacing.space12 * (1f - scrollBehavior.state.collapsedFraction) } }
    val listStartPadding = spacing.screenHorizontal
    val listEndPadding = spacing.screenHorizontal
    val currentSearchStatus = remember(searchStatus, filteredApps) {
        searchStatus.copy(
            resultStatus = when {
                searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                filteredApps.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                else -> SearchStatus.ResultStatus.SHOW
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
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

    // 每当排序变化（排序模式或选中项优先）时，将主列表跳转回顶部；否则，保持之前的滚动偏移量，即使列表已重新排序。
    LaunchedEffect(uiState.sortMode, uiState.selectedFirst) { mainListState.scrollToItem(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassScaffold(
            topBar = {
                currentSearchStatus.TopAppBarAnim {
                    TopBar(
                        title = FlyTxt.AccessControl.Title,
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 0.dp,
                        navigationIcon = { NavigationBackIcon(navigator = navigator) },
                        actions = {
                            GlassTopActionPill(
                                actions = listOf(
                                    GlassTopAction(
                                        icon = FlyCat.ListChevronsUpDown,
                                        contentDescription = FlyTxt.AccessControl.Settings.SortMode,
                                        onClick = { showSortMenu = true },
                                        anchor = sortAnchor,
                                        popup = {
                                            AccessControlSortMenu(
                                                show = showSortMenu,
                                                anchor = sortAnchor,
                                                sortMode = uiState.sortMode,
                                                onDismiss = { showSortMenu = false },
                                                onSortModeChange = viewModel::onSortModeChange,
                                            )
                                        },
                                    ),
                                    GlassTopAction(
                                        icon = FlyCat.Bolt,
                                        contentDescription = FlyTxt.AccessControl.Settings.Title,
                                        onClick = { showOpsMenu = true },
                                        anchor = opsAnchor,
                                        popup = {
                                            AccessControlOperationsMenu(
                                                show = showOpsMenu,
                                                anchor = opsAnchor,
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
                                                importStrategy = importStrategy,
                                                onImportStrategyChange = viewModel::onImportStrategyChange,
                                            )
                                        },
                                    ),
                                ),
                                backdrop = LocalTopBarBackdrop.current,
                            )
                        },
                        bottomContent = {
                            AccessControlSearchBar(
                                query = searchStatus.searchText,
                                onQueryChange = { searchStatus = searchStatus.copy(searchText = it) },
                                label = currentSearchStatus.label,
                                topPadding = dynamicTopPadding,
                                startPadding = listStartPadding,
                                endPadding = listEndPadding,
                            )
                        },
                    )
                }
            },
        ) { innerPadding ->
            val combinedInnerPadding = combinePaddingValues(innerPadding, mainLikePadding)
            val listBottomPadding = combinedInnerPadding.calculateBottomPadding()

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(
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
                if (searchStatus.searchText.isNotBlank() && filteredApps.isEmpty()) {
                    SearchEmptyState(text = FlyTxt.AccessControl.Search.Empty, modifier = Modifier.padding(bottom = listBottomPadding))
                } else {
                    ScreenLazyColumn(
                        scrollBehavior = scrollBehavior,
                        innerPadding = combinedInnerPadding,
                        lazyListState = mainListState,
                        contentPadding = PaddingValues(
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
        }
    }
}

@Composable
private fun AccessControlSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val isLightTheme = !isDarkTheme()
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainerHigh
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        color = Color.Transparent,
        label = label,
        onSearch = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        },
        expanded = false,
        onExpandedChange = {},
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = FlyTxt.Component.Editor.Action.Search,
                modifier = Modifier.size(AppTheme.sizes.searchIconTouchTarget).padding(start = spacing.space16, end = spacing.space8),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(AppTheme.sizes.searchIconTouchTarget).padding(start = spacing.space8, end = spacing.space16),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = FlyTxt.Component.Button.Clear,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = startPadding, end = endPadding).padding(top = topPadding, bottom = AppTheme.sizes.searchBarBottomPadding).clip(CircleShape).glassCardSurface(CircleShape, fallbackColor = surfaceColor).edgeStroke(
            shape = CircleShape,
            stroke = if (isLightTheme) GlassEdgeStrokes.SmallLight else GlassEdgeStrokes.SmallDark,
        ),
    )
}

@Composable
private fun AccessControlSortMenu(
    show: Boolean,
    anchor: GlassPopupAnchor,
    sortMode: AccessControlViewModel.SortMode,
    onDismiss: () -> Unit,
    onSortModeChange: (AccessControlViewModel.SortMode) -> Unit,
) {
    GlassPopupHost(
        show = show,
        onDismissRequest = onDismiss,
        anchor = anchor,
        anchorContent = { GlassTopActionAnchorIcon(FlyCat.ListChevronsUpDown) },
    ) {
        AccessControlViewModel.SortMode.entries.forEach { mode ->
            GlassMenuItem(
                text = mode.displayName,
                selected = mode == sortMode,
                onClick = {
                    onSortModeChange(mode)
                    onDismiss()
                },
            )
        }
    }
}

/** 操作菜单的级联子页。 */
private enum class OpsMenuPage {
    None,
    Batch,
    Region,
    ImportExport,
    ImportStrategy,
}

@Composable
private fun AccessControlOperationsMenu(
    show: Boolean,
    anchor: GlassPopupAnchor,
    uiState: AccessControlViewModel.UiState,
    onDismiss: () -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onSelectedFirstChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectChinaApps: () -> Unit,
    onSelectNonChinaApps: () -> Unit,
    onImportPackages: (String) -> Int,
    onExportPackages: () -> String,
    importStrategy: AccessControlImportStrategy,
    onImportStrategyChange: (AccessControlImportStrategy) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val settings = FlyTxt.AccessControl.Settings
    val submenu = rememberGlassSubmenuState(show)
    var submenuPage by remember { mutableStateOf(OpsMenuPage.None) }
    val applyAndClose: (() -> Unit) -> Unit = { action ->
        action()
        submenu.close()
        onDismiss()
    }
    val importFromClipboard = {
        val text = clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
            .orEmpty()
        if (text.isNotEmpty()) {
            onImportPackages(text)
        }
        Unit
    }
    val exportToClipboard = {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("packages", onExportPackages()))
        Unit
    }

    GlassPopupHost(
        show = show,
        onDismissRequest = onDismiss,
        anchor = anchor,
        anchorContent = { GlassTopActionAnchorIcon(FlyCat.Bolt) },
        submenu = submenu,
        submenuContent = {
            when (submenuPage) {
                OpsMenuPage.Batch -> {
                    GlassSubmenuPageHeader(submenu, settings.BatchOperation, anchor)
                    GlassMenuItem(settings.SelectAll, onClick = { applyAndClose(onSelectAll) })
                    GlassMenuItem(settings.DeselectAll, onClick = { applyAndClose(onDeselectAll) })
                    GlassMenuItem(settings.Invert, onClick = { applyAndClose(onInvertSelection) })
                }
                OpsMenuPage.Region -> {
                    GlassSubmenuPageHeader(submenu, settings.RegionQuickSelect, anchor)
                    GlassMenuItem(settings.ChinaApps, onClick = { applyAndClose(onSelectChinaApps) })
                    GlassMenuItem(settings.OverseasApps, onClick = { applyAndClose(onSelectNonChinaApps) })
                }
                OpsMenuPage.ImportExport -> {
                    GlassSubmenuPageHeader(submenu, settings.ImportExport, anchor)
                    GlassMenuItem(settings.Import, onClick = { applyAndClose(importFromClipboard) })
                    GlassMenuItem(settings.Export, onClick = { applyAndClose(exportToClipboard) })
                }
                OpsMenuPage.ImportStrategy -> {
                    GlassSubmenuPageHeader(submenu, FlyTxt.NetworkSettings.ProxyOptions.ImportStrategyTitle, anchor)
                    GlassMenuItem(
                        text = FlyTxt.MetaFeature.Backup.RestoreMerge,
                        selected = importStrategy == AccessControlImportStrategy.MERGE,
                        onClick = { applyAndClose { onImportStrategyChange(AccessControlImportStrategy.MERGE) } },
                    )
                    GlassMenuItem(
                        text = FlyTxt.MetaFeature.Backup.RestoreOverwrite,
                        selected = importStrategy == AccessControlImportStrategy.OVERWRITE,
                        onClick = { applyAndClose { onImportStrategyChange(AccessControlImportStrategy.OVERWRITE) } },
                    )
                }
                OpsMenuPage.None -> Unit
            }
        },
    ) {
        GlassMenuItem(
            text = settings.ShowSystemApps,
            selected = uiState.showSystemApps,
            onClick = { applyAndClose { onShowSystemAppsChange(!uiState.showSystemApps) } },
        )
        GlassMenuItem(
            text = settings.SelectedFirst,
            selected = uiState.selectedFirst,
            onClick = { applyAndClose { onSelectedFirstChange(!uiState.selectedFirst) } },
        )
        GlassMenuDivider()
        GlassPopupSubmenuItem(
            text = settings.BatchOperation,
            anchor = anchor,
            open = submenu.open && submenuPage == OpsMenuPage.Batch,
            onOpen = { bounds ->
                submenuPage = OpsMenuPage.Batch
                submenu.openAt(bounds)
            },
        )
        GlassPopupSubmenuItem(
            text = settings.RegionQuickSelect,
            anchor = anchor,
            open = submenu.open && submenuPage == OpsMenuPage.Region,
            onOpen = { bounds ->
                submenuPage = OpsMenuPage.Region
                submenu.openAt(bounds)
            },
        )
        GlassPopupSubmenuItem(
            text = settings.ImportExport,
            anchor = anchor,
            open = submenu.open && submenuPage == OpsMenuPage.ImportExport,
            onOpen = { bounds ->
                submenuPage = OpsMenuPage.ImportExport
                submenu.openAt(bounds)
            },
        )
        GlassPopupSubmenuItem(
            text = FlyTxt.NetworkSettings.ProxyOptions.ImportStrategyTitle,
            anchor = anchor,
            open = submenu.open && submenuPage == OpsMenuPage.ImportStrategy,
            onOpen = { bounds ->
                submenuPage = OpsMenuPage.ImportStrategy
                submenu.openAt(bounds)
            },
        )
    }
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
            // 将默认的16dp垂直内边距稍微减小，使行间距更紧凑，水平方向保持16dp不变，以确保卡片宽度不变。
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
    val bitmap = rememberAppIconBitmap(packageName = packageName, bitmapSize = bitmapSize) ?: return
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier.size(imageSize),
    )
}

@Composable
private fun SearchEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
}
