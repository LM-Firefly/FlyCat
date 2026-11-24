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

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.presentation.theme.LocalBlurTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.blurBlendToken
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 应用名的渐变刷（与品牌 logo 同系的粉蓝过渡）。 */
internal val AppNameGradient = listOf(
    Color(0xFFEFC0D9),
    Color(0xFFD2C6F0),
    Color(0xFFC0D5F5),
)

/**
 * 应用标识块：logo＋应用名，按主题 Foreground Blur 组做 DstIn 前景模糊——内容轮廓作蒙版，把壁纸层的模糊场与混色填进字形/图形（同示例 ForegroundBlurDemo）。
 * 关于页与主题预览共用此组件；无壁纸层时回退渐变原色。混色/半径/噪点/明暗随主题页实时变化。
 */
@Composable
fun ForegroundBlurIdentity(name: String, modifier: Modifier = Modifier) {
    val foreground = LocalBlurTheme.current.foreground
    val dark = isDarkTheme()
    val token = remember(foreground.blendMode, dark) { blurBlendToken(foreground.blendMode, dark, foreground = true) }
    val wallpaper = LocalGlassBackdrop.current
    val foregroundBlur = if (wallpaper != null) {
        Modifier.textureBlur(
            backdrop = wallpaper,
            shape = RectangleShape,
            blurRadiusX = foreground.blurRadius,
            blurRadiusY = foreground.blurRadius,
            noiseCoefficient = foreground.noise,
            colors = BlurDefaults.blurColors(
                blendColors = token,
                brightness = foreground.brightness,
                contrast = foreground.contrast,
                saturation = foreground.saturation,
            ),
            contentBlendMode = BlendMode.DstIn,
        )
    } else {
        Modifier
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.height(96.dp).aspectRatio(FlyCatLogoAspectRatio)) {
            FlyCatLogoIcon(tint = Brush.linearGradient(AppNameGradient), modifier = Modifier.fillMaxSize().then(foregroundBlur))
        }
        Text(
            text = name,
            modifier = Modifier.padding(top = UiDp.dp12, bottom = UiDp.dp5).then(foregroundBlur),
            style = MiuixTheme.textStyles.title1.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                brush = Brush.linearGradient(AppNameGradient),
            ),
        )
    }
}
