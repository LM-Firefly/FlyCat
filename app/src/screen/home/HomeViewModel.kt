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
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.common.util.stateInWhileSubscribed
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.presentation.AndroidContractStateViewModel
import com.github.yumelira.yumebox.core.presentation.LoadableState
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.data.network.IpMonitoringState
import com.github.yumelira.yumebox.data.network.NetworkInfoService
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.domain.model.TrafficData
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.ProxyGroupSyncPriority
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import tf.gal.yumebox.locale.YumeTxt
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

private enum class PendingTransition {
    None,
    AwaitingPermission,
    Starting,
    Stopping,
}

class HomeViewModel(
    application: Application,
    private val proxyFacade: ProxyFacade,
    private val profilesRepository: ProfilesRepository,
    private val networkInfoService: NetworkInfoService,
    private val networkSettingsStore: NetworkSettingsStore,
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

    val hasEnabledProfile: StateFlow<Boolean> =
        profiles
            .map { list -> list.any { it.active } }
            .stateInWhileSubscribed(viewModelScope, false)

    val runtimeSnapshot = proxyFacade.runtimeSnapshot
    val isRunning =
        runtimeSnapshot
            .map(RuntimeStateMapper::isActuallyRunning)
            .stateInWhileSubscribed(viewModelScope, RuntimeStateMapper.isActuallyRunning(runtimeSnapshot.value))
    val isRemoteController: StateFlow<Boolean> =
        runtimeSnapshot
            .map { it.owner == RuntimeOwner.RemoteController }
            .stateInWhileSubscribed(viewModelScope, runtimeSnapshot.value.owner == RuntimeOwner.RemoteController)
    val isRemoteControllerMode: StateFlow<Boolean> =
        combine(
                remoteControllerStore.controllerEnabled.state,
                remoteControllerStore.activeBackendId.state,
                remoteControllerStore.backends.state,
            ) { enabled, activeBackendId, backends ->
                enabled && backends.any { it.id == activeBackendId }
            }
            .stateInWhileSubscribed(
                viewModelScope,
                remoteControllerStore.controllerEnabled.value &&
                    remoteControllerStore.activeBackend() != null,
            )
    val isConfigReloading: StateFlow<Boolean> = proxyFacade.isConfigReloading
    val controllerBackendName: StateFlow<String?> =
        combine(
                remoteControllerStore.activeBackendId.state,
                remoteControllerStore.backends.state,
            ) { id, list ->
                list.firstOrNull { it.id == id }?.let { it.name.ifBlank { "${it.host}:${it.port}" } }
            }
            .stateInWhileSubscribed(viewModelScope, null)
    val currentProfile = proxyFacade.currentProfile
    val trafficNow = proxyFacade.trafficNow
    val proxyGroups = proxyFacade.proxyGroups

    private val _proxyMode = MutableStateFlow(RunMode.VpnService)
    val proxyMode: StateFlow<RunMode> = _proxyMode.asStateFlow()

    private val _pendingTransition = MutableStateFlow(PendingTransition.None)
    private var pendingStartRequest: PendingStartRequest? = null

    private val _vpnPrepareIntent =
        MutableSharedFlow<Intent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val vpnPrepareIntent = _vpnPrepareIntent.asSharedFlow()

    val controlState: StateFlow<HomeProxyControlState> =
        combine(runtimeSnapshot, _pendingTransition) { snapshot, pendingTransition ->
                resolveControlState(snapshot.owner, snapshot.phase, pendingTransition)
            }
            .stateInWhileSubscribed(
                viewModelScope,
                resolveControlState(
                    runtimeSnapshot.value.owner,
                    runtimeSnapshot.value.phase,
                    _pendingTransition.value,
                ),
            )

    private val _speedHistory = MutableStateFlow<List<Long>>(emptyList())
    val speedHistory: StateFlow<List<Long>> = _speedHistory.asStateFlow()
    private var reconcileJob: Job? = null

    private val mainProxyNode: StateFlow<com.github.yumelira.yumebox.core.model.Proxy?> =
        proxyFacade.resolvedPrimaryNode

    val selectedServerName: StateFlow<String?> =
        mainProxyNode
            .map { it?.name }
            .stateInWhileSubscribed(viewModelScope, null)

    val selectedServerPing: StateFlow<Int?> =
        mainProxyNode
            .map { node -> node?.delay?.takeIf { delay -> delay > 0 } }
            .stateInWhileSubscribed(viewModelScope, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ipMonitoringState: StateFlow<IpMonitoringState> =
        isRunning
            .flatMapLatest { running ->
                if (running) {
                    networkInfoService.startIpMonitoring(
                        isProxyActiveFlow = isRunning,
                        externalRefreshFlow =
                            PollingTimers.ticks(PollingTimerSpecs.HomeIpRefresh).map { Unit },
                    )
                } else {
                    flowOf(IpMonitoringState.Loading)
                }
            }
            .stateInWhileSubscribed(viewModelScope, IpMonitoringState.Loading)

    val screenState: StateFlow<HomeScreenState> =
        combine(
                combine(controlState, trafficNow, profiles, profilesLoaded, hasEnabledProfile) {
                    control,
                    traffic,
                    profileList,
                    loaded,
                    hasEnabled ->
                    HomeScreenState(
                        controlState = control,
                        trafficNow = traffic,
                        profiles = profileList,
                        profilesLoaded = loaded,
                        hasEnabledProfile = hasEnabled,
                    )
                },
                combine(recommendedProfile, currentProfile, selectedServerName, selectedServerPing, speedHistory) {
                    recommended,
                    current,
                    serverName,
                    serverPing,
                    history ->
                    Array(5) { i ->
                        when (i) {
                            0 -> recommended
                            1 -> current
                            2 -> serverName
                            3 -> serverPing
                            else -> history
                        }
                    }
                },
                combine(proxyMode, isRemoteController, controllerBackendName, ipMonitoringState, uiState) {
                    mode,
                    remote,
                    backendName,
                    ipState,
                    ui ->
                    Array(5) { i ->
                        when (i) {
                            0 -> mode
                            1 -> remote
                            2 -> backendName
                            3 -> ipState
                            else -> ui
                        }
                    }
                },
            ) { base, mid, tail ->
                val recommended = mid[0] as Profile?
                val current = mid[1] as Profile?
                val serverName = mid[2] as String?
                val serverPing = mid[3] as Int?
                @Suppress("UNCHECKED_CAST")
                val history = mid[4] as List<Long>
                val mode = tail[0] as RunMode
                val remote = tail[1] as Boolean
                val backendName = tail[2] as String?
                val ipState = tail[3] as IpMonitoringState
                val ui = tail[4] as HomeUiState
                base.copy(
                    recommendedProfile = recommended,
                    currentProfile = current,
                    selectedServerName = serverName,
                    selectedServerPing = serverPing,
                    speedHistory = history,
                    proxyMode = mode,
                    isRemoteController = remote,
                    controllerBackendName = backendName,
                    ipMonitoringState = ipState,
                    uiMessage = ui.message,
                    uiError = ui.error,
                )
            }
            .combine(runtimeSnapshot) { screen, snapshot ->
                screen.copy(runtimeStartedAt = snapshot.startedAt)
            }
            .stateInWhileSubscribed(viewModelScope, HomeScreenState())

    init {
        refreshProfiles()
        reconcileRuntimeState()
        observeControlState()
        observeRuntimeState()
        observeRuntimeFailures()
        syncProxyModeState()
        startSpeedSampling()
        observeProfileChanges()
    }

    // Fault barrier: repository failure only logs and marks profiles as loaded (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    private fun refreshProfiles() {
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
                    _speedHistory.value = List(24) { 0L }
                }
                _uiState.update {
                    it.copy(
                        isStartingProxy = state == HomeProxyControlState.Connecting,
                        loadingProgress =
                            if (state == HomeProxyControlState.Connecting) {
                                YumeTxt.Home.Message.Preparing
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }

    private fun observeRuntimeState() {
        viewModelScope.launch {
            runtimeSnapshot
                .map { it.phase }
                .distinctUntilChanged()
                .collect { phase ->
                    when (phase) {
                        RuntimePhase.Starting -> {
                            clearPendingStart()
                            if (
                                _pendingTransition.value == PendingTransition.AwaitingPermission ||
                                    _pendingTransition.value == PendingTransition.Starting
                            ) {
                                _pendingTransition.value = PendingTransition.None
                            }
                        }

                        RuntimePhase.Running -> {
                            clearPendingStart()
                            if (
                                _pendingTransition.value == PendingTransition.Starting ||
                                    _pendingTransition.value == PendingTransition.AwaitingPermission
                            ) {
                                _pendingTransition.value = PendingTransition.None
                            }
                        }

                        RuntimePhase.Stopping -> {
                            clearPendingStart()
                            if (_pendingTransition.value == PendingTransition.Stopping) {
                                _pendingTransition.value = PendingTransition.None
                            }
                        }

                        RuntimePhase.Idle,
                        RuntimePhase.Failed -> {
                            clearPendingStart()
                            _pendingTransition.value = PendingTransition.None
                        }
                    }
                }
        }
    }

    private fun syncProxyModeState() {
        viewModelScope.launch {
            runtimeSnapshot
                .map {
                    RuntimeStateMapper.resolveDisplayMode(it, networkSettingsStore.runMode.value)
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
        val configuredMode = networkSettingsStore.runMode.value
        _proxyMode.value =
            RuntimeStateMapper.resolveDisplayMode(runtimeSnapshot.value, configuredMode)
    }

    fun setHomeScreenActive(isActive: Boolean) {
        proxyFacade.setProxyGroupSyncPriority(
            priority = if (isActive) ProxyGroupSyncPriority.FAST else ProxyGroupSyncPriority.OFF,
            source = "home",
        )
    }

    fun reconcileRuntimeState() {
        if (reconcileJob?.isActive == true) return
        // Off-main: reconcile touches the persisted runtime state (MMKV) and the active-profile
        // query; keeping it off the main thread avoids hitching navigation.
        reconcileJob = viewModelScope.launch(Dispatchers.IO) {
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

    // Fault barrier: any reload failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    suspend fun reloadProfile() {
        try {
            applyLoading(true)

            val activeProfile = profilesRepository.queryActiveProfile()
            if (activeProfile == null) {
                showError(
                    YumeTxt.Home.Message.ConfigSwitchFailed.format(
                        YumeTxt.ProfilesVM.Error.ProfileNotExist
                    )
                )
                return
            }

            profilesRepository.updateProfile(activeProfile.uuid)

            profilesRepository.setActiveProfile(activeProfile.uuid)
            showMessage(YumeTxt.Home.Message.ConfigSwitched)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.e(error, "Failed to reload profile")
            showError(YumeTxt.Home.Message.ConfigSwitchFailed.format(error.message))
        } finally {
            applyLoading(false)
        }
    }

    fun isCurrentProfile(profileId: java.util.UUID): Boolean =
        currentProfile.value?.uuid == profileId

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
        _pendingTransition.value = PendingTransition.Starting

        viewModelScope.launch { startProxyInternal(request) }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val request = pendingStartRequest ?: return
        if (_pendingTransition.value != PendingTransition.AwaitingPermission) return

        if (!granted) {
            clearPendingStart()
            _pendingTransition.value = PendingTransition.None
            refreshProxyMode()
            return
        }

        _pendingTransition.value = PendingTransition.Starting
        viewModelScope.launch { startProxyInternal(request) }
    }

    // Fault barrier: any stop failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    suspend fun stopProxy() {
        if (
            !controlState.value.canInteract || controlState.value != HomeProxyControlState.Running
        ) {
            return
        }

        _pendingTransition.value = PendingTransition.Stopping

        try {
            withContext(Dispatchers.IO) {
                AutoStartSessionGate.markManualPaused()
                proxyFacade.stopProxy()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _pendingTransition.value = PendingTransition.None
            Timber.e(error, "Failed to stop proxy")
            showError(YumeTxt.Home.Message.StopFailed.format(error.message))
        }
    }

    private fun startSpeedSampling(sampleLimit: Int = 24) {
        viewModelScope.launch {
            PollingTimers.ticks(PollingTimerSpecs.HomeSpeedSampling).collect {
                val snapshot = runtimeSnapshot.value
                val sample =
                    when {
                        snapshot.phase == RuntimePhase.Idle ||
                            snapshot.phase == RuntimePhase.Failed -> 0L
                        snapshot.phase.running -> {
                            val t = proxyFacade.trafficNow.value
                            val d = TrafficData.from(t)
                            (d.upload + d.download).coerceAtLeast(0L)
                        }

                        else -> 0L
                    }
                _speedHistory.update { old ->
                    buildList(sampleLimit) {
                        repeat((sampleLimit - old.size - 1).coerceAtLeast(0)) { add(0L) }
                        addAll(old.takeLast(sampleLimit - 1))
                        add(sample)
                    }
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

    // Fault barrier: any start failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    private suspend fun startProxyInternal(request: PendingStartRequest) {
        val startedAt = System.currentTimeMillis()
        try {
            _proxyMode.value = request.mode
            Timber.d("Home startProxy kickoff: mode=${request.mode} profileId=${request.profileId}")

            withContext(Dispatchers.IO) {
                if (request.profileId.isNotBlank()) {
                    profilesRepository.setActiveProfile(
                        java.util.UUID.fromString(request.profileId)
                    )
                }

                AutoStartSessionGate.clearManualPaused()
                proxyFacade.startProxy(request.mode)
            }

            Timber.i(
                "Home startProxy completed in ${System.currentTimeMillis() - startedAt}ms, mode=${request.mode}"
            )

            // If the remote controller is active, ProxyFacade.startProxy() is a no-op and no runtime
            // phase change will arrive to reset the pending transition — clear it so the home button
            // doesn't stick on "Connecting" forever.
            if (proxyFacade.isRemoteControllerActive()) {
                clearPendingStart()
                _pendingTransition.value = PendingTransition.None
            }
        } catch (error: com.github.yumelira.yumebox.runtime.api.VpnPermissionRequired) {
            _pendingTransition.value = PendingTransition.AwaitingPermission
            _vpnPrepareIntent.emit(error.intent)
            Timber.i("VPN permission required")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            clearPendingStart()
            _pendingTransition.value = PendingTransition.None
            Timber.e(error, "Failed to start proxy")
            showError(YumeTxt.Home.Message.StartFailed.format(error.message))
        }
    }

    private fun clearPendingStart() {
        pendingStartRequest = null
    }

    private fun resolveControlState(
        owner: RuntimeOwner,
        phase: RuntimePhase,
        pendingTransition: PendingTransition,
    ): HomeProxyControlState {
        if (owner == RuntimeOwner.RemoteController && phase == RuntimePhase.Failed) {
            return HomeProxyControlState.Lost
        }
        val phaseStillActive =
            phase != RuntimePhase.Stopping &&
                phase != RuntimePhase.Idle &&
                phase != RuntimePhase.Failed
        if (pendingTransition == PendingTransition.Stopping && phaseStillActive) {
            return HomeProxyControlState.Disconnecting
        }
        return when (phase) {
            RuntimePhase.Running -> HomeProxyControlState.Running
            RuntimePhase.Starting -> HomeProxyControlState.Connecting
            RuntimePhase.Stopping -> HomeProxyControlState.Disconnecting
            RuntimePhase.Idle,
            RuntimePhase.Failed ->
                when (pendingTransition) {
                    PendingTransition.AwaitingPermission,
                    PendingTransition.Starting -> HomeProxyControlState.Connecting
                    PendingTransition.Stopping -> HomeProxyControlState.Idle
                    PendingTransition.None -> HomeProxyControlState.Idle
                }
        }
    }

    private data class PendingStartRequest(
        val profileId: String,
        val mode: RunMode,
    )

    /** Aggregated home page snapshot — Screen should collect this once. */
    data class HomeScreenState(
        val controlState: HomeProxyControlState = HomeProxyControlState.Idle,
        val trafficNow: com.github.yumelira.yumebox.core.model.Traffic = 0L,
        val profiles: List<Profile> = emptyList(),
        val profilesLoaded: Boolean = false,
        val hasEnabledProfile: Boolean = false,
        val recommendedProfile: Profile? = null,
        val currentProfile: Profile? = null,
        val selectedServerName: String? = null,
        val selectedServerPing: Int? = null,
        val speedHistory: List<Long> = emptyList(),
        val proxyMode: RunMode = RunMode.VpnService,
        val isRemoteController: Boolean = false,
        val controllerBackendName: String? = null,
        val ipMonitoringState: IpMonitoringState = IpMonitoringState.Loading,
        val uiMessage: String? = null,
        val uiError: String? = null,
        val runtimeStartedAt: Long? = null,
    )

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
