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

package com.github.lmfirefly.flycat.feature.override.presentation.screen

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.override.OverrideContentType
import com.github.lmfirefly.flycat.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownOption
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownPreference
import com.github.lmfirefly.flycat.presentation.component.GlassMenuItem
import com.github.lmfirefly.flycat.presentation.component.GlassPopupHost
import com.github.lmfirefly.flycat.presentation.component.GlassScaffold
import com.github.lmfirefly.flycat.presentation.component.GlassTopAction
import com.github.lmfirefly.flycat.presentation.component.GlassTopActionPill
import com.github.lmfirefly.flycat.presentation.component.SegmentedTabRow
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetConfirmAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppDialog
import com.github.lmfirefly.flycat.presentation.component.GlassTextButton
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.CenteredText
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.component.stickySegmentedTabs
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.BadgePlus
import com.github.lmfirefly.flycat.presentation.icon.flycat.Delete
import com.github.lmfirefly.flycat.presentation.icon.flycat.Diff
import com.github.lmfirefly.flycat.presentation.icon.flycat.Download
import com.github.lmfirefly.flycat.presentation.icon.flycat.Edit
import com.github.lmfirefly.flycat.presentation.icon.flycat.Share
import com.github.lmfirefly.flycat.presentation.icon.flycat.ShieldCheck
import com.github.lmfirefly.flycat.presentation.icon.flycat.ShieldMinus
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalTopBarBackdrop
import com.github.lmfirefly.flycat.presentation.theme.Spacing
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private val overrideConfigItemGap = Spacing().space12

@Composable
fun OverrideListScreen(onNavigateBack: () -> Unit, onOpenCodeEditor: (OverrideConfig) -> Unit) {
    val viewModel: OverrideConfigViewModel = koinViewModel()
    val userConfigs by viewModel.userConfigs.collectAsStateWithLifecycle()
    val builtInConfigs by viewModel.builtInConfigs.collectAsStateWithLifecycle()
    val usageCountMap by viewModel.usageCountMap.collectAsStateWithLifecycle()
    val pendingRevealConfigId by viewModel.pendingRevealConfigId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = MiuixScrollBehavior()

    val showCreateDialog = remember { mutableStateOf(false) }
    var createDialogMode by remember { mutableStateOf(OverrideConfigInputMode.CreateNew) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val deleteTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }
    val exportTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }
    val applyTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }

    val listState = rememberLazyListState()
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val configItems = remember(userConfigs, usageCountMap) {
        userConfigs.map { config ->
            OverrideConfigListItem(
                config = config,
                isInUse = (usageCountMap[config.id] ?: 0) > 0,
            )
        }
    }
    // 吸顶 tab 行永远占第一格，两个分区的条目都从索引 1 开始。
    val userListIndexOffset = 1
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromUser = from.index - userListIndexOffset
        val toUser = to.index - userListIndexOffset
        if (fromUser < 0 || toUser < 0) return@rememberReorderableLazyListState
        viewModel.reorderUserConfigs(fromUser, toUser)
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val targetConfig = exportTargetConfig.value
        if (uri == null || targetConfig == null) {
            exportTargetConfig.value = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(targetConfig.content.toByteArray())
                output.flush()
            } ?: error(FlyTxt.Override.Export.Failed.format(targetConfig.name))
        }.onSuccess {
            context.toast(FlyTxt.Override.Export.Success.format(targetConfig.name))
        }.onFailure { error ->
            context.toast(FlyTxt.Override.Export.Failed.format(error.message))
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

    LaunchedEffect(configItems, builtInConfigs, pendingRevealConfigId, selectedSectionIndex) {
        val targetId = pendingRevealConfigId ?: return@LaunchedEffect
        val builtInIndex = builtInConfigs.indexOfFirst { it.id == targetId }
        val userIndex = configItems.indexOfFirst { it.config.id == targetId }
        val targetSection = when {
            userIndex >= 0 -> 1
            builtInIndex >= 0 -> 0
            else -> return@LaunchedEffect
        }
        if (selectedSectionIndex != targetSection) {
            selectedSectionIndex = targetSection
            return@LaunchedEffect
        }
        val targetIndex = userListIndexOffset + if (targetSection == 0) builtInIndex else userIndex
        listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
        viewModel.consumePendingRevealConfig(targetId)
    }

    GlassScaffold(
        topBar = {
            TopBar(
                title = FlyTxt.Override.Title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(onNavigateBack = onNavigateBack) },
                actions = {
                    GlassTopActionPill(
                        actions = listOf(
                            GlassTopAction(
                                icon = FlyCat.BadgePlus,
                                contentDescription = FlyTxt.Override.Action.Create,
                                onClick = {
                                    createDialogMode = OverrideConfigInputMode.CreateNew
                                    showCreateDialog.value = true
                                },
                            ),
                            GlassTopAction(
                                icon = FlyCat.Download,
                                contentDescription = FlyTxt.Override.Action.NetworkImport,
                                onClick = {
                                    createDialogMode = OverrideConfigInputMode.LocalFile
                                    showCreateDialog.value = true
                                },
                            ),
                        ),
                        backdrop = LocalTopBarBackdrop.current,
                    )
                },
            )
        },
    ) { paddingValues ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(paddingValues, mainLikePadding),
            lazyListState = listState,
        ) {
            when {
                userConfigs.isEmpty() && builtInConfigs.isEmpty() -> {
                    item(key = "override-empty", contentType = "override-empty") {
                        CenteredText(
                            firstLine = FlyTxt.Override.Empty.Title,
                            secondLine = FlyTxt.Override.Empty.Hint,
                            modifier = Modifier.fillParentMaxSize(),
                            showEmptyResourceIllustration = true,
                        )
                    }
                }
                else -> {
                    stickySegmentedTabs(
                        key = "__override_sections__",
                        tabs = listOf(
                            FlyTxt.Override.Section.BuiltIn,
                            FlyTxt.Override.Section.User,
                        ),
                        selectedTabIndex = selectedSectionIndex,
                        onTabSelected = { selectedSectionIndex = it },
                        tabHeight = UiDp.dp42,
                    )
                    if (selectedSectionIndex == 0) {
                        items(
                            items = builtInConfigs,
                            key = { it.id },
                            contentType = { "builtin-override-card" },
                        ) { config ->
                            OverrideBuiltInConfigCard(
                                config = config,
                                onExport = {
                                    exportTargetConfig.value = config
                                    exportConfigLauncher.launch(
                                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = config.contentType.exportMimeType
                                            putExtra(
                                                Intent.EXTRA_TITLE,
                                                "${config.name}.${config.contentType.extension}",
                                            )
                                        },
                                    )
                                },
                                onApply = { applyTargetConfig.value = config },
                                onEdit = { onOpenCodeEditor(config) },
                            )
                        }
                    } else {
                        items(
                            items = configItems,
                            key = { it.config.id },
                            contentType = { "override-config-card" },
                        ) { item ->
                            val config = item.config
                            ReorderableItem(state = reorderState, key = config.id) { isDragging ->
                                OverrideConfigCard(
                                    config = config,
                                    isDragging = isDragging,
                                    isInUse = item.isInUse,
                                    onExport = {
                                        exportTargetConfig.value = config
                                        exportConfigLauncher.launch(
                                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = config.contentType.exportMimeType
                                                putExtra(
                                                    Intent.EXTRA_TITLE,
                                                    "${config.name}.${config.contentType.extension}",
                                                )
                                            },
                                        )
                                    },
                                    onDelete = {
                                        deleteTargetConfig.value = config
                                        showDeleteDialog.value = true
                                    },
                                    onApply = { applyTargetConfig.value = config },
                                    onEdit = { onOpenCodeEditor(config) },
                                )
                            }
                        }
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
            onConfirmImport = { content, sourceName ->
                viewModel
                    .importConfig(content, sourceName)
                    .onSuccess { showCreateDialog.value = false }
                    .onFailure { error ->
                        context.toast(error.message ?: FlyTxt.Override.Import.ReadError)
                    }
            },
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
            target = applyTargetConfig.value,
            viewModel = viewModel,
            onDismiss = { applyTargetConfig.value = null },
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.OverrideConfigCard(
    config: OverrideConfig,
    isDragging: Boolean,
    isInUse: Boolean,
    onExport: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var actionsOpen by remember(config.id) { mutableStateOf(false) }
    var actionsMounted by remember(config.id) { mutableStateOf(false) }
    val actionsMenuAnchor = rememberGlassPopupAnchor()
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = overrideConfigItemGap / 2)
            .longPressDraggableHandle()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    actionsMounted = true
                    actionsOpen = true
                },
            )
            .alpha(if (isDragging) 0.92f else 1f),
        insideMargin = PaddingValues(UiDp.dp16),
    ) {
        OverrideConfigRow(
            config = config,
            anchor = actionsMenuAnchor,
            statusBadge = { OverrideConfigStatusBadge(isBuiltIn = false, isInUse = isInUse) },
        )
    }
    if (actionsMounted) {
        OverrideConfigActionsMenu(
            show = actionsOpen,
            anchor = actionsMenuAnchor,
            isBuiltIn = false,
            isInUse = isInUse,
            onDismiss = { actionsOpen = false },
            onDismissFinished = { actionsMounted = false },
            onEdit = onEdit,
            onApply = onApply,
            onExport = onExport,
            onDelete = { onDelete() },
        )
    }
}

@Composable
private fun OverrideBuiltInConfigCard(
    config: OverrideConfig,
    onExport: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
) {
    var actionsOpen by remember(config.id) { mutableStateOf(false) }
    var actionsMounted by remember(config.id) { mutableStateOf(false) }
    val actionsMenuAnchor = rememberGlassPopupAnchor()
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = overrideConfigItemGap / 2)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    actionsMounted = true
                    actionsOpen = true
                },
            ),
        insideMargin = PaddingValues(UiDp.dp16),
    ) {
        OverrideConfigRow(
            config = config,
            anchor = actionsMenuAnchor,
            statusBadge = { OverrideConfigStatusBadge(isBuiltIn = true, isInUse = true) },
        )
    }
    if (actionsMounted) {
        OverrideConfigActionsMenu(
            show = actionsOpen,
            anchor = actionsMenuAnchor,
            isBuiltIn = true,
            isInUse = true,
            onDismiss = { actionsOpen = false },
            onDismissFinished = { actionsMounted = false },
            onEdit = onEdit,
            onApply = onApply,
            onExport = onExport,
            onDelete = null,
        )
    }
}

/** 卡片主体：名称 + 类型副标题 + 右侧盾牌徽章（兼作操作菜单的弹出锚点）。 */
@Composable
private fun OverrideConfigRow(
    config: OverrideConfig,
    anchor: GlassPopupAnchor,
    statusBadge: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
        verticalAlignment = Alignment.CenterVertically,
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
        Box(
            modifier = Modifier.glassPopupAnchor(
                anchor = anchor,
                cornerRadius = AppTheme.sizes.statusCapsuleHeight / 2,
                floating = true,
            ),
        ) {
            statusBadge()
        }
    }
}

/** 整卡点击弹出的玻璃操作菜单：从盾牌徽章里长出来，收起动画播完才卸载。 */
@Composable
private fun OverrideConfigActionsMenu(
    show: Boolean,
    anchor: GlassPopupAnchor,
    isBuiltIn: Boolean,
    isInUse: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onEdit: () -> Unit,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    GlassPopupHost(
        show = show,
        onDismissRequest = onDismiss,
        anchor = anchor,
        anchorContent = { OverrideConfigStatusBadge(isBuiltIn = isBuiltIn, isInUse = isInUse) },
        onDismissFinished = onDismissFinished,
    ) {
        GlassMenuItem(
            text = FlyTxt.Override.Card.Edit,
            icon = FlyCat.Edit,
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        GlassMenuItem(
            text = FlyTxt.Override.Card.Apply,
            icon = FlyCat.Diff,
            onClick = {
                onDismiss()
                onApply()
            },
        )
        GlassMenuItem(
            text = FlyTxt.Override.Card.Export,
            icon = FlyCat.Share,
            onClick = {
                onDismiss()
                onExport()
            },
        )
        if (onDelete != null) {
            GlassMenuItem(
                text = FlyTxt.Override.Card.Delete,
                icon = FlyCat.Delete,
                contentColor = AppTheme.colors.status.destructive,
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

/** 卡片与菜单共享的盾牌徽章：内置 > 使用中 > 未使用。 */
@Composable
private fun OverrideConfigStatusBadge(isBuiltIn: Boolean, isInUse: Boolean) {
    val active = isInUse && !isBuiltIn
    Box(
        modifier = Modifier.size(AppTheme.sizes.statusCapsuleHeight + AppTheme.spacing.space4).background(
            color = if (active) {
                colorScheme.primary.copy(alpha = AppTheme.opacity.subtle)
            } else {
                colorScheme.secondaryContainer.copy(alpha = AppTheme.opacity.surfaceVariant)
            },
            shape = CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isBuiltIn || isInUse) FlyCat.ShieldCheck else FlyCat.ShieldMinus,
            contentDescription = when {
                isBuiltIn -> FlyTxt.Override.Status.BuiltIn
                isInUse -> FlyTxt.Override.Status.InUse
                else -> FlyTxt.Override.Status.NotInUse
            },
            tint = if (active) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(UiDp.dp18),
        )
    }
}

@Composable
private fun CreateConfigDialog(
    show: MutableState<Boolean>,
    initialMode: OverrideConfigInputMode,
    onConfirmCreate: (String, OverrideContentType) -> Unit,
    onConfirmImport: (String, String) -> Unit,
    onConfirmNetworkImport: suspend (String) -> Result<OverrideConfig>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var inputMode by remember(show.value, initialMode) { mutableStateOf(initialMode) }
    val nameTextFieldValueState = remember(show.value) { mutableStateOf(TextFieldValue()) }
    var contentType by remember(show.value) { mutableStateOf(OverrideContentType.Yaml) }
    var selectedImportUri by remember(show.value) { mutableStateOf<Uri?>(null) }
    var selectedImportFileName by remember(show.value) { mutableStateOf("") }
    var networkImportUrl by remember(show.value) { mutableStateOf("") }
    var isNetworkImporting by remember(show.value) { mutableStateOf(false) }
    var stableContentHeightPx by remember(show.value) { mutableStateOf(0) }
    val canConfirm = when (inputMode) {
        OverrideConfigInputMode.CreateNew -> nameTextFieldValueState.value.text.isNotBlank()
        OverrideConfigInputMode.LocalFile -> selectedImportUri != null && selectedImportFileName.isNotBlank()
        OverrideConfigInputMode.NetworkUrl -> networkImportUrl.isNotBlank() && !isNetworkImporting
    }
    val stableContentHeight = remember(stableContentHeightPx, density) { if (stableContentHeightPx <= 0) { UiDp.dp0 } else { with(density) { stableContentHeightPx.toDp() } } }
    val importConfigLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        selectedImportUri = uri
        selectedImportFileName = uri?.let { selectedUri ->
            context.contentResolver.query(
                selectedUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && columnIndex >= 0) { cursor.getString(columnIndex) } else { "" }
            }.orEmpty().ifBlank {
                selectedUri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\').orEmpty()
            }
        }.orEmpty()
    }

    AppActionBottomSheet(
        show = show.value,
        title = FlyTxt.Override.Dialog.Create.Title,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = canConfirm && !isNetworkImporting,
                contentDescription = FlyTxt.Override.Action.Create,
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
                                    ?: error(FlyTxt.Override.Import.ReadError)
                            }.onSuccess { content ->
                                onConfirmImport(content, selectedImportFileName)
                            }.onFailure { error ->
                                context.toast(
                                    FlyTxt.Override.Import.FileError.format(error.message)
                                )
                            }
                        }

                        OverrideConfigInputMode.NetworkUrl -> {
                            val url = networkImportUrl.trim()
                            scope.launch {
                                isNetworkImporting = true
                                onConfirmNetworkImport(url)
                                    .onSuccess { show.value = false }
                                    .onFailure { error ->
                                        context.toast(
                                            FlyTxt.Override.Import.NetworkError.format(
                                                error.message ?: FlyTxt.Util.Error.UnknownError
                                            )
                                        )
                                    }
                                isNetworkImporting = false
                            }
                        }
                    }
                },
            )
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = UiDp.dp16),
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
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = stableContentHeight)) {
                Crossfade(
                    targetState = inputMode,
                    animationSpec = tween(AnimationSpecs.DURATION_CROSSFADE),
                    label = "OverrideInputModeContent",
                ) { currentInputMode ->
                    when (currentInputMode) {
                        OverrideConfigInputMode.CreateNew -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().onSizeChanged { stableContentHeightPx = maxOf(stableContentHeightPx, it.height) },
                                verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
                            ) {
                                GlassTextField(
                                    value = nameTextFieldValueState.value,
                                    onValueChange = { updatedTextFieldValue ->
                                        nameTextFieldValueState.value = updatedTextFieldValue
                                    },
                                    label = FlyTxt.Override.Dialog.Create.Name,
                                    useLabelAsPlaceholder = true,
                                )
                            }
                        }

                        OverrideConfigInputMode.LocalFile -> {
                            ImportOverrideFileContent(
                                modifier = Modifier.fillMaxWidth().onSizeChanged { stableContentHeightPx = maxOf(stableContentHeightPx, it.height) },
                                fileName = selectedImportFileName,
                                onPickFile = { importConfigLauncher.launch("*/*") },
                            )
                        }

                        OverrideConfigInputMode.NetworkUrl -> {
                            ImportOverrideNetworkContent(
                                modifier = Modifier.fillMaxWidth().onSizeChanged { stableContentHeightPx = maxOf(stableContentHeightPx, it.height) },
                                url = networkImportUrl,
                                enabled = !isNetworkImporting,
                                onUrlChange = { networkImportUrl = it },
                            )
                        }
                    }
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
    val inputModeOptions = OverrideConfigInputMode.entries
    val selectedModeIndex = inputModeOptions.indexOf(selectedMode).coerceAtLeast(0)
    SegmentedTabRow(
        tabs = inputModeOptions.map(OverrideConfigInputMode::label),
        selectedTabIndex = selectedModeIndex,
        onTabSelected = { index -> inputModeOptions.getOrNull(index)?.let(onSelectedModeChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OverrideTypeSelector(
    selectedType: OverrideContentType,
    onSelectedTypeChange: (OverrideContentType) -> Unit,
) {
    GlassDropdownPreference(
        title = FlyTxt.Override.Dialog.Create.Type,
        options = OverrideContentType.entries.map { GlassDropdownOption(it, it.label) },
        selected = selectedType,
        onSelected = onSelectedTypeChange,
        glassSurface = true,
    )
}

@Composable
private fun ImportOverrideFileContent(
    modifier: Modifier = Modifier,
    fileName: String,
    onPickFile: () -> Unit,
) {
    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onPickFile,
        )
    ) {
        GlassTextField(
            value = fileName,
            onValueChange = {},
            label = FlyTxt.ProfilesPage.Input.SelectFile,
            useLabelAsPlaceholder = true,
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImportOverrideNetworkContent(
    modifier: Modifier = Modifier,
    url: String,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
) {
    GlassTextField(
        value = url,
        onValueChange = onUrlChange,
        label = FlyTxt.Override.Dialog.Create.Url,
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

    val summary = when {
        config == null -> ""
        isInUse -> FlyTxt.Override.Dialog.Delete.InUseMessage.format(config.name)
        else -> FlyTxt.Override.Dialog.Delete.Message.format(config.name)
    }

    AppDialog(
        show = show.value,
        title = FlyTxt.Override.Dialog.Delete.Title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
            GlassTextButton(text = FlyTxt.Override.Dialog.Button.Cancel, onClick = onDismiss, modifier = Modifier.weight(1f))
            GlassTextButton(
                text = FlyTxt.Override.Dialog.Button.Delete,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                primary = true,
            )
        }
    }
}

private data class OverrideConfigListItem(
    val config: OverrideConfig,
    val isInUse: Boolean,
)

private enum class OverrideConfigInputMode {
    CreateNew,
    LocalFile,
    NetworkUrl,
}

private val OverrideContentType.label: String
    get() = when (this) {
        OverrideContentType.Yaml -> "YAML"
        OverrideContentType.JavaScript -> "JavaScript"
    }

private val OverrideContentType.exportMimeType: String
    get() = when (this) {
        OverrideContentType.Yaml -> "application/x-yaml"
        OverrideContentType.JavaScript -> "application/javascript"
    }

private val OverrideConfigInputMode.label: String
    get() = when (this) {
        OverrideConfigInputMode.CreateNew -> FlyTxt.Override.Action.New
        OverrideConfigInputMode.LocalFile -> FlyTxt.ProfilesPage.Type.LocalFile
        OverrideConfigInputMode.NetworkUrl -> FlyTxt.Override.Action.NetworkImport
    }
