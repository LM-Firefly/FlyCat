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
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.ILogObserver
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.root.RootTunRuntimeRecovery
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow
import timber.log.Timber

/**
 * Routing [IClashManager]: the remote External Controller wins when active, then the root
 * runtime while a root session is live, otherwise the in-process local core. All backend
 * behavior lives in [HttpClashManager] / [RootTunClashManager] / [LocalClashManager]; this
 * class only routes and handles root binder death.
 */
class ClashGateway(
    context: Context,
    private val remote: IClashManager,
    private val isRemoteControllerActive: () -> Boolean,
) : IClashManager {
    private val appContext = context.appContextOrSelf
    private val local = LocalClashManager(appContext)
    private val root = RootTunClashManager(appContext)

    private fun useRemote(): Boolean = isRemoteControllerActive()

    private fun useRootRuntime(): Boolean = RootTunStatusFlow.current(appContext).isSessionActive

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> route(call: (IClashManager) -> T): T {
        if (useRemote()) return call(remote)
        if (!useRootRuntime()) return call(local)
        return try {
            call(root)
        } catch (error: Throwable) { // fault barrier: root binder may die for any reason; recover then rethrow
            handleRootRuntimeFailure(error)
            throw error
        }
    }

    override fun queryTunnelState(): TunnelState = route { it.queryTunnelState() }

    override fun queryTrafficNow(): Long = route { it.queryTrafficNow() }

    override fun queryTrafficTotal(): Long = route { it.queryTrafficTotal() }

    override fun queryConnections(): ConnectionSnapshot = route { it.queryConnections() }

    /** Profile preview always resolves from the locally compiled config, even in root mode. */
    override fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        if (useRemote()) {
            remote.queryProfileProxyGroups(excludeNotSelectable)
        } else {
            local.queryProfileProxyGroups(excludeNotSelectable)
        }

    override fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        route { it.queryAllProxyGroups(excludeNotSelectable) }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        route { it.queryProxyGroupNames(excludeNotSelectable) }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        route { it.queryProxyGroup(name, proxySort) }

    override fun queryConfiguration(): UiConfiguration = route { it.queryConfiguration() }

    override fun queryProviders(): ProviderList = route { it.queryProviders() }

    override fun patchSelector(group: String, name: String): Boolean =
        route { it.patchSelector(group, name) }

    override fun closeConnection(id: String): Boolean = route { it.closeConnection(id) }

    override fun closeAllConnections() = route { it.closeAllConnections() }

    override suspend fun healthCheck(group: String) = route { it.healthCheck(group) }

    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        route { it.healthCheckProxy(group, proxyName) }

    override suspend fun updateProvider(type: Provider.Type, name: String) =
        route { it.updateProvider(type, name) }

    override fun requestStop() = route { it.requestStop() }

    override fun setLogObserver(observer: ILogObserver?) {
        if (useRemote()) {
            remote.setLogObserver(observer)
            return
        }
        if (useRootRuntime()) {
            local.setLogObserver(null)
            root.setLogObserver(observer)
        } else {
            root.setLogObserver(null)
            local.setLogObserver(observer)
        }
    }

    private fun handleRootRuntimeFailure(error: Throwable) {
        if (RootTunRuntimeRecovery.isBinderConnectionFailure(error)) {
            root.resetLogStream()
            RootTunRuntimeRecovery.handleBinderGone(
                appContext,
                RootTunRuntimeRecovery.binderFailureReason(error),
            )
            Timber.w(error, "Root runtime binder died")
            return
        }
        Timber.w(error, "Root runtime query failed")
    }
}
