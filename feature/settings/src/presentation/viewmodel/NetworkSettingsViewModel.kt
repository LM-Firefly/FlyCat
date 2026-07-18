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

package com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsControllerContract
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.Preference
import com.github.lmfirefly.flycat.core.model.AccessControlMode
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunDnsMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunStack
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeStateMapper
import com.github.lmfirefly.flycat.runtime.api.root.RootAccessStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkSettingsViewModel(
    application: Application,
    private val settings: NetworkSettingsReader,
    private val controller: NetworkSettingsControllerContract,
    private val proxyFacade: ProxyControlContract,
) : AndroidViewModel(application) {

    val runMode: Preference<RunMode> = settings.runMode
    val bypassPrivateNetwork: Preference<Boolean> = settings.bypassPrivateNetwork
    val dnsHijack: Preference<Boolean> = settings.dnsHijack
    val allowBypass: Preference<Boolean> = settings.allowBypass
    val enableIPv6: Preference<Boolean> = settings.enableIPv6
    val systemProxy: Preference<Boolean> = settings.systemProxy
    val disableAllOverride: Preference<Boolean> = settings.disableAllOverride
    val accessControlMode: Preference<AccessControlMode> = settings.accessControlMode

    val tunStack: Preference<TunStack> = settings.tunStack
    val tunAutoRoute: Preference<Boolean> = settings.tunAutoRoute
    val tunStrictRoute: Preference<Boolean> = settings.tunStrictRoute
    val tunAutoRedirect: Preference<Boolean> = settings.tunAutoRedirect
    val tunDnsMode: Preference<TunDnsMode> = settings.tunDnsMode
    private val tunIfName = settings.tunIfName
    private val tunMtu = settings.tunMtu
    private val tunFakeIpRange = settings.tunFakeIpRange
    private val tunFakeIpRange6 = settings.tunFakeIpRange6

    private val _tunIfNameDraft = MutableStateFlow(tunIfName.value)
    val tunIfNameDraft: StateFlow<String> = _tunIfNameDraft.asStateFlow()

    private val _tunMtuDraft = MutableStateFlow(tunMtu.value.toString())
    val tunMtuDraft: StateFlow<String> = _tunMtuDraft.asStateFlow()

    private val _tunFakeIpRangeDraft = MutableStateFlow(tunFakeIpRange.value)
    val tunFakeIpRangeDraft: StateFlow<String> = _tunFakeIpRangeDraft.asStateFlow()

    private val _tunFakeIpRange6Draft = MutableStateFlow(tunFakeIpRange6.value)
    val tunFakeIpRange6Draft: StateFlow<String> = _tunFakeIpRange6Draft.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    private val _ebpfAvailable = MutableStateFlow(false)
    val ebpfAvailable: StateFlow<Boolean> = _ebpfAvailable.asStateFlow()

    private val runtimeSnapshot = proxyFacade.runtimeSnapshot

    val serviceState: StateFlow<ServiceState> =
        runtimeSnapshot
            .map { snapshot -> ServiceState.fromPhase(snapshot.phase) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceState.Idle)

    val currentRunMode: StateFlow<RunMode> = runMode.state

    init {
        viewModelScope.launch {
            val rootStatus = proxyFacade.evaluateRootAccess()
            _rootAvailable.value = rootStatus.canStartRootTun
            // eBPF requires root + cgroup v2 + BPF capability probe
            if (rootStatus.canStartRootTun) {
                val cgroupPath = com.github.lmfirefly.flycat.runtime.service.root.EbpfCgroupSupport.rootCgroupPath()
                _ebpfAvailable.value = cgroupPath != null &&
                    withContext(Dispatchers.IO) {
                        com.github.lmfirefly.flycat.runtime.service.root.EbpfBridgeProcess.isCapabilityAvailable(
                            getApplication(), cgroupPath
                        )
                    }
            }
        }
    }

    val uiState: StateFlow<NetworkSettingsUiState> =
        combine(currentRunMode, runtimeSnapshot, tunDnsMode.state, rootAvailable, ebpfAvailable) {
                configuredMode,
                snapshot,
                dnsMode,
                rootAvail,
                ebpfAvail ->
                val effectiveMode = RuntimeStateMapper.resolveDisplayMode(snapshot, configuredMode)
                val activeMode = RuntimeStateMapper.modeForOwner(snapshot.owner)
                NetworkSettingsUiState(
                    serviceState = ServiceState.fromPhase(snapshot.phase),
                    configuredMode = configuredMode,
                    effectiveMode = effectiveMode,
                    needsRestart = snapshot.phase == RuntimePhase.Running && activeMode != configuredMode,
                    rootAvailable = rootAvail,
                    ebpfAvailable = ebpfAvail,
                    showServiceOptions = true,
                    showTunOnlyOptions = configuredMode == RunMode.VpnService,
                    showAccessControlMode = true,
                    showRootTunAdvanced = configuredMode == RunMode.Tun,
                    showFakeIpRange = configuredMode == RunMode.Tun && dnsMode == TunDnsMode.FakeIp,
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = NetworkSettingsUiState(),
            )

    private val commonTunOptionsUiState: StateFlow<CommonTunOptionsUiState> =
        combine(bypassPrivateNetwork.state, dnsHijack.state, enableIPv6.state, tunStack.state) {
                bypassPrivateNetwork,
                dnsHijack,
                enableIPv6,
                tunStack ->
                CommonTunOptionsUiState(
                    bypassPrivateNetwork = bypassPrivateNetwork,
                    dnsHijack = dnsHijack,
                    enableIPv6 = enableIPv6,
                    tunStack = tunStack,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    CommonTunOptionsUiState(
                        bypassPrivateNetwork = bypassPrivateNetwork.value,
                        dnsHijack = dnsHijack.value,
                        enableIPv6 = enableIPv6.value,
                        tunStack = tunStack.value,
                    ),
            )

    val tunServiceOptionsUiState: StateFlow<TunServiceOptionsUiState> =
        combine(commonTunOptionsUiState, allowBypass.state, systemProxy.state) {
                common,
                allowBypass,
                systemProxy ->
                TunServiceOptionsUiState(
                    common = common,
                    allowBypass = allowBypass,
                    systemProxy = systemProxy,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    TunServiceOptionsUiState(
                        common =
                            CommonTunOptionsUiState(
                                bypassPrivateNetwork = bypassPrivateNetwork.value,
                                dnsHijack = dnsHijack.value,
                                enableIPv6 = enableIPv6.value,
                                tunStack = tunStack.value,
                            ),
                        allowBypass = allowBypass.value,
                        systemProxy = systemProxy.value,
                    ),
            )

    private val rootTunRoutingUiState =
        combine(
            tunAutoRoute.state,
            tunStrictRoute.state,
            tunAutoRedirect.state,
            tunDnsMode.state,
        ) { tunAutoRoute, tunStrictRoute, tunAutoRedirect, tunDnsMode ->
            RootTunRoutingUiState(
                tunAutoRoute = tunAutoRoute,
                tunStrictRoute = tunStrictRoute,
                tunAutoRedirect = tunAutoRedirect,
                tunDnsMode = tunDnsMode,
            )
        }

    private val rootTunDraftsUiState =
        combine(
            tunIfNameDraft,
            tunMtuDraft,
            tunFakeIpRangeDraft,
            tunFakeIpRange6Draft,
        ) { tunIfNameDraft, tunMtuDraft, tunFakeIpRangeDraft, tunFakeIpRange6Draft
            ->
            RootTunDraftsUiState(
                tunIfNameDraft = tunIfNameDraft,
                tunMtuDraft = tunMtuDraft,
                tunFakeIpRangeDraft = tunFakeIpRangeDraft,
                tunFakeIpRange6Draft = tunFakeIpRange6Draft,
            )
        }

    val rootTunServiceOptionsUiState: StateFlow<RootTunServiceOptionsUiState> =
        combine(commonTunOptionsUiState, rootTunRoutingUiState, rootTunDraftsUiState) {
                common,
                routing,
                drafts ->
                RootTunServiceOptionsUiState(
                    common = common,
                    tunAutoRoute = routing.tunAutoRoute,
                    tunStrictRoute = routing.tunStrictRoute,
                    tunAutoRedirect = routing.tunAutoRedirect,
                    tunDnsMode = routing.tunDnsMode,
                    tunIfNameDraft = drafts.tunIfNameDraft,
                    tunMtuDraft = drafts.tunMtuDraft,
                    tunFakeIpRangeDraft = drafts.tunFakeIpRangeDraft,
                    tunFakeIpRange6Draft = drafts.tunFakeIpRange6Draft,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    RootTunServiceOptionsUiState(
                        common = commonTunOptionsUiState.value,
                        tunAutoRoute = tunAutoRoute.value,
                        tunStrictRoute = tunStrictRoute.value,
                        tunAutoRedirect = tunAutoRedirect.value,
                        tunDnsMode = tunDnsMode.value,
                        tunIfNameDraft = tunIfNameDraft.value,
                        tunMtuDraft = tunMtuDraft.value,
                        tunFakeIpRangeDraft = tunFakeIpRangeDraft.value,
                        tunFakeIpRange6Draft = tunFakeIpRange6Draft.value,
                    ),
            )

    fun onRunModeChange(mode: RunMode) {
        controller.setRunMode(mode)
    }

    suspend fun evaluateRootAccess(): RootAccessStatus {
        return proxyFacade.evaluateRootAccess()
    }

    fun onBypassPrivateNetworkChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(bypassPrivateNetwork, enabled)
    }

    fun onDnsHijackChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(dnsHijack, enabled)
    }

    fun onAllowBypassChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(allowBypass, enabled)
    }

    fun onEnableIPv6Change(enabled: Boolean) {
        controller.setAndRestartIfNeeded(enableIPv6, enabled)
    }

    fun onSystemProxyChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(systemProxy, enabled)
    }

    fun onDisableAllOverrideChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(disableAllOverride, enabled)
    }

    fun onTunStackChange(stack: TunStack) {
        controller.setAndRestartIfNeeded(tunStack, stack)
    }

    fun onTunAutoRouteChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(tunAutoRoute, enabled)
    }

    fun onTunStrictRouteChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(tunStrictRoute, enabled)
    }

    fun ontunAutoRedirectChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(tunAutoRedirect, enabled)
    }

    fun ontunDnsModeChange(mode: TunDnsMode) {
        controller.setAndRestartIfNeeded(tunDnsMode, mode)
    }

    fun onAccessControlModeChange(mode: AccessControlMode) {
        controller.setAndRestartIfNeeded(accessControlMode, mode)
    }

    fun ontunIfNameDraftChange(value: String) {
        _tunIfNameDraft.value = value
    }

    fun committunIfName() {
        val normalized = _tunIfNameDraft.value.trim().ifBlank { DEFAULT_ROOT_TUN_IF_NAME }
        _tunIfNameDraft.value = normalized
        controller.setAndRestartIfNeeded(tunIfName, normalized)
    }

    fun ontunMtuDraftChange(value: String) {
        _tunMtuDraft.value = value
    }

    fun committunMtu() {
        val parsed = _tunMtuDraft.value.trim().toIntOrNull()?.takeIf { it > 0 } ?: return
        _tunMtuDraft.value = parsed.toString()
        controller.setAndRestartIfNeeded(tunMtu, parsed)
    }

    fun ontunFakeIpRangeDraftChange(value: String) {
        _tunFakeIpRangeDraft.value = value
    }

    fun committunFakeIpRange() {
        val normalized = _tunFakeIpRangeDraft.value.trim().ifBlank { DEFAULT_FAKE_IP_RANGE }
        _tunFakeIpRangeDraft.value = normalized
        controller.setAndRestartIfNeeded(tunFakeIpRange, normalized)
    }

    fun ontunFakeIpRange6DraftChange(value: String) {
        _tunFakeIpRange6Draft.value = value
    }

    fun committunFakeIpRange6() {
        val normalized = _tunFakeIpRange6Draft.value.trim().ifBlank { DEFAULT_FAKE_IP_RANGE6 }
        _tunFakeIpRange6Draft.value = normalized
        controller.setAndRestartIfNeeded(tunFakeIpRange6, normalized)
    }

    fun startService(mode: RunMode) {
        viewModelScope.launch {
            switchService(mode).onFailure { error ->
                _errors.tryEmit(error.message ?: "Failed to start proxy service")
            }
        }
    }

    fun restartService() {
        viewModelScope.launch {
            if (!RuntimeStateMapper.isActuallyRunning(runtimeSnapshot.value)) return@launch
            switchService(runMode.value).onFailure { error ->
                _errors.tryEmit(error.message ?: "Failed to restart proxy service")
            }
        }
    }

    private suspend fun switchService(mode: RunMode): Result<Unit> = runCatching {
        controller.startService(mode).getOrThrow()
    }

    companion object {
        private const val DEFAULT_ROOT_TUN_IF_NAME = "Yume"
        private const val DEFAULT_FAKE_IP_RANGE = "198.18.0.1/16"
        private const val DEFAULT_FAKE_IP_RANGE6 = "fc00::/18"
    }
}

data class NetworkSettingsUiState(
    val serviceState: ServiceState = ServiceState.Idle,
    val configuredMode: RunMode = RunMode.VpnService,
    val effectiveMode: RunMode = RunMode.VpnService,
    val needsRestart: Boolean = false,
    val rootAvailable: Boolean = false,
    val ebpfAvailable: Boolean = false,
    val showServiceOptions: Boolean = true,
    val showTunOnlyOptions: Boolean = true,
    val showAccessControlMode: Boolean = true,
    val showRootTunAdvanced: Boolean = false,
    val showFakeIpRange: Boolean = false,
)

data class CommonTunOptionsUiState(
    val bypassPrivateNetwork: Boolean = false,
    val dnsHijack: Boolean = false,
    val enableIPv6: Boolean = false,
    val tunStack: TunStack = TunStack.System,
)

data class TunServiceOptionsUiState(
    val common: CommonTunOptionsUiState = CommonTunOptionsUiState(),
    val allowBypass: Boolean = false,
    val systemProxy: Boolean = false,
)

data class RootTunServiceOptionsUiState(
    val common: CommonTunOptionsUiState = CommonTunOptionsUiState(),
    val tunAutoRoute: Boolean = false,
    val tunStrictRoute: Boolean = false,
    val tunAutoRedirect: Boolean = false,
    val tunDnsMode: TunDnsMode = TunDnsMode.RedirHost,
    val tunIfNameDraft: String = "",
    val tunMtuDraft: String = "",
    val tunFakeIpRangeDraft: String = "",
    val tunFakeIpRange6Draft: String = "",
)

private data class RootTunRoutingUiState(
    val tunAutoRoute: Boolean,
    val tunStrictRoute: Boolean,
    val tunAutoRedirect: Boolean,
    val tunDnsMode: TunDnsMode,
)

private data class RootTunDraftsUiState(
    val tunIfNameDraft: String,
    val tunMtuDraft: String,
    val tunFakeIpRangeDraft: String,
    val tunFakeIpRange6Draft: String,
)

enum class ServiceState {
    Idle,
    Starting,
    Running,
    Stopping,
    Failed;

    companion object {
        fun fromPhase(phase: RuntimePhase): ServiceState =
            when (phase) {
                RuntimePhase.Idle -> Idle
                RuntimePhase.Starting -> Starting
                RuntimePhase.Running -> Running
                RuntimePhase.Stopping -> Stopping
                RuntimePhase.Failed -> Failed
            }
    }
}
