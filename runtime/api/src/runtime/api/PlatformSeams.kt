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

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.runtime.api


import com.github.yumelira.yumebox.core.model.RunMode

/**
 * Platform-neutral core process control. Android wires
 * `com.github.yumelira.yumebox.runtime.service.core.CoreProcess`; desktop can implement the same
 * contract without VpnService/libsu.
 */
interface ProcessController {
    /** Active controller endpoint for the running core, or null when stopped. */
    fun currentEndpoint(): CoreEndpointRef?

    fun stop()

    fun stopRoot()

    fun isRootDaemonAlive(): Boolean

    fun reconnectRoot(): String?
}

/** Sock path + bearer secret for the mihomo controller. */
data class CoreEndpointRef(val sock: String, val secret: String)

/**
 * Cross-process / cross-host runtime phase store. Android: ContentProvider-backed StatusProvider.
 */
interface RuntimeStatusStore {
    fun isRuntimeActive(runModeName: String): Boolean

    fun queryRuntimePhase(runModeName: String): RuntimePhase

    fun queryRuntimeStartedAt(runModeName: String): Long?

    fun queryRuntimeLastError(runModeName: String): String?

    fun markRuntimeIdle(runModeName: String)

    fun reconcilePersistedRuntimeState()

    /** Platform-local service/process liveness for a configured run mode name. */
    fun isLocalRuntimeServiceAlive(runModeName: String): Boolean

    fun clearLegacyStateFiles()
}

/**
 * Platform-specific start/stop of the local runtime host (VPN service, root daemon, etc.). Desktop
 * can implement without Android Service APIs.
 */
interface RuntimeLauncher {
    suspend fun start(owner: RuntimeOwner, mode: RunMode)

    suspend fun stop(owner: RuntimeOwner)
}

/**
 * Resolves the live core controller endpoint (local unix path + secret, or remote backend
 * metadata).
 */
interface CoreEndpointSource {
    fun localSocketPath(): String?

    fun localSecret(): String?

    fun isRemoteActive(): Boolean
}
