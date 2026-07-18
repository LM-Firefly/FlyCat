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
