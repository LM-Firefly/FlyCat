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

package com.github.lmfirefly.flycat.runtime.api.remote

import com.github.lmfirefly.flycat.core.model.ConnectionOverviewSnapshot
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.model.Provider
import com.github.lmfirefly.flycat.core.model.ProviderList
import com.github.lmfirefly.flycat.core.model.RuntimeRule
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroup
import com.github.lmfirefly.flycat.core.model.proxy.ProxySort
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState

interface IClashManager {
    suspend fun queryTunnelState(): TunnelState

    suspend fun queryTrafficNow(): Long

    suspend fun queryTrafficTotal(): Long

    suspend fun queryConnections(): ConnectionSnapshot

    suspend fun queryConnectionsOverview(): ConnectionOverviewSnapshot

    suspend fun queryRules(): List<RuntimeRule>

    suspend fun setRuleDisabled(index: Int, disabled: Boolean): Boolean

    suspend fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup>

    suspend fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup>

    suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String>

    suspend fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup

    suspend fun queryProviders(): ProviderList

    suspend fun patchSelector(group: String, name: String): Boolean

    suspend fun closeConnection(id: String): Boolean

    suspend fun closeAllConnections()

    suspend fun healthCheck(group: String)

    suspend fun healthCheckProxy(group: String, proxyName: String): Int

    suspend fun updateProvider(type: Provider.Type, name: String)

    suspend fun patchForceSelector(group: String, name: String): Boolean

    suspend fun queryActiveProfileTunRouteExcludeAddress(): List<String>

    suspend fun queryProfileProxyGroupNames(excludeNotSelectable: Boolean): List<String>

    suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean

    suspend fun requestStop()

    fun setLogObserver(observer: ILogObserver?)
}
