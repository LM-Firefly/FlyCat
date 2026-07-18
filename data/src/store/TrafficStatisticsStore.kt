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

package com.github.lmfirefly.flycat.data.store

import com.github.lmfirefly.flycat.core.contract.TrafficStatisticsRepository
import com.github.lmfirefly.flycat.core.model.traffic.AppRouteTrafficUsage
import com.github.lmfirefly.flycat.core.model.traffic.AppTrafficDeltaRecord
import com.github.lmfirefly.flycat.core.model.traffic.AppTrafficUsage
import com.github.lmfirefly.flycat.core.model.traffic.DailyTraffic
import com.github.lmfirefly.flycat.core.model.traffic.StatisticsTimeRange
import com.github.lmfirefly.flycat.core.model.traffic.TimeSlotTraffic
import com.github.lmfirefly.flycat.core.model.traffic.TrafficStatisticsBuckets
import com.github.lmfirefly.flycat.data.store.room.AppTrafficDelta
import com.github.lmfirefly.flycat.data.store.room.RouteTrafficDelta
import com.github.lmfirefly.flycat.data.store.room.TrafficStatisticsDao
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

/**
 * Facade over traffic statistics persistence.
 *
 * Daily per-app / per-route aggregation lives in Room (see [TrafficStatisticsDao]); each record is a
 * row-level accumulate rather than a whole-history JSON rewrite. The only state that stays in MMKV is
 * the collector's resume baseline (last total upload/download + profile id), kept synchronous.
 */
class TrafficStatisticsStore(
    private val mmkv: MMKV,
    private val dao: TrafficStatisticsDao,
) : TrafficStatisticsRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val dayCalendar: ThreadLocal<Calendar> = ThreadLocal.withInitial { Calendar.getInstance() }
    private val lock = Any()
    private var lastTrafficDirty = false
    private var lastTrafficUpload = NO_PERSISTED_TRAFFIC
    private var lastTrafficDownload = NO_PERSISTED_TRAFFIC
    private var lastProfileId: String? = null
    private var pendingFlushJob: Job? = null
    init { upgradeSchemaIfNeeded(); loadData() }
    fun recordAppTrafficBatch(timestamp: Long, records: List<AppTrafficDeltaRecord>) {
        if (records.isEmpty()) return
        val dayKey = getDayKey(timestamp)
        storeScope.launch {
            runCatching {
                val merged = LinkedHashMap<String, AppTrafficDeltaRecord>()
                for (record in records) {
                    if (record.uploadDelta <= 0L && record.downloadDelta <= 0L) continue
                    val normalizedRouteKey =
                        record.routeKey?.takeIf(String::isNotBlank)
                            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY
                    val mergeKey = "${record.appKey}::$normalizedRouteKey"
                    val existing = merged[mergeKey]
                    if (existing == null) {
                        merged[mergeKey] =
                            record.copy(
                                routeKey = normalizedRouteKey,
                                routeLabel =
                                    record.routeLabel?.takeIf(String::isNotBlank)
                                        ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_NAME,
                            )
                    } else {
                        merged[mergeKey] =
                            existing.copy(
                                uploadDelta = existing.uploadDelta + record.uploadDelta,
                                downloadDelta = existing.downloadDelta + record.downloadDelta,
                                packageName = existing.packageName ?: record.packageName,
                                appName =
                                    if (existing.appName.isNotBlank()) existing.appName
                                    else record.appName,
                                routeLabel =
                                    existing.routeLabel?.takeIf(String::isNotBlank)
                                        ?: record.routeLabel,
                            )
                    }
                }
                val appDeltas = mutableListOf<AppTrafficDelta>()
                val routeDeltas = mutableListOf<RouteTrafficDelta>()
                val slotIndex = getSlotIndex(timestamp)
                for (record in merged.values) {
                    if (record.uploadDelta <= 0L && record.downloadDelta <= 0L) continue
                    appDeltas.add(AppTrafficDelta(dateMillis = dayKey, appKey = record.appKey, slotIndex = slotIndex, packageName = record.packageName, appName = record.appName, uploadDelta = record.uploadDelta, downloadDelta = record.downloadDelta, lastActiveAt = timestamp))
                    routeDeltas.add(RouteTrafficDelta(dateMillis = dayKey, appKey = record.appKey, routeKey = record.routeKey?.takeIf(String::isNotBlank)?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY, slotIndex = slotIndex, routeLabel = record.routeLabel?.takeIf(String::isNotBlank)?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_NAME, uploadDelta = record.uploadDelta, downloadDelta = record.downloadDelta, lastActiveAt = timestamp))
                }
                dao.recordBatch(appDeltas = appDeltas, routeDeltas = routeDeltas, retentionCutoffMillis = System.currentTimeMillis() - (MAX_APP_DAYS_TO_KEEP * DAY_MS))
            }.onFailure { Timber.e(it, "Failed to persist traffic to Room") }
        }
    }

    override fun getAppUsagesFlow(range: StatisticsTimeRange): Flow<List<AppTrafficUsage>> =
        dao.getAppUsagesFlow(rangeCutoff(range))

    override fun getTimeSlotTrafficFlow(range: StatisticsTimeRange): Flow<List<TimeSlotTraffic>> =
        dao.getTimeSlotTrafficFlow(rangeCutoff(range))

    override fun getDailyTrafficFlow(range: StatisticsTimeRange): Flow<List<DailyTraffic>> =
        dao.getDailyTrafficFlow(rangeCutoff(range))

    override suspend fun getAppUsagesSorted(range: StatisticsTimeRange): List<AppTrafficUsage> =
        dao.getAppUsagesSorted(rangeCutoff(range))

    suspend fun getAppRouteUsages(
        appKey: String,
        range: StatisticsTimeRange,
    ): List<AppRouteTrafficUsage> {
        if (appKey.isBlank()) return emptyList()
        return dao.getAppRouteUsages(appKey, rangeCutoff(range))
    }

    override fun clearAll() {
        synchronized(lock) {
            lastTrafficDirty = false
            lastTrafficUpload = NO_PERSISTED_TRAFFIC
            lastTrafficDownload = NO_PERSISTED_TRAFFIC
            lastProfileId = null
        }
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
        storeScope.launch { runCatching { dao.clearAll() }.onFailure { Timber.e(it, "Failed to clear Room traffic data") } }
    }

    fun flushNow() { flushPendingData() }

    fun getLastTrafficUpload(): Long = synchronized(lock) { lastTrafficUpload }

    fun getLastTrafficDownload(): Long = synchronized(lock) { lastTrafficDownload }

    fun getLastProfileId(): String? = synchronized(lock) { lastProfileId }

    fun setLastTraffic(
        upload: Long,
        download: Long,
        profileId: String?,
        forcePersist: Boolean = false,
    ) {
        var changed = false
        synchronized(lock) {
            if (lastTrafficUpload != upload) { lastTrafficUpload = upload; changed = true }
            if (lastTrafficDownload != download) { lastTrafficDownload = download; changed = true }
            if (lastProfileId != profileId) { lastProfileId = profileId; changed = true }
            if (changed) { lastTrafficDirty = true }
        }
        if (!changed) return
        if (forcePersist) {
            cancelPendingFlush()
            flushNow()
        } else {
            scheduleFlush()
        }
    }

    private fun loadData() {
        lastTrafficUpload = mmkv.decodeLong(KEY_LAST_TRAFFIC_UPLOAD, NO_PERSISTED_TRAFFIC)
        lastTrafficDownload = mmkv.decodeLong(KEY_LAST_TRAFFIC_DOWNLOAD, NO_PERSISTED_TRAFFIC)
        lastProfileId = mmkv.decodeString(KEY_LAST_PROFILE_ID)
    }

    private fun getDayKey(timestamp: Long): Long {
        val calendar = checkNotNull(dayCalendar.get()).apply { timeInMillis = timestamp }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** Returns 0–11, each representing a2-hour bucket: 0=[0:00–2:00), 1=[2:00–4:00), …, 11=[22:00–24:00). */
    private fun getSlotIndex(timestamp: Long): Int {
        val calendar = checkNotNull(dayCalendar.get()).apply { timeInMillis = timestamp }
        return calendar.get(Calendar.HOUR_OF_DAY) / 2
    }

    private fun rangeCutoff(range: StatisticsTimeRange): Long = getDayKey(System.currentTimeMillis()) - ((range.days - 1) * DAY_MS)

    private fun flushPendingData() {
        val trafficSnapshot: Triple<Long, Long, String?>?
        synchronized(lock) {
            pendingFlushJob = null
            trafficSnapshot = if (lastTrafficDirty) { Triple(lastTrafficUpload, lastTrafficDownload, lastProfileId) } else { null }
            lastTrafficDirty = false
        }
        trafficSnapshot?.let { (upload, download, profileId) ->
            runCatching {
                mmkv.encode(KEY_LAST_TRAFFIC_UPLOAD, upload)
                mmkv.encode(KEY_LAST_TRAFFIC_DOWNLOAD, download)
                if (profileId.isNullOrBlank()) { mmkv.removeValueForKey(KEY_LAST_PROFILE_ID) } else { mmkv.encode(KEY_LAST_PROFILE_ID, profileId) }
            }.onFailure { e ->
                Timber.e(e, "Failed to flush last traffic snapshot")
                synchronized(lock) { lastTrafficDirty = true }
            }
        }
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (pendingFlushJob?.isActive == true) return
            pendingFlushJob = storeScope.launch {
                delay(BASELINE_FLUSH_INTERVAL_MS)
                flushPendingData()
            }
        }
    }

    private fun cancelPendingFlush() {
        synchronized(lock) {
            pendingFlushJob?.cancel()
            pendingFlushJob = null
        }
    }

    private fun upgradeSchemaIfNeeded() {
        val currentVersion = mmkv.decodeInt(KEY_STATS_SCHEMA_VERSION, 0)
        if (currentVersion >= CURRENT_STATS_SCHEMA_VERSION) { return }
        clearAllForSchemaUpgrade()
        mmkv.encode(KEY_STATS_SCHEMA_VERSION, CURRENT_STATS_SCHEMA_VERSION)
    }

    private fun clearAllForSchemaUpgrade() {
        synchronized(lock) { lastTrafficDirty = false; lastTrafficUpload = NO_PERSISTED_TRAFFIC; lastTrafficDownload = NO_PERSISTED_TRAFFIC; lastProfileId = null }
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
        mmkv.removeValueForKey(LEGACY_KEY_DAILY_SUMMARIES)
        mmkv.removeValueForKey(LEGACY_KEY_PROFILE_USAGES)
        mmkv.removeValueForKey(LEGACY_KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(LEGACY_KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(LEGACY_KEY_LAST_PROFILE_ID)
    }

    fun close() {
        storeScope.cancel()
    }

    companion object {
        private const val KEY_LAST_TRAFFIC_UPLOAD = "last_traffic_upload_v2"
        private const val KEY_LAST_TRAFFIC_DOWNLOAD = "last_traffic_download_v2"
        private const val KEY_LAST_PROFILE_ID = "last_profile_id_v2"
        private const val KEY_STATS_SCHEMA_VERSION = "traffic_stats_schema_version"
        private const val LEGACY_KEY_DAILY_SUMMARIES = "daily_summaries"
        private const val LEGACY_KEY_PROFILE_USAGES = "profile_usages"
        private const val LEGACY_KEY_LAST_TRAFFIC_UPLOAD = "last_traffic_upload"
        private const val LEGACY_KEY_LAST_TRAFFIC_DOWNLOAD = "last_traffic_download"
        private const val LEGACY_KEY_LAST_PROFILE_ID = "last_profile_id"
        private const val CURRENT_STATS_SCHEMA_VERSION = 3
        private const val BASELINE_FLUSH_INTERVAL_MS = 10_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_APP_DAYS_TO_KEEP = 90
        private const val NO_PERSISTED_TRAFFIC = -1L
    }
}
