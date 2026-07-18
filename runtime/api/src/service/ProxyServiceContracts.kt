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

package com.github.yumelira.yumebox.runtime.api.service

import android.content.Context
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeTargetMode
import java.util.concurrent.atomic.AtomicReference

enum class LocalRuntimePhase {
    Idle,
    Starting,
    Running,
    Stopping,
    Failed;
    val isActive: Boolean
        get() = this != Idle
}

interface LocalRuntimeServiceContract {
    fun start(
        context: Context,
        mode: RuntimeTargetMode,
        source: String = ProxyServiceContracts.SOURCE_UNKNOWN,
    )
    fun stop(
        context: Context,
        clashRequestStopAction: String,
    )
}

interface LocalRuntimeStatusContract {
    val serviceRunning: Boolean
    fun reconcilePersistedRuntimeState()
    fun clearLegacyStateFiles()
    fun isRuntimeActive(mode: RuntimeTargetMode): Boolean
    fun queryRuntimePhase(mode: RuntimeTargetMode): LocalRuntimePhase
    fun queryRuntimeStartedAt(mode: RuntimeTargetMode): Long?
    fun isLocalRuntimeServiceAlive(mode: RuntimeTargetMode): Boolean
    fun markRuntimeIdle(mode: RuntimeTargetMode)
}

/**
 * Immutable snapshot of all runtime service contracts.
 *
 * Registered atomically in [RuntimeServiceContractRegistry.register] by
 * `StatusProvider.onCreate()` and read lock-free from any thread.
 */
data class RegisteredContracts(
    val localRuntimeService: LocalRuntimeServiceContract,
    val localRuntimeStatus: LocalRuntimeStatusContract,
    val rootAccessSupport: com.github.yumelira.yumebox.runtime.api.service.root.RootAccessSupportContract,
    val rootTunRuntimeRecovery: com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract,
    val rootTunForegroundService: com.github.yumelira.yumebox.runtime.api.service.root.RootTunForegroundServiceContract,
    val rootTunStateStoreFactory: com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreFactoryContract,
    val rootPackageQuery: com.github.yumelira.yumebox.runtime.api.service.root.RootPackageQueryContract,
    val rootTunBinding: com.github.yumelira.yumebox.runtime.api.service.root.RootTunBindingContract,
    val localRuntimeSessionHelpers: com.github.yumelira.yumebox.runtime.api.service.runtime.session.LocalRuntimeSessionHelpers,
)

object RuntimeServiceContractRegistry {
    private val snapshot = AtomicReference<RegisteredContracts?>(null)

    /** Register all contracts atomically. Must be called exactly once from `StatusProvider.onCreate()`. */
    fun register(contracts: RegisteredContracts) {
        snapshot.set(contracts)
    }

    /** Current snapshot, or `null` if [register] has not been called yet. */
    val contracts: RegisteredContracts?
        get() = snapshot.get()

    // -- Read-only convenience accessors (delegating to the immutable snapshot) --

    val localRuntimeService: LocalRuntimeServiceContract?
        get() = snapshot.get()?.localRuntimeService
    val localRuntimeStatus: LocalRuntimeStatusContract?
        get() = snapshot.get()?.localRuntimeStatus
    val rootAccessSupport: com.github.yumelira.yumebox.runtime.api.service.root.RootAccessSupportContract?
        get() = snapshot.get()?.rootAccessSupport
    val rootTunRuntimeRecovery: com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract?
        get() = snapshot.get()?.rootTunRuntimeRecovery
    val rootTunForegroundService: com.github.yumelira.yumebox.runtime.api.service.root.RootTunForegroundServiceContract?
        get() = snapshot.get()?.rootTunForegroundService
    val rootTunStateStoreFactory: com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreFactoryContract?
        get() = snapshot.get()?.rootTunStateStoreFactory
    val rootPackageQuery: com.github.yumelira.yumebox.runtime.api.service.root.RootPackageQueryContract?
        get() = snapshot.get()?.rootPackageQuery
    val rootTunBinding: com.github.yumelira.yumebox.runtime.api.service.root.RootTunBindingContract?
        get() = snapshot.get()?.rootTunBinding
    val localRuntimeSessionHelpers: com.github.yumelira.yumebox.runtime.api.service.runtime.session.LocalRuntimeSessionHelpers?
        get() = snapshot.get()?.localRuntimeSessionHelpers
}

object ProxyServiceContracts {
    const val SOURCE_UI = "ui"
    const val SOURCE_TILE = "tile"
    const val SOURCE_AUTO_RESTART = "auto_restart"
    const val SOURCE_AUTO_RESTART_BOOT = "auto_restart_boot"
    const val SOURCE_AUTO_RESTART_REPLACED = "auto_restart_replaced"
    const val SOURCE_UNKNOWN = "unknown"

    fun intentSelf(action: String, packageName: String? = null): android.content.Intent =
        android.content.Intent(action).apply {
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
}
