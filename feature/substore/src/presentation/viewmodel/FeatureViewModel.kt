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

package com.github.lmfirefly.flycat.feature.substore.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.Preference
import com.github.lmfirefly.flycat.core.contract.SubStoreSettings
import com.github.lmfirefly.flycat.core.model.profile.LinkOpenMode
import com.github.lmfirefly.flycat.core.util.path.SubStorePaths
import com.github.lmfirefly.flycat.feature.substore.SubStoreServiceController
import com.github.lmfirefly.flycat.feature.substore.SubStoreServiceRequest
import com.github.lmfirefly.flycat.feature.substore.engine.NativeLibraryManager
import com.github.lmfirefly.flycat.feature.substore.model.AutoCloseMode
import com.github.lmfirefly.flycat.feature.substore.util.SubStoreDownloadClient
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.util.showToastDialog
import com.github.lmfirefly.flycat.ui.platform.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class FeatureViewModel(
    store: SubStoreSettings,
    private val application: Application,
    private val downloadClient: SubStoreDownloadClient,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    val allowLanAccess: Preference<Boolean> = store.allowLanAccess
    val backendPort: Preference<Int> = store.backendPort
    val frontendPort: Preference<Int> = store.frontendPort
    val selectedPanelType: Preference<Int> = store.selectedPanelType
    val panelOpenMode: Preference<LinkOpenMode> = store.panelOpenMode
    val exitUiWhenBackground: Preference<Boolean> = store.exitUiWhenBackground
    private val subStoreAutoCloseModeOrdinal: Preference<Int> = store.subStoreAutoCloseModeOrdinal

    private val _autoCloseMode = MutableStateFlow(autoCloseModeFromOrdinal(subStoreAutoCloseModeOrdinal.value))
    val autoCloseMode: StateFlow<AutoCloseMode> = _autoCloseMode.asStateFlow()

    val serviceRunningState: StateFlow<Boolean> =
        SubStoreServiceController.snapshot
            .map { it.isRunning }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SubStoreServiceController.snapshot.value.isRunning,
            )

    private var autoCloseJob: Job? = null

    companion object {
        private const val JAVET_RELEASE_BASE_URL = "https://github.com/LM-Firefly/FlyCat/releases/download/libjavet"
        private fun javetReleaseUrl(): String {
            val abi = android.os.Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: "arm64-v8a"
            return "$JAVET_RELEASE_BASE_URL/libjavet-${abi}.so.xz"
        }
    }

    private val _isDownloadingSubStoreFrontend = MutableStateFlow(false)
    val isDownloadingSubStoreFrontend: StateFlow<Boolean> =
        _isDownloadingSubStoreFrontend.asStateFlow()

    private val _isDownloadingSubStoreBackend = MutableStateFlow(false)
    val isDownloadingSubStoreBackend: StateFlow<Boolean> =
        _isDownloadingSubStoreBackend.asStateFlow()

    private val _isSubStoreInitialized = MutableStateFlow(false)
    val isSubStoreInitialized: StateFlow<Boolean> = _isSubStoreInitialized.asStateFlow()

    private val _isDownloadingJavet = MutableStateFlow(false)
    val isDownloadingJavet: StateFlow<Boolean> = _isDownloadingJavet.asStateFlow()

    private val _isJavetLoaded = MutableStateFlow(false)
    val isJavetLoaded: StateFlow<Boolean> = _isJavetLoaded.asStateFlow()

    fun startService() {
        if (DeviceUtils.is32BitDevice()) {
            Timber.w("Sub-Store start skipped: 32-bit device")
            showToast(FlyTxt.Feature.SubStore.Not32Bit)
            return
        }
        if (!checkSubStoreReadiness()) return
        Timber.w(
            "Sub-Store start requested: frontendPort=${frontendPort.value}, backendPort=${backendPort.value}, allowLan=${allowLanAccess.value}, autoClose=${_autoCloseMode.value}"
        )
        viewModelScope.launch {
            runCatching {
                    SubStoreServiceController.startService(
                        context = application,
                        request =
                            SubStoreServiceRequest(
                                backendPort = backendPort.value,
                                frontendPort = frontendPort.value,
                                allowLan = allowLanAccess.value,
                            ),
                    )
                }
                .onSuccess {
                    Timber.w("Sub-Store startService() dispatched to Android Service")
                    setupAutoCloseTimer()
                }
                .onFailure { error ->
                    Timber.e(error, "Sub-Store start dispatch failed")
                    showToast(error.message ?: FlyTxt.Util.Error.UnknownError)
                }
        }
    }

    private fun checkSubStoreReadiness(): Boolean {
        return when {
            !_isJavetLoaded.value -> {
                Timber.w("Sub-Store readiness failed: javetLoaded=${_isJavetLoaded.value}")
                showToast(FlyTxt.Feature.SubStore.JavetNotReady)
                false
            }

            !_isSubStoreInitialized.value -> {
                Timber.w(
                    "Sub-Store readiness failed: resources not ready (frontendReady=${SubStorePaths.isFrontendReady()}, backendReady=${SubStorePaths.isBackendReady()})"
                )
                showToast(FlyTxt.Feature.SubStore.DownloadSubStoreFirst)
                false
            }

            else -> true
        }
    }

    fun stopService() {
        viewModelScope.launch {
            cancelAutoCloseTimer()
            SubStoreServiceController.stopService(application)
            _autoCloseMode.value = AutoCloseMode.DISABLED
            subStoreAutoCloseModeOrdinal.set(AutoCloseMode.DISABLED.ordinal)
        }
    }

    fun setAllowLanAccess(allow: Boolean) = allowLanAccess.set(allow)

    fun setAutoCloseMode(mode: AutoCloseMode) {
        subStoreAutoCloseModeOrdinal.set(mode.ordinal)
        _autoCloseMode.value = mode
        val serviceActive = SubStoreServiceController.snapshot.value.isActive
        when {
            mode == AutoCloseMode.DISABLED && serviceRunningState.value -> stopService()
            mode != AutoCloseMode.DISABLED && !serviceActive -> startService()
            serviceRunningState.value -> {
                cancelAutoCloseTimer()
                setupAutoCloseTimer()
            }
        }
    }

    fun initializeSubStoreStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _autoCloseMode.value = autoCloseModeFromOrdinal(subStoreAutoCloseModeOrdinal.value)
            _isSubStoreInitialized.value = SubStorePaths.isResourcesReady()
            NativeLibraryManager.initialize(application)
            val available = NativeLibraryManager.isLibraryAvailable(NativeLibraryManager.JAVET_LIBRARY_NAME)
            _isJavetLoaded.value = if (available) {
                NativeLibraryManager.loadJniLibrary(NativeLibraryManager.JAVET_LIBRARY_NAME)
            } else false
            Timber.w(
                "Sub-Store status initialized: autoClose=${_autoCloseMode.value}, resourcesReady=${_isSubStoreInitialized.value}, javetAvailable=$available, javetLoaded=${_isJavetLoaded.value}, serviceRunning=${serviceRunningState.value}"
            )
            tryStartServiceIfConfigured()
        }
    }

    fun refreshExtensionStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            NativeLibraryManager.initialize(application)
            val available = NativeLibraryManager.isLibraryAvailable(NativeLibraryManager.JAVET_LIBRARY_NAME)
            _isJavetLoaded.value = if (available) {
                NativeLibraryManager.loadJniLibrary(NativeLibraryManager.JAVET_LIBRARY_NAME)
            } else false
            tryStartServiceIfConfigured()
        }
    }

    fun downloadJavetLibrary() {
        if (_isDownloadingJavet.value) return
        viewModelScope.launch {
            _isDownloadingJavet.value = true
            val wasLoaded = _isJavetLoaded.value
            val installed = runCatching {
                NativeLibraryManager.initialize(application)
                val tempFile = requireNotNull(NativeLibraryManager.getDownloadTempFile(NativeLibraryManager.JAVET_LIBRARY_NAME))
                tempFile.delete()
                val url = javetReleaseUrl()
                Timber.d("Javet download: starting from $url")
                val downloadOk = downloadClient.download(url, tempFile)
                Timber.d("Javet download: result=$downloadOk, fileSize=${tempFile.length()}")
                if (!downloadOk) error("Download failed from $url")
                val installOk = NativeLibraryManager.installDownloadedArchive(NativeLibraryManager.JAVET_LIBRARY_NAME, tempFile)
                Timber.d("Javet install: result=$installOk")
                if (!installOk) error("XZ decompression or file installation failed")
                installOk
            }.getOrElse { error ->
                Timber.e(error, "Javet installation failed")
                showToast(FlyTxt.Feature.SubStore.DownloadError.format(error.message ?: FlyTxt.Util.Error.UnknownError))
                false
            }
            if (installed) {
                // Try loading after successful install
                val loadOk = NativeLibraryManager.loadJniLibrary(NativeLibraryManager.JAVET_LIBRARY_NAME)
                Timber.d("Javet load after install: result=$loadOk")
                _isJavetLoaded.value = loadOk
                if (loadOk) {
                    showToast(FlyTxt.Feature.SubStore.JavetDownloadSuccess)
                } else {
                    showToast(FlyTxt.Feature.SubStore.JavetNotReady)
                }
            } else {
                _isJavetLoaded.value = wasLoaded
                showToast(FlyTxt.Feature.SubStore.JavetDownloadFailed)
            }
            _isDownloadingJavet.value = false
        }
    }

    fun setSelectedPanelType(panelType: Int) {
        selectedPanelType.set(panelType)
    }

    fun setPanelOpenMode(mode: LinkOpenMode) = panelOpenMode.set(mode)

    fun setExitUiWhenBackground(enabled: Boolean) = exitUiWhenBackground.set(enabled)

    fun downloadSubStoreFrontend() {
        launchResourceDownload(
            loadingState = _isDownloadingSubStoreFrontend,
            successMessage = FlyTxt.Feature.SubStore.FrontendDownloadSuccess,
            failureMessage = FlyTxt.Feature.SubStore.FrontendDownloadFailed,
        ) {
            SubStorePaths.ensureStructure()
            SubStorePaths.frontendDir.apply { if (!exists()) mkdirs() }
            downloadClient.downloadAndExtract(
                url = "https://github.com/sub-store-org/Sub-Store-Front-End/releases/latest/download/dist.zip",
                targetDir = SubStorePaths.frontendDir,
            )
        }
    }

    fun downloadSubStoreBackend() {
        launchResourceDownload(
            loadingState = _isDownloadingSubStoreBackend,
            successMessage = FlyTxt.Feature.SubStore.BackendDownloadSuccess,
            failureMessage = FlyTxt.Feature.SubStore.BackendDownloadFailed,
        ) {
            SubStorePaths.ensureStructure()
            SubStorePaths.backendDir.apply { if (!exists()) mkdirs() }
            downloadClient.download(
                url = "https://github.com/sub-store-org/Sub-Store/releases/latest/download/sub-store.bundle.js",
                targetFile = SubStorePaths.backendBundle,
            )
        }
    }

    fun downloadSubStoreAll() {
        viewModelScope.launch {
            if (_isDownloadingSubStoreFrontend.value || _isDownloadingSubStoreBackend.value)
                return@launch
            downloadSubStoreFrontend()
            _isDownloadingSubStoreFrontend.first { !it }
            downloadSubStoreBackend()
        }
    }

    private fun showToast(msg: String) = showToastDialog(msg)

    private fun launchResourceDownload(
        loadingState: MutableStateFlow<Boolean>,
        successMessage: String,
        failureMessage: String,
        action: suspend () -> Boolean,
    ) {
        if (loadingState.value) return
        viewModelScope.launch {
            loadingState.value = true
            runCatching {
                    val success = withContext(Dispatchers.IO) { action() }
                    showToast(if (success) successMessage else failureMessage)
                    if (success) {
                        _isSubStoreInitialized.value = SubStorePaths.isResourcesReady()
                        tryStartServiceIfConfigured()
                    }
                }
                .onFailure { error ->
                    showToast(
                        FlyTxt.Feature.SubStore.DownloadError.format(
                            error.message ?: FlyTxt.Util.Error.UnknownError
                        )
                    )
                }
            loadingState.value = false
        }
    }

    private fun setupAutoCloseTimer() {
        cancelAutoCloseTimer()
        val mode = _autoCloseMode.value
        mode.minutes?.let { minutes ->
            autoCloseJob = applicationScope.launch {
                val timeoutMillis = minutes * 60 * 1000L
                delay(timeoutMillis)
                showToast(FlyTxt.Feature.ServiceStatus.AutoClosed)
                runCatching { SubStoreServiceController.stopService(application) }
                _autoCloseMode.value = AutoCloseMode.DISABLED
                subStoreAutoCloseModeOrdinal.set(AutoCloseMode.DISABLED.ordinal)
            }
        }
    }

    private fun cancelAutoCloseTimer() {
        autoCloseJob?.cancel()
        autoCloseJob = null
    }

    private fun tryStartServiceIfConfigured() {
        Timber.w(
            "Sub-Store auto-start check: autoClose=${_autoCloseMode.value}, serviceRunning=${serviceRunningState.value}"
        )
        if (
            _autoCloseMode.value != AutoCloseMode.DISABLED &&
                !SubStoreServiceController.snapshot.value.isActive
        ) {
            startService()
        }
    }

    private fun autoCloseModeFromOrdinal(ordinal: Int): AutoCloseMode {
        return AutoCloseMode.entries.getOrElse(ordinal) { AutoCloseMode.DISABLED }
    }
}
