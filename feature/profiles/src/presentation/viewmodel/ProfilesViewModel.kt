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

package com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.OverrideApplyExecutor
import com.github.lmfirefly.flycat.core.contract.OverrideConfigRepository
import com.github.lmfirefly.flycat.core.contract.ProfileBindingReader
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import com.github.lmfirefly.flycat.core.util.coroutine.safeRunSilent
import com.github.lmfirefly.flycat.feature.profiles.domain.ProfileCrudUseCase
import com.github.lmfirefly.flycat.feature.profiles.presentation.screen.copyProfileImport
import com.github.lmfirefly.flycat.presentation.viewmodel.AndroidContractStateViewModel
import com.github.lmfirefly.flycat.runtime.api.contract.ProfileRepositoryContract
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import com.github.lmfirefly.flycat.runtime.api.remote.IFetchObserver
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.github.lmfirefly.flycat.locale.FlyTxt
import timber.log.Timber

class ProfilesViewModel(
    application: Application,
    private val profilesRepository: ProfileRepositoryContract,
    private val bindingProvider: ProfileBindingReader,
    private val OverrideApplyExecutor: OverrideApplyExecutor,
    private val proxyControl: ProxyControlContract,
    private val overrideConfigRepository: OverrideConfigRepository,
    private val profileCrud: ProfileCrudUseCase,
) :
    AndroidContractStateViewModel<ProfilesUiState, ProfilesUiEffect>(
        application,
        ProfilesUiState(),
    ) {

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    /** Whether the proxy is currently running. */
    val isRunning: StateFlow<Boolean> = proxyControl.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The currently active profile, if any. */
    val currentProfile: StateFlow<Profile?> = proxyControl.currentProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** User-defined override configs. */
    val userConfigs: StateFlow<List<OverrideConfig>> = overrideConfigRepository
        .getUserConfigsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Built-in override configs (loaded once). */
    val builtInConfigs: StateFlow<List<OverrideConfig>> = flow {
        emit(overrideConfigRepository.getBuiltInConfigs())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Stop the proxy. Delegates to ProxyControlContract. */
    suspend fun stopProxy() { proxyControl.stopProxy() }

    private var toggleJob: Job? = null

    init {
        refreshProfiles()
    }

    @Suppress("TooGenericExceptionCaught")
    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                setLoading(true)
                _profiles.value = profileCrud.queryAllProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to refresh profiles")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        interval: Long = 0L,
        fileUri: Uri? = null,
        ageSecretKey: String = "",
    ) {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value =
                    DownloadProgress(percent = 0, message = FlyTxt.ProfilesVM.Progress.Preparing)

                val uuid = profileCrud.createProfile(
                    type = type, name = name, source = source, ageSecretKey = ageSecretKey,
                    onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                )

                if (uuid == null) {
                    showError(FlyTxt.ProfilesVM.Message.AddFailed.format("Unknown"))
                    _downloadProgress.value = null
                    return@launch
                }

                if (type == Profile.Type.File && fileUri != null) {
                    getApplication<Application>().copyProfileImport(fileUri, uuid)
                }

                _downloadProgress.value =
                    DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                showMessage(FlyTxt.ProfilesVM.Message.ProfileAdded.format(name))
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to create profile")
                refreshProfiles()
                showError(FlyTxt.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun cloneProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                val newUuid = profileCrud.cloneProfile(uuid)
                if (newUuid != null) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileAdded.format("Clone"))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.AddFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to clone profile")
                showError(FlyTxt.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun deleteProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                val profile = _profiles.value.find { it.uuid == uuid }
                if (profile?.active == true && isRunning.value) {
                    showError(FlyTxt.ProfilesVM.Message.CannotDeleteActiveProfile)
                    return@launch
                }
                if (profileCrud.deleteProfile(uuid)) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileDeleted)
                } else {
                    showError(FlyTxt.ProfilesVM.Message.DeleteFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to delete profile")
                showError(FlyTxt.ProfilesVM.Message.DeleteFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun activateProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                if (profileCrud.activateProfile(uuid)) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format("Active"))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to activate profile")
                showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    fun updateAllUrlProfiles() {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value = DownloadProgress(0, FlyTxt.ProfilesVM.Progress.Preparing)
                val count = profileCrud.updateAllUrlProfiles(
                    profiles = _profiles.value,
                    onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                )
                if (count > 0) {
                    _downloadProgress.value =
                        DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                    showMessage(FlyTxt.ProfilesPage.Action.UpdateAll)
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to update all url profiles")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    fun updateProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                _downloadProgress.value = DownloadProgress(percent = 0, message = FlyTxt.ProfilesVM.Progress.Preparing)
                val success = profileCrud.updateProfile(
                    uuid = uuid,
                    onProgress = { status -> _downloadProgress.value = status.toDownloadProgress() },
                )
                if (success) {
                    _downloadProgress.value =
                        DownloadProgress(percent = 100, message = FlyTxt.ProfilesVM.Progress.ImportComplete, isCompleted = true)
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format(uuid.toString()))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format("Unknown"))
                    _downloadProgress.value = null
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to update profile")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun patchProfile(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String? = null,
    ) {
        viewModelScope.launch {
            try {
                setLoading(true)
                if (profileCrud.patchProfile(uuid, name, source, interval, ageSecretKey)) {
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format(name))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to patch profile")
                showError(FlyTxt.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    fun importProfileFromFile(uri: Uri, name: String) {
        createProfile(type = Profile.Type.File, name = name, fileUri = uri)
    }

    @Suppress("TooGenericExceptionCaught")
    fun reorderProfiles(from: Int, to: Int) {
        viewModelScope.launch {
            try {
                val current = _profiles.value
                if (from !in current.indices || to !in current.indices || from == to) return@launch

                val reordered = current.toMutableList()
                val moved = reordered.removeAt(from)
                reordered.add(to, moved)

                _profiles.value = reordered
                profileCrud.reorderProfiles(reordered.map { it.uuid })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to reorder profiles")
                refreshProfiles()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun toggleProfileEnabled(uuid: UUID) {
        toggleJob?.cancel()
        toggleJob = viewModelScope.launch {
            try {
                val newState = profileCrud.toggleProfileEnabled(uuid)
                if (newState != null) {
                    val profile = profilesRepository.queryProfileByUUID(uuid)
                    showMessage(FlyTxt.ProfilesVM.Message.ProfileUpdated.format(profile?.name ?: ""))
                } else {
                    showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format("Unknown"))
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to toggle profile")
                showError(FlyTxt.ProfilesVM.Message.ToggleFailed.format(error.message ?: "Unknown"))
            }
        }
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    fun clearError() {
        clearErrorState()
    }

    fun clearMessage() {
        clearMessageState()
    }

    suspend fun getBinding(profileId: String): ProfileBinding? =
        bindingProvider.getBinding(profileId)

    suspend fun saveOverrideBinding(
        profileId: String,
        overrideIds: List<String>,
        applyNow: Boolean,
    ): ProfileBinding? {
        val normalizedIds = overrideIds.distinct()
        val current = bindingProvider.getBinding(profileId)
        val updated = current?.copy(overrideIds = normalizedIds)
            ?: ProfileBinding(profileId = profileId, overrideIds = normalizedIds)
        bindingProvider.setBinding(updated)
        if (applyNow) OverrideApplyExecutor.applyOverride(profileId)
        return bindingProvider.getBinding(profileId)
    }

    private fun showError(message: String) {
        postError(message, ProfilesUiEffect.ShowError(message))
    }

    private fun showMessage(message: String) {
        postMessage(message, ProfilesUiEffect.ShowMessage(message))
    }
}
