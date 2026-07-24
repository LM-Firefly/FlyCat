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

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.OverrideContentType
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.*
import com.github.yumelira.yumebox.presentation.theme.Spacing
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.viewmodel.OverrideConfigViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private val overrideConfigItemGap = Spacing().space12

@Composable
fun OverrideListScreen(
    onOpenCodeEditor: (OverrideConfig) -> Unit,
    viewModel: OverrideConfigViewModel = koinViewModel(),
) {
    val builtInConfigs by viewModel.builtInConfigs.collectAsState()
    val userConfigs by viewModel.userConfigs.collectAsState()
    val usageCountMap by viewModel.usageCountMap.collectAsState()
    val pendingRevealConfigId by viewModel.pendingRevealConfigId.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = MiuixScrollBehavior()

    val showCreateDialog = remember { mutableStateOf(false) }
    var createDialogMode by remember { mutableStateOf(OverrideConfigInputMode.CreateNew) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val deleteTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }
    val exportTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }
    val applyTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }
    val showApplySheet = remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val createFabController = rememberOverrideFabController()
    val builtInItems =
        remember(builtInConfigs, usageCountMap) {
            builtInConfigs.map { config ->
                OverrideConfigListItem(
                    config = config,
                    isInUse = (usageCountMap[config.id] ?: 0) > 0,
                    isBuiltIn = true,
                )
            }
        }
    val userItems =
        remember(userConfigs, usageCountMap) {
            userConfigs.map { config ->
                OverrideConfigListItem(
                    config = config,
                    isInUse = (usageCountMap[config.id] ?: 0) > 0,
                    isBuiltIn = false,
                )
            }
        }
    // Reorder only applies to the user section; list indices must skip built-in rows + titles.
    // Layout: [builtin title?] + N built-in cards + [user title?] + M user cards
    val userListIndexOffset =
        remember(builtInItems, userItems) {
            val builtinBlock =
                if (builtInItems.isEmpty()) 0 else 1 + builtInItems.size
            val userTitle = if (userItems.isEmpty()) 0 else 1
            builtinBlock + userTitle
        }
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromUser = from.index - userListIndexOffset
            val toUser = to.index - userListIndexOffset
            if (fromUser < 0 || toUser < 0) return@rememberReorderableLazyListState
            viewModel.reorderUserConfigs(fromUser, toUser)
        }

    val exportConfigLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            val targetConfig = exportTargetConfig.value
            if (uri == null || targetConfig == null) {
                exportTargetConfig.value = null
                return@rememberLauncherForActivityResult
            }

            runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(targetConfig.content.toByteArray())
                        output.flush()
                    } ?: error(YumeTxt.Override.Export.Failed.format(targetConfig.name))
                }
                .onSuccess {
                    context.toast(YumeTxt.Override.Export.Success.format(targetConfig.name))
                }
                .onFailure { error ->
                    context.toast(YumeTxt.Override.Export.Failed.format(error.message))
                }

            exportTargetConfig.value = null
        }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only jump after an explicit create/import/duplicate. Re-run when list data arrives so we
    // don't consume the pending id before the new row is composed.
    LaunchedEffect(pendingRevealConfigId, builtInItems, userItems, userListIndexOffset) {
        val targetId = pendingRevealConfigId ?: return@LaunchedEffect
        val builtInIndex = builtInItems.indexOfFirst { it.config.id == targetId }
        val userIndex = userItems.indexOfFirst { it.config.id == targetId }
        val targetIndex =
            when {
                builtInIndex >= 0 ->
                    (if (builtInItems.isEmpty()) 0 else 1) + builtInIndex
                userIndex >= 0 -> userListIndexOffset + userIndex
                else -> return@LaunchedEffect
            }
        listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
        viewModel.consumePendingRevealConfig(targetId)
    }

    Scaffold(
        floatingActionButton = {
            OverrideAnimatedFab(
                controller = createFabController,
                visible = !showCreateDialog.value,
                imageVector = Yume.`Badge-plus`,
                contentDescription = YumeTxt.Override.Action.Create,
                onClick = {
                    createDialogMode = OverrideConfigInputMode.CreateNew
                    showCreateDialog.value = true
                },
            )
        },
        topBar = { TopBar(title = YumeTxt.Override.Title, scrollBehavior = scrollBehavior) },
    ) { paddingValues ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(paddingValues, mainLikePadding),
            lazyListState = listState,
            onScrollDirectionChanged = createFabController::onScrollDirectionChanged,
        ) {
            // Built-ins first. No empty-state placeholder — with bundled overrides the list is never blank.
            if (builtInItems.isNotEmpty()) {
                item(key = "section-builtin", contentType = "section-title") {
                    Title(YumeTxt.Override.Section.BuiltIn)
                }
                items(
                    items = builtInItems,
                    key = { it.config.id },
                    contentType = { "override-builtin-card" },
                ) { item ->
                    val config = item.config
                    OverrideConfigCard(
                        config = config,
                        isDragging = false,
                        isInUse = item.isInUse,
                        isBuiltIn = true,
                        onApply = { openApplySheet(config, applyTargetConfig, showApplySheet) },
                        onCopy = { viewModel.duplicateConfig(config.id) },
                        onExport = {
                            exportTargetConfig.value = config
                            exportConfigLauncher.launch(
                                "${config.name}.${config.contentType.extension}"
                            )
                        },
                        onEdit = { onOpenCodeEditor(config) },
                        onDelete = null,
                        enableDrag = false,
                    )
                }
            }

            // "导入覆写" section only appears when the user actually has imports — no empty title/hint.
            if (userItems.isNotEmpty()) {
                item(key = "section-user", contentType = "section-title") {
                    Title(YumeTxt.Override.Section.User)
                }
                items(
                    items = userItems,
                    key = { it.config.id },
                    contentType = { "override-config-card" },
                ) { item ->
                    val config = item.config
                    ReorderableItem(state = reorderState, key = config.id) { isDragging ->
                        OverrideConfigCard(
                            config = config,
                            isDragging = isDragging,
                            isInUse = item.isInUse,
                            isBuiltIn = false,
                            onApply = { openApplySheet(config, applyTargetConfig, showApplySheet) },
                            onCopy = { viewModel.duplicateConfig(config.id) },
                            onExport = {
                                exportTargetConfig.value = config
                                exportConfigLauncher.launch(
                                    "${config.name}.${config.contentType.extension}"
                                )
                            },
                            onEdit = { onOpenCodeEditor(config) },
                            onDelete = {
                                deleteTargetConfig.value = config
                                showDeleteDialog.value = true
                            },
                            enableDrag = true,
                        )
                    }
                }
            }
        }

        CreateConfigDialog(
            show = showCreateDialog,
            initialMode = createDialogMode,
            onConfirmCreate = { name, contentType ->
                viewModel.createConfig(name = name, contentType = contentType)
                showCreateDialog.value = false
            },
            onConfirmImport = viewModel::importConfig,
            onConfirmNetworkImport = viewModel::importConfigFromUrl,
            onDismiss = { showCreateDialog.value = false },
        )

        DeleteConfirmDialog(
            show = showDeleteDialog,
            config = deleteTargetConfig.value,
            viewModel = viewModel,
            onConfirm = {
                deleteTargetConfig.value?.id?.let(viewModel::deleteConfig)
                deleteTargetConfig.value = null
                showDeleteDialog.value = false
            },
            onDismiss = {
                deleteTargetConfig.value = null
                showDeleteDialog.value = false
            },
        )

        OverrideApplyToProfilesSheet(
            show = showApplySheet,
            config = applyTargetConfig.value,
            viewModel = viewModel,
            onDismiss = { showApplySheet.value = false },
            onDismissFinished = { applyTargetConfig.value = null },
        )
    }
}

private fun openApplySheet(
    config: OverrideConfig,
    applyTargetConfig: MutableState<OverrideConfig?>,
    showApplySheet: MutableState<Boolean>,
) {
    applyTargetConfig.value = config
    showApplySheet.value = true
}

/**
 * Inverse of the per-subscription override picker: pick which subscriptions should bind
 * this one override. Global switch = all selected; individual rows stay free of dividers to
 * match the cleaned-up subscription settings sheet.
 */
@Composable
private fun OverrideApplyToProfilesSheet(
    show: MutableState<Boolean>,
    config: OverrideConfig?,
    viewModel: OverrideConfigViewModel,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val isShown = show.value && config != null

    var profiles by remember { mutableStateOf(emptyList<Profile>()) }
    var selectedProfileIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionSeeded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(isShown, config?.id) {
        // Capture first so the null check smart-casts; checking `config == null` after
        // `isShown` would be always-false (isShown already implies config != null).
        val target = config
        if (target == null || !isShown) {
            profiles = emptyList()
            selectedProfileIds = emptySet()
            selectionSeeded = false
            isLoading = false
            isSaving = false
            return@LaunchedEffect
        }
        isLoading = true
        selectionSeeded = false
        viewModel
            .loadApplySnapshot(target.id)
            .onSuccess { snapshot ->
                profiles = snapshot.profiles
                selectedProfileIds = snapshot.selectedProfileIds
                selectionSeeded = true
            }
            .onFailure { error ->
                context.toast(
                    YumeTxt.Override.ApplySheet.Failed.format(
                        error.message ?: YumeTxt.Util.Error.UnknownError
                    )
                )
                profiles = emptyList()
                selectedProfileIds = emptySet()
                selectionSeeded = true
            }
        isLoading = false
    }

    val allProfileIds = remember(profiles) { profiles.map { it.uuid.toString() }.toSet() }
    val selectionReady = selectionSeeded && !isLoading
    val applyGlobally =
        allProfileIds.isNotEmpty() && selectedProfileIds.containsAll(allProfileIds)

    val saveSelection = {
        val target = config
        if (!isSaving && target != null && selectionSeeded) {
            scope.launch {
                isSaving = true
                viewModel
                    .applyOverrideToProfiles(target.id, selectedProfileIds)
                    .onSuccess {
                        context.toast(YumeTxt.Override.ApplySheet.Success)
                        onDismiss()
                    }
                    .onFailure { error ->
                        context.toast(
                            YumeTxt.Override.ApplySheet.Failed.format(
                                error.message ?: YumeTxt.Util.Error.UnknownError
                            )
                        )
                    }
                isSaving = false
            }
        }
    }

    AppActionBottomSheet(
        show = isShown,
        title = YumeTxt.Override.ApplySheet.Title,
        startAction = {
            AppBottomSheetCloseAction(
                onClick = onDismiss,
                contentDescription = YumeTxt.Override.ApplySheet.Button.Cancel,
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = !isSaving && !isLoading && selectionSeeded,
                onClick = saveSelection,
                contentDescription = YumeTxt.Override.ApplySheet.Button.Confirm,
            )
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = true,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = UiDp.dp16),
            verticalArrangement = Arrangement.spacedBy(spacing.space16),
        ) {
            Card {
                SwitchPreference(
                    title = YumeTxt.Override.ApplySheet.Global,
                    summary = YumeTxt.Override.ApplySheet.GlobalSummary,
                    checked = applyGlobally,
                    enabled = selectionReady && allProfileIds.isNotEmpty(),
                    onCheckedChange = { enabled ->
                        if (selectionReady && allProfileIds.isNotEmpty()) {
                            selectedProfileIds =
                                if (enabled) {
                                    allProfileIds
                                } else {
                                    emptySet()
                                }
                        }
                    },
                )
            }

            when {
                isLoading || !selectionSeeded -> {
                    // Keep sheet height stable while the profile list loads.
                    Spacer(modifier = Modifier.height(UiDp.dp24))
                }
                profiles.isEmpty() -> {
                    Text(
                        text = YumeTxt.Override.ApplySheet.Empty,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = spacing.space16),
                    )
                }
                else -> {
                    Card {
                        LazyColumn(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(max = componentSizes.profileSettingsListMaxHeight)
                        ) {
                            items(
                                items = profiles,
                                key = { profile -> profile.uuid.toString() },
                            ) { profile ->
                                val profileId = profile.uuid.toString()
                                val isSelected = profileId in selectedProfileIds
                                CheckboxPreference(
                                    title = profile.name,
                                    checked = isSelected,
                                    checkboxLocation = CheckboxLocation.End,
                                    onCheckedChange = { checked ->
                                        selectedProfileIds =
                                            if (checked) {
                                                selectedProfileIds + profileId
                                            } else {
                                                selectedProfileIds - profileId
                                            }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.OverrideConfigCard(
    config: OverrideConfig,
    isDragging: Boolean,
    isInUse: Boolean,
    isBuiltIn: Boolean,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    enableDrag: Boolean,
) {
    OverrideConfigCardContent(
        config = config,
        isDragging = isDragging,
        isInUse = isInUse,
        isBuiltIn = isBuiltIn,
        onApply = onApply,
        onCopy = onCopy,
        onExport = onExport,
        onEdit = onEdit,
        onDelete = onDelete,
        modifier =
            if (enableDrag) {
                Modifier.longPressDraggableHandle()
            } else {
                Modifier
            },
    )
}

@Composable
private fun OverrideConfigCard(
    config: OverrideConfig,
    isDragging: Boolean,
    isInUse: Boolean,
    isBuiltIn: Boolean,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    enableDrag: Boolean,
) {
    // Non-reorderable overload for built-ins (no ReorderableCollectionItemScope receiver).
    OverrideConfigCardContent(
        config = config,
        isDragging = isDragging,
        isInUse = isInUse,
        isBuiltIn = isBuiltIn,
        onApply = onApply,
        onCopy = onCopy,
        onExport = onExport,
        onEdit = onEdit,
        onDelete = onDelete,
        modifier = Modifier,
    )
    // silence unused for symmetry with reorderable overload
    @Suppress("UNUSED_EXPRESSION")
    enableDrag
}

@Composable
private fun OverrideConfigCardContent(
    config: OverrideConfig,
    isDragging: Boolean,
    isInUse: Boolean,
    isBuiltIn: Boolean,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier,
) {
    val accentTintColor = colorScheme.primary

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = overrideConfigItemGap / 2)
                .then(modifier)
                .alpha(if (isDragging) 0.92f else 1f),
        insideMargin = PaddingValues(UiDp.dp16),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(UiDp.dp8),
                ) {
                    Text(
                        text = config.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = config.contentType.label,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isBuiltIn) {
                    OverrideBuiltInBadge()
                } else {
                    OverrideConfigStateIndicator(inUse = isInUse)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = UiDp.dp12),
                thickness = UiDp.dp0_5,
                color = colorScheme.outline.copy(alpha = 0.5f),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp8)) {
                    OverrideCardActionIconButton(
                        imageVector = Yume.Copy,
                        contentDescription = YumeTxt.Override.Card.Copy,
                        onClick = onCopy,
                    )
                    OverrideCardActionIconButton(
                        imageVector = Yume.Share,
                        contentDescription = YumeTxt.Override.Card.Export,
                        onClick = onExport,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Right cluster mirrors ProfileCard: labeled actions (Apply + Edit [+ Delete]).
                IconButton(
                    modifier = Modifier.padding(end = UiDp.dp8),
                    backgroundColor = colorScheme.secondaryContainer.copy(alpha = 0.78f),
                    minHeight = UiDp.dp35,
                    minWidth = UiDp.dp35,
                    onClick = onApply,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = UiDp.dp10),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiDp.dp2),
                    ) {
                        Icon(
                            modifier = Modifier.size(UiDp.dp20),
                            imageVector = Yume.Diff,
                            tint = colorScheme.onSurface.copy(alpha = 0.85f),
                            contentDescription = YumeTxt.Override.Card.Apply,
                        )
                        Text(
                            modifier = Modifier.padding(end = UiDp.dp3),
                            text = YumeTxt.Override.Card.ApplyButton,
                            color = colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                    }
                }

                IconButton(
                    modifier = Modifier.padding(end = if (onDelete != null) UiDp.dp8 else UiDp.dp0),
                    backgroundColor = colorScheme.primary.copy(alpha = 0.1f),
                    minHeight = UiDp.dp35,
                    minWidth = UiDp.dp35,
                    onClick = onEdit,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = UiDp.dp10),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiDp.dp2),
                    ) {
                        Icon(
                            modifier = Modifier.size(UiDp.dp20),
                            imageVector = Yume.Edit,
                            tint = accentTintColor,
                            contentDescription = YumeTxt.Override.Card.Edit,
                        )
                        Text(
                            modifier = Modifier.padding(end = UiDp.dp3),
                            text = YumeTxt.Override.Card.EditButton,
                            color = accentTintColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        backgroundColor = colorScheme.secondaryContainer.copy(alpha = 0.78f),
                        minHeight = UiDp.dp35,
                        minWidth = UiDp.dp35,
                        onClick = onDelete,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = UiDp.dp10),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                modifier = Modifier.size(UiDp.dp20),
                                imageVector = Yume.Delete,
                                tint = colorScheme.onSurface.copy(alpha = 0.85f),
                                contentDescription = YumeTxt.Override.Card.Delete,
                            )
                            Text(
                                modifier = Modifier.padding(start = UiDp.dp4, end = UiDp.dp3),
                                text = YumeTxt.Override.Card.DeleteButton,
                                color = colorScheme.onSurface.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverrideConfigStateIndicator(inUse: Boolean) {
    val tint = if (inUse) colorScheme.primary else colorScheme.onSurfaceVariantSummary
    OverrideStatusBadge(
        imageVector = if (inUse) Yume.ShieldCheck else Yume.ShieldMinus,
        contentDescription =
            if (inUse) YumeTxt.Override.Status.InUse else YumeTxt.Override.Status.NotInUse,
        tint = tint,
        backgroundColor =
            if (inUse) {
                colorScheme.primary.copy(alpha = 0.1f)
            } else {
                colorScheme.secondaryContainer.copy(alpha = 0.78f)
            },
    )
}

@Composable
private fun OverrideBuiltInBadge() {
    OverrideStatusBadge(
        imageVector = Yume.ShieldCheck,
        contentDescription = YumeTxt.Override.Status.BuiltIn,
        tint = colorScheme.onSurfaceVariantSummary,
        backgroundColor = colorScheme.secondaryContainer.copy(alpha = 0.78f),
    )
}

@Composable
private fun CreateConfigDialog(
    show: MutableState<Boolean>,
    initialMode: OverrideConfigInputMode,
    onConfirmCreate: (String, OverrideContentType) -> Unit,
    onConfirmImport: suspend (String, String) -> Result<OverrideConfig>,
    onConfirmNetworkImport: suspend (String) -> Result<OverrideConfig>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var inputMode by remember(show.value, initialMode) { mutableStateOf(initialMode) }
    val nameTextFieldValueState = remember(show.value) { mutableStateOf(TextFieldValue()) }
    var contentType by remember(show.value) { mutableStateOf(OverrideContentType.Yaml) }
    var selectedImportUri by remember(show.value) { mutableStateOf<Uri?>(null) }
    var selectedImportFileName by remember(show.value) { mutableStateOf("") }
    var networkImportUrl by remember(show.value) { mutableStateOf("") }
    var isImporting by remember(show.value) { mutableStateOf(false) }
    val canConfirm =
        when (inputMode) {
            OverrideConfigInputMode.CreateNew -> nameTextFieldValueState.value.text.isNotBlank()
            OverrideConfigInputMode.LocalFile ->
                selectedImportUri != null && selectedImportFileName.isNotBlank()
            OverrideConfigInputMode.NetworkUrl -> networkImportUrl.isNotBlank() && !isImporting
        }
    val importConfigLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            selectedImportUri = uri
            selectedImportFileName =
                uri?.let { selectedUri ->
                        context.contentResolver
                            .query(
                                selectedUri,
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                null,
                                null,
                                null,
                            )
                            ?.use { cursor ->
                                val columnIndex =
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && columnIndex >= 0) {
                                    cursor.getString(columnIndex)
                                } else {
                                    ""
                                }
                            }
                            .orEmpty()
                            .ifBlank {
                                selectedUri.lastPathSegment
                                    ?.substringAfterLast('/')
                                    ?.substringAfterLast('\\')
                                    .orEmpty()
                            }
                    }
                    .orEmpty()
        }

    AppActionBottomSheet(
        show = show.value,
        title = YumeTxt.Override.Dialog.Create.Title,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = canConfirm && !isImporting,
                contentDescription = YumeTxt.Override.Action.Create,
                onClick = {
                    if (!canConfirm) return@AppBottomSheetConfirmAction
                    keyboardController?.hide()
                    when (inputMode) {
                        OverrideConfigInputMode.CreateNew -> {
                            onConfirmCreate(nameTextFieldValueState.value.text, contentType)
                        }

                        OverrideConfigInputMode.LocalFile -> {
                            val importUri = selectedImportUri ?: return@AppBottomSheetConfirmAction
                            runCatching {
                                    context.contentResolver
                                        .openInputStream(importUri)
                                        ?.bufferedReader()
                                        ?.use { reader -> reader.readText() }
                                        ?: error(YumeTxt.Override.Import.ReadError)
                                }
                                .onSuccess { content ->
                                    scope.launch {
                                        isImporting = true
                                        onConfirmImport(content, selectedImportFileName)
                                            .onSuccess { show.value = false }
                                            .onFailure { error ->
                                                context.toast(
                                                    error.message
                                                        ?: YumeTxt.Override.Import.ReadError
                                                )
                                            }
                                        isImporting = false
                                    }
                                }
                                .onFailure { error ->
                                    context.toast(
                                        YumeTxt.Override.Import.FileError.format(error.message)
                                    )
                                }
                        }

                        OverrideConfigInputMode.NetworkUrl -> {
                            val url = networkImportUrl.trim()
                            scope.launch {
                                isImporting = true
                                onConfirmNetworkImport(url)
                                    .onSuccess { show.value = false }
                                    .onFailure { error ->
                                        context.toast(
                                            YumeTxt.Override.Import.NetworkError.format(
                                                error.message ?: YumeTxt.Util.Error.UnknownError
                                            )
                                        )
                                    }
                                isImporting = false
                            }
                        }
                    }
                },
            )
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing))
                    .padding(bottom = UiDp.dp16),
            verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
        ) {
            OverrideInputModeSelector(
                selectedMode = inputMode,
                onSelectedModeChange = { inputMode = it },
            )
            if (inputMode == OverrideConfigInputMode.CreateNew) {
                OverrideTypeSelector(
                    selectedType = contentType,
                    onSelectedTypeChange = { contentType = it },
                )
            }
            when (inputMode) {
                OverrideConfigInputMode.CreateNew -> {
                    TextField(
                        value = nameTextFieldValueState.value,
                        onValueChange = { updatedTextFieldValue ->
                            nameTextFieldValueState.value = updatedTextFieldValue
                        },
                        label = YumeTxt.Override.Dialog.Create.Name,
                        useLabelAsPlaceholder = true,
                    )
                }

                OverrideConfigInputMode.LocalFile -> {
                    ImportOverrideFileContent(
                        modifier = Modifier.fillMaxWidth(),
                        fileName = selectedImportFileName,
                        onPickFile = { importConfigLauncher.launch("*/*") },
                    )
                }

                OverrideConfigInputMode.NetworkUrl -> {
                    ImportOverrideNetworkContent(
                        modifier = Modifier.fillMaxWidth(),
                        url = networkImportUrl,
                        enabled = !isImporting,
                        onUrlChange = { networkImportUrl = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverrideInputModeSelector(
    selectedMode: OverrideConfigInputMode,
    onSelectedModeChange: (OverrideConfigInputMode) -> Unit,
) {
    val inputModeOptions = remember { OverrideConfigInputMode.entries.toList() }
    val selectedModeIndex = inputModeOptions.indexOf(selectedMode).coerceAtLeast(0)

    top.yukonga.miuix.kmp.basic.Card {
        OverlayDropdownPreference(
            title = YumeTxt.ProfilesPage.Type.Title,
            items = inputModeOptions.map(OverrideConfigInputMode::label),
            selectedIndex = selectedModeIndex,
            onSelectedIndexChange = { index ->
                inputModeOptions.getOrNull(index)?.let(onSelectedModeChange)
            },
        )
    }
}

@Composable
private fun OverrideTypeSelector(
    selectedType: OverrideContentType,
    onSelectedTypeChange: (OverrideContentType) -> Unit,
) {
    val contentTypeOptions = remember { OverrideContentType.entries.toList() }
    val selectedTypeIndex = contentTypeOptions.indexOf(selectedType).coerceAtLeast(0)

    top.yukonga.miuix.kmp.basic.Card {
        OverlayDropdownPreference(
            title = YumeTxt.Override.Dialog.Create.Type,
            items = contentTypeOptions.map { it.label },
            selectedIndex = selectedTypeIndex,
            onSelectedIndexChange = { index ->
                contentTypeOptions.getOrNull(index)?.let(onSelectedTypeChange)
            },
        )
    }
}

@Composable
private fun ImportOverrideFileContent(
    modifier: Modifier = Modifier,
    fileName: String,
    onPickFile: () -> Unit,
) {
    Box(modifier = modifier) {
        top.yukonga.miuix.kmp.basic.Card {
            BasicComponent(
                title = YumeTxt.ProfilesPage.Input.SelectFile,
                summary =
                    fileName.ifBlank { YumeTxt.ProfilesPage.Validation.SelectFile },
                onClick = onPickFile,
            )
        }
    }
}

@Composable
private fun ImportOverrideNetworkContent(
    modifier: Modifier = Modifier,
    url: String,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
) {
    TextField(
        value = url,
        onValueChange = onUrlChange,
        label = YumeTxt.Override.Dialog.Create.Url,
        useLabelAsPlaceholder = true,
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun DeleteConfirmDialog(
    show: MutableState<Boolean>,
    config: OverrideConfig?,
    viewModel: OverrideConfigViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isInUse by remember { mutableStateOf(false) }

    LaunchedEffect(show.value, config?.id) {
        isInUse = if (show.value && config != null) viewModel.isConfigInUse(config.id) else false
    }

    val summary =
        when {
            config == null -> ""
            isInUse -> YumeTxt.Override.Dialog.Delete.InUseMessage.format(config.name)
            else -> YumeTxt.Override.Dialog.Delete.Message.format(config.name)
        }

    AppDialog(
        show = show.value,
        title = YumeTxt.Override.Dialog.Delete.Title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
            Button(modifier = Modifier.weight(1f), onClick = onDismiss) {
                Text(YumeTxt.Override.Dialog.Button.Cancel)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = YumeTxt.Override.Dialog.Button.Delete, color = colorScheme.onPrimary)
            }
        }
    }
}

private data class OverrideConfigListItem(
    val config: OverrideConfig,
    val isInUse: Boolean,
    val isBuiltIn: Boolean = false,
)

private enum class OverrideConfigInputMode {
    CreateNew,
    LocalFile,
    NetworkUrl,
}

private val OverrideContentType.label: String
    get() =
        when (this) {
            OverrideContentType.Yaml -> "YAML"
            OverrideContentType.JavaScript -> "JavaScript"
        }

private val OverrideConfigInputMode.label: String
    get() =
        when (this) {
            OverrideConfigInputMode.CreateNew -> YumeTxt.Override.Action.New
            OverrideConfigInputMode.LocalFile -> YumeTxt.ProfilesPage.Type.LocalFile
            OverrideConfigInputMode.NetworkUrl -> YumeTxt.Override.Action.NetworkImport
        }
