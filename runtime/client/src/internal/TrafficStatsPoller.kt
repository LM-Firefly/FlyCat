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
                    var consecutiveFailures = 0
                    var lastConnectionQueryMs = 0L
                    var lastPayloadQueryMs = 0L
                    PollingTimers.ticks(PollingTimerSpecs.RuntimeTrafficPolling)
                        .throttleByScene(
                            screenOn = screenOn,
                            appForeground = AppForegroundState.foreground,
                            backgroundIntervalMs = PollingTimerSpecs.TrafficPoller.BACKGROUND_INTERVAL_MS,
                            screenOffIntervalMs = PollingTimerSpecs.TrafficPoller.SCREEN_OFF_INTERVAL_MS,
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
                                val hasSubscribers = _connectionSnapshot.subscriptionCount.value > 0
                                if (hasSubscribers || nowMs - lastConnectionQueryMs >= PollingTimerSpecs.TrafficPoller.CONNECTION_REFRESH_INTERVAL_MS) {
                                    lastConnectionQueryMs = nowMs
                                    refreshConnectionSnapshot()
                                    queryTrafficTotal(notify = false)
                                }
                                notifyTrafficUpdated()
                                consecutiveFailures = 0
                                failureBackoffUntilMs = 0L
                            }.onFailure { error ->
                                consecutiveFailures++
                                failureBackoffUntilMs =
                                    nowMs +
                                        (consecutiveFailures * PollingTimerSpecs.TrafficPoller.FAILURE_BACKOFF_STEP_MS)
                                            .coerceAtMost(PollingTimerSpecs.TrafficPoller.FAILURE_BACKOFF_MAX_MS)
                                Timber.d(
                                    error,
                                    "Traffic polling skipped (consecutive failures: %d)",
                                    consecutiveFailures,
                                )
                            }
                            if (shouldRefreshPayload() && nowMs - lastPayloadQueryMs >= PollingTimerSpecs.TrafficPoller.PAYLOAD_REFRESH_INTERVAL_MS) {
                                lastPayloadQueryMs = nowMs
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
