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

package com.github.yumelira.yumebox.data.store

import android.app.Application
import android.net.Uri
import com.github.yumelira.yumebox.core.contract.LogRecordGateway
import com.github.yumelira.yumebox.core.contract.LogStoreReader
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.data.logging.AppLogBridge
import com.github.yumelira.yumebox.data.logging.AppLogBuffer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
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
        private const val LOG_TAIL_WINDOW_BYTES = 1L * 1024L * 1024L
        private val logLineRegex = """\[(.+?)] \[(.+?)] (.+)""".toRegex()
        private val logLevels = enumEntries<LogMessage.Level>().associateBy { it.name }
    }

    override val logDir: File
        get() = logRecordGateway.getLogDir(application)

    private val _isRecordingState = MutableStateFlow(logRecordGateway.isRecording)
    override val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()

    init {
        AppLogBridge.updateRuntimeLogRecordingDemand(logRecordGateway.isRecording)
    }

    override fun startRecording() {
        var failure: Throwable? = null
        runCatching { logRecordGateway.start(application) }
            .onFailure { failure = it }
        val recording = logRecordGateway.isRecording
        AppLogBridge.updateRuntimeLogRecordingDemand(recording)
        _isRecordingState.value = recording
        failure?.let { throw it }
    }

    override fun stopRecording() {
        var failure: Throwable? = null
        runCatching { logRecordGateway.stop(application) }
            .onFailure { failure = it }
        val recording = logRecordGateway.isRecording
        AppLogBridge.updateRuntimeLogRecordingDemand(recording)
        _isRecordingState.value = recording
        failure?.let { throw it }
    }

    override fun setLogPreviewVisible(owner: String, visible: Boolean) {
        AppLogBridge.setLogPreviewVisible(owner, visible)
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

    override suspend fun readLogEntriesSince(fileName: String, sinceByteOffset: Long, maxEntries: Int): Pair<List<LogEntry>, Long> = withContext(Dispatchers.IO) {
        val file = resolveLogFile(fileName)
            ?: return@withContext emptyList<LogEntry>() to sinceByteOffset
        if (maxEntries <= 0) return@withContext emptyList<LogEntry>() to sinceByteOffset
        try {
            val currentLength = file.length()
            if (currentLength < sinceByteOffset || sinceByteOffset < 0L) {
                // File was truncated/rotated — full re-read
                val entries = readTailLogEntries(file, maxEntries)
                entries to currentLength
            } else if (currentLength == sinceByteOffset) {
                // No new data
                emptyList<LogEntry>() to sinceByteOffset
            } else {
                // Read only the appended portion
                val newEntries = readLogEntriesFromOffset(file, sinceByteOffset, maxEntries)
                newEntries to currentLength
            }
        } catch (_: IOException) { emptyList<LogEntry>() to sinceByteOffset } catch (_: SecurityException) { emptyList<LogEntry>() to sinceByteOffset }
    }

    override suspend fun exportLogFile(fileName: String, targetUri: Any): Boolean = exportLogFileUri(fileName, targetUri as Uri)

    private suspend fun exportLogFileUri(fileName: String, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun exportMergedLog(fileName: String): String? = withContext(Dispatchers.IO) {
        val source = resolveLogFile(fileName) ?: return@withContext null
        try {
            val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val base = source.name.removeSuffix(logRecordGateway.logSuffix)
            val targetName = "merged_${base}_$timestamp${logRecordGateway.logSuffix}"
            val target = File(logDir, targetName)
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetName
        } catch (_: IOException) { null } catch (_: SecurityException) { null }
    }

    override suspend fun exportRecentLogsToUri(targetUri: Any): Boolean = exportRecentLogsToUriInternal(targetUri as Uri)

    private suspend fun exportRecentLogsToUriInternal(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            application.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.bufferedWriter().use { writer ->
                    writer.appendLine("=== App Logs (respect app log level) ===")
                    val appLogs = AppLogBuffer.getAppSnapshot()
                    if (appLogs.isEmpty()) {
                        writer.appendLine("(empty)")
                    } else {
                        appLogs.forEach { writer.appendLine(it) }
                    }
                    writer.appendLine()
                    writer.appendLine("=== Crash Logs ===")
                    val crashFiles = listRecentCrashLogFiles()
                    if (crashFiles.isEmpty()) {
                        writer.appendLine("(empty)")
                    } else {
                        crashFiles.forEach { file ->
                            writer.appendLine("--- ${file.name} ---")
                            file.forEachLine { writer.appendLine(it) }
                            writer.appendLine("--- end ${file.name} ---")
                            writer.appendLine()
                        }
                    }
                    writer.appendLine("=== Mihomo Logs (respect core log level) ===")
                    val mihomoLogs = AppLogBuffer.getMihomoSnapshot()
                    if (mihomoLogs.isEmpty()) {
                        writer.appendLine("(empty)")
                    } else {
                        mihomoLogs.forEach { writer.appendLine(it) }
                    }
                }
            } ?: return@withContext false
            true
        } catch (_: IOException) { false } catch (_: SecurityException) { false }
    }

    private fun listRecentCrashLogFiles(maxFiles: Int = 2): List<File> {
        val files = logDir.listFiles { file ->
            file.isFile && (
                file.name.startsWith("crash_") ||
                file.name.startsWith("native_exit_")
            ) && file.name.endsWith(logRecordGateway.logSuffix)
        } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.take(maxFiles)
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
                    if (ring.size == maxEntries) { ring.removeFirst() }
                    ring.addLast(entry)
                }
            }
        }
        return ring.toList()
    }

    /**
     * Reads log entries starting from [startOffset] using [FileChannel] to seek,
     * then [BufferedReader] with UTF-8 to correctly decode multi-byte characters.
     * At most [maxEntries] entries are returned.
     */
    private fun readLogEntriesFromOffset(file: File, startOffset: Long, maxEntries: Int): List<LogEntry> {
        val entries = ArrayList<LogEntry>(maxEntries)
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            channel.position(startOffset)
            // Use Channels.newInputStream + UTF-8 Reader — RandomAccessFile.readLine()
            // uses ISO-8859-1 which corrupts multi-byte chars (e.g. Chinese).
            java.io.InputStreamReader(java.nio.channels.Channels.newInputStream(channel), Charsets.UTF_8).buffered().use { reader ->
                // Skip to the next complete line boundary (the previous read may have ended mid-line)
                if (startOffset > 0L) { reader.readLine() }
                var line: String?
                while (reader.readLine().also { line = it } != null) { val entry = parseLogLine(line!!) ?: continue; if (entries.size >= maxEntries) break; entries.add(entry) }
            }
        }
        return entries
    }

    private fun parseLogLine(line: String): LogEntry? {
        if (line.isBlank()) return null
        val match = logLineRegex.find(line) ?: return null
        val (timeStr, levelStr, message) = match.destructured
        val level = logLevels[levelStr] ?: LogMessage.Level.Unknown
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
