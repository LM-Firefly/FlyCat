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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.contract.TrafficStatisticsRepository
import com.github.yumelira.yumebox.core.model.AppTrafficUsage
import com.github.yumelira.yumebox.core.model.DailyTrafficSummary
import com.github.yumelira.yumebox.core.model.DailyTraffic
import com.github.yumelira.yumebox.core.model.StatisticsTimeRange
import com.github.yumelira.yumebox.core.model.TimeSlotTraffic
import com.github.yumelira.yumebox.presentation.component.TimeSlotTrafficItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppSortMode { NAME, UPLOAD, DOWNLOAD, TOTAL }

class TrafficStatisticsViewModel(private val trafficStatisticsStore: TrafficStatisticsRepository) :
    ViewModel() {
    private val selectedTimeRange = MutableStateFlow(StatisticsTimeRange.TODAY)
    private val appSortMode = MutableStateFlow(AppSortMode.NAME)
    private val appSortAscending = MutableStateFlow(true)

    fun setAppSortMode(mode: AppSortMode) {
        if (appSortMode.value == mode) {
            appSortAscending.value = !appSortAscending.value
        } else {
            appSortMode.value = mode
            appSortAscending.value = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sortedAppsFlow = selectedTimeRange.flatMapLatest { range ->
        combine(
            trafficStatisticsStore.getAppUsagesFlow(range),
            appSortMode,
            appSortAscending,
        ) { apps, mode, ascending ->
            sortApps(apps, mode, ascending)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val barChartFlow = selectedTimeRange.flatMapLatest { range ->
        when (range) {
            StatisticsTimeRange.TODAY -> {
                trafficStatisticsStore.getTimeSlotTrafficFlow(range).map { slots ->
                    val allSlots = (0..11).map { s ->
                        slots.firstOrNull { it.slotIndex == s }
                            ?: com.github.yumelira.yumebox.core.model.TimeSlotTraffic(s, 0L, 0L)
                    }
                    val items = allSlots.map {
                        TimeSlotTrafficItem(slotIndex = it.slotIndex, upload = it.totalUpload, download = it.totalDownload)
                    }
                    val labels = (0..11).map { "%d-%d".format(it * 2, it * 2 + 2) }
                    items to labels
                }
            }
            StatisticsTimeRange.WEEK -> {
                trafficStatisticsStore.getDailyTrafficFlow(range).map { days ->
                    val dayMap = days.associateBy { it.dateMillis }
                    val calendar = checkNotNull(dayStartCalendar.get())
                    val todayMillis = calendar.timeInMillis
                    val allDays = (13 downTo 0).map { offset ->
                        val millis = todayMillis - offset * DAY_MS
                        dayMap[millis] ?: DailyTraffic(millis, 0L, 0L)
                    }
                    val items = allDays.map { d ->
                        TimeSlotTrafficItem(slotIndex = 0, upload = d.totalUpload, download = d.totalDownload)
                    }
                    val labels = allDays.map { d ->
                        checkNotNull(dateFormat.get()).format(Date(d.dateMillis))
                    }
                    items to labels
                }
            }
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrafficStatisticsUiState> =
        combine(
            selectedTimeRange,
            sortedAppsFlow,
            barChartFlow,
        ) { range, sortedApps, (barItems, barLabels) ->
            buildUiState(range, sortedApps, barItems, barLabels)
        }
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

    private fun sortApps(
        apps: List<AppTrafficUsage>,
        mode: AppSortMode,
        ascending: Boolean,
    ): List<AppTrafficUsage> {
        val sorted = when (mode) {
            AppSortMode.NAME -> apps.sortedBy { it.appName.lowercase() }
            AppSortMode.UPLOAD -> apps.sortedByDescending { it.totalUpload }
            AppSortMode.DOWNLOAD -> apps.sortedByDescending { it.totalDownload }
            AppSortMode.TOTAL -> apps.sortedByDescending { it.totalBytes }
        }
        return if (ascending) sorted else sorted.reversed()
    }

    private fun buildUiState(
        range: StatisticsTimeRange,
        sortedApps: List<AppTrafficUsage>,
        barItems: List<TimeSlotTrafficItem>,
        barLabels: List<String>,
    ): TrafficStatisticsUiState {
        val totalUpload = sortedApps.sumOf(AppTrafficUsage::totalUpload)
        val totalDownload = sortedApps.sumOf(AppTrafficUsage::totalDownload)

        return TrafficStatisticsUiState(
            selectedTimeRange = range,
            summary =
                DailyTrafficSummary(
                    dateMillis = range.days.toLong(),
                    totalUpload = totalUpload,
                    totalDownload = totalDownload,
                ),
            topApps = sortedApps,
            barChartItems = barItems,
            barChartLabels = barLabels,
            appSortMode = appSortMode.value,
            appSortAscending = appSortAscending.value,
        )
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val dateFormat = ThreadLocal.withInitial {
            SimpleDateFormat("M/d", Locale.getDefault())
        }
        private val dayStartCalendar = ThreadLocal.withInitial {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }
}

data class TrafficStatisticsUiState(
    val selectedTimeRange: StatisticsTimeRange = StatisticsTimeRange.TODAY,
    val summary: DailyTrafficSummary = DailyTrafficSummary.empty,
    val topApps: List<AppTrafficUsage> = emptyList(),
    val barChartItems: List<TimeSlotTrafficItem> = emptyList(),
    val barChartLabels: List<String> = emptyList(),
    val appSortMode: AppSortMode = AppSortMode.NAME,
    val appSortAscending: Boolean = true,
)
