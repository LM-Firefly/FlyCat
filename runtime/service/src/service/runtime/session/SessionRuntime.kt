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

package com.github.yumelira.yumebox.runtime.service.runtime.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.ConnectionOverviewSnapshot
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunEncode
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.service.ServiceNetworkObserver
import com.github.yumelira.yumebox.runtime.service.ServicePowerController
import com.github.yumelira.yumebox.runtime.service.runtime.records.SelectionDao
import com.github.yumelira.yumebox.runtime.service.runtime.records.SelectionRestoreExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import timber.log.Timber
import java.util.TimeZone
import java.util.UUID
import kotlin.math.min

// Established runtime session seam (owner-token lifecycle + snapshot + core control); splitting
// is tracked separately.
@Suppress("LargeClass")
class SessionRuntime(
    private val host: RuntimeHost,
    private val transport: RuntimeTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val screenOn: StateFlow<Boolean> = MutableStateFlow(true),
    private val powerController: ServicePowerController? = null,
) {
    private val compiledConfigPipeline = CompiledConfigPipeline(host.context.appContextOrSelf)
    private val proxyGroupResolver = RuntimeProxyGroupResolver(compiledConfigPipeline)
    private val lock = Mutex()
    private val snapshotLock = Any()

    @Volatile private var interruptReason: String? = null
    private var currentSpec: RuntimeSpec? = null
    @Volatile private var currentSnapshot: RuntimeSnapshot = RuntimeSnapshot(targetMode = host.mode.toRuntimeTargetMode())
    private var networkObserver: ServiceNetworkObserver? = null
    private var timeZoneReceiver: BroadcastReceiver? = null
    private val queryCache = SessionRuntimeQueryCache()
    private val telemetry =
        SessionRuntimeTelemetry(host = host, scope = scope) { ready ->
            publishSnapshot(currentSnapshot.copy(logReady = ready))
        }
    val telemetryTrafficNow: StateFlow<Long> = telemetry.trafficNow
    val telemetryTrafficTotal: StateFlow<Long> = telemetry.trafficTotal

    suspend fun start(spec: RuntimeSpec): RuntimeOperationResult =
        lock.withLock {
            clearInterruptRequest()
            try {
                    stopInternal(reason = null, notifyHost = false)
                    startupLog(spec, "session: start begin")
                    startInternal(spec)
                    RuntimeOperationResult(success = true)
                }
                catch (e: CancellationException) {
                    startupLog(spec, "session: start cancelled")
                    throw e
                }
                catch (error: Exception) {
                    if (error is RuntimeInterruptedException) {
                        startupLog(spec, "session: start interrupted reason=${error.message}")
                        RuntimeOperationResult(success = true)
                    } else {
                        rollback(spec, error.message ?: "start runtime failed")
                        RuntimeOperationResult(
                            success = false,
                            error = error.message ?: "start runtime failed",
                        )
                    }
                }
        }

    suspend fun reload(spec: RuntimeSpec): RuntimeOperationResult =
        lock.withLock {
            clearInterruptRequest()
            try {
                    startupLog(spec, "session: reload begin")
                    reloadInternal(spec)
                    RuntimeOperationResult(success = true)
                }
                catch (e: CancellationException) {
                    startupLog(spec, "session: reload cancelled")
                    throw e
                }
                catch (error: Exception) {
                    if (error is RuntimeInterruptedException) {
                        startupLog(spec, "session: reload interrupted reason=${error.message}")
                        RuntimeOperationResult(success = true)
                    } else {
                        startupLog(spec, "failed=${error.message ?: "reload runtime failed"}")
                        RuntimeOperationResult(
                            success = false,
                            error = error.message ?: "reload runtime failed",
                        )
                    }
                }
        }

    suspend fun restart(spec: RuntimeSpec): RuntimeOperationResult =
        lock.withLock {
            clearInterruptRequest()
            try {
                    stopInternal(reason = null, notifyHost = false)
                    startupLog(spec, "session: restart begin")
                    startInternal(spec)
                    RuntimeOperationResult(success = true)
                }
                catch (e: CancellationException) {
                    startupLog(spec, "session: restart cancelled")
                    throw e
                }
                catch (error: Exception) {
                    if (error is RuntimeInterruptedException) {
                        startupLog(spec, "session: restart interrupted reason=${error.message}")
                        RuntimeOperationResult(success = true)
                    } else {
                        rollback(spec, error.message ?: "restart runtime failed")
                        RuntimeOperationResult(
                            success = false,
                            error = error.message ?: "restart runtime failed",
                        )
                    }
                }
        }

    suspend fun stop(reason: String? = null): RuntimeOperationResult {
        requestStop(reason)
        return lock.withLock {
            runCatching {
                    stopInternal(reason = reason, notifyHost = true)
                    RuntimeOperationResult(success = true)
                }
                .getOrElse { error ->
                    RuntimeOperationResult(
                        success = false,
                        error = error.message ?: "stop runtime failed",
                    )
                }
        }
    }

    fun requestStop(reason: String? = null) {
        interruptReason = reason ?: "runtime stop requested"
    }

    suspend fun destroy() {
        requestStop("runtime destroyed")
        lock.withLock {
            runCatching { stopInternal(reason = "runtime destroyed", notifyHost = false) }
        }
        scope.cancel()
    }

    fun snapshot(): RuntimeSnapshot = currentSnapshot

    fun queryTunnelState(): TunnelState =
        if (currentSnapshot.phase == RuntimePhase.Running) {
            Clash.queryTunnelState()
        } else {
            TunnelState(TunnelState.Mode.Rule)
        }

    fun queryTrafficNow(): Long =
        if (currentSnapshot.phase == RuntimePhase.Running) {
            Clash.queryTrafficNow().also {
                queryCache.updateTrafficNow(it)
                publishSnapshot(currentSnapshot.copy(trafficReady = true))
            }
        } else {
            0L
        }

    fun queryTrafficTotal(): Long =
        if (currentSnapshot.phase == RuntimePhase.Running) {
            Clash.queryTrafficTotal().also {
                queryCache.updateTrafficTotal(it)
                publishSnapshot(currentSnapshot.copy(trafficReady = true))
            }
        } else {
            0L
        }

    fun queryConnections(): ConnectionSnapshot {
        if (currentSnapshot.phase != RuntimePhase.Running) return ConnectionSnapshot()
        return Clash.queryConnections()
    }

    fun queryConnectionsOverview(): ConnectionOverviewSnapshot {
        if (currentSnapshot.phase != RuntimePhase.Running) return ConnectionOverviewSnapshot()
        return Clash.queryConnectionsOverview()
    }

    suspend fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> {
        if (currentSnapshot.phase != RuntimePhase.Running) return emptyList()
        val groups =
            runCatching { resolveRuntimeProxyGroups(excludeNotSelectable) }
                .getOrElse {
                    if (excludeNotSelectable) {
                        val selectable = Clash.queryGroupNames(true).toSet()
                        ensureRuntimeSnapshot().proxyGroups.filter { selectable.contains(it.name) }
                    } else {
                        ensureRuntimeSnapshot().proxyGroups
                    }
                }
        queryCache.replaceProxyGroups(groups)
        publishSnapshot(currentSnapshot.copy(groupsReady = groups.isNotEmpty()))
        return groups
    }

    suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        if (currentSnapshot.phase != RuntimePhase.Running) return emptyList()
        return runCatching { resolveRuntimeProxyGroupNames(excludeNotSelectable) }
            .getOrElse { queryAllProxyGroups(excludeNotSelectable).map { it.name } }
    }

    fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        if (currentSnapshot.phase != RuntimePhase.Running) {
            error("runtime not running")
        }
        val group = Clash.queryGroup(name, proxySort)
        if (proxySort == ProxySort.Default && group.name.isNotBlank()) {
            queryCache.upsertProxyGroup(name, group)
        }
        return group
    }

    suspend fun queryConfiguration(): UiConfiguration {
        if (currentSnapshot.phase != RuntimePhase.Running) return UiConfiguration()
        return ensureRuntimeSnapshot().configuration
    }

    fun queryProviders(): List<Provider> {
        if (currentSnapshot.phase != RuntimePhase.Running) return emptyList()
        return runCatching { Clash.queryProviders() }.getOrDefault(emptyList())
    }

    fun patchSelector(group: String, name: String): Boolean {
        val profileUuid = currentSnapshot.profileUuid?.let(UUID::fromString)
        return Clash.patchSelector(group, name).also { patched ->
            if (!patched) {
                profileUuid?.let { SelectionDao.remove(it, group) }
                return@also
            }

            if (
                currentSnapshot.phase == RuntimePhase.Running ||
                    currentSnapshot.phase == RuntimePhase.Starting
            ) {
                val refreshedGroup = refreshRuntimeProxyGroup(group)
                if (refreshedGroup?.type == Proxy.Type.Selector) {
                    profileUuid?.let { SelectionDao.upsertManualSelection(it, group, name) }
                } else {
                    profileUuid?.let { SelectionDao.remove(it, group) }
                }
            }
        }
    }

    fun patchForceSelector(group: String, name: String): Boolean {
        val profileUuid = currentSnapshot.profileUuid?.let(UUID::fromString)
        return Clash.patchForceSelector(group, name).also { patched ->
            var supportsPinnedSelection = false
            if (currentSnapshot.phase == RuntimePhase.Running || currentSnapshot.phase == RuntimePhase.Starting) {
                val refreshedGroup = refreshRuntimeProxyGroup(group)
                supportsPinnedSelection = refreshedGroup?.let {
                    it.type == Proxy.Type.URLTest || it.type == Proxy.Type.Fallback
                } == true
            }
            SelectionDao.persistForcePinnedSelection(
                profileUUID = profileUuid,
                proxyGroup = group,
                requestedNode = name,
                patched = patched,
                supportsPinnedSelection = supportsPinnedSelection,
            )
        }
    }

    fun closeConnection(id: String): Boolean {
        if (currentSnapshot.phase != RuntimePhase.Running) return false
        return Clash.closeConnection(id)
    }

    fun closeAllConnections() {
        if (currentSnapshot.phase != RuntimePhase.Running) return
        Clash.closeAllConnections()
    }

    suspend fun healthCheck(group: String): String? {
        Timber.d(
            "SessionRuntime healthCheck: group=%s phase=%s owner=%s",
            group,
            currentSnapshot.phase,
            currentSnapshot.owner,
        )
        return runCatching {
                Clash.healthCheck(group).await()
                refreshRuntimeProxyGroup(group)
                null
            }
            .getOrElse { it.message ?: "health check failed" }
    }

    suspend fun healthCheckProxy(group: String, proxyName: String): String {
        Timber.d(
            "SessionRuntime healthCheckProxy: group=%s proxy=%s phase=%s owner=%s",
            group,
            proxyName,
            currentSnapshot.phase,
            currentSnapshot.owner,
        )
        return runCatching {
                Clash.healthCheckProxy(proxyName).await().also { refreshRuntimeProxyGroup(group) }
            }
            .getOrElse {
                """{"delay":-1,"error":${rootTunEncode(it.message ?: "health check proxy failed")}}"""
            }
    }

    suspend fun updateProvider(type: String, name: String): String? {
        val providerType =
            runCatching { Provider.Type.valueOf(type) }
                .getOrElse {
                    return "invalid provider type: $type"
                }
        return runCatching {
                Clash.updateProvider(providerType, name).await()
                refreshRuntimeSnapshot()
                null
            }
            .getOrElse { it.message ?: "update provider failed" }
    }

    fun setLogObserver(observer: ((LogMessage) -> Unit)?) {
        telemetry.setLogObserver(observer)
    }

    fun queryRecentLogsJson(sinceSeq: Long): RuntimeLogChunk =
        telemetry.queryRecentLogsJson(sinceSeq)

    private suspend fun startInternal(spec: RuntimeSpec) {
        val startedAt = System.currentTimeMillis()
        currentSpec = spec
        publishSnapshot(
            RuntimeSnapshot(
                owner = spec.owner,
                phase = RuntimePhase.Starting,
                targetMode = host.mode.toRuntimeTargetMode(),
                profileUuid = spec.profileUuid,
                profileName = spec.profileName,
                profileReady = true,
                startedAt = startedAt,
                effectiveFingerprint = spec.effectiveFingerprint,
            )
        )
        host.onStarting(spec)
        ensureNotInterrupted(spec)

        claimCoreAndTeardownPrevious()
        compileAndLoad(spec)
        ensureNotInterrupted(spec)
        startObservers()
        notifyCurrentTimeZone()
        ensureNotInterrupted(spec)

        transport.prepare(spec)
        transport.start(spec)
        awaitProxyGroupsReady(spec)
        ensureNotInterrupted(spec)
        restoreSelections(spec)
        startLogStream()
        startupLog(spec, "snapshot refresh: begin")
        refreshRuntimeSnapshot()
        startupLog(spec, "snapshot refresh: done")

        updateSnapshot {
            currentSnapshot.copy(
                phase = RuntimePhase.Running,
                profileReady = true,
                groupsReady = queryCache.snapshot().proxyGroups.isNotEmpty(),
                trafficReady = true,
                configReady = true,
                transportReady = true,
                logReady = telemetry.isLogStreaming(),
                startedAt = startedAt,
                effectiveFingerprint = spec.effectiveFingerprint,
            )
        }
        host.onProfileLoaded(spec.profileUuid)
        host.onStarted(spec)
        startupLog(spec, "started")
    }

    private suspend fun reloadInternal(spec: RuntimeSpec) {
        check(currentSpec != null) { "runtime not started" }
        updateSnapshot {
            currentSnapshot.copy(
                phase = RuntimePhase.Starting,
                profileUuid = spec.profileUuid,
                profileName = spec.profileName,
                effectiveFingerprint = spec.effectiveFingerprint,
                groupsReady = false,
                trafficReady = false,
            )
        }

        compileAndLoad(spec)
        awaitProxyGroupsReady(spec)
        ensureNotInterrupted(spec)
        restoreSelections(spec)
        currentSpec = spec
        startupLog(spec, "snapshot refresh: begin")
        refreshRuntimeSnapshot()
        startupLog(spec, "snapshot refresh: done")
        updateSnapshot {
            currentSnapshot.copy(
                phase = RuntimePhase.Running,
                profileReady = true,
                groupsReady = queryCache.snapshot().proxyGroups.isNotEmpty(),
                trafficReady = true,
                configReady = true,
                transportReady = true,
                logReady = telemetry.isLogStreaming(),
                effectiveFingerprint = spec.effectiveFingerprint,
                lastError = null,
            )
        }
        host.onProfileLoaded(spec.profileUuid)
        startupLog(spec, "reload done")
    }

    private fun stopInternal(reason: String?, notifyHost: Boolean) {
        if (currentSnapshot.phase == RuntimePhase.Idle && currentSpec == null) {
            clearInterruptRequest()
            return
        }

        updateSnapshot {
            currentSnapshot.copy(
                phase =
                    if (currentSnapshot.phase == RuntimePhase.Idle) {
                        RuntimePhase.Idle
                    } else {
                        RuntimePhase.Stopping
                    },
                transportReady = false,
                groupsReady = false,
                trafficReady = false,
                configReady = false,
                logReady = false,
                lastError = reason,
            )
        }
        stopLogStream()
        stopConnectionTracking()
        stopObservers()
        runCatching { transport.stop() }
        teardownTransportAndCore()
        currentSpec = null
        queryCache.clear()
        publishSnapshot(
            RuntimeSnapshot(
                owner = RuntimeOwner.None,
                phase = if (reason.isNullOrBlank()) RuntimePhase.Idle else RuntimePhase.Failed,
                targetMode = host.mode.toRuntimeTargetMode(),
                lastError = reason,
            )
        )
        if (notifyHost) {
            host.onStopped(reason)
        }
        clearInterruptRequest()
    }

    private fun rollback(spec: RuntimeSpec, reason: String) {
        stopLogStream()
        stopObservers()
        runCatching { transport.stop() }
        teardownTransportAndCore()
        currentSpec = null
        queryCache.clear()
        publishSnapshot(
            RuntimeSnapshot(
                owner = spec.owner,
                phase = RuntimePhase.Failed,
                targetMode = host.mode.toRuntimeTargetMode(),
                profileUuid = spec.profileUuid,
                profileName = spec.profileName,
                profileReady = false,
                lastError = reason,
                effectiveFingerprint = spec.effectiveFingerprint,
            )
        )
        startupLog(spec, "failed=$reason")
        host.reportFailure(reason)
    }

    private suspend fun compileAndLoad(spec: RuntimeSpec) {
        ensureNotInterrupted(spec)
        compiledConfigPipeline.compileAndLoad(spec) { message ->
            startupLog(spec, message)
            ensureNotInterrupted(spec)
        }
        ensureNotInterrupted(spec)
    }

    private suspend fun awaitProxyGroupsReady(spec: RuntimeSpec) {
        ensureNotInterrupted(spec)
        val expectedGroups = readExpectedGroupNames(spec)
        startupLog(
            spec,
            "runtime verify: expectedGroups=${expectedGroups.size}" +
                expectedGroups
                    .takeIf { it.isNotEmpty() }
                    ?.let { " sample=${it.take(5)}" }
                    .orEmpty(),
        )
        if (expectedGroups.isEmpty()) {
            return
        }

        repeat(PROXY_GROUP_READY_RETRY_COUNT) { attempt ->
            ensureNotInterrupted(spec)
            val names = runCatching { Clash.queryGroupNames(false) }.getOrDefault(emptyList())
            if (names.isNotEmpty()) {
                startupLog(
                    spec,
                    "runtime verify: actualGroups=${names.size} sample=${names.take(5)}",
                )
                return
            }
            if (attempt < PROXY_GROUP_READY_RETRY_COUNT - 1) {
                startupLog(spec, "runtime verify: actualGroups=0 retry=${attempt + 1}")
                delay(PROXY_GROUP_READY_RETRY_DELAY_MS)
            }
        }

        ensureNotInterrupted(spec)
        error(
            "runtime loaded but exposed 0 proxy groups; expected=${expectedGroups.size} " +
                "sample=${expectedGroups.take(min(5, expectedGroups.size))}"
        )
    }

    private suspend fun readExpectedGroupNames(spec: RuntimeSpec): List<String> =
        runCatching {
                proxyGroupResolver.expectedGroupNames(spec, false)
            }
            .getOrElse { error ->
                startupLog(spec, "runtime verify: expected group inspect failed=${error.message}")
                emptyList()
            }

    private suspend fun restoreSelections(spec: RuntimeSpec) {
        val profileUuid = UUID.fromString(spec.profileUuid)
        val restoreSelections = SelectionDao.queryRestorableSelections(profileUuid)
        val restorePins = SelectionDao.getAllPins(profileUuid)
        if (restoreSelections.isEmpty() && restorePins.isEmpty()) {
            return
        }
        val runtimeGroups =
            runCatching { resolveRuntimeProxyGroups(excludeNotSelectable = false) }
                .getOrDefault(emptyList())
        SelectionRestoreExecutor.restore(
            profileUuid = profileUuid,
            selections = restoreSelections,
            pins = restorePins,
            runtimeGroups = runtimeGroups,
            tag = spec.owner.name,
        )
    }

    private fun startObservers() {
        val appContext = host.context.appContextOrSelf
        if (networkObserver == null) {
            networkObserver =
                ServiceNetworkObserver(appContext) {
                        transport.onNetworkChanged()
                    }
                    .also { it.start() }
        }
        if (timeZoneReceiver == null) {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
                            runCatching { notifyCurrentTimeZone() }
                        }
                    }
                }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            timeZoneReceiver = receiver
        }
    }

    private fun stopObservers() {
        runCatching { networkObserver?.stop() }
        networkObserver = null
        timeZoneReceiver?.let { receiver ->
            runCatching { host.context.appContextOrSelf.unregisterReceiver(receiver) }
        }
        timeZoneReceiver = null
    }

    private fun notifyCurrentTimeZone() {
        val timeZone = TimeZone.getDefault()
        Clash.notifyTimeZoneChanged(timeZone.id, timeZone.rawOffset / 1000)
    }

    private fun claimCoreAndTeardownPrevious() {
        coreOwner = this
        teardownCore()
    }

    private fun teardownTransportAndCore() {
        runCatching { transport.stop() }
        teardownCore()
    }

    private fun teardownCore() {
        // The Go core is process-wide but SessionRuntime instances are per-service. During a
        // service handover the replacement session claims ownership first; a late teardown
        // from the outgoing session (async destroy) must not kill the live core underneath it.
        val owner = coreOwner
        if (owner !== this && owner != null) return
        coreOwner = null
        // The compiled tun package lists mirror the loaded config; drop them with the core so a
        // stale profile's lists never drive per-app routing in the next session.
        CompiledTunPackages.clear()
        runCatching { Clash.stopTun() }
        runCatching { Clash.stopRootTun() }
        runCatching { Clash.stopHttp() }
        runCatching { Clash.reset() }
    }

    private suspend fun refreshRuntimeSnapshot() {
        if (
            currentSnapshot.phase != RuntimePhase.Running &&
                currentSnapshot.phase != RuntimePhase.Starting
        ) {
            queryCache.clear()
            return
        }

        val configuration = runCatching { Clash.queryConfiguration() }.getOrDefault(UiConfiguration())
        val providers = runCatching { Clash.queryProviders() }.getOrDefault(emptyList())
        val proxyGroups =
            runCatching { resolveRuntimeProxyGroups(excludeNotSelectable = false) }
                .getOrDefault(emptyList())
        val trafficNow = runCatching { Clash.queryTrafficNow() }.getOrDefault(0L)
        val trafficTotal = runCatching { Clash.queryTrafficTotal() }.getOrDefault(0L)
        queryCache.replace(
            configuration = configuration,
            providers = providers,
            proxyGroups = proxyGroups,
            trafficNow = trafficNow,
            trafficTotal = trafficTotal,
        )
    }

    private fun refreshRuntimeProxyGroup(
        name: String,
        proxySort: ProxySort = ProxySort.Default,
    ): ProxyGroup? {
        if (
            currentSnapshot.phase != RuntimePhase.Running &&
                currentSnapshot.phase != RuntimePhase.Starting
        ) {
            return null
        }

        val group = runCatching { Clash.queryGroup(name, proxySort) }.getOrNull() ?: return null
        queryCache.upsertProxyGroup(name, group)
        return group
    }

    private suspend fun resolveRuntimeProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        proxyGroupResolver.resolvedGroupNames(currentSpec, excludeNotSelectable)

    private suspend fun resolveRuntimeProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        proxyGroupResolver.resolvedGroups(currentSpec, excludeNotSelectable, enrichLive = true)

    private suspend fun ensureRuntimeSnapshot(): SessionRuntimeQuerySnapshot {
        val snapshot = queryCache.snapshot()
        if (snapshot.proxyGroups.isNotEmpty()) {
            return snapshot
        }
        refreshRuntimeSnapshot()
        return queryCache.snapshot()
    }

    private fun startLogStream() {
        telemetry.startLogStream(Clash::subscribeLogcat, Clash::unsubscribeLogcat)
    }

    private fun stopLogStream() {
        telemetry.stopLogStream()
    }

    private fun stopConnectionTracking() {
        telemetry.stopTelemetry()
    }

    private fun publishSnapshot(snapshot: RuntimeSnapshot) {
        synchronized(snapshotLock) {
            currentSnapshot = snapshot.copy(running = snapshot.phase.running)
            host.onSnapshotChanged(currentSnapshot)
        }
    }

    private inline fun updateSnapshot(transform: (RuntimeSnapshot) -> RuntimeSnapshot) {
        synchronized(snapshotLock) {
            val next = transform(currentSnapshot)
            currentSnapshot = next.copy(running = next.phase.running)
            host.onSnapshotChanged(currentSnapshot)
        }
    }

    private fun startupLog(spec: RuntimeSpec, message: String) {
        val scope =
            when (spec.owner) {
                RuntimeOwner.LocalTun -> RuntimeStartupLogStore.Scope.LOCAL_TUN
                RuntimeOwner.RootTun -> RuntimeStartupLogStore.Scope.ROOT_TUN
                RuntimeOwner.RemoteController,
                RuntimeOwner.None -> return
            }
        RuntimeStartupLogStore(host.context.appContextOrSelf, scope)
            .append("${scope.tag} session: $message")
    }

    private fun ensureNotInterrupted(spec: RuntimeSpec) {
        val reason = interruptReason ?: return
        startupLog(spec, "session: interrupted reason=$reason")
        throw RuntimeInterruptedException(reason)
    }

    private fun clearInterruptRequest() {
        interruptReason = null
    }

    private fun com.github.yumelira.yumebox.core.model.RunMode.toRuntimeTargetMode(): RuntimeTargetMode {
        return when (this) {
            com.github.yumelira.yumebox.core.model.RunMode.VpnService -> RuntimeTargetMode.Tun
            com.github.yumelira.yumebox.core.model.RunMode.Tun -> RuntimeTargetMode.RootTun
        }
    }

    private companion object {
        private const val PROXY_GROUP_READY_RETRY_COUNT = 10
        private const val PROXY_GROUP_READY_RETRY_DELAY_MS = 200L

        /** The session instance currently owning the process-wide Go core. */
        @Volatile private var coreOwner: SessionRuntime? = null
    }

    private class RuntimeInterruptedException(message: String) : CancellationException(message)
}
