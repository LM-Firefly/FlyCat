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

package com.github.lmfirefly.flycat.feature.home.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.NetworkInfoReader
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.ProxySyncPriority
import com.github.lmfirefly.flycat.core.contract.RemoteControllerStoreReader
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.model.IpMonitoringState
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.traffic.TrafficData
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.feature.home.domain.ProfileManagementUseCase
import com.github.lmfirefly.flycat.feature.home.domain.ProxyLifecycleUseCase
import com.github.lmfirefly.flycat.presentation.viewmodel.AndroidContractStateViewModel
import com.github.lmfirefly.flycat.presentation.viewmodel.LoadableState
import com.github.lmfirefly.flycat.runtime.api.contract.ProfileRepositoryContract
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeStateMapper
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.lmfirefly.flycat.locale.FlyTxt
import timber.log.Timber

enum class HomeProxyControlState {
    Idle,
    Connecting,
    Running,
    Lost,
    Disconnecting;

    val canInteract: Boolean
        get() = this == Idle || this == Running
}

class HomeViewModel(
    application: Application,
    private val proxyFacade: ProxyControlContract,
    private val profilesRepository: ProfileRepositoryContract,
    private val networkInfoService: NetworkInfoReader,
    private val networkSettingsStore: NetworkSettingsReader,
    private val remoteControllerStore: RemoteControllerStoreReader,
    private val profileUseCase: ProfileManagementUseCase,
    private val lifecycleUseCase: ProxyLifecycleUseCase,
) :
    AndroidContractStateViewModel<HomeViewModel.HomeUiState, HomeViewModel.HomeUiEffect>(
        application,
        HomeUiState(),
    ) {
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _recommendedProfile = MutableStateFlow<Profile?>(null)
    val recommendedProfile: StateFlow<Profile?> = _recommendedProfile.asStateFlow()

    private val _profilesLoaded = MutableStateFlow(false)
    val profilesLoaded: StateFlow<Boolean> = _profilesLoaded.asStateFlow()

    val hasEnabledProfile: StateFlow<Boolean> = profiles.map { list -> list.any { it.active } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val runtimeSnapshot = proxyFacade.runtimeSnapshot
    val isRunning =
        runtimeSnapshot
            .map(RuntimeStateMapper::isActuallyRunning)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RuntimeStateMapper.isActuallyRunning(runtimeSnapshot.value),
            )
    val isRemoteController: StateFlow<Boolean> =
        runtimeSnapshot
            .map { it.owner == RuntimeOwner.RemoteController }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                runtimeSnapshot.value.owner == RuntimeOwner.RemoteController,
            )
    val controllerBackendName: StateFlow<String?> =
        combine(
                remoteControllerStore.activeBackendId.state,
                remoteControllerStore.backends.state,
            ) { id, list ->
                list.firstOrNull { it.id == id }?.let { it.name.ifBlank { "${it.host}:${it.port}" } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val currentProfile = proxyFacade.currentProfile

    data class HomeProfileState(val profiles: List<Profile>, val profilesLoaded: Boolean, val recommendedProfile: Profile?, val hasEnabledProfile: Boolean, val currentProfile: Profile?)

    val homeProfileState: StateFlow<HomeProfileState> = combine(profiles, profilesLoaded, recommendedProfile, hasEnabledProfile, currentProfile) { p, loaded, recommended, hasEnabled, current -> HomeProfileState(p, loaded, recommended, hasEnabled, current) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeProfileState(profiles.value, profilesLoaded.value, recommendedProfile.value, hasEnabledProfile.value, currentProfile.value))

    val trafficData: StateFlow<TrafficData> = proxyFacade.trafficNow.map(TrafficData::from).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrafficData.zero)

    private val vpnController = VpnProxyController(viewModelScope, proxyFacade)

    private val _runMode = MutableStateFlow(RunMode.VpnService)
    val runMode: StateFlow<RunMode> = _runMode.asStateFlow()

    private var pendingStartRequest: PendingStartRequest? = null

    val vpnPrepareIntent = vpnController.vpnPrepareIntent

    val controlState: StateFlow<HomeProxyControlState> =
        combine(runtimeSnapshot, vpnController.pendingTransition) { snapshot, pendingTransition ->
                resolveControlState(snapshot.owner, snapshot.phase, pendingTransition)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                resolveControlState(
                    runtimeSnapshot.value.owner,
                    runtimeSnapshot.value.phase,
                    vpnController.pendingTransition.value,
                ),
            )

    private val _speedHistory = MutableStateFlow<List<TrafficData>>(emptyList())
    val speedHistory: StateFlow<List<TrafficData>> = _speedHistory.asStateFlow()
    private val _homeScreenActive = MutableStateFlow(false)
    @OptIn(ExperimentalCoroutinesApi::class)
    val connections: StateFlow<List<ConnectionInfo>> = _homeScreenActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList())
        else combine(proxyFacade.connectionSnapshot, runtimeSnapshot.map { it.phase.running }.distinctUntilChanged()) { snapshot, running -> if (running) snapshot.connections.take(256) else emptyList() }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tunnelMode: StateFlow<TunnelState.Mode?> = proxyFacade.tunnelMode
    private var reconcileJob: Job? = null
    private var lastReconcileTime = 0L

    private val mainProxyNode: StateFlow<Proxy?> =
        proxyFacade.resolvedPrimaryNode

    val selectedServerName: StateFlow<String?> =
        mainProxyNode
            .map { it?.name }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedServerPing: StateFlow<Int?> =
        mainProxyNode
            .map { node -> node?.delay?.takeIf { delay -> delay > 0 } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ipMonitoringState: StateFlow<IpMonitoringState> =
        isRunning
            .flatMapLatest { running ->
                if (running) {
                    networkInfoService.startIpMonitoring(
                        isProxyActiveFlow = isRunning,
                    )
                } else {
                    flowOf(IpMonitoringState.Loading)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                IpMonitoringState.Loading,
            )

    init {
        refreshProfiles()
        reconcileRuntimeState()
        observeControlState()
        vpnController.observeRuntimeState()
        observeRuntimeFailures()
        syncRunModeState()
        startSpeedSampling()
        observeProfileChanges()
    }

    fun refreshProfiles() {
        viewModelScope.launch {
            val snapshot = profileUseCase.queryProfiles()
            _profiles.value = snapshot.profiles
            _recommendedProfile.value = snapshot.recommendedProfile
            _profilesLoaded.value = snapshot.loaded
        }
    }

    private fun observeProfileChanges() {
        viewModelScope.launch {
            proxyFacade.currentProfile
                .map { it?.uuid }
                .distinctUntilChanged()
                .collect { refreshProfiles() }
        }
    }

    private fun observeControlState() {
        viewModelScope.launch {
            controlState.collect { state ->
                if (state != HomeProxyControlState.Running) {
                    _speedHistory.value = List(24) { TrafficData.zero }
                }
                _uiState.update {
                    it.copy(
                        isStartingProxy = state == HomeProxyControlState.Connecting,
                        loadingProgress =
                            if (state == HomeProxyControlState.Connecting) {
                                FlyTxt.Home.Message.Preparing
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }

    private fun syncRunModeState() {
        viewModelScope.launch {
            runtimeSnapshot
                .map {
                    RuntimeStateMapper.resolveDisplayMode(it, networkSettingsStore.runMode.value)
                }
                .distinctUntilChanged()
                .collect { refreshRunMode() }
        }
    }

    private fun observeRuntimeFailures() {
        viewModelScope.launch {
            runtimeSnapshot
                .drop(1)
                .map { snapshot -> Triple(snapshot.phase, snapshot.lastError, snapshot.generation) }
                .distinctUntilChanged()
                .collect { (phase, lastError, _) ->
                    if (phase == RuntimePhase.Failed && !lastError.isNullOrBlank()) {
                        showError(lastError)
                    }
                }
        }
    }

    fun refreshRunMode() {
        _runMode.value = lifecycleUseCase.resolveDisplayMode(runtimeSnapshot.value)
    }

    fun setHomeScreenActive(isActive: Boolean) {
        _homeScreenActive.value = isActive
        proxyFacade.setProxyGroupSyncPriority(
            priority = if (isActive) ProxySyncPriority.SLOW else ProxySyncPriority.OFF,
            source = "home",
        )
    }

    fun reconcileRuntimeState() {
        if (reconcileJob?.isActive == true) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastReconcileTime < RECONCILE_MIN_INTERVAL_MS) return
        lastReconcileTime = now
        reconcileJob = viewModelScope.launch {
            runCatching {
                lifecycleUseCase.reconcileRuntimeState()
                refreshProfiles()
                refreshRunMode()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "Failed to reconcile runtime state for home")
            }
        }
    }

    fun isCurrentProfile(profileId: UUID): Boolean = proxyFacade.currentProfile.value?.uuid == profileId

    fun switchActiveProfile(profileId: String) {
        viewModelScope.launch {
            profileUseCase.switchActiveProfile(
                profileId = profileId,
                isRunning = controlState.value == HomeProxyControlState.Running,
                runMode = networkSettingsStore.runMode.value,
                onSuccess = { refreshProfiles() },
                onError = { msg -> showError(FlyTxt.Home.Message.ConfigSwitchFailed.format(msg)) },
            )
        }
    }

    fun reloadProfile() {
        viewModelScope.launch {
            profileUseCase.reloadProfile(
                isRunning = controlState.value == HomeProxyControlState.Running,
                runMode = networkSettingsStore.runMode.value,
                onSuccess = { refreshProfiles() },
                onError = { msg -> showError(FlyTxt.Home.Message.ConfigSwitchFailed.format(msg)) },
            )
        }
    }

    fun switchRunMode(mode: RunMode) {
        viewModelScope.launch {
            lifecycleUseCase.switchRunMode(
                mode = mode,
                isRunning = controlState.value == HomeProxyControlState.Running,
                onSuccess = { _runMode.value = mode; refreshRunMode() },
                onError = { msg -> showError(FlyTxt.Home.Message.StartFailed.format(msg)) },
            )
        }
    }

    fun switchTunnelMode(mode: TunnelState.Mode) {
        viewModelScope.launch {
            lifecycleUseCase.switchTunnelMode(
                mode = mode,
                currentMode = tunnelMode.value,
                isRunning = controlState.value == HomeProxyControlState.Running,
                onError = { msg -> showError(FlyTxt.Home.Message.StartFailed.format(msg)) },
            )
        }
    }

    fun startProxy(profileId: String, mode: RunMode? = null) {
        if (!controlState.value.canInteract || controlState.value != HomeProxyControlState.Idle) {
            return
        }

        val request =
            PendingStartRequest(
                profileId = profileId,
                mode = mode ?: networkSettingsStore.runMode.value,
            )
        pendingStartRequest = request
        vpnController.startProxy(
            mode = request.mode,
            onPreStart = {
                _runMode.value = request.mode
                if (request.mode == RunMode.Tun) {
                    val rootStatus = proxyFacade.evaluateRootAccess()
                    if (!rootStatus.canStartRootTun) { showError(rootStatus.rootTunBlockedMessage()); throw CancellationException("RootTun not available") }
                }
                withContext(Dispatchers.IO) {
                    if (request.profileId.isNotBlank()) { profilesRepository.setActiveProfile(java.util.UUID.fromString(request.profileId)) }
                    AutoStartSessionGate.clearManualPaused()
                }
            },
            onSuccess = { Timber.i("Home startProxy completed, mode=${request.mode}"); if (proxyFacade.isRemoteControllerActive()) { clearPendingStart(); vpnController.clearPendingTransition() } },
        )
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val request = pendingStartRequest ?: return
        vpnController.onVpnPermissionResult(granted) {
            startProxy(request.profileId, request.mode)
        }
    }

    // Fault barrier: any stop failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    suspend fun stopProxy() {
        if (
            !controlState.value.canInteract || controlState.value != HomeProxyControlState.Running
        ) {
            return
        }

        vpnController.stopProxy(onPreStop = { AutoStartSessionGate.markManualPaused() })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startSpeedSampling(sampleLimit: Int = 24) {
        // Pre-allocate a mutable list to use as a ring buffer, avoiding per-second allocation.
        val ringBuffer = MutableList(sampleLimit) { TrafficData.zero }
        // Pre-allocated scratch list for building snapshots without intermediate subList/concat.
        val scratchList = ArrayList<TrafficData>(sampleLimit)
        var writeIndex = 0
        var fillCount = 0
        var lastEmittedAllZero = false
        viewModelScope.launch {
            _homeScreenActive.flatMapLatest { active -> if (active) trafficData else emptyFlow() }.collect { sample ->
                val snapshot = runtimeSnapshot.value
                val data = when {
                    snapshot.phase.running -> sample
                    else -> TrafficData.zero
                }
                // Skip StateFlow update when idle: all samples are zero and list content won't change.
                if (data == TrafficData.zero && lastEmittedAllZero && fillCount >= sampleLimit) return@collect
                ringBuffer[writeIndex] = data
                writeIndex = (writeIndex + 1) % sampleLimit
                fillCount = (fillCount + 1).coerceAtMost(sampleLimit)
                // Build snapshot in scratch list, then copy to a new list for StateFlow identity change.
                scratchList.clear()
                if (fillCount < sampleLimit) {
                    for (i in 0 until fillCount) scratchList.add(ringBuffer[i])
                } else {
                    for (i in writeIndex until sampleLimit) scratchList.add(ringBuffer[i])
                    for (i in 0 until writeIndex) scratchList.add(ringBuffer[i])
                }
                lastEmittedAllZero = scratchList.all { it == TrafficData.zero }
                _speedHistory.value = ArrayList(scratchList)
            }
        }
    }

    private fun showError(error: String) = postError(error, HomeUiEffect.ShowError(error))

    private fun showMessage(message: String) = postMessage(message, HomeUiEffect.ShowMessage(message))

    fun consumeMessage() = clearMessageState()

    fun consumeError() = clearErrorState()

    private fun clearPendingStart() {
        pendingStartRequest = null
    }

    private fun resolveControlState(
        owner: RuntimeOwner,
        phase: RuntimePhase,
        pendingTransition: VpnProxyController.PendingTransition,
    ): HomeProxyControlState {
        if (owner == RuntimeOwner.RemoteController && phase == RuntimePhase.Failed) {
            return HomeProxyControlState.Lost
        }
        if (pendingTransition == VpnProxyController.PendingTransition.Stopping && phase != RuntimePhase.Stopping && phase != RuntimePhase.Idle && phase != RuntimePhase.Failed) { return HomeProxyControlState.Disconnecting }
        return when (phase) {
            RuntimePhase.Running -> HomeProxyControlState.Running
            RuntimePhase.Starting -> HomeProxyControlState.Connecting
            RuntimePhase.Stopping -> HomeProxyControlState.Disconnecting
            RuntimePhase.Idle,
            RuntimePhase.Failed ->
                when (pendingTransition) {
                    VpnProxyController.PendingTransition.AwaitingPermission,
                    VpnProxyController.PendingTransition.Starting -> HomeProxyControlState.Connecting
                    VpnProxyController.PendingTransition.Stopping -> HomeProxyControlState.Idle
                    VpnProxyController.PendingTransition.None -> HomeProxyControlState.Idle
                }
        }
    }

    private data class PendingStartRequest(
        val profileId: String,
        val mode: RunMode,
    )

    companion object {
        private const val RECONCILE_MIN_INTERVAL_MS = 1_000L
    }

    data class HomeUiState(
        override val isLoading: Boolean = false,
        val isStartingProxy: Boolean = false,
        val loadingProgress: String? = null,
        override val message: String? = null,
        override val error: String? = null,
    ) : LoadableState<HomeUiState> {
        override fun withLoading(loading: Boolean): HomeUiState = copy(isLoading = loading)

        override fun withError(error: String?): HomeUiState = copy(error = error)

        override fun withMessage(message: String?): HomeUiState = copy(message = message)
    }

    sealed interface HomeUiEffect {
        data class ShowMessage(val message: String) : HomeUiEffect
        data class ShowError(val message: String) : HomeUiEffect
    }
}
