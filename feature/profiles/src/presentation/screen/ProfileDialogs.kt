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

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProfileBinding
import com.github.yumelira.yumebox.core.util.AgeKeyCrypto
import com.github.yumelira.yumebox.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.yumelira.yumebox.presentation.component.AgeSecretKeyField
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Scan-eye`
import com.github.yumelira.yumebox.presentation.icon.yume.ArrowRight
import com.github.yumelira.yumebox.presentation.icon.yume.Copy
import com.github.yumelira.yumebox.presentation.icon.yume.Sparkles
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROFILE_SETTINGS_MIN_HEIGHT_FRACTION = 0.5f
private const val PROFILE_SETTINGS_MAX_HEIGHT_FRACTION = 0.7f

@Composable
internal fun ProfileSettingsDialog(
    show: Boolean,
    profile: Profile,
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
    // True once override selection has been seeded from the binding (or from the user editing it).
    // The binding loads ASYNCHRONOUSLY after the dialog is interactive, so a late binding arrival
    // must not clobber a toggle the user already changed.
    var overrideSelectionInitialized by remember { mutableStateOf(false) }

    // Reset per dialog-open identity only (NOT on every binding change), so re-firing when the
    // async binding loads cannot wipe edits already in progress.
    LaunchedEffect(show, profile.uuid, profile.name, profile.source, profile.ageSecretKey) {
        if (show) {
            editName = TextFieldValue(profile.name, TextRange(profile.name.length))
            editSource = TextFieldValue(profile.source, TextRange(profile.source.length))
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
        title = MLang.ProfilesPage.SettingsDialog.Title,
        startAction = {
            AppBottomSheetCloseAction(
                onClick = onDismiss,
                contentDescription = MLang.ProfilesPage.Button.Cancel,
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                onClick = saveSettings,
                contentDescription = MLang.ProfilesPage.Button.Confirm,
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
                    label = MLang.ProfilesPage.Input.ProfileName,
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (profile.type == Profile.Type.Url) {
                    TextField(
                        value = editSource,
                        onValueChange = { editSource = it },
                        label = MLang.ProfilesPage.SettingsDialog.ChangeLink,
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                }
                // Age secret key with generate, copy, and visibility toggle
                val ageKeyContext = LocalContext.current
                val scope = rememberCoroutineScope()
                var ageKeyVisible by remember { mutableStateOf(false) }
                var agePublicKey by remember { mutableStateOf("") }
                // Age public key (read-only, derived from private key)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    Text(
                        text = "age 公钥",
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
                            label = "age1...",
                            useLabelAsPlaceholder = true,
                            readOnly = true,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val keys = AgeKeyCrypto.toPublicKeys(
                                        listOf(editAgeSecretKey.text)
                                    )
                                    if (keys != null && keys.isNotEmpty()) {
                                        agePublicKey = keys.first()
                                        Toast.makeText(ageKeyContext, "已生成 age 公钥", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ageKeyContext, "生成失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Yume.ArrowRight,
                                contentDescription = "Derive public key",
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = ageKeyContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("age_key", agePublicKey)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(ageKeyContext, "已复制 age 公钥", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(
                                imageVector = Yume.Copy,
                                contentDescription = "Copy public key",
                            )
                        }
                    }
                }
                // Age secret key input
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    Text(
                        text = MLang.ProfilesPage.SettingsDialog.AgeSecretKey,
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
                        // Generate key pair button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val result = AgeKeyCrypto.genX25519KeyPair()
                                    if (result != null) {
                                        editAgeSecretKey = TextFieldValue(result.first, TextRange(result.first.length))
                                        Toast.makeText(ageKeyContext, "已生成 age 私钥", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ageKeyContext, "生成失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Yume.Sparkles,
                                contentDescription = "Generate secret key",
                            )
                        }
                        // Copy button
                        IconButton(
                            onClick = {
                                val clipboard = ageKeyContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("age_key", editAgeSecretKey.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(ageKeyContext, "已复制 age 私钥", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(
                                imageVector = Yume.Copy,
                                contentDescription = "Copy secret key",
                            )
                        }
                        // Visibility toggle button
                        IconButton(
                            onClick = { ageKeyVisible = !ageKeyVisible },
                        ) {
                            Icon(
                                imageVector = Yume.`Scan-eye`,
                                contentDescription = if (ageKeyVisible) "Hide key" else "Show key",
                            )
                        }
                    }
                }
                Card {
                    Column {
                        SwitchPreference(
                            title = MLang.ProfilesPage.SettingsDialog.CustomRouting,
                            summary = MLang.ProfilesPage.SettingsDialog.CustomRoutingSummary,
                            checked = customRoutingSelected,
                            onCheckedChange = {
                                overrideSelectionInitialized = true
                                customRoutingSelected = it
                            },
                        )
                    }
                }

                if (userConfigs.isNotEmpty()) {
                    Card {
                        LazyColumn(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(max = componentSizes.profileSettingsListMaxHeight)
                        ) {
                            itemsIndexed(userConfigs, key = { _, config -> config.id }) {
                                index,
                                config ->
                                val isSelected = config.id in pendingSelectedOverrideIds
                                BasicComponent(
                                    title = config.name,
                                    summary =
                                        config.description?.takeIf { it.isNotBlank() }
                                            ?: MLang.ProfilesPage.SettingsDialog.NoDescription,
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
                                if (index < userConfigs.lastIndex) {
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
