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

package com.github.lmfirefly.flycat.presentation.component.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.github.lmfirefly.flycat.presentation.component.rememberSurfaceBackdrop
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassColorBlendMode
import top.yukonga.miuix.kmp.glass.GlassMaterial
import top.yukonga.miuix.kmp.glass.GlassMaterials
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** 把 [GlassMaterial] 的颜色层映射为 [textureBlur] 的 [BlurColors]；配方单一来源为 [GlassMaterials.dialogGlass]（与历史手写配方逐像素一致）。 */
internal fun GlassMaterial.toBlurColors(): BlurColors = BlurColors(
    blendColors = listOfNotNull(first, second, third).map { BlendColorEntry(it.color, it.mode.toBlurBlendMode()) },
)

internal fun GlassColorBlendMode.toBlurBlendMode(): BlurBlendMode = when (this) {
    GlassColorBlendMode.SrcOver -> BlurBlendMode.SrcOver
    GlassColorBlendMode.PlusDarker -> BlurBlendMode.PlusDarker
    GlassColorBlendMode.PlusLighter -> BlurBlendMode.PlusLighter
    GlassColorBlendMode.SoftLight -> BlurBlendMode.SoftLight
    GlassColorBlendMode.HardLight -> BlurBlendMode.HardLight
    GlassColorBlendMode.Overlay -> BlurBlendMode.Overlay
    GlassColorBlendMode.Luminosity -> BlurBlendMode.Luminosity
    GlassColorBlendMode.ColorDodge -> BlurBlendMode.ColorDodge
    GlassColorBlendMode.ColorBurn -> BlurBlendMode.ColorBurn
}

/** 弹层面板玻璃表面：配方取自 [GlassMaterials.dialogGlass]，采样页面壁纸层（跨层安全）；无壁纸/无 shader 时回退实色。 */
@Composable
internal fun Modifier.dialogGlassSurface(shape: Shape, fallbackColor: Color, backdrop: LayerBackdrop?): Modifier = if (backdrop != null && isRuntimeShaderSupported()) {
    val material = GlassMaterials.dialogGlass(isDarkTheme())
    textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = material.blurRadius.value,
        colors = material.toBlurColors(),
    )
} else {
    background(fallbackColor, shape)
}

object AppDialogDefaults {
    @Composable fun titleColor(): Color = DialogDefaults.titleColor()

    @Composable fun summaryColor(): Color = DialogDefaults.summaryColor()

    @Composable fun backgroundColor(): Color = DialogDefaults.backgroundColor()

    val outsideMargin: DpSize
        get() = DialogDefaults.outsideMargin

    val insideMargin: DpSize
        get() = DialogDefaults.insideMargin
}

@Composable
fun AppDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    titleColor: Color = AppDialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = AppDialogDefaults.summaryColor(),
    backgroundColor: Color = AppDialogDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = AppDialogDefaults.outsideMargin,
    insideMargin: DpSize = AppDialogDefaults.insideMargin,
    cornerRadius: Dp = UiDp.dp18,
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 面板级玻璃实验：同 AppActionBottomSheet——玻璃配方（GlassMaterials.dialogGlass）采样页面壁纸层，激活时透明背景。
    val pageWallpaperBackdrop = LocalGlassBackdrop.current
    val glassActive = pageWallpaperBackdrop != null && isRuntimeShaderSupported()
    val dialogShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    OverlayDialog(
        show = show,
        modifier = modifier.then(
            if (glassActive) {
                Modifier.clip(dialogShape).dialogGlassSurface(dialogShape, fallbackColor = backgroundColor, backdrop = pageWallpaperBackdrop)
            } else {
                Modifier
            }
        ),
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        backgroundColor = if (glassActive) Color.Transparent else backgroundColor,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        cornerRadius = cornerRadius,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        renderInRootScaffold = renderInRootScaffold,
        content = {
            // 内容玻璃采样页面壁纸层（跨层安全，卡片同款）；不采样自身内容层（HyperOS 自采样递归崩）。
            CompositionLocalProvider(LocalGlassBackdrop provides pageWallpaperBackdrop) {
                content()
            }
        },
    )
}
