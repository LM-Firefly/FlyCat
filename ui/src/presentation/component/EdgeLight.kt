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

@file:Suppress("UnusedSymbol", "FunctionName")

package com.github.lmfirefly.flycat.presentation.component

import android.graphics.BlurMaskFilter
import android.graphics.Paint as FrameworkPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 沿 [shape] 内侧绘制一条发光的发丝级边缘描边，采用 NexioSchedule 底栏样式，用于替代深色的发丝级边框。
 * 描边颜色取自面板自身的背景色（接近中性色时回退为白色），并以加色/滤色混合绘制，因此呈现为光晕边缘而非实线。
 * [blurRadius] 驱动描边的高斯羽化半径（BlurMaskFilter），让边缘呈柔和光晕而非硬线。
 */
fun Modifier.edgeLight(shape: Shape, color: Color, width: Dp, blurRadius: Dp = EdgeLightDefaults.BlurRadius, blendMode: BlendMode = BlendMode.Plus): Modifier = drawWithCache {
    // 缓存而非每帧重建：轮廓路径与画笔仅依赖于尺寸和参数。
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokeWidthPx = width.toPx() * 2f
    // 描边跨在轮廓中心线上；裁剪到轮廓后仅保留内侧一半，因此可见的边缘宽度等于 [width]。
    val androidPath = outline.toClipPath().asAndroidPath()
    val blurPx = blurRadius.toPx()
    // 加色/滤色混合近似：亮色加色(ADD)、暗色滤色(SCREEN)。
    val porterMode = if (blendMode == BlendMode.Screen) PorterDuff.Mode.SCREEN else PorterDuff.Mode.ADD
    // 真实高斯羽化（BlurMaskFilter）替代叠加硬描边：外圈柔光 + 柔化核心线，彻底消除硬边。
    val haloPaint = strokePaint(
        colorArgb = color.copy(alpha = color.alpha * HaloAlphaFactor).toArgb(),
        strokeWidth = strokeWidthPx + blurPx * 4f,
        porterMode = porterMode,
        blurSigmaPx = blurPx * 3f,
    )
    val rimPaint = strokePaint(colorArgb = color.copy(alpha = color.alpha * RimAlphaFactor).toArgb(), strokeWidth = strokeWidthPx, porterMode = porterMode, blurSigmaPx = (strokeWidthPx * 0.5f).coerceAtLeast(blurPx * 1.5f))
    onDrawWithContent {
        drawContent()
        if (strokeWidthPx <= 0f) return@onDrawWithContent
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        canvas.clipPath(androidPath)
        canvas.drawPath(androidPath, haloPaint)
        canvas.drawPath(androidPath, rimPaint)
        canvas.restoreToCount(save)
    }
}

private fun strokePaint(colorArgb: Int, strokeWidth: Float, porterMode: PorterDuff.Mode, blurSigmaPx: Float): FrameworkPaint = FrameworkPaint(FrameworkPaint.ANTI_ALIAS_FLAG).apply {
    color = colorArgb
    style = FrameworkPaint.Style.STROKE
    this.strokeWidth = strokeWidth
    xfermode = PorterDuffXfermode(porterMode)
    // 高斯羽化（API 28+ 硬件加速支持；更老设备回退为无羽化）。
    if (blurSigmaPx > 0f) maskFilter = BlurMaskFilter(blurSigmaPx, BlurMaskFilter.Blur.NORMAL)
}

/** 将 [Outline] 转换为裁剪用的 [Path]，使描边能被裁剪成它所跟随的形状。 */
private fun Outline.toClipPath(): Path = when (this) {
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

/** 底部栏边缘灯光默认值，与 NexioSchedule 的 `rememberDefaultEdgeLight` 对齐。 */
object EdgeLightDefaults {
    val WidthLight: Dp = 0.2.dp
    val WidthDark: Dp = 0.32.dp
    val BlurRadius: Dp = 0.8.dp
    const val AlphaLight = 0.18f
    const val AlphaDark = 0.16f
    fun width(isLightTheme: Boolean): Dp = if (isLightTheme) WidthLight else WidthDark
    fun blendMode(isLightTheme: Boolean): BlendMode = if (isLightTheme) BlendMode.Plus else BlendMode.Screen
    /** 边缘颜色 = 基色提高饱和度并限制明度后的结果；中性色变为白色。 */
    fun tint(baseColor: Color, isLightTheme: Boolean): Color = highlightTint(baseColor, if (isLightTheme) AlphaLight else AlphaDark)
}

/** 相对于清晰边缘的柔和光晕描边的 alpha 乘数。 */
private const val HaloAlphaFactor = 0.35f

/** 清晰边 alpha 乘数（略降以弱化硬线感）。 */
private const val RimAlphaFactor = 0.85f

/** 饱和度提升 [SATURATION_BOOST]，同时将明度降低至 [HIGHLIGHT_VALUE]。 */
private fun highlightTint(baseColor: Color, alpha: Float): Color {
    if (baseColor.alpha <= 0f) return Color.White.copy(alpha = alpha)
    val v = maxOf(baseColor.red, maxOf(baseColor.green, baseColor.blue))
    val minC = minOf(baseColor.red, minOf(baseColor.green, baseColor.blue))
    val saturation = if (v > 0f) (v - minC) / v else 0f
    if (saturation < NEUTRAL_SATURATION_EPS) return Color.White.copy(alpha = alpha)
    fun boost(channel: Float) = ((v - (v - channel) * SATURATION_BOOST) / v * HIGHLIGHT_VALUE).coerceIn(0f, 1f)
    val vivid = Color(
        red = boost(baseColor.red),
        green = boost(baseColor.green),
        blue = boost(baseColor.blue),
    )
    return vivid.copy(alpha = alpha)
}

/** 应用于边缘着色的饱和度倍数，相对于基础颜色。 */
private const val SATURATION_BOOST = 2.5f

/** 边缘着色的数值上限：数值越低越浓密/越暗，数值越高越亮。 */
private const val HIGHLIGHT_VALUE = 0.4f

/** 低于此饱和度的基础色被视为中性色，并获得纯白色描边。 */
private const val NEUTRAL_SATURATION_EPS = 0.02f
