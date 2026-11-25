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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.github.lmfirefly.flycat.core.model.profile.LinkOpenMode
import com.github.lmfirefly.flycat.core.model.profile.ProfileLink
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppFormDialog
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import kotlinx.coroutines.flow.drop

@Composable
internal fun LinkSettingsDialog(
    show: MutableState<Boolean>,
    links: List<ProfileLink>,
    linkOpenMode: LinkOpenMode,
    defaultLinkId: String,
    onOpenModeChange: (LinkOpenMode) -> Unit,
    onDefaultLinkChange: (String) -> Unit,
    onAddLink: () -> Unit,
    onDeleteLink: (String) -> Unit,
    onOpenLink: (ProfileLink) -> Unit,
) {
    AppActionBottomSheet(
        show = show.value,
        title = FlyTxt.ProfilesPage.LinkSettings.Title,
        onDismissRequest = { show.value = false },
        enableNestedScroll = true,
        content = {
            LinkSettingsContent(
                links = links,
                linkOpenMode = linkOpenMode,
                defaultLinkId = defaultLinkId,
                onOpenModeChange = onOpenModeChange,
                onDefaultLinkChange = onDefaultLinkChange,
                onAddLink = onAddLink,
                onDeleteLink = onDeleteLink,
                onOpenLink = onOpenLink,
                onClose = { show.value = false },
            )
        },
    )
}

@Composable
internal fun AddLinkDialog(
    show: MutableState<Boolean>,
    linkToEdit: ProfileLink?,
    linkName: String,
    onNameChange: (String) -> Unit,
    linkUrl: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var error by remember { mutableStateOf("") }
    val currentNameState = rememberTextFieldState()
    val currentUrlState = rememberTextFieldState()

    LaunchedEffect(show.value, linkToEdit) {
        if (show.value) {
            if (linkToEdit != null) {
                currentNameState.setTextAndPlaceCursorAtEnd(linkToEdit.name)
                currentUrlState.setTextAndPlaceCursorAtEnd(linkToEdit.url)
            } else {
                currentNameState.setTextAndPlaceCursorAtEnd("")
                currentUrlState.setTextAndPlaceCursorAtEnd("")
            }
            error = ""
        }
    }
    LaunchedEffect(currentNameState, currentUrlState) {
        snapshotFlow { currentNameState.text.toString() to currentUrlState.text.toString() }.drop(1).collect { error = "" }
    }

    AppFormDialog(
        show = show.value,
        title = if (linkToEdit != null) {
            FlyTxt.ProfilesPage.LinkSettings.EditLink
        } else {
            FlyTxt.ProfilesPage.LinkSettings.AddLink
        },
        onDismissRequest = onDismiss,
        onConfirm = {
            error = when {
                currentNameState.text.isBlank() ->
                    FlyTxt.ProfilesPage.LinkSettings.Validation.EnterName
                currentUrlState.text.isBlank() -> FlyTxt.ProfilesPage.LinkSettings.Validation.EnterUrl
                !currentUrlState.text.startsWith("http", ignoreCase = true) ->
                    FlyTxt.ProfilesPage.LinkSettings.Validation.InvalidUrl
                else -> ""
            }
            if (error.isEmpty()) {
                onNameChange(currentNameState.text.toString())
                onUrlChange(currentUrlState.text.toString())
                onConfirm()
            }
        },
        error = error.ifBlank { null },
        cancelText = FlyTxt.ProfilesPage.Button.Cancel,
        confirmText = FlyTxt.ProfilesPage.Button.Confirm,
    ) {
        GlassTextField(
            state = currentNameState,
            label = FlyTxt.ProfilesPage.LinkSettings.Name,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        GlassTextField(
            state = currentUrlState,
            label = FlyTxt.ProfilesPage.LinkSettings.Url,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
