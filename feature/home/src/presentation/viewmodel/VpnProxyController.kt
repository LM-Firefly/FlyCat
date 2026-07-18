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

package com.github.yumelira.yumebox.feature.home.presentation.viewmodel

import android.content.Intent
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.contract.ProxyControlContract
import com.github.yumelira.yumebox.runtime.api.remote.VpnPermissionRequired
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages the VPN proxy start/stop lifecycle and permission flow.
 * Extracts the common pattern shared between app and lite HomeViewModels.
 */
class VpnProxyController(private val scope: CoroutineScope, private val proxyFacade: ProxyControlContract) {
    enum class PendingTransition {
        None,
        AwaitingPermission,
        Starting,
        Stopping,
    }
    private val _pendingTransition = MutableStateFlow(PendingTransition.None)
    val pendingTransition: StateFlow<PendingTransition> = _pendingTransition.asStateFlow()
    private val _vpnPrepareIntent = MutableSharedFlow<Intent>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val vpnPrepareIntent: SharedFlow<Intent> = _vpnPrepareIntent.asSharedFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    val runtimeSnapshot: StateFlow<RuntimeSnapshot> = proxyFacade.runtimeSnapshot
    /**
     * Observes runtime phase changes and clears pending transitions when a
     * terminal phase is reached (Idle, Failed, Running).
     */
    fun observeRuntimeState() {
        scope.launch {
            runtimeSnapshot.map { it.phase }.distinctUntilChanged().collect { phase -> if (phase == RuntimePhase.Idle || phase == RuntimePhase.Failed || phase == RuntimePhase.Running) { _pendingTransition.value = PendingTransition.None } }
        }
    }
    /**
     * Starts the proxy with the given mode. If VPN permission is required,
     * transitions to [PendingTransition.AwaitingPermission] and emits the
     * prepare intent.
     *
     * @param mode The proxy mode to start (Tun, Http, RootTun)
     * @param onPreStart Optional callback invoked before starting (e.g., to sync profile)
     * @param onSuccess Optional callback invoked after successful start
     */
    fun startProxy(mode: ProxyMode, onPreStart: (suspend () -> Unit)? = null, onSuccess: (() -> Unit)? = null) {
        if (_pendingTransition.value == PendingTransition.AwaitingPermission) return
        _pendingTransition.value = PendingTransition.Starting
        scope.launch {
            try {
                onPreStart?.invoke()
                proxyFacade.startProxy(mode)
                onSuccess?.invoke()
            } catch (error: VpnPermissionRequired) {
                _pendingTransition.value = PendingTransition.AwaitingPermission
                _vpnPrepareIntent.emit(error.intent)
                Timber.i("VPN permission required")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _pendingTransition.value = PendingTransition.None
                Timber.e(error, "Failed to start proxy")
                _messages.emit(error.message ?: "Start failed")
            }
        }
    }
    /**
     * Handles the result of a VPN permission request.
     *
     * @param granted Whether the permission was granted
     * @param onRetry Callback to retry the start flow (e.g., to re-invoke [startProxy])
     */
    fun onVpnPermissionResult(granted: Boolean, onRetry: () -> Unit) {
        if (_pendingTransition.value != PendingTransition.AwaitingPermission) return
        if (!granted) {
            _pendingTransition.value = PendingTransition.None
            _messages.tryEmit("VPN permission denied")
            return
        }
        _pendingTransition.value = PendingTransition.None
        onRetry()
    }
    /**
     * Stops the proxy with the given mode.
     *
     * @param mode The proxy mode to stop (null = stop all)
     * @param onPreStop Optional callback invoked before stopping
     */
    fun stopProxy(mode: ProxyMode? = null, onPreStop: (suspend () -> Unit)? = null) {
        if (_pendingTransition.value == PendingTransition.Stopping) return
        _pendingTransition.value = PendingTransition.Stopping
        scope.launch {
            try {
                onPreStop?.invoke()
                proxyFacade.stopProxy(mode)
                _pendingTransition.value = PendingTransition.None
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _pendingTransition.value = PendingTransition.None
                Timber.e(error, "Failed to stop proxy")
                _messages.emit(error.message ?: "Stop failed")
            }
        }
    }
    /**
     * Resets the pending transition to None. Use when external state changes
     * (e.g., remote controller takeover) require clearing the transition.
     */
    fun clearPendingTransition() { _pendingTransition.value = PendingTransition.None }
    /**
     * Emits a message to the [messages] flow. Use for user-facing feedback
     * that doesn't fit the VPN lifecycle (e.g., "no active profile").
     */
    fun emitMessage(message: String) { _messages.tryEmit(message) }
}
