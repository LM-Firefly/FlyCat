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

package com.github.lmfirefly.flycat.feature.proxy.domain

import com.github.lmfirefly.flycat.core.contract.ProxyGroupRepository
import com.github.lmfirefly.flycat.core.contract.ProxySyncPriority
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Encapsulates proxy group health check and sync priority orchestration
 * extracted from ProxyViewModel.
 */
class ProxyHealthCheckUseCase(
    private val proxyGroupRepository: ProxyGroupRepository,
) {
    companion object {
        const val HEALTH_CHECK_TIMEOUT_MS = 5_000L
        private const val POST_CHECK_DELAY_MS = 1_500L
        private const val UI_SETTLE_DELAY_MS = 2_200L
    }

    /**
     * Run health check for a single group or all groups.
     * Returns [HealthCheckResult] with timing metadata for UI feedback.
     */
    suspend fun runHealthCheck(groupName: String?, currentGroups: List<ProxyGroupInfo>): HealthCheckResult {
        val testingTargets = if (groupName != null) {
            setOf(groupName)
        } else {
            currentGroups.mapTo(linkedSetOf()) { it.name }
        }

        val result = runCatching {
            if (groupName != null) {
                withTimeout(HEALTH_CHECK_TIMEOUT_MS) { proxyGroupRepository.healthCheck(groupName) }
                delay(POST_CHECK_DELAY_MS)
                proxyGroupRepository.refreshProxyGroup(groupName)
            } else {
                proxyGroupRepository.healthCheckAll()
                if (currentGroups.isNotEmpty()) {
                    delay(POST_CHECK_DELAY_MS)
                    proxyGroupRepository.refreshProxyGroups(force = true)
                }
            }
        }

        return HealthCheckResult(
            testingTargets = testingTargets,
            settleDelayMs = UI_SETTLE_DELAY_MS,
            error = result.exceptionOrNull(),
        )
    }

    /**
     * Run health check for a single proxy node.
     */
    suspend fun runProxyHealthCheck(groupName: String, proxyName: String) {
        withTimeout(HEALTH_CHECK_TIMEOUT_MS) {
            proxyGroupRepository.healthCheckProxy(groupName, proxyName)
        }
    }

    /**
     * Update sync priority for a source. Returns true if the state changed.
     */
    fun updateSyncPriority(isActive: Boolean, source: String): Boolean {
        proxyGroupRepository.setProxyGroupSyncPriority(
            priority = if (isActive) ProxySyncPriority.FAST else ProxySyncPriority.OFF,
            source = source,
        )
        return true
    }

    /**
     * Warm up proxy groups if needed when a source becomes active.
     */
    suspend fun warmUpIfNeeded(isActive: Boolean, currentGroups: List<ProxyGroupInfo>) {
        if (isActive && currentGroups.isEmpty()) {
            proxyGroupRepository.refreshProxyGroups()
        }
    }

    data class HealthCheckResult(
        val testingTargets: Set<String>,
        val settleDelayMs: Long,
        val error: Throwable?,
    )
}
