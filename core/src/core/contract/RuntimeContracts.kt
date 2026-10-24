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

package com.github.lmfirefly.flycat.core.contract

import com.github.lmfirefly.flycat.core.model.profile.Imported
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import java.io.File
import java.util.UUID

/** Contract for log record persistence consumed by [runtime:service]. */
interface LogRecordGateway {
    val isRecording: Boolean
    val currentLogFileName: String?
    val logPrefix: String
    val logSuffix: String
    val stopWaitMillis: Long
    fun start(application: android.app.Application)
    fun stop(application: android.app.Application)
    fun getLogDir(application: android.app.Application): File
}

/** Functional interface for writing a single log line to the runtime log. */
fun interface RuntimeLogWriter {
    fun writeLog(line: String)
}

/**
 * Read-only contract for [runtime:service] bootstrap decisions.
 * Android Services cannot use constructor injection; this interface abstracts the
 * data-store queries they need.
 */
interface ServiceBootstrapReader {
    val automaticRestart: Boolean
    val autoUpdateCurrentProfileOnStart: Boolean
    val runMode: RunMode
    fun isRemoteControllerActive(): Boolean
    fun consumePostUpdateColdStartPending(): Boolean
    fun markAutoStartStarted()
    fun clearAutoStart()
    fun isAutoStartInFlight(): Boolean
}

/**
 * Static holder for [ServiceBootstrapReader] so that Android framework-instantiated components can access it without constructor injection.
 */
object ServiceBootstrapHolder {
    @Volatile
    private var _reader: ServiceBootstrapReader? = null
    val reader: ServiceBootstrapReader
        get() = _reader ?: error("ServiceBootstrapReader not initialized; call initialize() in Application.onCreate")
    fun initialize(reader: ServiceBootstrapReader) { _reader = reader }
}

/** Read/write contract for runtime service active profile state. */
interface ServiceStateReader {
    var activeProfile: UUID?
}

/** Read/write contract for profile records persisted by [runtime:service]. */
interface ProfileStoreReader {
    fun loadImported(): List<Imported>
    fun saveImported(list: List<Imported>)
    fun loadProfileOrder(): List<UUID>
    fun saveProfileOrder(order: List<UUID>)
}

/** Commands for controlling the runtime lifecycle (stop, reconcile, etc.). */
interface RuntimeLifecycleCommand {
    suspend fun stopProxy()
    suspend fun reconcileRuntimeState()
    suspend fun applyRemoteControllerState()
}

/** Sends broadcast intents for profile/override changes. */
interface BroadcastNotifier {
    fun notifyProfileChanged()
    fun notifyOverrideChanged()
}

/** Lifecycle hook for resources that must be cleaned up when the application terminates. */
fun interface AppShutdownHandler {
    fun onShutdown()
}

/**
 * 为设置 UI 提供实时特权访问状态（priv-kit 特权服务 + Shizuku 外部启动通道）与 HyperOS 超级岛支持。
 * 由拥有特权通道和超级岛代码的 [runtime:service] 实现。
 */
interface PrivilegedAccessReader {
    val isSuperIslandSupported: Boolean
    /** Shizuku 授权器状态（供「外部」启动方式引导授权与打开 Shizuku）。 */
    fun isShizukuRunning(): Boolean
    fun hasShizukuPermission(): Boolean
    fun requestShizukuPermission(callback: (Boolean) -> Unit)
    fun openShizuku(context: android.content.Context): Boolean
    /** priv-kit 自有特权服务通道（Root / 无线 ADB / 静态端口 / 外部 Shizuku 启动）。 */
    val privilegeRunningFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun isPrivilegeRunning(): Boolean
    fun startPrivilege(
        context: android.content.Context,
        source: PrivilegeStartSource,
        logListener: PrivilegeLogListener?,
        callback: (Boolean) -> Unit,
    )
    fun pairPrivilege(pairingCode: String, pairingPort: Int, callback: (Boolean) -> Unit)
    fun discoverPairingPort(callback: (Int?) -> Unit)
    fun startPairingSession(context: android.content.Context)
    fun stopPairingSession(context: android.content.Context)
    fun stopPrivilege()
    fun privilegeServerUid(): Int?
    fun privilegeManualCommand(): String?
    fun privilegeIdentityFingerprint(): String?
    fun privilegeTcpPort(): Int?
    fun configurePrivilegeTcp(enabled: Boolean, callback: (Boolean) -> Unit)
}

/** 特权服务启动日志输出（source 为来源标识，如 runtime/diag/adb/tcp）。 */
fun interface PrivilegeLogListener {
    fun onLog(source: String, message: String)
}

/** 特权服务启动来源。 */
enum class PrivilegeStartSource {
    /** Root 身份直接启动。 */
    ROOT,
    /** 无线调试（ADB）启动。 */
    ADB,
    /** 无线调试静态端口（TCP/IP）启动。 */
    TCP,
    /** 借助 Shizuku 的 shell 身份执行启动命令（外部授权器）。 */
    SHIZUKU,
}
