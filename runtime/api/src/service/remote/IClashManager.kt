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

package com.github.yumelira.yumebox.runtime.api.service.remote

import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProviderList
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration

interface IClashManager {
    suspend fun queryTunnelState(): TunnelState

    suspend fun queryTrafficNow(): Long

    suspend fun queryTrafficTotal(): Long

    suspend fun queryConnections(): ConnectionSnapshot

    suspend fun queryProfileProxyGroupNames(excludeNotSelectable: Boolean): List<String>

    suspend fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup>

    suspend fun queryActiveProfileTunRouteExcludeAddress(): List<String>

    suspend fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup>

    suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String>

    suspend fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup

    suspend fun queryConfiguration(): UiConfiguration

    suspend fun queryProviders(): ProviderList

    suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean

    suspend fun patchSelector(group: String, name: String): Boolean

    suspend fun patchForceSelector(group: String, name: String): Boolean

    suspend fun closeConnection(id: String): Boolean

    suspend fun closeAllConnections()

    suspend fun healthCheck(group: String)

    suspend fun healthCheckProxy(group: String, proxyName: String): Int

    suspend fun updateProvider(type: Provider.Type, name: String)

    fun requestStop()

    fun setLogObserver(observer: ILogObserver?)
}
