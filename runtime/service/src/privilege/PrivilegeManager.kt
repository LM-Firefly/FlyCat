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

package com.github.lmfirefly.flycat.runtime.service.privilege

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.github.lmfirefly.flycat.core.contract.PrivilegeLogListener
import com.github.lmfirefly.flycat.core.contract.PrivilegeStartSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.PrivilegeStartupLogLine
import priv.kit.core.PrivilegeStartupLogListener
import priv.kit.core.adb.PrivilegeAdbAuthorizationStatus
import priv.kit.core.adb.PrivilegeAdbConnectionOptions
import priv.kit.core.binder.PrivilegeBinderWrapper
import priv.kit.core.binder.PrivilegeSystemServiceSource

/**
 * priv-kit 自有特权运行时（Root / 无线 ADB / 静态端口 / 手动 / 外部授权器启动的 Privileged Server）的接入封装。
 * 作为超级岛 XMSF 防火墙绕过的唯一通道；XMSF bypass 会话编排见 [PrivilegedBypass]。
 */
object PrivilegeManager {
    private const val TAG = "FlyCatPrivilege"
    /** 外部启动后等待服务端握手的时长与轮询间隔。 */
    private const val EXTERNAL_HANDSHAKE_TIMEOUT_MS = 15_000L
    private const val EXTERNAL_HANDSHAKE_POLL_MS = 300L
    /** 配对端口 mDNS 发现窗口（配对窗口服务生命周期短，窗口宜放宽）。 */
    private const val PAIRING_DISCOVERY_TIMEOUT_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Privileged Server 是否已连接（纯内存状态，无 IPC）。 */
    fun isRunning(): Boolean = Privilege.serverState.value != null
    /** 运行状态流：供界面在任意入口配对成功后自动收尾（关弹窗/刷新）。 */
    val running: StateFlow<Boolean> = Privilege.serverState.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)
    /** 按指定来源启动 Privileged Server；过程日志经 [logListener] 回传，结果经 [callback] 回传。 */
    fun start(context: Context, source: PrivilegeStartSource, logListener: PrivilegeLogListener?, callback: (Boolean) -> Unit) {
        scope.launch {
            val listener = logListener?.let { sink ->
                PrivilegeStartupLogListener { line -> sink.onLog(line.source, line.message) }
            }
            val started = runCatching {
                when {
                    isRunning() -> true
                    source == PrivilegeStartSource.SHIZUKU -> startViaShizuku(context)
                    source == PrivilegeStartSource.ROOT -> tryStart { Privilege.startRoot(startupLogListener = listener) }
                    source == PrivilegeStartSource.TCP -> tryStartTcp(listener)
                    else -> tryStart { Privilege.startAdb(startupLogListener = listener) }
                }
            }.getOrElse { error ->
                Log.w(TAG, "Privileged server start failed", error)
                logListener?.onLog("runtime", "Startup failed: ${error.message.orEmpty()}")
                false
            }
            withContext(Dispatchers.Main) { callback(started) }
        }
    }
    /** 停止当前 Privileged Server。 */
    fun stop() {
        runCatching { Privilege.shutdownServer() }
            .onFailure { error -> Log.w(TAG, "Privileged server shutdown failed", error) }
    }
    /** 已连接服务端的 uid（0=root，2000=shell），未连接为 null。 */
    fun serverUid(): Int? = Privilege.serverState.value?.uid
    /** 手动启动命令（供 PC `adb shell` 执行）；无法解析时为 null。 */
    fun manualCommand(): String? = runCatching { Privilege.nativeStarterCommand }.getOrNull()
    /** 本应用 ADB 公钥指纹（冒号分隔十六进制），无法读取时为 null。 */
    fun identityFingerprint(): String? = runCatching { Privilege.createAdbManager().getIdentityInfo().publicKeyFingerprint }.getOrNull()
    /** 已配置的无线调试静态端口，null 表示未配置。 */
    fun tcpPort(): Int? = runCatching { Privilege.createAdbManager().getConfiguredTcpPort() }.getOrNull()
    /** 配置（true）或停用（false）无线调试静态端口；结果经 [callback] 回传。 */
    fun configureTcp(enabled: Boolean, callback: (Boolean) -> Unit) {
        scope.launch {
            val ok = runCatching {
                val manager = Privilege.createAdbManager()
                if (enabled) manager.switchToTcp() else manager.stopTcp()
                true
            }.getOrElse { error ->
                Log.w(TAG, "Static port configuration failed", error)
                false
            }
            withContext(Dispatchers.Main) { callback(ok) }
        }
    }
    /** 经已配置的静态端口启动；未配置或未授权时失败。 */
    private suspend fun tryStartTcp(listener: PrivilegeStartupLogListener?): Boolean {
        val manager = runCatching { Privilege.createAdbManager() }.getOrNull() ?: return false
        val port = manager.getConfiguredTcpPort() ?: run {
            Log.w(TAG, "Static port is not configured")
            listener?.onLog(PrivilegeStartupLogLine("tcp", "Static port is not configured"))
            return false
        }
        val check = runCatching { manager.prepareTcpForStart(tcpPort = port) }.getOrNull() ?: return false
        if (check.status == PrivilegeAdbAuthorizationStatus.UNAUTHORIZED) {
            val request = runCatching { manager.requestTcpAuthorization(tcpPort = port) }.getOrNull() ?: return false
            if (!request.authorized) return false
        }
        return tryStart { Privilege.startAdb(options = PrivilegeAdbConnectionOptions(port = port), startupLogListener = listener) }
    }
    /** 借助 Shizuku user service 的 shell 身份执行启动命令，随后轮询认领握手。 */
    private suspend fun startViaShizuku(context: Context): Boolean {
        val command = manualCommand() ?: run {
            Log.w(TAG, "Unable to resolve the native starter command")
            return false
        }
        if (!ShizukuManager.execInUserService(context, command)) return false
        val deadline = SystemClock.elapsedRealtime() + EXTERNAL_HANDSHAKE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isRunning()) return true
            runCatching { Privilege.connectReadyServer() }
                .onFailure { error -> Log.w(TAG, "connectReadyServer failed", error) }
            if (isRunning()) return true
            delay(EXTERNAL_HANDSHAKE_POLL_MS)
        }
        return isRunning()
    }
    /** 无线调试配对（Android 11+）；端口来自预发现或用户手输（系统配对框会显示）。 */
    fun pair(pairingCode: String, pairingPort: Int, callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            scope.launch { withContext(Dispatchers.Main) { callback(false) } }
            return
        }
        scope.launch {
            val paired = runCatching {
                Privilege.createAdbManager().pair(pairingCode = pairingCode.trim(), port = pairingPort)
                true
            }.getOrElse { error ->
                Log.w(TAG, "Wireless debugging pairing failed", error)
                false
            }
            withContext(Dispatchers.Main) { callback(paired) }
        }
    }
    /** 配对 + 启动的一站式流程（弹窗入口使用）；端口为空时现场发现。 */
    suspend fun pairAndStart(context: Context, pairingCode: String, pairingPort: Int?): Boolean {
        if (!pairOnly(pairingCode, pairingPort)) return false
        return runCatching {
            if (isRunning()) true else tryStart { Privilege.startAdb() }
        }.getOrElse { error ->
            Log.w(TAG, "Privileged server start failed", error)
            false
        }
    }
    /** 仅完成无线调试配对（通知入口按粒度反馈：配对成功即视为完成，启动另行报告）。 */
    suspend fun pairOnly(pairingCode: String, pairingPort: Int?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val port = pairingPort ?: runCatching {
            Privilege.createAdbManager().discoverPairingPort(timeoutMillis = PAIRING_DISCOVERY_TIMEOUT_MS)
        }.getOrElse { error ->
            Log.w(TAG, "Pairing port discovery failed", error)
            null
        } ?: return false
        return runCatching {
            Privilege.createAdbManager().pair(pairingCode = pairingCode.trim(), port = port)
            true
        }.getOrElse { error ->
            Log.w(TAG, "Wireless debugging pairing failed", error)
            false
        }
    }
    /** 仅启动（已配对/已就绪场景）。 */
    suspend fun startOnly(): Boolean = runCatching {
        if (isRunning()) true else tryStart { Privilege.startAdb() }
    }.getOrElse { error ->
        Log.w(TAG, "Privileged server start failed", error)
        false
    }
    /** 预发现配对端口（mDNS）；配对窗口服务生命周期短，宜在弹窗打开时就开始监听。 */
    fun discoverPairingPort(callback: (Int?) -> Unit) {
        scope.launch {
            val port = runCatching {
                Privilege.createAdbManager().discoverPairingPort(timeoutMillis = PAIRING_DISCOVERY_TIMEOUT_MS)
            }.getOrElse { error ->
                Log.w(TAG, "Pairing port discovery failed", error)
                null
            }
            withContext(Dispatchers.Main) { callback(port) }
        }
    }
    /** suspend 版端口发现（供通知配对会话使用）。 */
    internal suspend fun awaitPairingPort(): Int? = suspendCancellableCoroutine { continuation ->
        discoverPairingPort { port -> if (continuation.isActive) continuation.resume(port) }
    }
    /** 经 Privileged Server 转发 connectivity binder 事务（调用身份为服务端的 shell/root）。 */
    suspend fun setPackageNetworkingBlocked(uid: Int, blocked: Boolean): Boolean {
        val connectivity = PrivilegeBinderWrapper.fromSystemService("connectivity")
            ?: PrivilegeBinderWrapper.fromSystemService(
                serviceName = "connectivity",
                source = PrivilegeSystemServiceSource.SERVER_PROCESS,
            )
            ?: run {
                Log.e(TAG, "connectivity service is not available")
                return false
            }
        return OemDenyFirewall.setPackageDenied(connectivity, uid, denied = blocked)
    }
    private suspend fun tryStart(block: suspend () -> PrivilegeServerInfo): Boolean = runCatching {
        block()
        true
    }.getOrElse { error ->
        Log.w(TAG, "Privileged server launch attempt failed", error)
        false
    }
}
