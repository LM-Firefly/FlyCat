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

package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.core.data.TrafficStatisticsReader
import com.github.yumelira.yumebox.core.model.AppRouteTrafficUsage
import com.github.yumelira.yumebox.core.model.AppTrafficDeltaRecord
import com.github.yumelira.yumebox.core.model.AppTrafficUsage
import com.github.yumelira.yumebox.core.model.DailyAppTrafficSummary
import com.github.yumelira.yumebox.core.model.DailyRouteTrafficSummary
import com.github.yumelira.yumebox.core.model.DailyTrafficSummary
import com.github.yumelira.yumebox.core.model.StatisticsTimeRange
import com.github.yumelira.yumebox.core.model.TrafficStatisticsBuckets
import com.github.yumelira.yumebox.data.store.room.AppTrafficDelta
import com.github.yumelira.yumebox.data.store.room.RouteTrafficDelta
import com.github.yumelira.yumebox.data.store.room.TrafficStatisticsDao
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Calendar

class TrafficStatisticsStore(private val mmkv: MMKV, private val dao: TrafficStatisticsDao) : TrafficStatisticsReader {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var flushJob: Job? = null
    private var dailyAppDirty = false
    private var dailyRouteDirty = false
    private var lastTrafficDirty = false
    private var lastTrafficUpload = NO_PERSISTED_TRAFFIC
    private var lastTrafficDownload = NO_PERSISTED_TRAFFIC
    private var lastProfileId: String? = null
    private val dailyAppData = LinkedHashMap<Long, LinkedHashMap<String, AppTrafficUsage>>()
    private val dailyRouteData = LinkedHashMap<Long, LinkedHashMap<String, LinkedHashMap<String, AppRouteTrafficUsage>>>()

    private val _dailyAppSummaries = MutableStateFlow<Map<Long, DailyAppTrafficSummary>>(emptyMap())
    override val dailyAppSummaries: StateFlow<Map<Long, DailyAppTrafficSummary>> =
        _dailyAppSummaries.asStateFlow()
    private val _dailyRouteSummaries =
        MutableStateFlow<Map<Long, DailyRouteTrafficSummary>>(emptyMap())

    init {
        upgradeSchemaIfNeeded()
        loadData()
    }

    fun recordAppTrafficBatch(timestamp: Long, records: List<AppTrafficDeltaRecord>) {
        if (records.isEmpty()) return

        val dayKey = getDayKey(timestamp)
        var hasChanges = false
        var routeChanges = false

        synchronized(lock) {
            records.forEach { record ->
                if (record.uploadDelta <= 0L && record.downloadDelta <= 0L) return@forEach
                val dayAppUsages = dailyAppData.getOrPut(dayKey) { linkedMapOf() }
                val currentUsage =
                    dayAppUsages[record.appKey]
                    ?: AppTrafficUsage(
                        appKey = record.appKey,
                        packageName = record.packageName,
                        appName = record.appName,
                    )
                    dayAppUsages[record.appKey] = currentUsage.copy(
                    packageName = record.packageName ?: currentUsage.packageName,
                    appName = record.appName.ifBlank { currentUsage.appName },
                    totalUpload = currentUsage.totalUpload + record.uploadDelta,
                    totalDownload = currentUsage.totalDownload + record.downloadDelta,
                    lastActiveAt = timestamp,
                )
            hasChanges = true

            val routeKey =
                record.routeKey?.takeIf(String::isNotBlank)
                    ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY
            val routeLabel =
                record.routeLabel?.takeIf(String::isNotBlank)
                    ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_NAME
            val appRouteUsages =
                dailyRouteData.getOrPut(dayKey) { linkedMapOf() }.getOrPut(record.appKey) { linkedMapOf() }
            val currentRouteUsage =
                appRouteUsages[routeKey] ?: AppRouteTrafficUsage(
                    appKey = record.appKey,
                    routeKey = routeKey,
                    routeLabel = routeLabel,
                )
                appRouteUsages[routeKey] = currentRouteUsage.copy(
                    routeLabel = routeLabel,
                    totalUpload = currentRouteUsage.totalUpload + record.uploadDelta,
                    totalDownload = currentRouteUsage.totalDownload + record.downloadDelta,
                    lastActiveAt = timestamp,
                )
                routeChanges = true
            }
            if (!hasChanges) return
        }
        val appDaySnapshot = synchronized(lock) {
            DailyAppTrafficSummary(dateMillis = dayKey, appUsages = HashMap(dailyAppData[dayKey] ?: emptyMap()))
        }
        _dailyAppSummaries.update { it + (dayKey to appDaySnapshot) }
        if (routeChanges) {
            val routeDaySnapshot = synchronized(lock) {
                DailyRouteTrafficSummary(
                    dateMillis = dayKey,
                    routeUsagesByApp = dailyRouteData[dayKey]
                        ?.mapValues { (_, routeUsages) -> HashMap(routeUsages) } ?: emptyMap(),
                )
            }
            _dailyRouteSummaries.update { it + (dayKey to routeDaySnapshot) }
        }
        trimOldDataIfNeeded()
        markDailyDataDirty(routeChanges = routeChanges)

        // Persist to Room in background
        storeScope.launch {
            runCatching {
                val appDeltas = records.mapNotNull { record ->
                    if (record.uploadDelta <= 0L && record.downloadDelta <= 0L) return@mapNotNull null
                    AppTrafficDelta(
                        dateMillis = dayKey,
                        appKey = record.appKey,
                        packageName = record.packageName,
                        appName = record.appName,
                        uploadDelta = record.uploadDelta,
                        downloadDelta = record.downloadDelta,
                        lastActiveAt = timestamp,
                    )
                }
                val routeDeltas = records.mapNotNull { record ->
                    if (record.uploadDelta <= 0L && record.downloadDelta <= 0L) return@mapNotNull null
                    RouteTrafficDelta(
                        dateMillis = dayKey,
                        appKey = record.appKey,
                        routeKey = record.routeKey?.takeIf(String::isNotBlank)
                            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY,
                        routeLabel = record.routeLabel?.takeIf(String::isNotBlank)
                            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_NAME,
                        uploadDelta = record.uploadDelta,
                        downloadDelta = record.downloadDelta,
                        lastActiveAt = timestamp,
                    )
                }
                dao.recordBatch(
                    appDeltas = appDeltas,
                    routeDeltas = routeDeltas,
                    retentionCutoffMillis = System.currentTimeMillis() - (MAX_APP_DAYS_TO_KEEP * DAY_MS),
                )
            }.onFailure { Timber.e(it, "Failed to persist traffic to Room") }
        }
    }

    override fun getAppUsagesSorted(range: StatisticsTimeRange): List<AppTrafficUsage> =
        aggregateAppUsages(days = range.days)

    override fun getTotalSummary(range: StatisticsTimeRange): DailyTrafficSummary =
        buildTotalSummary(days = range.days)

    override fun clearAll() {
        flushJob?.cancel()
        synchronized(lock) {
            dailyAppData.clear()
            dailyRouteData.clear()
            dailyAppDirty = false
            dailyRouteDirty = false
            lastTrafficDirty = false
            lastTrafficUpload = NO_PERSISTED_TRAFFIC
            lastTrafficDownload = NO_PERSISTED_TRAFFIC
            lastProfileId = null
        }
        _dailyAppSummaries.value = emptyMap()
        _dailyRouteSummaries.value = emptyMap()
        mmkv.removeValueForKey(KEY_DAILY_APP_SUMMARIES)
        mmkv.removeValueForKey(KEY_DAILY_ROUTE_SUMMARIES)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
        storeScope.launch {
            runCatching { dao.clearAll() }
                .onFailure { Timber.e(it, "Failed to clear Room traffic data") }
        }
    }

    fun flushNow() {
        flushJob?.cancel()
        flushPendingData()
    }

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
            if (lastTrafficUpload != upload) {
                lastTrafficUpload = upload
                changed = true
            }
            if (lastTrafficDownload != download) {
                lastTrafficDownload = download
                changed = true
            }
            if (lastProfileId != profileId) {
                lastProfileId = profileId
                changed = true
            }
            if (changed) {
                lastTrafficDirty = true
            }
        }

        if (!changed) return

        if (forcePersist) {
            flushNow()
        } else {
            scheduleFlush()
        }
    }

    private fun loadData() {
        lastTrafficUpload = mmkv.decodeLong(KEY_LAST_TRAFFIC_UPLOAD, NO_PERSISTED_TRAFFIC)
        lastTrafficDownload = mmkv.decodeLong(KEY_LAST_TRAFFIC_DOWNLOAD, NO_PERSISTED_TRAFFIC)
        lastProfileId = mmkv.decodeString(KEY_LAST_PROFILE_ID)
        mmkv.decodeString(KEY_DAILY_APP_SUMMARIES)?.let { jsonStr ->
            runCatching {
                val summaries: Map<Long, DailyAppTrafficSummary> = json.decodeFromString(jsonStr)
                val cleaned = cleanOldDailyAppData(summaries.toMutableMap())
                synchronized(lock) {
                    cleaned.forEach { (dayKey, summary) ->
                        dailyAppData[dayKey] = LinkedHashMap(summary.appUsages)
                    }
                }
                _dailyAppSummaries.value = cleaned
            }
        }
        mmkv.decodeString(KEY_DAILY_ROUTE_SUMMARIES)?.let { jsonStr ->
            runCatching {
                val summaries: Map<Long, DailyRouteTrafficSummary> = json.decodeFromString(jsonStr)
                val cleaned = cleanOldDailyRouteData(summaries.toMutableMap())
                synchronized(lock) {
                    cleaned.forEach { (dayKey, summary) ->
                        dailyRouteData[dayKey] = summary.routeUsagesByApp
                            .mapValuesTo(linkedMapOf()) { (_, routeUsages) -> LinkedHashMap(routeUsages) }
                    }
                }
                _dailyRouteSummaries.value = cleaned
            }
        }
    }

    private fun aggregateAppUsages(days: Int): List<AppTrafficUsage> {
        val aggregated = linkedMapOf<String, AppTrafficUsage>()
        rangeDayKeys(days).forEach { dayKey ->
            _dailyAppSummaries.value[dayKey]?.appUsages?.values?.forEach { usage ->
                val existing = aggregated[usage.appKey]
                aggregated[usage.appKey] =
                    if (existing == null) {
                        usage
                    } else {
                        existing.copy(
                            packageName = existing.packageName ?: usage.packageName,
                            appName =
                                if (existing.appName.isNotBlank()) {
                                    existing.appName
                                } else {
                                    usage.appName
                                },
                            totalUpload = existing.totalUpload + usage.totalUpload,
                            totalDownload = existing.totalDownload + usage.totalDownload,
                            lastActiveAt = maxOf(existing.lastActiveAt, usage.lastActiveAt),
                        )
                    }
            }
        }
        val unattributed = aggregated.remove(TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY)
        val sortedApps = aggregated.values.sortedByDescending(AppTrafficUsage::totalBytes)
        return buildList(sortedApps.size + if (unattributed != null) 1 else 0) {
            addAll(sortedApps)
            unattributed?.let(::add)
        }
    }

    private fun buildTotalSummary(days: Int): DailyTrafficSummary {
        val usages = aggregateAppUsages(days)
        return DailyTrafficSummary(
            dateMillis = System.currentTimeMillis(),
            totalUpload = usages.sumOf(AppTrafficUsage::totalUpload),
            totalDownload = usages.sumOf(AppTrafficUsage::totalDownload),
        )
    }

    private fun rangeDayKeys(days: Int): List<Long> =
        List(days) { offset ->
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            getDayKey(calendar.timeInMillis)
        }

    private fun getDayKey(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Prunes in-memory day-buckets and StateFlow maps when they exceed [MAX_APP_DAYS_TO_KEEP].
     * Called after each batch write to bound growth between cold-start cleans.
     */
    private fun trimOldDataIfNeeded() {
        if (dailyAppData.size <= MAX_APP_DAYS_TO_KEEP &&
            dailyRouteData.size <= MAX_APP_DAYS_TO_KEEP
        ) return
        val cutoffTime = System.currentTimeMillis() - (MAX_APP_DAYS_TO_KEEP * DAY_MS)
        synchronized(lock) {
            if (dailyAppData.size > MAX_APP_DAYS_TO_KEEP) {
                dailyAppData.keys.removeAll { it < cutoffTime }
            }
            if (dailyRouteData.size > MAX_APP_DAYS_TO_KEEP) {
                dailyRouteData.keys.removeAll { it < cutoffTime }
            }
        }
        if (_dailyAppSummaries.value.size > MAX_APP_DAYS_TO_KEEP) {
            _dailyAppSummaries.update { current -> current.filterKeys { it >= cutoffTime } }
        }
        if (_dailyRouteSummaries.value.size > MAX_APP_DAYS_TO_KEEP) {
            _dailyRouteSummaries.update { current -> current.filterKeys { it >= cutoffTime } }
        }
    }

    private fun cleanOldDailyAppData(
        summaries: MutableMap<Long, DailyAppTrafficSummary>
    ): Map<Long, DailyAppTrafficSummary> {
        val cutoffTime = System.currentTimeMillis() - (MAX_APP_DAYS_TO_KEEP * DAY_MS)
        return summaries.filterKeys { it >= cutoffTime }
    }

    private fun cleanOldDailyRouteData(
        summaries: MutableMap<Long, DailyRouteTrafficSummary>
    ): Map<Long, DailyRouteTrafficSummary> {
        val cutoffTime = System.currentTimeMillis() - (MAX_APP_DAYS_TO_KEEP * DAY_MS)
        return summaries.filterKeys { it >= cutoffTime }
    }

    private fun markDailyDataDirty(routeChanges: Boolean) {
        synchronized(lock) {
            dailyAppDirty = true
            if (routeChanges) {
                dailyRouteDirty = true
            }
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = storeScope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            flushPendingData()
        }
    }

    private fun flushPendingData() {
        val dailyAppSnapshot: Map<Long, DailyAppTrafficSummary>?
        val dailyRouteSnapshot: Map<Long, DailyRouteTrafficSummary>?
        val trafficSnapshot: Triple<Long, Long, String?>?

        synchronized(lock) {
            dailyAppSnapshot = if (dailyAppDirty) _dailyAppSummaries.value else null
            dailyRouteSnapshot = if (dailyRouteDirty) _dailyRouteSummaries.value else null
            trafficSnapshot =
                if (lastTrafficDirty) {
                    Triple(lastTrafficUpload, lastTrafficDownload, lastProfileId)
                } else {
                    null
                }
            dailyAppDirty = false
            dailyRouteDirty = false
            lastTrafficDirty = false
        }

        dailyAppSnapshot?.let { summaries ->
            runCatching { mmkv.encode(KEY_DAILY_APP_SUMMARIES, json.encodeToString(summaries)) }
                .onFailure { e ->
                    Timber.e(e, "Failed to flush daily app traffic summaries")
                    synchronized(lock) { dailyAppDirty = true }
                }
        }
        dailyRouteSnapshot?.let { summaries ->
            runCatching { mmkv.encode(KEY_DAILY_ROUTE_SUMMARIES, json.encodeToString(summaries)) }
                .onFailure { e ->
                    Timber.e(e, "Failed to flush daily route traffic summaries")
                    synchronized(lock) { dailyRouteDirty = true }
                }
        }

        trafficSnapshot?.let { (upload, download, profileId) ->
            runCatching {
                mmkv.encode(KEY_LAST_TRAFFIC_UPLOAD, upload)
                mmkv.encode(KEY_LAST_TRAFFIC_DOWNLOAD, download)
                if (profileId.isNullOrBlank()) {
                    mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
                } else {
                    mmkv.encode(KEY_LAST_PROFILE_ID, profileId)
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to flush last traffic snapshot")
                synchronized(lock) { lastTrafficDirty = true }
            }
        }
    }

    private fun upgradeSchemaIfNeeded() {
        val currentVersion = mmkv.decodeInt(KEY_STATS_SCHEMA_VERSION, 0)
        if (currentVersion >= CURRENT_STATS_SCHEMA_VERSION) {
            return
        }
        clearAllForSchemaUpgrade()
        mmkv.encode(KEY_STATS_SCHEMA_VERSION, CURRENT_STATS_SCHEMA_VERSION)
    }

    private fun clearAllForSchemaUpgrade() {
        synchronized(lock) {
            dailyAppData.clear()
            dailyRouteData.clear()
            dailyAppDirty = false
            dailyRouteDirty = false
            lastTrafficDirty = false
            lastTrafficUpload = NO_PERSISTED_TRAFFIC
            lastTrafficDownload = NO_PERSISTED_TRAFFIC
            lastProfileId = null
        }
        _dailyAppSummaries.value = emptyMap()
        _dailyRouteSummaries.value = emptyMap()
        mmkv.removeValueForKey(KEY_DAILY_APP_SUMMARIES)
        mmkv.removeValueForKey(KEY_DAILY_ROUTE_SUMMARIES)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
        mmkv.removeValueForKey(LEGACY_KEY_DAILY_SUMMARIES)
        mmkv.removeValueForKey(LEGACY_KEY_PROFILE_USAGES)
        mmkv.removeValueForKey(LEGACY_KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(LEGACY_KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(LEGACY_KEY_LAST_PROFILE_ID)
    }

    companion object {
        private const val KEY_DAILY_APP_SUMMARIES = "daily_app_summaries_v2"
        private const val KEY_DAILY_ROUTE_SUMMARIES = "daily_route_summaries_v2"
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
        private const val FLUSH_DEBOUNCE_MS = 30_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_APP_DAYS_TO_KEEP = 90
        private const val NO_PERSISTED_TRAFFIC = -1L
    }
}
