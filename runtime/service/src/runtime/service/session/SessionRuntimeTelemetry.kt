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

package com.github.yumelira.yumebox.runtime.service.session

import com.github.yumelira.yumebox.core.domain.ConnectionHistoryManager
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.runtime.api.LogObserver
import com.github.yumelira.yumebox.runtime.api.LogSubscription
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

internal class SessionRuntimeTelemetry(
    private val host: RuntimeHost,
    private val scope: CoroutineScope,
    private val onLogReadyChanged: (Boolean) -> Unit,
) {
    private var logSubscription: LogSubscription? = null
    @Volatile private var logStreaming = false
    private var connectionTrackingJob: Job? = null
    private val logSeq = AtomicLong(0L)
    private val recentLogs = ArrayDeque<Pair<Long, String>>()
    @Volatile private var localLogObserver: ((LogMessage) -> Unit)? = null

    fun setLogObserver(observer: ((LogMessage) -> Unit)?) {
        localLogObserver = observer
    }

    fun queryRecentLogsJson(sinceSeq: Long): RuntimeLogChunk {
        synchronized(recentLogs) {
            val items = recentLogs.filter { it.first > sinceSeq }.map { it.second }
            return RuntimeLogChunk(nextSeq = logSeq.get(), items = items)
        }
    }

    fun isLogStreaming(): Boolean = logStreaming

    fun startLogStream(subscribe: (LogObserver) -> LogSubscription) {
        stopLogStream()
        setLogReady(false)
        logSubscription =
            subscribe(
                object : LogObserver {
                    override fun onConnected() {
                        setLogReady(true)
                    }

                    override fun onError(error: Throwable) {
                        setLogReady(false)
                    }

                    override fun newItem(log: LogMessage) {
                        runCatching { localLogObserver?.invoke(log) }
                            .onFailure { Timber.w(it, "Local log observer rejected an item") }
                        runCatching { host.onLogItem(log) }
                            .onFailure { Timber.w(it, "Runtime host rejected a log item") }
                        val encoded = Json.encodeToString(LogMessage.serializer(), log)
                        val seq = logSeq.incrementAndGet()
                        synchronized(recentLogs) {
                            recentLogs.addLast(seq to encoded)
                            while (recentLogs.size > MAX_BUFFERED_LOGS) {
                                recentLogs.removeFirst()
                            }
                        }
                    }
                }
            )
    }

    fun stopLogStream() {
        runCatching { logSubscription?.close() }
            .onFailure { Timber.w(it, "Failed to close runtime log subscription") }
        logSubscription = null
        synchronized(recentLogs) { recentLogs.clear() }
        setLogReady(false)
    }

    private fun setLogReady(ready: Boolean) {
        logStreaming = ready
        runCatching { host.onLogReady(ready) }
            .onFailure { Timber.w(it, "Runtime host rejected log readiness") }
        runCatching { onLogReadyChanged(ready) }
            .onFailure { Timber.w(it, "Runtime snapshot rejected log readiness") }
    }

    fun startConnectionTracking() {
        stopConnectionTracking()
        connectionTrackingJob =
            scope.launch(Dispatchers.IO) {
                PollingTimers.ticks(PollingTimerSpecs.SessionConnectionTracking).collect {
                    runCatching {
                        val core =
                            com.github.yumelira.yumebox.runtime.service.core.CoreProcess.controller(
                                host.context
                            )
                        val snapshot = core.queryConnections()
                        ConnectionHistoryManager.updateConnections(snapshot.connections)
                    }
                }
            }
    }

    fun stopConnectionTracking() {
        connectionTrackingJob?.cancel()
        connectionTrackingJob = null
    }

    private companion object {
        private const val MAX_BUFFERED_LOGS = 256
    }
}
