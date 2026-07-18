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

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.language.LanguageScope
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.util.OverrideEditorStore
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch

@Composable
internal fun ProfileShareDialog(
    profile: Profile?,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val profileToShare = profile ?: return
    val context = LocalContext.current

    ShareOptionsDialog(
        show = show,
        profile = profileToShare,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        onShareFile = {
            shareProfileFile(context, profileToShare)
            onDismiss()
        },
        onShareLink = {
            shareProfileLink(context, profileToShare)
            onDismiss()
        },
    )
}

@Composable
internal fun ProfileEditOptionsDialogHost(
    profile: Profile?,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSettingsRequested: (Profile) -> Unit,
) {
    val profileToEdit = profile ?: return
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var openPreviewOnDismiss by remember(profileToEdit.uuid) { mutableStateOf(false) }

    ProfileEditOptionsDialog(
        show = show,
        onOpenConfig = {
            openPreviewOnDismiss = false
            onDismiss()
            val configFile = importedConfigFile(context, profileToEdit)
            scope.launch {
                openProfileConfigPreview(
                    targetFile = configFile,
                    missingMessage =
                        MLang.ProfilesPage.SettingsDialog.ConfigMissing.format(configFile.absolutePath),
                    editable = true,
                    onReadFailed = context::toast,
                ) { content, callback ->
                    OverrideEditorStore.setupConfigPreview(
                        title = profileToEdit.name,
                        content = content,
                        language = LanguageScope.Yaml,
                        callback = callback,
                    )
                    openPreviewOnDismiss = true
                }
            }
        },
        onEditSettings = {
            openPreviewOnDismiss = false
            onDismiss()
            onSettingsRequested(profileToEdit)
        },
        onDismiss = {
            openPreviewOnDismiss = false
            onDismiss()
        },
        onDismissFinished = {
            onDismissFinished()
            if (openPreviewOnDismiss) {
                openPreviewOnDismiss = false
                navigator.push(Route.OverrideConfigPreview)
            }
        },
    )
}

private fun shareProfileFile(context: Context, profile: Profile) {
    val file = importedConfigFile(context, profile)
    if (!file.exists()) {
        context.toast(MLang.ProfilesPage.ShareDialog.ImportedConfigMissing.format(file.absolutePath))
        return
    }

    runCatching {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/x-yaml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(
                Intent.createChooser(shareIntent, MLang.ProfilesPage.ShareDialog.ShareFile)
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
        .onFailure { error -> context.toast(error.message ?: "Share failed") }
}

private fun shareProfileLink(context: Context, profile: Profile) {
    val url = profile.source.takeIf { profile.type == Profile.Type.Url }
    if (url == null) {
        context.toast(MLang.ProfilesPage.ShareDialog.NoLink)
        return
    }

    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    context.startActivity(
        Intent.createChooser(shareIntent, MLang.ProfilesPage.ShareDialog.ShareLink)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    )
}
