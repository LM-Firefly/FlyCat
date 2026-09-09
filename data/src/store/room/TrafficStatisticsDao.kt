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

package com.github.lmfirefly.flycat.data.store.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import com.github.lmfirefly.flycat.core.model.traffic.AppRouteTrafficUsage
import com.github.lmfirefly.flycat.core.model.traffic.AppTrafficUsage
import com.github.lmfirefly.flycat.core.model.traffic.DailyTraffic
import com.github.lmfirefly.flycat.core.model.traffic.TimeSlotTraffic
import kotlinx.coroutines.flow.Flow

/**
 * 用于流量统计的房间数据访问对象。
 * 声明为抽象类（而非接口），以便先插入后持久化的逻辑能存在于具体的 `@Transaction` 方法中，这些方法将调用生成的查询/上插原语。
 * 写入模型——进入SQL循环前，增量数据在内存中通过复合主键进行预合并，然后通过Room的`@Upsert`（`INSERT … ON CONFLICT DO UPDATE`）写入。
 */
@Dao
abstract class TrafficStatisticsDao {

    // 区域写入（更新插入）

    /**
     * 在单个事务中记录一批应用与路由的增量变更。
     * 增量在内存中根据组合主键预先合并，然后才执行SQL，从而避免单批次内对相同单元格的重复调用。
     * 合并后的行通过 `@Upsert`（INSERT … ON CONFLICT DO UPDATE）写入。
     * 两个增量均 `<= 0` 的记录将被跳过（调用方已过滤，但保留此防护检查）。
     */
    @Transaction
    open suspend fun recordBatch(
        appDeltas: List<AppTrafficDelta>,
        routeDeltas: List<RouteTrafficDelta>,
    ) {
        // --- 合并前应用变更（按 dateMillis, appKey, slotIndex）---
        val mergedApp = LinkedHashMap<AppKey, AppTrafficDailyEntity>()
        for (delta in appDeltas) {
            if (delta.uploadDelta <= 0L && delta.downloadDelta <= 0L) continue
            val key = AppKey(delta.dateMillis, delta.appKey, delta.slotIndex)
            val existing = mergedApp[key]
            if (existing == null) {
                mergedApp[key] =
                    AppTrafficDailyEntity(
                        dateMillis = delta.dateMillis,
                        appKey = delta.appKey,
                        slotIndex = delta.slotIndex,
                        packageName = delta.packageName,
                        appName = delta.appName,
                        totalUpload = delta.uploadDelta,
                        totalDownload = delta.downloadDelta,
                        lastActiveAt = delta.lastActiveAt,
                    )
            } else {
                mergedApp[key] =
                    existing.copy(
                        totalUpload = existing.totalUpload + delta.uploadDelta,
                        totalDownload = existing.totalDownload + delta.downloadDelta,
                        packageName = existing.packageName ?: delta.packageName,
                        appName = if (existing.appName.isNotBlank()) existing.appName else delta.appName,
                        lastActiveAt = maxOf(existing.lastActiveAt, delta.lastActiveAt),
                    )
            }
        }

        // --- 合并路由差异按 (dateMillis, appKey, routeKey, slotIndex) ---
        val mergedRoute = LinkedHashMap<RouteKey, RouteTrafficDailyEntity>()
        for (delta in routeDeltas) {
            if (delta.uploadDelta <= 0L && delta.downloadDelta <= 0L) continue
            val key = RouteKey(delta.dateMillis, delta.appKey, delta.routeKey, delta.slotIndex)
            val existing = mergedRoute[key]
            if (existing == null) {
                mergedRoute[key] =
                    RouteTrafficDailyEntity(
                        dateMillis = delta.dateMillis,
                        appKey = delta.appKey,
                        routeKey = delta.routeKey,
                        slotIndex = delta.slotIndex,
                        routeLabel = delta.routeLabel,
                        totalUpload = delta.uploadDelta,
                        totalDownload = delta.downloadDelta,
                        lastActiveAt = delta.lastActiveAt,
                    )
            } else {
                mergedRoute[key] =
                    existing.copy(
                        totalUpload = existing.totalUpload + delta.uploadDelta,
                        totalDownload = existing.totalDownload + delta.downloadDelta,
                        routeLabel = if (existing.routeLabel.isNotBlank()) existing.routeLabel else delta.routeLabel,
                        lastActiveAt = maxOf(existing.lastActiveAt, delta.lastActiveAt),
                    )
            }
        }

        if (mergedApp.isNotEmpty()) upsertApp(mergedApp.values.toList())
        if (mergedRoute.isNotEmpty()) upsertRoute(mergedRoute.values.toList())
    }

    // 带累加的更新插入：冲突时将增量添加到现有总数，而非替换。
    @Query(
        """
        INSERT INTO app_traffic_daily (date_millis, app_key, slot_index, package_name, app_name, total_upload, total_download, last_active_at)
        VALUES (:dateMillis, :appKey, :slotIndex, :packageName, :appName, :totalUpload, :totalDownload, :lastActiveAt)
        ON CONFLICT (date_millis, app_key, slot_index)
        DO UPDATE SET
            total_upload = total_upload + excluded.total_upload,
            total_download = total_download + excluded.total_download,
            package_name = COALESCE(excluded.package_name, package_name),
            app_name = CASE WHEN app_name = '' THEN excluded.app_name ELSE app_name END,
            last_active_at = MAX(last_active_at, excluded.last_active_at)
        """
    )
    protected abstract suspend fun upsertOneApp(
        dateMillis: Long,
        appKey: String,
        slotIndex: Int,
        packageName: String?,
        appName: String,
        totalUpload: Long,
        totalDownload: Long,
        lastActiveAt: Long,
    )

    @Query(
        """
        INSERT INTO route_traffic_daily (date_millis, app_key, route_key, slot_index, route_label, total_upload, total_download, last_active_at)
        VALUES (:dateMillis, :appKey, :routeKey, :slotIndex, :routeLabel, :totalUpload, :totalDownload, :lastActiveAt)
        ON CONFLICT (date_millis, app_key, route_key, slot_index)
        DO UPDATE SET
            total_upload = total_upload + excluded.total_upload,
            total_download = total_download + excluded.total_download,
            route_label = CASE WHEN route_label = '' THEN excluded.route_label ELSE route_label END,
            last_active_at = MAX(last_active_at, excluded.last_active_at)
        """
    )
    protected abstract suspend fun upsertOneRoute(
        dateMillis: Long,
        appKey: String,
        routeKey: String,
        slotIndex: Int,
        routeLabel: String,
        totalUpload: Long,
        totalDownload: Long,
        lastActiveAt: Long,
    )

    private suspend fun upsertApp(entities: List<AppTrafficDailyEntity>) {
        for (e in entities) {
            upsertOneApp(e.dateMillis, e.appKey, e.slotIndex, e.packageName, e.appName, e.totalUpload, e.totalDownload, e.lastActiveAt)
        }
    }

    private suspend fun upsertRoute(entities: List<RouteTrafficDailyEntity>) {
        for (e in entities) {
            upsertOneRoute(e.dateMillis, e.appKey, e.routeKey, e.slotIndex, e.routeLabel, e.totalUpload, e.totalDownload, e.lastActiveAt)
        }
    }

    /** 复合键，用于预合并应用差异。 */
    private data class AppKey(val dateMillis: Long, val appKey: String, val slotIndex: Int)

    /** 用于预合并路由增量的组合键。*/
    private data class RouteKey(val dateMillis: Long, val appKey: String, val routeKey: String, val slotIndex: Int)

    // 结束区域

    // 区域读数（汇总）

    /**
     * 在`[cutoffMillis, +inf)`区间内进行反应式的每应用聚合，结果求和并排序。
     * 未归因分类（`app_key = 'system:unattributed'`，[com.github.lmfirefly.flycat.data.model.TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY]）无论其总量如何，始终排在最后；其他所有应用按总字节数降序排列。
     * `app_name`/`package_name`通过SQLite的单个`max()`裸列规则从最新行中获取（`MAX(last_active_at)`选择获胜行的裸列）。
     */
    @Query(
        """
        SELECT app_key AS appKey,
               package_name AS packageName,
               app_name AS appName,
               SUM(total_upload) AS totalUpload,
               SUM(total_download) AS totalDownload,
               MAX(last_active_at) AS lastActiveAt
        FROM app_traffic_daily
        WHERE date_millis >= :cutoffMillis
        GROUP BY app_key
        ORDER BY (app_key = 'system:unattributed') ASC,
                 (SUM(total_upload) + SUM(total_download)) DESC
        """
    )
    abstract fun getAppUsagesFlow(cutoffMillis: Long): Flow<List<AppTrafficUsage>>

    @Query(
        """
        SELECT slot_index AS slotIndex,
               SUM(total_upload) AS totalUpload,
               SUM(total_download) AS totalDownload
        FROM app_traffic_daily
        WHERE date_millis >= :cutoffMillis
        GROUP BY slot_index
        ORDER BY slot_index ASC
        """
    )
    abstract fun getTimeSlotTrafficFlow(cutoffMillis: Long): Flow<List<TimeSlotTraffic>>

    @Query(
        """
        SELECT date_millis AS dateMillis,
               SUM(total_upload) AS totalUpload,
               SUM(total_download) AS totalDownload
        FROM app_traffic_daily
        WHERE date_millis >= :cutoffMillis
        GROUP BY date_millis
        ORDER BY date_millis ASC
        """
    )
    abstract fun getDailyTrafficFlow(cutoffMillis: Long): Flow<List<DailyTraffic>>

    /** [getAppUsagesFlow] 的单次执行版本，用于保持门面一致性。 */
    @Query(
        """
        SELECT app_key AS appKey,
               package_name AS packageName,
               app_name AS appName,
               SUM(total_upload) AS totalUpload,
               SUM(total_download) AS totalDownload,
               MAX(last_active_at) AS lastActiveAt
        FROM app_traffic_daily
        WHERE date_millis >= :cutoffMillis
        GROUP BY app_key
        ORDER BY (app_key = 'system:unattributed') ASC,
                 (SUM(total_upload) + SUM(total_download)) DESC
        """
    )
    abstract suspend fun getAppUsagesSorted(cutoffMillis: Long): List<AppTrafficUsage>

    /**
     * 对单个应用在 `[cutoffMillis, +inf)` 区间内的按路由聚合，按总字节数降序、最近访问时间降序排列。
     * `route_label` 取自最近一行数据（遵循 SQLite 单 `max()` 裸列规则）。
     */
    @Query(
        """
        SELECT app_key AS appKey,
               route_key AS routeKey,
               route_label AS routeLabel,
               SUM(total_upload) AS totalUpload,
               SUM(total_download) AS totalDownload,
               MAX(last_active_at) AS lastActiveAt
        FROM route_traffic_daily
        WHERE app_key = :appKey AND date_millis >= :cutoffMillis
        GROUP BY route_key
        ORDER BY (SUM(total_upload) + SUM(total_download)) DESC,
                 MAX(last_active_at) DESC
        """
    )
    abstract suspend fun getAppRouteUsages(
        appKey: String,
        cutoffMillis: Long,
    ): List<AppRouteTrafficUsage>

    // 结束区域

    // 区域保留与清除

    @Transaction
    open suspend fun deleteOlderThan(cutoffMillis: Long) {
        deleteAppOlderThan(cutoffMillis)
        deleteRouteOlderThan(cutoffMillis)
    }

    @Query("DELETE FROM app_traffic_daily WHERE date_millis < :cutoffMillis")
    protected abstract suspend fun deleteAppOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM route_traffic_daily WHERE date_millis < :cutoffMillis")
    protected abstract suspend fun deleteRouteOlderThan(cutoffMillis: Long): Int

    @Transaction
    open suspend fun clearAll() {
        clearAllApp()
        clearAllRoute()
    }

    @Query("DELETE FROM app_traffic_daily")
    protected abstract suspend fun clearAllApp()

    @Query("DELETE FROM route_traffic_daily")
    protected abstract suspend fun clearAllRoute()

    // 结束区域
}
