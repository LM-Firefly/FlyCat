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

package com.github.lmfirefly.flycat.presentation.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.core.model.AppBackgroundStyle
import com.github.lmfirefly.flycat.presentation.component.background.AppBackgroundSpec
import com.github.lmfirefly.flycat.presentation.component.background.BgEffectBackground
import com.github.panpf.sketch.rememberAsyncImagePainter

/** 把背景设置解析为注入 `GlassScaffold` 的 [AppBackgroundSpec]：关闭 / OS3 动态渐变 / 用户壁纸图。[wallpaperBlur] 为壁纸显示模糊半径（dp，0=不模糊）。 */
@Composable
fun rememberAppBackgroundSpec(style: AppBackgroundStyle, wallpaperUri: String, wallpaperBlur: Float): AppBackgroundSpec = when (style) {
    AppBackgroundStyle.Off -> remember { AppBackgroundSpec(enabled = false) }
    AppBackgroundStyle.DynamicGradient -> remember { dynamicGradientSpec() }
    AppBackgroundStyle.Wallpaper -> if (wallpaperUri.isBlank()) { remember { dynamicGradientSpec() } } else {
        remember(wallpaperUri, wallpaperBlur) {
            AppBackgroundSpec(enabled = true) { bgModifier ->
                Image(
                    painter = rememberAsyncImagePainter(wallpaperUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // 模糊作为外层效果，只模糊可见背景；layerBackdrop 录制的是清晰图，玻璃卡片采样不受影响。
                    modifier = Modifier.fillMaxSize().then(if (wallpaperBlur > 0f) Modifier.blur(wallpaperBlur.dp) else Modifier).then(bgModifier),
                )
            }
        }
    }
}

private fun dynamicGradientSpec(): AppBackgroundSpec = AppBackgroundSpec(enabled = true) { bgModifier ->
    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = bgModifier,
        isFullSize = true,
    )
}
