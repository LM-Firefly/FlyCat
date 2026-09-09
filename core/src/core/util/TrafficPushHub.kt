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

package com.github.lmfirefly.flycat.core.util

import android.os.SystemClock
import com.github.lmfirefly.flycat.core.model.traffic.Traffic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 流量数据统一数据源（唯一 truth）。
 * 所有模式（LOCAL_TUN / ROOT_TUN / REMOTE）的流量数据统一写入此中心，
 * 所有消费者（TrafficStatsPoller、SessionRuntimeTelemetry、通知栏）从此处读取。
 */
object TrafficPushHub {
    private val _trafficNow = MutableStateFlow(0L)
    val trafficNow: StateFlow<Traffic> = _trafficNow.asStateFlow()
    private val _trafficTotal = MutableStateFlow(0L)
    val trafficTotal: StateFlow<Traffic> = _trafficTotal.asStateFlow()

    @Volatile
    var lastUpdateTimestampMs: Long = 0L
        private set

    fun update(trafficNowPacked: Long, trafficTotalPacked: Long) {
        _trafficNow.value = trafficNowPacked
        _trafficTotal.value = trafficTotalPacked
        lastUpdateTimestampMs = SystemClock.elapsedRealtime()
    }

    /** 在 [thresholdMs] 内是否收到过推送事件。 */
    fun isActive(thresholdMs: Long): Boolean =
        SystemClock.elapsedRealtime() - lastUpdateTimestampMs < thresholdMs

    fun reset() {
        _trafficNow.value = 0L
        _trafficTotal.value = 0L
        lastUpdateTimestampMs = 0L
    }
}
