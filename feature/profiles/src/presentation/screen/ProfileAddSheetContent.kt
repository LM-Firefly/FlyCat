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

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.github.lmfirefly.flycat.core.util.crypto.AgeKeyCrypto
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.DownloadProgress
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.PackageCheck
import com.github.lmfirefly.flycat.presentation.icon.flycat.Eye
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowRight
import com.github.lmfirefly.flycat.presentation.icon.flycat.Copy
import com.github.lmfirefly.flycat.presentation.icon.flycat.Sparkles
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.PROFILE_IMPORT_TYPE_QR
import com.github.lmfirefly.flycat.presentation.util.PROFILE_IMPORT_TYPE_URL
import com.github.lmfirefly.flycat.presentation.util.toast
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
internal fun DownloadProgressContent(
    downloadProgress: DownloadProgress?,
    stableSheetHeightPx: Int,
    stableSheetHeight: androidx.compose.ui.unit.Dp,
    downloadSheetContentHeight: androidx.compose.ui.unit.Dp,
    downloadCompleteSheetContentHeight: androidx.compose.ui.unit.Dp,
) {
    val isCompleted = downloadProgress?.isCompleted == true
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .height(
                    if (isCompleted) {
                        downloadCompleteSheetContentHeight
                    } else if (stableSheetHeightPx > 0) {
                        stableSheetHeight
                    } else {
                        downloadSheetContentHeight
                    }
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16, Alignment.CenterVertically),
    ) {
        AnimatedContent(
            targetState = isCompleted,
            modifier = Modifier.size(UiDp.dp48),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                fadeIn(animationSpec = tween(AnimationSpecs.DURATION_NORMAL)) togetherWith fadeOut(animationSpec = tween(AnimationSpecs.DURATION_NORMAL))
            },
            label = "ProgressIcon",
        ) { complete ->
            if (complete) {
                Icon(
                    imageVector = FlyCat.PackageCheck,
                    contentDescription = "Complete",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier =
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(UiDp.dp16))
                            .background(MiuixTheme.colorScheme.primary)
                            .padding(UiDp.dp10),
                )
            } else {
                InfiniteProgressIndicator(modifier = Modifier.size(UiDp.dp32))
            }
        }

        downloadProgress?.message?.let { message ->
            Text(
                text = message,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ProfileFormContent(
    selectedTypeIndex: Int,
    profileLocked: Boolean,
    nameTextFieldValue: TextFieldValue,
    urlTextFieldValue: TextFieldValue,
    fileNameTextFieldValue: TextFieldValue,
    ageSecretKeyTextFieldValue: TextFieldValue,
    error: String,
    hasCameraPermission: Boolean,
    showCameraPreview: Boolean,
    onContainerMeasured: (androidx.compose.ui.unit.IntSize) -> Unit,
    onTypeSelected: (Int) -> Unit,
    onNameChange: (TextFieldValue) -> Unit,
    onUrlChange: (TextFieldValue) -> Unit,
    onAgeSecretKeyChange: (TextFieldValue) -> Unit,
    onPickFile: () -> Unit,
    onSelectQrImage: () -> Unit,
    onQrScanned: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().onSizeChanged(onContainerMeasured),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        ProfileTypeSelectorCard(
            selectedTypeIndex = selectedTypeIndex,
            profileLocked = profileLocked,
            onTypeSelected = onTypeSelected,
        )

        Crossfade(
            targetState = selectedTypeIndex,
            animationSpec = tween(AnimationSpecs.DURATION_CROSSFADE),
            label = "ProfileTypeContent",
        ) { typeIndex ->
            when (typeIndex) {
                PROFILE_IMPORT_TYPE_QR ->
                    QrScannerContent(
                        hasCameraPermission = hasCameraPermission,
                        showCameraPreview = showCameraPreview,
                        onSelectQrImage = onSelectQrImage,
                        onQrScanned = onQrScanned,
                    )

                else ->
                    ManualProfileContent(
                        typeIndex = typeIndex,
                        profileLocked = profileLocked,
                        nameTextFieldValue = nameTextFieldValue,
                        urlTextFieldValue = urlTextFieldValue,
                        fileNameTextFieldValue = fileNameTextFieldValue,
                        ageSecretKeyTextFieldValue = ageSecretKeyTextFieldValue,
                        error = error,
                        onNameChange = onNameChange,
                        onUrlChange = onUrlChange,
                        onAgeSecretKeyChange = onAgeSecretKeyChange,
                        onPickFile = onPickFile,
                    )
            }
        }
    }
}

@Composable
private fun ProfileTypeSelectorCard(
    selectedTypeIndex: Int,
    profileLocked: Boolean,
    onTypeSelected: (Int) -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Card {
        Box(
            modifier =
                Modifier.alpha(if (profileLocked) 0.5f else 1f)
                    .clickable(
                        enabled = profileLocked,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
        ) {
            WindowSpinnerPreference(
                title = FlyTxt.ProfilesPage.Type.Title,
                items =
                    remember {
                        listOf(
                            DropdownItem(FlyTxt.ProfilesPage.Type.Subscription),
                            DropdownItem(FlyTxt.ProfilesPage.Type.LocalFile),
                            DropdownItem(FlyTxt.ProfilesPage.Type.QrScan),
                        )
                    },
                selectedIndex = selectedTypeIndex,
                onSelectedIndexChange = onTypeSelected,
            )
        }
    }
}

@Composable
private fun QrScannerContent(
    hasCameraPermission: Boolean,
    showCameraPreview: Boolean,
    onSelectQrImage: () -> Unit,
    onQrScanned: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .height(UiDp.dp200)
                    .clip(RoundedCornerShape(UiDp.dp12))
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showCameraPreview) {
                key("qr_scanner_stable") { StableQrScanner(onScanned = onQrScanned) }
            } else if (!hasCameraPermission) {
                Text(FlyTxt.ProfilesPage.QrScanner.NeedPermission)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(UiDp.dp32))
            }
        }

        TextButton(
            text = FlyTxt.ProfilesPage.QrScanner.SelectFromAlbum,
            onClick = onSelectQrImage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ManualProfileContent(
    typeIndex: Int,
    profileLocked: Boolean,
    nameTextFieldValue: TextFieldValue,
    urlTextFieldValue: TextFieldValue,
    fileNameTextFieldValue: TextFieldValue,
    ageSecretKeyTextFieldValue: TextFieldValue,
    error: String,
    onNameChange: (TextFieldValue) -> Unit,
    onUrlChange: (TextFieldValue) -> Unit,
    onAgeSecretKeyChange: (TextFieldValue) -> Unit,
    onPickFile: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ageKeyVisible by remember { mutableStateOf(false) }
    var agePublicKey by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        TextField(
            value = nameTextFieldValue,
            onValueChange = onNameChange,
            label = FlyTxt.ProfilesPage.Input.ProfileName,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (typeIndex == PROFILE_IMPORT_TYPE_URL) {
            TextField(
                value = urlTextFieldValue,
                onValueChange = onUrlChange,
                label = FlyTxt.ProfilesPage.Input.SubscriptionUrl,
                useLabelAsPlaceholder = true,
                maxLines = 2,
                readOnly = profileLocked,
                enabled = !profileLocked,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextField(
                value = fileNameTextFieldValue,
                onValueChange = {},
                label = FlyTxt.ProfilesPage.Input.SelectFile,
                useLabelAsPlaceholder = true,
                readOnly = true,
                enabled = false,
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onPickFile,
                        ),
            )
        }
        // Age public key (read-only, derived from private key)
        Column(verticalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
            Text(
                text = FlyTxt.ProfilesPage.AgeKey.PublicKey,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.outline,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
            ) {
                TextField(
                    value = agePublicKey,
                    onValueChange = { agePublicKey = it },
                    label = FlyTxt.ProfilesPage.AgeKey.PublicKeyPlaceholder,
                    useLabelAsPlaceholder = true,
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            val keys = AgeKeyCrypto.agePublicKey(
                                ageSecretKeyTextFieldValue.text
                            )
                            if (keys != null) {
                                agePublicKey = keys
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("age_key", keys))
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GeneratedPublicKey.format(keys), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GenerateFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = FlyCat.ArrowRight,
                        contentDescription = FlyTxt.ProfilesPage.AgeKey.DerivePublicKey,
                    )
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("age_key", agePublicKey))
                        Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.CopiedPublicKey.format(agePublicKey), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = FlyCat.Copy,
                        contentDescription = FlyTxt.ProfilesPage.AgeKey.CopyPublicKey,
                    )
                }
            }
        }
        // Age secret key input with generate, copy, and visibility toggle
        Column(verticalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
            Text(
                text = FlyTxt.ProfilesPage.AgeKey.SecretKey,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.outline,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
            ) {
                TextField(
                    value = ageSecretKeyTextFieldValue,
                    onValueChange = onAgeSecretKeyChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    visualTransformation = if (ageKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            val result = AgeKeyCrypto.genAgeKey()
                            if (result != null) {
                                onAgeSecretKeyChange(TextFieldValue(result.secretKey, TextRange(result.secretKey.length)))
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("age_key", result.secretKey))
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GeneratedSecretKey.format(result.secretKey), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GenerateFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = FlyCat.Sparkles,
                        contentDescription = FlyTxt.ProfilesPage.AgeKey.GenerateSecretKey,
                    )
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("age_key", ageSecretKeyTextFieldValue.text))
                        Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.CopiedSecretKey.format(ageSecretKeyTextFieldValue.text), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = FlyCat.Copy,
                        contentDescription = FlyTxt.ProfilesPage.AgeKey.CopySecretKey,
                    )
                }
                IconButton(
                    onClick = { ageKeyVisible = !ageKeyVisible },
                ) {
                    Icon(
                        imageVector = FlyCat.Eye,
                        contentDescription = if (ageKeyVisible) FlyTxt.ProfilesPage.AgeKey.HideKey else FlyTxt.ProfilesPage.AgeKey.ShowKey,
                    )
                }
            }
        }
        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}
