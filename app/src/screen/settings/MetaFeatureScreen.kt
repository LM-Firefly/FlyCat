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

package com.github.yumelira.yumebox.screen.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.core.model.GeoFileType
import com.github.yumelira.yumebox.core.model.geoXItems
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.feature.substore.util.SubStoreDownloadClient
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.AppConfirmDialog
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.AppFormDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang
import kotlin.system.exitProcess
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import java.io.File

@Composable
fun MetaFeatureScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: MetaFeatureViewModel = koinViewModel()

    val showGeoXDownloadSheet = remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showWebDavRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreSuccessDialog by remember { mutableStateOf(false) }
    var showWebDavConfigDialog by remember { mutableStateOf(false) }
    var showLoadingDialog by remember { mutableStateOf(false) }
    var loadingTitle by remember { mutableStateOf("") }
    var loadingMessage by remember { mutableStateOf("") }
    var restoreSuccessMessage by remember { mutableStateOf("") }
    var webDavError by remember { mutableStateOf<String?>(null) }
    var webDavUrlField by remember { mutableStateOf(TextFieldValue("")) }
    var webDavDirField by remember { mutableStateOf(TextFieldValue("")) }
    var webDavAccountField by remember { mutableStateOf(TextFieldValue("")) }
    var webDavPasswordField by remember { mutableStateOf(TextFieldValue("")) }

    val ageKeyHybrid = remember { mutableStateOf(false) }
    val ageKeyDialogVisible = remember { mutableStateOf(false) }

    fun openWebDavDialog() {
        val config = viewModel.getWebDavConfig()
        webDavUrlField = TextFieldValue(config.url, TextRange(config.url.length))
        webDavDirField = TextFieldValue(config.directory, TextRange(config.directory.length))
        webDavAccountField = TextFieldValue(config.account, TextRange(config.account.length))
        webDavPasswordField = TextFieldValue(config.password, TextRange(config.password.length))
        webDavError = null
        showWebDavConfigDialog = true
    }

    fun currentDraftConfig(): MetaWebDavConfig {
        return MetaWebDavConfig(
            url = webDavUrlField.text.trim(),
            directory = webDavDirField.text.trim(),
            account = webDavAccountField.text.trim(),
            password = webDavPasswordField.text,
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { targetUri ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showLoadingDialog = true
            loadingTitle = MLang.MetaFeature.Backup.BackupDialogTitle
            loadingMessage = MLang.MetaFeature.Backup.BackupDialogMessage
            try {
                viewModel.backupToUri(context, targetUri)
                    .onSuccess {
                        context.toast(MLang.MetaFeature.Backup.BackupSuccess)
                    }
                    .onFailure { error ->
                        context.toast(
                            MLang.MetaFeature.Backup.BackupFailed.format(
                                error.localizedMessage ?: MLang.Util.Error.UnknownError,
                            ),
                        )
                    }
            } finally {
                showLoadingDialog = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { sourceUri ->
        if (sourceUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            showLoadingDialog = true
            loadingTitle = MLang.MetaFeature.Backup.RestoreDialogTitle
            loadingMessage = MLang.MetaFeature.Backup.RestoreDialogMessage
            try {
                viewModel.restoreFromUri(context, sourceUri)
                    .onSuccess {
                        restoreSuccessMessage = MLang.MetaFeature.Backup.RestoreSuccess
                        showRestoreSuccessDialog = true
                    }
                    .onFailure { error ->
                        context.toast(
                            MLang.MetaFeature.Backup.RestoreFailed.format(
                                error.localizedMessage ?: MLang.Util.Error.UnknownError,
                            ),
                        )
                    }
            } finally {
                showLoadingDialog = false
            }
        }
    }

    Scaffold(
        topBar = { TopBar(title = MLang.MetaFeature.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.MetaFeature.Section.ConnectionAndTraffic)
                Card {
                    ArrowPreference(
                        title = MLang.Connection.Title,
                        summary = MLang.Connection.Summary,
                        onClick = { navigator.push(Route.Connection) },
                    )
                    ArrowPreference(
                        title = MLang.TrafficStatistics.Title,
                        summary = MLang.TrafficStatistics.EntrySummary,
                        onClick = { navigator.push(Route.TrafficStatistics) },
                    )
                }
            }
            item {
                Title(MLang.MetaFeature.Section.Routing)
                Card {
                    ArrowPreference(
                        title = MLang.MetaFeature.CustomRouting.Title,
                        summary = MLang.MetaFeature.CustomRouting.Summary,
                        onClick = { navigator.push(Route.CustomRouting) },
                    )
                    ArrowPreference(
                        title = MLang.MetaFeature.GeoX.OnlineUpdateTitle,
                        summary = MLang.MetaFeature.GeoX.OnlineUpdateSummary,
                        onClick = { showGeoXDownloadSheet.value = true },
                    )
                }
            }
            item {
                Title(MLang.MetaFeature.Section.BackupRestore)
                Card {
                    ArrowPreference(
                        title = MLang.MetaFeature.Backup.BackupTitle,
                        summary = MLang.MetaFeature.Backup.BackupSummary,
                        onClick = {
                            backupLauncher.launch(viewModel.defaultBackupFileName())
                        },
                    )
                    ArrowPreference(
                        title = MLang.MetaFeature.Backup.RestoreTitle,
                        summary = MLang.MetaFeature.Backup.RestoreSummary,
                        onClick = {
                            showRestoreConfirmDialog = true
                        },
                    )
                    ArrowPreference(
                        title = "WebDAV 配置",
                        summary = "设置 WebDAV 地址、子目录、账号和密码",
                        onClick = { openWebDavDialog() },
                    )
                    ArrowPreference(
                        title = "WebDAV 连接测试",
                        summary = "验证 WebDAV 配置可用性",
                        onClick = {
                            val config = viewModel.getWebDavConfig()
                            if (!config.isValid()) {
                                context.toast("请先完成 WebDAV 配置")
                                return@ArrowPreference
                            }
                            scope.launch {
                                showLoadingDialog = true
                                loadingTitle = "WebDAV 测试"
                                loadingMessage = "正在连接 WebDAV，请稍候"
                                try {
                                    viewModel.testWebDavConfig(config)
                                        .onSuccess {
                                            context.toast("WebDAV 可用")
                                        }
                                        .onFailure { error ->
                                            context.toast("WebDAV 测试失败：${error.localizedMessage ?: MLang.Util.Error.UnknownError}")
                                        }
                                } finally {
                                    showLoadingDialog = false
                                }
                            }
                        },
                    )
                    ArrowPreference(
                        title = "WebDAV 云端备份",
                        summary = "打包当前数据并上传到 WebDAV",
                        onClick = {
                            scope.launch {
                                showLoadingDialog = true
                                loadingTitle = "WebDAV 备份"
                                loadingMessage = "正在创建备份并上传，请稍候"
                                try {
                                    viewModel.backupToWebDav(context)
                                        .onSuccess { remoteName ->
                                            context.toast("WebDAV 备份完成：$remoteName")
                                        }
                                        .onFailure { error ->
                                            context.toast("WebDAV 备份失败：${error.localizedMessage ?: MLang.Util.Error.UnknownError}")
                                        }
                                } finally {
                                    showLoadingDialog = false
                                }
                            }
                        },
                    )
                    ArrowPreference(
                        title = "WebDAV 云端恢复（最新）",
                        summary = "下载并恢复 WebDAV 上最新备份",
                        onClick = {
                            showWebDavRestoreConfirmDialog = true
                        },
                    )
                }
            }
            item {
                Title(MLang.MetaFeature.AgeKey.Section)
                Card {
                    ArrowPreference(
                        title = MLang.MetaFeature.AgeKey.X25519Title,
                        onClick = {
                            ageKeyHybrid.value = false
                            ageKeyDialogVisible.value = true
                        },
                    )
                    ArrowPreference(
                        title = MLang.MetaFeature.AgeKey.HybridTitle,
                        onClick = {
                            ageKeyHybrid.value = true
                            ageKeyDialogVisible.value = true
                        },
                    )
                }
            }
        }

        GeoXDownloadDialog(
            show = showGeoXDownloadSheet,
            context = context,
            scope = scope,
            viewModel = viewModel,
        )
        AppConfirmDialog(
            show = showRestoreConfirmDialog,
            title = MLang.MetaFeature.Backup.RestoreConfirmTitle,
            message = MLang.MetaFeature.Backup.RestoreConfirmMessage,
            onDismissRequest = { showRestoreConfirmDialog = false },
            onConfirm = {
                showRestoreConfirmDialog = false
                restoreLauncher.launch(arrayOf("*/*"))
            },
        )
        AppConfirmDialog(
            show = showWebDavRestoreConfirmDialog,
            title = "确认从 WebDAV 恢复",
            message = "将覆盖当前用户数据，建议先做本地或云端备份。",
            onDismissRequest = { showWebDavRestoreConfirmDialog = false },
            onConfirm = {
                showWebDavRestoreConfirmDialog = false
                scope.launch {
                    showLoadingDialog = true
                    loadingTitle = "WebDAV 恢复"
                    loadingMessage = "正在下载并恢复最新备份，请稍候"
                    try {
                        viewModel.restoreLatestFromWebDav(context)
                            .onSuccess { remoteName ->
                                restoreSuccessMessage = MLang.MetaFeature.Backup.WebDavRestoreSuccess.format(remoteName)
                                showRestoreSuccessDialog = true
                            }
                            .onFailure { error ->
                                context.toast("WebDAV 恢复失败：${error.localizedMessage ?: MLang.Util.Error.UnknownError}")
                            }
                    } finally {
                        showLoadingDialog = false
                    }
                }
            },
        )
        AppFormDialog(
            show = showWebDavConfigDialog,
            title = "WebDAV 配置",
            summary = "URL 示例：https://dav.jianguoyun.com/dav/",
            onDismissRequest = { showWebDavConfigDialog = false },
            onConfirm = {
                val config = currentDraftConfig()
                webDavError = when {
                    config.url.isBlank() -> "请填写 WebDAV URL"
                    config.account.isBlank() -> "请填写 WebDAV 账号"
                    config.password.isBlank() -> "请填写 WebDAV 密码"
                    else -> null
                }
                if (webDavError == null) {
                    viewModel.updateWebDavConfig(config)
                    showWebDavConfigDialog = false
                    context.toast("WebDAV 配置已保存")
                }
            },
            error = webDavError,
        ) {
            TextField(
                value = webDavUrlField,
                onValueChange = {
                    webDavUrlField = it
                    webDavError = null
                },
                label = "WebDAV URL",
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = webDavDirField,
                onValueChange = {
                    webDavDirField = it
                    webDavError = null
                },
                label = "子目录（可选）",
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = webDavAccountField,
                onValueChange = {
                    webDavAccountField = it
                    webDavError = null
                },
                label = "账号",
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = webDavPasswordField,
                onValueChange = {
                    webDavPasswordField = it
                    webDavError = null
                },
                label = "密码",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppConfirmDialog(
            show = showRestoreSuccessDialog,
            title = MLang.MetaFeature.Backup.RestoreSuccessTitle,
            message = MLang.MetaFeature.Backup.RestoreSuccessMessage.format(restoreSuccessMessage),
            onDismissRequest = { showRestoreSuccessDialog = false },
            onConfirm = {
                showRestoreSuccessDialog = false
                restartApplication(context)
            },
            confirmText = MLang.MetaFeature.Backup.RestartNow,
        )
        AppDialog(
            show = showLoadingDialog,
            title = loadingTitle,
            onDismissRequest = {},
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = loadingMessage)
            }
        }

        AgeKeyGeneratorDialog(
            show = ageKeyDialogVisible.value,
            hybrid = ageKeyHybrid.value,
            onDismiss = { ageKeyDialogVisible.value = false },
            onDismissFinished = {},
        )
    }
}

@Composable
private fun GeoXDownloadDialog(
    show: MutableState<Boolean>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: MetaFeatureViewModel,
) {
    val spacing = AppTheme.spacing
    val selectedItems = remember { mutableStateMapOf<GeoFileType, Boolean>() }
    val canConfirm = selectedItems.values.any { it }

    AppDialog(
        show = show.value,
        title = MLang.MetaFeature.Download.DialogTitle,
        onDismissRequest = { show.value = false },
    ) {
        Column {
            geoXItems.forEach { item ->
                BasicComponent(
                    title = item.title,
                    endActions = {
                        Checkbox(
                            state = ToggleableState(selectedItems[item.type] ?: false),
                            onClick = {
                                selectedItems[item.type] = !(selectedItems[item.type] ?: false)
                            },
                        )
                    },
                    onClick = {
                        selectedItems[item.type] = !(selectedItems[item.type] ?: false)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space16),
            ) {
                TextButton(
                    text = MLang.Component.Button.Cancel,
                    onClick = { show.value = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = MLang.Component.Button.Confirm,
                    onClick = {
                        val itemsToDownload = geoXItems.filter { selectedItems[it.type] == true }
                        if (itemsToDownload.isEmpty()) {
                            return@TextButton
                        }
                        show.value = false
                        scope.launch {
                            val count = viewModel.downloadGeoXFiles(context, itemsToDownload)
                            context.toast(MLang.MetaFeature.Download.DownloadComplete.format(count, itemsToDownload.size))
                        }
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
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
    exitProcess(0)
}
