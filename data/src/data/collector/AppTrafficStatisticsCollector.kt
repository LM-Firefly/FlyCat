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

package com.github.yumelira.yumebox.data.collector

import com.github.yumelira.yumebox.core.contract.TrafficCollectorContract
import com.github.yumelira.yumebox.core.model.TrafficData
import com.github.yumelira.yumebox.core.model.AppIdentity
import com.github.yumelira.yumebox.core.model.AppTrafficDeltaRecord
import com.github.yumelira.yumebox.core.model.ConnectionInfo
import com.github.yumelira.yumebox.core.model.ConnectionTrafficBaseline
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TrafficStatisticsBuckets
import com.github.yumelira.yumebox.data.repository.AppIdentityResolver
import com.github.yumelira.yumebox.data.store.TrafficStatisticsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class AppTrafficStatisticsCollector(
    private val isRunningFlow: Flow<Boolean>,
    private val currentProfileId: () -> String?,
    private val trafficStatisticsStore: TrafficStatisticsStore,
    private val appIdentityResolver: AppIdentityResolver,
    private val trafficTotalFlow: StateFlow<Traffic>,
    private val connectionJoinFlow: SharedFlow<ConnectionInfo>,
    private val connectionCloseFlow: SharedFlow<ConnectionInfo>,
    private val queryActiveProfileId: suspend () -> String?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2)),
) : TrafficCollectorContract {
    private var collectionJob: Job? = null
    private var monitoringJob: Job? = null
    private val connectionBaselines = linkedMapOf<String, ConnectionTrafficBaseline>()
    private var lastTotalUpload = NO_BASELINE
    private var lastTotalDownload = NO_BASELINE
    private var lastProfileId: String? = null
    private var lastPersistTimestamp = 0L
    private val pendingDeltas = mutableListOf<AppTrafficDeltaRecord>()

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
            lastTotalUpload = trafficStatisticsStore.getLastTrafficUpload()
            lastTotalDownload = trafficStatisticsStore.getLastTrafficDownload()
            lastProfileId = trafficStatisticsStore.getLastProfileId()
            connectionBaselines.clear()
            launch {
                connectionJoinFlow.collect { joined ->
                    handleConnectionJoin(joined)
                }
            }
            launch {
                connectionCloseFlow.collect { closed ->
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
        val currentProfileId = currentProfileId() ?: runCatching { queryActiveProfileId() }.getOrNull()
        val isFirstCall = lastTotalUpload < 0L || lastTotalDownload < 0L
        val profileChanged = !isFirstCall && currentProfileId != lastProfileId
        val trafficReset = !isFirstCall && !profileChanged && (totalTraffic.upload < lastTotalUpload || totalTraffic.download < lastTotalDownload)
        if (isFirstCall || profileChanged || trafficReset) {
            initializeTotals(
                totalTraffic = totalTraffic,
                profileId = currentProfileId,
                forcePersist = true,
            )
            return
        }
        // 定期刷新待处理的关闭事件增量
        flushPendingDeltasIfNeeded(timestamp)
        initializeTotals(
            totalTraffic = totalTraffic,
            profileId = currentProfileId,
            forcePersist = false,
        )
    }

    private fun handleConnectionJoin(joined: ConnectionInfo) {
        val identity = appIdentityResolver.resolve(joined.metadata)
        connectionBaselines[joined.id] =
            ConnectionTrafficBaseline(
                id = joined.id,
                upload = joined.upload,
                download = joined.download,
                appKey = identity.appKey,
                packageName = identity.packageName,
                appName = identity.appName,
            )
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

    private fun handleConnectionClose(closed: ConnectionInfo) {
        val baseline = connectionBaselines.remove(closed.id) ?: return
        val uploadDelta = (closed.upload - baseline.upload).coerceAtLeast(0L)
        val downloadDelta = (closed.download - baseline.download).coerceAtLeast(0L)
        if (uploadDelta <= 0L && downloadDelta <= 0L) return
        val identity = AppIdentity(
            appKey = baseline.appKey,
            packageName = baseline.packageName,
            appName = baseline.appName,
        )
        val routeKey = closed.chains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: closed.providerChains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: closed.rulePayload.takeIf(String::isNotBlank)
            ?: closed.rule.takeIf(String::isNotBlank)
            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY
        val routeLabel = closed.chains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: closed.providerChains.lastOrNull()?.takeIf(String::isNotBlank)
            ?: routeKey
        pendingDeltas.add(
            AppTrafficDeltaRecord(
                appKey = identity.appKey,
                packageName = identity.packageName,
                appName = identity.appName,
                uploadDelta = uploadDelta,
                downloadDelta = downloadDelta,
                routeKey = routeKey,
                routeLabel = routeLabel,
            )
        )
    }

    private fun resetBaselines() {
        flushPendingDeltasNow()
        trafficStatisticsStore.flushNow()
        connectionBaselines.clear()
        lastTotalUpload = NO_BASELINE
        lastTotalDownload = NO_BASELINE
        lastProfileId = null
    }

    private fun flushPendingDeltasIfNeeded(timestamp: Long) {
        if (pendingDeltas.isEmpty()) return
        if (timestamp - lastPersistTimestamp < PERSIST_INTERVAL_MS) return
        flushPendingDeltasNow()
    }

    private fun flushPendingDeltasNow() {
        if (pendingDeltas.isEmpty()) return
        val batch = pendingDeltas.toList()
        pendingDeltas.clear()
        lastPersistTimestamp = System.currentTimeMillis()
        trafficStatisticsStore.recordAppTrafficBatch(timestamp = lastPersistTimestamp, records = batch)
    }

    private fun buildTrafficBucketKey(appKey: String, routeKey: String?): String =
        if (routeKey.isNullOrBlank()) appKey else "$appKey::$routeKey"

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
        flushPendingDeltasNow()
        trafficStatisticsStore.flushNow()
        monitoringJob?.cancel()
        monitoringJob = null
        collectionJob?.cancel()
        collectionJob = null
        connectionBaselines.clear()
        scope.cancel()
    }

    override fun close() = stop()

    companion object {
        private const val TAG = "AppTrafficStatsCollector"
        private const val NO_BASELINE = -1L
        private const val PERSIST_INTERVAL_MS = 10_000L
    }
}
