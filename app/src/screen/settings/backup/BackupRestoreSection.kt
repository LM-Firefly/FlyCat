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

package com.github.yumelira.yumebox.screen.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BackupRestoreSection() {
    val viewModel = koinViewModel<BackupRestoreViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            uri?.let(viewModel::exportBackup)
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            restoreUri = uri
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupRestoreEvent.Message -> context.toast(event.text)
            }
        }
    }

    Title(MLang.Feature.BackupRestore.Section)
    Card {
        ArrowPreference(
            title = MLang.Feature.BackupRestore.ExportTitle,
            summary = MLang.Feature.BackupRestore.ExportSummary,
            enabled = !uiState.isBusy,
            onClick = {
                runCatching { exportLauncher.launch(viewModel.defaultBackupFileName()) }
                    .onFailure { context.toast(MLang.Feature.BackupRestore.Error.OpenOutputFailed) }
            },
        )
        ArrowPreference(
            title = MLang.Feature.BackupRestore.RestoreTitle,
            summary = MLang.Feature.BackupRestore.RestoreSummary,
            enabled = !uiState.isBusy,
            onClick = {
                runCatching {
                        restoreLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*")
                        )
                    }
                    .onFailure { context.toast(MLang.Feature.BackupRestore.Error.OpenInputFailed) }
            },
        )
    }

    BackupConfirmDialog(
        show = restoreUri != null,
        title = MLang.Feature.BackupRestore.RestoreDialog.Title,
        message = MLang.Feature.BackupRestore.RestoreDialog.Message,
        confirmText = MLang.Feature.BackupRestore.RestoreTitle,
        onDismiss = { restoreUri = null },
        onConfirm = {
            val uri = restoreUri ?: return@BackupConfirmDialog
            restoreUri = null
            viewModel.restoreBackup(uri)
        },
    )
}

@Composable
private fun BackupConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(show = show, title = title, summary = message, onDismissRequest = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
            Button(modifier = Modifier.weight(1f), onClick = onDismiss) {
                Text(MLang.Feature.BackupRestore.Cancel)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = confirmText, color = MiuixTheme.colorScheme.onPrimary)
            }
        }
    }
}
