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

package com.github.yumelira.yumebox.data.store.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.yumelira.yumebox.data.model.TrafficStatisticsBuckets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrafficStatisticsDaoTest {

    private lateinit var database: TrafficDatabase
    private lateinit var dao: TrafficStatisticsDao

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TrafficDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        dao = database.trafficStatisticsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accumulate_sumsTotals_keepsAppNameWhenBlank_coalescesPackage() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(
                        dateMillis = DAY1,
                        appKey = "app.a",
                        packageName = "com.a",
                        appName = "AppA",
                        upload = 100L,
                        download = 50L,
                        lastActiveAt = T1,
                    )
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )
        // Second record: blank appName and null packageName must NOT overwrite existing values.
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(
                        dateMillis = DAY1,
                        appKey = "app.a",
                        packageName = null,
                        appName = "   ",
                        upload = 10L,
                        download = 5L,
                        lastActiveAt = T2,
                    )
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )

        val usages = dao.getAppUsagesSorted(NO_RETENTION)
        assertEquals(1, usages.size)
        val usage = usages.single()
        assertEquals("app.a", usage.appKey)
        assertEquals(110L, usage.totalUpload)
        assertEquals(55L, usage.totalDownload)
        assertEquals("AppA", usage.appName)
        assertEquals("com.a", usage.packageName)
        assertEquals(T2, usage.lastActiveAt)
    }

    @Test
    fun accumulate_coalescesPackageForwardWhenPreviouslyNull() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(
                        dateMillis = DAY1,
                        appKey = "app.b",
                        packageName = null,
                        appName = "AppB",
                        upload = 10L,
                        download = 0L,
                        lastActiveAt = T1,
                    )
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(
                        dateMillis = DAY1,
                        appKey = "app.b",
                        packageName = "com.b",
                        appName = "AppB",
                        upload = 5L,
                        download = 0L,
                        lastActiveAt = T2,
                    )
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )

        val usage = dao.getAppUsagesSorted(NO_RETENTION).single()
        assertEquals("com.b", usage.packageName)
        assertEquals(15L, usage.totalUpload)
    }

    @Test
    fun rangeAggregation_sumsAcrossDayBuckets_andHonoursCutoff() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY1, "app.a", "com.a", "AppA", upload = 100L, download = 50L, lastActiveAt = T1)
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY2, "app.a", "com.a", "AppA", upload = 200L, download = 100L, lastActiveAt = T2)
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )

        val bothDays = dao.getAppUsagesSorted(DAY1).single()
        assertEquals(300L, bothDays.totalUpload)
        assertEquals(150L, bothDays.totalDownload)

        val onlyDay2 = dao.getAppUsagesSorted(DAY2).single()
        assertEquals(200L, onlyDay2.totalUpload)
        assertEquals(100L, onlyDay2.totalDownload)
    }

    @Test
    fun unattributedBucket_sortsLast_evenWhenLargest() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY1, "app.a", "com.a", "AppA", upload = 10L, download = 0L, lastActiveAt = T1),
                    appDelta(
                        dateMillis = DAY1,
                        appKey = TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY,
                        packageName = null,
                        appName = TrafficStatisticsBuckets.UNATTRIBUTED_APP_NAME,
                        upload = 1_000L,
                        download = 0L,
                        lastActiveAt = T2,
                    ),
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )

        val usages = dao.getAppUsagesSorted(NO_RETENTION)
        assertEquals(2, usages.size)
        assertEquals("app.a", usages[0].appKey)
        assertEquals(TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY, usages[1].appKey)
    }

    @Test
    fun deleteOlderThan_removesOnlyRowsStrictlyOlderThanCutoff() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY1, "app.old", "com.old", "Old", upload = 10L, download = 0L, lastActiveAt = T1)
                ),
            routeDeltas =
                listOf(
                    routeDelta(DAY1, "app.old", "route.old", "RouteOld", upload = 10L, download = 0L, lastActiveAt = T1)
                ),
            retentionCutoffMillis = NO_RETENTION,
        )
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY2, "app.new", "com.new", "New", upload = 20L, download = 0L, lastActiveAt = T2)
                ),
            routeDeltas =
                listOf(
                    routeDelta(DAY2, "app.new", "route.new", "RouteNew", upload = 20L, download = 0L, lastActiveAt = T2)
                ),
            retentionCutoffMillis = NO_RETENTION,
        )

        // Cutoff == DAY2: DAY1 rows are strictly older (removed); DAY2 rows are kept.
        dao.deleteOlderThan(DAY2)

        val usages = dao.getAppUsagesSorted(NO_RETENTION)
        assertEquals(1, usages.size)
        assertEquals("app.new", usages.single().appKey)
        assertTrue(dao.getAppRouteUsages("app.old", NO_RETENTION).isEmpty())
        assertEquals(1, dao.getAppRouteUsages("app.new", NO_RETENTION).size)
    }

    @Test
    fun clearAll_emptiesBothTables() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY1, "app.a", "com.a", "AppA", upload = 10L, download = 0L, lastActiveAt = T1)
                ),
            routeDeltas =
                listOf(
                    routeDelta(DAY1, "app.a", "route.x", "RouteX", upload = 10L, download = 0L, lastActiveAt = T1)
                ),
            retentionCutoffMillis = NO_RETENTION,
        )

        dao.clearAll()

        assertTrue(dao.getAppUsagesSorted(NO_RETENTION).isEmpty())
        assertTrue(dao.getAppRouteUsages("app.a", NO_RETENTION).isEmpty())
    }

    @Test
    fun routeAccumulate_sumsTotals_keepsLabelWhenBlank_andSortsByTotal() = runBlocking {
        dao.recordBatch(
            appDeltas = emptyList(),
            routeDeltas =
                listOf(
                    routeDelta(DAY1, "app.a", "route.x", "RouteX", upload = 100L, download = 0L, lastActiveAt = T1)
                ),
            retentionCutoffMillis = NO_RETENTION,
        )
        // Blank label must not overwrite the stored one; totals accumulate.
        dao.recordBatch(
            appDeltas = emptyList(),
            routeDeltas =
                listOf(
                    routeDelta(DAY1, "app.a", "route.x", "", upload = 50L, download = 0L, lastActiveAt = T2),
                    routeDelta(DAY1, "app.a", "route.y", "RouteY", upload = 20L, download = 0L, lastActiveAt = T2),
                ),
            retentionCutoffMillis = NO_RETENTION,
        )

        val routes = dao.getAppRouteUsages("app.a", NO_RETENTION)
        assertEquals(2, routes.size)
        // route.x total = 150 > route.y total = 20, so route.x sorts first.
        assertEquals("route.x", routes[0].routeKey)
        assertEquals("RouteX", routes[0].routeLabel)
        assertEquals(150L, routes[0].totalUpload)
        assertEquals("route.y", routes[1].routeKey)
        assertEquals(20L, routes[1].totalUpload)
    }

    @Test
    fun recordBatch_skipsRecordsWithNonPositiveDeltas() = runBlocking {
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY1, "app.zero", "com.zero", "Zero", upload = 0L, download = 0L, lastActiveAt = T1)
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )
        assertTrue(dao.getAppUsagesSorted(NO_RETENTION).isEmpty())
    }

    @Test
    fun recordBatch_appliesRetentionInSameTransaction() = runBlocking {
        // Seed an old-day row first (no retention), then a write with retention cutoff == DAY2.
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY1, "app.old", "com.old", "Old", upload = 10L, download = 0L, lastActiveAt = T1)
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = NO_RETENTION,
        )
        dao.recordBatch(
            appDeltas =
                listOf(
                    appDelta(DAY2, "app.new", "com.new", "New", upload = 20L, download = 0L, lastActiveAt = T2)
                ),
            routeDeltas = emptyList(),
            retentionCutoffMillis = DAY2,
        )

        val usages = dao.getAppUsagesSorted(NO_RETENTION)
        assertEquals(1, usages.size)
        assertEquals("app.new", usages.single().appKey)
        assertNull(usages.firstOrNull { it.appKey == "app.old" })
    }

    private fun appDelta(
        dateMillis: Long,
        appKey: String,
        packageName: String?,
        appName: String,
        upload: Long,
        download: Long,
        lastActiveAt: Long,
    ) =
        AppTrafficDelta(
            dateMillis = dateMillis,
            appKey = appKey,
            packageName = packageName,
            appName = appName,
            uploadDelta = upload,
            downloadDelta = download,
            lastActiveAt = lastActiveAt,
        )

    private fun routeDelta(
        dateMillis: Long,
        appKey: String,
        routeKey: String,
        routeLabel: String,
        upload: Long,
        download: Long,
        lastActiveAt: Long,
    ) =
        RouteTrafficDelta(
            dateMillis = dateMillis,
            appKey = appKey,
            routeKey = routeKey,
            routeLabel = routeLabel,
            uploadDelta = upload,
            downloadDelta = download,
            lastActiveAt = lastActiveAt,
        )

    private companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val DAY1 = 1_000L * DAY_MS
        private const val DAY2 = 1_001L * DAY_MS
        private const val T1 = DAY1 + 3_600_000L
        private const val T2 = DAY2 + 3_600_000L

        // date_millis are always positive, so `date_millis < 0` deletes nothing => retention off.
        private const val NO_RETENTION = 0L
    }
}
