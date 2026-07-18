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

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.feature.override.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
import kotlinx.coroutines.launch
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private sealed interface ApplyUi {
    data object Loading : ApplyUi

    data class Ready(val profiles: List<ProfileSnapshot>, val selected: Set<String>) : ApplyUi
}

internal data class ProfileSnapshot(val uuid: String, val name: String)

@Composable
internal fun OverrideApplyToProfilesSheet(
    target: OverrideConfig?,
    viewModel: OverrideConfigViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val componentSizes = AppTheme.sizes

    var ui by remember { mutableStateOf<ApplyUi>(ApplyUi.Loading) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(target?.id) {
        val config = target ?: return@LaunchedEffect
        ui = ApplyUi.Loading
        isSaving = false
        viewModel.loadApplySnapshot(config.id).onSuccess { snapshot ->
            ui = ApplyUi.Ready(
                profiles = snapshot.profiles.map { ProfileSnapshot(it.uuid.toString(), it.name) },
                selected = snapshot.selectedProfileIds,
            )
        }
        .onFailure { error ->
            context.toast(
                FlyTxt.Override.ApplySheet.Failed.format(
                    error.message ?: "Unknown error"
                )
            )
            ui = ApplyUi.Ready(emptyList(), emptySet())
        }
    }

    val saveSelection = {
        val config = target
        val selection = (ui as? ApplyUi.Ready)?.selected
        if (!isSaving && config != null && selection != null) {
            scope.launch {
                isSaving = true
                viewModel.applyOverrideToProfiles(config.id, selection).onSuccess {
                    context.toast(FlyTxt.Override.ApplySheet.Success)
                    onDismiss()
                }
                .onFailure { error ->
                    context.toast(
                        FlyTxt.Override.ApplySheet.Failed.format(
                            error.message ?: "Unknown error"
                        )
                    )
                }
                isSaving = false
            }
        }
    }

    AppActionBottomSheet(
        show = target != null,
        title = FlyTxt.Override.ApplySheet.Title,
        startAction = {
            AppBottomSheetCloseAction(
                onClick = onDismiss,
                contentDescription = FlyTxt.Override.ApplySheet.Button.Cancel,
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = !isSaving && ui is ApplyUi.Ready,
                onClick = saveSelection,
                contentDescription = FlyTxt.Override.ApplySheet.Button.Confirm,
            )
        },
        onDismissRequest = onDismiss,
        enableNestedScroll = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = UiDp.dp16), verticalArrangement = Arrangement.spacedBy(UiDp.dp16)) {
            when (val state = ui) {
                ApplyUi.Loading -> { Spacer(modifier = Modifier.height(UiDp.dp24)) }

                is ApplyUi.Ready -> {
                    if (state.profiles.isEmpty()) { Text(text = FlyTxt.Override.ApplySheet.Empty, color = colorScheme.onSurfaceVariantSummary) } else {
                        Card {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = componentSizes.profileSettingsListMaxHeight)) {
                                items(items = state.profiles, key = { it.uuid }) { profile ->
                                    val isSelected = profile.uuid in state.selected
                                    SwitchPreference(
                                        title = profile.name,
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            val current = ui as? ApplyUi.Ready ?: return@SwitchPreference
                                            ui = current.copy(
                                                selected = if (checked) {
                                                    current.selected + profile.uuid
                                                } else {
                                                    current.selected - profile.uuid
                                                },
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
    }
}
