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

package com.github.yumelira.yumebox.feature.override.presentation.screen

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.OverrideContentType
import com.github.yumelira.yumebox.feature.override.presentation.component.OverrideAnimatedFab
import com.github.yumelira.yumebox.feature.override.presentation.component.OverrideCardActionIconButton
import com.github.yumelira.yumebox.feature.override.presentation.component.OverrideStatusBadge
import com.github.yumelira.yumebox.feature.override.presentation.component.rememberOverrideFabController
import com.github.yumelira.yumebox.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.BadgePlus
import com.github.yumelira.yumebox.presentation.icon.yume.Delete
import com.github.yumelira.yumebox.presentation.icon.yume.Diff
import com.github.yumelira.yumebox.presentation.icon.yume.Edit
import com.github.yumelira.yumebox.presentation.icon.yume.Share
import com.github.yumelira.yumebox.presentation.icon.yume.ShieldCheck
import com.github.yumelira.yumebox.presentation.icon.yume.ShieldMinus
import com.github.yumelira.yumebox.presentation.theme.Spacing
import com.github.yumelira.yumebox.presentation.theme.UiDp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
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
    val createFabController = rememberOverrideFabController()
    val configItems =
        remember(userConfigs, usageCountMap) {
            userConfigs.map { config ->
                OverrideConfigListItem(
                    config = config,
                    isInUse = (usageCountMap[config.id] ?: 0) > 0,
                )
            }
        }
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            viewModel.reorderUserConfigs(from.index, to.index)
        }

    val exportConfigLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
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
                }
                .onSuccess {
                    context.toast(FlyTxt.Override.Export.Success.format(targetConfig.name))
                }
                .onFailure { error ->
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

    LaunchedEffect(configItems, pendingRevealConfigId) {
        val targetId = pendingRevealConfigId ?: return@LaunchedEffect
        val targetIndex = configItems.indexOfFirst { it.config.id == targetId }
        if (targetIndex < 0) return@LaunchedEffect
        listState.animateScrollToItem((targetIndex - 1).coerceAtLeast(0))
        viewModel.consumePendingRevealConfig(targetId)
    }

    Scaffold(
        floatingActionButton = {
            OverrideAnimatedFab(
                controller = createFabController,
                visible = !showCreateDialog.value,
                imageVector = Yume.BadgePlus,
                contentDescription = FlyTxt.Override.Action.Create,
                onClick = {
                    createDialogMode = OverrideConfigInputMode.CreateNew
                    showCreateDialog.value = true
                },
            )
        },
        topBar = { TopBar(title = FlyTxt.Override.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(onNavigateBack = onNavigateBack) }) },
    ) { paddingValues ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(paddingValues, mainLikePadding),
            lazyListState = listState,
            onScrollDirectionChanged = createFabController::onScrollDirectionChanged,
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
                    // Built-in overrides section
                    if (builtInConfigs.isNotEmpty()) {
                        item(key = "builtin-section-header", contentType = "section-header") {
                            Title(FlyTxt.Override.Section.BuiltIn)
                        }
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
                    }
                    // User overrides section
                    if (userConfigs.isNotEmpty()) {
                        item(key = "user-section-header", contentType = "section-header") {
                            Title(FlyTxt.Override.Section.User)
                        }
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
    val accentTintColor = colorScheme.primary

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = overrideConfigItemGap / 2)
                .longPressDraggableHandle()
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
                OverrideConfigStateIndicator(inUse = isInUse)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = UiDp.dp12),
                thickness = UiDp.dp0_5,
                color = colorScheme.outline.copy(alpha = 0.5f),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp8)) {
                    OverrideCardActionIconButton(
                        imageVector = Yume.Share,
                        contentDescription = FlyTxt.Override.Card.Export,
                        onClick = onExport,
                    )
                    OverrideCardActionIconButton(
                        imageVector = Yume.Delete,
                        contentDescription = FlyTxt.Override.Card.Delete,
                        onClick = onDelete,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    modifier = Modifier.padding(start = UiDp.dp4),
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
                            contentDescription = FlyTxt.Override.Card.Apply,
                        )
                        Text(
                            modifier = Modifier.padding(end = UiDp.dp3),
                            text = FlyTxt.Override.Card.ApplyButton,
                            color = colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                    }
                }

                IconButton(
                    modifier = Modifier.padding(start = UiDp.dp4, end = UiDp.dp8),
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
                            contentDescription = FlyTxt.Override.Card.Edit,
                        )
                        Text(
                            modifier = Modifier.padding(end = UiDp.dp3),
                            text = FlyTxt.Override.Card.EditButton,
                            color = accentTintColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
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
            if (inUse) FlyTxt.Override.Status.InUse else FlyTxt.Override.Status.NotInUse,
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
private fun OverrideBuiltInConfigCard(
    config: OverrideConfig,
    onExport: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
) {
    val accentTintColor = colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = overrideConfigItemGap / 2),
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
                OverrideBuiltInIndicator()
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = UiDp.dp12),
                thickness = UiDp.dp0_5,
                color = colorScheme.outline.copy(alpha = 0.5f),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp8)) {
                    OverrideCardActionIconButton(
                        imageVector = Yume.Share,
                        contentDescription = FlyTxt.Override.Card.Export,
                        onClick = onExport,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    modifier = Modifier.padding(start = UiDp.dp4),
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
                            contentDescription = FlyTxt.Override.Card.Apply,
                        )
                        Text(
                            modifier = Modifier.padding(end = UiDp.dp3),
                            text = FlyTxt.Override.Card.ApplyButton,
                            color = colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                    }
                }

                IconButton(
                    modifier = Modifier.padding(start = UiDp.dp4),
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
                            contentDescription = FlyTxt.Override.Card.Edit,
                        )
                        Text(
                            modifier = Modifier.padding(end = UiDp.dp3),
                            text = FlyTxt.Override.Card.EditButton,
                            color = accentTintColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverrideBuiltInIndicator() {
    val tint = colorScheme.primary
    OverrideStatusBadge(
        imageVector = Yume.ShieldCheck,
        contentDescription = FlyTxt.Override.Status.BuiltIn,
        tint = tint,
        backgroundColor = tint.copy(alpha = 0.15f),
    )
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
    val canConfirm =
        when (inputMode) {
            OverrideConfigInputMode.CreateNew -> nameTextFieldValueState.value.text.isNotBlank()
            OverrideConfigInputMode.LocalFile ->
                selectedImportUri != null && selectedImportFileName.isNotBlank()
            OverrideConfigInputMode.NetworkUrl -> networkImportUrl.isNotBlank() && !isNetworkImporting
        }
    val stableContentHeight =
        remember(stableContentHeightPx, density) {
            if (stableContentHeightPx <= 0) {
                UiDp.dp0
            } else {
                with(density) { stableContentHeightPx.toDp() }
            }
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
                                }
                                .onSuccess { content ->
                                    onConfirmImport(content, selectedImportFileName)
                                }
                                .onFailure { error ->
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
                    animationSpec = tween(200),
                    label = "OverrideInputModeContent",
                ) { currentInputMode ->
                    when (currentInputMode) {
                        OverrideConfigInputMode.CreateNew -> {
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth().onSizeChanged {
                                        stableContentHeightPx =
                                            maxOf(stableContentHeightPx, it.height)
                                    },
                                verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
                            ) {
                                TextField(
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
                                modifier =
                                    Modifier.fillMaxWidth().onSizeChanged {
                                        stableContentHeightPx =
                                            maxOf(stableContentHeightPx, it.height)
                                    },
                                fileName = selectedImportFileName,
                                onPickFile = { importConfigLauncher.launch("*/*") },
                            )
                        }

                        OverrideConfigInputMode.NetworkUrl -> {
                            ImportOverrideNetworkContent(
                                modifier =
                                    Modifier.fillMaxWidth().onSizeChanged {
                                        stableContentHeightPx =
                                            maxOf(stableContentHeightPx, it.height)
                                    },
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
    val inputModeOptions = remember { OverrideConfigInputMode.entries.toList() }
    val selectedModeIndex = inputModeOptions.indexOf(selectedMode).coerceAtLeast(0)
    val inputModeItems = remember { inputModeOptions.map { inputMode -> DropdownItem(title = inputMode.label) } }

    top.yukonga.miuix.kmp.basic.Card {
        WindowSpinnerPreference(
            title = FlyTxt.ProfilesPage.Type.Title,
            items = inputModeItems,
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
    val contentTypeItems = remember { contentTypeOptions.map { contentType -> DropdownItem(title = contentType.label) } }

    top.yukonga.miuix.kmp.basic.Card {
        WindowSpinnerPreference(
            title = FlyTxt.Override.Dialog.Create.Type,
            items = contentTypeItems,
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
    Box(
        modifier =
            modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onPickFile,
            )
    ) {
        TextField(
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
    TextField(
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

    val summary =
        when {
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
            Button(modifier = Modifier.weight(1f), onClick = onDismiss) {
                Text(FlyTxt.Override.Dialog.Button.Cancel)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = FlyTxt.Override.Dialog.Button.Delete, color = colorScheme.onPrimary)
            }
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
    get() =
        when (this) {
            OverrideContentType.Yaml -> "YAML"
            OverrideContentType.JavaScript -> "JavaScript"
        }

private val OverrideContentType.exportMimeType: String
    get() = when (this) {
        OverrideContentType.Yaml -> "application/x-yaml"
        OverrideContentType.JavaScript -> "application/javascript"
    }

private val OverrideConfigInputMode.label: String
    get() =
        when (this) {
            OverrideConfigInputMode.CreateNew -> FlyTxt.Override.Action.New
            OverrideConfigInputMode.LocalFile -> FlyTxt.ProfilesPage.Type.LocalFile
            OverrideConfigInputMode.NetworkUrl -> FlyTxt.Override.Action.NetworkImport
        }
