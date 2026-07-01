package com.github.yumelira.yumebox.runtime.client.internal

import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.TrafficSampleCache
import com.github.yumelira.yumebox.core.util.throttleByScene
import com.github.yumelira.yumebox.runtime.client.RuntimeBackendRouter
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import com.github.yumelira.yumebox.runtime.client.root.RootTunController
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal class TrafficStatsPoller(
    private val router: RuntimeBackendRouter,
    private val screenOn: StateFlow<Boolean>,
    private val onTrafficUpdated: () -> Unit,
    private val onPayloadRefreshDue: suspend () -> Unit,
    private val shouldRefreshPayload: () -> Boolean,
) {
    private companion object {
        const val TRAFFIC_TOTAL_POLL_TICKS = 10
        const val RUNTIME_PAYLOAD_REFRESH_TICKS = 20
        const val CONNECTION_IDLE_REFRESH_TICKS = 15
        const val FAILURE_BACKOFF_STEP_MS = 1_000L
        const val FAILURE_BACKOFF_MAX_MS = 15_000L
    }
    private val _trafficNow = MutableStateFlow(0L)
    val trafficNow: StateFlow<Traffic> = _trafficNow.asStateFlow()
    private val _trafficTotal = MutableStateFlow(0L)
    val trafficTotal: StateFlow<Traffic> = _trafficTotal.asStateFlow()
    private val _connectionSnapshot = MutableStateFlow(ConnectionSnapshot())
    val connectionSnapshot: StateFlow<ConnectionSnapshot> = _connectionSnapshot.asStateFlow()
    private val _tunnelMode = MutableStateFlow<TunnelState.Mode?>(null)
    val tunnelMode: StateFlow<TunnelState.Mode?> = _tunnelMode.asStateFlow()
    private val pollingMutex = Mutex()
    private var pollingJob: Job? = null
    private var failureBackoffUntilMs: Long = 0L

    internal fun notifyTrafficUpdated() {
        onTrafficUpdated()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            pollingMutex.withLock {
                if (pollingJob?.isActive == true) return@withLock
                pollingJob?.cancel()
                pollingJob = scope.launch {
                    var tick = 0
                    var consecutiveFailures = 0
                    PollingTimers.ticks(PollingTimerSpecs.RuntimeTrafficPolling)
                        .throttleByScene(
                            screenOn = screenOn,
                            appForeground = AppForegroundState.foreground,
                            backgroundIntervalMs = 5_000L,
                            screenOffIntervalMs = 120_000L,
                        )
                        .collect {
                            val nowMs = SystemClock.elapsedRealtime()
                            if (nowMs < failureBackoffUntilMs) {
                                return@collect
                            }
                            if (!router.running) {
                                consecutiveFailures = 0
                                failureBackoffUntilMs = 0L
                                return@collect
                            }
                            runCatching {
                                queryTrafficNow(notify = false)
                                if (tick % TRAFFIC_TOTAL_POLL_TICKS == 0) {
                                    queryTrafficTotal(notify = false)
                                }
                                val hasSubscribers = _connectionSnapshot.subscriptionCount.value > 0
                                if (hasSubscribers || tick % CONNECTION_IDLE_REFRESH_TICKS == 0) {
                                    refreshConnectionSnapshot()
                                }
                                notifyTrafficUpdated()
                                consecutiveFailures = 0
                                failureBackoffUntilMs = 0L
                            }.onFailure { error ->
                                consecutiveFailures++
                                failureBackoffUntilMs =
                                    nowMs +
                                        (consecutiveFailures * FAILURE_BACKOFF_STEP_MS)
                                            .coerceAtMost(FAILURE_BACKOFF_MAX_MS)
                                Timber.d(
                                    error,
                                    "Traffic polling skipped (consecutive failures: %d)",
                                    consecutiveFailures,
                                )
                            }
                            tick++
                            if (tick % RUNTIME_PAYLOAD_REFRESH_TICKS == 0 && shouldRefreshPayload()) {
                                onPayloadRefreshDue()
                            }
                        }
                }
            }
        }
    }
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }
    fun reset() {
        _trafficNow.value = 0L
        _trafficTotal.value = 0L
        _connectionSnapshot.value = ConnectionSnapshot()
        _tunnelMode.value = null
        TrafficSampleCache.reset()
    }
    suspend fun queryTrafficNow(notify: Boolean = true): Long {
        if (!router.running) {
            _trafficNow.value = 0L
            return 0L
        }
        val traffic = router.dispatch(
            onRoot = { ctx -> RootTunController.queryTrafficNow(ctx) },
            onLocal = { ServiceClient.clash().queryTrafficNow() },
        )
        _trafficNow.value = traffic
        if (notify) {
            notifyTrafficUpdated()
        }
        return traffic
    }
    suspend fun queryTrafficTotal(notify: Boolean = true): Long {
        if (!router.running) {
            _trafficTotal.value = 0L
            return 0L
        }
        val traffic = router.dispatch(
            onRoot = { ctx -> RootTunController.queryTrafficTotal(ctx) },
            onLocal = { ServiceClient.clash().queryTrafficTotal() },
        )
        _trafficTotal.value = traffic
        if (notify) {
            notifyTrafficUpdated()
        }
        return traffic
    }
    suspend fun queryConnections(): ConnectionSnapshot {
        if (!router.running) return ConnectionSnapshot()
        return router.dispatch(
            onRoot = { ctx -> RootTunController.queryConnections(ctx) },
            onLocal = { ServiceClient.clash().queryConnections() },
        )
    }
    suspend fun closeConnection(id: String): Boolean {
        if (!router.running) return false
        return router.dispatch(
            onRoot = { ctx -> RootTunController.closeConnection(ctx, id) },
            onLocal = { ServiceClient.clash().closeConnection(id) },
        )
    }
    suspend fun closeAllConnections() {
        if (!router.running) return
        router.dispatch<Unit>(
            onRoot = { ctx -> RootTunController.closeAllConnections(ctx) },
            onLocal = { ServiceClient.clash().closeAllConnections() },
        )
    }
    suspend fun queryTunnelState(): TunnelState {
        return router.dispatch(
            onRoot = { ctx -> RootTunController.queryTunnelState(ctx) },
            onLocal = { ServiceClient.clash().queryTunnelState() },
        )
    }
    suspend fun refreshTunnelMode() {
        _tunnelMode.value = runCatching { queryTunnelState().mode }.getOrNull()
    }
    private suspend fun refreshConnectionSnapshot() {
        _connectionSnapshot.value = queryConnections()
    }
}
