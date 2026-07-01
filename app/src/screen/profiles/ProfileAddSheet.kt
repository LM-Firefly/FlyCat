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
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Package-check`
import com.github.yumelira.yumebox.presentation.icon.yume.`Scan-eye`
import com.github.yumelira.yumebox.presentation.icon.yume.ArrowRight
import com.github.yumelira.yumebox.presentation.icon.yume.Copy
import com.github.yumelira.yumebox.presentation.icon.yume.Sparkles
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.max
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var nameTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var url by remember { mutableStateOf("") }
    var urlTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var filePath by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var fileNameTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var ageSecretKey by remember { mutableStateOf("") }
    var ageSecretKeyTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var initialAgeSecretKey by remember { mutableStateOf("") }
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

    val urlPattern = remember {
        Regex(pattern = "^https?://\\S+$", options = setOf(RegexOption.IGNORE_CASE))
    }

    val applyNameText: (String) -> Unit = { updatedText ->
        name = updatedText
        nameTextFieldValue = textFieldValueAtEnd(updatedText)
    }
    val applyUrlText: (String) -> Unit = { updatedText ->
        url = updatedText
        urlTextFieldValue = textFieldValueAtEnd(updatedText)
    }
    val applyFileNameText: (String) -> Unit = { updatedText ->
        fileName = updatedText
        fileNameTextFieldValue = textFieldValueAtEnd(updatedText)
    }

    val readClipboardAndCheckUrl: () -> String? = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val clipText = item?.text?.toString()?.trim() ?: ""
                if (clipText.isNotEmpty() && urlPattern.matches(clipText)) {
                    clipText
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    val clearAllState = {
        applyNameText("")
        applyUrlText("")
        filePath = ""
        applyFileNameText("")
        ageSecretKey = ""
        ageSecretKeyTextFieldValue = TextFieldValue()
        initialAgeSecretKey = ""
        error = ""
        isDownloading = false
        hasShownCompleteAnimation = false
    }

    val clearCurrentTypeState = {
        when (selectedTypeIndex) {
            0 -> applyUrlText("")
            1 -> {
                filePath = ""
                applyFileNameText("")
            }

            2 -> {}
        }
        error = ""
    }

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
                selectedTypeIndex = 0
            }
        }

    LaunchedEffect(selectedTypeIndex) {
        if (selectedTypeIndex == 2 && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val showCameraPreview by
        remember(show.value, selectedTypeIndex, isDownloading, hasCameraPermission) {
            derivedStateOf {
                show.value && selectedTypeIndex == 2 && !isDownloading && hasCameraPermission
            }
        }

    DisposableEffect(show.value, profileToEdit, importUrl) {
        if (show.value) {
            clearAllState()
            if (profileToEdit != null) {
                applyNameText(profileToEdit.name)
                ageSecretKey = profileToEdit.ageSecretKey
                ageSecretKeyTextFieldValue = TextFieldValue(
                    profileToEdit.ageSecretKey,
                    TextRange(profileToEdit.ageSecretKey.length),
                )
                initialAgeSecretKey = profileToEdit.ageSecretKey
                if (profileToEdit.type == Profile.Type.Url) {
                    selectedTypeIndex = 0
                    applyUrlText(profileToEdit.source)
                } else {
                    selectedTypeIndex = 1
                    filePath = profileToEdit.source
                    applyFileNameText(
                        if (profileToEdit.source.isNotEmpty()) File(profileToEdit.source).name
                        else ""
                    )
                }
            } else if (!importUrl.isNullOrBlank()) {
                selectedTypeIndex = 0
                applyUrlText(importUrl)
            } else {
                selectedTypeIndex = 0
                try {
                    val clipboardUrl = readClipboardAndCheckUrl()
                    if (clipboardUrl != null) {
                        applyUrlText(clipboardUrl)
                    }
                } catch (_: Exception) {}
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

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val actualFileName =
                    context.contentResolver
                        .query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: MLang.ProfilesPage.Message.UnknownFile

                val extension = actualFileName.substringAfterLast(".", "")
                if (
                    !extension.equals("yaml", ignoreCase = true) &&
                        !extension.equals("yml", ignoreCase = true)
                ) {
                    error = MLang.ProfilesPage.Validation.YamlOnly
                    return@let
                }

                filePath = it.toString()
                error = ""
                applyFileNameText(actualFileName)

                val fileNameWithoutExt = actualFileName.substringBeforeLast(".")
                if (name.isBlank() || name == actualFileName) {
                    applyNameText(
                        fileNameWithoutExt.ifBlank { MLang.ProfilesPage.Input.NewProfile }
                    )
                }
            }
        }

    val qrImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val result = readQrFromImage(context, it)
                        if (result != null) {
                            applyUrlText(result)
                            selectedTypeIndex = 0
                            context.toast(MLang.ProfilesPage.QrScanner.RecognizeSuccess)
                        } else {
                            context.toast(MLang.ProfilesPage.QrScanner.RecognizeFailed)
                        }
                    } catch (error: Exception) {
                        context.toast(
                            MLang.ProfilesPage.QrScanner.RecognizeError.format(error.message ?: "")
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
        if (selectedTypeIndex == 2 || isDownloading) {
            return
        }
        if (selectedTypeIndex == 0 && urlTextFieldValue.text.isBlank()) {
            error = MLang.ProfilesPage.Validation.EnterUrl
            return
        }
        if (selectedTypeIndex == 1 && filePath.isBlank()) {
            error = MLang.ProfilesPage.Validation.SelectFile
            return
        }

        keyboardController?.hide()
        profilesViewModel.clearError()
        hasShownCompleteAnimation = false
        isDownloading = true

        if (selectedTypeIndex == 0) {
            if (profileToEdit != null) {
                val trimmedAgeSecretKey = ageSecretKeyTextFieldValue.text.trim()
                onUpdateProfile(
                    profileToEdit.uuid,
                    nameTextFieldValue.text,
                    urlTextFieldValue.text,
                    profileToEdit.interval,
                    if (trimmedAgeSecretKey != initialAgeSecretKey) trimmedAgeSecretKey else null,
                )
            } else {
                val trimmedAgeSecretKey = ageSecretKeyTextFieldValue.text.trim()
                onAddProfile(
                    nameTextFieldValue.text.ifBlank { MLang.ProfilesPage.Input.NewProfile },
                    urlTextFieldValue.text,
                    Profile.Type.Url,
                    0L,
                    null,
                    trimmedAgeSecretKey,
                )
            }
        } else {
            if (profileToEdit != null) {
                val trimmedAgeSecretKey = ageSecretKeyTextFieldValue.text.trim()
                onUpdateProfile(
                    profileToEdit.uuid,
                    name,
                    profileToEdit.source,
                    profileToEdit.interval,
                    if (trimmedAgeSecretKey != initialAgeSecretKey) trimmedAgeSecretKey else null,
                )
            } else {
                val trimmedAgeSecretKey = ageSecretKeyTextFieldValue.text.trim()
                onAddProfile(
                    nameTextFieldValue.text.ifBlank { MLang.ProfilesPage.Input.NewProfile },
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
        title =
            if (profileToEdit != null) MLang.ProfilesPage.Sheet.EditTitle
            else MLang.ProfilesPage.Sheet.AddTitle,
        startAction = {
            if (!isDownloading) {
                AppBottomSheetCloseAction(contentDescription = "Cancel", onClick = dismissSheet)
            }
        },
        endAction = {
            if (!isDownloading && selectedTypeIndex != 2) {
                AppBottomSheetConfirmAction(
                    contentDescription = "Confirm",
                    onClick = { submitProfile() },
                )
            }
        },
        onDismissRequest = dismissSheet,
    ) {
        val stableSheetHeight =
            remember(stableSheetHeightPx, density) {
                if (stableSheetHeightPx <= 0) UiDp.dp0
                else with(density) { stableSheetHeightPx.toDp() }
            }
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    .padding(bottom = UiDp.dp16)
        ) {
            AnimatedContent(
                targetState = isDownloading,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it }) +
                            fadeIn()) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(220),
                                targetOffsetX = { -it / 3 },
                            ) + fadeOut())
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(220),
                            initialOffsetX = { -it / 3 },
                        ) + fadeIn()) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(260),
                                targetOffsetX = { it },
                            ) + fadeOut())
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
                        selectedTypeIndex = selectedTypeIndex,
                        profileLocked = profileToEdit != null,
                        nameTextFieldValue = nameTextFieldValue,
                        urlTextFieldValue = urlTextFieldValue,
                        fileNameTextFieldValue = fileNameTextFieldValue,
                        ageSecretKeyTextFieldValue = ageSecretKeyTextFieldValue,
                        error = error,
                        hasCameraPermission = hasCameraPermission,
                        showCameraPreview = showCameraPreview,
                        onContainerMeasured = {
                            stableSheetHeightPx = max(stableSheetHeightPx, it.height)
                        },
                        onTypeSelected = {
                            selectedTypeIndex = it
                            clearCurrentTypeState()
                        },
                        onNameChange = { updatedTextFieldValue ->
                            nameTextFieldValue = updatedTextFieldValue
                            name = updatedTextFieldValue.text
                            error = ""
                        },
                        onUrlChange = { updatedTextFieldValue ->
                            urlTextFieldValue = updatedTextFieldValue
                            url = updatedTextFieldValue.text
                            error = ""
                        },
                        onAgeSecretKeyChange = { updatedTextFieldValue ->
                            ageSecretKeyTextFieldValue = updatedTextFieldValue
                            ageSecretKey = updatedTextFieldValue.text
                        },
                        onPickFile = { launcher.launch("*/*") },
                        onSelectQrImage = { qrImageLauncher.launch("image/*") },
                        onQrScanned = { scannedUrl ->
                            applyUrlText(scannedUrl)
                            selectedTypeIndex = 0
                        },
                    )
                }
            }
        }
    }
}

private fun textFieldValueAtEnd(text: String): TextFieldValue {
    return TextFieldValue(text = text, selection = TextRange(text.length))
}
