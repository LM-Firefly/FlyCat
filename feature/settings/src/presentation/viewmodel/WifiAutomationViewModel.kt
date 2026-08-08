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

package com.github.yumelira.yumebox.feature.settings.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.contract.NetworkSettingsReader
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.WifiAutomationAction
import com.github.yumelira.yumebox.core.model.WifiAutomationFallbackAction
import com.github.yumelira.yumebox.core.model.WifiAutomationRule
import com.github.yumelira.yumebox.runtime.service.WifiAutomationService
import com.github.yumelira.yumebox.runtime.service.session.WifiSsidObservation
import com.github.yumelira.yumebox.runtime.service.session.WifiSsidObserver
import com.github.yumelira.yumebox.runtime.service.session.WifiSsidScanResult
import com.github.yumelira.yumebox.runtime.service.session.WifiSsidScanner
import com.github.yumelira.yumebox.runtime.service.session.WifiSsidNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WifiAutomationViewModel(
    application: Application,
    private val settings: NetworkSettingsReader,
) : AndroidViewModel(application) {
    data class UiState(
        val enabled: Boolean = false,
        val rules: List<WifiAutomationRule> = emptyList(),
        val runMode: RunMode = RunMode.VpnService,
        val locationRequested: Boolean = false,
        val otherWifiAction: WifiAutomationFallbackAction = WifiAutomationFallbackAction.Keep,
        val noWifiAction: WifiAutomationFallbackAction = WifiAutomationFallbackAction.Keep,
        val isScanning: Boolean = false,
        val scanCompleted: Boolean = false,
        val scannedNetworks: List<WifiSsidNetwork> = emptyList(),
        val scanUnavailable: Boolean = false,
    )

    sealed interface Effect {
        data object AddedCurrentSsid : Effect
        data object SsidAlreadyExists : Effect
        data object NoWifi : Effect
        data object SsidUnavailable : Effect
        data object ScanUnavailable : Effect
    }

    private data class ScanState(
        val isScanning: Boolean = false,
        val scanCompleted: Boolean = false,
        val networks: List<WifiSsidNetwork> = emptyList(),
        val unavailable: Boolean = false,
    )

    private val scanState = MutableStateFlow(ScanState())
    private var scanJob: Job? = null

    private val settingsState =
        combine(
            settings.wifiAutomationEnabled.state,
            settings.wifiAutomationRules.state,
            settings.runMode.state,
            settings.wifiAutomationLocationRequested.state,
        ) { enabled, rules, runMode, locationRequested ->
            UiState(enabled, rules, runMode, locationRequested)
        }

    val uiState: StateFlow<UiState> =
        combine(
            settingsState,
            settings.wifiAutomationOtherWifiAction.state,
            settings.wifiAutomationNoWifiAction.state,
        ) { state, otherWifiAction, noWifiAction ->
            state.copy(otherWifiAction = otherWifiAction, noWifiAction = noWifiAction)
        }
            .combine(scanState) { state, scan ->
                state.copy(
                    isScanning = scan.isScanning,
                    scanCompleted = scan.scanCompleted,
                    scannedNetworks = scan.networks,
                    scanUnavailable = scan.unavailable,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UiState(
                    enabled = settings.wifiAutomationEnabled.value,
                    rules = settings.wifiAutomationRules.value,
                    runMode = settings.runMode.value,
                    locationRequested = settings.wifiAutomationLocationRequested.value,
                    otherWifiAction = settings.wifiAutomationOtherWifiAction.value,
                    noWifiAction = settings.wifiAutomationNoWifiAction.value,
                ),
            )

    private val _effects = MutableSharedFlow<Effect>()
    val effects = _effects.asSharedFlow()

    fun enable() {
        if (settings.runMode.value != RunMode.VpnService) return
        settings.wifiAutomationEnabled.set(true)
        WifiAutomationService.start(getApplication())
    }

    fun disable() {
        settings.wifiAutomationEnabled.set(false)
        WifiAutomationService.stop(getApplication())
    }

    fun markLocationPermissionRequested() {
        settings.wifiAutomationLocationRequested.set(true)
    }

    fun addManualSsid(
        rawSsid: String,
        action: WifiAutomationAction = WifiAutomationAction.Start,
    ) {
        val ssid = WifiSsidObserver.normalizeSsid(rawSsid) ?: return
        addRule(ssid, action)
    }

    fun scanWifi() {
        if (scanState.value.isScanning) return
        scanState.value = ScanState(isScanning = true)
        scanJob = viewModelScope.launch {
            when (val result = WifiSsidScanner.scanOnce(getApplication())) {
                is WifiSsidScanResult.Success -> {
                    scanState.value = ScanState(scanCompleted = true, networks = result.networks)
                }

                WifiSsidScanResult.Unavailable -> {
                    scanState.value = ScanState(scanCompleted = true, unavailable = true)
                    _effects.emit(Effect.ScanUnavailable)
                }
            }
        }
    }

    fun resetScan() {
        scanJob?.cancel()
        scanJob = null
        scanState.value = ScanState()
    }

    fun addCurrentSsid() {
        viewModelScope.launch {
            when (val result = WifiSsidObserver.readOnce(getApplication())) {
                is WifiSsidObservation.Connected -> addRule(result.ssid)
                WifiSsidObservation.NoWifi -> _effects.emit(Effect.NoWifi)
                WifiSsidObservation.Unavailable -> _effects.emit(Effect.SsidUnavailable)
            }
        }
    }

    fun changeRuleAction(ssid: String, action: WifiAutomationAction) {
        settings.wifiAutomationRules.set(
            settings.wifiAutomationRules.value.map { rule ->
                if (rule.ssid == ssid) rule.copy(action = action) else rule
            }
        )
        refreshAutomationIfEnabled()
    }

    fun removeRule(ssid: String) {
        settings.wifiAutomationRules.set(settings.wifiAutomationRules.value.filterNot { it.ssid == ssid })
        refreshAutomationIfEnabled()
    }

    fun changeOtherWifiAction(action: WifiAutomationFallbackAction) {
        settings.wifiAutomationOtherWifiAction.set(action)
        refreshAutomationIfEnabled()
    }

    fun changeNoWifiAction(action: WifiAutomationFallbackAction) {
        settings.wifiAutomationNoWifiAction.set(action)
        refreshAutomationIfEnabled()
    }

    private fun addRule(
        ssid: String,
        action: WifiAutomationAction = WifiAutomationAction.Start,
    ) {
        if (settings.wifiAutomationRules.value.any { it.ssid == ssid }) {
            viewModelScope.launch { _effects.emit(Effect.SsidAlreadyExists) }
            return
        }
        settings.wifiAutomationRules.set(
            settings.wifiAutomationRules.value + WifiAutomationRule(ssid, action)
        )
        refreshAutomationIfEnabled()
        viewModelScope.launch { _effects.emit(Effect.AddedCurrentSsid) }
    }

    private fun refreshAutomationIfEnabled() {
        if (settings.wifiAutomationEnabled.value) WifiAutomationService.start(getApplication())
    }
}
