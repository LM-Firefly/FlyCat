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

package com.github.yumelira.yumebox.runtime.client.remote

import android.content.Context
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProviderList
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.uds.UdsCallbackHandler
import com.github.yumelira.yumebox.core.uds.UdsClashEngine
import com.github.yumelira.yumebox.core.uds.UdsEventSubscriber
import com.github.yumelira.yumebox.core.uds.UdsProcessManager
import com.github.yumelira.yumebox.runtime.api.service.remote.IClashManager
import com.github.yumelira.yumebox.runtime.api.service.remote.ILogObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [IClashManager] implementation that delegates to [UdsClashEngine].
 *
 * This replaces the direct [Clash] singleton calls in [ClashGateway] when
 * the UDS transport mode is enabled. It manages the [UdsProcessManager]
 * lifecycle and provides the same interface as [HttpClashManager].
 */
class UdsClashManager(
    private val context: Context,
) : IClashManager {

    private val processManager = UdsProcessManager(context)
    private var engine: UdsClashEngine? = null

    /**
     * Starts the Go UDS server and initialises the engine.
     *
     * @param home mihomo home directory
     * @param versionName app version name
     * @param sdkVersion Android SDK version
     */
    suspend fun start(home: String, versionName: String, sdkVersion: Int = 0) {
        val conn = processManager.start(home, versionName, sdkVersion = sdkVersion)
        engine = UdsClashEngine(conn) { processManager.getEventSubscriber() }
        Timber.tag(TAG).i("UdsClashManager started")
    }

    /**
     * Stops the Go UDS server.
     */
    fun stop() {
        engine = null
        processManager.stop()
        Timber.tag(TAG).i("UdsClashManager stopped")
    }

    /**
     * Returns true if the UDS server is running.
     */
    fun isRunning(): Boolean = processManager.isRunning()

    /**
     * Returns the underlying [UdsProcessManager] for advanced operations.
     */
    fun getProcessManager(): UdsProcessManager = processManager

    /**
     * Returns the event subscriber for receiving pushed events (traffic, state, log).
     */
    fun getEventSubscriber(): UdsEventSubscriber? = processManager.getEventSubscriber()

    /**
     * Returns the callback handler for reverse socket owner queries.
     */
    fun getCallbackHandler(): UdsCallbackHandler? = processManager.getCallbackHandler()

    // ─── IClashManager implementation ─────────────────────────────────────────

    override suspend fun queryTunnelState(): TunnelState = withContext(Dispatchers.IO) {
        requireEngine().queryTunnelState()
    }

    override suspend fun queryTrafficNow(): Long = withContext(Dispatchers.IO) {
        requireEngine().queryTrafficNow()
    }

    override suspend fun queryTrafficTotal(): Long = withContext(Dispatchers.IO) {
        requireEngine().queryTrafficTotal()
    }

    override suspend fun queryConnections(): ConnectionSnapshot = withContext(Dispatchers.IO) {
        requireEngine().queryConnections()
    }

    override suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            requireEngine().queryGroupNames(excludeNotSelectable)
        }

    override suspend fun queryProfileProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            requireEngine().queryGroupNames(excludeNotSelectable)
        }

    override suspend fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        withContext(Dispatchers.IO) {
            // In UDS mode, we query all groups and filter.
            requireEngine().queryGroupNames(excludeNotSelectable).map { name ->
                requireEngine().queryGroup(name, ProxySort.Title)
            }
        }

    override suspend fun queryActiveProfileTunRouteExcludeAddress(): List<String> {
        // Not available via UDS — return empty.
        return emptyList()
    }

    override suspend fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        withContext(Dispatchers.IO) {
            requireEngine().queryGroupNames(excludeNotSelectable).map { name ->
                requireEngine().queryGroup(name, ProxySort.Title)
            }
        }

    override suspend fun queryProxyGroup(name: String, sort: ProxySort): ProxyGroup =
        withContext(Dispatchers.IO) {
            requireEngine().queryGroup(name, sort)
        }

    override suspend fun patchSelector(group: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            requireEngine().patchSelector(group, name)
        }

    override suspend fun closeConnection(id: String): Boolean = withContext(Dispatchers.IO) {
        requireEngine().closeConnection(id)
    }

    override suspend fun closeAllConnections() = withContext(Dispatchers.IO) {
        requireEngine().closeAllConnections()
    }

    override suspend fun healthCheck(group: String) = withContext(Dispatchers.IO) {
        requireEngine().healthCheck(group).await()
    }

    private suspend fun healthCheckAll() = withContext(Dispatchers.IO) {
        requireEngine().healthCheckAll()
    }

    override suspend fun healthCheckProxy(group: String, proxyName: String): Int = withContext(Dispatchers.IO) {
        requireEngine().healthCheckProxy(proxyName).await().toIntOrNull() ?: 0
    }

    override suspend fun queryProviders(): ProviderList = withContext(Dispatchers.IO) {
        ProviderList(requireEngine().queryProviders())
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) = withContext(Dispatchers.IO) {
        requireEngine().updateProvider(type, name).await()
    }

    override suspend fun queryConfiguration(): UiConfiguration = withContext(Dispatchers.IO) {
        requireEngine().queryConfiguration()
    }

    override suspend fun patchForceSelector(group: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            requireEngine().patchForceSelector(group, name)
        }

    override suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean =
        withContext(Dispatchers.IO) {
            requireEngine().patchTunnelMode(mode)
        }

    override suspend fun requestStop() {
        // Handled externally via processManager.stop()
    }

    override fun setLogObserver(observer: ILogObserver?) {
        // UDS mode uses subscribeLogcat() channel instead; observer not applicable.
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun requireEngine(): UdsClashEngine {
        return engine ?: throw IllegalStateException("UdsClashManager not started — call start() first")
    }

    companion object {
        private const val TAG = "UdsClashManager"
    }
}
