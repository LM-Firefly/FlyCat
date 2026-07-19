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

package com.github.yumelira.yumebox.runtime.client.manager

import android.content.Context
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProviderList
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.ILogObserver
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import com.github.yumelira.yumebox.runtime.service.manager.HttpClashManager

/**
 * Routing [IClashManager]: the remote External Controller wins when active, otherwise the local
 * out-of-process core over its `external-controller-unix` socket. Both are [HttpClashManager] — the
 * run mode only affects how the core was launched, not how it is queried.
 */
class ClashGateway(
    context: Context,
    private val remote: IClashManager,
    private val isRemoteControllerActive: () -> Boolean,
) : IClashManager {
    private val appContext = context.appContextOrSelf
    private val local: IClashManager = CoreProcess.rest(appContext)

    private fun pick(): IClashManager = if (isRemoteControllerActive()) remote else local

    override fun queryTunnelState(): TunnelState = pick().queryTunnelState()
    override fun queryTrafficNow(): Long = pick().queryTrafficNow()
    override fun queryTrafficTotal(): Long = pick().queryTrafficTotal()
    override fun queryConnections(): ConnectionSnapshot = pick().queryConnections()
    override fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        pick().queryProfileProxyGroups(excludeNotSelectable)
    override fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        pick().queryAllProxyGroups(excludeNotSelectable)
    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        pick().queryProxyGroupNames(excludeNotSelectable)
    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        pick().queryProxyGroup(name, proxySort)
    override fun queryConfiguration(): UiConfiguration = pick().queryConfiguration()
    override fun queryProviders(): ProviderList = pick().queryProviders()
    override fun patchSelector(group: String, name: String): Boolean = pick().patchSelector(group, name)
    override fun closeConnection(id: String): Boolean = pick().closeConnection(id)
    override fun closeAllConnections() = pick().closeAllConnections()
    override suspend fun healthCheck(group: String) = pick().healthCheck(group)
    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        pick().healthCheckProxy(group, proxyName)
    override suspend fun updateProvider(type: Provider.Type, name: String) =
        pick().updateProvider(type, name)
    override fun requestStop() = pick().requestStop()
    override fun setLogObserver(observer: ILogObserver?) = pick().setLogObserver(observer)
}
