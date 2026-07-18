package com.github.yumelira.yumebox.runtime.client.internal

import com.github.yumelira.yumebox.core.bridge.Bridge
import com.github.yumelira.yumebox.core.bridge.ConnectionCloseInterface
import com.github.yumelira.yumebox.core.bridge.ConnectionJoinInterface
import com.github.yumelira.yumebox.core.bridge.TrafficUpdateInterface
import com.github.yumelira.yumebox.core.model.ConnectionInfo
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.encodeTrafficValue
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.TrafficPushHub
import com.github.yumelira.yumebox.core.util.throttleByScene
import com.github.yumelira.yumebox.runtime.client.RuntimeBackendRouter
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import com.github.yumelira.yumebox.runtime.client.root.RootTunController
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import timber.log.Timber

internal class TrafficStatsPoller(
    private val router: RuntimeBackendRouter,
    private val screenOn: StateFlow<Boolean>,
    private val onTrafficUpdated: () -> Unit,
    private val onPayloadRefreshDue: suspend () -> Unit,
    private val shouldRefreshPayload: () -> Boolean,
) {
    // 流量数据统一由 TrafficPushHub 管理，消除中间 StateFlow 副本
    val trafficNow: StateFlow<Traffic> = TrafficPushHub.trafficNow
    val trafficTotal: StateFlow<Traffic> = TrafficPushHub.trafficTotal
    private val _connectionSnapshot = MutableStateFlow(ConnectionSnapshot())
    val connectionSnapshot: StateFlow<ConnectionSnapshot> = _connectionSnapshot.asStateFlow()
    private val _tunnelMode = MutableStateFlow<TunnelState.Mode?>(null)
    val tunnelMode: StateFlow<TunnelState.Mode?> = _tunnelMode.asStateFlow()
    private val _connectionCloseEvents = MutableSharedFlow<ConnectionInfo>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val connectionCloseEvents: SharedFlow<ConnectionInfo> = _connectionCloseEvents.asSharedFlow()
    private val _connectionJoinEvents = MutableSharedFlow<ConnectionInfo>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val connectionJoinEvents: SharedFlow<ConnectionInfo> = _connectionJoinEvents.asSharedFlow()
    private val liveConnections = ConcurrentHashMap<String, ConnectionInfo>()
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
                subscribeConnectionClose()
                subscribeConnectionJoin()
                subscribeTrafficUpdate()
                pollingJob = scope.launch {
                    var consecutiveFailures = 0
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
                                val pushActive = TrafficPushHub.isActive(PUSH_ACTIVE_THRESHOLD_MS)
                                // ① 流量总计+全局网速：仅推送不活跃时轮询（ROOT_TUN/REMOTE 回退）
                                if (!pushActive) {
                                    queryTrafficNow(notify = false)
                                    queryTrafficTotal(notify = false)
                                }
                                // ② 通知 UI 刷新：仅推送不活跃时（活跃时由 subscribeTrafficUpdate 直接通知）
                                if (!pushActive) notifyTrafficUpdated()
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
        TrafficPushHub.reset()
        _connectionSnapshot.value = ConnectionSnapshot()
        _tunnelMode.value = null
        liveConnections.clear()
    }
    suspend fun queryTrafficNow(notify: Boolean = true): Long {
        if (!router.running) {
            TrafficPushHub.update(0L, TrafficPushHub.trafficTotal.value)
            return 0L
        }
        val traffic = router.dispatch(
            onRoot = { ctx -> RootTunController.queryTrafficNow(ctx) },
            onLocal = { ServiceClient.clash().queryTrafficNow() },
        )
        TrafficPushHub.update(traffic, TrafficPushHub.trafficTotal.value)
        if (notify) {
            notifyTrafficUpdated()
        }
        return traffic
    }
    suspend fun queryTrafficTotal(notify: Boolean = true): Long {
        if (!router.running) {
            TrafficPushHub.update(TrafficPushHub.trafficNow.value, 0L)
            return 0L
        }
        val traffic = router.dispatch(
            onRoot = { ctx -> RootTunController.queryTrafficTotal(ctx) },
            onLocal = { ServiceClient.clash().queryTrafficTotal() },
        )
        TrafficPushHub.update(TrafficPushHub.trafficNow.value, traffic)
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
    private fun emitConnectionSnapshotFromLive() {
        val connections = liveConnections.values.toList()
        val current = _connectionSnapshot.value
        _connectionSnapshot.value = ConnectionSnapshot(
            downloadTotal = current.downloadTotal,
            uploadTotal = current.uploadTotal,
            connections = connections,
            memory = current.memory,
        )
    }
    private fun subscribeConnectionClose() {
        Bridge.nativeSubscribeConnectionClose(object : ConnectionCloseInterface {
            override fun received(jsonPayload: String) {
                runCatching {
                    val event = eventJson.decodeFromString<ConnectionInfo>(jsonPayload)
                    liveConnections.remove(event.id)
                    _connectionCloseEvents.tryEmit(event)
                    emitConnectionSnapshotFromLive()
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to parse connection close event")
                }
            }
        })
    }
    private fun subscribeConnectionJoin() {
        Bridge.nativeSubscribeConnectionJoin(object : ConnectionJoinInterface {
            override fun received(jsonPayload: String) {
                runCatching {
                    val event = eventJson.decodeFromString<ConnectionInfo>(jsonPayload)
                    liveConnections[event.id] = event
                    _connectionJoinEvents.tryEmit(event)
                    emitConnectionSnapshotFromLive()
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to parse connection join event")
                }
            }
        })
    }
    private fun subscribeTrafficUpdate() {
        Bridge.nativeSubscribeTrafficUpdate(object : TrafficUpdateInterface {
            override fun received(jsonPayload: String) {
                runCatching {
                    val event = eventJson.decodeFromString<TrafficUpdatePayload>(jsonPayload)
                    val packedTotal =
                        (encodeTrafficValue(event.uploadTotal) shl 32) or
                            encodeTrafficValue(event.downloadTotal)
                    val packedNow =
                        (encodeTrafficValue(event.uploadSpeed) shl 32) or
                            encodeTrafficValue(event.downloadSpeed)
                    // 统一写入 TrafficPushHub（唯一数据源）
                    TrafficPushHub.update(trafficNowPacked = packedNow, trafficTotalPacked = packedTotal)
                    // 从推送事件更新连接快照的流量总计（与流量数据原子化更新）
                    val current = _connectionSnapshot.value
                    _connectionSnapshot.value = current.copy(
                        uploadTotal = event.uploadTotal,
                        downloadTotal = event.downloadTotal,
                    )
                    notifyTrafficUpdated()
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to parse traffic update event")
                }
            }
        })
    }
    @kotlinx.serialization.Serializable
    private data class TrafficUpdatePayload(
        val uploadTotal: Long = 0L,
        val downloadTotal: Long = 0L,
        val uploadSpeed: Long = 0L,
        val downloadSpeed: Long = 0L,
    )
    private companion object {
        private const val TAG = "TrafficStatsPoller"
        private val eventJson = Json { ignoreUnknownKeys = true }
        /** If a push event was received within this window, skip redundant polling (LOCAL_TUN mode). */
        private const val PUSH_ACTIVE_THRESHOLD_MS = 6_000L
    }
}
