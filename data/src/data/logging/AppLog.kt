package com.github.yumelira.yumebox.data.logging

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.github.yumelira.yumebox.core.data.AppLogSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import timber.log.Timber

object AppLogBridge {
    @Volatile
    var runtimeLogWriter: ((String) -> Unit)? = null
}

object AppLogBuffer : AppLogSettings {
    private const val MAX_SIZE = 1000
    private val buffer = ArrayDeque<String>(MAX_SIZE)
    @SuppressLint("ConstantLocale")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    @Volatile
    override var minLogLevel: Int = Log.DEBUG
    @Synchronized
    fun add(priority: Int, tag: String?, message: String) {
        if (priority < minLogLevel) return
        val time = dateFormat.format(Date())
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
        if (buffer.size >= MAX_SIZE) {
            buffer.removeFirst()
        }
        buffer.addLast(logLine)
        AppLogBridge.runtimeLogWriter?.invoke(logLine)
    }
    @Synchronized
    fun getSnapshot(): List<String> {
        return buffer.toList()
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
        runCatching { saveRecentExitInfoToFile() }
            .onFailure { Timber.w(it, "Failed to collect process exit info") }
    }
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        handleException(ex)
        defaultHandler?.uncaughtException(thread, ex)
            ?: run {
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
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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
                } catch (error: Exception) {
                    Timber.e(error, "Error appending recent logs")
                }
            }
            Timber.i("Crash log saved to ${file.absolutePath}")
        } catch (error: Exception) {
            Timber.e(error, "Error saving crash log")
        }
    }
    private fun saveRecentExitInfoToFile() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = ctx.getSystemService(ActivityManager::class.java) ?: return
        val exitInfos =
            activityManager.getHistoricalProcessExitReasons(ctx.packageName, 0, 10)
                .filter {
                    it.reason == ApplicationExitReason.NATIVE_CRASH ||
                        it.reason == ApplicationExitReason.ANR ||
                        it.reason == ApplicationExitReason.CRASH
                }
        if (exitInfos.isEmpty()) return
        val newestTimestamp = exitInfos.maxOfOrNull { it.timestamp } ?: return
        val prefs = ctx.getSharedPreferences("crash_handler_prefs", Context.MODE_PRIVATE)
        val lastSavedTimestamp = prefs.getLong("last_exit_info_ts", 0L)
        if (newestTimestamp <= lastSavedTimestamp) return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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
    // android.app.ApplicationExitReason (API 30+)
    private object ApplicationExitReason {
        const val CRASH = 4
        const val NATIVE_CRASH = 5
        const val ANR = 6
    }
    private fun reasonToString(reason: Int): String {
        return when (reason) {
            ApplicationExitReason.CRASH -> "CRASH"
            ApplicationExitReason.NATIVE_CRASH -> "NATIVE_CRASH"
            ApplicationExitReason.ANR -> "ANR"
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
