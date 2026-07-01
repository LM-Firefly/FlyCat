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

package com.github.yumelira.yumebox.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.service.common.log.Log
import com.github.yumelira.yumebox.runtime.service.common.util.CoreRuntimeConfig
import com.github.yumelira.yumebox.runtime.service.notification.ServiceNotificationManager
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeHost
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeTransport
import com.github.yumelira.yumebox.runtime.service.runtime.session.SessionRuntime
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStarted
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStopped
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendProfileLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Shared lifecycle logic for the foreground runtime services ([ClashService] and [TunService]).
 *
 * The two services cannot share a superclass — the TUN path is mandated by Android to extend
 * [android.net.VpnService] while the HTTP path extends a plain [Service] — so the identical
 * onCreate/onStartCommand/onDestroy/receiver/reload behavior is delegated to this controller
 * instead. The hosting service keeps only its required parent class, transport, notification
 * config, startup scope, and any service-specific global init/teardown.
 */
class RuntimeForegroundController(
    private val service: Service,
    private val scope: CoroutineScope,
    private val mode: ProxyMode,
    private val label: String,
    private val notificationConfig: ServiceNotificationManager.Config,
    private val logScope: RuntimeStartupLogStore.Scope,
    private val createTransport: () -> RuntimeTransport,
    private val createSpec: () -> RuntimeSpec,
) {
    private val tag = logScope.tag

    private var reason: String? = null
    private val notificationManager by lazy {
        ServiceNotificationManager(service, notificationConfig)
    }
    private val startupLogStore by lazy {
        RuntimeStartupLogStore(service, logScope)
    }
    private var notificationJob: Job? = null
    private var runtime: SessionRuntime? = null
    private var reloadJob: Job? = null

    private val runtimeEventsReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action ?: return) {
                    Intents.actionProfileChanged(service.packageName),
                    Intents.actionOverrideChanged(service.packageName) -> scheduleReload()

                    Intents.actionClashRequestStop(service.packageName) -> {
                        reason = intent.getStringExtra(Intents.EXTRA_STOP_REASON)
                        reloadJob?.cancel()
                        reloadJob = null
                        StatusProvider.markRuntimeStopping(mode)
                        runtime?.requestStop(reason)
                        service.stopSelf()
                    }
                }
            }
        }

    fun onCreate() {
        runCatching {
                startupLogStore.append("$tag service: onCreate begin")

                notificationManager.createChannel()
                service.startForeground(
                    notificationConfig.notificationId,
                    notificationManager.createInitialNotification(),
                )
                startupLogStore.append("$tag service: startForeground done")

                StatusProvider.clearLegacyStateFiles()
                StatusProvider.markRuntimeStarting(mode)
                CoreRuntimeConfig.applyCustomUserAgentIfPresent(service)

                runtime =
                    SessionRuntime(
                        host =
                            object : RuntimeHost {
                                override val context = service
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
                                    startupLogStore.append("$tag failed=$error")
                                    StatusProvider.markRuntimeFailed(this@RuntimeForegroundController.mode)
                                    service.sendClashStopped(error)
                                    Log.e("$label runtime failed: $error")
                                    service.stopSelf()
                                }
                            },
                        transport = createTransport(),
                        scope = scope,
                    )

                registerRuntimeReceiver()
                startupLogStore.append("$tag service: receiver registered")
                scope.launch {
                    runCatching {
                            startupLogStore.append("$tag spec: create begin")
                            val spec = createSpec()
                            startupLogStore.append(
                                "$tag spec: create done profile=${spec.profileUuid} overrides=${spec.overrideSpecs.size}"
                            )
                            val result = runtime!!.start(spec)
                            check(result.success) { result.error ?: "${label.lowercase()} runtime start failed" }
                        }
                        .onFailure { error ->
                            reason = error.message ?: "${label.lowercase()} runtime start failed"
                            startupLogStore.append("$tag failed=$reason")
                            StatusProvider.markRuntimeFailed(mode)
                            service.sendClashStopped(reason)
                            service.stopSelf()
                        }
                }
            }
            .onFailure { error ->
                reason = error.message ?: "${label.lowercase()} runtime start failed"
                startupLogStore.append("$tag failed=$reason")
                StatusProvider.markRuntimeFailed(mode)
                service.sendClashStopped(reason)
                service.stopSelf()
            }
    }

    fun onStartCommand(): Int {
        if (notificationJob?.isActive != true) {
            val always = MutableStateFlow(true)
            notificationJob = notificationManager.startTrafficUpdate(scope, always, always)
        }
        return Service.START_STICKY
    }

    fun onDestroy() {
        runCatching { service.unregisterReceiver(runtimeEventsReceiver) }
        reloadJob?.cancel()
        reloadJob = null
        notificationJob?.cancel()
        notificationJob = null

        runtime?.let {
            it.requestStop(reason)
            it.destroy()
        }

        StatusProvider.markRuntimeIdle(mode)
        service.sendClashStopped(reason)
        startupLogStore.append("$tag destroy")
        Log.i("${service.javaClass.simpleName} destroyed: ${reason ?: "successfully"}")
    }

    fun onTrimMemory() {
        com.github.yumelira.yumebox.core.Clash.forceGc()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerRuntimeReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(Intents.actionProfileChanged(service.packageName))
                addAction(Intents.actionOverrideChanged(service.packageName))
                addAction(Intents.actionClashRequestStop(service.packageName))
            }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(runtimeEventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            service.registerReceiver(runtimeEventsReceiver, filter)
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            startupLogStore.append("$tag spec: reload create begin")
            val spec =
                runCatching { createSpec() }
                    .getOrElse { error ->
                        reason = error.message
                        startupLogStore.append(
                            "$tag failed=${error.message ?: "${label.lowercase()} runtime spec refresh failed"}"
                        )
                        Log.w("$label runtime spec refresh failed: ${error.message}")
                        return@launch
                    }
            startupLogStore.append(
                "$tag spec: reload create done profile=${spec.profileUuid} overrides=${spec.overrideSpecs.size}"
            )

            val result = runtime!!.reload(spec)
            if (!result.success) {
                reason = result.error
                startupLogStore.append(
                    "$tag failed=${result.error ?: "${label.lowercase()} runtime reload failed"}"
                )
                Log.w("$label runtime reload failed: ${result.error}")
            }
        }
    }
}
