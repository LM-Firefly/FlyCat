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

package com.github.yumelira.yumebox.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.common.util.stateInWhileSubscribed
import com.github.yumelira.yumebox.data.controller.NetworkSettingsController
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.data.model.AccessControlMode
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.data.model.TunStack
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.Preference
import com.github.yumelira.yumebox.runtime.service.root.RootAccessSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Backs both the run-mode picker ([NetworkSettingsScreen]) and the VpnService options page
 * ([VpnServiceOptionsScreen]). The TCP/IP stack is fixed to gVisor and the parallel HTTP run mode is
 * gone, so this only exposes the configured mode plus the VpnService knobs.
 */
class NetworkSettingsViewModel(
    application: Application,
    settings: NetworkSettingsStore,
    private val controller: NetworkSettingsController,
) : AndroidViewModel(application) {
    val runMode: Preference<RunMode> = settings.runMode
    val bypassPrivateNetwork: Preference<Boolean> = settings.bypassPrivateNetwork
    val dnsHijack: Preference<Boolean> = settings.dnsHijack
    val allowBypass: Preference<Boolean> = settings.allowBypass
    val enableIPv6: Preference<Boolean> = settings.enableIPv6
    val systemProxy: Preference<Boolean> = settings.systemProxy
    val disableAllOverride: Preference<Boolean> = settings.disableAllOverride
    val accessControlMode: Preference<AccessControlMode> = settings.accessControlMode

    // Root Tun geometry — its own service-config sub-page ([TunServiceOptionsScreen]).
    val tunStack: Preference<TunStack> = settings.tunStack
    val tunAutoRoute: Preference<Boolean> = settings.tunAutoRoute
    val tunStrictRoute: Preference<Boolean> = settings.tunStrictRoute
    val tunAutoRedirect: Preference<Boolean> = settings.tunAutoRedirect
    val tunDnsMode: Preference<TunDnsMode> = settings.tunDnsMode
    val tunIfName: Preference<String> = settings.tunIfName
    val tunMtu: Preference<Int> = settings.tunMtu
    val tproxyPort: Preference<Int> = settings.tproxyPort

    // Root availability, probed once on open — gates the Tun / TPROXY cards (greyed when false, no toast).
    // Probing constructs the libsu shell, so a rooted device may surface its su prompt here; that grant
    // flow is intended. A non-rooted device fast-fails to false and the root cards stay disabled.
    private val _rootAvailable = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            _rootAvailable.value = RootAccessSupport.evaluateAsync(getApplication()).canStartRoot
        }
    }

    val uiState: StateFlow<NetworkSettingsUiState> =
        runMode.state
            .map { NetworkSettingsUiState(configuredMode = it) }
            .distinctUntilChanged()
            .stateInWhileSubscribed(viewModelScope, NetworkSettingsUiState())

    val tunServiceOptionsUiState: StateFlow<TunServiceOptionsUiState> =
        combine(
                bypassPrivateNetwork.state,
                dnsHijack.state,
                enableIPv6.state,
                allowBypass.state,
                systemProxy.state,
            ) { bypassPrivateNetwork, dnsHijack, enableIPv6, allowBypass, systemProxy ->
                TunServiceOptionsUiState(
                    common =
                        CommonTunOptionsUiState(
                            bypassPrivateNetwork = bypassPrivateNetwork,
                            dnsHijack = dnsHijack,
                            enableIPv6 = enableIPv6,
                        ),
                    allowBypass = allowBypass,
                    systemProxy = systemProxy,
                )
            }
            .stateInWhileSubscribed(
                viewModelScope,
                TunServiceOptionsUiState(
                    common =
                        CommonTunOptionsUiState(
                            bypassPrivateNetwork = bypassPrivateNetwork.value,
                            dnsHijack = dnsHijack.value,
                            enableIPv6 = enableIPv6.value,
                        ),
                    allowBypass = allowBypass.value,
                    systemProxy = systemProxy.value,
                ),
            )

    /**
     * Selects the run mode — the single mode key across the runtime. The root Tun / TPROXY cards are
     * disabled in the UI when [rootAvailable] is false, so reaching here for a root mode already implies
     * root was granted.
     */
    fun onRunModeChange(mode: RunMode) {
        controller.setRunMode(mode)
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

    fun onTunStackChange(value: TunStack) = controller.setAndRestartIfNeeded(tunStack, value)

    fun onTunAutoRouteChange(enabled: Boolean) =
        controller.setAndRestartIfNeeded(tunAutoRoute, enabled)

    fun onTunStrictRouteChange(enabled: Boolean) =
        controller.setAndRestartIfNeeded(tunStrictRoute, enabled)

    fun onTunAutoRedirectChange(enabled: Boolean) =
        controller.setAndRestartIfNeeded(tunAutoRedirect, enabled)

    fun onTunDnsModeChange(value: TunDnsMode) =
        controller.setAndRestartIfNeeded(tunDnsMode, value)

    fun onTunIfNameChange(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) controller.setAndRestartIfNeeded(tunIfName, trimmed)
    }

    fun onTunMtuChange(value: Int) {
        if (value in 576..9000) controller.setAndRestartIfNeeded(tunMtu, value)
    }

    fun onTproxyPortChange(value: Int) {
        if (value in 1..65535) controller.setAndRestartIfNeeded(tproxyPort, value)
    }

    fun onDisableAllOverrideChange(enabled: Boolean) {
        controller.setAndRestartIfNeeded(disableAllOverride, enabled)
    }

    fun onAccessControlModeChange(mode: AccessControlMode) {
        controller.setAndRestartIfNeeded(accessControlMode, mode)
    }
}

data class NetworkSettingsUiState(
    val configuredMode: RunMode = RunMode.VpnService,
)

data class CommonTunOptionsUiState(
    val bypassPrivateNetwork: Boolean = false,
    val dnsHijack: Boolean = false,
    val enableIPv6: Boolean = false,
)

data class TunServiceOptionsUiState(
    val common: CommonTunOptionsUiState = CommonTunOptionsUiState(),
    val allowBypass: Boolean = false,
    val systemProxy: Boolean = false,
)
