package com.github.lmfirefly.flycat.runtime.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.ServiceCompat
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.appContextOrSelf
import com.github.lmfirefly.flycat.core.model.LogMessage
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.util.TrafficPushHub
import com.github.lmfirefly.flycat.runtime.api.constants.Intents
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec
import com.github.lmfirefly.flycat.runtime.service.config.CoreRuntimeConfig
import com.github.lmfirefly.flycat.runtime.service.notification.ServiceNotificationManager
import com.github.lmfirefly.flycat.runtime.service.session.RuntimeHost
import com.github.lmfirefly.flycat.runtime.service.session.SessionRuntime
import com.github.lmfirefly.flycat.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.github.lmfirefly.flycat.runtime.service.session.transport.RuntimeTransport
import com.github.lmfirefly.flycat.runtime.service.util.Log
import com.github.lmfirefly.flycat.runtime.service.util.sendClashStarted
import com.github.lmfirefly.flycat.runtime.service.util.sendClashStopped
import com.github.lmfirefly.flycat.runtime.service.util.sendProfileLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal class RuntimeForegroundController(
    private val service: Service,
    private val scope: CoroutineScope,
    private val mode: RunMode,
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
                    // 立即在主线程移除前台通知。
                    // 下方的IO协程可能在到达 stopForegroundService() 之前因作用域取消 (cancelAndJoinBlocking) 而被取消。
                    stopForegroundService()
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
                    override val mode: RunMode = this@RuntimeForegroundController.mode

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
                screenOn = powerController.screenOn,
            )
        }
        // 启动器在每个启动请求前无条件标记"启动中"；对于针对已运行会话的可重入命令，这是唯一能将持久化阶段翻转回去的位置，否则它将卡在"启动中"状态。
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
        // 同步重置流量数据以在重新启动服务前清除陈旧值。由于下方异步的 runtime.destroy() 可能在替代会话抢先获取核心所有权时被跳过，因此我们不能仅依赖 teardownCore() 方法。
        TrafficPushHub.reset()
        if (isRuntimeInitialized) {
            runtime.requestStop(reason)
            // 始终调用 destroy() 以保证核心清理（通过 teardownCore 执行 Clash.reset）。
            // 当 stopRequested 为 true 时，广播处理程序已经启动了一个 IO 协程来执行 runtime.stop()，但作用域取消（TunService 中的 cancelAndJoinBlocking）可能会在 teardownCore 完成前中断它。
            // 我们在作用域外的专用线程上运行 destroy，因此它不受作用域取消的影响。
            // destroy() 是幂等的——内部锁对并发调用进行串行化处理。
            Thread({ runBlocking { withTimeoutOrNull(3000L) { runtime.destroy() } } },
                "runtime-destroy-guard").apply { isDaemon = true }.start()
        }
        // 保护：启动器可能已经标记了替换运行时的阶段Starting；阶段存储仅持有单个模式槽位。
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
        // 双保险：STOP_FOREGROUND_REMOVE 可能无法在所有Android版本上可靠取消ONGOING_EVENT通知。请显式取消。
        runCatching {
            val nm = service.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            nm.cancel(notificationConfig.notificationId)
        }
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
