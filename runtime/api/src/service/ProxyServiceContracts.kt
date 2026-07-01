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
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeTargetMode

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

object RuntimeServiceContractRegistry {
    @Volatile
    var localRuntimeService: LocalRuntimeServiceContract? = null
    @Volatile
    var localRuntimeStatus: LocalRuntimeStatusContract? = null
    @Volatile
    var rootAccessSupport: com.github.yumelira.yumebox.runtime.api.service.root.RootAccessSupportContract? = null
    @Volatile
    var rootTunRuntimeRecovery: com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract? = null
    @Volatile
    var rootTunForegroundService: com.github.yumelira.yumebox.runtime.api.service.root.RootTunForegroundServiceContract? = null
    @Volatile
    var rootTunStateStoreFactory: com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreFactoryContract? = null
    @Volatile
    var rootPackageQuery: com.github.yumelira.yumebox.runtime.api.service.root.RootPackageQueryContract? = null
    @Volatile
    var rootTunBinding: com.github.yumelira.yumebox.runtime.api.service.root.RootTunBindingContract? = null
    @Volatile
    var localRuntimeSessionHelpers: com.github.yumelira.yumebox.runtime.api.service.runtime.session.LocalRuntimeSessionHelpers? = null
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
