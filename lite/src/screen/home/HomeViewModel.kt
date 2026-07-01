package com.github.yumelira.yumebox.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.NetworkInfoReader
import com.github.yumelira.yumebox.core.domain.model.TrafficData
import com.github.yumelira.yumebox.core.model.IpMonitoringState
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.lite.config.TunProfileSync
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.ProxyGroupSyncPriority
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import com.github.yumelira.yumebox.runtime.client.VpnProxyController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

enum class HomeControlState {
    Idle,
    Connecting,
    Running,
    Disconnecting,
}

class HomeViewModel(
    application: Application,
    private val proxyFacade: ProxyFacade,
    private val profilesRepository: ProfilesRepository,
    private val networkInfoService: NetworkInfoReader,
    private val tunProfileSync: TunProfileSync,
) : AndroidViewModel(application) {
    private val vpnController = VpnProxyController(viewModelScope, proxyFacade)
    private val fallbackProfile = MutableStateFlow<Profile?>(null)
    val messages = vpnController.messages
    val vpnPrepareIntent = vpnController.vpnPrepareIntent

    val runtimeSnapshot = proxyFacade.runtimeSnapshot
    val isRunning: StateFlow<Boolean> =
        runtimeSnapshot
            .map(RuntimeStateMapper::isActuallyRunning)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RuntimeStateMapper.isActuallyRunning(runtimeSnapshot.value),
            )

    val currentProfile: StateFlow<Profile?> =
        combine(proxyFacade.currentProfile, fallbackProfile) { runtimeProfile, previewProfile ->
                runtimeProfile ?: previewProfile
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val trafficData: StateFlow<TrafficData> =
        proxyFacade.trafficNow
            .map(TrafficData::from)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrafficData.ZERO)

    val selectedServerName: StateFlow<String?> =
        proxyFacade.resolvedPrimaryNode
            .map { it?.name }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedServerPing: StateFlow<Int?> =
        proxyFacade.resolvedPrimaryNode
            .map { node -> node?.delay?.takeIf { it > 0 } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ipMonitoringState: StateFlow<IpMonitoringState> =
    isRunning
        .flatMapLatest { running ->
            if (!running) {
                flowOf(IpMonitoringState.Loading)
            } else {
                networkInfoService.startIpMonitoring(
                    isProxyActiveFlow = isRunning,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IpMonitoringState.Loading)

    val controlState: StateFlow<HomeControlState> = combine(
        runtimeSnapshot,
        vpnController.pendingTransition,
    ) { snapshot, pending ->
        resolveControlState(snapshot, pending)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        resolveControlState(runtimeSnapshot.value, vpnController.pendingTransition.value),
    )

    init {
        refresh()
        vpnController.observeRuntimeState()
        observeProfileChanges()
    }

    fun setActive(active: Boolean) {
        proxyFacade.setProxyGroupSyncPriority(
            priority = if (active) ProxyGroupSyncPriority.FAST else ProxyGroupSyncPriority.OFF,
            source = "lite_home",
        )
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                tunProfileSync.syncActiveProfile()
                proxyFacade.reconcileRuntimeState()
                fallbackProfile.value = profilesRepository.queryActiveProfile()
                networkInfoService.triggerRefresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.w(error, "Failed to refresh lite home")
            }
        }
    }

    fun startProxy() {
        if (controlState.value != HomeControlState.Idle) return
        if (currentProfile.value == null) {
            vpnController.emitMessage("先导入并激活一个配置")
            return
        }

        vpnController.startProxy(
            mode = ProxyMode.Tun,
            onPreStart = { tunProfileSync.syncActiveProfile() },
        )
    }

    fun onVpnPermissionResult(granted: Boolean) {
        vpnController.onVpnPermissionResult(granted) { startProxy() }
    }

    fun stopProxy() {
        if (controlState.value != HomeControlState.Running) return
        vpnController.stopProxy(mode = ProxyMode.Tun)
    }

    private fun observeProfileChanges() {
        viewModelScope.launch {
            proxyFacade.currentProfile
                .map { it?.uuid }
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    try {
                        fallbackProfile.value = profilesRepository.queryActiveProfile()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                    }
            }
        }
    }

    private fun resolveControlState(
        snapshot: RuntimeSnapshot,
        pending: VpnProxyController.PendingTransition,
    ): HomeControlState {
        return when (snapshot.phase) {
            RuntimePhase.Running -> HomeControlState.Running
            RuntimePhase.Starting -> HomeControlState.Connecting
            RuntimePhase.Stopping -> HomeControlState.Disconnecting
            RuntimePhase.Idle,
            RuntimePhase.Failed ->
                when (pending) {
                    VpnProxyController.PendingTransition.AwaitingPermission,
                    VpnProxyController.PendingTransition.Starting -> HomeControlState.Connecting

                    VpnProxyController.PendingTransition.Stopping -> HomeControlState.Disconnecting
                    VpnProxyController.PendingTransition.None -> HomeControlState.Idle
                }
        }
    }
}
