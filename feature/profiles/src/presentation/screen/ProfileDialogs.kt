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

package com.github.lmfirefly.flycat.feature.profiles.presentation.screen

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.override.OverrideInternalConstants
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import com.github.lmfirefly.flycat.core.util.crypto.AgeKeyCrypto
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetConfirmAction
import com.github.lmfirefly.flycat.presentation.component.input.AgeSecretKeyField
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowRight
import com.github.lmfirefly.flycat.presentation.icon.flycat.Copy
import com.github.lmfirefly.flycat.presentation.icon.flycat.Eye
import com.github.lmfirefly.flycat.presentation.icon.flycat.Sparkles
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ProfileSettingsSection {
    Subscription,
    Override,
}

@Composable
internal fun ProfileSettingsDialog(
    show: Boolean,
    profile: Profile,
    builtInConfigs: List<OverrideConfig>,
    userConfigs: List<OverrideConfig>,
    binding: ProfileBinding?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSaveProfileMeta: (ProfileMetaUpdate) -> Unit,
    onSaveOverrideSettings: suspend (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val selectableConfigs = remember(builtInConfigs, userConfigs) { builtInConfigs + userConfigs }
    val sectionOptions = remember {
        listOf(
            ProfileSettingsSection.Subscription,
            ProfileSettingsSection.Override,
        )
    }

    var selectedSection by remember { mutableStateOf(ProfileSettingsSection.Subscription) }
    var editName by remember {
        mutableStateOf(TextFieldValue(profile.name, TextRange(profile.name.length)))
    }
    var editSource by remember {
        mutableStateOf(TextFieldValue(profile.source, TextRange(profile.source.length)))
    }
    var editAgeSecretKey by remember { mutableStateOf(TextFieldValue()) }
    var ageSecretKeyEdited by remember { mutableStateOf(false) }
    var customRoutingSelected by remember { mutableStateOf(false) }
    var pendingSelectedOverrideIds by remember { mutableStateOf(emptyList<String>()) }
    var overrideSelectionInitialized by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val customRoutingId = OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
    val selectableById = remember(selectableConfigs) { selectableConfigs.associateBy { it.id } }
    val visibleSelectedOverrideIds = remember(pendingSelectedOverrideIds, selectableById) { pendingSelectedOverrideIds.filter(selectableById::containsKey) }
    val overrideRowIds =
        remember(selectableConfigs, pendingSelectedOverrideIds, visibleSelectedOverrideIds) {
            val selectedIds = pendingSelectedOverrideIds.toSet()
            visibleSelectedOverrideIds + selectableConfigs.asSequence().map(OverrideConfig::id).filterNot(selectedIds::contains).toList() }
    val currentVisibleSelectedOverrideIds by rememberUpdatedState(visibleSelectedOverrideIds)
    val selectedOverrideListState = rememberLazyListState()
    val selectedOverrideReorderState =
        rememberReorderableLazyListState(selectedOverrideListState) { from, to ->
            pendingSelectedOverrideIds =
                reorderVisibleOverrideIds(
                    allIds = pendingSelectedOverrideIds,
                    visibleIds = currentVisibleSelectedOverrideIds,
                    from = from.index,
                    to = to.index,
                )
        }
    // Reset per dialog-open identity only (NOT on every binding change).
    LaunchedEffect(show, profile.uuid) {
        if (show) {
            selectedSection = ProfileSettingsSection.Subscription
            editName = TextFieldValue(profile.name, TextRange(profile.name.length))
            editSource = TextFieldValue(profile.source, TextRange(profile.source.length))
            editAgeSecretKey = TextFieldValue()
            ageSecretKeyEdited = false
            overrideSelectionInitialized = false
            customRoutingSelected = false
            pendingSelectedOverrideIds = emptyList()
        }
    }

    // Seed override selection from the binding exactly once it becomes available.
    LaunchedEffect(show, profile.uuid, binding) {
        if (show && !overrideSelectionInitialized && binding != null) {
            val overrideIds = binding.overrideIds
            customRoutingSelected =
                overrideIds.contains(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID)
            pendingSelectedOverrideIds = overrideIds
            overrideSelectionInitialized = true
        }
    }

    val toggleUserOverrideSelection: (String) -> Unit = { overrideId ->
        overrideSelectionInitialized = true
        pendingSelectedOverrideIds =
            if (overrideId in pendingSelectedOverrideIds) { pendingSelectedOverrideIds - overrideId } else { (pendingSelectedOverrideIds + overrideId).distinct() }
    }
    val saveSettings = {
        if (!isSaving) {
            scope.launch {
                isSaving = true
                runCatching {
                    val trimmedName = editName.text.trim()
                    val trimmedSource = editSource.text.trim()
                    val trimmedAgeSecretKey = editAgeSecretKey.text.trim()
                    val targetSource =
                        if (profile.type == Profile.Type.Url && trimmedSource.isNotEmpty()) {
                            trimmedSource
                        } else {
                            profile.source
                        }
                    val hasMetaChanges =
                        trimmedName != profile.name || targetSource != profile.source || ageSecretKeyEdited
                    if (trimmedName.isNotEmpty() && targetSource.isNotEmpty() && hasMetaChanges) {
                        onSaveProfileMeta(
                            ProfileMetaUpdate(
                                name = trimmedName,
                                source = targetSource,
                                updateAgeSecretKey = ageSecretKeyEdited,
                                ageSecretKey = if (ageSecretKeyEdited) trimmedAgeSecretKey else null,
                            )
                        )
                    }
                    val finalSelectedOverrideIds =
                        buildFinalOverrideIds(
                            selectedOverrideIds = pendingSelectedOverrideIds,
                            customRoutingSelected = customRoutingSelected,
                        )
                    onSaveOverrideSettings(finalSelectedOverrideIds)
                }
                    .onSuccess { onDismiss() }
                    .onFailure { error -> context.toast(error.message ?: FlyTxt.ProfilesPage.SettingsDialog.SaveFailed) }
                isSaving = false
            }
        }
    }

    AppActionBottomSheet(
        show = show,
        modifier = Modifier,
        title = FlyTxt.ProfilesPage.SettingsDialog.Title,
        startAction = {
            AppBottomSheetCloseAction(
                onClick = onDismiss,
                contentDescription = FlyTxt.ProfilesPage.Button.Cancel,
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = !isSaving,
                onClick = saveSettings,
                contentDescription = FlyTxt.ProfilesPage.Button.Confirm,
            )
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().animateContentSize(animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.StandardEasing)).padding(bottom = UiDp.dp16), verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
            Card {
                WindowSpinnerPreference(
                    title = FlyTxt.ProfilesPage.SettingsDialog.SectionType,
                    items =
                        listOf(
                            DropdownItem(title = FlyTxt.ProfilesPage.SettingsDialog.SectionSubscription),
                            DropdownItem(title = FlyTxt.ProfilesPage.SettingsDialog.SectionOverride),
                        ),
                    selectedIndex = sectionOptions.indexOf(selectedSection).coerceAtLeast(0),
                    onSelectedIndexChange = { index -> sectionOptions.getOrNull(index)?.let { selectedSection = it } },
                )
            }
            Crossfade(targetState = selectedSection, animationSpec = tween(AnimationSpecs.DURATION_CROSSFADE), label = "ProfileSettingsSection") { section ->
                when (section) {
                    ProfileSettingsSection.Subscription -> {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
                            TextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = FlyTxt.ProfilesPage.Input.ProfileName,
                                useLabelAsPlaceholder = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (profile.type == Profile.Type.Url) {
                                TextField(
                                    value = editSource,
                                    onValueChange = { editSource = it },
                                    label = FlyTxt.ProfilesPage.SettingsDialog.ChangeLink,
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2,
                                )
                            }
                            // Age secret key
                            val ageKeyContext = LocalContext.current
                            val ageScope = rememberCoroutineScope()
                            var ageKeyVisible by remember { mutableStateOf(false) }
                            var agePublicKey by remember { mutableStateOf("") }
                            // Age public key
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                                Text(
                                    text = FlyTxt.ProfilesPage.AgeKey.PublicKey,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.outline,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                                ) {
                                    TextField(
                                        value = agePublicKey,
                                        onValueChange = { agePublicKey = it },
                                        label = FlyTxt.ProfilesPage.AgeKey.PublicKeyPlaceholder,
                                        useLabelAsPlaceholder = true,
                                        readOnly = true,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    IconButton(
                                        onClick = {
                                            ageScope.launch {
                                                val key = AgeKeyCrypto.agePublicKey(editAgeSecretKey.text)
                                                if (key != null) {
                                                    agePublicKey = key
                                                    val clipboard = ageKeyContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("age_key", key))
                                                    Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GeneratedPublicKey.format(key), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GenerateFailed, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(imageVector = FlyCat.ArrowRight, contentDescription = FlyTxt.ProfilesPage.AgeKey.DerivePublicKey)
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = ageKeyContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("age_key", agePublicKey)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.CopiedPublicKey.format(agePublicKey), Toast.LENGTH_SHORT).show()
                                        },
                                    ) {
                                        Icon(imageVector = FlyCat.Copy, contentDescription = FlyTxt.ProfilesPage.AgeKey.CopyPublicKey)
                                    }
                                }
                            }
                            // Age secret key input
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                                Text(
                                    text = FlyTxt.ProfilesPage.SettingsDialog.AgeSecretKey,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.outline,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                                ) {
                                    TextField(
                                        value = editAgeSecretKey,
                                        onValueChange = { editAgeSecretKey = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        visualTransformation = if (ageKeyVisible) VisualTransformation.None
                                            else PasswordVisualTransformation(),
                                    )
                                    IconButton(
                                        onClick = {
                                            ageScope.launch {
                                                val result = AgeKeyCrypto.genAgeKey()
                                                if (result != null) {
                                                    editAgeSecretKey = TextFieldValue(result.secretKey, TextRange(result.secretKey.length))
                                                    val clipboard = ageKeyContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("age_key", result.secretKey))
                                                    Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GeneratedSecretKey.format(result.secretKey), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GenerateFailed, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(imageVector = FlyCat.Sparkles, contentDescription = FlyTxt.ProfilesPage.AgeKey.GenerateSecretKey)
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = ageKeyContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("age_key", editAgeSecretKey.text)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.CopiedSecretKey.format(editAgeSecretKey.text), Toast.LENGTH_SHORT).show()
                                        },
                                    ) {
                                        Icon(imageVector = FlyCat.Copy, contentDescription = FlyTxt.ProfilesPage.AgeKey.CopySecretKey)
                                    }
                                    IconButton(
                                        onClick = { ageKeyVisible = !ageKeyVisible },
                                    ) {
                                        Icon(imageVector = FlyCat.Eye, contentDescription = if (ageKeyVisible) FlyTxt.ProfilesPage.AgeKey.HideKey else FlyTxt.ProfilesPage.AgeKey.ShowKey)
                                    }
                                }
                            }
                        }
                    }
                    ProfileSettingsSection.Override -> {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
                            Card {
                                Column {
                                    SwitchPreference(
                                        title = FlyTxt.ProfilesPage.SettingsDialog.CustomRouting,
                                        summary = FlyTxt.ProfilesPage.SettingsDialog.CustomRoutingSummary,
                                        checked = customRoutingSelected,
                                        onCheckedChange = {
                                            overrideSelectionInitialized = true
                                            customRoutingSelected = it
                                            pendingSelectedOverrideIds =
                                                if (it) {
                                                    (pendingSelectedOverrideIds + customRoutingId).distinct()
                                                } else { pendingSelectedOverrideIds - customRoutingId }
                                        },
                                    )
                                }
                            }
                            if (overrideRowIds.isNotEmpty()) {
                                Card {
                                    LazyColumn(state = selectedOverrideListState, modifier = Modifier.fillMaxWidth().heightIn(max = componentSizes.profileSettingsListMaxHeight)) {
                                        items(overrideRowIds, key = { id -> "override-$id" }) { id ->
                                            val config = selectableById[id] ?: return@items
                                            val isSelected = id in pendingSelectedOverrideIds
                                            Box(modifier = Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(AnimationSpecs.DURATION_ITEM_FADE_IN), fadeOutSpec = tween(AnimationSpecs.DURATION_INSTANT), placementSpec = tween(AnimationSpecs.DURATION_MEDIUM, easing = AnimationSpecs.StandardEasing))) {
                                                if (isSelected) {
                                                    ReorderableItem(selectedOverrideReorderState, key = "override-$id") { isDragging ->
                                                        BasicComponent(
                                                            title = config.name,
                                                            modifier = Modifier.longPressDraggableHandle().alpha(if (isDragging) 0.9f else 1f),
                                                            endActions = {
                                                                Checkbox(
                                                                    state = ToggleableState.On,
                                                                    onClick = {
                                                                        overrideSelectionInitialized = true
                                                                        pendingSelectedOverrideIds = pendingSelectedOverrideIds - id
                                                                    },
                                                                )
                                                            },
                                                            onClick = {},
                                                        )
                                                    }
                                                } else {
                                                    BasicComponent(
                                                        title = config.name,
                                                        endActions = {
                                                            Checkbox(
                                                                state = ToggleableState.Off,
                                                                onClick = {
                                                                    toggleUserOverrideSelection(id)
                                                                },
                                                            )
                                                        },
                                                        onClick = {
                                                            toggleUserOverrideSelection(id)
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
                }
            }
        }
    }
}

private fun buildFinalOverrideIds(
    selectedOverrideIds: List<String>,
    customRoutingSelected: Boolean,
): List<String> {
    val customRoutingId = OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
    val normalizedIds = selectedOverrideIds.distinct()
    if (!customRoutingSelected) {
        return normalizedIds - customRoutingId
    }
    if (customRoutingId in normalizedIds) {
        return normalizedIds
    }
    return normalizedIds + customRoutingId
}

private fun reorderVisibleOverrideIds(
    allIds: List<String>,
    visibleIds: List<String>,
    from: Int,
    to: Int,
): List<String> {
    if (from !in visibleIds.indices || to !in visibleIds.indices || from == to) return allIds
    val reorderedVisible = visibleIds.toMutableList()
    reorderedVisible.add(to, reorderedVisible.removeAt(from))
    val visibleSet = visibleIds.toSet()
    val replacements = reorderedVisible.iterator()
    return allIds.map { id -> if (id in visibleSet) replacements.next() else id }
}

internal data class ProfileMetaUpdate(
    val name: String,
    val source: String,
    val updateAgeSecretKey: Boolean,
    val ageSecretKey: String?,
)
