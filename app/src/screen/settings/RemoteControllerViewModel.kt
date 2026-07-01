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

package com.github.yumelira.yumebox.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.add
import com.github.yumelira.yumebox.core.data.remove
import com.github.yumelira.yumebox.core.data.update
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.remote.HttpClashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteControllerViewModel(
    application: Application,
    private val store: RemoteControllerStore,
    private val proxyFacade: ProxyFacade,
) : AndroidViewModel(application) {

    val controllerEnabled: StateFlow<Boolean> =
        store.controllerEnabled.state.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = store.controllerEnabled.value,
        )

    val backends: StateFlow<List<RemoteBackend>> =
        store.backends.state.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = store.backends.value,
        )

    val activeBackendId: StateFlow<String> =
        store.activeBackendId.state.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = store.activeBackendId.value,
        )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        store.controllerEnabled.set(enabled)
        proxyFacade.applyRemoteControllerState()
    }

    fun addBackend(name: String, host: String, port: Int, secret: String) {
        val backend =
            RemoteBackend(
                id = RemoteBackend.newId(),
                name = name.trim(),
                host = host.trim(),
                port = port,
                secret = secret.trim(),
            )
        store.backends.add(backend)
        if (store.activeBackendId.value.isBlank()) {
            store.activeBackendId.set(backend.id)
            proxyFacade.applyRemoteControllerState()
        }
    }

    fun updateBackend(updated: RemoteBackend) {
        store.backends.update({ it.id == updated.id }, { updated })
        if (updated.id == store.activeBackendId.value) {
            proxyFacade.applyRemoteControllerState()
        }
    }

    fun deleteBackend(id: String) {
        val wasActive = store.activeBackendId.value == id
        store.backends.remove { it.id == id }
        if (wasActive) {
            store.activeBackendId.set("")
            store.controllerEnabled.set(false)
            proxyFacade.applyRemoteControllerState()
        }
    }

    fun setActive(id: String) {
        store.activeBackendId.set(id)
        proxyFacade.applyRemoteControllerState()
    }

    fun testConnection(backend: RemoteBackend) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val manager = HttpClashManager(backendProvider = { backend })
                        manager.queryTunnelState()
                    }
                }
            result
                .onSuccess { state -> _messages.tryEmit("连接成功：模式 ${state.mode}") }
                .onFailure { error ->
                    _messages.tryEmit("连接失败：${error.message ?: error::class.simpleName}")
                }
        }
    }
}
