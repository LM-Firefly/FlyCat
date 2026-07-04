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

package com.github.yumelira.yumebox.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.runtime.api.Components
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.ILogObserver
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRecordService : Service() {
    companion object {
        private const val TAG = "LogRecordService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "log_record_channel"

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

        /**
         * Injected by the app layer so recording taps the mode-aware log channel (local core /
         * root TUN / external controller). Without it recording falls back to the in-process
         * core subscription, which only sees local TUN/HTTP runtimes.
         */
        @Volatile
        var clashManagerProvider: (suspend () -> IClashManager)? = null

        fun start(context: Context) {
            val intent =
                Intent(context, LogRecordService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LogRecordService::class.java))
        }

        fun getLogDir(context: Context): File = File(context.filesDir, LOG_DIR).apply { mkdirs() }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var logWriter: BufferedWriter? = null
    private var logCollectJob: Job? = null
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Must enter foreground unconditionally before any IO can fail; the service is
                // launched via startForegroundService and skipping this is an ANR-level crash.
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording()
            }
            ACTION_STOP -> stopRecording()
            else -> if (!isRecording) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closeLogWriter()
        serviceScope.cancel()
        isRecording = false
        currentLogFileName = null
        super.onDestroy()
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        logCollectJob = serviceScope.launch {
            val opened = runCatching {
                val logDir = getLogDir(applicationContext)
                val timestamp =
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "$LOG_PREFIX$timestamp$LOG_SUFFIX"
                logFile = File(logDir, fileName)
                logWriter = BufferedWriter(FileWriter(logFile, true))
                currentLogFileName = fileName
            }
            if (opened.isFailure) {
                Timber.tag(TAG).e(opened.exceptionOrNull(), "Log recording start failed")
                isRecording = false
                currentLogFileName = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            updateNotification()
            collectLogs()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun collectLogs() {
        val observer =
            object : ILogObserver {
                override fun newItem(log: LogMessage) {
                    if (isRecording) {
                        runCatching {
                                val line =
                                    "[${dateFormat.format(
                                    log.time
                                )}] [${log.level.name}] ${log.message}\n"
                                logWriter?.write(line)
                                logWriter?.flush()
                            }
                            .onFailure { error ->
                                Timber.tag(TAG).e(error, "Log write failed")
                            }
                    }
                }
            }

        val setObserver: ((ILogObserver?) -> Unit)? = resolveLogGateway()?.let { it::setLogObserver }
        if (setObserver == null) {
            // Without the injected gateway there is no mode-aware log source; recording would
            // silently produce an empty file, so fail visibly instead.
            Timber.tag(TAG).e("Log gateway unavailable; stopping recording")
            isRecording = false
            currentLogFileName = null
            closeLogWriter()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            setObserver(observer)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // fault barrier: the log source is a mode-dependent gateway (core/binder/HTTP);
            // recording must end gracefully instead of crashing the service.
            Timber.e(error, "Log observer setup failed")
        } finally {
            runCatching { setObserver(null) }
        }
    }

    private suspend fun resolveLogGateway(): IClashManager? =
        clashManagerProvider?.let { provider ->
            runCatching { provider() }
                .onFailure { error ->
                    Timber.tag(TAG).e(error, "Mode-aware log gateway resolution failed")
                }
                .getOrNull()
        }

    private fun updateNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID, createNotification()) }
    }

    private fun stopRecording() {
        logCollectJob?.cancel()
        logCollectJob = null
        closeLogWriter()

        isRecording = false
        currentLogFileName = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeLogWriter() {
        runCatching {
                logWriter?.flush()
                logWriter?.close()
                logWriter = null
                logFile = null
            }
            .onFailure { error -> Timber.tag(TAG).e(error, "Log writer close failed") }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    MLang.Service.Notification.LogChannel,
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { setShowBadge(false) }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
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
            .setContentTitle(MLang.Service.Notification.LogRecording)
            .setContentText(currentLogFileName ?: MLang.Service.Notification.LogRecordingPending)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                MLang.Service.Notification.Stop,
                stopPendingIntent,
            )
            .build()
    }
}
