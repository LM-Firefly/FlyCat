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

package com.github.yumelira.yumebox.feature.meta.presentation.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.TrafficStatisticsReader
import com.github.yumelira.yumebox.core.model.AppTrafficUsage
import com.github.yumelira.yumebox.core.model.DailyTrafficSummary
import com.github.yumelira.yumebox.core.model.StatisticsTimeRange
import com.github.yumelira.yumebox.core.model.TrafficStatisticsBuckets
import com.github.yumelira.yumebox.presentation.component.TrafficDonutSlice
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrafficStatisticsViewModel(private val trafficStatisticsStore: TrafficStatisticsReader) :
    ViewModel() {
    private val selectedTimeRange = MutableStateFlow(StatisticsTimeRange.TODAY)

    val uiState: StateFlow<TrafficStatisticsUiState> =
        combine(selectedTimeRange, trafficStatisticsStore.dailyAppSummaries) { range, _ ->
            withContext(Dispatchers.Default) {
                val topApps = trafficStatisticsStore.getAppUsagesSorted(range)
                val totals = trafficStatisticsStore.getTotalSummary(range)
                TrafficStatisticsUiState(
                    selectedTimeRange = range,
                    summary =
                        DailyTrafficSummary(
                            dateMillis = range.days.toLong(),
                            totalUpload = totals.totalUpload,
                            totalDownload = totals.totalDownload,
                        ),
                    topApps = topApps,
                    donutSlices = buildDonutSlices(topApps),
                )
            }
        }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TrafficStatisticsUiState(),
            )

    fun setTimeRange(range: StatisticsTimeRange) {
        selectedTimeRange.value = range
    }

    fun clearAllStatistics() {
        viewModelScope.launch { trafficStatisticsStore.clearAll() }
    }

    private fun buildDonutSlices(apps: List<AppTrafficUsage>): List<TrafficDonutSlice> {
        if (apps.isEmpty()) return emptyList()

        val unknown = apps.firstOrNull { it.appKey == TrafficStatisticsBuckets.UNKNOWN_APP_KEY }
        val unattributed = apps.firstOrNull {
            it.appKey == TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY
        }
        val regularApps = apps.filterNot {
            it.appKey == TrafficStatisticsBuckets.UNKNOWN_APP_KEY ||
                it.appKey == TrafficStatisticsBuckets.UNATTRIBUTED_APP_KEY
        }
        val primaryApps = regularApps.take(MAX_DONUT_PRIMARY_APPS)
        val overflowBytes =
            regularApps.drop(MAX_DONUT_PRIMARY_APPS).sumOf(AppTrafficUsage::totalBytes)

        return buildList {
            primaryApps.forEach { usage ->
                add(
                    TrafficDonutSlice(
                        key = usage.appKey,
                        label = usage.appName,
                        value = usage.totalBytes,
                        color = colorForAppKey(usage.appKey),
                    )
                )
            }

            if (overflowBytes > 0L) {
                add(
                    TrafficDonutSlice(
                        key = OTHER_SLICE_KEY,
                        label = MLang.TrafficStatistics.Donut.Other,
                        value = overflowBytes,
                        color = COLOR_TRAFFIC_OTHER,
                    )
                )
            }

            unattributed
                ?.takeIf { it.totalBytes > 0L }
                ?.let { usage ->
                    add(
                        TrafficDonutSlice(
                            key = usage.appKey,
                            label = usage.appName,
                            value = usage.totalBytes,
                            color = COLOR_TRAFFIC_UNATTRIBUTED,
                        )
                    )
                }

            unknown
                ?.takeIf { it.totalBytes > 0L }
                ?.let { usage ->
                    add(
                        TrafficDonutSlice(
                            key = usage.appKey,
                            label = usage.appName,
                            value = usage.totalBytes,
                            color = COLOR_TRAFFIC_UNKNOWN,
                        )
                    )
                }
        }
    }

    private fun colorForAppKey(appKey: String): Color {
        val hue = ((appKey.hashCode().toLong() and 0xFFFFFFFFL) % 360L).toFloat()
        return Color.hsv(hue = hue, saturation = 0.62f, value = 0.88f)
    }

    companion object {
        const val OTHER_SLICE_KEY = "other"
        private const val MAX_DONUT_PRIMARY_APPS = 5
        private val COLOR_TRAFFIC_OTHER = Color(0xFF94A3B8)
        private val COLOR_TRAFFIC_UNATTRIBUTED = Color(0xFFD97706)
        private val COLOR_TRAFFIC_UNKNOWN = Color(0xFF64748B)
    }
}

data class TrafficStatisticsUiState(
    val selectedTimeRange: StatisticsTimeRange = StatisticsTimeRange.TODAY,
    val summary: DailyTrafficSummary = DailyTrafficSummary.EMPTY,
    val topApps: List<AppTrafficUsage> = emptyList(),
    val donutSlices: List<TrafficDonutSlice> = emptyList(),
)
