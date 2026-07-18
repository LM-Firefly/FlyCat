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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.theme.UiDp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal suspend fun openProfileConfigPreview(
    targetFile: File,
    missingMessage: String,
    editable: Boolean,
    onReadFailed: (String) -> Unit,
    onPreviewPrepared: (String, (suspend (String) -> Unit)?) -> Unit,
) {
    if (!targetFile.exists()) {
        onReadFailed(missingMessage)
        return
    }

    val configContent =
        runCatching { withContext(Dispatchers.IO) { targetFile.readText() } }
            .getOrElse {
                onReadFailed(it.message ?: "Failed to read profile")
                return
            }

    val saveCallback: (suspend (String) -> Unit)? =
        if (editable) {
            { updatedContent: String ->
                runCatching { withContext(Dispatchers.IO) { targetFile.writeText(updatedContent) } }
                    .getOrElse {
                        throw IllegalStateException(
                            it.message ?: FlyTxt.ProfilesPage.SettingsDialog.SaveFailed,
                            it,
                        )
                    }
            }
        } else {
            null
        }

    onPreviewPrepared(configContent, saveCallback)
}

@Composable
internal fun ProfileEditOptionsDialog(
    show: Boolean,
    onOpenConfig: () -> Unit,
    onEditSettings: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    AppDialog(
        show = show,
        title = FlyTxt.ProfilesPage.SettingsDialog.EditProfile,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenConfig) {
                Text(FlyTxt.ProfilesPage.SettingsDialog.OpenConfig)
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onEditSettings,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = FlyTxt.ProfilesPage.SettingsDialog.EditSettings,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
