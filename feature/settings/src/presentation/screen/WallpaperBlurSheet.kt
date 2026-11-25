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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.lmfirefly.flycat.core.model.AppBackgroundStyle
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings.Companion.DIR_BOTTOM
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings.Companion.DIR_LEFT
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings.Companion.DIR_RIGHT
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownOption
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownPreference
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppFormDialog
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.blendDropdownOptions
import com.github.lmfirefly.flycat.presentation.theme.noiseText
import com.github.lmfirefly.flycat.presentation.theme.parseBlurTheme
import com.github.lmfirefly.flycat.presentation.theme.serializeBlurTheme
import com.github.lmfirefly.flycat.presentation.theme.sliderText
import top.yukonga.miuix.kmp.preference.SliderPreference

/**
 * 「应用背景」弹窗（原独立设置页）：背景模式（关闭／OS2、OS3 动态渐变／壁纸图片）与壁纸来源，
 * 以及作用于全部全局背景的渐进模糊（原主题页 Progressive Blur 组）：方向／混色／半径／噪点／梯度起讫与曲率
 * （见 AppBackgroundProvider）。弹窗在应用设置上方打开，页面背景即实时预览，故不单列预览区。
 */
@Composable
fun WallpaperBlurSheet(show: Boolean, onDismiss: () -> Unit, navigator: Navigator) {
    val viewModel: AppSettingsViewModel = viewModel()
    val json by viewModel.blurTheme.state.collectAsStateWithLifecycle()
    val theme = remember(json) { parseBlurTheme(json) }
    val appBackgroundStyle by viewModel.appBackgroundStyle.state.collectAsStateWithLifecycle()
    val wallpaperZoom by viewModel.moeWallpaperZoom.state.collectAsStateWithLifecycle()
    val wallpaperBiasX by viewModel.moeWallpaperBiasX.state.collectAsStateWithLifecycle()
    val wallpaperBiasY by viewModel.moeWallpaperBiasY.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        navigator.push(
            Route.MoeWallpaperCrop(
                wallpaperUri = uri.toString(),
                initialZoom = wallpaperZoom,
                initialBiasX = wallpaperBiasX,
                initialBiasY = wallpaperBiasY,
            )
        )
    }
    val update: (BlurThemeSettings) -> Unit = remember(viewModel) { { next -> viewModel.blurTheme.set(serializeBlurTheme(next)) } }
    AppActionBottomSheet(
        show = show,
        title = FlyTxt.AppSettings.Interface.AppBackgroundTitle,
        onDismissRequest = onDismiss,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        insideMargin = DpSize(UiDp.dp12, UiDp.dp8),
    ) {
        val style = theme.progressive
        val directionNames = remember { listOf("Top", "Bottom", "Left", "Right") }
        Card(modifier = Modifier.fillMaxWidth(), applyHorizontalPadding = false) {
            GlassDropdownPreference(
                title = FlyTxt.AppSettings.Interface.AppBackgroundTitle,
                summary = FlyTxt.AppSettings.Interface.AppBackgroundSummary,
                options = listOf(
                    GlassDropdownOption(value = 0, label = FlyTxt.AppSettings.Interface.AppBackgroundOff),
                    GlassDropdownOption(value = 1, label = "OS2 ${FlyTxt.AppSettings.Interface.AppBackgroundDynamic}"),
                    GlassDropdownOption(value = 2, label = "OS3 ${FlyTxt.AppSettings.Interface.AppBackgroundDynamic}"),
                    GlassDropdownOption(value = 3, label = FlyTxt.AppSettings.Interface.AppBackgroundWallpaper),
                ),
                selected = AppBackgroundStyle.entries.indexOf(appBackgroundStyle),
                onSelected = { viewModel.onAppBackgroundStyleChange(AppBackgroundStyle.entries[it]) },
            )
            AnimatedVisibility(visible = appBackgroundStyle == AppBackgroundStyle.Wallpaper) {
                Column {
                    GlassDropdownPreference(
                        title = FlyTxt.AppSettings.Interface.HomeWallpaperSourceTitle,
                        summary = FlyTxt.AppSettings.Interface.HomeWallpaperSourceSummary,
                        options = listOf(
                            GlassDropdownOption(value = 0, label = FlyTxt.AppSettings.Interface.HomeWallpaperSourceGallery),
                            GlassDropdownOption(value = 1, label = FlyTxt.AppSettings.Interface.HomeWallpaperSourceUrl),
                        ),
                        selected = null,
                        onSelected = { index ->
                            when (index) {
                                0 -> wallpaperPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                1 -> showUrlInputDialog = true
                            }
                        },
                    )
                }
            }
            GlassDropdownPreference(
                title = FlyTxt.AppSettings.BlurEffects.Direction,
                options = listOf(
                    GlassDropdownOption(value = 0, label = directionNames[0]),
                    GlassDropdownOption(value = DIR_BOTTOM, label = directionNames[1]),
                    GlassDropdownOption(value = DIR_LEFT, label = directionNames[2]),
                    GlassDropdownOption(value = DIR_RIGHT, label = directionNames[3]),
                ),
                selected = style.direction,
                onSelected = { update(theme.copy(progressive = style.copy(direction = it))) },
            )
            GlassDropdownPreference(
                title = FlyTxt.AppSettings.BlurEffects.BlendMode,
                options = blendDropdownOptions(foreground = false),
                selected = style.blendMode,
                onSelected = { update(theme.copy(progressive = style.copy(blendMode = it))) },
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.BlurRadius,
                valueText = "${style.blurRadius.toInt()}",
                value = style.blurRadius / 50f,
                onValueChange = { update(theme.copy(progressive = style.copy(blurRadius = it * 50f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Noise,
                valueText = noiseText(style.noise),
                value = style.noise / 0.1f,
                onValueChange = { update(theme.copy(progressive = style.copy(noise = it * 0.1f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Start,
                valueText = sliderText(style.start),
                value = style.start,
                onValueChange = { update(theme.copy(progressive = style.copy(start = it))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.End,
                valueText = sliderText(style.end),
                value = style.end,
                onValueChange = { update(theme.copy(progressive = style.copy(end = it))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Curve,
                valueText = sliderText(style.curve),
                value = (style.curve - 0.25f) / 2.75f,
                onValueChange = { update(theme.copy(progressive = style.copy(curve = 0.25f + it * 2.75f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp),
            )
        }
    }

    if (showUrlInputDialog) {
        RemoteWallpaperUrlDialog(
            show = showUrlInputDialog,
            initialUrl = urlInput,
            onDismiss = { showUrlInputDialog = false },
            onConfirm = { url ->
                showUrlInputDialog = false
                urlInput = url
                navigator.push(
                    Route.MoeWallpaperCrop(
                        wallpaperUri = url,
                        initialZoom = wallpaperZoom,
                        initialBiasX = wallpaperBiasX,
                        initialBiasY = wallpaperBiasY,
                    )
                )
            },
        )
    }
}

@Composable
private fun RemoteWallpaperUrlDialog(show: Boolean, initialUrl: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val urlState = rememberTextFieldState(initialText = initialUrl)
    AppFormDialog(
        show = show,
        title = FlyTxt.AppSettings.Interface.HomeWallpaperUrlDialogTitle,
        onDismissRequest = onDismiss,
        onConfirm = {
            val trimmed = urlState.text.toString().trim()
            if (trimmed.isNotEmpty()) onConfirm(trimmed)
        },
        scrollable = false,
    ) {
        GlassTextField(
            state = urlState,
            label = "https://example.com/image.jpg",
            useLabelAsPlaceholder = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
