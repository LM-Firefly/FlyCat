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

package com.github.lmfirefly.flycat.feature.profiles.presentation.screen

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.DownloadProgress
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetConfirmAction
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.ProfileImportType
import com.github.lmfirefly.flycat.presentation.util.isYamlConfigFileName
import com.github.lmfirefly.flycat.presentation.util.profileNameFromConfigFileName
import com.github.lmfirefly.flycat.presentation.util.readClipboardSubscriptionUrl
import com.github.lmfirefly.flycat.presentation.util.readDisplayName
import com.github.lmfirefly.flycat.presentation.util.sourceFileName
import com.github.lmfirefly.flycat.presentation.util.toast
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
internal fun AddProfileSheet(
    show: MutableState<Boolean>,
    profileToEdit: Profile? = null,
    importUrl: String? = null,
    onAddProfile:
        (
            name: String,
            source: String,
            type: Profile.Type,
            interval: Long,
            fileUri: android.net.Uri?,
            ageSecretKey: String,
        ) -> Unit,
    onUpdateProfile: (uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String?) -> Unit,
    onDownloadComplete: () -> Unit,
    profilesViewModel: ProfilesViewModel,
) {
    val configuration = LocalConfiguration.current
    val downloadSheetContentHeight = configuration.screenHeightDp.dp * 0.3f
    val downloadCompleteSheetContentHeight = configuration.screenHeightDp.dp * 0.42f
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedImportType by remember { mutableStateOf(ProfileImportType.Url) }
    val nameState = rememberTextFieldState()
    val urlState = rememberTextFieldState()
    var filePath by remember { mutableStateOf("") }
    val fileNameState = rememberTextFieldState()
    val ageSecretKeyState = rememberTextFieldState()
    var initialAgeSecretKey by remember { mutableStateOf("") }
    val intervalState = rememberTextFieldState()
    var error by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    val downloadProgress by profilesViewModel.downloadProgress.collectAsStateWithLifecycle()
    val uiState by profilesViewModel.uiState.collectAsStateWithLifecycle()
    var hasShownCompleteAnimation by remember { mutableStateOf(false) }
    var stableSheetHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(show.value) {
        if (!show.value) {
            hasShownCompleteAnimation = false
            isDownloading = false
        }
    }
    LaunchedEffect(nameState, urlState, intervalState) {
        snapshotFlow {
            Triple(nameState.text.toString(), urlState.text.toString(), intervalState.text.toString())
        }.drop(1).collect { error = "" }
    }

    val applyNameText: (String) -> Unit = { updatedText ->
        nameState.setTextAndPlaceCursorAtEnd(updatedText)
    }
    val applyUrlText: (String) -> Unit = { updatedText ->
        urlState.setTextAndPlaceCursorAtEnd(updatedText)
    }
    val applyFileNameText: (String) -> Unit = { updatedText ->
        fileNameState.setTextAndPlaceCursorAtEnd(updatedText)
    }

    val clearAllState = {
        applyNameText("")
        applyUrlText("")
        filePath = ""
        applyFileNameText("")
        ageSecretKeyState.setTextAndPlaceCursorAtEnd("")
        initialAgeSecretKey = ""
        intervalState.setTextAndPlaceCursorAtEnd("")
        error = ""
        isDownloading = false
        hasShownCompleteAnimation = false
    }

    val clearCurrentTypeState = {
        when (selectedImportType) {
            ProfileImportType.Url -> applyUrlText("")
            ProfileImportType.LocalFile -> {
                filePath = ""
                applyFileNameText("")
            }
            ProfileImportType.Qr -> {}
        }
        error = ""
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
        isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            context.toast(FlyTxt.ProfilesPage.QrScanner.NeedCamera, Toast.LENGTH_LONG)
            selectedImportType = ProfileImportType.Url
        }
    }

    LaunchedEffect(selectedImportType) {
        if (selectedImportType == ProfileImportType.Qr && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val showCameraPreview by remember(show.value, selectedImportType, isDownloading, hasCameraPermission) {
        derivedStateOf {
            show.value &&
                selectedImportType == ProfileImportType.Qr &&
                !isDownloading &&
                hasCameraPermission
        }
    }

    DisposableEffect(show.value, profileToEdit, importUrl) {
        if (show.value) {
            clearAllState()
            if (profileToEdit != null) {
                applyNameText(profileToEdit.name)
                ageSecretKeyState.setTextAndPlaceCursorAtEnd(profileToEdit.ageSecretKey)
                initialAgeSecretKey = profileToEdit.ageSecretKey
                if (profileToEdit.interval > 0) {
                    intervalState.setTextAndPlaceCursorAtEnd(profileToEdit.interval.toString())
                }
                if (profileToEdit.type == Profile.Type.Url) {
                    selectedImportType = ProfileImportType.Url
                    applyUrlText(profileToEdit.source)
                } else {
                    selectedImportType = ProfileImportType.fromProfile(profileToEdit.type)
                    filePath = profileToEdit.source
                    applyFileNameText(sourceFileName(profileToEdit.source))
                }
            } else if (!importUrl.isNullOrBlank()) {
                selectedImportType = ProfileImportType.Url
                applyUrlText(importUrl)
            } else {
                selectedImportType = ProfileImportType.Url
                readClipboardSubscriptionUrl(context)?.let(applyUrlText)
            }
        }
        onDispose {}
    }
    LaunchedEffect(uiState.error) {
        val errorMessage = uiState.error
        if (errorMessage != null) {
            context.toast(errorMessage, Toast.LENGTH_LONG)
            if (isDownloading) {
                isDownloading = false
                error = errorMessage
            }
            profilesViewModel.clearError()
        }
    }

    LaunchedEffect(downloadProgress?.isCompleted, isDownloading) {
        if (isDownloading && downloadProgress?.isCompleted == true && !hasShownCompleteAnimation) {
            hasShownCompleteAnimation = true
            onDownloadComplete()
        }
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null && isDownloading && !hasShownCompleteAnimation) {
            hasShownCompleteAnimation = true
            onDownloadComplete()
        }
        if (uiState.message != null) {
            profilesViewModel.clearMessage()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val actualFileName = readDisplayName(context, it, FlyTxt.ProfilesPage.Message.UnknownFile)

            if (!isYamlConfigFileName(actualFileName)) {
                error = FlyTxt.ProfilesPage.Validation.YamlOnly
                return@let
            }

            filePath = it.toString()
            error = ""
            applyFileNameText(actualFileName)

            if (
                nameState.text.isBlank() || nameState.text.toString() == actualFileName
            ) {
                applyNameText(
                    profileNameFromConfigFileName(
                        actualFileName,
                        FlyTxt.ProfilesPage.Input.NewProfile,
                    )
                )
            }
        }
    }

    val qrImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                // QR 解码故障屏障：任何 ML Kit/IO 故障都以 toast 提示。
                @Suppress("TooGenericExceptionCaught")
                try {
                    val result = readQrFromImage(context, it)
                    if (result != null) {
                        applyUrlText(result)
                        selectedImportType = ProfileImportType.Url
                        context.toast(FlyTxt.ProfilesPage.QrScanner.RecognizeSuccess)
                    } else {
                        context.toast(FlyTxt.ProfilesPage.QrScanner.RecognizeFailed)
                    }
                } catch (error: Exception) {
                    context.toast(
                        FlyTxt.ProfilesPage.QrScanner.RecognizeError.format(error.message ?: "")
                    )
                }
            }
        }
    }

    val dismissSheet = {
        if (!isDownloading) {
            show.value = false
            profilesViewModel.clearDownloadProgress()
        }
    }

    fun submitProfile() {
        if (selectedImportType == ProfileImportType.Qr || isDownloading) {
            return
        }
        if (selectedImportType == ProfileImportType.Url && urlState.text.isBlank()) {
            error = FlyTxt.ProfilesPage.Validation.EnterUrl
            return
        }
        if (selectedImportType == ProfileImportType.LocalFile && filePath.isBlank()) {
            error = FlyTxt.ProfilesPage.Validation.SelectFile
            return
        }
        keyboardController?.hide()
        profilesViewModel.clearError()
        hasShownCompleteAnimation = false
        isDownloading = true
        val trimmedAgeSecretKey = ageSecretKeyState.text.toString().trim()
        val ageKeyUpdate = if (trimmedAgeSecretKey != initialAgeSecretKey) trimmedAgeSecretKey else null
        if (selectedImportType == ProfileImportType.Url) {
            val newInterval = intervalState.text.toString().toLongOrNull() ?: 0L
            if (profileToEdit != null) {
                onUpdateProfile(
                    profileToEdit.uuid,
                    nameState.text.toString(),
                    urlState.text.toString(),
                    newInterval,
                    ageKeyUpdate,
                )
            } else {
                onAddProfile(
                    nameState.text.toString().ifBlank { FlyTxt.ProfilesPage.Input.NewProfile },
                    urlState.text.toString(),
                    Profile.Type.Url,
                    newInterval,
                    null,
                    trimmedAgeSecretKey,
                )
            }
        } else {
            if (profileToEdit != null) {
                onUpdateProfile(
                    profileToEdit.uuid,
                    nameState.text.toString(),
                    profileToEdit.source,
                    profileToEdit.interval,
                    ageKeyUpdate,
                )
            } else {
                onAddProfile(
                    nameState.text.toString().ifBlank { FlyTxt.ProfilesPage.Input.NewProfile },
                    filePath,
                    Profile.Type.File,
                    0L,
                    filePath.toUri(),
                    trimmedAgeSecretKey,
                )
            }
        }
    }

    AppActionBottomSheet(
        show = show.value,
        title = if (profileToEdit != null) {
            FlyTxt.ProfilesPage.Sheet.EditTitle
        } else {
            FlyTxt.ProfilesPage.Sheet.AddTitle
        },
        startAction = {
            if (!isDownloading) {
                AppBottomSheetCloseAction(contentDescription = FlyTxt.Component.Action.Cancel, onClick = dismissSheet)
            }
        },
        endAction = {
            if (!isDownloading && selectedImportType != ProfileImportType.Qr) {
                AppBottomSheetConfirmAction(
                    contentDescription = "Confirm",
                    onClick = { submitProfile() },
                )
            }
        },
        onDismissRequest = dismissSheet,
    ) {
        val stableSheetHeight = remember(stableSheetHeightPx, density) {
            if (stableSheetHeightPx <= 0) {
                UiDp.dp0
            } else {
                with(density) { stableSheetHeightPx.toDp() }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                .animateContentSize(animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.StandardEasing))
                .padding(bottom = UiDp.dp16)
        ) {
            AnimatedContent(
                targetState = isDownloading,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(animationSpec = tween(AnimationSpecs.DURATION_SLIDE_ENTER), initialOffsetX = { it }) +
                            fadeIn(animationSpec = tween(AnimationSpecs.DURATION_FAST))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(AnimationSpecs.DURATION_MEDIUM),
                                targetOffsetX = { -it / 3 },
                            ) + fadeOut(animationSpec = tween(AnimationSpecs.DURATION_FAST)))
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(AnimationSpecs.DURATION_MEDIUM),
                            initialOffsetX = { -it / 3 },
                        ) + fadeIn(animationSpec = tween(AnimationSpecs.DURATION_FAST))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(AnimationSpecs.DURATION_SLIDE_ENTER),
                                targetOffsetX = { it },
                            ) + fadeOut(animationSpec = tween(AnimationSpecs.DURATION_FAST)))
                    }
                },
                label = "ProfileImportContentSwitch",
            ) { downloading ->
                if (downloading) {
                    DownloadProgressContent(
                        downloadProgress = downloadProgress,
                        stableSheetHeightPx = stableSheetHeightPx,
                        stableSheetHeight = stableSheetHeight,
                        downloadSheetContentHeight = downloadSheetContentHeight,
                        downloadCompleteSheetContentHeight = downloadCompleteSheetContentHeight,
                    )
                } else {
                    ProfileFormContent(
                        selectedImportType = selectedImportType,
                        profileLocked = profileToEdit != null,
                        nameState = nameState,
                        urlState = urlState,
                        fileNameState = fileNameState,
                        ageSecretKeyState = ageSecretKeyState,
                        intervalState = intervalState,
                        error = error,
                        hasCameraPermission = hasCameraPermission,
                        showCameraPreview = showCameraPreview,
                        onContainerMeasured = {
                            stableSheetHeightPx = max(stableSheetHeightPx, it.height)
                        },
                        onTypeSelected = {
                            selectedImportType = it
                            clearCurrentTypeState()
                        },
                        onPickFile = { launcher.launch("*/*") },
                        onSelectQrImage = { qrImageLauncher.launch("image/*") },
                        onQrScanned = { scannedUrl ->
                            applyUrlText(scannedUrl)
                            selectedImportType = ProfileImportType.Url
                        },
                    )
                }
            }
        }
    }
}
