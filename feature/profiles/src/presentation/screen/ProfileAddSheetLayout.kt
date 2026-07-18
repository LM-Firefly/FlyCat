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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.DownloadProgress
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetConfirmAction
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.PROFILE_IMPORT_TYPE_QR

internal class ProfileAddSheetActions(
    val dismiss: () -> Unit,
    val submit: () -> Unit,
    val selectType: (Int) -> Unit,
    val changeName: (TextFieldValue) -> Unit,
    val changeUrl: (TextFieldValue) -> Unit,
    val changeAgeSecretKey: (TextFieldValue) -> Unit,
    val updateHeight: (Int) -> Unit,
    val pickFile: () -> Unit,
    val selectQrImage: () -> Unit,
    val qrScanned: (String) -> Unit,
)

context(actions: ProfileAddSheetActions)
@Composable
internal fun ProfileAddSheetContent(
    show: Boolean,
    isEditing: Boolean,
    isDownloading: Boolean,
    selectedTypeIndex: Int,
    nameValue: TextFieldValue,
    urlValue: TextFieldValue,
    fileNameValue: TextFieldValue,
    ageSecretKeyValue: TextFieldValue,
    error: String,
    hasCameraPermission: Boolean,
    showCameraPreview: Boolean,
    downloadProgress: DownloadProgress?,
    stableHeightPx: Int,
    downloadHeight: Dp,
    downloadCompleteHeight: Dp,
) {
    val density = LocalDensity.current
    val stableHeight =
        remember(stableHeightPx, density) {
            if (stableHeightPx <= 0) UiDp.dp0 else with(density) { stableHeightPx.toDp() }
        }

    AppActionBottomSheet(
        show = show,
        title = if (isEditing) FlyTxt.ProfilesPage.Sheet.EditTitle else FlyTxt.ProfilesPage.Sheet.AddTitle,
        startAction = {
            if (!isDownloading) {
                AppBottomSheetCloseAction(contentDescription = FlyTxt.Component.Action.Cancel, onClick = actions.dismiss)
            }
        },
        endAction = {
            if (!isDownloading && selectedTypeIndex != PROFILE_IMPORT_TYPE_QR) {
                AppBottomSheetConfirmAction(contentDescription = "Confirm", onClick = actions.submit)
            }
        },
        onDismissRequest = actions.dismiss,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize(animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.StandardEasing))
                    .padding(bottom = UiDp.dp16),
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
                        stableSheetHeightPx = stableHeightPx,
                        stableSheetHeight = stableHeight,
                        downloadSheetContentHeight = downloadHeight,
                        downloadCompleteSheetContentHeight = downloadCompleteHeight,
                    )
                } else {
                    ProfileFormContent(
                        selectedTypeIndex = selectedTypeIndex,
                        profileLocked = isEditing,
                        nameTextFieldValue = nameValue,
                        urlTextFieldValue = urlValue,
                        fileNameTextFieldValue = fileNameValue,
                        ageSecretKeyTextFieldValue = ageSecretKeyValue,
                        error = error,
                        hasCameraPermission = hasCameraPermission,
                        showCameraPreview = showCameraPreview,
                        onContainerMeasured = { actions.updateHeight(it.height) },
                        onTypeSelected = actions.selectType,
                        onNameChange = actions.changeName,
                        onUrlChange = actions.changeUrl,
                        onAgeSecretKeyChange = actions.changeAgeSecretKey,
                        onPickFile = actions.pickFile,
                        onSelectQrImage = actions.selectQrImage,
                        onQrScanned = actions.qrScanned,
                    )
                }
            }
        }
    }
}
