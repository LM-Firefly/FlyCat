package com.github.lmfirefly.flycat.runtime.client.internal

import com.github.lmfirefly.flycat.core.bridge.Bridge
import com.github.lmfirefly.flycat.core.bridge.ConnectionCloseInterface
import com.github.lmfirefly.flycat.core.bridge.ConnectionJoinInterface
import com.github.lmfirefly.flycat.core.bridge.TrafficUpdatePackedInterface
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.model.ConnectionOverviewSnapshot
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.model.traffic.Traffic
import com.github.lmfirefly.flycat.core.model.traffic.encodeTrafficValue
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState
import com.github.lmfirefly.flycat.core.util.AppForegroundState
import com.github.lmfirefly.flycat.core.util.PollingTimerSpecs
import com.github.lmfirefly.flycat.core.util.PollingTimers
import com.github.lmfirefly.flycat.core.util.TrafficPushHub
import com.github.lmfirefly.flycat.core.util.throttleByScene
import com.github.lmfirefly.flycat.runtime.client.RuntimeBackendRouter
import com.github.lmfirefly.flycat.runtime.client.remote.ServiceClient
import com.github.lmfirefly.flycat.runtime.client.root.RootTunController
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    private val _reliableConnectionCloseEvents = Channel<ConnectionInfo>(
        RELIABLE_EVENT_BUFFER_CAPACITY,
        BufferOverflow.DROP_OLDEST,
    )
    val reliableConnectionCloseEvents: ReceiveChannel<ConnectionInfo> = _reliableConnectionCloseEvents
    private val _connectionJoinEvents = MutableSharedFlow<ConnectionInfo>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val connectionJoinEvents: SharedFlow<ConnectionInfo> = _connectionJoinEvents.asSharedFlow()
    private val _reliableConnectionJoinEvents = Channel<ConnectionInfo>(
        RELIABLE_EVENT_BUFFER_CAPACITY,
        BufferOverflow.DROP_OLDEST,
    )
    val reliableConnectionJoinEvents: ReceiveChannel<ConnectionInfo> = _reliableConnectionJoinEvents
    private val liveConnections = ConcurrentHashMap<String, ConnectionInfo>()
    private val pollingMutex = Mutex()
    private var pollingJob: Job? = null
    private var failureBackoffUntilMs: Long = 0L
    private val reliableQueueWarnLock = Any()
    private var lastReliableQueueWarnTimestampMs = 0L
    private var suppressedReliableQueueWarnCount = 0
    private val connectionCloseCallback = object : ConnectionCloseInterface {
        override fun received(jsonPayload: String) {
            runCatching {
                val event = eventJson.decodeFromString<ConnectionClosePayload>(jsonPayload)
                val cached = liveConnections.remove(event.id)
                val merged =
                    cached?.copy(upload = event.upload, download = event.download)
                        ?: ConnectionInfo(
                            id = event.id,
                            upload = event.upload,
                            download = event.download,
                        )
                if (_reliableConnectionCloseEvents.trySend(merged).isFailure) {
                    logReliableQueueRejected("close", merged.id)
                }
                _connectionCloseEvents.tryEmit(merged)
                emitConnectionSnapshotFromLive()
            }.onFailure { e ->
                Timber.tag(TAG).w(e, "Failed to parse connection close event")
            }
        }
    }
    private val connectionJoinCallback = object : ConnectionJoinInterface {
        override fun received(jsonPayload: String) {
            runCatching {
                val event = eventJson.decodeFromString<ConnectionJoinPayload>(jsonPayload)
                val connection =
                    ConnectionInfo(
                        id = event.id,
                        metadata = event.metadata,
                        start = event.start,
                        chains = event.chains,
                        providerChains = event.providerChains,
                        rule = event.rule,
                        rulePayload = event.rulePayload,
                    )
                liveConnections[event.id] = connection
                if (_reliableConnectionJoinEvents.trySend(connection).isFailure) {
                    logReliableQueueRejected("join", connection.id)
                }
                _connectionJoinEvents.tryEmit(connection)
                emitConnectionSnapshotFromLive()
            }.onFailure { e ->
                Timber.tag(TAG).w(e, "Failed to parse connection join event")
            }
        }
    }
    private val trafficUpdatePackedCallback = object : TrafficUpdatePackedInterface {
        override fun received(uploadTotal: Long, downloadTotal: Long, uploadSpeed: Long, downloadSpeed: Long) {
            val packedTotal =
                (encodeTrafficValue(uploadTotal) shl 32) or
                    encodeTrafficValue(downloadTotal)
            val packedNow =
                (encodeTrafficValue(uploadSpeed) shl 32) or
                    encodeTrafficValue(downloadSpeed)
            // 统一写入 TrafficPushHub（唯一数据源）
            TrafficPushHub.update(trafficNowPacked = packedNow, trafficTotalPacked = packedTotal)
            // 仅在 totals 实际变化时更新 connectionSnapshot，避免触发下游无效传播
            val current = _connectionSnapshot.value
            if (current.uploadTotal != uploadTotal || current.downloadTotal != downloadTotal) {
                _connectionSnapshot.value = current.copy(
                    uploadTotal = uploadTotal,
                    downloadTotal = downloadTotal,
                )
            }
            notifyTrafficUpdated()
        }
    }

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
                    suspend fun refreshPayloadIfDue(nowMs: Long) {
                        if (shouldRefreshPayload() && nowMs - lastPayloadQueryMs >= PollingTimerSpecs.TrafficPoller.PAYLOAD_REFRESH_INTERVAL_MS) {
                            lastPayloadQueryMs = nowMs
                            onPayloadRefreshDue()
                        }
                    }
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
                            if (TrafficPushHub.isActive(PUSH_ACTIVE_THRESHOLD_MS)) {
                                consecutiveFailures = 0
                                failureBackoffUntilMs = 0L
                                refreshPayloadIfDue(nowMs)
                                return@collect
                            }
                            runCatching {
                                // 流量总计+全局网速：仅推送不活跃时轮询（ROOT_TUN/REMOTE 回退）
                                queryTrafficNow(notify = false)
                                queryTrafficTotal(notify = false)
                                // 通知 UI 刷新：仅推送不活跃时（活跃时由 subscribeTrafficUpdate 直接通知）
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
                            refreshPayloadIfDue(nowMs)
                        }
                }
            }
        }
    }
    fun stop() {
        Bridge.nativeUnsubscribeConnectionClose()
        Bridge.nativeUnsubscribeConnectionJoin()
        Bridge.nativeUnsubscribeTrafficUpdate()
        pollingJob?.cancel()
        pollingJob = null
    }
    fun reset() {
        TrafficPushHub.reset()
        _connectionSnapshot.value = ConnectionSnapshot()
        _tunnelMode.value = null
        liveConnections.clear()
        drainReliableConnectionEvents()
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

    suspend fun queryConnectionsOverview(): ConnectionOverviewSnapshot {
        if (!router.running) return ConnectionOverviewSnapshot()
        return router.dispatch(
            onRoot = { ctx -> RootTunController.queryConnectionsOverview(ctx) },
            onLocal = { ServiceClient.clash().queryConnectionsOverview() },
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
    private fun logReliableQueueRejected(eventType: String, id: String) {
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(reliableQueueWarnLock) {
            if (nowMs - lastReliableQueueWarnTimestampMs >= RELIABLE_QUEUE_WARN_INTERVAL_MS) {
                val suppressed = suppressedReliableQueueWarnCount
                suppressedReliableQueueWarnCount = 0
                lastReliableQueueWarnTimestampMs = nowMs
                if (suppressed > 0) {
                    Timber.tag(TAG).w(
                        "Reliable connection %s queue rejected event id=%s (suppressed %d similar warnings)",
                        eventType,
                        id,
                        suppressed,
                    )
                } else {
                    Timber.tag(TAG).w(
                        "Reliable connection %s queue rejected event id=%s",
                        eventType,
                        id,
                    )
                }
                return
            }
            suppressedReliableQueueWarnCount++
        }
    }
    private fun drainReliableConnectionEvents() {
        while (_reliableConnectionCloseEvents.tryReceive().isSuccess) {
            // drain stale reliable events when runtime state is reset
        }
        while (_reliableConnectionJoinEvents.tryReceive().isSuccess) {
            // drain stale reliable events when runtime state is reset
        }
    }
    private fun subscribeConnectionClose() {
        Bridge.nativeSubscribeConnectionClose(connectionCloseCallback)
    }
    private fun subscribeConnectionJoin() {
        Bridge.nativeSubscribeConnectionJoin(connectionJoinCallback)
    }
    private fun subscribeTrafficUpdate() {
        Bridge.nativeSubscribeTrafficUpdatePacked(trafficUpdatePackedCallback)
    }

    @Serializable
    private data class ConnectionJoinPayload(
        val id: String = "",
        val start: String = "",
        val metadata: JsonObject = JsonObject(emptyMap()),
        val chains: List<String> = emptyList(),
        val providerChains: List<String> = emptyList(),
        val rule: String = "",
        val rulePayload: String = "",
    )

    @Serializable
    private data class ConnectionClosePayload(
        val id: String = "",
        val upload: Long = 0L,
        val download: Long = 0L,
    )

    private companion object {
        private const val TAG = "TrafficStatsPoller"
        private val eventJson = Json { ignoreUnknownKeys = true }
        private const val RELIABLE_EVENT_BUFFER_CAPACITY = 4_096
        private const val RELIABLE_QUEUE_WARN_INTERVAL_MS = 15_000L
        /** If a push event was received within this window, skip redundant polling (LOCAL_TUN mode). */
        private const val PUSH_ACTIVE_THRESHOLD_MS = 6_000L
    }
}
