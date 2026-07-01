package com.github.yumelira.yumebox.screen.log

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.LogStoreReader
import com.github.yumelira.yumebox.core.model.LogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogViewModel(private val repository: LogStoreReader) : ViewModel() {
    private val recordingFlow = MutableStateFlow(repository.isRecording())
    private val logEntriesFlow = MutableStateFlow<List<LogEntry>>(emptyList())

    val isRecording: StateFlow<Boolean> = recordingFlow.asStateFlow()
    val tempLogEntries: StateFlow<List<LogEntry>> = logEntriesFlow.asStateFlow()

    fun startRecording() {
        repository.startRecording()
        recordingFlow.value = true
        logEntriesFlow.value = emptyList()
    }

    fun stopRecording() {
        repository.stopRecording()
        recordingFlow.value = false
    }

    fun refreshTempLogEntries() {
        if (!recordingFlow.value) return
        viewModelScope.launch(Dispatchers.IO) {
            logEntriesFlow.value = repository.readTempLogEntries()
        }
    }

    suspend fun saveTempLog(targetUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val entries = logEntriesFlow.value
            if (entries.isEmpty()) return@withContext false
            try {
                repository.writeLogEntries(targetUri = targetUri, entries = entries)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                false
            }
        }

    suspend fun exportRecentLogsToUri(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        repository.exportRecentLogsToUri(targetUri)
    }
}
