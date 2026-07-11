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

package com.github.yumelira.yumebox.screen.profiles

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_FILE
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_QR
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_URL
import com.github.yumelira.yumebox.presentation.util.importTypeIndexFor
import com.github.yumelira.yumebox.presentation.util.profileNameFromConfigFileName
import com.github.yumelira.yumebox.presentation.util.readClipboardSubscriptionUrl
import com.github.yumelira.yumebox.presentation.util.sourceFileName
import com.github.yumelira.yumebox.runtime.api.Profile
import dev.oom_wg.purejoy.mlang.MLang
import java.util.UUID
import kotlin.math.max

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
    onUpdateProfile: (uuid: UUID, name: String, source: String, interval: Long) -> Unit,
    onDownloadComplete: () -> Unit,
    profilesViewModel: ProfilesViewModel,
) {
    val configuration = LocalConfiguration.current
    val downloadSheetContentHeight = configuration.screenHeightDp.dp * 0.3f
    val downloadCompleteSheetContentHeight = configuration.screenHeightDp.dp * 0.42f
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val form = rememberProfileAddFormState()
    var selectedTypeIndex by form.typeIndex
    var nameTextFieldValue by form.name
    var urlTextFieldValue by form.url
    var filePath by form.filePath
    var fileNameTextFieldValue by form.fileName
    var ageSecretKeyTextFieldValue by form.ageSecretKey
    var error by form.error
    var isDownloading by form.isDownloading

    val downloadProgress by profilesViewModel.downloadProgress.collectAsState()
    val uiState by profilesViewModel.uiState.collectAsState()
    var hasShownCompleteAnimation by form.hasShownComplete
    var stableSheetHeightPx by form.stableHeightPx

    LaunchedEffect(show.value) {
        if (!show.value) {
            hasShownCompleteAnimation = false
            isDownloading = false
        }
    }

    val applyNameText: (String) -> Unit = { updatedText ->
        nameTextFieldValue = textValueAtEnd(updatedText)
    }
    val applyUrlText: (String) -> Unit = { updatedText ->
        urlTextFieldValue = textValueAtEnd(updatedText)
    }
    val applyFileNameText: (String) -> Unit = { updatedText ->
        fileNameTextFieldValue = textValueAtEnd(updatedText)
    }

    val clearAllState = form::reset
    val clearCurrentTypeState = form::clearTypeInput

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                context.toast(MLang.ProfilesPage.QrScanner.NeedCamera, Toast.LENGTH_LONG)
                selectedTypeIndex = PROFILE_IMPORT_TYPE_URL
            }
        }

    LaunchedEffect(selectedTypeIndex) {
        if (selectedTypeIndex == PROFILE_IMPORT_TYPE_QR && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val showCameraPreview by
        remember(show.value, selectedTypeIndex, isDownloading, hasCameraPermission) {
            derivedStateOf {
                show.value &&
                    selectedTypeIndex == PROFILE_IMPORT_TYPE_QR &&
                    !isDownloading &&
                    hasCameraPermission
            }
        }

    DisposableEffect(show.value, profileToEdit, importUrl) {
        if (show.value) {
            clearAllState()
            if (profileToEdit != null) {
                applyNameText(profileToEdit.name)
                if (profileToEdit.type == Profile.Type.Url) {
                    selectedTypeIndex = PROFILE_IMPORT_TYPE_URL
                    applyUrlText(profileToEdit.source)
                } else {
                    selectedTypeIndex = importTypeIndexFor(profileToEdit.type)
                    filePath = profileToEdit.source
                    applyFileNameText(sourceFileName(profileToEdit.source))
                }
            } else if (!importUrl.isNullOrBlank()) {
                selectedTypeIndex = PROFILE_IMPORT_TYPE_URL
                applyUrlText(importUrl)
            } else {
                selectedTypeIndex = PROFILE_IMPORT_TYPE_URL
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

    val launchers =
        rememberProfileImportLaunchers(
            context = context,
            onFileSelected = { uri, fileName ->
                filePath = uri.toString()
                error = ""
                applyFileNameText(fileName)
                if (nameTextFieldValue.text.isBlank() || nameTextFieldValue.text == fileName) {
                    applyNameText(
                        profileNameFromConfigFileName(
                            fileName,
                            MLang.ProfilesPage.Input.NewProfile,
                        )
                    )
                }
            },
            onUnsupportedFile = { error = MLang.ProfilesPage.Validation.YamlOnly },
            onQrDecoded = { url ->
                applyUrlText(url)
                selectedTypeIndex = PROFILE_IMPORT_TYPE_URL
            },
        )

    val dismissSheet = { dismissProfileAddSheet(show, isDownloading, profilesViewModel) }

    val actions =
        ProfileAddSheetActions(
            dismiss = dismissSheet,
            submit = {
                val draft =
                    ProfileDraft(
                        typeIndex = selectedTypeIndex,
                        name = nameTextFieldValue.text,
                        url = urlTextFieldValue.text,
                        filePath = filePath,
                        ageSecretKey = ageSecretKeyTextFieldValue.text,
                        profileToEdit = profileToEdit,
                        isDownloading = isDownloading,
                    )
                with(
                    ProfileSubmissionActions(
                        hideKeyboard = { keyboardController?.hide() },
                        clearError = profilesViewModel::clearError,
                        startDownload = {
                            hasShownCompleteAnimation = false
                            isDownloading = true
                        },
                        showError = { error = it },
                        addProfile = onAddProfile,
                        updateProfile = onUpdateProfile,
                    )
                ) {
                    submitProfile(draft)
                }
            },
            selectType = {
            selectedTypeIndex = it
            clearCurrentTypeState()
            },
            changeName = { value ->
                nameTextFieldValue = value
                error = ""
            },
            changeUrl = { value ->
                urlTextFieldValue = value
                error = ""
            },
            changeAgeSecretKey = { ageSecretKeyTextFieldValue = it },
            updateHeight = { height -> stableSheetHeightPx = max(stableSheetHeightPx, height) },
            pickFile = launchers.pickFile,
            selectQrImage = launchers.selectQrImage,
            qrScanned = { url ->
                applyUrlText(url)
                selectedTypeIndex = PROFILE_IMPORT_TYPE_URL
            },
        )
    with(actions) {
        ProfileAddSheetContent(
            show = show.value,
            isEditing = profileToEdit != null,
            isDownloading = isDownloading,
            selectedTypeIndex = selectedTypeIndex,
            nameValue = nameTextFieldValue,
            urlValue = urlTextFieldValue,
            fileNameValue = fileNameTextFieldValue,
            ageSecretKeyValue = ageSecretKeyTextFieldValue,
            error = error,
            hasCameraPermission = hasCameraPermission,
            showCameraPreview = showCameraPreview,
            downloadProgress = downloadProgress,
            stableHeightPx = stableSheetHeightPx,
            downloadHeight = downloadSheetContentHeight,
            downloadCompleteHeight = downloadCompleteSheetContentHeight,
        )
    }
}
