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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

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
        private const val NODE_TEST_TIMEOUT_MS = 6_000L
        private const val NODE_TEST_CONCURRENCY = 32
        private const val PROGRESS_REFRESH_INTERVAL_MS = 700L
        private const val GROUP_REFRESH_TIMEOUT_MS = 600L
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

        proxyGroupRepository.markDelayTestActive(true)
        val result = runCatching {
            if (groupName != null) {
                withTimeout(HEALTH_CHECK_TIMEOUT_MS) { proxyGroupRepository.healthCheck(groupName) }
                delay(POST_CHECK_DELAY_MS)
                withTimeoutOrNull(2_000L) { proxyGroupRepository.refreshProxyGroup(groupName) }
            } else {
                withTimeout(HEALTH_CHECK_TIMEOUT_MS) { proxyGroupRepository.healthCheckAll() }
                if (currentGroups.isNotEmpty()) {
                    delay(POST_CHECK_DELAY_MS)
                    withTimeoutOrNull(3_000L) { proxyGroupRepository.refreshProxyGroups(force = true) }
                }
            }
        }
        proxyGroupRepository.markDelayTestActive(false)

        return HealthCheckResult(
            testingTargets = testingTargets,
            settleDelayMs = UI_SETTLE_DELAY_MS,
            error = result.exceptionOrNull(),
        )
    }

    /**
     * 逐节点测试指定策略组的延迟，每完成一个节点通过 [onProgress] 上报 (tested, total)。
     * 测试期间周期性刷新该组以渐进呈现结果；单节点超时/失败计为已完成，返回值仅含基础设施错误。
     */
    suspend fun runGroupHealthCheck(groupName: String, proxyNames: List<String>, onProgress: (tested: Int, total: Int) -> Unit): Throwable? {
        proxyGroupRepository.markDelayTestActive(true)
        var error: Throwable? = null
        try {
            coroutineScope {
                val progressRefresh = launch {
                    while (true) {
                        delay(PROGRESS_REFRESH_INTERVAL_MS)
                        runCatching {
                            withTimeoutOrNull(GROUP_REFRESH_TIMEOUT_MS) {
                                proxyGroupRepository.refreshProxyGroup(groupName)
                            }
                        }.onFailure { failure -> if (failure is CancellationException) throw failure }
                    }
                }
                try {
                    if (proxyNames.isEmpty()) {
                        runCatching {
                            withTimeout(HEALTH_CHECK_TIMEOUT_MS) { proxyGroupRepository.healthCheck(groupName) }
                        }.onFailure { failure -> if (failure is CancellationException) throw failure }
                            .exceptionOrNull()
                            ?.let { thrown -> error = thrown }
                    } else {
                        val semaphore = Semaphore(NODE_TEST_CONCURRENCY)
                        var completed = 0
                        proxyNames.forEach { proxyName ->
                            launch {
                                semaphore.withPermit {
                                    runCatching {
                                        withTimeoutOrNull(NODE_TEST_TIMEOUT_MS) {
                                            proxyGroupRepository.healthCheckProxy(groupName, proxyName)
                                        }
                                    }.onFailure { failure -> if (failure is CancellationException) throw failure }
                                    completed++
                                    onProgress(completed, proxyNames.size)
                                }
                            }
                        }
                    }
                } finally {
                    progressRefresh.cancel()
                }
            }
            withTimeoutOrNull(GROUP_REFRESH_TIMEOUT_MS) {
                proxyGroupRepository.refreshProxyGroup(groupName)
            }
        } finally {
            proxyGroupRepository.markDelayTestActive(false)
        }
        return error
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
     * 当源激活时，若需要则预热代理组。
     * 使用较短的超时时间以避免阻塞后续的UI操作。
     */
    suspend fun warmUpIfNeeded(isActive: Boolean, currentGroups: List<ProxyGroupInfo>) {
        if (isActive && currentGroups.isEmpty()) {
            withTimeoutOrNull(1_500L) {
                proxyGroupRepository.refreshProxyGroups()
            }
        }
    }

    data class HealthCheckResult(
        val testingTargets: Set<String>,
        val settleDelayMs: Long,
        val error: Throwable?,
    )
}
