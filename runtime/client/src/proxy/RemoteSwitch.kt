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

package com.github.lmfirefly.flycat.runtime.client.proxy

import com.github.lmfirefly.flycat.core.contract.RemoteControllerStoreReader
import com.github.lmfirefly.flycat.core.model.PausedLocalRuntime
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.util.AppForegroundState
import com.github.lmfirefly.flycat.core.util.PollingTimerSpecs
import com.github.lmfirefly.flycat.core.util.PollingTimers
import com.github.lmfirefly.flycat.core.util.enumByNameOrNull
import com.github.lmfirefly.flycat.core.util.throttleByScene
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.client.remote.ServiceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** 外部控制器模式的探测/挂载/回退机制。所需的首选项存储在仓库中；此对象负责实时接管，这样[ProxyFacade]就不会发展出第二个生命周期。 */
internal class RemoteSwitch(
    private val scope: CoroutineScope,
    private val store: RemoteControllerStoreReader,
    private val screenOn: StateFlow<Boolean>,
    private val operationMutex: Mutex,
    private val snapshot: () -> RuntimeSnapshot,
    private val publishRemoteRunning: () -> Unit,
    private val reconcile: suspend () -> Unit,
    private val startLocal: suspend (RuntimeOwner, RunMode) -> Unit,
    private val startTrafficPolling: () -> Unit,
    private val stopTrafficPolling: () -> Unit,
    private val connectBackend: suspend () -> Unit,
    private val onAfterRunning: suspend () -> Unit,
    private val detectActiveOwner: () -> RuntimeOwner,
    private val localModeForOwner: (RuntimeOwner) -> RunMode?,
    private val configuredMode: () -> RunMode,
    private val stopOwner: suspend (RuntimeOwner) -> Unit,
    private val reconcilePersistedRuntimeState: () -> Unit,
) {
    private companion object {
        const val PROBE_FAILURE_THRESHOLD = 3
        const val PROBE_BACKGROUND_INTERVAL_MS = 30_000L
        const val PROBE_SCREEN_OFF_INTERVAL_MS = 60_000L
    }
    private val mutex = Mutex()
    private var probeJob: Job? = null
    private var consecutiveFailures = 0
    fun apply() {
        scope.launch { mutex.withLock { applyLocked() } }
    }
    private suspend fun applyLocked() {
        if (!store.isWanted()) {
            consecutiveFailures = 0
            detachIfHolding()
            stopWatch()
            return
        }
        startWatch()
        if (reachable()) {
            consecutiveFailures = 0
            attach()
            return
        }
        handleProbeMiss()
    }
    /** 单次探测失败不得重启本地运行时：仅当确有挂载时计数，连续失败达阈值才回退。 */
    private suspend fun handleProbeMiss() {
        val holding = store.controllerAttached.value || snapshot().owner == RuntimeOwner.RemoteController
        if (!holding) return
        consecutiveFailures += 1
        if (consecutiveFailures < PROBE_FAILURE_THRESHOLD) return
        consecutiveFailures = 0
        detach()
    }
    private suspend fun watchdogTick() {
        if (!store.isWanted()) {
            consecutiveFailures = 0
            detachIfHolding()
            return
        }
        if (reachable()) {
            consecutiveFailures = 0
            attach()
            return
        }
        handleProbeMiss()
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startWatch() {
        if (probeJob?.isActive == true) return
        probeJob = scope.launch {
            PollingTimers.ticks(PollingTimerSpecs.RemoteControllerProbe).throttleByScene(
                screenOn = screenOn,
                appForeground = AppForegroundState.foreground,
                backgroundIntervalMs = PROBE_BACKGROUND_INTERVAL_MS,
                screenOffIntervalMs = PROBE_SCREEN_OFF_INTERVAL_MS,
            ).collect {
                mutex.withLock { watchdogTick() }
            }
        }
    }
    private fun stopWatch() {
        probeJob?.cancel()
        probeJob = null
    }
    private suspend fun reachable(): Boolean = runCatching { ServiceClient.probe() }.onFailure { error ->
        if (error is CancellationException) throw error
        Timber.d(error, "Remote controller probe skipped")
    }.getOrDefault(false)
    private fun isWatchingRemote(): Boolean {
        val current = snapshot()
        return current.owner == RuntimeOwner.RemoteController &&
            current.phase == RuntimePhase.Running
    }
    private suspend fun attach() {
        store.controllerAttached.set(true)
        operationMutex.withLock {
            if (!isWatchingRemote()) {
                pauseLocalIfRunning()
                publishRemoteRunning()
            }
        }
        runCatching { connectBackend() }.onFailure { error ->
            if (error is CancellationException) throw error
            Timber.d(error, "Remote controller backend connect skipped")
        }
        startTrafficPolling()
        onAfterRunning()
    }
    private suspend fun detachIfHolding() {
        if (store.controllerAttached.value || snapshot().owner == RuntimeOwner.RemoteController) {
            detach()
        }
    }
    private suspend fun detachIfActive() {
        if (store.isActive()) detach()
    }
    private suspend fun detach() {
        store.controllerAttached.set(false)
        stopTrafficPolling()
        val paused = store.takePausedLocal()?.toTypedOrNull()
        if (snapshot().owner == RuntimeOwner.RemoteController) {
            reconcile()
        }
        if (paused == null) return
        Timber.i("Controller fallback: resuming local runtime owner=${paused.owner} mode=${paused.mode}")
        runCatching { startLocal(paused.owner, paused.mode) }.onFailure { error ->
            if (error is CancellationException) throw error
            Timber.w(error, "Failed to resume local runtime after controller fallback")
        }
    }
    private suspend fun pauseLocalIfRunning() {
        runCatching {
            val owner = detectActiveOwner()
            if (owner != RuntimeOwner.LocalTun && owner != RuntimeOwner.RootTun) return
            val mode = localModeForOwner(owner) ?: configuredMode()
            store.rememberPausedLocal(owner.name, mode.name)
            Timber.i("Controller switch: pausing local runtime owner=$owner mode=$mode")
            stopOwner(owner)
            stopTrafficPolling()
            reconcilePersistedRuntimeState()
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Timber.w(error, "Failed to pause local runtime on controller switch")
        }
    }
    private data class TypedPausedLocal(val owner: RuntimeOwner, val mode: RunMode)
    private fun PausedLocalRuntime.toTypedOrNull(): TypedPausedLocal? {
        val owner = enumByNameOrNull<RuntimeOwner>(ownerName) ?: return null
        val mode = enumByNameOrNull<RunMode>(modeName) ?: return null
        return when (owner) {
            RuntimeOwner.LocalTun,
            RuntimeOwner.RootTun -> TypedPausedLocal(owner, mode)
            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> null
        }
    }
}
