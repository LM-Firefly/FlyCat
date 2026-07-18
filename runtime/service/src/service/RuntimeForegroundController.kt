package com.github.yumelira.yumebox.runtime.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.ServiceCompat
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.service.common.log.Log
import com.github.yumelira.yumebox.runtime.service.common.util.CoreRuntimeConfig
import com.github.yumelira.yumebox.runtime.service.notification.ServiceNotificationManager
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeHost
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeTransport
import com.github.yumelira.yumebox.runtime.service.runtime.session.SessionRuntime
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStarted
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStopped
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendProfileLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

internal class RuntimeForegroundController(
    private val service: Service,
    private val scope: CoroutineScope,
    private val mode: ProxyMode,
    private val logScope: RuntimeStartupLogStore.Scope,
    private val notificationConfig: ServiceNotificationManager.Config,
    private val transportFactory: () -> RuntimeTransport,
    private val specFactory: suspend (Context) -> RuntimeSpec,
    private val logTag: String,
) {
    private val powerController by lazy { ServicePowerController(service) }
    private val notificationManager = ServiceNotificationManager(service, notificationConfig)
    private val startupLogStore = RuntimeStartupLogStore(service, logScope)

    private var notificationJob: Job? = null
    private var reloadJob: Job? = null
    private var reason: String? = null
    private var stopRequested = false
    private lateinit var runtime: SessionRuntime
    private val isRuntimeInitialized: Boolean
        get() = ::runtime.isInitialized

    private val runtimeEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val pkg = service.packageName
            when (action) {
                Intents.actionProfileChanged(pkg),
                Intents.actionOverrideChanged(pkg) -> scheduleReload()
                Intents.actionClashRequestStop(pkg) -> {
                    if (stopRequested) return
                    reason = intent.getStringExtra(Intents.EXTRA_STOP_REASON)
                    stopRequested = true
                    reloadJob?.cancel()
                    reloadJob = null
                    StatusProvider.markRuntimeStopping(mode)
                    notificationJob?.cancel()
                    notificationJob = null
                    scope.launch(Dispatchers.IO) {
                        if (isRuntimeInitialized) {
                            val stopResult = runCatching { runtime.stop(reason) }.getOrNull()
                            if (stopResult?.success == false) {
                                val error = stopResult.error ?: "$logTag runtime stop failed"
                                this@RuntimeForegroundController.reason = error
                                StatusProvider.markRuntimeFailed(mode)
                                service.sendClashStopped(error)
                                Log.e("$logTag runtime stop failed: $error")
                            }
                        }
                        stopForegroundService()
                        service.stopSelf()
                    }
                }
            }
        }
    }

    fun onCreate() {
        powerController.start()
        runCatching {
            startupLogStore.append("$logTag service: onCreate begin")

            notificationManager.createChannel()
            service.startForeground(
                notificationConfig.notificationId,
                notificationManager.createInitialNotification(),
            )
            startupLogStore.append("$logTag service: startForeground done")

            StatusProvider.clearLegacyStateFiles()
            StatusProvider.markRuntimeStarting(mode)
            CoreRuntimeConfig.applyCustomUserAgentIfPresent(service)

            runtime = SessionRuntime(
                screenOn = powerController.screenOn,
                powerController = powerController,
                host = object : RuntimeHost {
                    override val context: Context = service
                    override val mode: ProxyMode = this@RuntimeForegroundController.mode

                    override fun onStarting(spec: RuntimeSpec) = Unit

                    override fun onStarted(spec: RuntimeSpec) {
                        StatusProvider.markRuntimeRunning(this@RuntimeForegroundController.mode)
                        service.sendClashStarted()
                    }

                    override fun onStopped(reason: String?) {
                        this@RuntimeForegroundController.reason = reason
                        StatusProvider.markRuntimeIdle(this@RuntimeForegroundController.mode)
                        service.sendClashStopped(reason)
                    }

                    override fun onProfileLoaded(profileUuid: String) {
                        service.sendProfileLoaded(UUID.fromString(profileUuid))
                    }

                    override fun onSnapshotChanged(snapshot: RuntimeSnapshot) = Unit
                    override fun onLogReady(ready: Boolean) = Unit
                    override fun onLogItem(log: LogMessage) = Unit

                    override fun reportFailure(error: String) {
                        reason = error
                        startupLogStore.append("$logTag failed=$error")
                        StatusProvider.markRuntimeFailed(this@RuntimeForegroundController.mode)
                        service.sendClashStopped(error)
                        Log.e("$logTag runtime failed: $error")
                        service.stopSelf()
                    }
                },
                transport = transportFactory(),
                scope = scope,
            )

            registerRuntimeReceiver()
            startupLogStore.append("$logTag service: receiver registered")
            scope.launch(Dispatchers.IO) {
                runCatching {
                    startupLogStore.append("$logTag spec: create begin")
                    val spec = specFactory(service.appContextOrSelf)
                    startupLogStore.append("$logTag spec: create done profile=${spec.profileUuid} overrides=${spec.overrideSpecs.size}")
                    val result = runtime.start(spec)
                    check(result.success) { result.error ?: "$logTag runtime start failed" }
                }.onFailure { error ->
                    reason = error.message ?: "$logTag runtime start failed"
                    startupLogStore.append("$logTag failed=$reason")
                    StatusProvider.markRuntimeFailed(mode)
                    service.stopSelf()
                }
            }
        }.onFailure { error ->
            reason = error.message ?: "$logTag runtime start failed"
            startupLogStore.append("$logTag failed=$reason")
            StatusProvider.markRuntimeFailed(mode)
            service.stopSelf()
        }
    }

    fun onStartCommand() {
        if (notificationJob?.isActive != true) {
            notificationJob = notificationManager.startTrafficUpdate(
                scope = scope,
                trafficNow = runtime.telemetryTrafficNow,
                trafficTotal = runtime.telemetryTrafficTotal,
            )
        }
        // The launcher marks Starting unconditionally before every start request; on a
        // re-entrant command against an already-running session this is the only place
        // that can flip the persisted phase back, otherwise it is stuck at Starting.
        if (isRuntimeInitialized && runtime.snapshot().running) {
            StatusProvider.markRuntimeRunning(mode)
        }
    }

    fun onDestroy() {
        runCatching { service.unregisterReceiver(runtimeEventsReceiver) }
        reloadJob?.cancel()
        reloadJob = null
        notificationJob?.cancel()
        notificationJob = null
        notificationManager.resetSpeedSmoothing()
        stopForegroundService()

        if (isRuntimeInitialized) {
            if (stopRequested) {
                runtime.requestStop(reason)
            } else {
                runtime.requestStop(reason)
                scope.launch(Dispatchers.IO) { runtime.destroy() }
            }
        }

        // Guarded: the launcher may already have marked the phase Starting for the
        // replacement runtime; the phase store holds a single mode slot.
        if (StatusProvider.queryRuntimePhase(mode).isNotIdle) {
            StatusProvider.markRuntimeIdle(mode)
        }
        service.sendClashStopped(reason)
        startupLogStore.append("$logTag destroy")
        Log.i("$logTag destroyed: ${reason ?: "successfully"}")

        powerController.stop()
    }

    fun onTrimMemory() {
        Clash.forceGc()
    }

    fun onVpnRevoked() {
        if (stopRequested) return
        stopRequested = true
        reason = "VPN connection revoked by system"
        reloadJob?.cancel()
        reloadJob = null
        StatusProvider.markRuntimeStopping(mode)
        notificationJob?.cancel()
        notificationJob = null
        scope.launch(Dispatchers.IO) {
            if (isRuntimeInitialized) {
                runCatching { runtime.stop(reason) }
            }
            stopForegroundService()
            service.stopSelf()
        }
    }

    private fun stopForegroundService() {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerRuntimeReceiver() {
        val pkg = service.packageName
        val filter = IntentFilter().apply {
            addAction(Intents.actionProfileChanged(pkg))
            addAction(Intents.actionOverrideChanged(pkg))
            addAction(Intents.actionClashRequestStop(pkg))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(runtimeEventsReceiver, filter, Service.RECEIVER_NOT_EXPORTED)
        } else {
            service.registerReceiver(runtimeEventsReceiver, filter)
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch(Dispatchers.IO) {
            startupLogStore.append("$logTag spec: reload create begin")
            val spec = runCatching {
                specFactory(service.appContextOrSelf)
            }.getOrElse { error ->
                reason = error.message
                startupLogStore.append("$logTag failed=${error.message ?: "$logTag spec refresh failed"}")
                Log.w("$logTag spec refresh failed: ${error.message}")
                return@launch
            }
            startupLogStore.append("$logTag spec: reload create done profile=${spec.profileUuid} overrides=${spec.overrideSpecs.size}")

            val result = runtime.reload(spec)
            if (!result.success) {
                reason = result.error
                startupLogStore.append("$logTag failed=${result.error ?: "$logTag runtime reload failed"}")
                Log.w("$logTag runtime reload failed: ${result.error}")
            }
        }
    }
}
