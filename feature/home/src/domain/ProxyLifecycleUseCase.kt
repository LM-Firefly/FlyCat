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

package com.github.lmfirefly.flycat.feature.home.domain

import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Encapsulates proxy lifecycle business logic: mode switching, tunnel mode
 * patching, and runtime reconciliation.
 *
 * Start/stop with VPN permission flow remains in [com.github.lmfirefly.flycat.feature.home.presentation.viewmodel.VpnProxyController]
 * because it is tightly coupled to Android Activity context and permission callbacks.
 */
class ProxyLifecycleUseCase(
    private val proxyFacade: ProxyControlContract,
    private val networkSettingsStore: NetworkSettingsReader,
) {
    /** Persist a new run mode and restart the proxy if currently running. */
    suspend fun switchRunMode(
        mode: com.github.lmfirefly.flycat.core.model.tunnel.RunMode,
        isRunning: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        try {
            if (networkSettingsStore.runMode.value == mode) return
            if (mode == com.github.lmfirefly.flycat.core.model.tunnel.RunMode.Tun) {
                val rootStatus = proxyFacade.evaluateRootAccess()
                if (!rootStatus.canStartRootTun) {
                    onError(rootStatus.rootTunBlockedMessage())
                    return
                }
            }
            networkSettingsStore.runMode.set(mode)
            if (isRunning) {
                withContext(Dispatchers.IO) {
                    AutoStartSessionGate.clearManualPaused()
                    proxyFacade.startProxy(mode)
                }
            }
            onSuccess()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.e(error, "Failed to switch proxy mode")
            onError(error.message ?: "Unknown error")
        }
    }

    /** Patch the tunnel mode on a running proxy. */
    suspend fun switchTunnelMode(
        mode: com.github.lmfirefly.flycat.core.model.tunnel.TunnelState.Mode,
        currentMode: com.github.lmfirefly.flycat.core.model.tunnel.TunnelState.Mode?,
        isRunning: Boolean,
        onError: (String) -> Unit = {},
    ) {
        try {
            if (currentMode == mode) return
            if (!isRunning) return
            val switched = withContext(Dispatchers.IO) { proxyFacade.patchTunnelMode(mode) }
            if (!switched) onError("patch tunnel mode failed")
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.e(error, "Failed to switch tunnel mode")
            onError(error.message ?: "Unknown error")
        }
    }

    /** Reconcile the runtime state and return the current snapshot. */
    suspend fun reconcileRuntimeState(): RuntimeSnapshot {
        proxyFacade.reconcileRuntimeState()
        return proxyFacade.runtimeSnapshot.value
    }

    /** Resolve the effective display run mode from the current snapshot and configured mode. */
    fun resolveDisplayMode(snapshot: RuntimeSnapshot): com.github.lmfirefly.flycat.core.model.tunnel.RunMode {
        return com.github.lmfirefly.flycat.runtime.api.contract.RuntimeStateMapper.resolveDisplayMode(
            snapshot,
            networkSettingsStore.runMode.value,
        )
    }
}
