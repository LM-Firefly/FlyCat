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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
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
    val RemoteControllerProbe = PollingTimerSpec("remote_controller_probe", 5_000L, 5_000L)
    val ProxyHealthcheckRefresh = PollingTimerSpec("proxy_healthcheck_refresh", 2_500L, 2_500L)

    object TrafficPoller {
        /** 有订阅者（如连接页面）时的连接快照查询间隔。 */
        const val CONNECTION_ACTIVE_INTERVAL_MS = 1_000L
        /** Interval between payload-refresh checks (foreground ms). */
        const val PAYLOAD_REFRESH_INTERVAL_MS = 20_000L
        const val FAILURE_BACKOFF_STEP_MS = 1_000L
        const val FAILURE_BACKOFF_MAX_MS = 15_000L
        const val BACKGROUND_INTERVAL_MS = 5_000L
        /** 灭屏完全挂起（非降频）——见 [throttleByScene] 的 screenOffIntervalMs=0。 */
        const val SCREEN_OFF_INTERVAL_MS = 0L
    }

    object Telemetry {
        const val BACKGROUND_INTERVAL_MS = 5_000L
        const val SCREEN_OFF_INTERVAL_MS = 0L
    }

    object ProxyGroupSync {
        const val FAST_BACKGROUND_MS = 5_000L
        const val FAST_SCREEN_OFF_MS = 0L
        const val SLOW_BACKGROUND_MS = 30_000L
        const val SLOW_SCREEN_OFF_MS = 0L
    }

    object RootLogPolling {
        const val BACKGROUND_INTERVAL_MS = 10_000L
        const val SCREEN_OFF_INTERVAL_MS = 0L
    }

    object LogFlush {
        const val FOREGROUND_INTERVAL_MS = 2_000L
        const val SCREEN_OFF_INTERVAL_MS = 10_000L
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

@kotlinx.coroutines.ExperimentalCoroutinesApi
fun Flow<Long>.throttleWhenScreenOff(screenOn: StateFlow<Boolean>): Flow<Long> = screenOn.transformLatest { isScreenOn ->
    if (isScreenOn) {
        this@throttleWhenScreenOff.collect { emit(it) }
    } else {
        // 灭屏: 暂停发射，等待亮屏
        emptyFlow<Long>().collect { emit(it) }
    }
}

/**
 * 按前台/灭屏场景节流上游 tick。
 *
 * - 亮屏 + 前台：原速透传上游
 * - 亮屏 + 后台：以 [backgroundIntervalMs] 自建定时器
 * - 灭屏：[screenOffIntervalMs] > 0 时以该间隔降频；= 0 时完全挂起（省电首选）
 *
 * 灭屏挂起时不会持有上游 ticks 订阅，共享 ticker 可在 WhileSubscribed 超时后真正停下。
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun Flow<Long>.throttleByScene(screenOn: StateFlow<Boolean>, appForeground: StateFlow<Boolean>, backgroundIntervalMs: Long, screenOffIntervalMs: Long = 0L): Flow<Long> = combine(screenOn, appForeground) { isOn, isFg -> isOn to isFg }.transformLatest { (isScreenOn, isForeground) ->
    when {
        !isScreenOn -> {
            if (screenOffIntervalMs <= 0L) {
                awaitCancellation()
            } else {
                flow {
                    while (currentCoroutineContext().isActive) {
                        emit(SystemClock.elapsedRealtime())
                        delay(screenOffIntervalMs)
                    }
                }.collect { emit(it) }
            }
        }
        !isForeground -> flow {
            while (currentCoroutineContext().isActive) {
                emit(SystemClock.elapsedRealtime())
                delay(backgroundIntervalMs)
            }
        }.collect { emit(it) }
        else -> this@throttleByScene.collect { emit(it) }
    }
}
