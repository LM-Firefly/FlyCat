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

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppTrafficDailyEntity::class,
        RouteTrafficDailyEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TrafficDatabase : RoomDatabase() {
    abstract fun trafficStatisticsDao(): TrafficStatisticsDao

    companion object {
        const val DATABASE_NAME = "traffic_statistics.db"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove redundant indices — primary keys already cover these columns.
                db.execSQL("DROP INDEX IF EXISTS `index_app_traffic_daily_date_millis`")
                db.execSQL("DROP INDEX IF EXISTS `index_route_traffic_daily_date_millis_app_key`")
            }
        }
    }
}
