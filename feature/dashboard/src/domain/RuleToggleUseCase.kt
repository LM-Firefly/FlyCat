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

package com.github.lmfirefly.flycat.feature.dashboard.domain

import com.github.lmfirefly.flycat.core.contract.RuntimeRuleRepository
import com.github.lmfirefly.flycat.core.model.RuntimeRule
import timber.log.Timber

/**
 * Encapsulates the rule toggle business logic: debounce, optimistic update,
 * rollback on failure, and silent calibration after settling.
 *
 * Extracted from RulesViewModel to separate domain concerns from UI state management.
 */
class RuleToggleUseCase(
    private val runtimeRuleRepository: RuntimeRuleRepository,
) {
    companion object {
        const val TOGGLE_DEBOUNCE_MS = 250L
        const val SILENT_CALIBRATION_MS = 2_500L
    }

    /** Query all rules from the runtime. */
    suspend fun queryRules(): List<RuntimeRule> = runtimeRuleRepository.queryRules()

    /**
     * Apply a debounced toggle to the runtime.
     * Returns [ToggleResult.Success] if the toggle was applied, or
     * [ToggleResult.Failed] with the original rule for rollback.
     */
    suspend fun applyToggle(index: Int, desiredEnabled: Boolean): ToggleResult {
        val disabled = !desiredEnabled
        return runCatching { runtimeRuleRepository.setRuleDisabled(index, disabled) }
            .fold(
                onSuccess = { ok ->
                    if (ok) ToggleResult.Success
                    else ToggleResult.Failed(IllegalStateException("setRuleDisabled returned false"))
                },
                onFailure = { error ->
                    Timber.w(error, "Failed to toggle rule index=%s", index)
                    ToggleResult.Failed(error)
                },
            )
    }

    /**
     * Perform silent calibration: re-query rules and return them if they differ
     * from the current set. Returns null if no update is needed or on failure.
     */
    suspend fun calibrateIfNeeded(currentRules: List<RuntimeRule>): List<RuntimeRule>? {
        return runCatching { runtimeRuleRepository.queryRules() }
            .getOrNull()
            ?.takeIf { it != currentRules }
    }

    /** Filter rules by search query. */
    fun filterRules(rules: List<RuntimeRule>, query: String): List<RuntimeRule> {
        if (query.isBlank()) return rules
        return rules.filter { it.matchesQuery(query) }
    }

    sealed class ToggleResult {
        data object Success : ToggleResult()
        data class Failed(val error: Throwable) : ToggleResult()
    }
}

private fun RuntimeRule.matchesQuery(query: String): Boolean =
    payload.contains(query, ignoreCase = true) ||
        type.contains(query, ignoreCase = true) ||
        proxy.contains(query, ignoreCase = true) ||
        index.toString().contains(query)
