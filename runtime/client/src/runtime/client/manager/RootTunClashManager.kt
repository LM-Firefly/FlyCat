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
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProviderList
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.ILogObserver
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.client.root.RootTunController
import com.github.yumelira.yumebox.runtime.service.root.rootTunDecode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * [IClashManager] over the root-process runtime via the AIDL controller. Non-suspend interface
 * members bridge with runBlocking — inherited from the sync seam; never call on the main thread.
 * Log streaming polls `queryRecentLogs` because logcat channels cannot cross the binder.
 */
internal class RootTunClashManager(context: Context) : IClashManager {
    private val appContext = context.appContextOrSelf
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var logJob: Job? = null
    private var logSeq: Long = 0L

    override fun queryTunnelState(): TunnelState =
        runBlocking { RootTunController.queryTunnelState(appContext) }

    override fun queryTrafficNow(): Long =
        runBlocking { RootTunController.queryTrafficNow(appContext) }

    override fun queryTrafficTotal(): Long =
        runBlocking { RootTunController.queryTrafficTotal(appContext) }

    override fun queryConnections(): ConnectionSnapshot =
        runBlocking { RootTunController.queryConnections(appContext) }

    /** Profile preview resolves from the local compiled config; the router never sends it here. */
    override fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        emptyList()

    override fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        runBlocking { RootTunController.queryAllProxyGroups(appContext, excludeNotSelectable) }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        runBlocking { RootTunController.queryProxyGroupNames(appContext, excludeNotSelectable) }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        runBlocking { RootTunController.queryProxyGroup(appContext, name, proxySort) }

    override fun queryConfiguration(): UiConfiguration =
        runBlocking { RootTunController.queryConfiguration(appContext) }

    override fun queryProviders(): ProviderList =
        ProviderList(runBlocking { RootTunController.queryProviders(appContext) })

    override fun patchSelector(group: String, name: String): Boolean =
        runBlocking { RootTunController.patchSelector(appContext, group, name) }

    override fun closeConnection(id: String): Boolean =
        runBlocking { RootTunController.closeConnection(appContext, id) }

    override fun closeAllConnections() =
        runBlocking { RootTunController.closeAllConnections(appContext) }

    override suspend fun healthCheck(group: String) =
        RootTunController.healthCheck(appContext, group)

    override suspend fun healthCheckProxy(group: String, proxyName: String): Int {
        val payload = RootTunController.healthCheckProxy(appContext, group, proxyName)
        return Json.parseToJsonElement(payload).jsonObject["delay"]?.jsonPrimitive?.int ?: -1
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) =
        RootTunController.updateProvider(appContext, type, name)

    override fun requestStop() = runBlocking { RootTunController.requestStop(appContext) }

    override fun setLogObserver(observer: ILogObserver?) {
        logJob?.cancel()
        if (observer == null) {
            logJob = null
            logSeq = 0L
            return
        }
        logJob = scope.launch {
            PollingTimers.ticks(PollingTimerSpecs.RuntimeRootLogPolling).collect {
                runCatching {
                        val chunk = RootTunController.queryRecentLogs(appContext, logSeq)
                        chunk.items.forEach { raw ->
                            observer.newItem(rootTunDecode<LogMessage>(raw))
                        }
                        logSeq = chunk.nextSeq
                    }
                    .onFailure { error -> Timber.d(error, "Root runtime log polling skipped") }
            }
        }
    }

    /** Called by the router when the root binder dies so a stale polling loop does not linger. */
    fun resetLogStream() {
        logJob?.cancel()
        logJob = null
        logSeq = 0L
    }
}
