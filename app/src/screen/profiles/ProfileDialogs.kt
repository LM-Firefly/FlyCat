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

package com.github.yumelira.yumebox.screen.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.ProfileBinding
import com.github.yumelira.yumebox.presentation.component.AgeSecretKeyField
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.runtime.api.Profile
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROFILE_SETTINGS_MIN_HEIGHT_FRACTION = 0.5f
private const val PROFILE_SETTINGS_MAX_HEIGHT_FRACTION = 0.7f

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
    onSaveOverrideSettings: (List<String>) -> Unit,
) {
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity
    val componentSizes = AppTheme.sizes
    val selectableConfigs = remember(builtInConfigs, userConfigs) { builtInConfigs + userConfigs }

    var editName by remember {
        mutableStateOf(TextFieldValue(profile.name, TextRange(profile.name.length)))
    }
    var editSource by remember { mutableStateOf(TextFieldValue()) }
    var editAgeSecretKey by remember { mutableStateOf(TextFieldValue()) }
    var ageSecretKeyEdited by remember { mutableStateOf(false) }
    var customRoutingSelected by remember { mutableStateOf(false) }
    var pendingSelectedOverrideIds by remember { mutableStateOf(emptyList<String>()) }
    // True once override selection has been seeded from the binding (or from the user editing it).
    // The binding loads ASYNCHRONOUSLY after the dialog is interactive, so a late binding arrival
    // must not clobber a toggle the user already changed.
    var overrideSelectionInitialized by remember { mutableStateOf(false) }

    // Reset per dialog-open identity only (NOT on every binding change), so re-firing when the
    // async binding loads cannot wipe edits already in progress.
    LaunchedEffect(show, profile.uuid, profile.name, profile.source, profile.hasAgeSecretKey) {
        if (show) {
            editName = TextFieldValue(profile.name, TextRange(profile.name.length))
            editSource = TextFieldValue()
            editAgeSecretKey = TextFieldValue()
            ageSecretKeyEdited = false
            overrideSelectionInitialized = false
            customRoutingSelected = false
            pendingSelectedOverrideIds = emptyList()
        }
    }

    // Seed override selection from the binding exactly once it becomes available, and only while
    // the user has not yet edited it. Preserves correct initial state when the binding loads before
    // any user interaction; ignores the late load otherwise.
    LaunchedEffect(show, profile.uuid, binding) {
        if (show && !overrideSelectionInitialized && binding != null) {
            val overrideIds = binding.overrideIds
            customRoutingSelected =
                overrideIds.contains(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID)
            pendingSelectedOverrideIds = overrideIds
            overrideSelectionInitialized = true
        }
    }

    val toggleUserOverrideSelection: (String, Boolean) -> Unit = { overrideId, isSelected ->
        overrideSelectionInitialized = true
        pendingSelectedOverrideIds =
            toggleOverrideIdSelection(pendingSelectedOverrideIds, overrideId, isSelected)
    }
    val saveSettings = {
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
        onDismiss()
    }

    AppActionBottomSheet(
        show = show,
        modifier = Modifier,
        title = YumeTxt.ProfilesPage.SettingsDialog.Title,
        startAction = {
            AppBottomSheetCloseAction(
                onClick = onDismiss,
                contentDescription = YumeTxt.ProfilesPage.Button.Cancel,
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                onClick = saveSettings,
                contentDescription = YumeTxt.ProfilesPage.Button.Confirm,
            )
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = true,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minimumSheetHeight = maxHeight * PROFILE_SETTINGS_MIN_HEIGHT_FRACTION
            val maximumSheetHeight = maxHeight * PROFILE_SETTINGS_MAX_HEIGHT_FRACTION

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = minimumSheetHeight, max = maximumSheetHeight)
                        .padding(bottom = spacing.space16),
                verticalArrangement = Arrangement.spacedBy(spacing.space16),
            ) {
                TextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = YumeTxt.ProfilesPage.Input.ProfileName,
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (profile.type == Profile.Type.Url) {
                    TextField(
                        value = editSource,
                        onValueChange = { editSource = it },
                        label = YumeTxt.ProfilesPage.SettingsDialog.ChangeLink,
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                }

                AgeSecretKeyField(
                    value = editAgeSecretKey,
                    onValueChange = {
                        editAgeSecretKey = it
                        ageSecretKeyEdited = true
                    },
                    label = YumeTxt.ProfilesPage.SettingsDialog.AgeSecretKey,
                    modifier = Modifier.fillMaxWidth(),
                )

                Card {
                    Column {
                        SwitchPreference(
                            title = YumeTxt.ProfilesPage.SettingsDialog.CustomRouting,
                            summary = YumeTxt.ProfilesPage.SettingsDialog.CustomRoutingSummary,
                            checked = customRoutingSelected,
                            onCheckedChange = {
                                overrideSelectionInitialized = true
                                customRoutingSelected = it
                            },
                        )
                    }
                }

                if (selectableConfigs.isNotEmpty()) {
                    Card {
                        LazyColumn(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(max = componentSizes.profileSettingsListMaxHeight)
                        ) {
                            itemsIndexed(selectableConfigs, key = { _, config -> config.id }) {
                                index,
                                config ->
                                val isSelected = config.id in pendingSelectedOverrideIds
                                BasicComponent(
                                    title = config.name,
                                    endActions = {
                                        Checkbox(
                                            state = ToggleableState(isSelected),
                                            onClick = {
                                                toggleUserOverrideSelection(config.id, isSelected)
                                            },
                                        )
                                    },
                                    onClick = {
                                        toggleUserOverrideSelection(config.id, isSelected)
                                    },
                                )
                                if (index < selectableConfigs.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = spacing.space16),
                                        thickness = componentSizes.thinDividerThickness,
                                        color =
                                            MiuixTheme.colorScheme.outline.copy(
                                                alpha = opacity.outline
                                            ),
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

private fun toggleOverrideIdSelection(
    selectedOverrideIds: List<String>,
    overrideId: String,
    isSelected: Boolean,
): List<String> =
    if (isSelected) {
        selectedOverrideIds - overrideId
    } else {
        (selectedOverrideIds + overrideId).distinct()
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

internal data class ProfileMetaUpdate(
    val name: String,
    val source: String,
    val updateAgeSecretKey: Boolean,
    val ageSecretKey: String?,
)
