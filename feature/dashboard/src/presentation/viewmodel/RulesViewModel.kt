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

package com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.model.RuntimeRule
import com.github.lmfirefly.flycat.feature.dashboard.domain.RuleToggleUseCase
import com.github.lmfirefly.flycat.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

data class RulesUiState(
    val rules: List<RuntimeRule> = emptyList(),
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    val error: String? = null,
    val toggleError: String? = null,
    val togglingIndexes: Set<Int> = emptySet(),
    val searchQuery: String = "",
)

class RulesViewModel(
    private val ruleToggle: RuleToggleUseCase,
) : BaseViewModel<RulesUiState>(RulesUiState()) {

    private val toggleMutex = Mutex()
    private val toggleStateLock = Any()
    private val toggleJobs = mutableMapOf<Int, Job>()
    private val pendingEnabledByIndex = mutableMapOf<Int, Boolean>()
    private val originalRuleByIndex = mutableMapOf<Int, RuntimeRule>()
    private var silentCalibrationJob: Job? = null

    companion object {
        private const val TOGGLE_DEBOUNCE_MS = RuleToggleUseCase.TOGGLE_DEBOUNCE_MS
        private const val SILENT_CALIBRATION_MS = RuleToggleUseCase.SILENT_CALIBRATION_MS
    }

    val state: StateFlow<RulesUiState> get() = uiState

    val filteredRules: StateFlow<List<RuntimeRule>> = state
        .map { current -> ruleToggle.filterRules(current.rules, current.searchQuery) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        refresh()
    }

    fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { it.copy(isLoading = true, error = null) }
            runCatching { ruleToggle.queryRules() }
                .onSuccess { rules ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            isRunning = true,
                            rules = rules,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    Timber.w(error, "Failed to query runtime rules")
                    updateState {
                        it.copy(
                            isLoading = false,
                            isRunning = false,
                            rules = emptyList(),
                            error = error.message,
                        )
                    }
                }
        }
    }

    fun setRuleEnabled(index: Int, enabled: Boolean) {
        val currentRule = state.value.rules.firstOrNull { it.index == index } ?: return
        val disabled = !enabled

        synchronized(toggleStateLock) {
            if (!pendingEnabledByIndex.containsKey(index)) {
                originalRuleByIndex[index] = currentRule
            }
            pendingEnabledByIndex[index] = enabled
            silentCalibrationJob?.cancel()
            toggleJobs.remove(index)?.cancel()
            toggleJobs[index] =
                viewModelScope.launch(Dispatchers.IO) {
                    delay(TOGGLE_DEBOUNCE_MS)
                    applyDebouncedToggle(index)
                }
        }

        // Optimistic local update so UI responds immediately.
        updateState { current ->
            current.copy(
                rules = current.rules.map { if (it.index == index) it.copy(disabled = disabled) else it },
                togglingIndexes = current.togglingIndexes + index,
                toggleError = null,
            )
        }
    }

    private suspend fun applyDebouncedToggle(index: Int) {
        val desiredEnabled = synchronized(toggleStateLock) {
            pendingEnabledByIndex.remove(index) ?: return
        }

        toggleMutex.withLock {
            val result = ruleToggle.applyToggle(index, desiredEnabled)
            when (result) {
                is RuleToggleUseCase.ToggleResult.Success -> {
                    synchronized(toggleStateLock) {
                        toggleJobs.remove(index)
                        originalRuleByIndex.remove(index)
                    }
                    updateState {
                        it.copy(togglingIndexes = it.togglingIndexes - index, toggleError = null)
                    }
                    scheduleSilentCalibration()
                }
                is RuleToggleUseCase.ToggleResult.Failed -> {
                    synchronized(toggleStateLock) { toggleJobs.remove(index) }
                    rollbackRule(index, result.error)
                }
            }
        }
    }

    private fun scheduleSilentCalibration() {
        synchronized(toggleStateLock) {
            silentCalibrationJob?.cancel()
            silentCalibrationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    delay(SILENT_CALIBRATION_MS)
                    val shouldCalibrate =
                        synchronized(toggleStateLock) {
                            pendingEnabledByIndex.isEmpty() && toggleJobs.isEmpty()
                        }
                    if (!shouldCalibrate || currentState.togglingIndexes.isNotEmpty()) {
                        return@launch
                    }
                    val updated = ruleToggle.calibrateIfNeeded(currentState.rules)
                    if (updated != null) {
                        updateState { it.copy(rules = updated, isRunning = true, error = null) }
                    }
                }
        }
    }

    private fun rollbackRule(index: Int, error: Throwable) {
        Timber.w(error, "Failed to toggle rule index=%s", index)
        val originalRule = synchronized(toggleStateLock) { originalRuleByIndex.remove(index) }
        updateState { current ->
            current.copy(
                rules = current.rules.map { rule ->
                    if (rule.index == index && originalRule != null) {
                        originalRule
                    } else {
                        rule
                    }
                },
                togglingIndexes = current.togglingIndexes - index,
                toggleError = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    fun consumeToggleError() {
        updateState { it.copy(toggleError = null) }
    }

    fun refreshRules() {
        refresh()
    }

    override fun onCleared() {
        synchronized(toggleStateLock) {
            silentCalibrationJob?.cancel()
            silentCalibrationJob = null
            toggleJobs.values.forEach { it.cancel() }
            toggleJobs.clear()
            pendingEnabledByIndex.clear()
            originalRuleByIndex.clear()
        }
        super.onCleared()
    }
}
