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

import android.content.Context
import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL

/**
 * Builds the traffic-statistics Room database and returns its DAO.
 *
 * Keeping construction here confines the `TrafficDatabase` / `RoomDatabase` / `Room` types to
 * `:data`; callers (e.g. `:app` DI wiring) only ever touch [TrafficStatisticsDao], which exposes no
 * Room supertypes in its signatures. This lets `:data` keep Room as an `implementation` dependency
 * without leaking it onto the `:app` compile classpath.
 */
fun createTrafficStatisticsDao(context: Context): TrafficStatisticsDao =
    Room.databaseBuilder(
            context.applicationContext,
            TrafficDatabase::class.java,
            TrafficDatabase.DATABASE_NAME,
        )
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
        .trafficStatisticsDao()

/**
 * v1→v2: Add `slot_index` column to both tables and promote it into the primary key.
 *
 * Primary key changes require table recreation (SQLite cannot ALTER PK).
 * Existing rows are migrated with `slot_index = 0` (best-effort; per-slot breakdown is lost for historical data).
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // --- app_traffic_daily ---
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_traffic_daily_new` (
                `date_millis` INTEGER NOT NULL,
                `app_key` TEXT NOT NULL,
                `slot_index` INTEGER NOT NULL DEFAULT 0,
                `package_name` TEXT,
                `app_name` TEXT NOT NULL,
                `total_upload` INTEGER NOT NULL DEFAULT 0,
                `total_download` INTEGER NOT NULL DEFAULT 0,
                `last_active_at` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`date_millis`, `app_key`, `slot_index`)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `app_traffic_daily_new`
                (`date_millis`, `app_key`, `slot_index`, `package_name`, `app_name`,
                 `total_upload`, `total_download`, `last_active_at`)
            SELECT `date_millis`, `app_key`, 0, `package_name`, `app_name`,
                   `total_upload`, `total_download`, `last_active_at`
            FROM `app_traffic_daily`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `app_traffic_daily`")
        connection.execSQL("ALTER TABLE `app_traffic_daily_new` RENAME TO `app_traffic_daily`")
        // --- route_traffic_daily ---
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `route_traffic_daily_new` (
                `date_millis` INTEGER NOT NULL,
                `app_key` TEXT NOT NULL,
                `route_key` TEXT NOT NULL,
                `slot_index` INTEGER NOT NULL DEFAULT 0,
                `route_label` TEXT NOT NULL,
                `total_upload` INTEGER NOT NULL DEFAULT 0,
                `total_download` INTEGER NOT NULL DEFAULT 0,
                `last_active_at` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`date_millis`, `app_key`, `route_key`, `slot_index`)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `route_traffic_daily_new`
                (`date_millis`, `app_key`, `route_key`, `slot_index`, `route_label`,
                 `total_upload`, `total_download`, `last_active_at`)
            SELECT `date_millis`, `app_key`, `route_key`, 0, `route_label`,
                   `total_upload`, `total_download`, `last_active_at`
            FROM `route_traffic_daily`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `route_traffic_daily`")
        connection.execSQL("ALTER TABLE `route_traffic_daily_new` RENAME TO `route_traffic_daily`")
    }
}
