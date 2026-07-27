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

package com.github.yumelira.yumebox.runtime.client.session

import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.domain.model.ProxyGroupInfo
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.client.access.RuntimeAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** Proxy-group cache + refresh/select/health operations owned by the runtime session surface. */
internal class RuntimeGroupHub(
    private val scope: CoroutineScope,
    private val session: RuntimeSession,
    private val coreOps: RuntimeCoreOps,
    private val isRemoteControllerActive: () -> Boolean,
) {
    private companion object {
        const val PROXY_SELECT_FULL_REFRESH_DELAY_MS = 400L
    }

    private val groupStore =
        ProxyGroupStore(
            isRuntimeRunning = { session.snapshotValue().phase == RuntimePhase.Running },
            onGroupsReady = { ready -> session.updateGroupsReady(ready) },
        )
    private val refreshMutex = Mutex()

    val groups
        get() = groupStore.groups

    val resolvedPrimaryNode
        get() = groupStore.resolvedPrimaryNode

    fun clear(resetGroups: Boolean) = groupStore.clear(resetGroups)

    fun isGroupsEmpty(): Boolean = groupStore.groups.value.isEmpty()

    suspend fun selectProxy(group: String, proxyName: String): Boolean {
        Timber.d("Select proxy: group=$group proxy=$proxyName")
        val ok = coreOps.patchSelector(group, proxyName)
        if (ok) {
            refreshProxyGroup(group)
            scheduleGroupsRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
        }
        return ok
    }

    suspend fun healthCheck(group: String) {
        Timber.d("Health check request: group=%s", group)
        coreOps.healthCheck(group)
        Timber.d("Health check dispatched: group=%s", group)
        scheduleGroupRefresh(group, PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis)
        scheduleGroupsRefresh(PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis)
    }

    suspend fun healthCheckAll() {
        Timber.d("Health check all request")
        val manager = coreOps.api()
        val groupNames =
            manager.queryAllProxyGroups(excludeNotSelectable = false).map { it.name }
        groupNames.forEach { groupName ->
            manager.healthCheck(groupName)
            scheduleGroupRefresh(
                groupName,
                PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis,
            )
        }
        scheduleGroupsRefresh(PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis)
    }

    suspend fun healthCheckProxy(group: String, proxyName: String): Int {
        Timber.d("Health check proxy request: group=%s proxy=%s", group, proxyName)
        val delay = coreOps.healthCheckProxy(group, proxyName)
        Timber.d("Health check proxy done: group=%s proxy=%s delay=%s", group, proxyName, delay)
        refreshProxyGroup(group)
        scheduleGroupsRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
        return delay
    }

    suspend fun refreshProxyGroups() {
        refreshMutex.withLock {
            val snapshot = session.snapshotValue()
            var missingLocalRuntime = false
            val groups =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (!snapshot.running) {
                            return@runCatching queryPreviewProxyGroups()
                        }
                        coreOps
                            .queryAllProxyGroups(excludeNotSelectable = false)
                            .map(groupStore::toInfo)
                        }
                        .getOrElse { error ->
                            if (error is CancellationException) throw error
                            Timber.e(error, "Failed to refresh proxy groups")
                            missingLocalRuntime = session.isMissingLocalRuntime(snapshot)
                            null
                        }
                }

            if (groups != null) {
                groupStore.publish(groups)
            } else if (missingLocalRuntime) {
                session.handleMissingLocalRuntime(snapshot, "runtime backend unavailable")
                runCatching { queryPreviewProxyGroups() }
                    .onSuccess { preview -> groupStore.publish(preview) }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.d(
                            error,
                            "Fallback preview refresh skipped after stale runtime reset",
                        )
                    }
            }
        }
    }

    suspend fun refreshProxyGroup(name: String, sort: ProxySort = ProxySort.Default) {
        if (!session.snapshotValue().running) {
            if (groupStore.groups.value.isEmpty()) {
                refreshProxyGroups()
            }
            return
        }
        refreshMutex.withLock {
            val updatedGroup =
                withContext(Dispatchers.IO) {
                    runCatching { groupStore.toInfo(coreOps.queryProxyGroup(name, sort)) }
                        .getOrElse { error ->
                            if (error is CancellationException) throw error
                            Timber.e(error, "Failed to refresh proxy group: %s", name)
                            null
                        }
                } ?: return
            groupStore.publish(groupStore.upsert(updatedGroup))
        }
    }

    suspend fun refreshSafely() {
        val snapshot = session.snapshotValue()
        if (
            snapshot.phase != RuntimePhase.Running &&
                snapshot.owner != RuntimeOwner.RemoteController
        ) {
            return
        }
        runCatching { refreshProxyGroups() }
            .onFailure { error ->
                if (error is CancellationException) throw error
                Timber.d(error, "Runtime proxy group sync skipped")
            }
    }

    private fun scheduleGroupRefresh(groupName: String, delayMillis: Long = 0L) {
        if (groupName.isBlank()) return
        scope.launch {
            awaitDelay(delayMillis, "runtime_proxy_group_refresh_$groupName")
            runCatching { refreshProxyGroup(groupName) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.d(error, "Deferred proxy group refresh skipped: %s", groupName)
                }
        }
    }

    private fun scheduleGroupsRefresh(delayMillis: Long = 0L) {
        scope.launch {
            awaitDelay(delayMillis, "runtime_proxy_groups_refresh")
            refreshSafely()
        }
    }

    private suspend fun awaitDelay(delayMillis: Long, name: String) {
        if (delayMillis <= 0L) return
        PollingTimers.awaitTick(
            PollingTimerSpecs.dynamic(
                name = name,
                intervalMillis = delayMillis,
                initialDelayMillis = delayMillis,
            )
        )
    }

    private suspend fun queryPreviewProxyGroups(): List<ProxyGroupInfo> {
        if (isRemoteControllerActive()) {
            return coreOps.queryAllProxyGroups(excludeNotSelectable = false).map(groupStore::toInfo)
        }
        session.connectBackend()
        val activeProfile =
            RuntimeAccess.profile().queryActive().also {
                session.setCurrentProfile(it)
                session.updateProfileReady(it)
            }
        if (activeProfile == null) {
            return emptyList()
        }
        return coreOps.queryAllProxyGroups(excludeNotSelectable = false).map(groupStore::toInfo)
    }
}
