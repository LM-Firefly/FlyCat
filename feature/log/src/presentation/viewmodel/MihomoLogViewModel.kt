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

package com.github.lmfirefly.flycat.feature.log.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.model.LogMessage
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import com.github.lmfirefly.flycat.runtime.api.remote.ILogObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * mihomo 日志预览中显示的最低日志级别。[All] 保留所有条目；其他值则保留该级别及其更严重的级别（Debug < Info < Warning < Error）。
 * 这纯粹是针对完整实时流的客户端侧过滤——内核配置的日志级别不再限制用户在此处可以看到的内容。
 */
enum class LogLevelFilter {
    All,
    Debug,
    Info,
    Warning,
    Error,
}

/** 实时预览中渲染的一条 mihomo 日志行。 */
data class LiveLogEntry(
    val id: Long,
    val time: String,
    val level: LogMessage.Level,
    val message: String,
)

enum class LogConnectionState {
    Connecting,
    Live,
    Retrying,
}

/**
 * 将 mihomo（内核）的实时日志流式传输到有界批处理缓冲区中，并提供实时级别/文本过滤视图——移植自 YumeBox 的实时日志预览功能。
 * 通过 [ProxyControlContract.setLogObserver] 订阅，模式感知（local=JNI 推送 / root=轮询 / remote=HTTP 流），故 local/root/remote 均可预览。
 * 订阅仅在预览屏幕可见时建立：可见时 [setPreviewVisible] 订阅并启动处理，隐藏时退订并停止处理，与录制/后台完全解耦，仅预览时才订阅以节省功耗。
 */
class MihomoLogViewModel(private val clashControl: ProxyControlContract) : ViewModel() {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val nextId = AtomicLong(0L)
    private val pendingLock = Any()
    private val pendingEntries = ArrayDeque<LiveLogEntry>()
    private val _entries = MutableStateFlow<List<LiveLogEntry>>(emptyList())
    private val _levelFilter = MutableStateFlow(LogLevelFilter.All)
    private val _searchQuery = MutableStateFlow("")
    private val _connectionState = MutableStateFlow(LogConnectionState.Connecting)
    private val logObserver = object : ILogObserver {
        override fun newItem(log: LogMessage) {
            enqueue(log)
        }
    }
    private var batchJob: Job? = null
    private var processing = false
    val levelFilter: StateFlow<LogLevelFilter> = _levelFilter.asStateFlow()
    val connectionState: StateFlow<LogConnectionState> = _connectionState.asStateFlow()
    val filteredLogEntries: StateFlow<List<LiveLogEntry>> = combine(_entries, _levelFilter, _searchQuery) { entries, filter, query ->
        entries.filter { entry ->
            entry.level.passes(filter) && (query.isBlank() || entry.message.contains(query, ignoreCase = true) || entry.level.name.contains(query, ignoreCase = true))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    data class LogScreenState(
        val filteredEntries: List<LiveLogEntry> = emptyList(),
        val levelFilter: LogLevelFilter = LogLevelFilter.All,
        val connectionState: LogConnectionState = LogConnectionState.Connecting,
    )
    val screenState: StateFlow<LogScreenState> = combine(filteredLogEntries, levelFilter, connectionState) { entries, filter, connection ->
        LogScreenState(
            filteredEntries = entries,
            levelFilter = filter,
            connectionState = connection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogScreenState(),
    )
    /** 在此屏幕可见时切换原生订阅并启停日志处理。mihomo 日志流仅在预览打开时订阅/处理，与录制/后台解耦，避免网络爆发时的无谓唤醒与耗电。 */
    fun setPreviewVisible(visible: Boolean) {
        if (visible) {
            startProcessing()
            if (_connectionState.value != LogConnectionState.Live) {
                _connectionState.value = LogConnectionState.Connecting
                viewModelScope.launch {
                    delay(CONNECT_GRACE_MS)
                    if (_connectionState.value == LogConnectionState.Connecting) {
                        _connectionState.value = LogConnectionState.Live
                    }
                }
            }
        } else {
            stopProcessing()
        }
    }
    private fun startProcessing() {
        if (processing) return
        processing = true
        clashControl.setLogObserver(logObserver)
        batchJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(LOG_BATCH_WINDOW_MS)
                flush()
            }
        }
    }
    private fun stopProcessing() {
        processing = false
        clashControl.setLogObserver(null)
        batchJob?.cancel()
        batchJob = null
    }
    fun setLevelFilter(filter: LogLevelFilter) {
        _levelFilter.value = filter
    }
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    /** 当前筛选条目的快照，用于导出/复制。 */
    fun currentEntries(): List<LiveLogEntry> = filteredLogEntries.value
    override fun onCleared() {
        super.onCleared()
        stopProcessing()
    }
    private fun enqueue(message: LogMessage) {
        // 跳过应用端的 "[APP]" 引导标记，以保持 mihomo 预览输出为纯内核日志。
        if (message.message.startsWith("[APP]")) return
        if (_connectionState.value == LogConnectionState.Connecting) {
            _connectionState.value = LogConnectionState.Live
        }
        val entry = LiveLogEntry(
            id = nextId.incrementAndGet(),
            time = synchronized(timeFormat) { timeFormat.format(Date(message.time.time)) },
            level = message.level,
            message = message.message,
        )
        synchronized(pendingLock) {
            if (pendingEntries.size == MAX_ENTRIES) pendingEntries.removeFirst()
            pendingEntries.addLast(entry)
        }
    }
    private fun flush() {
        val batch = synchronized(pendingLock) {
            if (pendingEntries.isEmpty()) {
                emptyList()
            } else {
                pendingEntries.toList().also { pendingEntries.clear() }
            }
        }
        if (batch.isNotEmpty()) {
            _entries.update { entries -> (batch.asReversed() + entries).take(MAX_ENTRIES) }
        }
    }
    private fun LogMessage.Level.passes(filter: LogLevelFilter): Boolean {
        val rank = when (this) {
            LogMessage.Level.Debug -> 0
            LogMessage.Level.Info -> 1
            LogMessage.Level.Warning -> 2
            LogMessage.Level.Error -> 3
            LogMessage.Level.Silent,
            LogMessage.Level.Unknown,
                -> return filter == LogLevelFilter.All
        }
        val min = when (filter) {
            LogLevelFilter.All -> return true
            LogLevelFilter.Debug -> 0
            LogLevelFilter.Info -> 1
            LogLevelFilter.Warning -> 2
            LogLevelFilter.Error -> 3
        }
        return rank >= min
    }
    companion object {
        private const val MAX_ENTRIES = 2_000
        private const val LOG_BATCH_WINDOW_MS = 280L
        private const val CONNECT_GRACE_MS = 500L
    }
}
