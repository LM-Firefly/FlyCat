package com.github.yumelira.yumebox.runtime.client

import android.content.Context
import android.net.Uri
import android.os.IInterface
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimeServiceContract
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimeStatusContract
import com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
import com.github.yumelira.yumebox.runtime.api.service.RuntimeServiceContractRegistry
import com.github.yumelira.yumebox.runtime.api.service.root.RootAccessStatus
import com.github.yumelira.yumebox.runtime.api.service.root.RootAccessSupportContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootPackageQueryContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunForegroundServiceContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreFactoryContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object RuntimeContractResolver {
    private object ReflectionClassNames {
        const val RUNTIME_SERVICE_LAUNCHER = "com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeServiceLauncher"
        const val STATUS_PROVIDER = "com.github.yumelira.yumebox.runtime.service.StatusProvider"
        const val ROOT_TUN_SERVICE = "com.github.yumelira.yumebox.runtime.service.RootTunService"
        const val ROOT_TUN_RUNTIME_RECOVERY = "com.github.yumelira.yumebox.runtime.service.root.RootTunRuntimeRecovery"
        const val ROOT_TUN_STATE_STORE_FACTORY = "com.github.yumelira.yumebox.runtime.service.root.RootTunStateStoreFactory"
    }

    private val warmUpDone = AtomicBoolean(false)
    private val reflectionFallbackAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val reflectionFallbackFailures = ConcurrentHashMap<String, AtomicInteger>()
    private val reflectionFallbackWarned = ConcurrentHashMap.newKeySet<String>()
    private val reflectionFailureWarned = ConcurrentHashMap.newKeySet<String>()

    private object UnavailableLocalRuntimeService : LocalRuntimeServiceContract {
        override fun start(
            context: Context,
            mode: RuntimeTargetMode,
            source: String,
        ) {
            error("Local runtime launcher unavailable")
        }
        override fun stop(context: Context, clashRequestStopAction: String) {
            context.sendBroadcast(
                ProxyServiceContracts.intentSelf(
                    action = clashRequestStopAction,
                    packageName = context.packageName,
                ),
            )
        }
    }

    private object UnavailableRootAccessSupport : RootAccessSupportContract {
        override fun evaluate(context: Context): RootAccessStatus {
            return RootAccessStatus(
                rootAccessGranted = false,
                blockedMessage = "Root access support unavailable",
            )
        }
        override suspend fun evaluateAsync(context: Context): RootAccessStatus {
            return evaluate(context)
        }
        override suspend fun requireRootTunAccess(context: Context): RootAccessStatus {
            return evaluate(context).also { status ->
                check(status.canStartRootTun) { status.rootTunBlockedMessage() }
            }
        }
    }

    private object UnavailableLocalRuntimeStatus : LocalRuntimeStatusContract {
        override val serviceRunning: Boolean
            get() = false
        override fun reconcilePersistedRuntimeState() = Unit
        override fun clearLegacyStateFiles() = Unit
        override fun isRuntimeActive(mode: RuntimeTargetMode): Boolean = false
        override fun queryRuntimePhase(mode: RuntimeTargetMode): LocalRuntimePhase {
            return LocalRuntimePhase.Idle
        }
        override fun queryRuntimeStartedAt(mode: RuntimeTargetMode): Long? = null
        override fun isLocalRuntimeServiceAlive(mode: RuntimeTargetMode): Boolean = false
        override fun markRuntimeIdle(mode: RuntimeTargetMode) = Unit
    }

    private object NoOpRootTunForegroundService : RootTunForegroundServiceContract {
        override fun start(context: Context) = Unit
        override fun stop(context: Context) = Unit
    }

    private object NoOpRootTunRuntimeRecovery : RootTunRuntimeRecoveryContract {
        override fun isBinderAlive(service: IInterface?): Boolean = false
        override fun isBinderConnectionFailure(error: Throwable): Boolean = false
        override fun binderFailureReason(error: Throwable): String {
            return error.message ?: "RootTun IPC disconnected"
        }
        override fun handleBinderGone(context: Context, reason: String?) = Unit
    }

    private object NoOpRootTunStateStore : RootTunStateStoreContract {
        override fun snapshot(): RootTunStatus = RootTunStatus()
        override fun isRunning(): Boolean = false
        override fun updateStatus(status: RootTunStatus) = Unit
        override fun markIdle(error: String?) = Unit
        override fun clear() = Unit
    }

    private object NoOpRootTunStateStoreFactory : RootTunStateStoreFactoryContract {
        override fun create(context: Context): RootTunStateStoreContract = NoOpRootTunStateStore
    }

    private object NoOpRootPackageQuery : RootPackageQueryContract {
        override fun hasRootAccess(): Boolean = false
        override fun queryInstalledPackageNames(): Set<String>? = null
    }

    fun warmUp(context: Context) {
        if (warmUpDone.get()) return
        val alreadyReady = RuntimeServiceContractRegistry.localRuntimeStatus != null &&
            RuntimeServiceContractRegistry.localRuntimeService != null &&
            RuntimeServiceContractRegistry.rootTunForegroundService != null
        if (alreadyReady) {
            warmUpDone.set(true)
            return
        }
        runCatching {
            val uri = Uri.parse("content://${context.packageName}.status")
            context.contentResolver.call(uri, "currentProfile", null, null)
        }.onFailure {
            Timber.d(it, "RuntimeContractResolver warm-up via StatusProvider skipped")
        }
        if (RuntimeServiceContractRegistry.localRuntimeStatus != null) {
            warmUpDone.set(true)
        }
    }

    val localRuntimeService: LocalRuntimeServiceContract by lazy { resolveLocalRuntimeService() }
    val rootAccessSupport: RootAccessSupportContract by lazy { resolveRootAccessSupport() }
    val localRuntimeStatus: LocalRuntimeStatusContract by lazy { resolveLocalRuntimeStatus() }
    val rootTunForegroundService: RootTunForegroundServiceContract by lazy { resolveRootTunForegroundService() }
    val rootTunRuntimeRecovery: RootTunRuntimeRecoveryContract by lazy { resolveRootTunRuntimeRecovery() }
    val rootTunStateStoreFactory: RootTunStateStoreFactoryContract by lazy { resolveRootTunStateStoreFactory() }
    val rootPackageQuery: RootPackageQueryContract by lazy { resolveRootPackageQuery() }

    fun rootTunStateStore(context: Context): RootTunStateStoreContract {
        return rootTunStateStoreFactory.create(context)
    }

    private fun resolveLocalRuntimeService(): LocalRuntimeServiceContract {
        return RuntimeServiceContractRegistry.localRuntimeService
            ?: loadObject(ReflectionClassNames.RUNTIME_SERVICE_LAUNCHER)
            ?: UnavailableLocalRuntimeService
    }

    private fun resolveRootAccessSupport(): RootAccessSupportContract {
        return RuntimeServiceContractRegistry.rootAccessSupport
            ?: UnavailableRootAccessSupport
    }

    private fun resolveLocalRuntimeStatus(): LocalRuntimeStatusContract {
        return RuntimeServiceContractRegistry.localRuntimeStatus
            ?: loadObject(ReflectionClassNames.STATUS_PROVIDER)
            ?: UnavailableLocalRuntimeStatus
    }

    private fun resolveRootTunForegroundService(): RootTunForegroundServiceContract {
        return RuntimeServiceContractRegistry.rootTunForegroundService
            ?: loadObject(ReflectionClassNames.ROOT_TUN_SERVICE)
            ?: NoOpRootTunForegroundService
    }

    private fun resolveRootTunRuntimeRecovery(): RootTunRuntimeRecoveryContract {
        return RuntimeServiceContractRegistry.rootTunRuntimeRecovery
            ?: loadObject(ReflectionClassNames.ROOT_TUN_RUNTIME_RECOVERY)
            ?: NoOpRootTunRuntimeRecovery
    }

    private fun resolveRootTunStateStoreFactory(): RootTunStateStoreFactoryContract {
        return RuntimeServiceContractRegistry.rootTunStateStoreFactory
            ?: loadObject(ReflectionClassNames.ROOT_TUN_STATE_STORE_FACTORY)
            ?: NoOpRootTunStateStoreFactory
    }

    private fun resolveRootPackageQuery(): RootPackageQueryContract {
        return RuntimeServiceContractRegistry.rootPackageQuery
            ?: NoOpRootPackageQuery
    }

    private inline fun <reified T> loadObject(className: String): T? {
        val attempt = reflectionFallbackAttempts
            .computeIfAbsent(className) { AtomicInteger(0) }
            .incrementAndGet()
        if (reflectionFallbackWarned.add(className)) {
            Timber.w("RuntimeContractResolver using reflection fallback for %s (attempt=%d)", className, attempt)
        }
        return runCatching {
            val clazz = Class.forName(className)
            val instance = runCatching { clazz.getField("INSTANCE").get(null) }
                .getOrElse { clazz.getField("Companion").get(null) }
            instance as? T
        }.onFailure {
            val failures = reflectionFallbackFailures
                .computeIfAbsent(className) { AtomicInteger(0) }
                .incrementAndGet()
            if (reflectionFailureWarned.add(className)) {
                Timber.w(
                    it,
                    "RuntimeContractResolver reflection fallback failed for %s (attempt=%d, failures=%d)",
                    className,
                    attempt,
                    failures,
                )
            }
        }.getOrNull()
    }
}

internal class RuntimeBackendRouter(
    private val appContext: Context,
    private val ownerProvider: () -> RuntimeOwner,
    private val runningProvider: () -> Boolean,
) {
    val owner: RuntimeOwner get() = ownerProvider()
    val running: Boolean get() = runningProvider()
    val isRootTun: Boolean get() = ownerProvider() == RuntimeOwner.RootTun
    suspend fun <T> dispatch(
        requireRunning: Boolean = false,
        defaultIfNotRunning: () -> T = { error("Runtime is not running") },
        onRoot: suspend (Context) -> T,
        onRemote: (suspend () -> T)? = null,
        onLocal: suspend () -> T,
    ): T {
        if (requireRunning && !runningProvider()) return defaultIfNotRunning()
        return when (ownerProvider()) {
            RuntimeOwner.RootTun -> onRoot(appContext)
            RuntimeOwner.RemoteController -> {
                ServiceClient.connect(appContext)
                (onRemote ?: onLocal)()
            }
            else -> {
                ServiceClient.connect(appContext)
                onLocal()
            }
        }
    }
    suspend fun ensureLocalConnected() {
        if (ownerProvider() != RuntimeOwner.RootTun) {
            ServiceClient.connect(appContext)
        }
    }
}
