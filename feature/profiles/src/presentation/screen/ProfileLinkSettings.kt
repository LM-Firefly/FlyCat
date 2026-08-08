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

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.core.model.ProfileLink
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppFormDialog
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.TextField

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
    var currentName by remember {
        mutableStateOf(TextFieldValue(linkName, TextRange(linkName.length)))
    }
    var currentUrl by remember {
        mutableStateOf(TextFieldValue(linkUrl, TextRange(linkUrl.length)))
    }

    LaunchedEffect(show.value, linkToEdit) {
        if (show.value) {
            if (linkToEdit != null) {
                currentName = TextFieldValue(linkToEdit.name, TextRange(linkToEdit.name.length))
                currentUrl = TextFieldValue(linkToEdit.url, TextRange(linkToEdit.url.length))
            } else {
                currentName = TextFieldValue()
                currentUrl = TextFieldValue()
            }
            error = ""
        }
    }

    AppFormDialog(
        show = show.value,
        title =
            if (linkToEdit != null) {
                FlyTxt.ProfilesPage.LinkSettings.EditLink
            } else {
                FlyTxt.ProfilesPage.LinkSettings.AddLink
            },
        onDismissRequest = onDismiss,
        onConfirm = {
            error =
                when {
                    currentName.text.isBlank() ->
                        FlyTxt.ProfilesPage.LinkSettings.Validation.EnterName
                    currentUrl.text.isBlank() -> FlyTxt.ProfilesPage.LinkSettings.Validation.EnterUrl
                    !currentUrl.text.startsWith("http", ignoreCase = true) ->
                        FlyTxt.ProfilesPage.LinkSettings.Validation.InvalidUrl
                    else -> ""
                }
            if (error.isEmpty()) {
                onNameChange(currentName.text)
                onUrlChange(currentUrl.text)
                onConfirm()
            }
        },
        error = error.ifBlank { null },
        cancelText = FlyTxt.ProfilesPage.Button.Cancel,
        confirmText = FlyTxt.ProfilesPage.Button.Confirm,
    ) {
        TextField(
            value = currentName,
            onValueChange = {
                currentName = it
                error = ""
            },
            label = FlyTxt.ProfilesPage.LinkSettings.Name,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = currentUrl,
            onValueChange = {
                currentUrl = it
                error = ""
            },
            label = FlyTxt.ProfilesPage.LinkSettings.Url,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
