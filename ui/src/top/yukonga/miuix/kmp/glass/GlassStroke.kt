// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * 塑造泛光描边的一盏光。
 *
 * @property x 水平位置，取值 `[0, 1]`。
 * @property y 垂直位置，取值 `[0, 1]`。
 * @property z 带符号的深度。负值表示光位于表面之后。
 * @property color 光的颜色。其 alpha 即强度。
 */
@Immutable
data class GlassStrokeLight(
    val x: Float,
    val y: Float,
    val z: Float,
    val color: Color,
)

/**
 * 沿玻璃轮廓的受光边缘。
 *
 * @property width 平直描边带的宽度，单位 dp。
 * @property bevel 光线所依附的着色斜面的深度，单位 dp。
 * @property color 平直描边的颜色。其 alpha 即强度。
 * @property primary 第一盏光。所有内置描边都把它放在左上方。
 * @property secondary 第二盏光，与第一盏相对。
 */
@Immutable
data class GlassStroke(
    val width: Float,
    val bevel: Float,
    val color: Color,
    val primary: GlassStrokeLight,
    val secondary: GlassStrokeLight,
)

/** 内置的泛光描边。 */
object GlassStrokes {

    /** 大号亮色泛光描边。 */
    @Stable
    val BigLight: GlassStroke = GlassStroke(
        width = 0.8f,
        bevel = 1.2f,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = GlassStrokeLight(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.6f)),
        secondary = GlassStrokeLight(0.5f, 0.9f, -0.5f, Color(1f, 1f, 1f, 0.05f)),
    )

    /** 中号亮色泛光描边。 */
    @Stable
    val MiddleLight: GlassStroke = GlassStroke(
        width = 0.8f,
        bevel = 1.2f,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = GlassStrokeLight(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.5f)),
        secondary = GlassStrokeLight(0.7f, 0.8f, 0f, Color(1f, 1f, 1f, 0.3f)),
    )

    /** 小号亮色泛光描边。 */
    @Stable
    val SmallLight: GlassStroke = GlassStroke(
        width = 0.8f,
        bevel = 1.2f,
        color = Color(1f, 1f, 1f, 0.05f),
        primary = GlassStrokeLight(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.6f)),
        secondary = GlassStrokeLight(0.5f, 0.95f, -0.5f, Color(1f, 1f, 1f, 0.35f)),
    )

    /** 大号暗色泛光描边。 */
    @Stable
    val BigDark: GlassStroke = GlassStroke(
        width = 0.8f,
        bevel = 1.2f,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = GlassStrokeLight(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.4f)),
        secondary = GlassStrokeLight(0.5f, 0.9f, -0.5f, Color(1f, 1f, 1f, 0.01f)),
    )

    /** 中号暗色泛光描边。 */
    @Stable
    val MiddleDark: GlassStroke = GlassStroke(
        width = 0.8f,
        bevel = 1.2f,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = GlassStrokeLight(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.4f)),
        secondary = GlassStrokeLight(0.7f, 0.8f, 0f, Color(1f, 1f, 1f, 0.2f)),
    )

    /** 小号暗色泛光描边。 */
    @Stable
    val SmallDark: GlassStroke = GlassStroke(
        width = 0.8f,
        bevel = 1.2f,
        color = Color(1f, 1f, 1f, 0.05f),
        primary = GlassStrokeLight(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.6f)),
        secondary = GlassStrokeLight(0.5f, 0.95f, -0.36f, Color(1f, 1f, 1f, 0.25f)),
    )

    /**
     * 从一对主题描边中选取亮色或暗色那一个。
     *
     * @param isDark 为 true 时返回 [dark]。
     * @param light 亮色主题的描边。
     * @param dark 暗色主题的描边。
     */
    @Stable
    fun forTheme(
        isDark: Boolean,
        light: GlassStroke = MiddleLight,
        dark: GlassStroke = MiddleDark,
    ): GlassStroke = if (isDark) dark else light
}
