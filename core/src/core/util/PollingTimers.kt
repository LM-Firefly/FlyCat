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

package com.github.yumelira.yumebox.core.util

import android.os.SystemClock
import com.github.yumelira.yumebox.core.model.Traffic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object AppForegroundState {
    private val resumedCount = AtomicInteger(0)
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    fun onActivityResumed() { if (resumedCount.incrementAndGet() == 1) _foreground.value = true }
    fun onActivityPaused() { if (resumedCount.decrementAndGet() == 0) _foreground.value = false }
}

data class PollingTimerSpec(
    val name: String,
    val intervalMillis: Long,
    val initialDelayMillis: Long = intervalMillis,
) {
    init {
        require(name.isNotBlank()) { "Timer name must not be blank" }
        require(intervalMillis > 0L) { "Timer interval must be > 0" }
        require(initialDelayMillis >= 0L) { "Timer initial delay must be >= 0" }
    }
}

object PollingTimerSpecs {
    val MoeElapsedClock = PollingTimerSpec("acg_elapsed_clock", 1_000L, 0L)
    val RuntimeTrafficPolling = PollingTimerSpec("runtime_traffic_polling", 1_000L, 0L)
    val RuntimeProxyGroupSyncFast = PollingTimerSpec("runtime_proxy_group_sync_fast", 1_000L, 0L)
    val RuntimeProxyGroupSyncSlow = PollingTimerSpec("runtime_proxy_group_sync_slow", 4_000L, 0L)
    val RuntimeRootLogPolling = PollingTimerSpec("runtime_root_log_polling", 2_000L, 0L)
    val RootTunStatusNotification = PollingTimerSpec("root_tun_status_notification", 2_000L, 0L)
    val SessionConnectionTracking = PollingTimerSpec("session_connection_tracking", 1_000L, 0L)
    val ProxyHealthcheckRefresh = PollingTimerSpec("proxy_healthcheck_refresh", 2_500L, 2_500L)

    object TrafficPoller {
        /** 有订阅者（如连接页面）时的连接快照查询间隔。 */
        const val CONNECTION_ACTIVE_INTERVAL_MS = 1_000L
        /** Interval between payload-refresh checks (foreground ms). */
        const val PAYLOAD_REFRESH_INTERVAL_MS = 20_000L
        const val FAILURE_BACKOFF_STEP_MS = 1_000L
        const val FAILURE_BACKOFF_MAX_MS = 15_000L
        const val BACKGROUND_INTERVAL_MS = 5_000L
        const val SCREEN_OFF_INTERVAL_MS = 120_000L
    }

    object Telemetry {
        const val BACKGROUND_INTERVAL_MS = 5_000L
        const val SCREEN_OFF_INTERVAL_MS = 120_000L
    }

    object ProxyGroupSync {
        const val FAST_BACKGROUND_MS = 5_000L
        const val FAST_SCREEN_OFF_MS = 60_000L
        const val SLOW_BACKGROUND_MS = 30_000L
        const val SLOW_SCREEN_OFF_MS = 120_000L
    }

    object RootLogPolling {
        const val BACKGROUND_INTERVAL_MS = 10_000L
        const val SCREEN_OFF_INTERVAL_MS = 60_000L
    }

    object LogFlush {
        const val FOREGROUND_INTERVAL_MS = 350L
        const val SCREEN_OFF_INTERVAL_MS = 60_000L
    }

    fun dynamic(
        name: String,
        intervalMillis: Long,
        initialDelayMillis: Long = intervalMillis,
    ): PollingTimerSpec =
        PollingTimerSpec(
            name = "dynamic_$name",
            intervalMillis = intervalMillis,
            initialDelayMillis = initialDelayMillis,
        )
}

object PollingTimers {
    private const val STOP_TIMEOUT_MILLIS = 5_000L

    // One lightweight scheduler lane for all periodic tick emission in this process.
    private val schedulerScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private data class TimerKey(val intervalMillis: Long, val initialDelayMillis: Long)
    private val tickerCache = ConcurrentHashMap<TimerKey, SharedFlow<Long>>()

    fun ticks(spec: PollingTimerSpec): Flow<Long> {
        val key = TimerKey(spec.intervalMillis, spec.initialDelayMillis)
        return tickerCache.computeIfAbsent(key) {
            flow {
                    if (spec.initialDelayMillis > 0L) {
                        delay(spec.initialDelayMillis)
                    }
                    while (currentCoroutineContext().isActive) {
                        emit(SystemClock.elapsedRealtime())
                        delay(spec.intervalMillis)
                    }
                }
                .onCompletion { tickerCache.remove(key) }
                .shareIn(
                    scope = schedulerScope,
                    started =
                        SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
                    replay = 0,
                )
        }
    }

    suspend fun awaitTick(spec: PollingTimerSpec) {
        ticks(spec).first()
    }
}

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
        lastUpdateTimestampMs = System.currentTimeMillis()
    }

    /** 在 [thresholdMs] 内是否收到过推送事件。 */
    fun isActive(thresholdMs: Long): Boolean =
        System.currentTimeMillis() - lastUpdateTimestampMs < thresholdMs

    fun reset() {
        _trafficNow.value = 0L
        _trafficTotal.value = 0L
        lastUpdateTimestampMs = 0L
    }
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
fun Flow<Long>.throttleWhenScreenOff(screenOn: StateFlow<Boolean>, slowIntervalMs: Long = 5_000L): Flow<Long> = screenOn.transformLatest { isScreenOn ->
    if (isScreenOn) { this@throttleWhenScreenOff.collect { emit(it) } } else {
        flow {
            while (currentCoroutineContext().isActive) {
                emit(SystemClock.elapsedRealtime())
                delay(slowIntervalMs)
            }
        }.collect { emit(it) }
    }
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
fun Flow<Long>.throttleByScene(screenOn: StateFlow<Boolean>, appForeground: StateFlow<Boolean>, backgroundIntervalMs: Long, screenOffIntervalMs: Long): Flow<Long> = combine(screenOn, appForeground) { isOn, isFg -> isOn to isFg }.transformLatest { (isScreenOn, isForeground) ->
    when {
        !isScreenOn -> flow {
            while (currentCoroutineContext().isActive) {
                emit(SystemClock.elapsedRealtime())
                delay(screenOffIntervalMs)
            }
        }.collect { emit(it) }
        !isForeground -> flow {
            while (currentCoroutineContext().isActive) {
                emit(SystemClock.elapsedRealtime())
                delay(backgroundIntervalMs)
            }
        }.collect { emit(it) }
        else -> this@throttleByScene.collect { emit(it) }
    }
}
