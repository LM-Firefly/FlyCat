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
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.yumelira.yumebox.core.contract.ServiceBootstrapHolder
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.StartupTaskCoordinator
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.autostart.AutoStartUpdatePolicy
import com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
import com.github.yumelira.yumebox.runtime.service.R
import com.github.yumelira.yumebox.runtime.service.root.RootTunServiceBridge
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeServiceLauncher
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.runtime.util.RuntimeActivationAwaiter
import com.github.yumelira.yumebox.runtime.service.runtime.util.RuntimeActivationResult
import com.github.yumelira.yumebox.runtime.service.runtime.util.RuntimeActivationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class AutoRestartService : Service() {
    companion object {
        private const val TAG = "AutoRestartService"
        private const val NOTIFICATION_ID = 1101
        private const val CHANNEL_ID = "auto_restart_channel"
        const val EXTRA_REASON = "auto_restart_reason"
        const val REASON_BOOT_COMPLETED = "boot_completed"
        const val REASON_PACKAGE_REPLACED = "package_replaced"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bootstrap get() = ServiceBootstrapHolder.reader
    private val profileManager by lazy { ProfileManager(applicationContext) }
    private val foregroundStarted = AtomicBoolean(false)
    private val activationAwaiter = RuntimeActivationAwaiter()

    // Duplicate triggers (boot + replaced racing) are merged: a new onStartCommand
    // cancels the in-flight coroutine so only the latest reason is acted upon.
    private var autoStartJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!ensureForegroundStarted()) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ensureForegroundStarted()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        bootstrap.markAutoStartStarted()

        autoStartJob?.cancel()
        autoStartJob = serviceScope.launch {
            val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "unknown" }
            try {
                runCatching { checkAndAutoStart(reason) }
                    .onFailure { error ->
                        Timber.tag(TAG).e(error, "Auto start failed: ${error.message}")
                    }
            } finally {
                bootstrap.clearAutoStart()
                ServiceCompat.stopForeground(
                    this@AutoRestartService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun ensureForegroundStarted(): Boolean {
        if (foregroundStarted.get()) return true
        if (!foregroundStarted.compareAndSet(false, true)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        createNotificationChannel()
        val notification = createNotification()
        val started =
            runCatching {
                    val foregroundFlags =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        } else {
                            0
                        }
                    startForeground(NOTIFICATION_ID, notification, foregroundFlags)
                }
                .recoverCatching {
                    // Some vendors reject special-use type at runtime; fallback to the default call.
                    startForeground(NOTIFICATION_ID, notification)
                }
                .onFailure { error ->
                    Timber.tag(TAG).e(error, "Failed to start foreground for auto-restart service")
                }
                .isSuccess
        if (!started) {
            foregroundStarted.set(false)
        }
        return started
    }

    private suspend fun checkAndAutoStart(reason: String) {
        if (!bootstrap.automaticRestart) return
        if (bootstrap.isRemoteControllerActive()) {
            Timber.tag(TAG).i("Skip auto start: remote controller mode active")
            return
        }
        if (AutoStartSessionGate.shouldSkipAutoStart()) {
            Timber.tag(TAG).i("Skip auto start: manual pause gate is active in current session")
            return
        }
        StartupTaskCoordinator.awaitWarmup()
        val skipUpdateOnPostUpdateColdStart = bootstrap.consumePostUpdateColdStartPending()

        val activeProfile = profileManager.queryActive()
        if (activeProfile == null) {
            Timber.tag(TAG).w("No active profile for auto start")
            return
        }

        tryUpdateActiveProfileOnStart(
            activeProfile = activeProfile,
            reason = reason,
            skipForPostUpdateColdStart = skipUpdateOnPostUpdateColdStart,
        )

        val startupSource =
            when (reason) {
                REASON_BOOT_COMPLETED -> ProxyServiceContracts.SOURCE_AUTO_RESTART_BOOT
                REASON_PACKAGE_REPLACED -> ProxyServiceContracts.SOURCE_AUTO_RESTART_REPLACED
                else -> ProxyServiceContracts.SOURCE_AUTO_RESTART
            }
        when (bootstrap.proxyMode) {
            ProxyMode.Tun -> {
                if (VpnService.prepare(this) != null) {
                    Timber.tag(TAG).i("Skip auto start: VPN permission is missing for Tun mode")
                    return
                }
                RuntimeServiceLauncher.start(this, ProxyMode.Tun, startupSource)
            }
            ProxyMode.RootTun -> {
                val result = RootTunServiceBridge.start(this)
                if (!result.success) {
                    error(result.error ?: "RootTun auto start failed")
                }
            }
            ProxyMode.Http -> {
                RuntimeServiceLauncher.start(this, ProxyMode.Http, startupSource)
            }
        }

        val activationResult =
            runCatching { awaitRuntimeActivation(bootstrap.proxyMode) }
                .getOrElse { error ->
                    cleanupIncompleteRuntime(bootstrap.proxyMode)
                    throw error
                }
        val logScope = RuntimeStartupLogStore.scopeForMode(bootstrap.proxyMode)
        val startupLogStore = RuntimeStartupLogStore(this, logScope)
        when (activationResult) {
            is RuntimeActivationResult.Running -> {
                startupLogStore.append("${logScope.tag} auto-start: running reason=$reason")
                Timber.tag(TAG)
                    .i(
                        "Auto start active: reason=$reason profile=${activeProfile.name}, " +
                            "mode=${bootstrap.proxyMode}"
                    )
            }
            is RuntimeActivationResult.Failed -> {
                cleanupIncompleteRuntime(bootstrap.proxyMode)
                val message = activationResult.error ?: "runtime entered Failed"
                startupLogStore.append(
                    "${logScope.tag} auto-start: failed reason=$reason error=$message"
                )
                error(message)
            }
            is RuntimeActivationResult.TimedOut -> {
                cleanupIncompleteRuntime(bootstrap.proxyMode)
                val message =
                    "runtime activation timed out in ${activationResult.lastState.phase}" +
                        activationResult.lastState.error?.let { ": $it" }.orEmpty()
                startupLogStore.append(
                    "${logScope.tag} auto-start: timeout reason=$reason " +
                        "phase=${activationResult.lastState.phase} " +
                        "error=${activationResult.lastState.error}"
                )
                error(message)
            }
        }
    }

    private suspend fun awaitRuntimeActivation(mode: ProxyMode): RuntimeActivationResult =
        activationAwaiter.await(mode) {
            when (mode) {
                ProxyMode.RootTun ->
                    RootTunServiceBridge.queryStatus(this)
                        .also(RootTunStatusFlow::update)
                        .let { status ->
                            RuntimeActivationState(
                                phase = status.state,
                                error = status.lastError,
                            )
                        }
                ProxyMode.Tun,
                ProxyMode.Http ->
                    RuntimeActivationState(
                        phase = StatusProvider.queryRuntimePhase(mode),
                    )
            }
        }

    private suspend fun cleanupIncompleteRuntime(mode: ProxyMode) {
        when (mode) {
            ProxyMode.RootTun -> {
                runCatching { RootTunServiceBridge.stop(this) }
                RootTunService.stop(this)
                StatusProvider.markRuntimeIdle(ProxyMode.RootTun)
            }
            ProxyMode.Tun,
            ProxyMode.Http -> RuntimeServiceLauncher.stop(this, mode)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryUpdateActiveProfileOnStart(
        activeProfile: Profile,
        reason: String,
        skipForPostUpdateColdStart: Boolean,
    ) {
        when (
            AutoStartUpdatePolicy.decide(
                autoUpdateEnabled = bootstrap.autoUpdateCurrentProfileOnStart,
                activeProfile = activeProfile,
                skipForPostUpdateColdStart = skipForPostUpdateColdStart,
                startupReason = reason,
                coldStartReasons = setOf(REASON_BOOT_COMPLETED, REASON_PACKAGE_REPLACED),
            )
        ) {
            AutoStartUpdatePolicy.Decision.Proceed -> Unit
            AutoStartUpdatePolicy.Decision.AutoUpdateDisabled -> return
            AutoStartUpdatePolicy.Decision.SkipPostUpdateColdStart -> {
                Timber.tag(TAG).d("Skip auto update: post-update cold-start marker consumed")
                return
            }
            AutoStartUpdatePolicy.Decision.SkipColdStartReason -> {
                Timber.tag(TAG).d("Skip auto update on cold-start reason=$reason")
                return
            }
            AutoStartUpdatePolicy.Decision.UnsupportedProfileType -> {
                Timber.tag(TAG)
                    .d("Skip boot update: unsupported profile type=${activeProfile.type}")
                return
            }
            AutoStartUpdatePolicy.Decision.NoActiveProfile -> return
        }

        try {
            profileManager.update(activeProfile.uuid, null)
            Timber.tag(TAG).i("Boot update ok: ${activeProfile.uuid}")
        } catch (error: Exception) {
            // fault barrier: best-effort boot update goes through the core fetch bridge; any
            // failure must not block the auto restart itself.
            Timber.tag(TAG).w(error, "Boot update failed")
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    "Auto Restart Service",
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply {
                    description = "Used to restart proxy service automatically"
                    setShowBadge(false)
                }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FlyCat")
            .setContentText("Checking auto-start...")
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        bootstrap.clearAutoStart()
        serviceScope.cancel()
        super.onDestroy()
    }
}
