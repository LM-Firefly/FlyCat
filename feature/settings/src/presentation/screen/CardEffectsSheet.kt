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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownOption
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
import top.yukonga.miuix.kmp.preference.SliderPreference

/** 「卡片效果」弹窗：全局圆角、混色、描边、模糊与色彩参数；圆角由卡片、节点、弹层和弹窗共用。 */
@Composable
fun CardEffectsSheet(show: Boolean, onDismiss: () -> Unit) {
    val viewModel: AppSettingsViewModel = viewModel()
    val json by viewModel.blurTheme.state.collectAsStateWithLifecycle()
    val theme = remember(json) { parseBlurTheme(json) }
    val update: (BlurThemeSettings) -> Unit = remember(viewModel) { { next -> viewModel.blurTheme.set(serializeBlurTheme(next)) } }
    AppActionBottomSheet(
        show = show,
        title = FlyTxt.AppSettings.BlurEffects.CardEffectsTitle,
        onDismissRequest = onDismiss,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        insideMargin = DpSize(UiDp.dp12, UiDp.dp8),
    ) {
        val style = theme.texture
        val edgeNames = remember { listOf("Disabled", "Large Container", "Medium Container", "Small Container") }
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = style.cardCornerRadius.dp, applyHorizontalPadding = false) {
            GlassDropdownPreference(
                title = FlyTxt.AppSettings.BlurEffects.BlendMode,
                options = blendDropdownOptions(foreground = false),
                selected = style.blendMode,
                onSelected = { update(theme.copy(texture = style.copy(blendMode = it))) },
            )
            GlassDropdownPreference(
                title = FlyTxt.AppSettings.BlurEffects.EdgeStroke,
                options = listOf(
                    GlassDropdownOption(value = BlurThemeSettings.EDGE_DISABLED, label = edgeNames[0]),
                    GlassDropdownOption(value = BlurThemeSettings.EDGE_LARGE, label = edgeNames[1]),
                    GlassDropdownOption(value = BlurThemeSettings.EDGE_MEDIUM, label = edgeNames[2]),
                    GlassDropdownOption(value = BlurThemeSettings.EDGE_SMALL, label = edgeNames[3]),
                ),
                selected = style.edgeStroke,
                onSelected = { update(theme.copy(texture = style.copy(edgeStroke = it))) },
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.CardCornerRadius,
                valueText = "${style.cardCornerRadius.toInt()}",
                value = (style.cardCornerRadius - BlurThemeSettings.MIN_CARD_CORNER_RADIUS) / (BlurThemeSettings.MAX_CARD_CORNER_RADIUS - BlurThemeSettings.MIN_CARD_CORNER_RADIUS),
                onValueChange = {
                    update(
                        theme.copy(
                            texture = style.copy(
                                cardCornerRadius = BlurThemeSettings.MIN_CARD_CORNER_RADIUS + it *
                                    (BlurThemeSettings.MAX_CARD_CORNER_RADIUS - BlurThemeSettings.MIN_CARD_CORNER_RADIUS),
                            ),
                        ),
                    )
                },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.BlurRadius,
                valueText = "${style.blurRadius.toInt()}",
                value = style.blurRadius / 100f,
                onValueChange = { update(theme.copy(texture = style.copy(blurRadius = it * 100f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Noise,
                valueText = noiseText(style.noise),
                value = style.noise / 0.1f,
                onValueChange = { update(theme.copy(texture = style.copy(noise = it * 0.1f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Brightness,
                valueText = sliderText(style.brightness),
                value = (style.brightness + 1f) / 2f,
                onValueChange = { update(theme.copy(texture = style.copy(brightness = it * 2f - 1f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Contrast,
                valueText = sliderText(style.contrast),
                value = style.contrast / 3f,
                onValueChange = { update(theme.copy(texture = style.copy(contrast = it * 3f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
            )
            SliderPreference(
                title = FlyTxt.AppSettings.BlurEffects.Saturation,
                valueText = sliderText(style.saturation),
                value = style.saturation / 3f,
                onValueChange = { update(theme.copy(texture = style.copy(saturation = it * 3f))) },
                insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp),
            )
        }
    }
}
