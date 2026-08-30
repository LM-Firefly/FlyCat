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

package com.github.lmfirefly.flycat.runtime.api.contract

import android.content.Context
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeTargetMode
import com.github.lmfirefly.flycat.runtime.api.root.RootAccessSupportContract
import com.github.lmfirefly.flycat.runtime.api.root.RootPackageQueryContract
import com.github.lmfirefly.flycat.runtime.api.root.RootTunBindingContract
import com.github.lmfirefly.flycat.runtime.api.root.RootTunForegroundServiceContract
import com.github.lmfirefly.flycat.runtime.api.root.RootTunRuntimeRecoveryContract
import com.github.lmfirefly.flycat.runtime.api.root.RootTunStateStoreFactoryContract
import com.github.lmfirefly.flycat.runtime.api.session.LocalRuntimeSessionHelpers
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
    val rootAccessSupport: RootAccessSupportContract,
    val rootTunRuntimeRecovery: RootTunRuntimeRecoveryContract,
    val rootTunForegroundService: RootTunForegroundServiceContract,
    val rootTunStateStoreFactory: RootTunStateStoreFactoryContract,
    val rootPackageQuery: RootPackageQueryContract,
    val rootTunBinding: RootTunBindingContract,
    val localRuntimeSessionHelpers: LocalRuntimeSessionHelpers,
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
    val rootAccessSupport: RootAccessSupportContract?
        get() = snapshot.get()?.rootAccessSupport
    val rootTunRuntimeRecovery: RootTunRuntimeRecoveryContract?
        get() = snapshot.get()?.rootTunRuntimeRecovery
    val rootTunForegroundService: RootTunForegroundServiceContract?
        get() = snapshot.get()?.rootTunForegroundService
    val rootTunStateStoreFactory: RootTunStateStoreFactoryContract?
        get() = snapshot.get()?.rootTunStateStoreFactory
    val rootPackageQuery: RootPackageQueryContract?
        get() = snapshot.get()?.rootPackageQuery
    val rootTunBinding: RootTunBindingContract?
        get() = snapshot.get()?.rootTunBinding
    val localRuntimeSessionHelpers: LocalRuntimeSessionHelpers?
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
