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
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.ConnectionOverviewSnapshot
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProviderList
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.RuntimeRule
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.runtime.api.service.remote.IClashManager
import com.github.yumelira.yumebox.runtime.api.service.remote.ILogObserver
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunDecode
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.LocalRuntimeSessionHelpers
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.SpecMode
import com.github.yumelira.yumebox.runtime.client.root.RootTunController
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
            localCall = { Clash.queryTunnelState() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryTunnelState(appContext) } },
            remoteCall = { remote.queryTunnelState() },
        )

    override suspend fun queryTrafficNow(): Long =
        dispatchSuspend(
            localCall = { if (!sessionHelpers.serviceRunning) 0L else Clash.queryTrafficNow() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryTrafficNow(appContext) } },
            remoteCall = { remote.queryTrafficNow() },
        )

    override suspend fun queryTrafficTotal(): Long =
        dispatchSuspend(
            localCall = { if (!sessionHelpers.serviceRunning) 0L else Clash.queryTrafficTotal() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryTrafficTotal(appContext) } },
            remoteCall = { remote.queryTrafficTotal() },
        )

    override suspend fun queryConnections(): ConnectionSnapshot =
        dispatchSuspend(
            localCall = { Clash.queryConnections() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryConnections(appContext) } },
            remoteCall = { remote.queryConnections() },
        )

    override suspend fun queryConnectionsOverview(): ConnectionOverviewSnapshot =
        dispatchSuspend(
            localCall = { Clash.queryConnectionsOverview() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryConnectionsOverview(appContext) } },
            remoteCall = { remote.queryConnectionsOverview() },
        )

    override suspend fun queryRules(): List<RuntimeRule> =
        dispatchSuspend(
            localCall = { Clash.queryRules() },
            rootCall = { Clash.queryRules() },
            remoteCall = { remote.queryRules() },
        )

    override suspend fun setRuleDisabled(index: Int, disabled: Boolean): Boolean =
        dispatchSuspend(
            localCall = { Clash.setRuleDisabled(index, disabled) },
            rootCall = { Clash.setRuleDisabled(index, disabled) },
            remoteCall = { remote.setRuleDisabled(index, disabled) },
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
            localCall = {
                withContext(Dispatchers.Default) {
                    val spec = activeRuntimeSpec() ?: return@withContext emptyList()
                    sessionHelpers.resolvedGroups(spec, excludeNotSelectable)
                }
            },
            rootCall = {
                withContext(Dispatchers.IO) {
                    RootTunController.queryAllProxyGroups(appContext, excludeNotSelectable)
                }
            },
            remoteCall = { remote.queryAllProxyGroups(excludeNotSelectable) },
        )

    override suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        dispatchSuspend(
            localCall = {
                withContext(Dispatchers.Default) {
                    val spec = activeRuntimeSpec() ?: return@withContext emptyList()
                    sessionHelpers.resolvedGroupNames(spec, excludeNotSelectable)
                }
            },
            rootCall = {
                withContext(Dispatchers.IO) { RootTunController.queryProxyGroupNames(appContext, excludeNotSelectable) }
            },
            remoteCall = { remote.queryProxyGroupNames(excludeNotSelectable) },
        )

    override suspend fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        dispatchSuspend(
            localCall = { Clash.queryGroup(name, proxySort) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryProxyGroup(appContext, name, proxySort) } },
            remoteCall = { remote.queryProxyGroup(name, proxySort) },
        )

    override suspend fun queryConfiguration(): UiConfiguration =
        dispatchSuspend(
            localCall = { Clash.queryConfiguration() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.queryConfiguration(appContext) } },
            remoteCall = { remote.queryConfiguration() },
        )

    override suspend fun queryProviders(): ProviderList {
        if (useRemote()) return remote.queryProviders()
        val providers =
            queryWithRuntimeSuspend(
                localCall = { ProviderList(Clash.queryProviders()).toList() },
                rootCall = { withContext(Dispatchers.IO) { RootTunController.queryProviders(appContext) } },
                fallbackOnRootFailure = false,
            )
        return ProviderList(providers)
    }

    override suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean =
        dispatch(
            localCall = { Clash.patchTunnelMode(mode) },
            rootCall = { Clash.patchTunnelMode(mode) },
            remoteCall = { remote.patchTunnelMode(mode) },
        )

    override suspend fun patchSelector(group: String, name: String): Boolean =
        dispatchSuspend(
            localCall = { Clash.patchSelector(group, name) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.patchSelector(appContext, group, name) } },
            remoteCall = { remote.patchSelector(group, name) },
        )

    override suspend fun patchForceSelector(group: String, name: String): Boolean =
        dispatchSuspend(
            localCall = { Clash.patchForceSelector(group, name) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.patchForceSelector(appContext, group, name) } },
            remoteCall = { remote.patchForceSelector(group, name) },
        )

    override suspend fun closeConnection(id: String): Boolean =
        dispatchSuspend(
            localCall = { Clash.closeConnection(id) },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.closeConnection(appContext, id) } },
            remoteCall = { remote.closeConnection(id) },
        )

    override suspend fun closeAllConnections() =
        dispatchSuspend(
            localCall = { Clash.closeAllConnections() },
            rootCall = { withContext(Dispatchers.IO) { RootTunController.closeAllConnections(appContext) } },
            remoteCall = { remote.closeAllConnections() },
        )

    override suspend fun healthCheck(group: String) =
        dispatchSuspend(
            localCall = {
                Timber.d("ClashManager healthCheck: group=%s", group)
                Clash.healthCheck(group).await()
            },
            rootCall = { RootTunController.healthCheck(appContext, group) },
            remoteCall = { remote.healthCheck(group) },
        )

    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        dispatchSuspend(
            localCall = {
                Timber.d("ClashManager healthCheckProxy: group=%s proxy=%s", group, proxyName)
                val json = Clash.healthCheckProxy(proxyName).await()
                val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(json)
                jsonElement.jsonObject["delay"]?.jsonPrimitive?.int ?: -1
            },
            rootCall = {
                val payload = RootTunController.healthCheckProxy(appContext, group, proxyName)
                val json = kotlinx.serialization.json.Json.parseToJsonElement(payload)
                json.jsonObject["delay"]?.jsonPrimitive?.int ?: -1
            },
            remoteCall = { remote.healthCheckProxy(group, proxyName) },
        )

    override suspend fun updateProvider(type: Provider.Type, name: String) =
        dispatchSuspend(
            localCall = { Clash.updateProvider(type, name).await() },
            rootCall = { RootTunController.updateProvider(appContext, type, name) },
            remoteCall = { remote.updateProvider(type, name) },
        )

    override suspend fun requestStop() =
        dispatchSuspend(
            localCall = {
                sessionHelpers.stopLocalServices(appContext.packageName)
                Unit
            },
            rootCall = { RootTunController.requestStop(appContext) },
            remoteCall = { remote.requestStop() },
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
                var lastPollMs = 0L
                PollingTimers.ticks(PollingTimerSpecs.RuntimeRootLogPolling).collect {
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    val isForeground = AppForegroundState.foreground.value
                    val isInteractive = (appContext.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true)
                    val minInterval = when {
                        !isInteractive -> PollingTimerSpecs.RootLogPolling.SCREEN_OFF_INTERVAL_MS
                        !isForeground -> PollingTimerSpecs.RootLogPolling.BACKGROUND_INTERVAL_MS
                        else -> 0L
                    }
                    if (nowMs - lastPollMs < minInterval) return@collect
                    lastPollMs = nowMs
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
                                }
                            }
                        }
                    }
            }
        }
    }

    private suspend fun localQueryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> {
        val profileUuid = sessionHelpers.activeProfileUuid ?: return emptyList()
        val spec = when (configuredRunMode()) {
            RunMode.Vpn -> sessionHelpers.createSpec(SpecMode.Tun)
            RunMode.Tun -> sessionHelpers.createSpec(SpecMode.RootTun)
        } ?: return emptyList()
        return sessionHelpers.resolvedGroups(spec, excludeNotSelectable, enrichLive = false)
    }

    private fun configuredRunMode(): RunMode {
        val raw =
            networkSettings.decodeString("runMode", RunMode.Vpn.name) ?: RunMode.Vpn.name
        return runCatching { RunMode.valueOf(raw) }.getOrDefault(RunMode.Vpn)
    }

    private fun activeRuntimeSpec(): RuntimeSpec? {
        val activeProfileUuid = sessionHelpers.activeProfileUuid ?: return null
        val spec = when (configuredRunMode()) {
            RunMode.Vpn -> sessionHelpers.createSpec(SpecMode.Tun)
            RunMode.Tun -> sessionHelpers.createSpec(SpecMode.RootTun)
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
        localCall: () -> T,
        rootCall: () -> T,
        remoteCall: () -> T,
        fallbackOnRootFailure: Boolean = false,
    ): T {
        if (useRemote()) return remoteCall()
        return queryWithRuntime(localCall, rootCall, fallbackOnRootFailure)
    }

    /** Suspend counterpart of [dispatch]; see [queryWithRuntimeSuspend]. */
    private suspend inline fun <T> dispatchSuspend(
        crossinline localCall: suspend () -> T,
        crossinline rootCall: suspend () -> T,
        crossinline remoteCall: suspend () -> T,
        fallbackOnRootFailure: Boolean = false,
    ): T {
        if (useRemote()) return remoteCall()
        return queryWithRuntimeSuspend({ localCall() }, { rootCall() }, fallbackOnRootFailure)
    }

    private inline fun <T> queryWithRuntime(
        localCall: () -> T,
        rootCall: () -> T,
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
        crossinline localCall: suspend () -> T,
        crossinline rootCall: suspend () -> T,
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
