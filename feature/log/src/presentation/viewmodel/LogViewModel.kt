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

package com.github.yumelira.yumebox.feature.log.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.contract.LogStoreReader
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogViewModel(private val repository: LogStoreReader) : ViewModel() {
    companion object {
        private const val LOG_SCREEN_OWNER = "log_screen"
        private const val LOG_DETAIL_OWNER_PREFIX = "log_detail:"
    }

    val logDir: java.io.File get() = repository.logDir
    val isRecording: StateFlow<Boolean> = repository.isRecordingState

    private val _logFiles = MutableStateFlow<List<LogFileInfo>>(emptyList())
    val logFiles: StateFlow<List<LogFileInfo>> = _logFiles.asStateFlow()

    /** Per-file byte offset tracker for incremental log reading. */
    private val fileOffsets = mutableMapOf<String, Long>()
    /** Per-file accumulated entry cache for incremental reading. */
    private val fileEntryCache = mutableMapOf<String, ArrayDeque<LogEntry>>()

    init {
        refreshLogFiles()
        viewModelScope.launch {
            repository.isRecordingState
                .drop(1)
                .collectLatest {
                    delay(300)
                    refreshLogFiles()
                }
        }
    }

    fun startRecording() {
        repository.startRecording()
    }

    fun stopRecording() {
        repository.stopRecording()
    }

    fun refreshLogFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _logFiles.value = repository.listLogFiles()
        }
    }

    fun isCurrentFileRecording(fileName: String): Boolean {
        return repository.isCurrentRecordingFile(fileName)
    }

    suspend fun readLogContent(fileName: String): List<LogEntry> = withContext(Dispatchers.IO) {
        repository.readLogEntries(fileName)
    }

    /**
     * Incrementally reads log entries for [fileName]. On first call (or after [resetIncrementalCache])
     * performs a full tail read; subsequent calls only read newly appended bytes.
     * Returns entries in reversed order (newest first) for display.
     */
    suspend fun readLogContentIncremental(fileName: String): List<LogEntry> = withContext(Dispatchers.IO) {
        val sinceOffset = fileOffsets[fileName] ?: 0L
        val (newEntries, newOffset) = repository.readLogEntriesSince(fileName, sinceOffset)
        if (newOffset < sinceOffset) {
            // File was truncated/rotated — clear cache for this file
            fileEntryCache.remove(fileName)
        }
        if (newEntries.isNotEmpty()) {
            val cache = fileEntryCache.getOrPut(fileName) { ArrayDeque() }
            cache.addAll(newEntries)
            // Keep at most 2000 entries to avoid unbounded growth
            while (cache.size > 2000) { cache.removeFirst() }
        }
        fileOffsets[fileName] = newOffset
        (fileEntryCache[fileName] ?: emptyList()).toList().asReversed()
    }

    /** Resets incremental cache for a specific file (e.g. after deletion). */
    private fun resetIncrementalCache(fileName: String) {
        fileOffsets.remove(fileName)
        fileEntryCache.remove(fileName)
    }

    suspend fun exportMergedLog(fileName: String): String? = withContext(Dispatchers.IO) {
        repository.exportMergedLog(fileName)
    }

    fun deleteLogFile(fileName: String) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteLogFile(fileName) }
            if (!deleted) return@launch
            resetIncrementalCache(fileName)
            refreshLogFiles()
        }
    }

    fun deleteAllLogs() {
        viewModelScope.launch {
            if (repository.isRecording()) {
                repository.stopRecording()
                delay(300)
            }
            withContext(Dispatchers.IO) { repository.deleteAllLogs() }
            fileOffsets.clear()
            fileEntryCache.clear()
            refreshLogFiles()
        }
    }

    suspend fun exportLogToUri(fileName: String, targetUri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.exportLogFile(fileName, targetUri)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false
        }
    }

    suspend fun exportRecentLogsToUri(targetUri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        repository.exportRecentLogsToUri(targetUri)
    }

    fun setLogScreenVisible(visible: Boolean) {
        repository.setLogPreviewVisible(LOG_SCREEN_OWNER, visible)
    }

    fun setLogDetailVisible(fileName: String, visible: Boolean) {
        repository.setLogPreviewVisible("$LOG_DETAIL_OWNER_PREFIX$fileName", visible)
    }
}
