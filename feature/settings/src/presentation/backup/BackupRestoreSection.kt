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

package com.github.yumelira.yumebox.feature.settings.presentation.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.tencent.mmkv.MMKV
import dev.oom_wg.purejoy.mlang.MLang
import kotlin.system.exitProcess
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BackupRestoreSection() {
    val viewModel = koinViewModel<BackupRestoreViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }

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
                is BackupRestoreEvent.RestoreSuccess -> showRestartDialog = true
            }
        }
    }

    Title(MLang.MetaFeature.Section.BackupRestore)
    Card {
        ArrowPreference(
            title = MLang.MetaFeature.Backup.BackupTitle,
            summary = MLang.MetaFeature.Backup.BackupSummary,
            enabled = !uiState.isBusy,
            onClick = {
                runCatching { exportLauncher.launch(viewModel.defaultBackupFileName()) }
                    .onFailure { context.toast(MLang.MetaFeature.Backup.BackupFailed.format("")) }
            },
        )
        ArrowPreference(
            title = MLang.MetaFeature.Backup.RestoreTitle,
            summary = MLang.MetaFeature.Backup.RestoreSummary,
            enabled = !uiState.isBusy,
            onClick = {
                runCatching {
                        restoreLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*")
                        )
                    }
                    .onFailure { context.toast(MLang.MetaFeature.Backup.RestoreFailed.format("")) }
            },
        )
    }

    BackupConfirmDialog(
        show = restoreUri != null,
        title = MLang.MetaFeature.Backup.RestoreConfirmTitle,
        message = MLang.MetaFeature.Backup.RestoreConfirmMessage,
        confirmText = MLang.MetaFeature.Backup.RestoreTitle,
        onDismiss = { restoreUri = null },
        onConfirm = {
            val uri = restoreUri ?: return@BackupConfirmDialog
            restoreUri = null
            viewModel.restoreBackup(uri)
        },
    )

    BackupConfirmDialog(
        show = showRestartDialog,
        title = MLang.MetaFeature.Backup.RestoreSuccessTitle,
        message = MLang.MetaFeature.Backup.RestoreSuccess,
        confirmText = MLang.MetaFeature.Backup.RestartNow,
        onDismiss = { showRestartDialog = false },
        onConfirm = {
            showRestartDialog = false
            restartApplication(context)
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
                Text(MLang.Component.Button.Cancel)
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

private fun restartApplication(context: android.content.Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    if (launchIntent == null) {
        context.toast(MLang.MetaFeature.Backup.RestartLaunchNotFound)
        return
    }
    context.startActivity(launchIntent)
    (context as? Activity)?.finishAffinity()
    runCatching { MMKV.defaultMMKV().sync() }
    exitProcess(0)
}
