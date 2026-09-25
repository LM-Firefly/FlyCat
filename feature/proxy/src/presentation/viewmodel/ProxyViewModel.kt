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
import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
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
import kotlinx.coroutines.withTimeoutOrNull
import com.github.lmfirefly.flycat.locale.FlyTxt

class ProxyViewModel(
    private val proxyGroupRepository: ProxyGroupRepository,
    private val connectionRepository: ConnectionRepository,
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

    /** 当前策略组延迟测试进度（已测节点数/总节点数），null 表示无逐节点进度。 */
    private val _delayTestProgress = MutableStateFlow<DelayTestProgress?>(null)
    val delayTestProgress: StateFlow<DelayTestProgress?> = _delayTestProgress.asStateFlow()

    data class DelayTestProgress(val tested: Int, val total: Int)

    /** 防止并发分组测试（YumeBox: groupDelayTestInProgress boolean lock） */
    @Volatile
    private var groupDelayTestInProgress = false
    /** 防止单节点重复测试（YumeBox: pendingProxyDelayTests Set） */
    private val pendingProxyDelayTests = mutableSetOf<String>()
    @Volatile
    private var lastForceRefreshAtMs = 0L

    private companion object {
        const val FORCE_REFRESH_COOLDOWN_MS = 30_000L
    }

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
        // 保活 isRunning 的 WhileSubscribed 上游，保证点击时读到的是当前运行状态。
        viewModelScope.launch { connectionRepository.isRunning.collect { } }
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

    /**
     * 当应用在代理页面激活时返回前台时调用。
     * 使用短超时避免阻塞UI；强刷带冷却，避免多窗口/分屏下 ON_RESUME 连发导致全量 JNI 查询。
     */
    fun onForegroundResume() {
        if (activeSyncSources.isEmpty()) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastForceRefreshAtMs < FORCE_REFRESH_COOLDOWN_MS) return
        lastForceRefreshAtMs = nowMs
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(800L) {
                    proxyGroupRepository.refreshProxyGroups(force = true)
                }
            }.onFailure { error -> if (error is CancellationException) throw error }
        }
    }

    fun refreshGroup(groupName: String) {
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(500L) {
                    proxyGroupRepository.refreshProxyGroup(groupName)
                }
            }.onFailure { error -> if (error is CancellationException) throw error }
        }
    }

    fun testDelay(groupName: String? = null) {
        if (groupDelayTestInProgress) return
        groupDelayTestInProgress = true
        viewModelScope.launch {
            var markedTargets: Set<String> = emptySet()
            try {
                setLoading(true)
                clearError()
                val currentGroups = proxyGroups.value
                if (groupName != null) {
                    showMessage(FlyTxt.Proxy.Testing.Group.format(groupName))
                    showMessage(FlyTxt.Proxy.Testing.RequestSent)
                    val targets = currentGroups.firstOrNull { group -> group.name == groupName }?.proxies.orEmpty().map { proxy -> proxy.name }
                    markedTargets = setOf(groupName)
                    _testingGroupNames.update { it + markedTargets }
                    if (targets.isNotEmpty()) {
                        _delayTestProgress.value = DelayTestProgress(tested = 0, total = targets.size)
                    }
                    setLoading(false)
                    val error =
                        healthCheck.runGroupHealthCheck(groupName, targets) { tested, total ->
                            _delayTestProgress.value = DelayTestProgress(tested = tested, total = total)
                        }
                    error?.let { thrown ->
                        showError(FlyTxt.Proxy.Testing.Failed.format(thrown.message))
                    }
                } else {
                    showMessage(FlyTxt.Proxy.Testing.All)
                    markedTargets = currentGroups.mapTo(linkedSetOf()) { it.name }
                    _testingGroupNames.update { it + markedTargets }
                    setLoading(false)
                    val result = healthCheck.runHealthCheck(groupName, currentGroups)
                    if (result.testingTargets.isNotEmpty()) {
                        delay(result.settleDelayMs)
                    }
                    result.error?.let { thrown ->
                        showError(FlyTxt.Proxy.Testing.Failed.format(thrown.message))
                    }
                }
            } finally {
                if (markedTargets.isNotEmpty()) {
                    _testingGroupNames.update { it - markedTargets }
                }
                _delayTestProgress.value = null
                groupDelayTestInProgress = false
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
        // 预览态没有可切换的内核，patchSelector 必然失败；明确提示而不是让点击看似无响应。
        if (!connectionRepository.isRunning.value) {
            showMessage(FlyTxt.Proxy.Selection.RequireRunning)
            return
        }
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
        if (!connectionRepository.isRunning.value) {
            showMessage(FlyTxt.Proxy.Selection.RequireRunning)
            return
        }
        viewModelScope.launch {
            runCatching {
                if (proxyName.isNotBlank() && isNodeLivenessUnknown(groupName, proxyName)) {
                    // Go 侧 URLTest/Fallback 的存活判定会忽略未测速/测速失败节点的强制选择，先测活再固定
                    _testingProxyNames.update { it + proxyName }
                    try {
                        runCatching { healthCheck.runProxyHealthCheck(groupName, proxyName) }.onFailure { error -> if (error is CancellationException) throw error }
                    } finally {
                        _testingProxyNames.update { it - proxyName }
                    }
                }
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

    /** 节点当前无有效延迟（未测速或测速失败）时需要先测活，否则 Go 侧存活判定会忽略强制选择。 */
    private fun isNodeLivenessUnknown(groupName: String, proxyName: String): Boolean {
        val proxy = proxyGroups.value.firstOrNull { group -> group.name == groupName }?.proxies?.firstOrNull { proxy -> proxy.name == proxyName }?: return false
        return proxy.delay !in 1..5000
    }

    fun testProxyDelay(proxyName: String) {
        val groupName = proxyGroups.value.firstOrNull { group ->
            group.proxies.any { it.name == proxyName }
        }?.name ?: return
        testProxyDelay(groupName, proxyName)
    }

    fun testProxyDelay(groupName: String, proxyName: String) {
        if (!pendingProxyDelayTests.add(proxyName)) return
        viewModelScope.launch {
            _testingProxyNames.update { it + proxyName }
            try {
                runCatching { healthCheck.runProxyHealthCheck(groupName, proxyName) }
                delay(500L)
            } finally {
                _testingProxyNames.update { it - proxyName }
                pendingProxyDelayTests.remove(proxyName)
            }
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
