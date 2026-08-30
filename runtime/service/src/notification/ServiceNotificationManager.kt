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

package com.github.lmfirefly.flycat.runtime.service.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.runtime.api.constants.Components
import com.github.lmfirefly.flycat.runtime.service.R
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.records.ImportedDao
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.transformLatest
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
    private var lastNotifyTime: Long = 0L
    private var smoothedTrafficNow: Long = 0L
    private var speedHoldCounter: Int = 0
    private var cachedProfileName: String? = null
    private var cachedProfileUuid: java.util.UUID? = null

    fun createChannel() {
        legacyChannelIds.forEach(notificationManager::deleteNotificationChannel)
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
    fun startTrafficUpdate(scope: CoroutineScope, trafficNow: StateFlow<Long>, trafficTotal: StateFlow<Long>, screenOn: StateFlow<Boolean>): Job =
        scope.launch(Dispatchers.Default) {
            screenOn.transformLatest { isOn ->
                if (isOn) {
                    combine(trafficNow, trafficTotal) { now, total ->
                        NotificationRenderState(now = now, total = total)
                    }
                    .distinctUntilChanged()
                    .collect { state ->
                        // 节流通知以避免过多的IPC开销。
                        // 我们采用手动时间检查，以确保首次和末次更新不会因采样而被遗漏。
                        val nowTime = System.currentTimeMillis()
                        if (nowTime - lastNotifyTime >= MIN_NOTIFY_INTERVAL_MS || state.now == 0L) {
                            emit(state)
                        }
                    }
                }
            }.collect { state ->
                val presentation = buildRunningPresentation(state)
                val fingerprint = "${presentation.title}|${presentation.content}|${presentation.subText ?: ""}"
                if (fingerprint != lastNotificationFingerprint) {
                    lastNotificationFingerprint = fingerprint
                    lastNotifyTime = System.currentTimeMillis()
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
                status = FlyTxt.Service.Notification.Running,
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
            .setColor(service.getColor(R.color.color_flycat))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun resolveProfileName(): String {
        val active = serviceStore.activeProfile ?: return FlyTxt.Service.Notification.UnknownProfile
        if (active == cachedProfileUuid && cachedProfileName != null) {
            return cachedProfileName!!
        }
        val name = ImportedDao.queryByUUID(active)?.name?.takeIf { it.isNotBlank() }
            ?: FlyTxt.Service.Notification.UnknownProfile
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
        lastNotifyTime = 0L
        cachedProfileName = null
        cachedProfileUuid = null
    }

    companion object {
        private const val SPEED_HOLD_TICKS = 2
        private const val MIN_NOTIFY_INTERVAL_MS = 2000L
        private val legacyChannelIds = listOf("clash_vpn_service", "clash_http_service")
        val vpnConfig =
            Config(
                notificationId = 1001,
                channelId = "flycat_vpn_service",
                channelName = "FlyCat VPN Service",
            )

        val httpConfig =
            Config(
                notificationId = 1002,
                channelId = "flycat_http_service",
                channelName = "FlyCat HTTP Service",
            )
    }

        private data class NotificationRenderState(
            val now: Long,
            val total: Long,
        )
}
