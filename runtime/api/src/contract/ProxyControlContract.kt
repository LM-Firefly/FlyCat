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

package com.github.yumelira.yumebox.runtime.api.contract

import com.github.yumelira.yumebox.core.contract.ConnectionRepository
import com.github.yumelira.yumebox.core.contract.ProxyGroupRepository
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.runtime.api.service.root.RootAccessStatus
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified contract for proxy lifecycle control exposed to feature modules.
 * Extends [ProxyGroupRepository] and [ConnectionRepository] and adds runtime-specific APIs.
 * Implemented by [com.github.yumelira.yumebox.runtime.client.ProxyFacade].
 */
interface ProxyControlContract : ProxyGroupRepository, ConnectionRepository {
    val runtimeSnapshot: StateFlow<RuntimeSnapshot>
    val currentProfile: StateFlow<Profile?>
    val trafficNow: StateFlow<Traffic>
    val tunnelMode: StateFlow<TunnelState.Mode?>
    val resolvedPrimaryNode: StateFlow<Proxy?>

    suspend fun startProxy(mode: ProxyMode)
    suspend fun stopProxy(mode: ProxyMode? = null)
    suspend fun reconcileRuntimeState()
    fun hasRootPackageAccess(): Boolean
    fun queryInstalledRootPackageNames(): Set<String>?
    suspend fun evaluateRootAccess(): RootAccessStatus
    fun applyRemoteControllerState()
    fun isRemoteControllerActive(): Boolean
    suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean
    suspend fun testRemoteConnection(backend: RemoteBackend): Result<TunnelState>
    suspend fun refreshCurrentProfile()
}
