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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.github.lmfirefly.flycat.core.util.crypto.AgeKeyCrypto
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.DownloadProgress
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownOption
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownPreference
import com.github.lmfirefly.flycat.presentation.component.GlassTextButton
import com.github.lmfirefly.flycat.presentation.component.SheetHeaderIconButton
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowRight
import com.github.lmfirefly.flycat.presentation.icon.flycat.Copy
import com.github.lmfirefly.flycat.presentation.icon.flycat.Eye
import com.github.lmfirefly.flycat.presentation.icon.flycat.EyeOff
import com.github.lmfirefly.flycat.presentation.icon.flycat.PackageCheck
import com.github.lmfirefly.flycat.presentation.icon.flycat.Sparkles
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.ProfileImportType
import com.github.lmfirefly.flycat.presentation.util.toast
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
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
        modifier = Modifier.fillMaxWidth().height(
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
            transitionSpec = { fadeIn(animationSpec = tween(AnimationSpecs.DURATION_NORMAL)) togetherWith fadeOut(animationSpec = tween(AnimationSpecs.DURATION_NORMAL)) },
            label = "ProgressIcon",
        ) { complete ->
            if (complete) {
                Icon(
                    imageVector = FlyCat.PackageCheck,
                    contentDescription = "Complete",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(UiDp.dp16)).background(MiuixTheme.colorScheme.primary).padding(UiDp.dp10),
                )
            } else { InfiniteProgressIndicator(modifier = Modifier.size(UiDp.dp32)) }
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
    selectedImportType: ProfileImportType,
    profileLocked: Boolean,
    nameState: TextFieldState,
    urlState: TextFieldState,
    fileNameState: TextFieldState,
    ageSecretKeyState: TextFieldState,
    intervalState: TextFieldState,
    error: String,
    hasCameraPermission: Boolean,
    showCameraPreview: Boolean,
    onContainerMeasured: (androidx.compose.ui.unit.IntSize) -> Unit,
    onTypeSelected: (ProfileImportType) -> Unit,
    onPickFile: () -> Unit,
    onSelectQrImage: () -> Unit,
    onQrScanned: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().onSizeChanged(onContainerMeasured),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        ProfileTypeSelectorCard(
            selectedImportType = selectedImportType,
            profileLocked = profileLocked,
            onTypeSelected = onTypeSelected,
        )

        Crossfade(
            targetState = selectedImportType,
            animationSpec = tween(AnimationSpecs.DURATION_CROSSFADE),
            label = "ProfileTypeContent",
        ) { importType ->
            when (importType) {
                ProfileImportType.Qr ->
                    QrScannerContent(
                        hasCameraPermission = hasCameraPermission,
                        showCameraPreview = showCameraPreview,
                        onSelectQrImage = onSelectQrImage,
                        onQrScanned = onQrScanned,
                    )
                ProfileImportType.Url,
                ProfileImportType.LocalFile ->
                    ManualProfileContent(
                        importType = importType,
                        profileLocked = profileLocked,
                        nameState = nameState,
                        urlState = urlState,
                        fileNameState = fileNameState,
                        ageSecretKeyState = ageSecretKeyState,
                        intervalState = intervalState,
                        error = error,
                        onPickFile = onPickFile,
                    )
            }
        }
    }
}

@Composable
private fun ProfileTypeSelectorCard(selectedImportType: ProfileImportType, profileLocked: Boolean, onTypeSelected: (ProfileImportType) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), applyHorizontalPadding = false) {
        Box(
            modifier = Modifier.alpha(if (profileLocked) 0.5f else 1f).clickable(
                enabled = profileLocked,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            )
        ) {
            GlassDropdownPreference(
                title = FlyTxt.ProfilesPage.Type.Title,
                options = ProfileImportType.entries.indices.map { index ->
                    GlassDropdownOption(
                        value = ProfileImportType.fromSpinnerIndex(index),
                        label = ProfileImportType.entries[index].spinnerTitle(),
                    )
                },
                selected = selectedImportType,
                onSelected = onTypeSelected,
                glassSurface = true,
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
            modifier = Modifier.fillMaxWidth().height(UiDp.dp200).clip(RoundedCornerShape(UiDp.dp12)).background(MiuixTheme.colorScheme.surfaceVariant),
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

        GlassTextButton(
            text = FlyTxt.ProfilesPage.QrScanner.SelectFromAlbum,
            onClick = onSelectQrImage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ManualProfileContent(
    importType: ProfileImportType,
    profileLocked: Boolean,
    nameState: TextFieldState,
    urlState: TextFieldState,
    fileNameState: TextFieldState,
    ageSecretKeyState: TextFieldState,
    intervalState: TextFieldState,
    error: String,
    onPickFile: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ageKeyVisible by remember { mutableStateOf(false) }
    val agePublicKeyState = rememberTextFieldState()
    val intervalInputTransformation = remember {
        InputTransformation {
            val filtered = toString().filter(Char::isDigit).take(6)
            if (filtered != toString()) replace(0, length, filtered)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        GlassTextField(
            state = nameState,
            label = FlyTxt.ProfilesPage.Input.ProfileName,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (importType == ProfileImportType.Url) {
            GlassTextField(
                state = urlState,
                label = FlyTxt.ProfilesPage.Input.SubscriptionUrl,
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 2),
                readOnly = profileLocked,
                enabled = !profileLocked,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!profileLocked) {
                Column(verticalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                    GlassTextField(
                        state = intervalState,
                        inputTransformation = intervalInputTransformation,
                        label = FlyTxt.ProfilesPage.Input.AutoUpdateInterval,
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = FlyTxt.ProfilesPage.Input.AutoUpdateIntervalHint,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            GlassTextField(
                state = fileNameState,
                label = FlyTxt.ProfilesPage.Input.SelectFile,
                useLabelAsPlaceholder = true,
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onPickFile),
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
                GlassTextField(
                    state = agePublicKeyState,
                    label = FlyTxt.ProfilesPage.AgeKey.PublicKeyPlaceholder,
                    useLabelAsPlaceholder = true,
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                SheetHeaderIconButton(
                    icon = FlyCat.ArrowRight,
                    contentDescription = FlyTxt.ProfilesPage.AgeKey.DerivePublicKey,
                    onClick = {
                        scope.launch {
                            val keys = AgeKeyCrypto.agePublicKey(ageSecretKeyState.text.toString())
                            if (keys != null) {
                                agePublicKeyState.setTextAndPlaceCursorAtEnd(keys)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("age_key", keys))
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GeneratedPublicKey.format(keys), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GenerateFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                SheetHeaderIconButton(
                    icon = FlyCat.Copy,
                    contentDescription = FlyTxt.ProfilesPage.AgeKey.CopiedPublicKey,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("age_key", agePublicKeyState.text.toString()))
                        Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.CopiedPublicKey.format(agePublicKeyState.text), Toast.LENGTH_SHORT).show()
                    },
                )
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
                GlassTextField(
                    state = ageSecretKeyState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.weight(1f),
                    secure = !ageKeyVisible,
                )
                SheetHeaderIconButton(
                    icon = FlyCat.Sparkles,
                    contentDescription = FlyTxt.ProfilesPage.AgeKey.GenerateSecretKey,
                    onClick = {
                        scope.launch {
                            val result = AgeKeyCrypto.genAgeKey()
                            if (result != null) {
                                ageSecretKeyState.setTextAndPlaceCursorAtEnd(result.secretKey)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("age_key", result.secretKey))
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GeneratedSecretKey.format(result.secretKey), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.GenerateFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                SheetHeaderIconButton(
                    icon = FlyCat.Copy,
                    contentDescription = FlyTxt.ProfilesPage.AgeKey.CopiedSecretKey,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val secretKey = ageSecretKeyState.text.toString()
                        clipboard.setPrimaryClip(ClipData.newPlainText("age_key", secretKey))
                        Toast.makeText(context, FlyTxt.ProfilesPage.AgeKey.CopiedSecretKey.format(secretKey), Toast.LENGTH_SHORT).show()
                    },
                )
                SheetHeaderIconButton(
                    icon = if (ageKeyVisible) FlyCat.EyeOff else FlyCat.Eye,
                    contentDescription = if (ageKeyVisible) FlyTxt.ProfilesPage.AgeKey.HideKey else FlyTxt.ProfilesPage.AgeKey.ShowKey,
                    onClick = { ageKeyVisible = !ageKeyVisible },
                )
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

private fun ProfileImportType.spinnerTitle(): String = when (this) {
    ProfileImportType.Url -> FlyTxt.ProfilesPage.Type.Subscription
    ProfileImportType.LocalFile -> FlyTxt.ProfilesPage.Type.LocalFile
    ProfileImportType.Qr -> FlyTxt.ProfilesPage.Type.QrScan
}
