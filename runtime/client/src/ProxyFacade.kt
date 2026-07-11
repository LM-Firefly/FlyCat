package com.github.yumelira.yumebox.runtime.client

import android.content.Context
import android.net.VpnService
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.data.ConnectionRepository
import com.github.yumelira.yumebox.core.data.NetworkSettingsReader
import com.github.yumelira.yumebox.core.data.ProxyGroupRepository
import com.github.yumelira.yumebox.core.data.ProxySyncPriority
import com.github.yumelira.yumebox.core.data.RemoteControllerStoreReader
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxyGroupInfo
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.throttleByScene
import com.github.yumelira.yumebox.runtime.api.remote.VpnPermissionRequired
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.root.RootAccessStatus
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toRuntimeTargetMode
import com.github.yumelira.yumebox.runtime.client.internal.ProxyEventBus
import com.github.yumelira.yumebox.runtime.client.internal.ProxyServiceEvent
import com.github.yumelira.yumebox.runtime.client.internal.TrafficStatsPoller
import com.github.yumelira.yumebox.runtime.client.remote.HttpClashManager
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

enum class ProxyGroupSyncPriority {
    OFF,
    SLOW,
    FAST,
}

class ProxyFacade(private val context: Context, private val networkSettingsStorage: NetworkSettingsReader, private val remoteControllerStore: RemoteControllerStoreReader,) : ProxyGroupRepository, ConnectionRepository {
    private companion object {
        const val DEFAULT_SYNC_PRIORITY_SOURCE = "default"
        const val CONTROLLER_SWITCH_STOP_TIMEOUT_MS = 4000L
        const val CONTROLLER_SWITCH_STOP_POLL_MS = 100L
    }

    private val appContext: Context = context.appContextOrSelf
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventBus = ProxyEventBus(appContext)
    private val router = RuntimeBackendRouter(
        appContext = appContext,
        ownerProvider = { _runtimeSnapshot.value.owner },
        runningProvider = { _runtimeSnapshot.value.running },
    )
    private val traffic = TrafficStatsPoller(
        router = router,
        screenOn = eventBus.screenOn,
        onTrafficUpdated = ::updateTrafficReady,
        onPayloadRefreshDue = ::refreshAllSafely,
        shouldRefreshPayload = ::shouldRefreshRuntimePayload,
    )
    private val runtimeControl = ProxyRuntimeControl(appContext) { eventBus.actionClashRequestStop }
    private val proxyGroupManager = com.github.yumelira.yumebox.runtime.client.internal.ProxyGroupManager(
        onProxyGroupsPublished = ::handleProxyGroupsPublished,
        scope = scope,
        router = router,
        appContext = appContext,
        snapshotProvider = { _runtimeSnapshot.value },
        isRootSessionActive = { rootTunManager.isRootSessionActive() },
        connectCurrentBackend = { connectCurrentBackend() },
        onScheduleFullRefresh = { delayMillis -> scheduleRuntimeProxyGroupsRefresh(delayMillis) },
    )
    private val rootTunManager = com.github.yumelira.yumebox.runtime.client.internal.RootTunManager(appContext)
    private val _runtimeSnapshot =
        MutableStateFlow(RuntimeStateMapper.idleSnapshot(networkSettingsStorage.proxyMode.value))
    val runtimeSnapshot: StateFlow<RuntimeSnapshot> = _runtimeSnapshot.asStateFlow()

    override val isRunning: StateFlow<Boolean> = runtimeSnapshot
        .map { it.running }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            runtimeSnapshot.value.running,
        )

    override val proxyGroups: StateFlow<List<ProxyGroupInfo>> = proxyGroupManager.proxyGroups
    val resolvedPrimaryNode: StateFlow<Proxy?> = proxyGroupManager.resolvedPrimaryNode
    val rootTunStatus: StateFlow<RootTunStatus> = rootTunManager.rootTunStatus

    private val _currentProfile = MutableStateFlow<Profile?>(null)
    val currentProfile: StateFlow<Profile?> = _currentProfile.asStateFlow()

    val trafficNow: StateFlow<Traffic> get() = traffic.trafficNow
    val trafficTotal: StateFlow<Traffic> get() = traffic.trafficTotal
    override val connectionSnapshot: StateFlow<ConnectionSnapshot> get() = traffic.connectionSnapshot
    val tunnelMode: StateFlow<TunnelState.Mode?> get() = traffic.tunnelMode
    private var proxyGroupSyncJob: Job? = null
    private var previewWarmupJob: Job? = null
    private val operationMutex = Mutex()
    private val proxyGroupSyncMutex = Mutex()
    private val controllerSwitchMutex = Mutex()
    private val syncPriorityRequests =
        MutableStateFlow<Map<String, ProxyGroupSyncPriority>>(emptyMap())
    private var activeProxyGroupSyncPriority = ProxyGroupSyncPriority.OFF
    private val generationCounter = AtomicLong(0L)

    val screenOn: StateFlow<Boolean> get() = eventBus.screenOn

    init {
        RuntimeContractResolver.warmUp(appContext)
        eventBus.register()
        observeServiceEvents()
        observeProxyGroupSyncPriority()
        initializeRuntimeSnapshot()
        observeRemoteController()
    }

    fun shutdown() {
        runCatching { eventBus.unregister() }
        runCatching { ServiceClient.disconnect() }
        runCatching { scope.cancel() }
    }

    fun isRemoteControllerActive(): Boolean =
        remoteControllerStore.controllerEnabled.value &&
            remoteControllerStore.activeBackend() != null

    fun applyRemoteControllerState() {
        scope.launch { controllerSwitchMutex.withLock { applyRemoteControllerStateLocked() } }
    }

    /**
     * Test connectivity to a remote mihomo backend.
     * Creates a temporary [HttpClashManager] and queries the tunnel state.
     */
    suspend fun testRemoteConnection(backend: RemoteBackend): Result<TunnelState> =
        runCatching { HttpClashManager(backendProvider = { backend }).queryTunnelState() }

    private suspend fun applyRemoteControllerStateLocked() {
        if (isRemoteControllerActive()) {
            val snapshot = _runtimeSnapshot.value
            if (
                snapshot.owner != RuntimeOwner.RemoteController ||
                    snapshot.phase != RuntimePhase.Running
            ) {
                stopLocalRuntimeForControllerSwitch()
                publishRuntimeSnapshot(
                    RuntimeSnapshot(
                        owner = RuntimeOwner.RemoteController,
                        phase = RuntimePhase.Running,
                        targetMode = networkSettingsStorage.proxyMode.value.toRuntimeTargetMode(),
                        generation = nextGeneration(),
                        startedAt = System.currentTimeMillis(),
                    )
                )
            }
            startTrafficPolling()
            refreshAllSafely()
        } else if (_runtimeSnapshot.value.owner == RuntimeOwner.RemoteController) {
            reconcileRuntimeState()
        }
    }

    private fun observeRemoteController() {
        scope.launch {
            remoteControllerStore.controllerEnabled.state.collect { applyRemoteControllerState() }
        }
    }

    private suspend fun stopLocalRuntimeForControllerSwitch() {
        runCatching {
            val owner = detectActiveOwner()
            if (
                owner == RuntimeOwner.LocalTun ||
                    owner == RuntimeOwner.LocalHttp ||
                    owner == RuntimeOwner.RootTun
            ) {
                Timber.i("Controller switch: stopping local runtime owner=$owner")
                runtimeControl.stop(owner)
                stopTrafficPolling()
                awaitLocalRuntimeFullyStopped(owner)
            }
        }.onFailure { error ->
            Timber.w(error, "Failed to stop local runtime on controller switch")
        }
    }

    private suspend fun awaitLocalRuntimeFullyStopped(owner: RuntimeOwner) {
        val mode = localModeForOwner(owner)
        if (mode != null) {
            val deadline = System.currentTimeMillis() + CONTROLLER_SWITCH_STOP_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (!RuntimeContractResolver.localRuntimeStatus.isRuntimeActive(mode.toRuntimeTargetMode())) break
                delay(CONTROLLER_SWITCH_STOP_POLL_MS)
            }
        }
        RuntimeContractResolver.localRuntimeStatus.reconcilePersistedRuntimeState()
    }

    private fun markRemoteControllerLost(error: Throwable) {
        val snapshot = _runtimeSnapshot.value
        if (snapshot.owner != RuntimeOwner.RemoteController) return

        publishRuntimeSnapshot(
            snapshot.copy(
                phase = RuntimePhase.Failed,
                trafficReady = false,
                lastError = error.message ?: error::class.simpleName ?: "remote backend lost",
                generation = nextGeneration(),
            )
        )
        traffic.reset()
    }

    private fun observeServiceEvents() {
        eventBus.events
            .onEach { event ->
                when (event) {
                    ProxyServiceEvent.ClashStarted ->
                        reconcileAndRefreshRuntimeState()
                    is ProxyServiceEvent.ClashStopped ->
                        handleRuntimeStopped(event.reason)
                    ProxyServiceEvent.ProfileLoaded,
                    ProxyServiceEvent.ProfileChanged,
                    ProxyServiceEvent.OverrideChanged,
                    ProxyServiceEvent.ServiceRecreated ->
                        reconcileAndRefreshRuntimeState()
                    is ProxyServiceEvent.RootRuntimeFailed -> {
                        Timber.w("Root runtime failed: ${event.error}")
                        handleRuntimeFailure(event.error)
                    }
                }
            }
            .launchIn(scope)
    }

    fun setProxyGroupSyncPriority(
        priority: ProxyGroupSyncPriority,
        source: String = DEFAULT_SYNC_PRIORITY_SOURCE,
    ) {
        syncPriorityRequests.update { current ->
            if (priority == ProxyGroupSyncPriority.OFF) {
                current - source
            } else {
                current + (source to priority)
            }
        }
    }

    override fun setProxyGroupSyncPriority(priority: ProxySyncPriority, source: String) {
        val mapped = when (priority) {
            ProxySyncPriority.OFF -> ProxyGroupSyncPriority.OFF
            ProxySyncPriority.SLOW -> ProxyGroupSyncPriority.SLOW
            ProxySyncPriority.FAST -> ProxyGroupSyncPriority.FAST
        }
        setProxyGroupSyncPriority(mapped, source)
    }

    override fun warmUpProxyGroups() {
        if (previewWarmupJob?.isActive == true) return
        previewWarmupJob = launchPreviewWarmup()
    }

    suspend fun awaitProxyGroupWarmUp() {
        previewWarmupJob?.let { existing ->
            when {
                existing.isActive -> {
                    existing.join()
                    return
                }

                existing.isCompleted -> return
            }
        }

        val job = launchPreviewWarmup()
        previewWarmupJob = job
        job.join()
    }

    suspend fun reconcileRuntimeState() {
        if (isRemoteControllerActive()) {
            applyRemoteControllerState()
            return
        }
        operationMutex.withLock {
            val configuredMode = networkSettingsStorage.proxyMode.value
            RuntimeContractResolver.localRuntimeStatus.reconcilePersistedRuntimeState()
            val shouldBootstrapRootTun = rootTunManager.shouldBootstrapRootTunRuntime()
            val rootStatus = rootTunManager.queryLiveRootTunStatus()
            rootTunManager.applyRootTunStatus(rootStatus)
            val owner = ProxyRuntimeOwnership.detectOwner(rootStatus, ::isLocalSessionActive)

            if (owner == RuntimeOwner.None) {
                stopTrafficPolling()
                clearRuntimeState(resetGroups = false)
                publishRuntimeSnapshot(RuntimeStateMapper.idleSnapshot(configuredMode))
                if (shouldBootstrapRootTun) {
                    scheduleRootTunBootstrap()
                } else {
                    rootTunManager.stopRootTunBootstrap()
                }
                refreshPreviewStateSafely()
                return
            }

            if (owner != RuntimeOwner.RootTun) {
                rootTunManager.stopRootTunBootstrap()
            }
            if (owner == RuntimeOwner.RootTun) {
                rootTunManager.ensureRootTunServiceAttached(rootStatus)
            }

            publishRuntimeSnapshot(
                ProxyRuntimeOwnership.activeSnapshot(
                    owner = owner,
                    configuredMode = configuredMode,
                    rootStatus = rootStatus,
                    localPhase = localRuntimePhaseForOwner(owner),
                    localStartedAt = localRuntimeStartedAtForOwner(owner),
                )
            )

            if (_runtimeSnapshot.value.phase.running) {
                startTrafficPolling()
                refreshAllSafely()
            } else {
                stopTrafficPolling()
                refreshPreviewStateSafely()
            }
            if (owner == RuntimeOwner.RootTun) {
                scheduleRootTunBootstrap()
            }
        }
    }

    private suspend fun reconcileAndRefreshRuntimeState() {
        reconcileRuntimeState()
        if (_runtimeSnapshot.value.phase == RuntimePhase.Running) {
            refreshAllSafely()
        } else {
            refreshPreviewStateSafely()
        }
    }

    private fun launchPreviewWarmup(): Job {
        return scope.launch {
            runCatching { refreshProxyGroups() }
                .onFailure { error -> Timber.d(error, "Warm up proxy groups skipped") }
        }
    }

    suspend fun startProxy(mode: ProxyMode = networkSettingsStorage.proxyMode.value) {
        Timber.i("Start proxy: mode=$mode")
        ServiceClient.connect(appContext)

        val activeProfile = ServiceClient.profile().queryActive()
        check(activeProfile != null) { "No profile selected" }

        if (mode == ProxyMode.Tun) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                throw VpnPermissionRequired(vpnIntent)
            }
        }

        operationMutex.withLock {
            val targetOwner = ProxyRuntimeOwnership.ownerForMode(mode)
            val currentOwner =
                detectActiveOwner().takeIf { it != RuntimeOwner.None }
                    ?: _runtimeSnapshot.value.owner
            if (currentOwner != RuntimeOwner.None) {
                stopProxyInternal(targetMode = mode, completeImmediately = true)
            }

            val generation = nextGeneration()

            clearRuntimeState(resetGroups = false)
            _currentProfile.value = activeProfile
            publishRuntimeSnapshot(
                ProxyRuntimeOwnership.startingSnapshot(
                    owner = targetOwner,
                    targetMode = mode,
                    profile = activeProfile,
                    generation = generation,
                )
            )

            runCatching { runtimeControl.start(targetOwner, mode) }
                .onFailure { error ->
                    clearRuntimeState(resetGroups = false)
                    publishRuntimeSnapshot(
                        RuntimeStateMapper.idleSnapshot(
                            configuredMode = mode,
                            generation = generation,
                            lastError = error.message,
                        )
                    )
                    stopTrafficPolling()
                    scope.launch { refreshPreviewStateSafely() }
                    throw error
                }
            if (targetOwner == RuntimeOwner.RootTun) {
                rootTunManager.applyRootTunStatus(
                    RootTunStatus(
                        state = RuntimePhase.Starting
                    )
                )
                scheduleRootTunBootstrap()
            }
        }
        if (ProxyRuntimeOwnership.ownerForMode(mode) == RuntimeOwner.RootTun) {
            handleRuntimeStarted(forceOwner = RuntimeOwner.RootTun)
        }
    }

    suspend fun stopProxy(mode: ProxyMode? = null) {
        val targetMode = mode ?: networkSettingsStorage.proxyMode.value

        operationMutex.withLock { stopProxyInternal(targetMode, completeImmediately = true) }
    }

    suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean = false): List<String> =
        proxyGroupManager.queryProxyGroupNames(excludeNotSelectable)

    suspend fun queryProfileProxyGroups(excludeNotSelectable: Boolean = false): List<ProxyGroup> =
        proxyGroupManager.queryProfileProxyGroups(excludeNotSelectable)

    suspend fun queryProxyGroup(name: String, sort: ProxySort = ProxySort.Default): ProxyGroup =
        proxyGroupManager.queryProxyGroup(name, sort)

    override suspend fun selectProxy(group: String, proxyName: String): Boolean =
        proxyGroupManager.selectProxy(group, proxyName)

    override suspend fun forceSelectProxy(group: String, proxyName: String): Boolean =
        proxyGroupManager.forceSelectProxy(group, proxyName)

    suspend fun patchTunnelMode(mode: TunnelState.Mode): Boolean {
        connectCurrentBackend()
        val ok = ServiceClient.clash().patchTunnelMode(mode)
        if (ok) {
            traffic.refreshTunnelMode()
        }
        return ok
    }

    override suspend fun healthCheck(group: String) =
        proxyGroupManager.healthCheck(group)

    override suspend fun healthCheckAll() =
        proxyGroupManager.healthCheckAll()

    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        proxyGroupManager.healthCheckProxy(group, proxyName)

    suspend fun queryTunnelState(): TunnelState = traffic.queryTunnelState()

    suspend fun queryConnections(): ConnectionSnapshot = traffic.queryConnections()

    override suspend fun closeConnection(id: String): Boolean = traffic.closeConnection(id)

    override suspend fun closeAllConnections() = traffic.closeAllConnections()

    suspend fun queryTrafficTotal(): Long = traffic.queryTrafficTotal()

    suspend fun queryTrafficNow(): Long = traffic.queryTrafficNow()

    suspend fun evaluateRootAccess(): RootAccessStatus {
        return rootTunManager.evaluateRootAccess()
    }

    fun hasRootPackageAccess(): Boolean {
        return rootTunManager.hasRootPackageAccess()
    }

    fun queryInstalledRootPackageNames(): Set<String>? {
        return rootTunManager.queryInstalledRootPackageNames()
    }

    override suspend fun refreshProxyGroups() {
        proxyGroupManager.setPreviewProfile(_currentProfile.value)
        proxyGroupManager.refreshProxyGroups(
            appContext = appContext,
            snapshot = _runtimeSnapshot.value,
            isRootSessionActive = { rootTunManager.isRootSessionActive() },
            connectCurrentBackend = { connectCurrentBackend() },
        )
    }

    override suspend fun refreshProxyGroup(name: String, sort: ProxySort) {
        proxyGroupManager.refreshProxyGroup(
            appContext = appContext,
            name = name,
            sort = sort,
            snapshot = _runtimeSnapshot.value,
            isRootSessionActive = { rootTunManager.isRootSessionActive() },
            connectCurrentBackend = { connectCurrentBackend() },
        )
    }

    suspend fun refreshCurrentProfile() {
        if (isRemoteControllerActive()) {
            _currentProfile.value = null
            updateProfileReady(null)
            return
        }
        when {
            _runtimeSnapshot.value.owner == RuntimeOwner.RootTun &&
                _runtimeSnapshot.value.phase == RuntimePhase.Running -> {
                val status = rootTunManager.currentRootTunStatus()
                rootTunManager.applyRootTunStatus(status)
                refreshRootCurrentProfile(status)
            }

            else -> {
                runCatching {
                        connectCurrentBackend()
                        val profile = ServiceClient.profile().queryActive()
                        _currentProfile.value = profile
                        updateProfileReady(profile)
                    }
                    .onFailure { error -> Timber.e(error, "Failed to refresh current profile") }
            }
        }
    }

    suspend fun refreshAll() {
        refreshCurrentProfile()
        refreshProxyGroups()
        if (_runtimeSnapshot.value.phase == RuntimePhase.Running) {
            traffic.queryTrafficNow(notify = false)
            traffic.queryTrafficTotal(notify = false)
            traffic.notifyTrafficUpdated()
            traffic.refreshTunnelMode()
        } else {
            traffic.reset()
        }
    }

    private suspend fun stopProxyInternal(
        targetMode: ProxyMode,
        completeImmediately: Boolean = false,
    ) {
        val owner =
            detectActiveOwner().takeIf { it != RuntimeOwner.None } ?: _runtimeSnapshot.value.owner
        val generation = nextGeneration()

        if (owner == RuntimeOwner.None) {
            rootTunManager.stopRootTunBootstrap()
            clearRuntimeState(resetGroups = false)
            publishRuntimeSnapshot(
                RuntimeStateMapper.idleSnapshot(targetMode, generation = generation)
            )
            stopTrafficPolling()
            scope.launch { refreshPreviewStateSafely() }
            return
        }

        val previousSnapshot = _runtimeSnapshot.value
        publishRuntimeSnapshot(
            previousSnapshot.copy(
                owner = owner,
                phase = RuntimePhase.Stopping,
                targetMode = targetMode.toRuntimeTargetMode(),
                profileReady = false,
                groupsReady = false,
                trafficReady = false,
                lastError = null,
                generation = generation,
            )
        )
        // Close TUN fd first so Android unbinds the VpnService.
        // stopService() cannot destroy a bound VpnService — the TUN fd must be closed first.
        if (owner == RuntimeOwner.LocalTun) {
            runCatching { com.github.yumelira.yumebox.core.Clash.stopTun() }
        }

        runCatching { runtimeControl.stop(owner) }
            .onFailure {
                publishRuntimeSnapshot(previousSnapshot)
                throw it
            }
        if (owner == RuntimeOwner.RootTun) {
            rootTunManager.stopRootTunBootstrap()
            rootTunManager.applyRootTunStatus(
                RootTunStatus(
                    state = RuntimePhase.Stopping
                )
            )
        }

        stopTrafficPolling()
        if (!completeImmediately) {
            return
        }

        clearRuntimeState(resetGroups = false)
        publishRuntimeSnapshot(RuntimeStateMapper.idleSnapshot(targetMode, generation = generation))
        scope.launch { refreshPreviewStateSafely() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startTrafficPolling() {
        traffic.start(scope)
    }

    private fun stopTrafficPolling() {
        traffic.stop()
    }

    private fun initializeRuntimeSnapshot() {
        if (isRemoteControllerActive()) {
            applyRemoteControllerState()
            return
        }
        val configuredMode = networkSettingsStorage.proxyMode.value
        rootTunManager.clearLegacyRuntimeCaches()
        RuntimeContractResolver.localRuntimeStatus.reconcilePersistedRuntimeState()
        val persistedRootStatus = rootTunManager.resolveObservedRootTunStatus()
        val shouldBootstrapRootTun = rootTunManager.shouldBootstrapRootTunRuntime(persistedRootStatus)
        val rootStatus =
            persistedRootStatus.takeIf { rootTunManager.shouldAttachRootTunForegroundService(it) } ?: RootTunStatus()
        rootTunManager.applyRootTunStatus(rootStatus)
        val owner = ProxyRuntimeOwnership.detectOwner(rootStatus, ::isLocalSessionActive)

        if (owner == RuntimeOwner.None) {
            clearRuntimeState(resetGroups = false)
            publishRuntimeSnapshot(RuntimeStateMapper.idleSnapshot(configuredMode))
            if (shouldBootstrapRootTun) {
                scheduleRootTunBootstrap()
            } else {
                rootTunManager.stopRootTunBootstrap()
            }
            scope.launch { refreshPreviewStateSafely() }
            return
        }

        if (owner != RuntimeOwner.RootTun) {
            rootTunManager.stopRootTunBootstrap()
        }
        if (owner == RuntimeOwner.RootTun) {
            rootTunManager.ensureRootTunServiceAttached(rootStatus)
        }

        publishRuntimeSnapshot(
            ProxyRuntimeOwnership.activeSnapshot(
                owner = owner,
                configuredMode = configuredMode,
                rootStatus = rootStatus,
                localPhase = localRuntimePhaseForOwner(owner),
                localStartedAt = localRuntimeStartedAtForOwner(owner),
            )
        )
        if (_runtimeSnapshot.value.phase.running) {
            startTrafficPolling()
            scope.launch { refreshAllSafely() }
        } else {
            stopTrafficPolling()
            scope.launch { refreshPreviewStateSafely() }
        }
        if (owner == RuntimeOwner.RootTun) {
            scheduleRootTunBootstrap()
            scope.launch { reconcileRootTunRuntimeStateSafely() }
        }
    }

    private fun detectActiveOwner(): RuntimeOwner {
        RuntimeContractResolver.localRuntimeStatus.reconcilePersistedRuntimeState()
        return ProxyRuntimeOwnership.detectOwner(rootTunManager.rootTunStatus.value, ::isLocalSessionActive)
    }

    private fun isLocalSessionActive(mode: ProxyMode?): Boolean {
        if (mode == null) return false
        return RuntimeContractResolver.localRuntimeStatus.isRuntimeActive(mode.toRuntimeTargetMode())
    }

    private fun localRuntimePhaseForOwner(owner: RuntimeOwner): LocalRuntimePhase {
        val localMode = localModeForOwner(owner) ?: return LocalRuntimePhase.Idle
        return RuntimeContractResolver.localRuntimeStatus.queryRuntimePhase(localMode.toRuntimeTargetMode())
    }

    private fun localRuntimeStartedAtForOwner(owner: RuntimeOwner): Long? {
        val localMode = localModeForOwner(owner) ?: return null
        return RuntimeContractResolver.localRuntimeStatus.queryRuntimeStartedAt(localMode.toRuntimeTargetMode())
            ?: _runtimeSnapshot.value.startedAt?.takeIf { _runtimeSnapshot.value.owner == owner }
    }

    private fun localModeForOwner(owner: RuntimeOwner): ProxyMode? {
        return when (owner) {
            RuntimeOwner.LocalTun -> ProxyMode.Tun
            RuntimeOwner.LocalHttp -> ProxyMode.Http
            RuntimeOwner.RootTun,
            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> null
        }
    }

    private suspend fun handleRuntimeStarted(forceOwner: RuntimeOwner? = null) {
        val currentSnapshot = _runtimeSnapshot.value
        val owner =
            forceOwner
                ?: currentSnapshot.owner.takeIf { it != RuntimeOwner.None }
                ?: detectActiveOwner()
        if (owner == RuntimeOwner.None) return

        publishRuntimeSnapshot(
            ProxyRuntimeOwnership.startedSnapshot(
                current = currentSnapshot,
                owner = owner,
                configuredMode = networkSettingsStorage.proxyMode.value,
            )
        )
        startTrafficPolling()
        refreshAllSafely()
    }

    private suspend fun handleRuntimeStopped(reason: String?) {
        if (isRemoteControllerActive()) {
            applyRemoteControllerState()
            return
        }
        val configuredMode = networkSettingsStorage.proxyMode.value
        val generation = nextGeneration()
        rootTunManager.stopRootTunBootstrap()

        if (!rootTunManager.isRootSessionActive()) {
            rootTunManager.markIdle(reason)
        }

        clearRuntimeState(resetGroups = false)
        publishRuntimeSnapshot(
            RuntimeStateMapper.idleSnapshot(
                configuredMode = configuredMode,
                generation = generation,
                lastError = reason,
            )
        )
        stopTrafficPolling()
        scope.launch { refreshPreviewStateSafely() }
    }

    private fun handleRuntimeFailure(error: String?) {
        if (isRemoteControllerActive()) {
            applyRemoteControllerState()
            return
        }
        val generation = nextGeneration()
        rootTunManager.stopRootTunBootstrap()
        if (!rootTunManager.isRootSessionActive()) {
            rootTunManager.markIdle(error)
        }
        clearRuntimeState(resetGroups = false)
        publishRuntimeSnapshot(
            RuntimeStateMapper.idleSnapshot(
                configuredMode = networkSettingsStorage.proxyMode.value,
                generation = generation,
                lastError = error ?: "root runtime failed",
            )
        )
        stopTrafficPolling()
        scope.launch { refreshPreviewStateSafely() }
    }

    private suspend fun refreshAllSafely() {
        val snapshot = _runtimeSnapshot.value
        if (snapshot.phase != RuntimePhase.Running && snapshot.owner != RuntimeOwner.RemoteController) {
            return
        }
        runCatching { withTimeoutOrNull(10_000L) { refreshAll() } ?: Timber.w("refreshAll timed out after 10s") }
            .onFailure { error ->
                if (snapshot.owner == RuntimeOwner.RemoteController) {
                    markRemoteControllerLost(error)
                }
                Timber.d(error, "Refresh runtime data skipped") }
    }

    private suspend fun refreshPreviewStateSafely() {
        runCatching {
                refreshCurrentProfile()
                refreshProxyGroups()
            }
            .onFailure { error -> Timber.d(error, "Refresh preview data skipped") }
    }

    private fun shouldRefreshRuntimePayload(): Boolean {
        val snapshot = _runtimeSnapshot.value
        return snapshot.phase == RuntimePhase.Running &&
            (!snapshot.profileReady ||
                !snapshot.groupsReady ||
                proxyGroupManager.proxyGroups.value.isEmpty() ||
                _currentProfile.value == null)
    }

    private fun observeProxyGroupSyncPriority() {
        scope.launch {
            combine(_runtimeSnapshot, syncPriorityRequests) { snapshot, requests ->
                    resolveEffectiveProxyGroupSyncPriority(snapshot, requests)
                }
                .distinctUntilChanged()
                .collect { priority -> restartProxyGroupSyncLoop(priority) }
        }
    }

    private fun resolveEffectiveProxyGroupSyncPriority(
        snapshot: RuntimeSnapshot,
        requests: Map<String, ProxyGroupSyncPriority>,
    ): ProxyGroupSyncPriority {
        if (snapshot.phase != RuntimePhase.Running && snapshot.owner != RuntimeOwner.RemoteController) {
            return ProxyGroupSyncPriority.OFF
        }
        return requests.values.maxByOrNull { it.ordinal } ?: ProxyGroupSyncPriority.OFF
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun restartProxyGroupSyncLoop(priority: ProxyGroupSyncPriority) {
        scope.launch {
            proxyGroupSyncMutex.withLock {
                if (activeProxyGroupSyncPriority == priority && proxyGroupSyncJob?.isActive == true) {
                    return@withLock
                }
                activeProxyGroupSyncPriority = priority
                proxyGroupSyncJob?.cancel()
                proxyGroupSyncJob = null
                if (priority == ProxyGroupSyncPriority.OFF) {
                    return@withLock
                }
                val timerSpec = when (priority) {
                    ProxyGroupSyncPriority.FAST -> PollingTimerSpecs.RuntimeProxyGroupSyncFast
                    ProxyGroupSyncPriority.SLOW -> PollingTimerSpecs.RuntimeProxyGroupSyncSlow
                    ProxyGroupSyncPriority.OFF -> return@withLock
                }
                val (bgInterval, screenOffInterval) = when (priority) {
                    ProxyGroupSyncPriority.FAST -> 5_000L to 60_000L
                    ProxyGroupSyncPriority.SLOW -> 20_000L to 120_000L
                    ProxyGroupSyncPriority.OFF -> return@withLock
                }
                proxyGroupSyncJob = scope.launch {
                    PollingTimers.ticks(timerSpec)
                        .throttleByScene(
                            screenOn = screenOn,
                            appForeground = AppForegroundState.foreground,
                            backgroundIntervalMs = bgInterval,
                            screenOffIntervalMs = screenOffInterval,
                        )
                        .collect {
                        refreshRuntimeProxyGroupsSafely()
                    }
                }
            }
        }
    }

    private suspend fun refreshRuntimeProxyGroupsSafely() {
        val snapshot = _runtimeSnapshot.value
        if (snapshot.phase != RuntimePhase.Running && snapshot.owner != RuntimeOwner.RemoteController) {
            return
        }
        runCatching { refreshProxyGroups() }
            .onFailure { error ->
                if (snapshot.owner == RuntimeOwner.RemoteController) {
                    markRemoteControllerLost(error)
                }
                Timber.d(error, "Runtime proxy group sync skipped")
            }
    }

    private fun scheduleRuntimeProxyGroupsRefresh(delayMillis: Long = 0L) {
        proxyGroupManager.scheduleRuntimeProxyGroupsRefresh(scope, delayMillis) { refreshRuntimeProxyGroupsSafely() }
    }

    private suspend fun reconcileRootTunRuntimeStateSafely() {
        rootTunManager.reconcileRootTunRuntimeState(
            snapshotProvider = { _runtimeSnapshot.value },
            onStartTrafficPolling = { startTrafficPolling() },
            onRefreshAll = { refreshAllSafely() },
        )
    }

    private fun scheduleRootTunBootstrap() {
        rootTunManager.scheduleRootTunBootstrap(
            scope = scope,
            snapshotProvider = { _runtimeSnapshot.value },
            onStatusResolved = { status, snapshot ->
                val configuredMode = networkSettingsStorage.proxyMode.value
                publishRuntimeSnapshot(
                    ProxyRuntimeOwnership.activeSnapshot(
                        owner = RuntimeOwner.RootTun,
                        configuredMode = configuredMode,
                        rootStatus = status,
                        localPhase = localRuntimePhaseForOwner(RuntimeOwner.RootTun),
                        localStartedAt = localRuntimeStartedAtForOwner(RuntimeOwner.RootTun),
                    )
                )
            },
            onStartTrafficPolling = { startTrafficPolling() },
            onRefreshAll = { refreshAllSafely() },
        )
    }

    private fun clearRuntimeState(resetGroups: Boolean = true) {
        _currentProfile.value = null
        traffic.reset()
        if (resetGroups) {
            proxyGroupManager.clearGroups()
        }
    }

    private fun updateProfileReady(profile: Profile?) {
        val snapshot = _runtimeSnapshot.value
        publishRuntimeSnapshot(
            snapshot.copy(
                profileReady = profile != null,
                profileUuid = profile?.uuid?.toString() ?: snapshot.profileUuid,
                profileName = profile?.name ?: snapshot.profileName,
            )
        )
    }

    private fun updateGroupsReady(ready: Boolean) {
        publishRuntimeSnapshot(_runtimeSnapshot.value.copy(groupsReady = ready))
    }

    private fun handleProxyGroupsPublished(groups: List<ProxyGroupInfo>) {
        updateGroupsReady(groups.isNotEmpty())
        proxyGroupManager.updateResolvedPrimaryNode(_runtimeSnapshot.value, groups)
    }

    private fun updateTrafficReady() {
        if (!_runtimeSnapshot.value.trafficReady) {
            publishRuntimeSnapshot(_runtimeSnapshot.value.copy(trafficReady = true))
        }
    }

    private fun publishRuntimeSnapshot(snapshot: RuntimeSnapshot) {
        val normalized = snapshot.copy(running = snapshot.phase.running)
        _runtimeSnapshot.value = normalized
    }

    private suspend fun refreshRootCurrentProfile(status: RootTunStatus) {
        runCatching {
                connectCurrentBackend()
                val profile =
                    status.profileUuid
                        ?.takeIf { it.isNotBlank() }
                        ?.let { uuid -> ServiceClient.profile().queryByUUID(java.util.UUID.fromString(uuid)) }
                        ?: ServiceClient.profile().queryActive()

                if (profile != null) {
                    _currentProfile.value = profile
                }
                updateProfileReady(profile)
            }
            .onFailure { error -> Timber.d(error, "Failed to refresh root current profile") }
    }

    private fun nextGeneration(): Long = generationCounter.incrementAndGet()

    private suspend fun connectCurrentBackend() {
        ServiceClient.connect(appContext)
    }
}
