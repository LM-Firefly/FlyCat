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

package com.github.lmfirefly.flycat.runtime.service.session.telemetry

import android.os.SystemClock
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.model.LogMessage
import com.github.lmfirefly.flycat.core.model.traffic.Traffic
import com.github.lmfirefly.flycat.core.util.ConnectionHistoryManager
import com.github.lmfirefly.flycat.core.util.PollingTimers
import com.github.lmfirefly.flycat.core.util.PollingTimerSpecs
import com.github.lmfirefly.flycat.core.util.TrafficPushHub
import com.github.lmfirefly.flycat.core.util.throttleByScene
import com.github.lmfirefly.flycat.runtime.api.root.rootTunEncode
import com.github.lmfirefly.flycat.runtime.service.session.RuntimeHost
import com.github.lmfirefly.flycat.runtime.service.session.spec.RuntimeLogChunk
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class SessionRuntimeTelemetry(
    private val host: RuntimeHost,
    private val scope: CoroutineScope,
    private val onLogReadyChanged: (Boolean) -> Unit,
) {
    private var logJob: Job? = null
    private var logSubscribe: (() -> ReceiveChannel<LogMessage>)? = null
    private var logUnsubscribe: (() -> Unit)? = null
    private val logSeq = AtomicLong(0L)
    private val recentLogs = ArrayDeque<Pair<Long, String>>()
    @Volatile
    private var hasActiveConsumer = false
    @Volatile
    private var lastConsumerActiveTimestamp = 0L

    // 流量数据统一从 TrafficPushHub 读取，不再独立轮询
    val trafficNow: StateFlow<Traffic> = TrafficPushHub.trafficNow
    val trafficTotal: StateFlow<Traffic> = TrafficPushHub.trafficTotal

    fun queryRecentLogsJson(sinceSeq: Long): RuntimeLogChunk {
        hasActiveConsumer = true
        lastConsumerActiveTimestamp = SystemClock.elapsedRealtime()
        synchronized(recentLogs) {
            val items = recentLogs.filter { it.first > sinceSeq }.map { it.second }
            return RuntimeLogChunk(nextSeq = logSeq.get(), items = items)
        }
    }

    fun isLogStreaming(): Boolean = logJob?.isActive == true

    /**
     * 通过存储核心的订阅/取消订阅钩子来准备日志流。
     * 实际的订阅是按需通过 [setLogConsumerActive] 建立的（仅在应用的 mihomo 日志预览打开时）
     * 这样根进程就不会在其整个生命周期内持有逐行的 JNI 订阅（Go 序列化 → JNI → Kotlin 反序列化）。
     */
    fun startLogStream(subscribe: () -> ReceiveChannel<LogMessage>, unsubscribe: () -> Unit) {
        logSubscribe = subscribe
        logUnsubscribe = unsubscribe
    }

    /** 驱动核心日志订阅：激活时附加，非激活时立即解除。 */
    fun setLogConsumerActive(active: Boolean) {
        if (active) {
            if (logJob?.isActive != true) attachStream()
        } else {
            detachStream()
        }
    }

    private fun attachStream() {
        val subscribe = logSubscribe ?: return
        val unsubscribe = logUnsubscribe ?: return
        host.onLogReady(false)
        logJob =
            scope.launch(Dispatchers.IO) {
                val receiver = subscribe()
                host.onLogReady(true)
                onLogReadyChanged(true)
                try {
                    while (isActive) {
                        val item = receiver.receive()
                        host.onLogItem(item)
                        if (hasActiveConsumer) {
                            if (SystemClock.elapsedRealtime() - lastConsumerActiveTimestamp > 30_000L) {
                                hasActiveConsumer = false
                            } else {
                                val encoded = rootTunEncode(item)
                                val seq = logSeq.incrementAndGet()
                                synchronized(recentLogs) {
                                    recentLogs.addLast(seq to encoded)
                                    while (recentLogs.size > MAX_BUFFERED_LOGS) {
                                        recentLogs.removeFirst()
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    receiver.cancel()
                    runCatching { unsubscribe() }
                    host.onLogReady(false)
                    onLogReadyChanged(false)
                }
            }
    }

    private fun detachStream() {
        logJob?.cancel()
        logJob = null
        host.onLogReady(false)
    }

    fun stopLogStream() {
        detachStream()
        logSubscribe = null
        logUnsubscribe = null
        synchronized(recentLogs) { recentLogs.clear() }
    }

    private companion object {
        private const val MAX_BUFFERED_LOGS = 256
    }
}
