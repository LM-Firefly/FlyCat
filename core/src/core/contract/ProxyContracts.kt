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

import com.github.lmfirefly.flycat.core.model.ConnectionOverviewSnapshot
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.core.model.proxy.ProxySort
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.core.model.RemoteBackend
import com.github.lmfirefly.flycat.core.model.RuntimeRule
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState.Mode
import kotlinx.coroutines.flow.StateFlow

/** Read-only contract for proxy display settings consumed by feature modules. */
interface ProxyDisplaySettingsReader {
    val sortMode: Preference<ProxySortMode>
    val displayMode: Preference<ProxyDisplayMode>
    val proxyMode: Preference<Mode>
    val sheetHeightFraction: Preference<Float>
}

/** Contract for remote controller store consumed by runtime and feature modules. */
interface RemoteControllerStoreReader {
    val controllerEnabled: Preference<Boolean>
    val backends: Preference<List<RemoteBackend>>
    val activeBackendId: Preference<String>
    fun activeBackend(): RemoteBackend?
}

/** Priority level for proxy group synchronization scheduling. */
enum class ProxySyncPriority {
    OFF,
    SLOW,
    FAST,
}

/** Read-only contract for proxy group state and control actions. Implemented by [runtime:client]. */
interface ProxyGroupRepository {
    val proxyGroups: StateFlow<List<ProxyGroupInfo>>
    suspend fun selectProxy(group: String, proxyName: String): Boolean
    suspend fun forceSelectProxy(group: String, proxyName: String): Boolean
    suspend fun refreshProxyGroups(force: Boolean = false)
    suspend fun refreshProxyGroup(name: String, sort: ProxySort = ProxySort.Default)
    suspend fun healthCheck(group: String)
    suspend fun healthCheckAll()
    suspend fun healthCheckProxy(group: String, proxyName: String): Int
    fun warmUpProxyGroups()
    fun setProxyGroupSyncPriority(priority: ProxySyncPriority, source: String = "default")
}

/** Read-only contract for connection state and control. Implemented by [runtime:client]. */
interface ConnectionRepository {
    val connectionSnapshot: StateFlow<ConnectionSnapshot>
    val isRunning: StateFlow<Boolean>
    suspend fun queryConnections(): ConnectionSnapshot
    suspend fun queryConnectionsOverview(): ConnectionOverviewSnapshot
    suspend fun closeConnection(id: String): Boolean
    suspend fun closeAllConnections()
}

/** Read-only contract for runtime rules and temporary enable/disable control. */
interface RuntimeRuleRepository {
    suspend fun queryRules(): List<RuntimeRule>
    suspend fun setRuleDisabled(index: Int, disabled: Boolean): Boolean
}
