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

package com.github.yumelira.yumebox.feature.profiles.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.contract.OverrideApplyExecutor
import com.github.yumelira.yumebox.core.contract.ProfileBindingReader
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProfileBinding
import com.github.yumelira.yumebox.feature.profiles.presentation.screen.copyProfileImport
import com.github.yumelira.yumebox.presentation.viewmodel.AndroidContractStateViewModel
import com.github.yumelira.yumebox.runtime.api.contract.ProfileRepositoryContract
import com.github.yumelira.yumebox.runtime.api.service.remote.IFetchObserver
import dev.oom_wg.purejoy.mlang.MLang
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ProfilesViewModel(
    application: Application,
    private val profilesRepository: ProfileRepositoryContract,
    private val bindingProvider: ProfileBindingReader,
    private val OverrideApplyExecutor: OverrideApplyExecutor,
) :
    AndroidContractStateViewModel<ProfilesUiState, ProfilesUiEffect>(
        application,
        ProfilesUiState(),
    ) {

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private var toggleJob: Job? = null

    init {
        refreshProfiles()
    }

    // Fault barrier: any refresh failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                setLoading(true)
                val allProfiles = profilesRepository.queryAllProfiles()

                _profiles.value = allProfiles
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to refresh profiles")
                showError(MLang.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    // Fault barrier: any create failure rolls back the staged profile and becomes UI error
    // state (CE rethrown).
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
            var createdUuid: UUID? = null
            try {
                setLoading(true)
                val uuid = profilesRepository.createProfile(type, name, source, ageSecretKey)
                createdUuid = uuid

                _downloadProgress.value =
                    DownloadProgress(percent = 0, message = MLang.ProfilesVM.Progress.Preparing)

                val observer = IFetchObserver { status ->
                    _downloadProgress.value = status.toDownloadProgress()
                }

                if (type == Profile.Type.File && fileUri != null) {
                    getApplication<Application>().copyProfileImport(fileUri, uuid)
                }

                profilesRepository.updateProfile(uuid, observer)
                _downloadProgress.value =
                    DownloadProgress(
                        percent = 100,
                        message = MLang.ProfilesVM.Progress.ImportComplete,
                        isCompleted = true,
                    )

                showMessage(MLang.ProfilesVM.Message.ProfileAdded.format(name))
                refreshProfiles()
                Timber.i("Profile created: $uuid")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to create profile")
                createdUuid?.let { uuid ->
                    runCatching { profilesRepository.deleteProfile(uuid) }
                        .onFailure { deleteError ->
                            Timber.w(deleteError, "Failed to rollback profile creation: $uuid")
                        }
                }
                refreshProfiles()
                showError(MLang.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    // Fault barrier: any clone failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    fun cloneProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                val newUuid = profilesRepository.cloneProfile(uuid)
                showMessage(MLang.ProfilesVM.Message.ProfileAdded.format("Clone"))
                refreshProfiles()
                Timber.i("Profile cloned: from=$uuid to=$newUuid")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to clone profile")
                showError(MLang.ProfilesVM.Message.AddFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    // Fault barrier: any delete failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    fun deleteProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                profilesRepository.deleteProfile(uuid)
                showMessage(MLang.ProfilesVM.Message.ProfileDeleted)
                refreshProfiles()
                Timber.i("Profile deleted: $uuid")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to delete profile")
                showError(MLang.ProfilesVM.Message.DeleteFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    // Fault barrier: any activate failure is surfaced as UI error state (CE rethrown).
    @Suppress("TooGenericExceptionCaught")
    fun activateProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                setLoading(true)
                profilesRepository.setActiveProfile(uuid)
                showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format("Active"))
                refreshProfiles()
                Timber.i("Profile activated: $uuid")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to activate profile")
                showError(MLang.ProfilesVM.Message.ToggleFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    fun updateAllUrlProfiles() {
        viewModelScope.launch {
            try {
                setLoading(true)
                val targets = _profiles.value.filter { it.type == Profile.Type.Url }
                for (profile in targets) {
                    _downloadProgress.value =
                        DownloadProgress(0, MLang.ProfilesVM.Progress.Preparing)
                    profilesRepository.updateProfile(
                        profile.uuid,
                        IFetchObserver { status ->
                            _downloadProgress.value = status.toDownloadProgress()
                        },
                    )
                }
                if (targets.isNotEmpty()) {
                    _downloadProgress.value =
                        DownloadProgress(
                            percent = 100,
                            message = MLang.ProfilesVM.Progress.ImportComplete,
                            isCompleted = true,
                        )
                    showMessage(MLang.ProfilesPage.Action.UpdateAll)
                }
                refreshProfiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to update all url profiles")
                showError(MLang.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
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
                _downloadProgress.value =
                    DownloadProgress(percent = 0, message = MLang.ProfilesVM.Progress.Preparing)

                val observer = IFetchObserver { status ->
                    _downloadProgress.value = status.toDownloadProgress()
                }

                profilesRepository.updateProfile(uuid, observer)

                _downloadProgress.value =
                    DownloadProgress(
                        percent = 100,
                        message = MLang.ProfilesVM.Progress.ImportComplete,
                        isCompleted = true,
                    )
                showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(uuid.toString()))
                refreshProfiles()
                Timber.i("Profile updated: $uuid")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to update profile")
                showError(MLang.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                setLoading(false)
            }
        }
    }

    // Fault barrier: any patch failure is surfaced as UI error state (CE rethrown).
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
                profilesRepository.patchProfile(
                    uuid = uuid,
                    name = name,
                    source = source,
                    interval = interval,
                    ageSecretKey = ageSecretKey,
                )
                showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(name))
                refreshProfiles()
                Timber.i("Profile patched: $uuid")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to patch profile")
                showError(MLang.ProfilesVM.Message.UpdateFailed.format(error.message ?: "Unknown"))
            } finally {
                setLoading(false)
            }
        }
    }

    fun importProfileFromFile(uri: Uri, name: String) {
        createProfile(type = Profile.Type.File, name = name, fileUri = uri)
    }

    // Fault barrier: any reorder failure logs and re-syncs the list from storage (CE rethrown).
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
                profilesRepository.reorderProfiles(reordered.map { it.uuid })
                Timber.d("Profiles reordered: $from->$to")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to reorder profiles")
                refreshProfiles()
            }
        }
    }

    // Fault barrier: any toggle failure is surfaced as UI error state (CE rethrown).
    // Cancels the previous toggle coroutine to prevent concurrent profile switches.
    @Suppress("TooGenericExceptionCaught")
    fun toggleProfileEnabled(uuid: UUID) {
        toggleJob?.cancel()
        toggleJob = viewModelScope.launch {
            try {
                val profile =
                    profilesRepository.queryProfileByUUID(uuid) ?: error("Profile not found: $uuid")

                if (profile.active) {
                    profilesRepository.clearActiveProfile(profile)
                    showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(profile.name))
                } else {
                    profilesRepository.setActiveProfile(uuid)
                    showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(profile.name))
                }
                refreshProfiles()
                Timber.d("Profile toggled: $uuid, active=${!profile.active}")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.e(error, "Failed to toggle profile")
                showError(MLang.ProfilesVM.Message.ToggleFailed.format(error.message ?: "Unknown"))
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
