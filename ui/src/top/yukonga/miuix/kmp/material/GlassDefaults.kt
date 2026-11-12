// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 玻璃材质的默认值。 */
object GlassDefaults {
    /** 玻璃表面的默认圆角半径。*/
    val CornerRadius: Dp = 24.dp
    /** 默认拐角平滑度。1 表示绘制连续拐角。*/
    val Smoothing: Float = 1f
    /**
     * 显示密度 [GlassStyle] 和 [GlassShadow] token 所设定的密度，单位为像素每 dp。
     * 其像素值会按 `density / SourceDensity` 进行缩放，从而使描边在任何屏幕上都保持相同的物理尺寸。
     */
    val SourceDensity: Float = 3f
    /** 背景模糊的默认噪点抖动系数。0 表示禁用噪点。 */
    val NoiseCoefficient: Float = 0.0045f
    /** 材质在轮廓之外延伸的距离，单位为 dp。全分辨率蒙版通道会在之后裁剪边缘，因此材质只需超出轮廓即可。 */
    val Overspill: Float = 3f
    /** 默认材质。 */
    @Stable
    val Style: GlassStyle = GlassStyles.CommonMediumRegularLowLight
    /** 当前主题的默认材质。 @param isDark 为 True 时返回暗色主题材质。 */
    @Stable
    fun style(isDark: Boolean): GlassStyle = GlassStyles.forTheme(
        isDark = isDark,
        light = GlassStyles.CommonMediumRegularLowLight,
        dark = GlassStyles.CommonMediumRegularDark,
    )
}
