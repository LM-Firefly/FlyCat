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
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.contract.ServiceBootstrapHolder
import com.github.lmfirefly.flycat.core.model.profile.Imported
import com.github.lmfirefly.flycat.core.model.proxy.ProxySort
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.runtime.api.constants.Components
import com.github.lmfirefly.flycat.runtime.service.R
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.records.ImportedDao
import com.github.lmfirefly.flycat.runtime.service.shizuku.ShizukuManager
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var lastNotifyTime: Long = 0L
    private var smoothedTrafficNow: Long = 0L
    private var speedHoldCounter: Int = 0
    // 一旦释放，流量更新协程绝不能再重发通知——否则服务停止后迟到的刷新会复活常驻通知。
    @Volatile private var released = false
    // 缓存终端节点，避免每次流量刷新都触发一次核心 IPC 列出全部代理组。
    private var currentNode: String? = null
    private var currentNodeUpdatedAt = 0L
    private var cachedProfile: Imported? = null
    private var cachedProfileUuid: java.util.UUID? = null
    private var cachedProfileAt = 0L
    private var islandShown = false
    private var bypassAcquired = false
    private var lastFrame: NoticeFrame? = null

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

    // 初始通知支撑 onCreate 中的 startForeground：该调用前任何抛出都会触发前台服务契约崩溃，因此此路径不读 MMKV/DAO，富化内容随后由流量更新协程提供。
    fun createInitialNotification(): Notification = buildNotification(
        NotificationPresentationFactory.createStatus(
            profileName = service.applicationInfo.loadLabel(service.packageManager).toString(),
            status = FlyTxt.Service.Notification.Running,
        )
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startTrafficUpdate(scope: CoroutineScope, trafficNow: StateFlow<Long>, trafficTotal: StateFlow<Long>, screenOn: StateFlow<Boolean>): Job =
        scope.launch(Dispatchers.Default) {
            try {
                screenOn.transformLatest { isOn ->
                    if (isOn) {
                        combine(trafficNow, trafficTotal) { now, total -> NotificationRenderState(now = now, total = total) }
                            .distinctUntilChanged()
                            .collect { state ->
                                // 节流通知以避免过多的IPC开销。
                                // 我们采用手动时间检查，以确保首次和末次更新不会因采样而被遗漏。
                                val nowTime = System.currentTimeMillis()
                                if (nowTime - lastNotifyTime >= minNotifyIntervalMs() || state.now == 0L) {
                                    emit(state)
                                }
                            }
                    }
                }.collect { state ->
                    refreshRunningNotification(smoothTrafficNow(state.now), state.total)
                }
            } finally {
                // 取消也必须恢复防火墙。被取消的 withLock 会立即返回，故置于 NonCancellable 中。
                withContext(NonCancellable) { releaseBypass() }
            }
        }

    private sealed interface NoticeFrame {
        data class Plain(val presentation: NotificationPresentation) : NoticeFrame
        data class Island(val running: NotificationPresentation.Running) : NoticeFrame
    }

    private suspend fun refreshRunningNotification(trafficNow: Long, trafficTotal: Long) {
        if (released) return
        val frame = loadFrame(trafficNow, trafficTotal)
        if (released) return
        when (frame) {
            is NoticeFrame.Island -> postIsland(frame)
            is NoticeFrame.Plain -> postPlain(frame)
        }
    }

    private fun loadFrame(trafficNow: Long, trafficTotal: Long): NoticeFrame {
        val presentation = buildRunningPresentation(trafficNow, trafficTotal)
        return if (isIslandEnabled() && presentation is NotificationPresentation.Running) {
            NoticeFrame.Island(presentation)
        } else {
            NoticeFrame.Plain(presentation)
        }
    }

    private suspend fun postIsland(frame: NoticeFrame.Island) {
        val held = acquireBypass()
        if (released) return
        // 仅首帧展开一次，且必须在 bypass 真正持有时；获取失败仍投递一次文本，下一 tick 会重试 bypass 而不重播展开动画。
        val promote = held && !islandShown
        // 内容未变则不重发，避免无意义的 IPC；data class 相等覆盖全部岛屿字段。
        if (!promote && frame == lastFrame) return
        deliver(islandNotification(frame.running, promote))
        if (promote) delay(ISLAND_SETTLE_DELAY_MS)
        if (released) return
        if (held) islandShown = true
        lastFrame = frame
    }

    private suspend fun postPlain(frame: NoticeFrame.Plain) {
        if (releaseBypass()) islandShown = false
        if (frame == lastFrame) return
        deliver(buildNotification(frame.presentation))
        lastFrame = frame
    }

    private suspend fun acquireBypass(): Boolean {
        if (bypassAcquired) return true
        if (!ShizukuManager.acquireXmsfBypass(service)) return false
        bypassAcquired = true
        return true
    }

    private suspend fun releaseBypass(): Boolean {
        if (!bypassAcquired) return true
        if (!ShizukuManager.releaseXmsfBypass(service)) return false
        bypassAcquired = false
        return true
    }

    private fun islandNotification(running: NotificationPresentation.Running, promote: Boolean): Notification {
        val notification = buildNotification(running)
        HyperIsland.applyExtras(service, notification, running, R.drawable.ic_logo_service, promote)
        return notification
    }

    private fun deliver(notification: Notification) {
        // 服务可能在（较慢的）核心查询期间停止；此刻重发会复活通知。
        if (!released) {
            lastNotifyTime = System.currentTimeMillis()
            service.startForeground(config.notificationId, notification)
        }
    }

    private fun buildRunningPresentation(trafficNow: Long, trafficTotal: Long): NotificationPresentation {
        // 读取订阅会反序列化整个存储列表，因此只解析一次。
        val profile = resolveProfile()
        val profileName =
            profile?.name?.takeIf { it.isNotBlank() }
                ?: FlyTxt.Service.Notification.UnknownProfile
        if (!shouldShowTrafficNotification()) {
            return NotificationPresentationFactory.createStatus(
                profileName = profileName,
                status = FlyTxt.Service.Notification.Running,
            )
        }
        return NotificationPresentationFactory.createRunning(
            profileName = profileName,
            profile = profile,
            currentNode = resolveCurrentNode(),
            trafficNow = trafficNow,
            trafficTotal = trafficTotal,
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(presentation.expandedText).setSummaryText(presentation.subText))
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

    private fun resolveProfile(): Imported? {
        val active = serviceStore.activeProfile ?: return null
        val now = SystemClock.elapsedRealtime()
        if (active == cachedProfileUuid && now - cachedProfileAt < PROFILE_REFRESH_MS) {
            return cachedProfile
        }
        cachedProfileUuid = active
        cachedProfileAt = now
        // 读取订阅会反序列化整个存储列表，故按 uuid + TTL 缓存，避免每次刷新都解析。
        cachedProfile = ImportedDao.queryByUUID(active)
        return cachedProfile
    }
    private fun resolveCurrentNode(): String? {
        val now = SystemClock.elapsedRealtime()
        if (now - currentNodeUpdatedAt < CURRENT_NODE_REFRESH_MS) {
            return currentNode
        }
        currentNodeUpdatedAt = now
        runCatching {
            currentNode = NotificationPresentationFactory.resolveNodeName(
                queryGroups = { names -> runCatching { Clash.queryGroupsBatch(names, ProxySort.Default) }.getOrDefault(emptyList()) },
                queryGroupNames = { runCatching { Clash.queryGroupNames(false) }.getOrDefault(emptyList()) },
            )
        }
        return currentNode
    }

    private fun shouldShowTrafficNotification(): Boolean {
        val settings = settingsStore
        if (settings.containsKey("showTrafficNotification")) {
            return settings.decodeBool("showTrafficNotification", true)
        }
        return serviceStore.showTrafficNotification
    }

    private fun isSuperIslandEnabled(): Boolean = settingsStore.decodeBool("superIslandEnabled", true)

    private fun isIslandEnabled(): Boolean = !ServiceBootstrapHolder.reader.isRemoteControllerActive() && isSuperIslandEnabled() && HyperIsland.isSupported()

    // 岛屿帧携带完整参数载荷且首帧持有 XMSF bypass，采用更长的节流间隔以降低系统侧开销。
    private fun minNotifyIntervalMs(): Long = if (isIslandEnabled()) ISLAND_MIN_NOTIFY_INTERVAL_MS else MIN_NOTIFY_INTERVAL_MS

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
        lastFrame = null
        lastNotifyTime = 0L
    }

    /** 停止更新并防止迟到的流量刷新复活常驻通知。调用方取消流量任务，由该任务的 finally 释放 XMSF bypass。 */
    fun release() {
        released = true
        islandShown = false
        lastFrame = null
    }

    companion object {
        private const val SPEED_HOLD_TICKS = 2
        private const val MIN_NOTIFY_INTERVAL_MS = 2000L
        private const val ISLAND_MIN_NOTIFY_INTERVAL_MS = 5000L
        /** 首帧 startForeground 之后再多保留片刻：HyperOS 在通知投递的同时决定是否显示超级岛入口。 */
        private const val ISLAND_SETTLE_DELAY_MS = 100L
        // 解析终端节点需要一次核心 IPC 批量列出代理组，节点仅在用户切换时变化，故缓存放宽。
        private const val CURRENT_NODE_REFRESH_MS = 10_000L
        // 订阅用量信息变化不频繁，缓存避免每次刷新全量反序列化订阅列表。
        private const val PROFILE_REFRESH_MS = 10_000L
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
