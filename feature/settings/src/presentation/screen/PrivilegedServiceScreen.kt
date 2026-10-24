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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.contract.PrivilegeStartSource
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.PrivilegedServiceViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppDialog
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Close
import com.github.lmfirefly.flycat.presentation.icon.flycat.Copy
import com.github.lmfirefly.flycat.presentation.icon.flycat.Play
import com.github.lmfirefly.flycat.presentation.icon.flycat.Square
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.horizontalPadding
import com.github.lmfirefly.flycat.presentation.util.toast
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 特权服务配置页：状态卡 + Root / 无线 ADB / 手动 / 外部 Shizuku 四种启动来源，参照 GKD 的特权服务页面形态。启动后的特权进程供超级岛防火墙绕过等系统级操作使用。 */
@Composable
fun PrivilegedServiceScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel: PrivilegedServiceViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logLines by viewModel.startupLog.collectAsStateWithLifecycle()
    val pairRequest by viewModel.pairDialogRequest.collectAsStateWithLifecycle()
    val pairDismiss by viewModel.pairDialogDismissRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPairDialog by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf(TextFieldValue()) }
    var pairPort by remember { mutableStateOf(TextFieldValue()) }
    var pendingLocalNetworkAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        /** 无论授权与否都继续执行：部分系统（如 HyperOS）权限状态与实际 mDNS 能力不一致，拦截只会误伤正常流程；真正缺权限时由发现失败的精准提示兜底。 */
        val action = pendingLocalNetworkAction
        pendingLocalNetworkAction = null
        action?.invoke()
    }
    /** Android 16+ 的本地网络权限是 mDNS 发现（配对/启动）的前提，按需申请后执行 [action]。 */
    fun withLocalNetwork(action: () -> Unit) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
            context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingLocalNetworkAction = action
            localNetworkLauncher.launch(LOCAL_NETWORK_PERMISSION)
        }
    }
    LaunchedEffect(pairRequest) {
        if (pairRequest) {
            showPairDialog = true
            viewModel.consumePairDialogRequest()
        }
    }
    LaunchedEffect(pairDismiss) {
        if (pairDismiss) {
            showPairDialog = false
            viewModel.consumePairDialogDismissRequest()
        }
    }
    LaunchedEffect(showPairDialog) {
        if (showPairDialog) viewModel.onPairDialogOpened()
    }
    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.AppSettings.ServiceSection.PrivilegeTitle,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.running) {
                                    FlyTxt.AppSettings.ServiceSection.PrivilegeStatusConnected
                                } else {
                                    FlyTxt.AppSettings.ServiceSection.PrivilegeStatusDisconnected
                                },
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            uiState.sourceText?.let { source ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = source,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            }
                        }
                        /** 顶部快捷启停圆钮（对照 priv-ui）：▶ 按当前标签页来源启动，■ 停止。 */
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(
                                color = if (uiState.running) {
                                    MiuixTheme.colorScheme.error
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            )
                            .clickable(enabled = !uiState.busy && (uiState.running || selectedTab != 2)) {
                                if (uiState.running) {
                                    viewModel.stop()
                                } else {
                                    when (selectedTab) {
                                        0 -> viewModel.start(PrivilegeStartSource.ROOT)
                                        1 -> withLocalNetwork { viewModel.pairAndStart(pairingCode = "") }
                                        3 -> viewModel.start(PrivilegeStartSource.SHIZUKU)
                                        else -> Unit
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (uiState.running) FlyCat.Square else FlyCat.Play,
                                contentDescription = if (uiState.running) {
                                    FlyTxt.AppSettings.ServiceSection.PrivilegeStop
                                } else {
                                    FlyTxt.AppSettings.ServiceSection.PrivilegeStartButton
                                },
                                tint = if (uiState.running) {
                                    MiuixTheme.colorScheme.onError
                                } else {
                                    MiuixTheme.colorScheme.onPrimary
                                },
                            )
                        }
                    }
                }
            }
            item {
                val tabs = listOf("Root", "ADB", FlyTxt.AppSettings.ServiceSection.PrivilegeTabManual, FlyTxt.AppSettings.ServiceSection.PrivilegeTabExternal)
                TabRow(
                    tabs = tabs,
                    selectedTabIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.horizontalPadding().padding(vertical = 8.dp),
                )
                when (selectedTab) {
                    0 -> PrivilegeRootTab(uiState, viewModel)
                    1 ->
                        PrivilegeAdbTab(
                            uiState = uiState,
                            onPairClick = { showPairDialog = true },
                            onStartAdb = { withLocalNetwork { viewModel.pairAndStart(pairingCode = "") } },
                            onTcpControl = { withLocalNetwork { viewModel.configureTcp(enabled = uiState.tcpPort == null) } },
                            onStartTcp = { withLocalNetwork { viewModel.start(PrivilegeStartSource.TCP) } },
                        )
                    2 -> PrivilegeManualTab(uiState, context)
                    3 -> PrivilegeExternalTab(uiState, viewModel)
                }
            }
            item {
                if (logLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = FlyTxt.AppSettings.ServiceSection.PrivilegeLogTitle,
                                    modifier = Modifier.weight(1f),
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = FlyCat.Copy,
                                    contentDescription = FlyTxt.AppSettings.ServiceSection.PrivilegeLogCopy,
                                    modifier = Modifier.size(20.dp).clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("priv-kit startup log", viewModel.copyStartupLog())
                                        )
                                        context.toast(FlyTxt.AppSettings.ServiceSection.PrivilegeManualCopied)
                                    },
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    imageVector = FlyCat.Close,
                                    contentDescription = FlyTxt.AppSettings.ServiceSection.PrivilegeLogClose,
                                    modifier = Modifier.size(20.dp).clickable { viewModel.clearStartupLog() },
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = logLines.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                }
            }
        }
        /** 对话框必须位于 Scaffold 内容区，才能取得 miuix overlay 渲染上下文。 */
        AppDialog(show = showPairDialog, onDismissRequest = { showPairDialog = false }, title = FlyTxt.AppSettings.ServiceSection.PrivilegePairDialogTitle) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = uiState.discoveredPairingPort?.let {
                        String.format(java.util.Locale.getDefault(), FlyTxt.AppSettings.ServiceSection.PrivilegePairPortFound, it)
                    } ?: FlyTxt.AppSettings.ServiceSection.PrivilegePairSearching,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                Text(
                    text = FlyTxt.AppSettings.ServiceSection.PrivilegePairDialogInstructions,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                TextField(
                    value = pairCode,
                    onValueChange = { pairCode = it },
                    label = FlyTxt.AppSettings.ServiceSection.PrivilegePairCodeHint,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = pairPort,
                    onValueChange = { pairPort = it },
                    label = FlyTxt.AppSettings.ServiceSection.PrivilegePairPortHint,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegePairTutorial,
                        onClick = { openPrivilegeTutorial(context) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = FlyTxt.Component.Button.Cancel,
                        onClick = { showPairDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegePairButton,
                        onClick = {
                            showPairDialog = false
                            withLocalNetwork { viewModel.pairAndStart(pairCode.text, pairPort.text) }
                        },
                        enabled = pairCode.text.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivilegeRootTab(uiState: PrivilegedServiceViewModel.UiState, viewModel: PrivilegedServiceViewModel) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = FlyTxt.AppSettings.ServiceSection.PrivilegeRootSummary,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.start(PrivilegeStartSource.ROOT) },
                enabled = !uiState.busy,
            ) {
                Text(FlyTxt.AppSettings.ServiceSection.PrivilegeStartButton)
            }
        }
    }
}

@Composable
private fun PrivilegeAdbTab(
    uiState: PrivilegedServiceViewModel.UiState,
    onPairClick: () -> Unit,
    onStartAdb: () -> Unit,
    onTcpControl: () -> Unit,
    onStartTcp: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = FlyTxt.AppSettings.ServiceSection.PrivilegeKeepAliveTitle,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = FlyTxt.AppSettings.ServiceSection.PrivilegeKeepAliveSummary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val ignored = isBatteryOptimizationIgnored(context)
                        val launched = batteryOptimizationIntents(context, ignored).any { intent ->
                            runCatching { context.startActivity(intent) }.isSuccess
                        }
                        if (!launched) {
                            context.toast(FlyTxt.Util.Error.UnknownError)
                        }
                    },
                ) {
                    Text(FlyTxt.AppSettings.ServiceSection.PrivilegeKeepAliveButton)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegeFingerprintTitle,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegeTutorial,
                        onClick = { openPrivilegeTutorial(context) },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.identityFingerprint ?: "—",
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegeWirelessTitle,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (uiState.wirelessDebugEnabled) {
                            FlyTxt.AppSettings.ServiceSection.PrivilegeWirelessOn
                        } else {
                            FlyTxt.AppSettings.ServiceSection.PrivilegeWirelessOff
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = FlyTxt.AppSettings.ServiceSection.PrivilegePairInstructions,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegePairButton,
                        onClick = onPairClick,
                        enabled = !uiState.busy,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onStartAdb,
                        enabled = !uiState.busy,
                    ) {
                        Text(FlyTxt.AppSettings.ServiceSection.PrivilegeStartButton)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegeTcpTitle,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = uiState.tcpStatusText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(
                        text = FlyTxt.AppSettings.ServiceSection.PrivilegeTcpControl,
                        onClick = onTcpControl,
                        enabled = !uiState.busy,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onStartTcp,
                        enabled = !uiState.busy && uiState.tcpPort != null,
                    ) {
                        Text(FlyTxt.AppSettings.ServiceSection.PrivilegeStartButton)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivilegeManualTab(uiState: PrivilegedServiceViewModel.UiState, context: Context) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = FlyTxt.AppSettings.ServiceSection.PrivilegeManualSummary,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.manualCommand ?: "—",
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val command = uiState.manualCommand
            Row {
                TextButton(
                    text = FlyTxt.AppSettings.ServiceSection.PrivilegeTutorial,
                    onClick = { openPrivilegeTutorial(context) },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (command != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("priv-kit starter", command))
                            context.toast(FlyTxt.AppSettings.ServiceSection.PrivilegeManualCopied)
                        }
                    },
                    enabled = command != null,
                ) {
                    Text(FlyTxt.AppSettings.ServiceSection.PrivilegeManualCopy)
                }
            }
        }
    }
}

@Composable
private fun PrivilegeExternalTab(uiState: PrivilegedServiceViewModel.UiState, viewModel: PrivilegedServiceViewModel) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FlyTxt.AppSettings.ServiceSection.ShizukuTitle,
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = uiState.shizukuStatusText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = FlyTxt.AppSettings.ServiceSection.PrivilegeExternalSummary,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = viewModel::onExternalStart,
                enabled = !uiState.busy,
            ) {
                Text(FlyTxt.AppSettings.ServiceSection.PrivilegeExternalStartButton)
            }
        }
    }
}

private const val PRIVILEGE_TUTORIAL_URL = "https://priv-kit.pages.dev/zh/guide/startup-tutorial"

/** Android 16+ 的本地网络访问权限，mDNS 发现无线调试服务的前提。 */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

private fun openPrivilegeTutorial(context: Context) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVILEGE_TUTORIAL_URL)))
    }.onFailure { context.toast(FlyTxt.Util.Error.UnknownError) }
}
