package com.github.lmfirefly.flycat.runtime.client.internal

import android.content.Context
import com.github.lmfirefly.flycat.core.appContextOrSelf
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.api.root.RootAccessStatus
import com.github.lmfirefly.flycat.runtime.api.root.RootTunStatus
import com.github.lmfirefly.flycat.runtime.client.RuntimeContractResolver
import com.github.lmfirefly.flycat.runtime.client.root.RootTunController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class RootTunManager(private val context: Context) {
    private companion object {
        const val ROOT_TUN_BOOTSTRAP_ATTEMPTS = 20
        const val ROOT_TUN_BOOTSTRAP_DELAY_MS = 300L
    }
    private val appContext: Context = context.appContextOrSelf
    private val rootTunStateStore by lazy { RuntimeContractResolver.rootTunStateStore(appContext) }
    private val rootTunBootstrapMutex = Mutex()
    private var rootTunBootstrapJob: Job? = null
    private val _rootTunStatus = MutableStateFlow(RootTunStatus())
    val rootTunStatus: StateFlow<RootTunStatus> = _rootTunStatus.asStateFlow()
    fun applyRootTunStatus(status: RootTunStatus) {
        _rootTunStatus.value = status
    }
    fun isRootSessionActive(): Boolean {
        val status = _rootTunStatus.value
        return status.state.isActive || status.runtimeReady
    }
    suspend fun evaluateRootAccess(): RootAccessStatus {
        return RuntimeContractResolver.rootAccessSupport.evaluateAsync(appContext)
    }
    fun hasRootPackageAccess(): Boolean {
        return RuntimeContractResolver.rootPackageQuery.hasRootAccess()
    }
    fun queryInstalledRootPackageNames(): Set<String>? {
        return RuntimeContractResolver.rootPackageQuery.queryInstalledPackageNames()
    }
    fun resolveObservedRootTunStatus(): RootTunStatus {
        return rootTunStateStore.snapshot()
    }
    suspend fun queryLiveRootTunStatus(): RootTunStatus {
        val shouldProbeRuntime = shouldBootstrapRootTunRuntime() || _rootTunStatus.value.state.isActive
        if (!shouldProbeRuntime) {
            return RootTunStatus()
        }
        return runCatching { RootTunController.queryStatus(appContext) }
            .onSuccess { status -> rootTunStateStore.updateStatus(status) }
            .getOrElse { error ->
                Timber.d(error, "RootTun live status unavailable during runtime reconcile")
                rootTunStateStore.snapshot()
            }
    }
    fun shouldBootstrapRootTunRuntime(
        status: RootTunStatus = rootTunStateStore.snapshot()
    ): Boolean {
        return shouldAttachRootTunForegroundService(status)
    }
    fun shouldAttachRootTunForegroundService(status: RootTunStatus): Boolean {
        return status.state.isActive || status.runtimeReady
    }
    suspend fun currentRootTunStatus(): RootTunStatus {
        return runCatching { RootTunController.queryStatus(appContext) }
            .getOrElse { rootTunStateStore.snapshot() }
    }
    fun ensureRootTunServiceAttached(status: RootTunStatus = rootTunStateStore.snapshot()) {
        if (!shouldAttachRootTunForegroundService(status)) {
            return
        }
        runCatching { RuntimeContractResolver.rootTunForegroundService.start(appContext) }
            .onFailure { error -> Timber.d(error, "Attach RootTun foreground service skipped") }
    }
    fun scheduleRootTunBootstrap(
        scope: CoroutineScope,
        snapshotProvider: () -> RuntimeSnapshot,
        onStatusResolved: (RootTunStatus, RuntimeSnapshot) -> Unit,
        onStartTrafficPolling: () -> Unit,
        onRefreshAll: suspend () -> Unit,
    ) {
        scope.launch {
            rootTunBootstrapMutex.withLock {
                if (rootTunBootstrapJob?.isActive == true) return@withLock
                rootTunBootstrapJob?.cancel()
                rootTunBootstrapJob = scope.launch {
                    repeat(ROOT_TUN_BOOTSTRAP_ATTEMPTS) { attempt ->
                        val persistedStatus = rootTunStateStore.snapshot()
                        if (!shouldAttachRootTunForegroundService(persistedStatus)) {
                            return@launch
                        }
                        val status = runCatching {
                            ensureRootTunServiceAttached(persistedStatus)
                            RootTunController.queryStatus(appContext)
                        }.getOrNull()
                        if (status != null) {
                            rootTunStateStore.updateStatus(status)
                            applyRootTunStatus(status)
                            onStatusResolved(status, snapshotProvider())
                            if (status.state == RuntimePhase.Running) {
                                onStartTrafficPolling()
                                onRefreshAll()
                                return@launch
                            }
                        }
                        if (attempt < ROOT_TUN_BOOTSTRAP_ATTEMPTS - 1) {
                            delay(ROOT_TUN_BOOTSTRAP_DELAY_MS)
                        }
                    }
                    // All attempts exhausted — report failure so UI can show a meaningful state
                    Timber.w("RootTun bootstrap failed after %d attempts", ROOT_TUN_BOOTSTRAP_ATTEMPTS)
                    rootTunStateStore.markIdle(error = "RootTun bootstrap timed out after ${ROOT_TUN_BOOTSTRAP_ATTEMPTS} attempts")
                    applyRootTunStatus(RootTunStatus())
                    onStatusResolved(RootTunStatus(), snapshotProvider())
                }
            }
        }
    }
    fun stopRootTunBootstrap() {
        rootTunBootstrapJob?.cancel()
        rootTunBootstrapJob = null
    }
    suspend fun reconcileRootTunRuntimeState(
        snapshotProvider: () -> RuntimeSnapshot,
        onStartTrafficPolling: () -> Unit,
        onRefreshAll: suspend () -> Unit,
    ) {
        runCatching {
                val persistedStatus = rootTunStateStore.snapshot()
                if (!shouldAttachRootTunForegroundService(persistedStatus)) {
                    return@runCatching
                }
                ensureRootTunServiceAttached(persistedStatus)
                val status = RootTunController.queryStatus(appContext)
                rootTunStateStore.updateStatus(status)
                applyRootTunStatus(status)
                val snapshot = snapshotProvider()
                if (status.state == RuntimePhase.Running) {
                    onStartTrafficPolling()
                    onRefreshAll()
                }
            }
            .onFailure { error -> Timber.d(error, "RootTun bootstrap reconcile skipped") }
    }
    fun clearLegacyRuntimeCaches() {
        val rootStatus = rootTunStateStore.snapshot()
        if (!rootStatus.state.isActive && !rootStatus.runtimeReady) {
            runCatching { rootTunStateStore.clear() }
        }
        applyRootTunStatus(RootTunStatus())
    }
    fun markIdle(reason: String?) {
        val status = rootTunStateStore.snapshot()
        if (status.state.isActive) {
            rootTunStateStore.markIdle(reason ?: status.lastError)
        }
        applyRootTunStatus(rootTunStateStore.snapshot())
    }
}
