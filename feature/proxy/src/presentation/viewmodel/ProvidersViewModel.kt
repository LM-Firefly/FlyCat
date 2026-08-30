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

package com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
import com.github.lmfirefly.flycat.core.contract.ProvidersRepository
import com.github.lmfirefly.flycat.core.model.Provider
import com.github.lmfirefly.flycat.locale.FlyTxt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class ProvidersViewModel(
    private val connectionRepository: ConnectionRepository,
    private val providersRepository: ProvidersRepository,
) : ViewModel() {
    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    val isRunning: StateFlow<Boolean> = connectionRepository.isRunning

    fun refreshProviders() {
        viewModelScope.launch {
            if (!connectionRepository.isRunning.value) {
                _providers.value = emptyList()
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }
            val result = providersRepository.queryProviders()
            result
                .onSuccess { providerList -> _providers.value = providerList.sorted() }
                .onFailure { error ->
                    Timber.e(error, "Failed to query providers")
                    _uiState.update {
                        it.copy(
                            error =
                                FlyTxt.Providers.Message.FetchFailed.format(
                                    error.message ?: "Unknown error"
                                )
                        )
                    }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateProvider(provider: Provider) {
        val providerKey = "${provider.type}_${provider.name}"
        viewModelScope.launch {
            _uiState.update { it.copy(updatingProviders = it.updatingProviders + providerKey) }
            val result = providersRepository.updateProvider(provider)
            result
                .onSuccess {
                    refreshProviders()
                    _uiState.update {
                        it.copy(
                            message = FlyTxt.Providers.Message.UpdateSuccess.format(provider.name)
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to update provider: %s", provider.name)
                    _uiState.update {
                        it.copy(
                            error =
                                FlyTxt.Providers.Message.UpdateFailed.format(
                                    error.message ?: "Unknown error"
                                )
                        )
                    }
                }
            _uiState.update { it.copy(updatingProviders = it.updatingProviders - providerKey) }
        }
    }

    fun updateAllProviders() {
        viewModelScope.launch {
            val httpProviders =
                _providers.value.filter { it.vehicleType == Provider.VehicleType.HTTP }
            if (httpProviders.isEmpty()) return@launch

            _uiState.update { it.copy(isUpdatingAll = true) }
            val providerKeys = httpProviders.map { "${it.type}_${it.name}" }.toSet()
            _uiState.update { it.copy(updatingProviders = providerKeys) }

            val result = providersRepository.updateAllProviders(httpProviders)
            result
                .onSuccess { updateResult ->
                    refreshProviders()
                    if (updateResult.failedProviders.isEmpty()) {
                        _uiState.update { it.copy(message = FlyTxt.Providers.Message.AllUpdated) }
                    } else {
                        val failedNames = updateResult.failedProviders.joinToString(", ")
                        Timber.w("Failed to update providers: %s", failedNames)
                        _uiState.update {
                            it.copy(
                                error =
                                    FlyTxt.Providers.Message.UpdateFailed.format(
                                        "Failed providers: $failedNames"
                                    )
                            )
                        }
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to update all providers")
                    _uiState.update {
                        it.copy(
                            error =
                                FlyTxt.Providers.Message.UpdateFailed.format(
                                    error.message ?: "Unknown error"
                                )
                        )
                    }
                }

            _uiState.update { it.copy(isUpdatingAll = false, updatingProviders = emptySet()) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun uploadProviderFile(context: Context, provider: Provider, uri: Uri) {
        val providerKey = "${provider.type}_${provider.name}"
        viewModelScope.launch {
            _uiState.update { it.copy(updatingProviders = it.updatingProviders + providerKey) }

            val result = providersRepository.uploadProviderFile(context, provider, uri)
            result
                .onSuccess {
                    refreshProviders()
                    _uiState.update {
                        it.copy(
                            message = FlyTxt.Providers.Message.UploadSuccess.format(provider.name)
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to upload provider file: %s", provider.name)
                    _uiState.update {
                        it.copy(
                            error =
                                FlyTxt.Providers.Message.UploadFailed.format(
                                    error.message ?: "Unknown error"
                                )
                        )
                    }
                }

            _uiState.update { it.copy(updatingProviders = it.updatingProviders - providerKey) }
        }
    }

    data class ProvidersUiState(
        val isLoading: Boolean = false,
        val isUpdatingAll: Boolean = false,
        val updatingProviders: Set<String> = emptySet(),
        val message: String? = null,
        val error: String? = null,
    )
}
