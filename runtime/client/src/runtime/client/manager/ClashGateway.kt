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
import com.github.yumelira.yumebox.core.model.RuntimeRule
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.ILogObserver
import com.github.yumelira.yumebox.runtime.api.ILogSubscription
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logRoutingJob: Job? = null
    private var activeLogToken: Any? = null
    private var routedLogSubscription: ILogSubscription? = null

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
    override fun queryRules(): List<RuntimeRule> = pick().queryRules()
    override suspend fun setRuleDisabled(rule: RuntimeRule, disabled: Boolean): List<RuntimeRule> {
        val target = pick()
        return target.setRuleDisabled(rule, disabled)
    }
    override fun patchSelector(group: String, name: String): Boolean = pick().patchSelector(group, name)
    override fun closeConnection(id: String): Boolean = pick().closeConnection(id)
    override fun closeAllConnections() = pick().closeAllConnections()
    override suspend fun healthCheck(group: String) = pick().healthCheck(group)
    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        pick().healthCheckProxy(group, proxyName)
    override suspend fun updateProvider(type: Provider.Type, name: String) =
        pick().updateProvider(type, name)
    override fun requestStop() = pick().requestStop()

    @Synchronized
    override fun subscribeLogs(observer: ILogObserver): ILogSubscription {
        clearActiveLogSubscription()
        val token = Any()
        activeLogToken = token

        var target = pick()
        routedLogSubscription = target.subscribeLogs(observer)
        logRoutingJob =
            logScope.launch {
                while (isActive) {
                    delay(LOG_ROUTE_POLL_MS)
                    synchronized(this@ClashGateway) {
                        if (activeLogToken !== token) return@launch
                        val nextTarget = pick()
                        if (nextTarget !== target) {
                            runCatching { nextTarget.subscribeLogs(observer) }
                                .onSuccess { nextSubscription ->
                                    routedLogSubscription?.close()
                                    routedLogSubscription = nextSubscription
                                    target = nextTarget
                                }
                        }
                    }
                }
            }
        return ILogSubscription {
            synchronized(this@ClashGateway) {
                if (activeLogToken === token) clearActiveLogSubscription()
            }
        }
    }

    private fun clearActiveLogSubscription() {
        activeLogToken = null
        logRoutingJob?.cancel()
        logRoutingJob = null
        routedLogSubscription?.close()
        routedLogSubscription = null
    }

    private companion object {
        const val LOG_ROUTE_POLL_MS = 500L
    }
}
