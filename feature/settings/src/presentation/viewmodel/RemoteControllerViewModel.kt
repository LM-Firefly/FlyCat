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

package com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.RemoteControllerStoreReader
import com.github.lmfirefly.flycat.core.contract.add
import com.github.lmfirefly.flycat.core.contract.remove
import com.github.lmfirefly.flycat.core.contract.update
import com.github.lmfirefly.flycat.core.model.RemoteBackend
import com.github.lmfirefly.flycat.core.model.RemoteProtocol
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.github.lmfirefly.flycat.locale.FlyTxt

class RemoteControllerViewModel(
    application: Application,
    private val store: RemoteControllerStoreReader,
    private val proxyFacade: ProxyControlContract,
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

    fun setProtocol(id: String, protocol: RemoteProtocol) {
        store.backends.update({ it.id == id }, { it.copy(protocol = protocol) })
        if (id == store.activeBackendId.value) {
            proxyFacade.applyRemoteControllerState()
        }
    }
}
