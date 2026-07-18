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

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.feature.override.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetConfirmAction
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.launch
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
        insideMargin = DpSize(UiDp.dp12, UiDp.dp16),
        enableNestedScroll = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = UiDp.dp16), verticalArrangement = Arrangement.spacedBy(UiDp.dp16)) {
            when (val state = ui) {
                ApplyUi.Loading -> { Spacer(modifier = Modifier.height(UiDp.dp24)) }

                is ApplyUi.Ready -> {
                    if (state.profiles.isEmpty()) { Text(text = FlyTxt.Override.ApplySheet.Empty, color = colorScheme.onSurfaceVariantSummary) } else {
                        Card(applyHorizontalPadding = false) {
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
