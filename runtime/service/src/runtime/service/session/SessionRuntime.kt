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

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.runtime.service.session


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.core.model.*
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import kotlin.math.min
import kotlinx.coroutines.*
import timber.log.Timber

// Established runtime session seam (owner-token lifecycle + snapshot + core control); splitting
// is tracked separately.
@Suppress("LargeClass")
class SessionRuntime(
    private val host: RuntimeHost,
    private val transport: RuntimeTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val compiledConfigPipeline = CompiledConfigPipeline(host.context.appContextOrSelf)

    /** Drives the running local core over REST-over-unix (traffic/groups/connections/…). */
    private val rest
        get() =
            com.github.yumelira.yumebox.runtime.service.core.CoreProcess.controller(
                host.context.appContextOrSelf
            )

    private val proxyGroupResolver =
        RuntimeProxyGroupResolver(compiledConfigPipeline, host.context.appContextOrSelf)
    private val lock = Any()
    private val interruptMonitor = Object()
    private val snapshotLock = Any()

    @Volatile private var interruptReason: String? = null
    @Volatile private var currentSpec: RuntimeSpec? = null
    @Volatile private var currentSnapshot: RuntimeSnapshot = RuntimeSnapshot(runMode = host.mode)
    private var networkObserver: ServiceNetworkObserver? = null
    private var timeZoneReceiver: BroadcastReceiver? = null
    @Volatile private var snapshotRefreshJob: Job? = null
    private val queryCache = SessionRuntimeQueryCache()
    private val telemetry =
        SessionRuntimeTelemetry(host = host, scope = scope) { ready ->
            updateSnapshot { it.copy(logReady = ready) }
        }

    fun start(spec: RuntimeSpec): RuntimeOperationResult = startFresh(spec, name = "start")

    fun reload(spec: RuntimeSpec): RuntimeOperationResult =
        runGuarded(spec, name = "reload", onFailure = { startupLog(spec, "failed=$it") }) {
            startupLog(spec, "session: reload begin")
            reloadInternal(spec)
        }

    fun restart(spec: RuntimeSpec): RuntimeOperationResult = startFresh(spec, name = "restart")

    private fun startFresh(spec: RuntimeSpec, name: String): RuntimeOperationResult =
        runGuarded(spec, name = name, onFailure = { rollback(spec, it) }) {
            stopInternal(reason = null, notifyHost = false)
            startupLog(spec, "session: $name begin")
            startInternal(spec)
        }

    /**
     * Shared interrupt-aware wrapper for the lifecycle entry points: serializes on [lock], treats
     * [RuntimeInterruptedException] as a successful no-op, and routes any other failure through
     * [onFailure] before surfacing it in the result.
     */
    private fun runGuarded(
        spec: RuntimeSpec,
        name: String,
        onFailure: (String) -> Unit,
        body: () -> Unit,
    ): RuntimeOperationResult =
        synchronized(lock) {
            clearInterruptRequest()
            runCatching {
                body()
                RuntimeOperationResult(success = true)
            }
                .getOrElse { error ->
                    if (error is RuntimeInterruptedException) {
                        startupLog(spec, "session: $name interrupted reason=${error.message}")
                        RuntimeOperationResult(success = true)
                    } else {
                        val message = error.message ?: "$name runtime failed"
                        onFailure(message)
                        RuntimeOperationResult(success = false, error = message)
                    }
                }
        }

    fun stop(reason: String? = null): RuntimeOperationResult {
        requestStop(reason)
        return synchronized(lock) {
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
        currentSpec?.let { startupLog(it, "stop requested") }
        snapshotRefreshJob?.cancel()
        if (currentSnapshot.owner == RuntimeOwner.VpnService) {
            // Drop the child and its TUN fd without waiting for the lifecycle lock.
            com.github.yumelira.yumebox.runtime.service.core.CoreProcess.killRunning()
        }
        synchronized(interruptMonitor) { interruptMonitor.notifyAll() }
    }

    fun destroy() {
        requestStop("runtime destroyed")
        synchronized(lock) {
            runCatching { stopInternal(reason = "runtime destroyed", notifyHost = false) }
        }
        scope.cancel()
    }

    fun snapshot(): RuntimeSnapshot = currentSnapshot

    fun queryTunnelState(): TunnelState =
        ifRunning(TunnelState(TunnelState.Mode.Rule)) { rest.queryTunnelState() }

    fun queryTrafficNow(): Long = queryTraffic(rest::queryTrafficNow, queryCache::updateTrafficNow)

    fun queryTrafficTotal(): Long =
        queryTraffic(rest::queryTrafficTotal, queryCache::updateTrafficTotal)

    private fun queryTraffic(fetch: () -> Long, cache: (Long) -> Unit): Long {
        if (currentSnapshot.phase != RuntimePhase.Running) return 0L
        return fetch().also { value ->
            cache(value)
            updateSnapshot { it.copy(trafficReady = true) }
        }
    }

    private inline fun <T> ifRunning(fallback: T, block: () -> T): T =
        if (currentSnapshot.phase == RuntimePhase.Running) block() else fallback

    fun queryConnections(): ConnectionSnapshot =
        ifRunning(ConnectionSnapshot()) { rest.queryConnections() }

    fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> {
        if (currentSnapshot.phase != RuntimePhase.Running) return emptyList()
        val groups = runCatching {
            resolveRuntimeProxyGroups(excludeNotSelectable)
        }
            .getOrElse {
                if (excludeNotSelectable) {
                    val selectable = rest.queryProxyGroupNames(true).toSet()
                    ensureRuntimeSnapshot().proxyGroups.filter { selectable.contains(it.name) }
                } else {
                    ensureRuntimeSnapshot().proxyGroups
                }
            }
        queryCache.replaceProxyGroups(groups)
        updateSnapshot { it.copy(groupsReady = groups.isNotEmpty()) }
        return groups
    }

    fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        if (currentSnapshot.phase != RuntimePhase.Running) return emptyList()
        return runCatching { resolveRuntimeProxyGroupNames(excludeNotSelectable) }
            .getOrElse { queryAllProxyGroups(excludeNotSelectable).map { it.name } }
    }

    fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        if (currentSnapshot.phase != RuntimePhase.Running) {
            error("runtime not running")
        }
        val group = rest.queryProxyGroup(name, proxySort)
        if (proxySort == ProxySort.Default && group.name.isNotBlank()) {
            queryCache.upsertProxyGroup(name, group)
        }
        return group
    }

    fun queryConfiguration(): UiConfiguration =
        ifRunning(UiConfiguration()) { ensureRuntimeSnapshot().configuration }

    fun queryProviders(): List<Provider> =
        ifRunning(emptyList()) { ensureRuntimeSnapshot().providers }

    fun patchSelector(group: String, name: String): Boolean = rest.patchSelector(group, name)

    fun closeConnection(id: String): Boolean = ifRunning(false) { rest.closeConnection(id) }

    fun closeAllConnections() = ifRunning(Unit) { rest.closeAllConnections() }

    suspend fun healthCheck(group: String): String? {
        Timber.d(
            "SessionRuntime healthCheck: group=%s phase=%s owner=%s",
            group,
            currentSnapshot.phase,
            currentSnapshot.owner,
        )
        return runCatching {
            rest.healthCheck(group)
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
            val delay =
                rest.healthCheckProxy(group, proxyName).also { refreshRuntimeProxyGroup(group) }
            """{"delay":$delay}"""
        }
            .getOrElse {
                val msg =
                    kotlinx.serialization.json.JsonPrimitive(
                        it.message ?: "health check proxy failed"
                    )
                """{"delay":-1,"error":$msg}"""
            }
    }

    suspend fun updateProvider(type: String, name: String): String? {
        val providerType = runCatching {
            Provider.Type.valueOf(type)
        }
            .getOrElse {
                return "invalid provider type: $type"
            }
        return runCatching {
            rest.updateProvider(providerType, name)
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

    private fun startInternal(spec: RuntimeSpec) {
        val startedAt = System.currentTimeMillis()
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        // One nativeCompile for the whole start path: transport handoff + expected group names.
        val prepared = prepareCompiledSpec(spec)
        startupLog(prepared, "compiled elapsedMs=${elapsedMillis(startedAtNanos)}")
        currentSpec = prepared
        publishSnapshot(
            RuntimeSnapshot(
                owner = prepared.owner,
                phase = RuntimePhase.Starting,
                runMode = host.mode,
                profileUuid = prepared.profileUuid,
                profileName = prepared.profileName,
                profileReady = true,
                startedAt = startedAt,
                effectiveFingerprint = prepared.effectiveFingerprint,
            )
        )
        host.onStarting(prepared)
        ensureNotInterrupted(prepared)

        claimCoreAndTeardownPrevious()
        ensureNotInterrupted(prepared)
        startObservers()
        notifyCurrentTimeZone()
        startConnectionTracking()
        ensureNotInterrupted(prepared)

        transport.prepare(prepared)
        transport.start(prepared)
        startupLog(
            prepared,
            "core launched elapsedMs=${elapsedMillis(startedAtNanos)} " +
                "profile=${prepared.profileName} overrides=${prepared.overrideSpecs.size}",
        )
        val initialGroups = awaitProxyGroupsReady(prepared)
        startupLog(
            prepared,
            "controller ready elapsedMs=${elapsedMillis(startedAtNanos)} groups=${initialGroups.size}",
        )
        ensureNotInterrupted(prepared)
        queryCache.replaceProxyGroups(initialGroups)
        startLogStream()

        updateSnapshot {
            it.copy(
                phase = RuntimePhase.Running,
                profileReady = true,
                groupsReady = initialGroups.isNotEmpty(),
                trafficReady = false,
                configReady = true,
                transportReady = true,
                logReady = telemetry.isLogStreaming(),
                startedAt = startedAt,
                effectiveFingerprint = prepared.effectiveFingerprint,
            )
        }
        host.onProfileLoaded(prepared.profileUuid)
        host.onStarted(prepared)
        startupLog(prepared, "started elapsedMs=${elapsedMillis(startedAtNanos)}")
        scheduleRuntimeSnapshotRefresh(prepared)
    }

    private fun reloadInternal(spec: RuntimeSpec) {
        check(currentSpec != null) { "runtime not started" }
        val prepared = prepareCompiledSpec(spec)
        updateSnapshot {
            it.copy(
                phase = RuntimePhase.Starting,
                profileUuid = prepared.profileUuid,
                profileName = prepared.profileName,
                effectiveFingerprint = prepared.effectiveFingerprint,
                groupsReady = false,
                trafficReady = false,
            )
        }

        // Config reload for the out-of-process core is applied by restarting the transport (the
        // core
        // reads its config at launch); re-verify groups afterwards.
        runCatching { transport.stop() }
        transport.start(prepared)
        val initialGroups = awaitProxyGroupsReady(prepared)
        ensureNotInterrupted(prepared)
        currentSpec = prepared
        queryCache.replaceProxyGroups(initialGroups)
        updateSnapshot {
            it.copy(
                phase = RuntimePhase.Running,
                profileReady = true,
                groupsReady = initialGroups.isNotEmpty(),
                trafficReady = false,
                configReady = true,
                transportReady = true,
                logReady = telemetry.isLogStreaming(),
                effectiveFingerprint = prepared.effectiveFingerprint,
                lastError = null,
            )
        }
        host.onProfileLoaded(prepared.profileUuid)
        startupLog(prepared, "reload done")
        scheduleRuntimeSnapshotRefresh(prepared)
    }

    private fun stopInternal(reason: String?, notifyHost: Boolean) {
        snapshotRefreshJob?.cancel()
        snapshotRefreshJob = null
        if (currentSnapshot.phase == RuntimePhase.Idle && currentSpec == null) {
            clearInterruptRequest()
            return
        }

        updateSnapshot {
            it.copy(
                phase =
                    if (it.phase == RuntimePhase.Idle) {
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
        teardownTransportAndCore()
        currentSpec = null
        queryCache.clear()
        // A stop that carries a reason (user request, VPN revoke) is still a stop, not a
        // failure; only the rollback path publishes Failed.
        publishSnapshot(
            RuntimeSnapshot(
                owner = RuntimeOwner.None,
                phase = RuntimePhase.Idle,
                runMode = host.mode,
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
        stopConnectionTracking()
        stopObservers()
        teardownTransportAndCore()
        currentSpec = null
        queryCache.clear()
        publishSnapshot(
            RuntimeSnapshot(
                owner = spec.owner,
                phase = RuntimePhase.Failed,
                runMode = host.mode,
                profileUuid = spec.profileUuid,
                profileName = spec.profileName,
                profileReady = false,
                lastError = reason,
                effectiveFingerprint = spec.effectiveFingerprint,
            )
        )
        startupLog(spec, "failed=$reason")
        appendCoreDiagnostics(spec)
        host.reportFailure(reason)
    }

    private fun awaitProxyGroupsReady(spec: RuntimeSpec): List<ProxyGroup> {
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

        var lastControllerError: String? = null
        repeat(PROXY_GROUP_READY_RETRY_COUNT) { attempt ->
            ensureNotInterrupted(spec)

            // Dead core cannot create the controller; fail fast with core.log instead of spinning.
            if (
                spec.owner == RuntimeOwner.VpnService &&
                    !com.github.yumelira.yumebox.runtime.service.core.CoreProcess.isLocalCoreAlive()
            ) {
                val coreTail =
                    com.github.yumelira.yumebox.runtime.service.core.CoreProcess.coreLogTail(
                        host.context.appContextOrSelf
                    )
                error(
                    "core process exited before controller became ready" +
                        (coreTail?.let { " ($it)" } ?: "")
                )
            }

            val groups = runCatching {
                rest.queryAllProxyGroups(false)
            }
                .onFailure { error ->
                    lastControllerError = error.message ?: error::class.simpleName
                }
                .getOrDefault(emptyList())
            val names = groups.map(ProxyGroup::name).filter(String::isNotBlank)
            if (names.isNotEmpty()) {
                startupLog(
                    spec,
                    "runtime verify: actualGroups=${names.size} sample=${names.take(5)}",
                )
                return groups
            }
            val tunnelOk = runCatching {
                rest.queryTunnelState()
                true
            }
                .onFailure { error ->
                    lastControllerError = error.message ?: error::class.simpleName
                }
                .getOrDefault(false)
            // Controller answered: if the compiled profile exposes no groups to verify, startup is
            // ready even though the live group list is empty.
            if (tunnelOk && expectedGroups.isEmpty()) {
                startupLog(
                    spec,
                    "runtime verify: controller ok with 0 expected groups; treating as ready",
                )
                return emptyList()
            }
            // Only log the first miss and the final attempt — intermediate retries are noise.
            if (attempt == 0 || attempt == PROXY_GROUP_READY_RETRY_COUNT - 1) {
                startupLog(
                    spec,
                    "runtime verify: actualGroups=0 controller=${if (tunnelOk) "ok" else "down"}" +
                        (lastControllerError?.let { " err=$it" } ?: "") +
                        " attempt=${attempt + 1}/$PROXY_GROUP_READY_RETRY_COUNT",
                )
            }
            if (attempt < PROXY_GROUP_READY_RETRY_COUNT - 1) {
                waitForRetryOrInterrupt(PROXY_GROUP_READY_RETRY_DELAY_MS)
            }
        }

        ensureNotInterrupted(spec)
        val coreTail =
            com.github.yumelira.yumebox.runtime.service.core.CoreProcess.coreLogTail(
                host.context.appContextOrSelf
            )
        if (expectedGroups.isNotEmpty()) {
            error(
                "runtime loaded but exposed 0 proxy groups; expected=${expectedGroups.size} " +
                    "sample=${expectedGroups.take(min(5, expectedGroups.size))}" +
                    (coreTail?.let { " core=$it" } ?: "")
            )
        }
        error(
            "core controller unavailable or exposed 0 proxy groups during startup" +
                (lastControllerError?.let { ": $it" } ?: "") +
                (coreTail?.let { " ($it)" } ?: "")
        )
    }

    private fun scheduleRuntimeSnapshotRefresh(spec: RuntimeSpec) {
        snapshotRefreshJob?.cancel()
        snapshotRefreshJob =
            scope.launch(Dispatchers.IO) {
                refreshRuntimeSnapshot(spec)
            }
    }

    private fun appendCoreDiagnostics(spec: RuntimeSpec) {
        val scope =
            when (spec.owner) {
                RuntimeOwner.VpnService -> RuntimeStartupLogStore.Scope.LOCAL_TUN
                RuntimeOwner.RootDaemon -> RuntimeStartupLogStore.Scope.ROOT_TUN
                RuntimeOwner.RemoteController,
                RuntimeOwner.None -> return
            }
        val diagnostics =
            com.github.yumelira.yumebox.runtime.service.core.CoreProcess.coreDiagnosticLog(
                host.context.appContextOrSelf
            )
        if (diagnostics.isBlank()) {
            RuntimeStartupLogStore(host.context.appContextOrSelf, scope)
                .append("${scope.tag} core diagnostics: (empty — core may have died before boot)")
            return
        }
        val store = RuntimeStartupLogStore(host.context.appContextOrSelf, scope)
        store.append("${scope.tag} core diagnostics begin")
        diagnostics.lineSequence().forEach { line ->
            store.append("${scope.tag} core| $line")
        }
        store.append("${scope.tag} core diagnostics end")
    }

    private fun readExpectedGroupNames(spec: RuntimeSpec): List<String> {
        if (spec.expectedProxyGroupNames.isNotEmpty()) {
            return spec.expectedProxyGroupNames
        }
        if (spec.compiledFinalYaml.isNotBlank()) {
            return compiledConfigPipeline.extractProxyGroupNames(spec.compiledFinalYaml)
        }
        // Last resort only: should be rare once start/reload always prepare the compiled YAML.
        return runCatching { proxyGroupResolver.expectedGroupNames(spec, false) }
            .getOrElse { error ->
                startupLog(spec, "runtime verify: expected group inspect failed=${error.message}")
                emptyList()
            }
    }

    /**
     * Ensures [RuntimeSpec.compiledFinalYaml] / [RuntimeSpec.expectedProxyGroupNames] are populated
     * with a single nativeCompile so transport handoff and readiness share the same result.
     */
    private fun prepareCompiledSpec(spec: RuntimeSpec): RuntimeSpec {
        if (spec.compiledFinalYaml.isNotBlank()) {
            if (spec.expectedProxyGroupNames.isNotEmpty()) {
                return spec
            }
            val names = compiledConfigPipeline.extractProxyGroupNames(spec.compiledFinalYaml)
            return spec.copy(expectedProxyGroupNames = names)
        }
        val compiled =
            kotlinx.coroutines.runBlocking { compiledConfigPipeline.compileDetailed(spec) }
        startupLog(
            spec,
            "compiled once groups=${compiled.proxyGroupNames.size} warnings=${compiled.warnings.size}",
        )
        return spec.copy(
            compiledFinalYaml = compiled.finalYaml,
            expectedProxyGroupNames = compiled.proxyGroupNames,
        )
    }

    private fun waitForRetryOrInterrupt(delayMs: Long) {
        synchronized(interruptMonitor) {
            if (interruptReason == null) interruptMonitor.wait(delayMs)
        }
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
        // The out-of-process core reads the device time zone itself; no push needed.
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
        // The core process is torn down by the transport (CoreProcess.stop kills it); nothing to do
        // in-process here now that there is no embedded core.
    }

    private fun refreshRuntimeSnapshot(expectedSpec: RuntimeSpec? = currentSpec) {
        if (
            currentSnapshot.phase != RuntimePhase.Running &&
                currentSnapshot.phase != RuntimePhase.Starting
        ) {
            queryCache.clear()
            return
        }

        val configuration = runCatching {
            rest.queryConfiguration()
        }.getOrDefault(UiConfiguration())
        val providers = runCatching { rest.queryProviders() }.getOrDefault(emptyList())
        val proxyGroups = runCatching {
            resolveRuntimeProxyGroups(excludeNotSelectable = false)
        }.getOrDefault(emptyList())
        val trafficNow = runCatching { rest.queryTrafficNow() }.getOrDefault(0L)
        val trafficTotal = runCatching { rest.queryTrafficTotal() }.getOrDefault(0L)
        if (currentSpec !== expectedSpec || currentSnapshot.phase != RuntimePhase.Running) {
            return
        }
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

        val group = runCatching { rest.queryProxyGroup(name, proxySort) }.getOrNull() ?: return null
        queryCache.upsertProxyGroup(name, group)
        return group
    }

    private fun resolveRuntimeProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        proxyGroupResolver.resolvedGroupNames(currentSpec, excludeNotSelectable)

    private fun resolveRuntimeProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        proxyGroupResolver.resolvedGroups(currentSpec, excludeNotSelectable, enrichLive = true)

    private fun ensureRuntimeSnapshot(): SessionRuntimeQuerySnapshot {
        val snapshot = queryCache.snapshot()
        if (snapshot.proxyGroups.isNotEmpty()) {
            return snapshot
        }
        refreshRuntimeSnapshot()
        return queryCache.snapshot()
    }

    private fun startLogStream() {
        telemetry.startLogStream(rest::subscribeLogs)
    }

    private fun stopLogStream() {
        telemetry.stopLogStream()
    }

    private fun startConnectionTracking() {
        telemetry.startConnectionTracking()
    }

    private fun stopConnectionTracking() {
        telemetry.stopConnectionTracking()
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
                RuntimeOwner.VpnService -> RuntimeStartupLogStore.Scope.LOCAL_TUN
                RuntimeOwner.RootDaemon -> RuntimeStartupLogStore.Scope.ROOT_TUN
                RuntimeOwner.RemoteController,
                RuntimeOwner.None -> return
            }
        RuntimeStartupLogStore(host.context.appContextOrSelf, scope)
            .append("${scope.tag} session: $message")
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L

    private fun ensureNotInterrupted(spec: RuntimeSpec) {
        val reason = interruptReason ?: return
        startupLog(spec, "session: interrupted reason=$reason")
        throw RuntimeInterruptedException(reason)
    }

    private fun clearInterruptRequest() {
        interruptReason = null
    }

    private companion object {
        private const val PROXY_GROUP_READY_RETRY_COUNT = 10
        private const val PROXY_GROUP_READY_RETRY_DELAY_MS = 200L

        /** The session instance currently owning the process-wide Go core. */
        @Volatile private var coreOwner: SessionRuntime? = null
    }

    private class RuntimeInterruptedException(message: String) : CancellationException(message)
}
