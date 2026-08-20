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

package com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.contract.ProxyDisplaySettingsReader
import com.github.lmfirefly.flycat.core.contract.ProxyGroupRepository
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.feature.proxy.domain.ProxyHealthCheckUseCase
import com.github.lmfirefly.flycat.presentation.viewmodel.ContractStateViewModel
import com.github.lmfirefly.flycat.presentation.viewmodel.LoadableState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.github.lmfirefly.flycat.locale.FlyTxt

class ProxyViewModel(
    private val proxyGroupRepository: ProxyGroupRepository,
    private val proxyDisplaySettingsStore: ProxyDisplaySettingsReader,
    appSettings: AppSettingsReader,
    private val healthCheck: ProxyHealthCheckUseCase,
) :
    ContractStateViewModel<ProxyViewModel.ProxyUiState, ProxyViewModel.ProxyUiEffect>(
        ProxyUiState()
    ) {
    private val _testingGroupNames = MutableStateFlow<Set<String>>(emptySet())
    val testingGroupNames: StateFlow<Set<String>> = _testingGroupNames.asStateFlow()

    private val _testingProxyNames = MutableStateFlow<Set<String>>(emptySet())
    val testingProxyNames: StateFlow<Set<String>> = _testingProxyNames.asStateFlow()

    /** Shared selected group name for tablet dual-pane (left=groups, right=nodes). */
    private val _uiSelectedGroupName = MutableStateFlow<String?>(null)
    val uiSelectedGroupName: StateFlow<String?> = _uiSelectedGroupName.asStateFlow()

    fun selectUiGroup(name: String?) {
        _uiSelectedGroupName.value = name
    }

    private val groupSorter = ProxyGroupSorter()

    val sortMode: StateFlow<ProxySortMode> =
        proxyDisplaySettingsStore.sortMode.state.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ProxySortMode.DEFAULT,
        )

    val displayMode: StateFlow<ProxyDisplayMode> = proxyDisplaySettingsStore.displayMode.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProxyDisplayMode.DOUBLE_DETAILED)

    val proxyGroups: StateFlow<List<ProxyGroupInfo>> =
        proxyGroupRepository.proxyGroups
            .map { groups -> groups.filterNot(ProxyGroupInfo::hidden) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeSyncSources = mutableSetOf<String>()

    init {
        proxyGroupRepository.warmUpProxyGroups()
        viewModelScope.launch {
            proxyGroups
                .distinctUntilChangedBy { groups -> groups.map(ProxyGroupInfo::name) }
                .collect { groups -> groupSorter.track(groups) }
        }
    }

    val sortedProxyGroups: StateFlow<List<ProxyGroupInfo>> =
        groupSorter.bind(scope = viewModelScope, proxyGroups = proxyGroups, sortMode = sortMode)

    fun ensureCoreLoaded(isActive: Boolean, source: String = "proxy_page") {
        val changed =
            if (isActive) {
                activeSyncSources.add(source)
            } else {
                activeSyncSources.remove(source)
            }
        if (!changed) return
        healthCheck.updateSyncPriority(isActive, source)
        if (isActive) {
            viewModelScope.launch {
                runCatching { healthCheck.warmUpIfNeeded(isActive, proxyGroups.value) }
                    .onFailure { error -> if (error is CancellationException) throw error }
            }
        }
    }

    fun refreshGroup(groupName: String) {
        viewModelScope.launch {
            runCatching { proxyGroupRepository.refreshProxyGroup(groupName) }
                .onFailure { error -> if (error is CancellationException) throw error }
        }
    }

    fun testDelay(groupName: String? = null) {
        viewModelScope.launch {
            setLoading(true)
            clearError()
            val currentGroups = proxyGroups.value
            val result = healthCheck.runHealthCheck(groupName, currentGroups)

            if (result.testingTargets.isNotEmpty()) {
                _testingGroupNames.update { it + result.testingTargets }
            }

            if (groupName != null) {
                showMessage(FlyTxt.Proxy.Testing.Group.format(groupName))
                showMessage(FlyTxt.Proxy.Testing.RequestSent)
            } else {
                showMessage(FlyTxt.Proxy.Testing.All)
            }

            setLoading(false)

            if (result.testingTargets.isNotEmpty()) {
                delay(result.settleDelayMs)
                _testingGroupNames.update { it - result.testingTargets }
            }

            result.error?.let { error ->
                showError(FlyTxt.Proxy.Testing.Failed.format(error.message))
            }
        }
    }

    fun setSortMode(mode: ProxySortMode) {
        proxyDisplaySettingsStore.sortMode.set(mode)
    }

    fun setDisplayMode(mode: ProxyDisplayMode) {
        proxyDisplaySettingsStore.displayMode.set(mode)
    }

    fun selectProxy(groupName: String, proxyName: String) {
        viewModelScope.launch {
            runCatching {
                    val success = proxyGroupRepository.selectProxy(groupName, proxyName)
                    if (success) {
                        showMessage(FlyTxt.Proxy.Selection.Switched.format(proxyName))
                    } else {
                        showError(FlyTxt.Proxy.Selection.Failed)
                    }
                }
                .onFailure { error -> showError(FlyTxt.Proxy.Selection.Error.format(error.message)) }
        }
    }

    fun forceSelectProxy(groupName: String, proxyName: String) {
        viewModelScope.launch {
            runCatching {
                val success = proxyGroupRepository.forceSelectProxy(groupName, proxyName)
                if (success) {
                    val target = proxyName.ifBlank { FlyTxt.Proxy.Mode.Direct }
                    showMessage(FlyTxt.Proxy.Selection.Switched.format(target))
                } else {
                    showError(FlyTxt.Proxy.Selection.Failed)
                }
            }.onFailure { error ->
                showError(FlyTxt.Proxy.Selection.Error.format(error.message))
            }
        }
    }

    fun testProxyDelay(proxyName: String) {
        val groupName = proxyGroups.value.firstOrNull { group ->
            group.proxies.any { it.name == proxyName }
        }?.name ?: return
        testProxyDelay(groupName, proxyName)
    }

    fun testProxyDelay(groupName: String, proxyName: String) {
        viewModelScope.launch {
            _testingProxyNames.update { it + proxyName }
            runCatching { healthCheck.runProxyHealthCheck(groupName, proxyName) }
            delay(500L)
            _testingProxyNames.update { it - proxyName }
        }
    }

    private fun showMessage(message: String) {
        postMessage(message, ProxyUiEffect.ShowMessage(message))
    }

    private fun showError(error: String) {
        postError(error, ProxyUiEffect.ShowError(error))
    }

    fun clearError() {
        clearErrorState()
    }

    data class ProxyUiState(
        override val isLoading: Boolean = false,
        override val message: String? = null,
        override val error: String? = null,
    ) : LoadableState<ProxyUiState> {
        override fun withLoading(loading: Boolean): ProxyUiState = copy(isLoading = loading)

        override fun withError(error: String?): ProxyUiState = copy(error = error)

        override fun withMessage(message: String?): ProxyUiState = copy(message = message)
    }

    sealed interface ProxyUiEffect {
        data class ShowMessage(val message: String) : ProxyUiEffect

        data class ShowError(val message: String) : ProxyUiEffect
    }
}
