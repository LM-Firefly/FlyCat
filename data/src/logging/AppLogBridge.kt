package com.github.lmfirefly.flycat.data.logging

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.github.lmfirefly.flycat.core.contract.AppLogSettings
import com.tencent.mmkv.MMKV
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.LinkedHashMap
import timber.log.Timber

object AppLogBridge {
    @Volatile
    var runtimeLogWriter: ((String) -> Unit)? = null

    /** 文件写入路径开关：为 false 时，日志仍会被缓冲以便导出，但不会转发到 LogRecordService。 */
    @Volatile
    var runtimeLogRecordingEnabled: Boolean = false

    fun updateRuntimeLogRecordingDemand(enabled: Boolean) {
        runtimeLogRecordingEnabled = enabled
    }
}

object AppLogBuffer : AppLogSettings {
    private const val MAX_SIZE = 1000
    private val appBuffer = ArrayDeque<String>(MAX_SIZE)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private var lastTimestampMs: Long = -1L
    private var cachedTimestamp: String = ""
    @Volatile
    override var minLogLevel: Int = Log.DEBUG
    private val rwLock = java.util.concurrent.locks.ReentrantReadWriteLock()

    internal fun shouldCaptureLog(priority: Int, tag: String?, message: String): Boolean {
        if (message.isBlank()) return false
        if (priority < minLogLevel) return false
        return true
    }

    fun add(priority: Int, tag: String?, message: String) {
        if (!shouldCaptureLog(priority, tag, message)) return
        appendFormatted(priority, tag, message)
    }

    private fun appendFormatted(priority: Int, tag: String?, message: String) {
        val nowMs = System.currentTimeMillis()
        val time = synchronized(this) {
            if (nowMs == lastTimestampMs) {
                cachedTimestamp
            } else {
                lastTimestampMs = nowMs
                cachedTimestamp = LocalDateTime.now().format(dateTimeFormatter)
                cachedTimestamp
            }
        }
        val level = when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
        }
        val logLine = "[$time] [$level] ${tag?.let { "$it: " } ?: ""}$message"
        rwLock.writeLock().lock()
        try {
            if (appBuffer.size >= MAX_SIZE) { appBuffer.removeFirst() }
            appBuffer.addLast(logLine)
        } finally {
            rwLock.writeLock().unlock()
        }
        if (AppLogBridge.runtimeLogRecordingEnabled) {
            AppLogBridge.runtimeLogWriter?.invoke(logLine)
        }
    }

    fun getAppSnapshot(): List<String> {
        rwLock.readLock().lock()
        try {
            return appBuffer.toList()
        } finally {
            rwLock.readLock().unlock()
        }
    }
}

class AppLogTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        AppLogBuffer.add(priority, tag, message)
        if (t != null) {
            AppLogBuffer.add(priority, tag, Log.getStackTraceString(t))
        }
    }
}

@SuppressLint("StaticFieldLeak")
object CrashHandler : Thread.UncaughtExceptionHandler {
    private var context: Context? = null
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    fun init(context: Context) {
        this.context = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(this)
        runCatching { saveRecentExitInfoToFile() }.onFailure { Timber.w(it, "Failed to collect process exit info") }
    }
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        handleException(ex)
        // Flush all MMKV mmap pages to disk before the process dies.
        runCatching { MMKV.defaultMMKV().sync() }
        defaultHandler?.uncaughtException(thread, ex) ?: run {
            Process.killProcess(Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }
    private fun handleException(ex: Throwable?): Boolean {
        if (ex == null) return false
        saveCrashInfoToFile(ex)
        return true
    }
    private fun saveCrashInfoToFile(ex: Throwable) {
        val ctx = context ?: return
        try {
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            val fileName = "crash_$timestamp.log"
            val logDir = File(ctx.filesDir, "logs").apply { mkdirs() }
            val file = File(logDir, fileName)
            file.printWriter().use { writer ->
                collectDeviceInfo(ctx).forEach { (k, v) ->
                    writer.println("$k=$v")
                }
                ex.printStackTrace(writer)
                try {
                    val recentLogs = AppLogBuffer.getAppSnapshot()
                    if (recentLogs.isNotEmpty()) {
                        writer.println("\n--- Recent App Logs ---")
                        recentLogs.forEach { writer.println(it) }
                        writer.println("--- End Recent App Logs ---")
                    }
                } catch (error: Exception) {
                    Timber.e(error, "Error appending recent logs")
                }
            }
            Timber.i("Crash log saved to ${file.absolutePath}")
        } catch (error: Exception) { Timber.e(error, "Error saving crash log") }
    }
    private fun saveRecentExitInfoToFile() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = ctx.getSystemService(ActivityManager::class.java) ?: return
        val exitInfos = activityManager.getHistoricalProcessExitReasons(ctx.packageName, 0, 10)
            .filter {
                it.reason == ExitReasonCode.NATIVE_CRASH ||
                it.reason == ExitReasonCode.ANR ||
                it.reason == ExitReasonCode.CRASH
            }
        if (exitInfos.isEmpty()) return
        val newest = exitInfos.maxByOrNull { it.timestamp } ?: return
        val prefs = ctx.getSharedPreferences("crash_handler_prefs", Context.MODE_PRIVATE)
        val lastSavedTimestamp = prefs.getLong("last_exit_info_ts", 0L)
        if (newest.timestamp <= lastSavedTimestamp) return
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        val logDir = File(ctx.filesDir, "logs").apply { mkdirs() }
        // 按最新一条的 reason 命名：Java 崩溃写入 crash_* 区（与进程内处理器同区展示），ANR 单列，避免全部伪装成 native_exit。
        val file = File(logDir, "${exitReasonFilePrefix(newest.reason)}_$timestamp.log")
        file.printWriter().use { writer ->
            writer.println("package=${ctx.packageName}")
            writer.println("collectedAt=$timestamp")
            writer.println()
            exitInfos.sortedByDescending { it.timestamp }.forEach { info ->
                writer.println("reason=${reasonToString(info.reason)}")
                writer.println("status=${info.status}")
                writer.println("importance=${info.importance}")
                writer.println("pid=${info.pid}")
                writer.println("processName=${info.processName}")
                writer.println("timestamp=${info.timestamp}")
                writer.println("description=${info.description}")
                // 在可用时附加 Native/Java 追踪（API 31+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        info.traceInputStream?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.isNotEmpty()) {
                                // tombstone(protobuf) 与 ANR/文本 trace 统一抽取可读字符串行：信号、原因、崩溃库、符号、线程名等（原 32 字节启发式会把 tombstone 误判为 utf8 文本写成乱码）。
                                writer.println("trace=")
                                writer.println("encoding=strings")
                                extractReadableStrings(bytes).forEach { writer.println(it) }
                            }
                        }
                    } catch (_: Exception) { /* best-effort */ }
                }
                writer.println("---")
            }
        }
        prefs.edit().putLong("last_exit_info_ts", newest.timestamp).apply()
        Timber.w("Native/ANR exit info saved to ${file.absolutePath}")
    }
    /** ApplicationExitInfo 落盘文件前缀：Java 崩溃与进程内 crash_* 同区，ANR 单列前缀。 */
    private fun exitReasonFilePrefix(reason: Int): String = when (reason) {
        ExitReasonCode.CRASH -> "crash_exit"
        ExitReasonCode.ANR -> "anr_exit"
        else -> "native_exit"
    }
    // 与 android.app.ApplicationExitReason 匹配的退出原因代码（API 30+）
    private object ExitReasonCode {
        const val CRASH = 4
        const val NATIVE_CRASH = 5
        const val ANR = 6
    }

    private fun reasonToString(reason: Int): String {
        return when (reason) {
            ExitReasonCode.CRASH -> "CRASH"
            ExitReasonCode.NATIVE_CRASH -> "NATIVE_CRASH"
            ExitReasonCode.ANR -> "ANR"
            else -> "OTHER($reason)"
        }
    }

    /** 从二进制 trace（protobuf tombstone 或文本）中抽取可打印 ASCII 连续段，每段一行，给出可读的崩溃摘要。 */
    private fun extractReadableStrings(bytes: ByteArray, minRun: Int = 4, maxLines: Int = 4000): List<String> {
        val lines = ArrayList<String>()
        val sb = StringBuilder()
        var last: String? = null
        fun flush() {
            if (sb.length >= minRun) {
                val s = sb.toString()
                if (s != last) { lines.add(s); last = s }
            }
            sb.setLength(0)
        }
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) sb.append(c.toChar()) else flush()
            if (lines.size >= maxLines) return lines
        }
        flush()
        return lines
    }

    private fun collectDeviceInfo(context: Context): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        map["MANUFACTURER"] = Build.MANUFACTURER
        map["BRAND"] = Build.BRAND
        map["MODEL"] = Build.MODEL
        map["SDK_INT"] = Build.VERSION.SDK_INT.toString()
        map["RELEASE"] = Build.VERSION.RELEASE
        map["packageName"] = context.packageName
        return map
    }
}
