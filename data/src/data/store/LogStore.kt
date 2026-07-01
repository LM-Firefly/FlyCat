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

package com.github.yumelira.yumebox.data.store

import android.app.Application
import android.net.Uri
import com.github.yumelira.yumebox.core.data.LogStoreReader
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.data.gateway.LogRecordGateway
import com.github.yumelira.yumebox.data.logging.AppLogBuffer
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.enums.enumEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LogStore(
    private val application: Application,
    private val logRecordGateway: LogRecordGateway,
) : LogStoreReader {
    companion object {
        private const val DEFAULT_MAX_ENTRIES = 2000
        private const val LOG_TAIL_WINDOW_BYTES = 4L * 1024L * 1024L
        private val LOG_LINE_REGEX = """\[(.+?)] \[(.+?)] (.+)""".toRegex()
        private val LOG_LEVELS = enumEntries<LogMessage.Level>().associateBy { it.name }
    }

    private val logDir: File
        get() = logRecordGateway.getLogDir(application)

    private val _isRecordingState = MutableStateFlow(logRecordGateway.isRecording)
    override val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()

    override fun startRecording() {
        logRecordGateway.start(application)
        _isRecordingState.value = true
    }

    override fun stopRecording() {
        logRecordGateway.stop(application)
        _isRecordingState.value = false
    }

    override fun isRecording(): Boolean = logRecordGateway.isRecording

    override fun isCurrentRecordingFile(fileName: String): Boolean =
        isRecording() && logRecordGateway.currentLogFileName == fileName

    override fun listLogFiles(): List<LogFileInfo> {
        val currentlyRecording = isRecording()
        val currentFileName = logRecordGateway.currentLogFileName
        val files =
            logDir.listFiles(::isManagedLogFile)?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        return files.map { file ->
            LogFileInfo(
                name = file.name,
                createdAt = file.lastModified(),
                size = file.length(),
                isRecording = currentlyRecording && file.name == currentFileName,
            )
        }
    }

    override suspend fun readLogEntries(
        fileName: String,
        maxEntries: Int,
    ): List<LogEntry> =
        withContext(Dispatchers.IO) {
            val file = resolveLogFile(fileName) ?: return@withContext emptyList()
            if (maxEntries <= 0) return@withContext emptyList()
            try {
                readTailLogEntries(file, maxEntries)
            } catch (_: IOException) {
                emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
        }

    override suspend fun exportLogFile(fileName: String, targetUri: Any): Boolean =
        exportLogFileUri(fileName, targetUri as Uri)

    private suspend fun exportLogFileUri(fileName: String, targetUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val source = resolveLogFile(fileName) ?: return@withContext false
            try {
                application.contentResolver.openOutputStream(targetUri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: return@withContext false
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }

    override suspend fun exportMergedLog(fileName: String): String? =
        withContext(Dispatchers.IO) {
            val source = resolveLogFile(fileName) ?: return@withContext null
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val base = source.name.removeSuffix(logRecordGateway.logSuffix)
                val targetName = "merged_${base}_$timestamp${logRecordGateway.logSuffix}"
                val target = File(logDir, targetName)
                source.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetName
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

    override suspend fun exportRecentLogsToUri(targetUri: Any): Boolean =
        exportRecentLogsToUriInternal(targetUri as Uri)

    private suspend fun exportRecentLogsToUriInternal(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            application.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.bufferedWriter().use { writer ->
                    val recentLogs = AppLogBuffer.getSnapshot()
                    if (recentLogs.isNotEmpty()) {
                        recentLogs.forEach { writer.appendLine(it) }
                    } else {
                        val recentFile = logDir.listFiles(::isManagedLogFile)
                            ?.maxByOrNull { it.lastModified() }
                        recentFile?.forEachLine { writer.appendLine(it) }
                    }
                }
            } ?: return@withContext false
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun readTempLogEntries(maxEntries: Int): List<LogEntry> =
        withContext(Dispatchers.IO) {
            val currentlyRecording = isRecording()
            val currentFileName = logRecordGateway.currentLogFileName
            if (!currentlyRecording || currentFileName == null) {
                return@withContext emptyList()
            }
            readLogEntries(currentFileName, maxEntries)
        }

    override suspend fun writeLogEntries(targetUri: Any, entries: List<LogEntry>): Boolean =
        writeLogEntriesInternal(targetUri as Uri, entries)

    private suspend fun writeLogEntriesInternal(targetUri: Uri, entries: List<LogEntry>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                application.contentResolver.openOutputStream(targetUri)?.use { output ->
                    val sb = StringBuilder()
                    entries.forEach { entry ->
                        sb.append("[${entry.time}] [${entry.level.name}] ${entry.message}\n")
                    }
                    output.write(sb.toString().toByteArray())
                }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }

    override suspend fun deleteLogFile(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = resolveLogFile(fileName) ?: return@withContext false
            stopRecordingIfNeeded(file.name)
            file.delete()
        }

    override suspend fun deleteAllLogs() =
        withContext(Dispatchers.IO) {
            stopRecordingIfNeeded()
            val files = logDir.listFiles(::isManagedLogFile) ?: return@withContext
            files.forEach { it.delete() }
        }

    private suspend fun stopRecordingIfNeeded(fileName: String? = null) {
        if (!isRecording()) return
        if (fileName != null && !isCurrentRecordingFile(fileName)) return
        stopRecording()
        delay(logRecordGateway.stopWaitMillis)
    }

    private fun readTailLogEntries(file: File, maxEntries: Int): List<LogEntry> {
            val ring = ArrayDeque<LogEntry>(maxEntries)
            val startOffset = (file.length() - LOG_TAIL_WINDOW_BYTES).coerceAtLeast(0L)
            file.inputStream().buffered().use { input ->
                if (startOffset > 0L) {
                    var skipped = 0L
                    while (skipped < startOffset) {
                        val delta = input.skip(startOffset - skipped)
                        if (delta <= 0L) break
                        skipped += delta
                    }
                }
                input.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val entry = parseLogLine(line) ?: return@forEach
                        if (ring.size == maxEntries) {
                            ring.removeFirst()
                        }
                        ring.addLast(entry)
                    }
                }
        }
        return ring.toList()
    }

    private fun parseLogLine(line: String): LogEntry? {
        if (line.isBlank()) return null
        val match = LOG_LINE_REGEX.find(line) ?: return null
        val (timeStr, levelStr, message) = match.destructured
        val level = LOG_LEVELS[levelStr] ?: LogMessage.Level.Unknown
        return LogEntry(time = timeStr, level = level, message = message)
    }

    private fun resolveLogFile(fileName: String): File? {
        if (!isSafeLogFileName(fileName)) return null
        val file = File(logDir, fileName)
        return file.takeIf(::isManagedLogFile)
    }

    private fun isManagedLogFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        if (!file.name.endsWith(logRecordGateway.logSuffix)) return false
        val prefix = logRecordGateway.logPrefix
        return prefix.isBlank() || file.name.startsWith(prefix)
    }

    private fun isSafeLogFileName(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        if (fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return false
        }
        if (!fileName.endsWith(logRecordGateway.logSuffix)) return false
        val prefix = logRecordGateway.logPrefix
        return prefix.isBlank() || fileName.startsWith(prefix)
    }

}
