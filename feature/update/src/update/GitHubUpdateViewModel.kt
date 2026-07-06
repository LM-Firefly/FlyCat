package com.github.yumelira.yumebox.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

data class GitHubUpdateUiState(
    val isChecking: Boolean = false,
    val source: UpdateSource = UpdateSource.Latest,
    val candidate: UpdateCandidate? = null,
    val message: String? = null,
)

class GitHubUpdateViewModel(
    private val updateManager: GitHubUpdateManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        GitHubUpdateUiState(source = updateManager.getSelectedSource()),
    )
    val uiState: StateFlow<GitHubUpdateUiState> = _uiState.asStateFlow()
    val downloadProgress: StateFlow<UpdateDownloadProgress> = updateManager.downloadProgress
    private var downloadJob: Job? = null
    fun setSource(source: UpdateSource) {
        if (_uiState.value.source == source) return
        updateManager.setSelectedSource(source)
        _uiState.value = _uiState.value.copy(source = source, candidate = null, message = null)
    }
    fun checkForUpdate() {
        if (_uiState.value.isChecking) return
        val source = _uiState.value.source
        Timber.i("Manual update check requested: source=%s", source.key)
        Timber.i("Manual update check bypass cache: source=%s", source.key)
        _uiState.value = _uiState.value.copy(isChecking = true, message = null)
        viewModelScope.launch {
            updateManager.checkForUpdate(source = source, isManualCheck = true)
                .onSuccess { candidate ->
                    Timber.i(
                        "Manual update check finished: source=%s hasCandidate=%s tag=%s version=%s",
                        source.key,
                        candidate != null,
                        candidate?.tag.orEmpty(),
                        candidate?.versionName.orEmpty(),
                    )
                    _uiState.value = GitHubUpdateUiState(
                        isChecking = false,
                        source = source,
                        candidate = candidate,
                        message = if (candidate == null) MLang.Component.Update.Message.NoUpdate else null,
                    )
                }
                .onFailure { throwable ->
                    Timber.w(throwable, "Manual update check failed: source=%s", source.key)
                    _uiState.value = GitHubUpdateUiState(
                        isChecking = false,
                        source = source,
                        message = MLang.Component.Update.Message.CheckFailed.format(
                            throwable.message ?: MLang.Util.Error.UnknownError,
                        ),
                    )
                }
        }
    }
    fun downloadAndInstall(candidate: UpdateCandidate, selectedPackage: UpdateManifestPackage? = null) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            updateManager.downloadAndInstall(candidate, selectedPackage)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        candidate = null,
                        message = MLang.Component.Update.Message.InstallPromptOpened,
                    )
                }
                .onFailure { throwable ->
                    val message = if (throwable.isUpdateDownloadCancelled()) {
                        MLang.Component.Button.Cancel
                    } else {
                        MLang.Component.Update.Message.InstallFailed.format(
                            throwable.message ?: MLang.Util.Error.UnknownError,
                        )
                    }
                    _uiState.value = _uiState.value.copy(message = message)
                }
        }
    }
    fun cancelDownload() {
        updateManager.cancelDownload()
    }
    fun dismissCandidate() {
        _uiState.value = _uiState.value.copy(candidate = null)
    }
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
