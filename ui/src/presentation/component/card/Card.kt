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

package com.github.lmfirefly.flycat.presentation.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.component.EdgeLightDefaults
import com.github.lmfirefly.flycat.presentation.component.dialog.toBlurColors
import com.github.lmfirefly.flycat.presentation.component.edgeLight
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.horizontalPadding
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassColorBlendMode
import top.yukonga.miuix.kmp.glass.GlassColorLayer
import top.yukonga.miuix.kmp.glass.GlassMaterial

/**
 * 全局列表卡片外壳。
 *
 * 默认卡面挂玻璃边缘光（与玻璃按钮无 shader 兜底同一套材质语言：tint/rim 而非透明度）。
 * 传入 [backdrop]（专用的壁纸/表面录制层，非 GlassScaffold 的正文录制）且支持 RuntimeShader 时，
 * 卡片升级为真玻璃：textureBlur 采样该背景 + 叠色，内缘再勾一圈边缘光。
 * HyperOS 同子树约束只禁止「采样包含自身的录制」，采样兄弟录制层（如纯壁纸层）安全。
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = UiDp.dp18,
    insideMargin: PaddingValues = PaddingValues(UiDp.dp0),
    applyHorizontalPadding: Boolean = true,
    colors: CardColors = CardDefaults.defaultColors(),
    // 默认采样全局壁纸层（导航根 AppNavContainer 提供）；为空则走边缘光实色回退。切勿回退到 LocalTopBarBackdrop（会自引用崩溃）。
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    content: @Composable () -> Unit,
) {
    val isLightTheme = !isDarkTheme()
    val glassEnabled = backdrop != null && isRuntimeShaderSupported()
    Card(
        modifier = (if (applyHorizontalPadding) { modifier.horizontalPadding() } else { modifier }).then(
            if (backdrop != null && isRuntimeShaderSupported()) {
                // 玻璃卡面叠边缘光（与玻璃圆钮/下拉行同配方）：textureBlur 采样壁纸，再沿内缘勾一圈光晕描边。
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(cornerRadius),
                    blurRadius = GlassCardBlurRadius,
                    colors = glassCardMaterial().toBlurColors(),
                ).edgeLight(
                    shape = RoundedCornerShape(cornerRadius),
                    color = EdgeLightDefaults.tint(colors.color, isLightTheme),
                    width = EdgeLightDefaults.width(isLightTheme),
                    blurRadius = EdgeLightDefaults.BlurRadius,
                    blendMode = EdgeLightDefaults.blendMode(isLightTheme),
                )
            } else {
                Modifier.edgeLight(
                    shape = RoundedCornerShape(cornerRadius),
                    color = EdgeLightDefaults.tint(colors.color, isLightTheme),
                    width = EdgeLightDefaults.width(isLightTheme),
                    blurRadius = EdgeLightDefaults.BlurRadius,
                    blendMode = EdgeLightDefaults.blendMode(isLightTheme),
                )
            },
        ),
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glassEnabled) CardDefaults.defaultColors(color = Color.Transparent) else colors,
    ) {
        content()
    }
}

/** 玻璃卡片的采样模糊半径。 */
private const val GlassCardBlurRadius = 60f

/**
 * 可复用玻璃卡面：有壁纸录制层且支持 RuntimeShader 时 textureBlur 采样（与 [Card] 同配方），
 * 否则回退 [fallbackColor] 实色。[glassTint] 为玻璃态上叠的半透明染色（如选中态 primary）。
 * 自身不裁剪，调用方需先 `.clip(shape)`。
 */
@Composable
fun Modifier.glassCardSurface(
    shape: Shape,
    fallbackColor: Color,
    glassTint: Color = Color.Transparent,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
): Modifier = if (backdrop != null && isRuntimeShaderSupported()) {
    this
        .textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = GlassCardBlurRadius,
            colors = glassCardMaterial().toBlurColors(),
        )
        .then(if (glassTint != Color.Transparent) Modifier.background(glassTint, shape) else Modifier)
} else {
    this.background(fallbackColor, shape)
}

/**
 * 玻璃卡片的材质配方（单源）：亮色蓝调 Overlay + 白 HardLight 提亮，暗色灰阶 Luminosity/PlusDarker 压暗。
 * 卡片表面与其它玻璃表面（如节点弹层面板）共用此配方，保持同一观感。
 */
@Composable
fun glassCardMaterial(): GlassMaterial = if (!isDarkTheme()) {
    GlassMaterial(
        blurRadius = GlassCardBlurRadius.dp,
        first = GlassColorLayer(Color(0x340034F9), GlassColorBlendMode.Overlay),
        second = GlassColorLayer(Color(0xB3FFFFFF), GlassColorBlendMode.HardLight),
    )
} else {
    GlassMaterial(
        blurRadius = GlassCardBlurRadius.dp,
        first = GlassColorLayer(Color(0x4DA9A9A9), GlassColorBlendMode.Luminosity),
        second = GlassColorLayer(Color(0x1A9C9C9C), GlassColorBlendMode.PlusDarker),
    )
}
