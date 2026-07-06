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
import com.github.yumelira.yumebox.core.Clash
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
import com.github.yumelira.yumebox.core.util.TrafficSampleCache
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.client.root.RootTunController
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.runtime.api.service.remote.IClashManager
import com.github.yumelira.yumebox.runtime.api.service.remote.ILogObserver
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunDecode
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.LocalRuntimeSessionHelpers
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.SpecMode
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Single gateway for the mihomo control surface. Dispatches between the remote External Controller,
 * the root runtime, and the in-process local core, with the local branch driving [Clash] (and the
 * proxy-group resolver) directly — there is no separate `ClashManager` delegation layer.
 */
class ClashGateway(
    context: Context,
    private val remote: IClashManager,
    private val isRemoteControllerActive: () -> Boolean,
    private val sessionHelpers: LocalRuntimeSessionHelpers =
        requireNotNull(com.github.yumelira.yumebox.runtime.api.service.RuntimeServiceContractRegistry.localRuntimeSessionHelpers) {
            "LocalRuntimeSessionHelpers not registered in RuntimeServiceContractRegistry"
        },
    private val rootTunRecovery: RootTunRuntimeRecoveryContract =
        requireNotNull(com.github.yumelira.yumebox.runtime.api.service.RuntimeServiceContractRegistry.rootTunRuntimeRecovery) {
            "RootTunRuntimeRecoveryContract not registered in RuntimeServiceContractRegistry"
        },
) : IClashManager {
    private val appContext = context.appContextOrSelf
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var rootLogJob: Job? = null
    private var rootLogSeq: Long = 0L

    private val networkSettings = MMKV.mmkvWithID("network_settings", MMKV.MULTI_PROCESS_MODE)
    private var logReceiver: ReceiveChannel<LogMessage>? = null

    private fun useRemote(): Boolean = isRemoteControllerActive()

    override suspend fun queryTunnelState(): TunnelState =
        dispatchSuspend(
            remoteCall = { remote.queryTunnelState() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryTunnelState(appContext) } },
            localCall = { Clash.queryTunnelState() },
        )

    override suspend fun queryTrafficNow(): Long =
        dispatchSuspend(
            remoteCall = { remote.queryTrafficNow() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryTrafficNow(appContext) } },
            localCall = {
                if (!sessionHelpers.serviceRunning) 0L else Clash.queryTrafficNow()
            },
        ).also { TrafficSampleCache.updateNow(it) }

    override suspend fun queryTrafficTotal(): Long =
        dispatchSuspend(
            remoteCall = { remote.queryTrafficTotal() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryTrafficTotal(appContext) } },
            localCall = {
                if (!sessionHelpers.serviceRunning) 0L else Clash.queryTrafficTotal()
            },
        ).also { TrafficSampleCache.updateTotal(it) }

    override suspend fun queryConnections(): ConnectionSnapshot =
        dispatchSuspend(
            remoteCall = { remote.queryConnections() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryConnections(appContext) } },
            localCall = { Clash.queryConnections() },
        )

    override suspend fun queryProfileProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        if (useRemote()) return remote.queryProfileProxyGroupNames(excludeNotSelectable)
        return localQueryProfileProxyGroups(excludeNotSelectable).map(ProxyGroup::name)
    }

    override suspend fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> {
        if (useRemote()) return remote.queryProfileProxyGroups(excludeNotSelectable)
        return localQueryProfileProxyGroups(excludeNotSelectable)
    }

    override suspend fun queryActiveProfileTunRouteExcludeAddress(): List<String> {
        if (useRemote()) return remote.queryActiveProfileTunRouteExcludeAddress()
        val profileUuid = sessionHelpers.activeProfileUuid ?: return emptyList()
        return sessionHelpers.previewTunRouteExcludeAddress(profileUuid)
    }

    override suspend fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        dispatchSuspend(
            remoteCall = { remote.queryAllProxyGroups(excludeNotSelectable) },
            rootCall = {
                withContext(Dispatchers.IO) {
                    RootTunController.queryAllProxyGroups(appContext, excludeNotSelectable)
                }
            },
            localCall = {
                withContext(Dispatchers.Default) {
                    val spec = activeRuntimeSpec() ?: return@withContext emptyList()
                    sessionHelpers.resolvedGroups(spec, excludeNotSelectable)
                }
            },
        )

    override suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        dispatchSuspend(
            remoteCall = { remote.queryProxyGroupNames(excludeNotSelectable) },
            rootCall = {
                withContext(Dispatchers.IO) {
                    RootTunController.queryProxyGroupNames(appContext, excludeNotSelectable)
                }
            },
            localCall = {
                withContext(Dispatchers.Default) {
                    val spec = activeRuntimeSpec() ?: return@withContext emptyList()
                    sessionHelpers.resolvedGroupNames(spec, excludeNotSelectable)
                }
            },
        )

    override suspend fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        dispatchSuspend(
            remoteCall = { remote.queryProxyGroup(name, proxySort) },
            rootCall = {
                withContext(Dispatchers.IO) { RootTunController.queryProxyGroup(appContext, name, proxySort) }
            },
            localCall = { Clash.queryGroup(name, proxySort) },
        )

    override suspend fun queryConfiguration(): UiConfiguration =
        dispatchSuspend(
            remoteCall = { remote.queryConfiguration() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryConfiguration(appContext) } },
            localCall = { Clash.queryConfiguration() },
        )

    override suspend fun queryProviders(): ProviderList {
        if (useRemote()) return remote.queryProviders()
        val providers =
            queryWithRuntimeSuspend(
                rootCall = { withContext(Dispatchers.IO) { RootTunController.queryProviders(appContext) } },
                localCall = { ProviderList(Clash.queryProviders()).toList() },
                fallbackOnRootFailure = false,
            )
        return ProviderList(providers)
    }

    override suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean =
        dispatch(
            remoteCall = { remote.patchTunnelMode(mode) },
            rootCall = { Clash.patchTunnelMode(mode) },
            localCall = { Clash.patchTunnelMode(mode) },
        )

    override suspend fun patchSelector(group: String, name: String): Boolean =
        dispatchSuspend(
            remoteCall = { remote.patchSelector(group, name) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.patchSelector(appContext, group, name) } },
            localCall = { Clash.patchSelector(group, name) },
        )

    override suspend fun patchForceSelector(group: String, name: String): Boolean =
        dispatchSuspend(
            remoteCall = { remote.patchForceSelector(group, name) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.patchForceSelector(appContext, group, name) } },
            localCall = { Clash.patchForceSelector(group, name) },
        )

    override suspend fun closeConnection(id: String): Boolean =
        dispatchSuspend(
            remoteCall = { remote.closeConnection(id) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.closeConnection(appContext, id) } },
            localCall = { Clash.closeConnection(id) },
        )

    override suspend fun closeAllConnections() =
        dispatchSuspend(
            remoteCall = { remote.closeAllConnections() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.closeAllConnections(appContext) } },
            localCall = { Clash.closeAllConnections() },
        )

    override suspend fun healthCheck(group: String) =
        dispatchSuspend(
            remoteCall = { remote.healthCheck(group) },
            rootCall = { RootTunController.healthCheck(appContext, group) },
            localCall = {
                Timber.d("ClashManager healthCheck: group=%s", group)
                Clash.healthCheck(group).await()
            },
        )

    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        dispatchSuspend(
            remoteCall = { remote.healthCheckProxy(group, proxyName) },
            rootCall = {
                val payload = RootTunController.healthCheckProxy(appContext, group, proxyName)
                val json = kotlinx.serialization.json.Json.parseToJsonElement(payload)
                json.jsonObject["delay"]?.jsonPrimitive?.int ?: -1
            },
            localCall = {
                Timber.d("ClashManager healthCheckProxy: group=%s proxy=%s", group, proxyName)
                val json = Clash.healthCheckProxy(proxyName).await()
                val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(json)
                jsonElement.jsonObject["delay"]?.jsonPrimitive?.int ?: -1
            },
        )

    override suspend fun updateProvider(type: Provider.Type, name: String) =
        dispatchSuspend(
            remoteCall = { remote.updateProvider(type, name) },
            rootCall = { RootTunController.updateProvider(appContext, type, name) },
            localCall = { Clash.updateProvider(type, name).await() },
        )

    override fun requestStop() =
        dispatch(
            remoteCall = { remote.requestStop() },
            rootCall = { runBlocking { RootTunController.requestStop(appContext) } },
            localCall = {
                sessionHelpers.stopLocalServices(appContext.packageName)
                sessionHelpers.stopLocalHttpProxy()
                Unit
            },
        )

    override fun setLogObserver(observer: ILogObserver?) {
        if (useRemote()) {
            remote.setLogObserver(observer)
            return
        }
        if (useRootRuntime()) {
            setLocalLogObserver(null)
            rootLogJob?.cancel()
            if (observer == null) {
                rootLogSeq = 0L
                return
            }
            rootLogJob = scope.launch {
                PollingTimers.ticks(PollingTimerSpecs.RuntimeRootLogPolling).collect {
                    runCatching {
                            val chunk = RootTunController.queryRecentLogs(appContext, rootLogSeq)
                            if (chunk.items.isNotEmpty()) {
                                chunk.items.forEach { raw ->
                                    observer.newItem(rootTunDecode<LogMessage>(raw))
                                }
                            }
                            rootLogSeq = chunk.nextSeq
                        }
                        .onFailure { error -> Timber.d(error, "Root runtime log polling skipped") }
                }
            }
        } else {
            rootLogJob?.cancel()
            rootLogSeq = 0L
            setLocalLogObserver(observer)
        }
    }

    /** In-process logcat subscription (formerly `ClashManager.setLogObserver`). */
    private fun setLocalLogObserver(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()

                Clash.forceGc()
            }

            if (observer != null) {
                logReceiver =
                    Clash.subscribeLogcat().also { receiver ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                while (isActive) {
                                    observer.newItem(receiver.receive())
                                }
                            } catch (_: CancellationException) {} catch (error: Exception) {
                                Timber.w("UI crashed", error)
                            } finally {
                                withContext(NonCancellable) {
                                    receiver.cancel()

                                    Clash.forceGc()
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun localQueryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> {
        val profileUuid = sessionHelpers.activeProfileUuid ?: return emptyList()
        val spec = when (configuredProxyMode()) {
            ProxyMode.RootTun -> sessionHelpers.createSpec(SpecMode.RootTun)
            ProxyMode.Http -> sessionHelpers.createSpec(SpecMode.Http)
            ProxyMode.Tun -> sessionHelpers.createSpec(SpecMode.Tun)
        } ?: return emptyList()
        return runBlocking(Dispatchers.Default) {
            sessionHelpers.resolvedGroups(spec, excludeNotSelectable, enrichLive = false)
        }
    }

    private fun configuredProxyMode(): ProxyMode {
        val raw =
            networkSettings.decodeString("proxyMode", ProxyMode.Tun.name) ?: ProxyMode.Tun.name
        return runCatching { ProxyMode.valueOf(raw) }.getOrDefault(ProxyMode.Tun)
    }

    private fun activeRuntimeSpec(): RuntimeSpec? {
        val activeProfileUuid = sessionHelpers.activeProfileUuid ?: return null
        val spec = when (configuredProxyMode()) {
            ProxyMode.RootTun -> sessionHelpers.createSpec(SpecMode.RootTun)
            ProxyMode.Http -> sessionHelpers.createSpec(SpecMode.Http)
            ProxyMode.Tun -> sessionHelpers.createSpec(SpecMode.Tun)
        }
        return spec?.takeIf { it.profileUuid == activeProfileUuid }
    }

    private fun useRootRuntime(): Boolean {
        val status = RootTunStatusFlow.current(appContext)
        return status.state.isActiveOrStopping || status.runtimeReady
    }

    /**
     * Non-suspend dispatch: remote controller wins when active, otherwise route between the root
     * runtime and the local service via [queryWithRuntime]. Mirrors the per-method
     * `if (useRemote()) ... else queryWithRuntime(...)` shape so the public overrides stay terse.
     */
    private inline fun <T> dispatch(
        remoteCall: () -> T,
        rootCall: () -> T,
        localCall: () -> T,
        fallbackOnRootFailure: Boolean = false,
    ): T {
        if (useRemote()) return remoteCall()
        return queryWithRuntime(rootCall, localCall, fallbackOnRootFailure)
    }

    /** Suspend counterpart of [dispatch]; see [queryWithRuntimeSuspend]. */
    private suspend inline fun <T> dispatchSuspend(
        crossinline remoteCall: suspend () -> T,
        crossinline rootCall: suspend () -> T,
        crossinline localCall: suspend () -> T,
        fallbackOnRootFailure: Boolean = false,
    ): T {
        if (useRemote()) return remoteCall()
        return queryWithRuntimeSuspend({ rootCall() }, { localCall() }, fallbackOnRootFailure)
    }

    private inline fun <T> queryWithRuntime(
        rootCall: () -> T,
        localCall: () -> T,
        fallbackOnRootFailure: Boolean = true,
    ): T {
        if (!useRootRuntime()) {
            return localCall()
        }
        return try {
            rootCall()
        } catch (error: Throwable) {
            handleRootRuntimeFailure(error)
            if (fallbackOnRootFailure) localCall() else throw error
        }
    }

    private suspend inline fun <T> queryWithRuntimeSuspend(
        crossinline rootCall: suspend () -> T,
        crossinline localCall: suspend () -> T,
        fallbackOnRootFailure: Boolean = true,
    ): T {
        if (!useRootRuntime()) {
            return localCall()
        }
        return try {
            rootCall()
        } catch (error: Throwable) {
            handleRootRuntimeFailure(error)
            if (fallbackOnRootFailure) localCall() else throw error
        }
    }

    private fun handleRootRuntimeFailure(error: Throwable) {
        if (rootTunRecovery.isBinderConnectionFailure(error)) {
            rootLogJob?.cancel()
            rootLogJob = null
            rootLogSeq = 0L
            rootTunRecovery.handleBinderGone(
                appContext,
                rootTunRecovery.binderFailureReason(error),
            )
            Timber.w(error, "Root runtime binder died")
            return
        }
        Timber.w(error, "Root runtime query failed")
    }
}
