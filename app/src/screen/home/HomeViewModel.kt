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

package com.github.yumelira.yumebox.screen.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.NetworkInfoReader
import com.github.yumelira.yumebox.core.data.NetworkSettingsReader
import com.github.yumelira.yumebox.core.domain.model.TrafficData
import com.github.yumelira.yumebox.core.model.ConnectionInfo
import com.github.yumelira.yumebox.core.model.IpMonitoringState
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.presentation.viewmodel.AndroidContractStateViewModel
import com.github.yumelira.yumebox.presentation.viewmodel.LoadableState
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.ProxyGroupSyncPriority
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import com.github.yumelira.yumebox.runtime.client.VpnProxyController
import dev.oom_wg.purejoy.mlang.MLang
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val proxyFacade: ProxyFacade,
    private val profilesRepository: ProfilesRepository,
    private val networkInfoService: NetworkInfoReader,
    private val networkSettingsStore: NetworkSettingsReader,
    private val remoteControllerStore: com.github.yumelira.yumebox.data.store.RemoteControllerStore,
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

    val hasEnabledProfile: Flow<Boolean> = profiles.map { list -> list.any { it.active } }

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
    val trafficData: StateFlow<TrafficData> = proxyFacade.trafficNow
        .map(TrafficData::from)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrafficData.ZERO)
    val proxyGroups = proxyFacade.proxyGroups

    private val vpnController = VpnProxyController(viewModelScope, proxyFacade)

    private val _proxyMode = MutableStateFlow(ProxyMode.Tun)
    val proxyMode: StateFlow<ProxyMode> = _proxyMode.asStateFlow()

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
    val connections: StateFlow<List<ConnectionInfo>> = _homeScreenActive
        .flatMapLatest { active ->
            if (!active) flowOf(emptyList())
            else combine(
                proxyFacade.connectionSnapshot,
                runtimeSnapshot.map { it.phase.running }.distinctUntilChanged(),
            ) { snapshot, running ->
                if (running) snapshot.connections.take(256) else emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tunnelMode: StateFlow<TunnelState.Mode?> = proxyFacade.tunnelMode
    private var reconcileJob: Job? = null
    private var lastReconcileTime = 0L

    private val mainProxyNode: StateFlow<com.github.yumelira.yumebox.core.model.Proxy?> =
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
        syncProxyModeState()
        startSpeedSampling()
        observeProfileChanges()
    }

    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                val allProfiles = profilesRepository.queryAllProfiles()
                val active = profilesRepository.queryActiveProfile()
                _profiles.value = allProfiles
                _recommendedProfile.value = active
                _profilesLoaded.value = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to refresh profiles")
                _profilesLoaded.value = true
            }
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
                    _speedHistory.value = List(24) { TrafficData.ZERO }
                }
                _uiState.update {
                    it.copy(
                        isStartingProxy = state == HomeProxyControlState.Connecting,
                        loadingProgress =
                            if (state == HomeProxyControlState.Connecting) {
                                MLang.Home.Message.Preparing
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }

    private fun syncProxyModeState() {
        viewModelScope.launch {
            runtimeSnapshot
                .map {
                    RuntimeStateMapper.resolveDisplayMode(it, networkSettingsStore.proxyMode.value)
                }
                .distinctUntilChanged()
                .collect { refreshProxyMode() }
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

    fun refreshProxyMode() {
        val configuredMode = networkSettingsStore.proxyMode.value
        _proxyMode.value =
            RuntimeStateMapper.resolveDisplayMode(runtimeSnapshot.value, configuredMode)
    }

    fun setHomeScreenActive(isActive: Boolean) {
        _homeScreenActive.value = isActive
        proxyFacade.setProxyGroupSyncPriority(
            priority = if (isActive) ProxyGroupSyncPriority.FAST else ProxyGroupSyncPriority.OFF,
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
                    proxyFacade.reconcileRuntimeState()
                    refreshProfiles()
                    refreshProxyMode()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.w(error, "Failed to reconcile runtime state for home")
                }
        }
    }

    suspend fun reloadProfile() {
        applyLoading(true)
        try {
            val activeProfile = profilesRepository.queryActiveProfile()
            if (activeProfile == null) {
                showError(
                    MLang.Home.Message.ConfigSwitchFailed.format(
                        MLang.ProfilesVM.Error.ProfileNotExist
                    )
                )
                return
            }

            profilesRepository.updateProfile(activeProfile.uuid)

            profilesRepository.setActiveProfile(activeProfile.uuid)
            showMessage(MLang.Home.Message.ConfigSwitched)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.e(error, "Failed to reload profile")
            showError(MLang.Home.Message.ConfigSwitchFailed.format(error.message))
        } finally {
            applyLoading(false)
        }
    }

    fun isCurrentProfile(profileId: UUID): Boolean =
        proxyFacade.currentProfile.value?.uuid == profileId

    fun switchActiveProfile(profileId: String) {
        viewModelScope.launch {
            try {
                val uuid = UUID.fromString(profileId)
                if (proxyFacade.currentProfile.value?.uuid == uuid) return@launch

                withContext(Dispatchers.IO) {
                    profilesRepository.setActiveProfile(uuid)
                }

                refreshProfiles()

                if (controlState.value == HomeProxyControlState.Running) {
                    withContext(Dispatchers.IO) {
                        AutoStartSessionGate.clearManualPaused()
                        proxyFacade.startProxy(networkSettingsStore.proxyMode.value)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to switch active profile")
                showError(MLang.Home.Message.ConfigSwitchFailed.format(error.message))
            }
        }
    }

    fun switchProxyMode(mode: ProxyMode) {
        viewModelScope.launch {
            try {
                if (networkSettingsStore.proxyMode.value == mode) return@launch

                if (mode == ProxyMode.RootTun) {
                    val rootStatus = proxyFacade.evaluateRootAccess()
                    if (!rootStatus.canStartRootTun) {
                        showError(rootStatus.rootTunBlockedMessage())
                        return@launch
                    }
                }

                networkSettingsStore.proxyMode.set(mode)
                _proxyMode.value = mode

                if (controlState.value == HomeProxyControlState.Running) {
                    withContext(Dispatchers.IO) {
                        AutoStartSessionGate.clearManualPaused()
                        proxyFacade.startProxy(mode)
                    }
                } else {
                    refreshProxyMode()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to switch proxy mode")
                showError(MLang.Home.Message.StartFailed.format(error.message))
            }
        }
    }

    fun switchTunnelMode(mode: TunnelState.Mode) {
        viewModelScope.launch {
            try {
                if (tunnelMode.value == mode) return@launch
                if (controlState.value != HomeProxyControlState.Running) return@launch

                val switched = withContext(Dispatchers.IO) {
                    proxyFacade.patchTunnelMode(mode)
                }

                if (!switched) {
                    showError(MLang.Home.Message.StartFailed.format("patch tunnel mode failed"))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to switch tunnel mode")
                showError(MLang.Home.Message.StartFailed.format(error.message))
            }
        }
    }

    fun startProxy(profileId: String, mode: ProxyMode? = null) {
        if (!controlState.value.canInteract || controlState.value != HomeProxyControlState.Idle) {
            return
        }

        val request =
            PendingStartRequest(
                profileId = profileId,
                mode = mode ?: networkSettingsStore.proxyMode.value,
            )
        pendingStartRequest = request
        vpnController.startProxy(
            mode = request.mode,
            onPreStart = {
                _proxyMode.value = request.mode
                if (request.mode == ProxyMode.RootTun) {
                    val rootStatus = proxyFacade.evaluateRootAccess()
                    if (!rootStatus.canStartRootTun) {
                        showError(rootStatus.rootTunBlockedMessage())
                        throw CancellationException("RootTun not available")
                    }
                }
                withContext(Dispatchers.IO) {
                    if (request.profileId.isNotBlank()) {
                        profilesRepository.setActiveProfile(
                            java.util.UUID.fromString(request.profileId)
                        )
                    }
                    AutoStartSessionGate.clearManualPaused()
                }
            },
            onSuccess = {
                Timber.i("Home startProxy completed, mode=${request.mode}")
                if (proxyFacade.isRemoteControllerActive()) {
                    clearPendingStart()
                    vpnController.clearPendingTransition()
                }
            },
        )
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val request = pendingStartRequest ?: return
        vpnController.onVpnPermissionResult(granted) {
            startProxy(request.profileId, request.mode)
        }
    }

    suspend fun stopProxy() {
        if (
            !controlState.value.canInteract || controlState.value != HomeProxyControlState.Running
        ) {
            return
        }

        vpnController.stopProxy(
            onPreStop = { AutoStartSessionGate.markManualPaused() },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startSpeedSampling(sampleLimit: Int = 24) {
        viewModelScope.launch {
            _homeScreenActive.flatMapLatest { active ->
                if (active) PollingTimers.ticks(PollingTimerSpecs.HomeSpeedSampling) else emptyFlow()
            }.collect {
                val snapshot = runtimeSnapshot.value
                val sample =
                    when {
                        snapshot.phase.running -> trafficData.value
                        else -> TrafficData.ZERO
                }
                _speedHistory.update { old ->
                    val deque = ArrayDeque(old)
                    while (deque.size >= sampleLimit) deque.removeFirst()
                    deque.addLast(sample)
                    deque
                }
            }
        }
    }

    private fun applyLoading(loading: Boolean) = super.setLoading(loading)

    private fun showMessage(message: String) =
        postMessage(message, HomeUiEffect.ShowMessage(message))

    private fun showError(error: String) = postError(error, HomeUiEffect.ShowError(error))

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
        if (
            pendingTransition == VpnProxyController.PendingTransition.Stopping &&
                phase != RuntimePhase.Stopping &&
                phase != RuntimePhase.Idle &&
                phase != RuntimePhase.Failed
        ) {
            return HomeProxyControlState.Disconnecting
        }
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
        val mode: ProxyMode,
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
