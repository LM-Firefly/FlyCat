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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.throttleWhenScreenOff
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Components
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunForegroundServiceContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.service.R
import com.github.yumelira.yumebox.runtime.service.notification.NotificationPresentation
import com.github.yumelira.yumebox.runtime.service.notification.NotificationPresentationFactory
import com.github.yumelira.yumebox.runtime.service.root.RootTunServiceBridge
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStarted
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStopped
import tf.gal.yumebox.locale.FlyTxt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RootTunService : BaseService() {
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val powerController by lazy { ServicePowerController(this) }
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        powerController.start()
        createChannel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                launch { runCatching { RootTunServiceBridge.stop(appContextOrSelf) } }
                return START_NOT_STICKY
            }

            ACTION_START,
            null -> {
                val cachedStatus = RootTunStatusFlow.current(appContextOrSelf)
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        NotificationPresentationFactory.createStatus(
                            profileName =
                                cachedStatus.profileName
                                    ?: FlyTxt.Service.Notification.UnknownProfile,
                            status = describeStatus(cachedStatus),
                        )
                    ),
                )
                // The root process no longer writes the service_cache phase mirror (single-writer
                // rule); seed it here so the phase is visible before the first poll lands.
                syncStatus(cachedStatus)
                if (!cachedStatus.state.isActiveOrStopping && !cachedStatus.state.isRecovering) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (notificationJob?.isActive != true) {
                    notificationJob =
                        launch(Dispatchers.Default) {
                            var startedBroadcastSent = false
                            var unreachableCount = 0
                            var lastStatus = cachedStatus

                            PollingTimers.ticks(PollingTimerSpecs.RootTunStatusNotification)
                                .throttleWhenScreenOff(powerController.screenOn, slowIntervalMs = 60_000L)
                                .collect {
                                    if (!powerController.screenOn.value) return@collect
                                    val snapshotResult = runCatching {
                                        RootTunServiceBridge.queryStatus(appContextOrSelf)
                                    }
                                    val snapshot = snapshotResult.getOrNull()
                                    if (snapshot == null) {
                                        unreachableCount++
                                        val error = snapshotResult.exceptionOrNull()
                                        val fallbackStatus =
                                            RootTunStatusFlow.current(appContextOrSelf).takeIf {
                                                it.state != RuntimePhase.Idle ||
                                                    !it.profileName.isNullOrBlank() ||
                                                    !it.lastError.isNullOrBlank()
                                            } ?: lastStatus
                                        val title =
                                            fallbackStatus.profileName
                                                ?: FlyTxt.Service.Notification.UnknownProfile
                                        val content =
                                            if (unreachableCount >= 3) {
                                                describeStatus(
                                                    fallbackStatus.copy(
                                                        lastError =
                                                            fallbackStatus.lastError
                                                                ?: error?.message
                                                                ?: "State unavailable"
                                                    )
                                                )
                                            } else {
                                                error?.message ?: "Waiting for reconnect"
                                            }
                                        notificationManager.notify(
                                            NOTIFICATION_ID,
                                            buildNotification(
                                                NotificationPresentationFactory.createStatus(
                                                    profileName = title,
                                                    status = content,
                                                )
                                            ),
                                        )
                                        if (
                                            !fallbackStatus.state.isActiveOrStopping &&
                                                !fallbackStatus.state.isRecovering
                                        ) {
                                            stopSelf()
                                            return@collect
                                        }
                                        return@collect
                                    }

                                    unreachableCount = 0
                                    lastStatus = snapshot
                                    syncStatus(snapshot)

                                    if (
                                        snapshot.state == RuntimePhase.Running &&
                                            !startedBroadcastSent
                                    ) {
                                        sendClashStarted()
                                        startedBroadcastSent = true
                                    }

                                    if (
                                        snapshot.state == RuntimePhase.Idle ||
                                            snapshot.state == RuntimePhase.Failed
                                    ) {
                                        notificationManager.notify(
                                            NOTIFICATION_ID,
                                            buildNotification(
                                                NotificationPresentationFactory.createStatus(
                                                    profileName =
                                                        snapshot.profileName
                                                            ?: FlyTxt.Service.Notification
                                                                .UnknownProfile,
                                                    status = describeStatus(snapshot),
                                                )
                                            ),
                                        )
                                        stopSelf()
                                        return@collect
                                    }

                                    val profileName =
                                        snapshot.profileName
                                            ?: FlyTxt.Service.Notification.UnknownProfile
                                    val presentation =
                                        if (snapshot.state == RuntimePhase.Running) {
                                            buildTrafficPresentation(profileName)
                                        } else {
                                            NotificationPresentationFactory.createStatus(
                                                profileName = profileName,
                                                status = describeStatus(snapshot),
                                            )
                                        }
                                    notificationManager.notify(
                                        NOTIFICATION_ID,
                                        buildNotification(presentation),
                                    )
                                }
                        }
                }

                return START_STICKY
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        notificationJob = null
        powerController.stop()

        val snapshot = RootTunStatusFlow.current(appContextOrSelf)
        if (!snapshot.state.isActiveOrStopping) {
            StatusProvider.markRuntimeIdle(RunMode.Tun)
            sendClashStopped(snapshot.lastError)
        }

        super.onDestroy()
    }

    private suspend fun buildTrafficPresentation(profileName: String): NotificationPresentation {
        val now =
            runCatching { RootTunServiceBridge.queryTrafficNow(appContextOrSelf) }.getOrDefault(0L)
        val total =
            runCatching { RootTunServiceBridge.queryTrafficTotal(appContextOrSelf) }
                .getOrDefault(0L)
        return NotificationPresentationFactory.createRunning(
            profileName = profileName,
            trafficNow = now,
            trafficTotal = total,
        )
    }

    private fun buildNotification(presentation: NotificationPresentation): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent().apply {
                    component = Components.PROXY_SHEET_ACTIVITY
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RootTunService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(presentation.title)
            .setContentText(presentation.content)
            .setSubText(presentation.subText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(presentation.expandedText)
                    .setSummaryText(presentation.subText)
            )
            .setSmallIcon(R.drawable.ic_logo_service)
            .setColor(getColor(R.color.color_yumebox))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, FlyTxt.Service.Tile.ClickToStopProxy, stopIntent)
            .build()
    }

    private fun createChannel() {
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(CHANNEL_NAME)
                .build()
        )
    }

    private fun syncStatus(status: RootTunStatus) {
        when (status.state) {
            RuntimePhase.Idle -> StatusProvider.markRuntimeIdle(RunMode.Tun)
            RuntimePhase.Starting -> StatusProvider.markRuntimeStarting(RunMode.Tun)
            RuntimePhase.Running -> StatusProvider.markRuntimeRunning(RunMode.Tun)
            RuntimePhase.Stopping -> StatusProvider.markRuntimeStopping(RunMode.Tun)
            RuntimePhase.Failed -> StatusProvider.markRuntimeFailed(RunMode.Tun)
        }
    }

    private fun describeStatus(status: RootTunStatus): String =
        when (status.state) {
            RuntimePhase.Starting -> "Starting..."
            RuntimePhase.Running -> FlyTxt.Service.Notification.Running
            RuntimePhase.Stopping -> "Stopping..."
            RuntimePhase.Failed -> "Failed: ${status.lastError ?: "unknown error"}"
            RuntimePhase.Idle -> "Stopped"
        }

    companion object : RootTunForegroundServiceContract {
        private const val ACTION_START = "com.github.yumelira.yumebox.ROOT_TUN_SERVICE_START"
        private const val ACTION_STOP = "com.github.yumelira.yumebox.ROOT_TUN_SERVICE_STOP"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "flycat_root_tun_service"
        private const val CHANNEL_NAME = "YumeBox RootTun Service"
        // Pre-rebrand channel id, deleted on channel creation to avoid an orphaned entry.
        private const val LEGACY_CHANNEL_ID = "clash_root_tun_service"

        override fun start(context: Context) {
            val intent = Intent(context, RootTunService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        override fun stop(context: Context) {
            context.stopService(Intent(context, RootTunService::class.java))
        }
    }
}
