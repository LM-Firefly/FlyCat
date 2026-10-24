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

package com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.PrivilegeLogListener
import com.github.lmfirefly.flycat.core.contract.PrivilegeStartSource
import com.github.lmfirefly.flycat.core.contract.PrivilegedAccessReader
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 特权服务配置页（Root / 无线 ADB / 手动 / 外部 Shizuku 四种启动来源）的展示状态与动作。 */
class PrivilegedServiceViewModel(
    private val application: Application,
    private val privilegedAccess: PrivilegedAccessReader,
) : ViewModel() {
    data class UiState(
        val running: Boolean = false,
        val serverUid: Int? = null,
        val wirelessDebugEnabled: Boolean = false,
        val shizukuRunning: Boolean = false,
        val shizukuGranted: Boolean = false,
        val manualCommand: String? = null,
        val identityFingerprint: String? = null,
        val tcpPort: Int? = null,
        val discoveredPairingPort: Int? = null,
        val busy: Boolean = false,
    ) {
        val sourceText: String?
            get() = when {
                !running -> null
                serverUid == 0 -> FlyTxt.AppSettings.ServiceSection.PrivilegeSourceRootText
                else -> FlyTxt.AppSettings.ServiceSection.PrivilegeSourceShellText
            }
        val shizukuStatusText: String
            get() = when {
                !shizukuRunning -> FlyTxt.AppSettings.ServiceSection.ShizukuStatusUnavailable
                !shizukuGranted -> FlyTxt.AppSettings.ServiceSection.ShizukuStatusPending
                else -> FlyTxt.AppSettings.ServiceSection.ShizukuStatusGranted
            }
        val tcpStatusText: String
            get() = tcpPort?.let {
                String.format(java.util.Locale.getDefault(), FlyTxt.AppSettings.ServiceSection.PrivilegeTcpPortText, it)
            } ?: FlyTxt.AppSettings.ServiceSection.PrivilegeTcpNotConfigured
    }
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    /** ADB 启动失败后请求界面弹出配对对话框（一次性事件）。 */
    private val _pairDialogRequest = MutableStateFlow(false)
    val pairDialogRequest: StateFlow<Boolean> = _pairDialogRequest.asStateFlow()
    /** 任意入口启动成功后请求收尾配对对话框（一次性事件）。 */
    private val _pairDialogDismissRequest = MutableStateFlow(false)
    val pairDialogDismissRequest: StateFlow<Boolean> = _pairDialogDismissRequest.asStateFlow()
    fun consumePairDialogDismissRequest() {
        _pairDialogDismissRequest.value = false
    }
    /** 启动日志（界面阶段消息 + 含来源标注的 priv-kit 明细行）。 */
    private val _startupLog = MutableStateFlow<List<String>>(emptyList())
    val startupLog: StateFlow<List<String>> = _startupLog.asStateFlow()
    private val logListener = PrivilegeLogListener { source, message ->
        appendStartupLog("[$source] $message")
    }
    fun appendStartupLog(line: String) {
        _startupLog.update { it + line }
    }
    fun clearStartupLog() {
        _startupLog.value = emptyList()
    }
    fun copyStartupLog(): String = _startupLog.value.joinToString("\n")
    fun consumePairDialogRequest() {
        _pairDialogRequest.value = false
    }
    init {
        refresh()
        loadManualCommand()
        // 任意入口（弹窗/通知/Root/手动）启动成功后自动收尾配对弹窗并刷新状态。
        viewModelScope.launch {
            privilegedAccess.privilegeRunningFlow.collect { running ->
                refresh()
                if (running) {
                    _pairDialogDismissRequest.value = true
                    privilegedAccess.stopPairingSession(application)
                }
            }
        }
    }
    fun refresh() {
        _uiState.update {
            it.copy(
                running = privilegedAccess.isPrivilegeRunning(),
                serverUid = privilegedAccess.privilegeServerUid(),
                wirelessDebugEnabled = isWirelessDebugEnabled(),
                shizukuRunning = privilegedAccess.isShizukuRunning(),
                shizukuGranted = privilegedAccess.hasShizukuPermission(),
                tcpPort = privilegedAccess.privilegeTcpPort(),
            )
        }
    }
    /** 按指定来源启动特权服务。 */
    fun start(source: PrivilegeStartSource) = launchStart(source)
    /** 经 Shizuku 外部启动：未运行→打开 Shizuku；未授权→申请权限后启动；就绪→直接启动。 */
    fun onExternalStart() {
        when {
            !privilegedAccess.isShizukuRunning() -> {
                if (!privilegedAccess.openShizuku(application)) {
                    application.toast(FlyTxt.AppSettings.ServiceSection.ShizukuNotRunning)
                }
            }
            !privilegedAccess.hasShizukuPermission() ->
                privilegedAccess.requestShizukuPermission { granted ->
                    viewModelScope.launch {
                        refresh()
                        if (granted) {
                            launchStart(PrivilegeStartSource.SHIZUKU)
                        } else {
                            application.toast(FlyTxt.AppSettings.ServiceSection.ShizukuPermissionRequired)
                        }
                    }
                }
            else -> launchStart(PrivilegeStartSource.SHIZUKU)
        }
    }
    /** 配对码非空时先完成无线调试配对再启动；留空直接启动（已配对过）。端口留空则用预发现/现场发现。 */
    fun pairAndStart(pairingCode: String, pairingPort: String = "") {
        val code = pairingCode.trim()
        if (code.isEmpty()) {
            launchStart(PrivilegeStartSource.ADB)
            return
        }
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        clearStartupLog()
        appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegeLogPairing)
        viewModelScope.launch {
            val port = resolvePairingPort(pairingPort.trim().toIntOrNull())
            if (port == null) {
                // 配对窗口的 mDNS 服务生命周期短，发现失败与配对码无关，单独提示。
                _uiState.update { it.copy(busy = false) }
                refresh()
                privilegedAccess.stopPairingSession(application)
                appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegePairDiscoverFailed)
                application.toast(FlyTxt.AppSettings.ServiceSection.PrivilegePairDiscoverFailed)
                return@launch
            }
            privilegedAccess.pairPrivilege(code, port) { paired ->
                privilegedAccess.stopPairingSession(application)
                if (paired) {
                    appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegeReady)
                    launchStart(PrivilegeStartSource.ADB, keepBusy = true)
                } else {
                    _uiState.update { it.copy(busy = false) }
                    refresh()
                    appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegePairFailed)
                    application.toast(FlyTxt.AppSettings.ServiceSection.PrivilegePairFailed)
                }
            }
        }
    }
    /** 配对对话框打开即预发现端口并开启通知栏配对会话，消除与系统配对窗口生命周期的竞态。 */
    fun onPairDialogOpened() {
        privilegedAccess.startPairingSession(application)
        if (_uiState.value.discoveredPairingPort != null || _uiState.value.busy) return
        viewModelScope.launch { discoverPairingPort() }
    }
    /** 端口优先级：手输 > 预发现 > 现场发现（mDNS）。 */
    private suspend fun resolvePairingPort(manualPort: Int?): Int? {
        manualPort?.let { return it }
        _uiState.value.discoveredPairingPort?.let { return it }
        return discoverPairingPort()
    }
    private suspend fun discoverPairingPort(): Int? = suspendCancellableCoroutine { continuation ->
        privilegedAccess.discoverPairingPort { port ->
            _uiState.update { it.copy(discoveredPairingPort = port) }
            if (continuation.isActive) continuation.resume(port)
        }
    }
    fun stop() {
        privilegedAccess.stopPrivilege()
        refresh()
    }
    /** 配置（true）或停用（false）无线调试静态端口。 */
    fun configureTcp(enabled: Boolean) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        privilegedAccess.configurePrivilegeTcp(enabled) { ok ->
            _uiState.update { it.copy(busy = false) }
            refresh()
            application.toast(
                if (ok) {
                    FlyTxt.AppSettings.ServiceSection.PrivilegeTcpChanged
                } else {
                    FlyTxt.AppSettings.ServiceSection.PrivilegeStartFailed
                }
            )
        }
    }
    private fun launchStart(source: PrivilegeStartSource, keepBusy: Boolean = false) {
        if (!keepBusy && _uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        if (!keepBusy) clearStartupLog()
        appendStartupLog(String.format(java.util.Locale.getDefault(), FlyTxt.AppSettings.ServiceSection.PrivilegeLogStartSource, source.logName))
        appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegeLogStarting)
        privilegedAccess.startPrivilege(application, source, logListener) { started ->
            _uiState.update { it.copy(busy = false) }
            refresh()
            when {
                started -> {
                    appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegeReady)
                    application.toast(FlyTxt.AppSettings.ServiceSection.PrivilegeReady)
                }
                // ADB 启动最常见的失败原因是尚未配对：给出明确提示并直接打开配对对话框。
                source == PrivilegeStartSource.ADB -> {
                    appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegeStartNotPaired)
                    _pairDialogRequest.value = true
                    application.toast(FlyTxt.AppSettings.ServiceSection.PrivilegeStartNotPaired)
                }
                else -> {
                    appendStartupLog(FlyTxt.AppSettings.ServiceSection.PrivilegeStartFailed)
                    application.toast(FlyTxt.AppSettings.ServiceSection.PrivilegeStartFailed)
                }
            }
        }
    }
    private val PrivilegeStartSource.logName: String
        get() = when (this) {
            PrivilegeStartSource.ROOT -> "Root"
            PrivilegeStartSource.ADB -> "ADB"
            PrivilegeStartSource.TCP -> "TCP"
            PrivilegeStartSource.SHIZUKU -> "Shizuku"
        }
    private fun loadManualCommand() {
        viewModelScope.launch(Dispatchers.IO) {
            val command = privilegedAccess.privilegeManualCommand()
            val fingerprint = privilegedAccess.privilegeIdentityFingerprint()
            _uiState.update { it.copy(manualCommand = command, identityFingerprint = fingerprint) }
        }
    }
    private fun isWirelessDebugEnabled(): Boolean = runCatching {
        Settings.Global.getInt(application.contentResolver, "adb_wifi_enabled", 0) != 0
    }.getOrDefault(false)
}
