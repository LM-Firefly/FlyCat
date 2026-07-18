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

package com.github.lmfirefly.flycat.runtime.service.android

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
import com.github.lmfirefly.flycat.core.contract.ServiceBootstrapHolder
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.core.util.coroutine.StartupTaskCoordinator
import com.github.lmfirefly.flycat.runtime.api.contract.AutoStartUpdatePolicy
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyServiceContracts
import com.github.lmfirefly.flycat.runtime.api.root.RootTunStatusFlow
import com.github.lmfirefly.flycat.runtime.service.R
import com.github.lmfirefly.flycat.runtime.service.StatusProvider
import com.github.lmfirefly.flycat.runtime.service.profile.ProfileManager
import com.github.lmfirefly.flycat.runtime.service.root.EbpfBridgeProcess
import com.github.lmfirefly.flycat.runtime.service.root.RootTunServiceBridge
import com.github.lmfirefly.flycat.runtime.service.session.RuntimeServiceLauncher
import com.github.lmfirefly.flycat.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.github.lmfirefly.flycat.runtime.service.util.RuntimeActivationAwaiter
import com.github.lmfirefly.flycat.runtime.service.util.RuntimeActivationResult
import com.github.lmfirefly.flycat.runtime.service.util.RuntimeActivationState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

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

        // Clean up orphaned eBPF bridges after APK replacement
        if (reason == REASON_PACKAGE_REPLACED) {
            runCatching { EbpfBridgeProcess.cleanupOrphanedBridges(this) }
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
        when (bootstrap.runMode) {
            RunMode.VpnService -> {
                if (VpnService.prepare(this) != null) {
                    Timber.tag(TAG).i("Skip auto start: VPN permission is missing for Tun mode")
                    return
                }
                RuntimeServiceLauncher.start(this, RunMode.VpnService, startupSource)
            }
            RunMode.Tun, RunMode.Ebpf -> {
                val result = RootTunServiceBridge.start(this)
                if (!result.success) {
                    error(result.error ?: "RootTun/eBPF auto start failed")
                }
            }
        }

        val activationResult =
            runCatching { awaitRuntimeActivation(bootstrap.runMode) }
                .getOrElse { error ->
                    cleanupIncompleteRuntime(bootstrap.runMode)
                    throw error
                }
        val logScope = RuntimeStartupLogStore.scopeForMode(bootstrap.runMode)
        val startupLogStore = RuntimeStartupLogStore(this, logScope)
        when (activationResult) {
            is RuntimeActivationResult.Running -> {
                startupLogStore.append("${logScope.tag} auto-start: running reason=$reason")
                Timber.tag(TAG)
                    .i(
                        "Auto start active: reason=$reason profile=${activeProfile.name}, " +
                            "mode=${bootstrap.runMode}"
                    )
            }
            is RuntimeActivationResult.Failed -> {
                cleanupIncompleteRuntime(bootstrap.runMode)
                val message = activationResult.error ?: "runtime entered Failed"
                startupLogStore.append(
                    "${logScope.tag} auto-start: failed reason=$reason error=$message"
                )
                error(message)
            }
            is RuntimeActivationResult.TimedOut -> {
                val timeoutMessage =
                    "runtime activation timed out in ${activationResult.lastState.phase}" +
                        activationResult.lastState.error?.let { ": $it" }.orEmpty()
                startupLogStore.append(
                    "${logScope.tag} auto-start: timeout reason=$reason " +
                        "phase=${activationResult.lastState.phase} " +
                        "error=${activationResult.lastState.error}"
                )

                // Vpn startup may outlive the activation await window. Do not force-stop the
                // runtime on timeout; otherwise we can cancel a still-progressing session and
                // surface a misleading perpetual "connecting" loop.
                if (bootstrap.runMode == RunMode.VpnService) {
                    Timber.tag(TAG).w("Auto start timeout ignored for Vpn: $timeoutMessage")
                    return
                }

                cleanupIncompleteRuntime(bootstrap.runMode)
                error(timeoutMessage)
            }
        }
    }

    private suspend fun awaitRuntimeActivation(mode: RunMode): RuntimeActivationResult =
        activationAwaiter.await(mode) {
            when (mode) {
                RunMode.Tun, RunMode.Ebpf ->
                    RootTunServiceBridge.queryStatus(this)
                        .also(RootTunStatusFlow::update)
                        .let { status ->
                            RuntimeActivationState(
                                phase = status.state,
                                error = status.lastError,
                            )
                        }
                RunMode.VpnService ->
                    RuntimeActivationState(
                        phase = StatusProvider.queryRuntimePhase(mode),
                    )
            }
        }

    private suspend fun cleanupIncompleteRuntime(mode: RunMode) {
        when (mode) {
            RunMode.Tun, RunMode.Ebpf -> {
                runCatching { RootTunServiceBridge.stop(this) }
                RootTunService.stop(this)
                StatusProvider.markRuntimeIdle(mode)
            }
            RunMode.VpnService -> {
                val source = StatusProvider.queryRuntimeRequestSource()
                val autoSources =
                    setOf(
                        ProxyServiceContracts.SOURCE_AUTO_RESTART,
                        ProxyServiceContracts.SOURCE_AUTO_RESTART_BOOT,
                        ProxyServiceContracts.SOURCE_AUTO_RESTART_REPLACED,
                    )
                if (source !in autoSources) {
                    Timber.tag(TAG)
                        .i(
                            "Skip cleanupIncompleteRuntime for Vpn: request source already switched to $source"
                        )
                    return
                }
                RuntimeServiceLauncher.stop(this, mode)
            }
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
        profileManager.close()
        serviceScope.cancel()
        super.onDestroy()
    }
}
