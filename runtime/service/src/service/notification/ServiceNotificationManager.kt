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

package com.github.yumelira.yumebox.runtime.service.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.throttleByScene
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Components
import com.github.yumelira.yumebox.runtime.service.R
import com.github.yumelira.yumebox.runtime.service.runtime.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.runtime.records.ImportedDao
import com.tencent.mmkv.MMKV
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ServiceNotificationManager(
    private val service: Service,
    private val config: Config,
) {
    data class Config(
        val notificationId: Int,
        val channelId: String,
        val channelName: String,
    )

    private val serviceStore by lazy { ServiceStore() }
    private val settingsStore by lazy { MMKV.mmkvWithID("settings", MMKV.MULTI_PROCESS_MODE) }
    private val notificationManager by lazy { NotificationManagerCompat.from(service) }
    private var lastNotificationFingerprint: String? = null
    private var smoothedTrafficNow: Long = 0L
    private var speedHoldCounter: Int = 0
    private var cachedProfileName: String? = null
    private var cachedProfileUuid: java.util.UUID? = null

    fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                    config.channelId,
                    NotificationManagerCompat.IMPORTANCE_LOW,
                )
                .setName(config.channelName)
                .build()
        )
    }

    fun createInitialNotification(): Notification = buildNotification(buildRunningPresentation())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startTrafficUpdate(scope: CoroutineScope, screenOn: StateFlow<Boolean>, appForeground: StateFlow<Boolean>, queryTrafficNow: () -> Long, queryTrafficTotal: () -> Long): Job =
        scope.launch(Dispatchers.Default) {
            PollingTimers.ticks(PollingTimerSpecs.ServiceTrafficNotification).throttleByScene(screenOn = screenOn, appForeground = appForeground, backgroundIntervalMs = NOTIFICATION_BACKGROUND_INTERVAL_MS, screenOffIntervalMs = NOTIFICATION_SCREEN_OFF_INTERVAL_MS).collect {
                val state =
                    NotificationRenderState(
                        now = runCatching(queryTrafficNow).getOrDefault(0L),
                        total = runCatching(queryTrafficTotal).getOrDefault(0L),
                    )
                val presentation = buildRunningPresentation(state)
                val fingerprint = "${presentation.title}|${presentation.content}|${presentation.subText ?: ""}"
                if (fingerprint != lastNotificationFingerprint) {
                    lastNotificationFingerprint = fingerprint
                    notificationManager.notify(
                        config.notificationId,
                        buildNotification(presentation),
                    )
                }
            }
        }

    private fun buildRunningPresentation(state: NotificationRenderState = currentRenderState()): NotificationPresentation {
        val profileName = resolveProfileName()
        if (!shouldShowTrafficNotification()) {
            return NotificationPresentationFactory.createStatus(
                profileName = profileName,
                status = MLang.Service.Notification.Running,
            )
        }

        val displayNow = smoothTrafficNow(state.now)
        return NotificationPresentationFactory.createRunning(
            profileName = profileName,
            trafficNow = displayNow,
            trafficTotal = state.total,
        )
    }

    private fun currentRenderState(): NotificationRenderState {
        return NotificationRenderState(
            now = 0L,
            total = 0L,
        )
    }

    private fun buildNotification(presentation: NotificationPresentation): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                service,
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

        return NotificationCompat.Builder(service, config.channelId)
            .setContentTitle(presentation.title)
            .setContentText(presentation.content)
            .setSubText(presentation.subText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(presentation.expandedText)
                    .setSummaryText(presentation.subText)
            )
            .setSmallIcon(R.drawable.ic_logo_service)
            .setColor(service.getColor(R.color.color_clash))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun resolveProfileName(): String {
        val active = serviceStore.activeProfile ?: return MLang.Service.Notification.UnknownProfile
        if (active == cachedProfileUuid && cachedProfileName != null) {
            return cachedProfileName!!
        }
        val name = ImportedDao.queryByUUID(active)?.name?.takeIf { it.isNotBlank() }
            ?: MLang.Service.Notification.UnknownProfile
        cachedProfileUuid = active
        cachedProfileName = name
        return name
    }

    private fun shouldShowTrafficNotification(): Boolean {
        val settings = settingsStore
        if (settings.containsKey("showTrafficNotification")) {
            return settings.decodeBool("showTrafficNotification", true)
        }
        return serviceStore.showTrafficNotification
    }

    private fun smoothTrafficNow(rawNow: Long): Long {
        return if (rawNow != 0L) {
            smoothedTrafficNow = rawNow
            speedHoldCounter = SPEED_HOLD_TICKS
            rawNow
        } else if (speedHoldCounter > 0) {
            speedHoldCounter--
            smoothedTrafficNow
        } else {
            smoothedTrafficNow = 0L
            0L
        }
    }

    fun resetSpeedSmoothing() {
        smoothedTrafficNow = 0L
        speedHoldCounter = 0
        lastNotificationFingerprint = null
        cachedProfileName = null
        cachedProfileUuid = null
    }

    companion object {
        private const val SPEED_HOLD_TICKS = 2
        private const val NOTIFICATION_BACKGROUND_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_SCREEN_OFF_INTERVAL_MS = 60_000L
        val VPN_CONFIG =
            Config(
                notificationId = 1001,
                channelId = "clash_vpn_service",
                channelName = "Clash VPN Service",
            )

        val HTTP_CONFIG =
            Config(
                notificationId = 1002,
                channelId = "clash_http_service",
                channelName = "Clash HTTP Service",
            )
    }

        private data class NotificationRenderState(
            val now: Long,
            val total: Long,
        )
}
