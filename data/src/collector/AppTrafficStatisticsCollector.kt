/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.data.collector

import com.github.lmfirefly.flycat.core.contract.TrafficCollectorContract
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.model.traffic.AppTrafficDeltaRecord
import com.github.lmfirefly.flycat.core.model.traffic.Traffic
import com.github.lmfirefly.flycat.core.model.traffic.TrafficData
import com.github.lmfirefly.flycat.core.model.traffic.TrafficStatisticsBuckets
import com.github.lmfirefly.flycat.data.repository.AppIdentityResolver
import com.github.lmfirefly.flycat.data.store.TrafficStatisticsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class AppTrafficStatisticsCollector(
    private val isRunningFlow: Flow<Boolean>,
    private val currentProfileId: () -> String?,
    private val trafficStatisticsStore: TrafficStatisticsStore,
    private val appIdentityResolver: AppIdentityResolver,
    private val trafficTotalFlow: StateFlow<Traffic>,
    private val connectionJoinFlow: ReceiveChannel<ConnectionInfo>,
    private val connectionCloseFlow: ReceiveChannel<ConnectionInfo>,
    private val queryActiveProfileId: suspend () -> String?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2)),
) : TrafficCollectorContract {
    private var collectionJob: Job? = null
    private var monitoringJob: Job? = null
    private val mutex = Mutex()
    private val connectionBaselines = linkedMapOf<String, ConnectionBaseline>()
    private var lastTotalUpload = NO_BASELINE
    private var lastTotalDownload = NO_BASELINE
    private var lastProfileId: String? = null
    private var lastPersistTimestamp = 0L
    private val pendingBuckets = linkedMapOf<String, PendingTrafficBucket>()

    init {
        startCollection()
    }

    private fun startCollection() {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            isRunningFlow.collectLatest { isRunning ->
                monitoringJob?.cancel()
                if (isRunning) {
                    monitoringJob = startTrafficMonitoring(this)
                } else {
                    resetBaselines()
                }
            }
        }
    }

    private fun startTrafficMonitoring(parentScope: CoroutineScope): Job {
        return parentScope.launch {
            mutex.withLock {
                lastTotalUpload = trafficStatisticsStore.getLastTrafficUpload()
                lastTotalDownload = trafficStatisticsStore.getLastTrafficDownload()
                lastProfileId = trafficStatisticsStore.getLastProfileId()
                connectionBaselines.clear()
            }
            launch {
                for (joined in connectionJoinFlow) {
                    handleConnectionJoin(joined)
                }
            }
            launch {
                for (closed in connectionCloseFlow) {
                    handleConnectionClose(closed)
                }
            }
            trafficTotalFlow.collectLatest { traffic ->
                runCatching { trackTrafficTotals(TrafficData.from(traffic)) }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.tag(TAG).e(error, "App traffic collection failed")
                }
            }
        }
    }

    /** Lightweight total tracker — no snapshot dependency. Close events handle attribution. */
    private suspend fun trackTrafficTotals(totalTraffic: TrafficData) {
        val timestamp = System.currentTimeMillis()
        // 快速路径1：如果总数未变、没有待处理的桶，且尚未到刷写基线的时间，则跳过所有操作。
        // 这避免了在空闲或低流量活动时每秒都获取互斥锁。
        if (lastTotalUpload >= 0L && lastTotalDownload >= 0L &&
            totalTraffic.upload == lastTotalUpload && totalTraffic.download == lastTotalDownload &&
            pendingBuckets.isEmpty() && timestamp - lastPersistTimestamp < PERSIST_INTERVAL_MS) return
        val currentProfileId = currentProfileId() ?: runCatching { queryActiveProfileId() }.getOrNull()
        var drainedBuckets: List<PendingTrafficBucket>? = null
        var flushTimestamp = 0L
        mutex.withLock {
            val isFirstCall = lastTotalUpload < 0L || lastTotalDownload < 0L
            val profileChanged = !isFirstCall && currentProfileId != lastProfileId
            val trafficReset = !isFirstCall && !profileChanged && (totalTraffic.upload < lastTotalUpload || totalTraffic.download < lastTotalDownload)
            // 快速路径2：若仅有总量微小变动且无待刷新的存储桶，则限流更新。
            if (!isFirstCall && !profileChanged && !trafficReset && pendingBuckets.isEmpty() &&
                timestamp - lastPersistTimestamp < TOTAL_TRACKER_THROTTLE_MS) return
            if (isFirstCall || profileChanged || trafficReset) {
                initializeTotals(
                    totalTraffic = totalTraffic,
                    profileId = currentProfileId,
                    forcePersist = true,
                )
                return
            }
            // 定期刷新待处理的关闭事件增量
            if (shouldFlushPendingBuckets(timestamp)) {
                drainedBuckets = drainPendingBucketsLocked()
                flushTimestamp = timestamp
            }
            initializeTotals(
                totalTraffic = totalTraffic,
                profileId = currentProfileId,
                forcePersist = false,
            )
        }
        if (!drainedBuckets.isNullOrEmpty()) {
            persistDrainedBuckets(timestamp = flushTimestamp, buckets = checkNotNull(drainedBuckets))
        }
    }

    private suspend fun handleConnectionJoin(joined: ConnectionInfo) {
        mutex.withLock {
            connectionBaselines[joined.id] =
                ConnectionBaseline(
                    upload = joined.upload,
                    download = joined.download,
                    hint = AppIdentityHint.from(joined.metadata),
                )
        }
    }

    private fun initializeTotals(
        totalTraffic: TrafficData,
        profileId: String?,
        forcePersist: Boolean,
    ) {
        lastTotalUpload = totalTraffic.upload
        lastTotalDownload = totalTraffic.download
        lastProfileId = profileId
        trafficStatisticsStore.setLastTraffic(
            upload = totalTraffic.upload,
            download = totalTraffic.download,
            profileId = profileId,
            forcePersist = forcePersist,
        )
    }

    private suspend fun handleConnectionClose(closed: ConnectionInfo) {
        mutex.withLock {
            val baseline = connectionBaselines.remove(closed.id) ?: return
            val uploadDelta = (closed.upload - baseline.upload).coerceAtLeast(0L)
            val downloadDelta = (closed.download - baseline.download).coerceAtLeast(0L)
            if (uploadDelta <= 0L && downloadDelta <= 0L) return
            val routeKey = resolveRouteKey(closed)
            val bucketKey = buildTrafficBucketKey(baseline.hint, routeKey)
            val existing = pendingBuckets[bucketKey]
            if (existing == null) {
                pendingBuckets[bucketKey] =
                    PendingTrafficBucket(
                        identityHint = baseline.hint,
                        routeKey = routeKey,
                        routeLabel = resolveRouteLabel(closed, routeKey),
                        uploadDelta = uploadDelta,
                        downloadDelta = downloadDelta,
                    )
            } else {
                pendingBuckets[bucketKey] =
                    existing.copy(
                        uploadDelta = existing.uploadDelta + uploadDelta,
                        downloadDelta = existing.downloadDelta + downloadDelta,
                    )
            }
        }
    }

    private suspend fun resetBaselines() {
        val drainedBuckets: List<PendingTrafficBucket>
        val flushTimestamp: Long
        mutex.withLock {
            drainedBuckets = drainPendingBucketsLocked()
            flushTimestamp = System.currentTimeMillis()
            connectionBaselines.clear()
            lastTotalUpload = NO_BASELINE
            lastTotalDownload = NO_BASELINE
            lastProfileId = null
        }
        if (drainedBuckets.isNotEmpty()) {
            persistDrainedBuckets(timestamp = flushTimestamp, buckets = drainedBuckets)
        }
        trafficStatisticsStore.flushNow()
    }

    private fun shouldFlushPendingBuckets(timestamp: Long): Boolean {
        if (pendingBuckets.isEmpty()) return false
        return timestamp - lastPersistTimestamp >= PERSIST_INTERVAL_MS
    }

    private fun drainPendingBucketsLocked(): List<PendingTrafficBucket> {
        if (pendingBuckets.isEmpty()) return emptyList()
        val drained = pendingBuckets.values.toList()
        pendingBuckets.clear()
        lastPersistTimestamp = System.currentTimeMillis()
        return drained
    }

    private fun persistDrainedBuckets(timestamp: Long, buckets: List<PendingTrafficBucket>) {
        val batch = buckets.mapNotNull { bucket ->
            if (bucket.uploadDelta <= 0L && bucket.downloadDelta <= 0L) {
                null
            } else {
                val identity = appIdentityResolver.resolve(
                    explicitPackageName = bucket.identityHint.packageName,
                    processName = bucket.identityHint.processName,
                    uid = bucket.identityHint.uid,
                )
                AppTrafficDeltaRecord(
                    appKey = identity.appKey,
                    packageName = identity.packageName,
                    appName = identity.appName,
                    uploadDelta = bucket.uploadDelta,
                    downloadDelta = bucket.downloadDelta,
                    routeKey = bucket.routeKey,
                    routeLabel = bucket.routeLabel,
                )
            }
        }
        if (batch.isEmpty()) return
        trafficStatisticsStore.recordAppTrafficBatch(timestamp = timestamp, records = batch)
    }

    private fun buildTrafficBucketKey(identityHint: AppIdentityHint, routeKey: String?): String {
        val baseKey = when {
            identityHint.packageName.isNotBlank() -> "pkg:${identityHint.packageName}"
            identityHint.uid != null && identityHint.uid > 0 -> "uid:${identityHint.uid}"
            identityHint.processName.isNotBlank() -> "process:${identityHint.processName}"
            else -> TrafficStatisticsBuckets.UNKNOWN_APP_KEY
        }
        return if (routeKey.isNullOrBlank()) baseKey else "$baseKey::$routeKey"
    }

    private fun resolveRouteKey(connection: ConnectionInfo): String =
        connection.chains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: connection.providerChains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: connection.rulePayload.takeIf(String::isNotBlank)
            ?: connection.rule.takeIf(String::isNotBlank)
            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY

    private fun resolveRouteLabel(connection: ConnectionInfo, routeKey: String?): String =
        connection.chains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: connection.providerChains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: routeKey
            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_NAME

    override fun stop() {
        // Cancel producers first so no new mutex.withLock { ... } blocks are entered.
        monitoringJob?.cancel()
        monitoringJob = null
        collectionJob?.cancel()
        collectionJob = null
        // Drain whatever is available without blocking — if the mutex is held by a concurrent persist, the remaining deltas are acceptable to lose at shutdown.
        val drainedBuckets = if (mutex.tryLock()) {
            try { drainPendingBucketsLocked() } finally { mutex.unlock() }
        } else { emptyList() }
        if (drainedBuckets.isNotEmpty()) {
            persistDrainedBuckets(timestamp = System.currentTimeMillis(), buckets = drainedBuckets)
        }
        trafficStatisticsStore.flushNow()
        connectionBaselines.clear()
        scope.cancel()
    }

    override fun close() = stop()

    private data class ConnectionBaseline(
        val upload: Long,
        val download: Long,
        val hint: AppIdentityHint,
    )

    private data class AppIdentityHint(
        val packageName: String,
        val processName: String,
        val uid: Int?,
    ) {
        companion object {
            fun from(metadata: JsonObject): AppIdentityHint {
                return AppIdentityHint(
                    packageName = metadata["packageName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    processName = metadata["process"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    uid = metadata["uid"]?.jsonPrimitive?.intOrNull,
                )
            }
        }
    }

    private data class PendingTrafficBucket(
        val identityHint: AppIdentityHint,
        val routeKey: String,
        val routeLabel: String,
        val uploadDelta: Long,
        val downloadDelta: Long,
    )

    companion object {
        private const val TAG = "AppTrafficStatsCollector"
        private const val NO_BASELINE = -1L
        private const val PERSIST_INTERVAL_MS = 10_000L
        private const val TOTAL_TRACKER_THROTTLE_MS = 5_000L
    }
}
