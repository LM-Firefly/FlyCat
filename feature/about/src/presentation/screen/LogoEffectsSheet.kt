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

package com.github.lmfirefly.flycat.feature.about.presentation.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownPreference
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.blendDropdownOptions
import com.github.lmfirefly.flycat.presentation.theme.noiseText
import com.github.lmfirefly.flycat.presentation.theme.parseBlurTheme
import com.github.lmfirefly.flycat.presentation.theme.serializeBlurTheme
import com.github.lmfirefly.flycat.presentation.theme.sliderText
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.preference.SliderPreference

/**
 * 「Logo 及 App 名称效果」弹窗（原独立设置页；唯一消费方为关于页标识块）。
 * 混色/半径/噪点/明暗经主题 LocalBlurTheme 实时驱动 [com.github.lmfirefly.flycat.presentation.component.ForegroundBlurIdentity]；
 * 弹窗在关于页上方打开，页面标识块本身就是实时预览，故不再单列预览区。
 */
@Composable
fun LogoEffectsSheet(show: Boolean, onDismiss: () -> Unit) {
    val settings = koinInject<AppSettingsReader>()
    val json by settings.blurTheme.state.collectAsStateWithLifecycle()
    val theme = remember(json) { parseBlurTheme(json) }
    val update: (BlurThemeSettings) -> Unit = remember(settings) { { next -> settings.blurTheme.set(serializeBlurTheme(next)) } }
    val style = theme.foreground
    AppActionBottomSheet(
        show = show,
        title = FlyTxt.AppSettings.BlurEffects.LogoEffectsTitle,
        onDismissRequest = onDismiss,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        insideMargin = DpSize(UiDp.dp12, UiDp.dp8),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), applyHorizontalPadding = false) {
            GlassDropdownPreference(
                title = FlyTxt.AppSettings.BlurEffects.BlendMode,
                options = blendDropdownOptions(foreground = true),
                selected = style.blendMode,
                onSelected = { update(theme.copy(foreground = style.copy(blendMode = it))) },
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.BlurRadius,
                valueText = "${style.blurRadius.toInt()}",
                value = style.blurRadius / 100f,
                onValueChange = { update(theme.copy(foreground = style.copy(blurRadius = it * 100f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Noise,
                valueText = noiseText(style.noise),
                value = style.noise / 0.1f,
                onValueChange = { update(theme.copy(foreground = style.copy(noise = it * 0.1f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Brightness,
                valueText = sliderText(style.brightness),
                value = (style.brightness + 1f) / 2f,
                onValueChange = { update(theme.copy(foreground = style.copy(brightness = it * 2f - 1f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Contrast,
                valueText = sliderText(style.contrast),
                value = style.contrast / 3f,
                onValueChange = { update(theme.copy(foreground = style.copy(contrast = it * 3f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Saturation,
                valueText = sliderText(style.saturation),
                value = style.saturation / 3f,
                onValueChange = { update(theme.copy(foreground = style.copy(saturation = it * 3f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp),
            )
        }
    }
}
