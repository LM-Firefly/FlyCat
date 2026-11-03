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
 * 构建流量统计 Room 数据库并返回其 DAO。
 *
 * 将构造放在此处，可将 `TrafficDatabase` / `RoomDatabase` / `Room` 类型限制在 `:data` 中；
 * 调用方（如 `:app` 的依赖注入配置）始终只接触 [TrafficStatisticsDao]，其签名中不暴露任何 Room 超类型。
 * 这样 `:data` 可以将 Room 保持为 `implementation` 依赖，而不会将其泄漏到 `:app` 的编译类路径上。
 */
fun createTrafficStatisticsDao(context: Context): TrafficStatisticsDao = Room.databaseBuilder(
    context.applicationContext,
    TrafficDatabase::class.java,
    TrafficDatabase.DATABASE_NAME,
).setDriver(AndroidSQLiteDriver()).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().trafficStatisticsDao()

/**
 * v1→v2: 向两个表中添加 `slot_index` 列，并将其提升为主键的一部分。
 *
 * 主键变更需要重建表（SQLite 无法 ALTER 修改主键）。
 * 现有行以 `slot_index = 0` 进行迁移（尽力而为；历史数据的按槽位明细将丢失）。
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
