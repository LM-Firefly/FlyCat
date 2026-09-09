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

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "route_traffic_daily",
    primaryKeys = ["date_millis", "app_key", "route_key", "slot_index"],
)
data class RouteTrafficDailyEntity(
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "route_key") val routeKey: String,
    @ColumnInfo(name = "slot_index") val slotIndex: Int = 0,
    @ColumnInfo(name = "route_label") val routeLabel: String,
    @ColumnInfo(name = "total_upload") val totalUpload: Long = 0L,
    @ColumnInfo(name = "total_download") val totalDownload: Long = 0L,
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long = 0L,
)
