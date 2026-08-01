package com.github.yumelira.yumebox.data.logging

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.github.yumelira.yumebox.core.contract.AppLogSettings
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

    /**
     * Sink for mihomo native logs (level, tag, message).
     * Set at app startup so mihomo logs are captured in AppLogBuffer even when LogRecordService is not running.
     */
    @Volatile
    var mihomoLogSink: ((Int, String?, String) -> Unit)? = null
}

object AppLogBuffer : AppLogSettings {
    private const val MAX_SIZE = 1000
    private val buffer = ArrayDeque<String>(MAX_SIZE)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @Volatile
    override var minLogLevel: Int = Log.DEBUG
    private val rwLock = java.util.concurrent.locks.ReentrantReadWriteLock()

    internal fun shouldCaptureLog(priority: Int, tag: String?, message: String): Boolean {
        if (priority < minLogLevel) return false
        if (tag == "mihomo" && priority < Log.WARN) return false
        return message.isNotBlank()
    }

    fun add(priority: Int, tag: String?, message: String) {
        if (!shouldCaptureLog(priority, tag, message)) return
        appendFormatted(priority, tag, message)
    }

    /** Insert a log line bypassing [minLogLevel] (e.g. mihomo native logs filtered by their own level). */
    fun forceAdd(priority: Int, tag: String?, message: String) {
        if (!shouldCaptureLog(priority, tag, message)) return
        appendFormatted(priority, tag, message)
    }

    private fun appendFormatted(priority: Int, tag: String?, message: String) {
        val time = LocalDateTime.now().format(dateTimeFormatter)
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
            if (buffer.size >= MAX_SIZE) { buffer.removeFirst() }
            buffer.addLast(logLine)
        } finally { rwLock.writeLock().unlock() }
        AppLogBridge.runtimeLogWriter?.invoke(logLine)
    }
    fun getSnapshot(): List<String> {
        rwLock.readLock().lock()
        try { return buffer.toList() } finally { rwLock.readLock().unlock() }
    }
}

class AppLogTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        AppLogBuffer.add(priority, tag, message)
        if (t != null) { AppLogBuffer.add(priority, tag, Log.getStackTraceString(t)) }
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
        defaultHandler?.uncaughtException(thread, ex) ?: run { Process.killProcess(Process.myPid()); kotlin.system.exitProcess(10) }
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
                    val recentLogs = AppLogBuffer.getSnapshot()
                    if (recentLogs.isNotEmpty()) {
                        writer.println("\n--- Recent App Logs ---")
                        recentLogs.forEach { writer.println(it) }
                        writer.println("--- End Recent App Logs ---")
                    }
                } catch (error: Exception) { Timber.e(error, "Error appending recent logs") }
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
        val newestTimestamp = exitInfos.maxOfOrNull { it.timestamp } ?: return
        val prefs = ctx.getSharedPreferences("crash_handler_prefs", Context.MODE_PRIVATE)
        val lastSavedTimestamp = prefs.getLong("last_exit_info_ts", 0L)
        if (newestTimestamp <= lastSavedTimestamp) return
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        val logDir = File(ctx.filesDir, "logs").apply { mkdirs() }
        val file = File(logDir, "native_exit_$timestamp.log")
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
                writer.println("---")
            }
        }
        prefs.edit().putLong("last_exit_info_ts", newestTimestamp).apply()
        Timber.w("Native/ANR exit info saved to ${file.absolutePath}")
    }
    // Exit reason codes matching android.app.ApplicationExitReason (API 30+)
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
