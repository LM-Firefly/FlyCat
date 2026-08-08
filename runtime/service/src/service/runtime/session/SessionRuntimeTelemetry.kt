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

package com.github.yumelira.yumebox.runtime.service.runtime.session

import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.domain.ConnectionHistoryManager
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.TrafficPushHub
import com.github.yumelira.yumebox.core.util.throttleByScene
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunEncode
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
import java.util.concurrent.atomic.AtomicLong

internal class SessionRuntimeTelemetry(
    private val host: RuntimeHost,
    private val scope: CoroutineScope,
    private val onLogReadyChanged: (Boolean) -> Unit,
) {
    private var logJob: Job? = null
    private var telemetryJob: Job? = null
    private val logSeq = AtomicLong(0L)
    private val recentLogs = ArrayDeque<Pair<Long, String>>()
    @Volatile
    private var localLogObserver: ((LogMessage) -> Unit)? = null

    // 流量数据统一从 TrafficPushHub 读取，不再独立轮询
    val trafficNow: StateFlow<Traffic> = TrafficPushHub.trafficNow
    val trafficTotal: StateFlow<Traffic> = TrafficPushHub.trafficTotal

    fun setLogObserver(observer: ((LogMessage) -> Unit)?) {
        localLogObserver = observer
    }

    fun queryRecentLogsJson(sinceSeq: Long): RuntimeLogChunk {
        synchronized(recentLogs) {
            val items = recentLogs.filter { it.first > sinceSeq }.map { it.second }
            return RuntimeLogChunk(nextSeq = logSeq.get(), items = items)
        }
    }

    fun isLogStreaming(): Boolean = logJob?.isActive == true

    fun startLogStream(subscribe: () -> ReceiveChannel<LogMessage>, unsubscribe: () -> Unit) {
        stopLogStream()
        host.onLogReady(false)
        logJob =
            scope.launch(Dispatchers.IO) {
                val receiver = subscribe()
                host.onLogReady(true)
                onLogReadyChanged(true)
                try {
                    while (isActive) {
                        val item = receiver.receive()
                        localLogObserver?.invoke(item)
                        host.onLogItem(item)
                        val encoded = rootTunEncode(item)
                        val seq = logSeq.incrementAndGet()
                        synchronized(recentLogs) {
                            recentLogs.addLast(seq to encoded)
                            while (recentLogs.size > MAX_BUFFERED_LOGS) {
                                recentLogs.removeFirst()
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

    fun stopLogStream() {
        logJob?.cancel()
        logJob = null
        synchronized(recentLogs) { recentLogs.clear() }
        host.onLogReady(false)
    }

    fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    private companion object {
        private const val MAX_BUFFERED_LOGS = 256
    }
}
