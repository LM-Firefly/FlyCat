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

package com.github.yumelira.yumebox.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.runtime.service.R
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LogRecordService : Service() {
    companion object {
        private const val TAG = "LogRecordService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "log_record_channel"
        private const val CHANNEL_NAME = "日志记录"
        private const val LOG_BUFFER_FLUSH_THRESHOLD_CHARS = 8 * 1024

        private const val ACTION_START = "com.github.yumelira.yumebox.LOG_START"
        private const val ACTION_STOP = "com.github.yumelira.yumebox.LOG_STOP"

        const val LOG_DIR = "logs"
        const val LOG_PREFIX = ""
        const val LOG_SUFFIX = ".log"

        @Volatile
        var isRecording: Boolean = false
            private set

        @Volatile
        var currentLogFileName: String? = null
            private set

        @Volatile
        private var instance: LogRecordService? = null

        fun start(context: Context) {
            val intent =
                Intent(context, LogRecordService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, LogRecordService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun getLogDir(context: Context): File {
            return File(context.filesDir, LOG_DIR).apply { mkdirs() }
        }

        @JvmStatic
        fun writeLog(logLine: String) {
            instance?.writeLogInternal(logLine)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var logWriter: BufferedWriter? = null
    private var flushJob: Job? = null
    private var logFile: File? = null
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val writerLock = Any()
    private val pendingLogBuffer = StringBuilder()
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        flushJob?.cancel()
        closeLogWriter()
        serviceScope.cancel()
        isRecording = false
        currentLogFileName = null
        instance = null
        super.onDestroy()
    }

    private fun writeLogInternal(logLine: String) {
        if (!isRecording) return
        runCatching {
            appendLogLine(logLine + "\n")
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "Runtime app log write failed")
        }
    }

    private fun startRecording() {
        if (isRecording) return

        runCatching {
                val logDir = getLogDir(applicationContext)
                val timestamp =
                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                val fileName = "$LOG_PREFIX$timestamp$LOG_SUFFIX"
                logFile = File(logDir, fileName)
                logWriter = BufferedWriter(FileWriter(logFile, true))

                currentLogFileName = fileName
                isRecording = true
                startFlushLoop()

                startForeground(NOTIFICATION_ID, createNotification())
            }
            .onFailure { error ->
                Timber.tag(TAG).e(error, "Log recording start failed")
                isRecording = false
                currentLogFileName = null
                stopSelf()
            }
    }

    private fun updateNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID, createNotification()) }
    }

    private fun stopRecording() {
        flushJob?.cancel()
        flushJob = null
        closeLogWriter()

        isRecording = false
        currentLogFileName = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeLogWriter() {
        runCatching {
                synchronized(writerLock) {
                    flushPendingBufferLocked()
                    logWriter?.flush()
                    logWriter?.close()
                    logWriter = null
                    logFile = null
                }
            }
            .onFailure { error -> Timber.tag(TAG).e(error, "Log writer close failed") }
        }

    private fun startFlushLoop() {
        flushJob?.cancel()
        flushJob = serviceScope.launch {
            while (flushSignal.tryReceive().isSuccess) {
            }
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            while (isActive) {
                flushSignal.receive()
                val isInteractive = pm?.isInteractive != false
                delay(if (isInteractive) PollingTimerSpecs.LogFlush.FOREGROUND_INTERVAL_MS else PollingTimerSpecs.LogFlush.SCREEN_OFF_INTERVAL_MS)
                flushPendingBuffer()
            }
        }
    }

    private fun appendLogLine(line: String) {
        synchronized(writerLock) {
            val wasBufferEmpty = pendingLogBuffer.isEmpty()
            pendingLogBuffer.append(line)
            if (pendingLogBuffer.length >= LOG_BUFFER_FLUSH_THRESHOLD_CHARS) {
                flushPendingBufferLocked()
            } else if (wasBufferEmpty) {
                flushSignal.trySend(Unit)
            }
        }
    }

    private fun flushPendingBuffer() {
        synchronized(writerLock) {
            flushPendingBufferLocked()
        }
    }

    private fun flushPendingBufferLocked() {
        if (pendingLogBuffer.isEmpty()) return
        logWriter?.apply {
            write(pendingLogBuffer.toString())
            flush()
        }
        pendingLogBuffer.setLength(0)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "日志记录服务通知"
                        setShowBadge(false)
                    }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent().setComponent(Components.MAIN_ACTIVITY)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent = Intent(this, LogRecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在记录日志")
            .setContentText(currentLogFileName ?: "日志记录中...")
            .setSmallIcon(R.drawable.ic_logo_service)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .build()
    }
}
