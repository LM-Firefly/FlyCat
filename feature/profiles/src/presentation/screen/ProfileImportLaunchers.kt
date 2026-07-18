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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.util.isYamlConfigFileName
import com.github.yumelira.yumebox.presentation.util.readDisplayName
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch

internal data class ProfileImportLaunchers(
    val pickFile: () -> Unit,
    val selectQrImage: () -> Unit,
)

@Composable
internal fun rememberProfileImportLaunchers(
    context: Context,
    onFileSelected: (Uri, String) -> Unit,
    onUnsupportedFile: () -> Unit,
    onQrDecoded: (String) -> Unit,
): ProfileImportLaunchers {
    val scope = rememberCoroutineScope()
    val file =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val fileName = readDisplayName(context, uri, MLang.ProfilesPage.Message.UnknownFile)
            if (!isYamlConfigFileName(fileName)) {
                onUnsupportedFile()
                return@rememberLauncherForActivityResult
            }
            onFileSelected(uri, fileName)
        }
    val qrImage =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    val value = readQrFromImage(context, uri)
                    if (value == null) {
                        context.toast(MLang.ProfilesPage.QrScanner.RecognizeFailed)
                    } else {
                        onQrDecoded(value)
                        context.toast(MLang.ProfilesPage.QrScanner.RecognizeSuccess)
                    }
                } catch (error: Exception) {
                    context.toast(MLang.ProfilesPage.QrScanner.RecognizeError.format(error.message ?: ""))
                }
            }
        }
    return ProfileImportLaunchers(
        pickFile = { file.launch("*/*") },
        selectQrImage = { qrImage.launch("image/*") },
    )
}
