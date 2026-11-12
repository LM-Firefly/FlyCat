// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 为 [EdgeStroke] 贡献光照的方向光。
 *
 * shader 会把 `(x - 0.5, y - 0.7, z)` 归一化为光线方向，因此 `(0.5, 0.7)` 是参考原点、
 * 不产生任何贡献；[z] 为负表示光源位于表面平面之后，照亮朝向内侧的边缘。
 *
 * @property color 其 alpha 决定贡献强度的缩放。
 * @property intensity 与 [color] 的 alpha 相乘。
 */
@Immutable
data class EdgeLightSource(
    val x: Float,
    val y: Float,
    val z: Float,
    val color: Color = Color.White,
    @param:FloatRange(from = 0.0) val intensity: Float = 1f,
)

/**
 * 各光源边缘贡献的门控方式——即平方前乘入 Lambert 项的因子，决定光源能照亮轮廓的哪一段弧。
 */
enum class EdgePeak {

    /** 固定竖直门控：主光源占据边缘上半部分，次光源占据下半部分。 */
    Vertical,

    /** 门控跟随各光源自身的 XY 方向，照亮的弧随光源移动。 */
    Axial,

    /** `dot(n.xy, L.xy)²`：单个光源产生相隔 180° 的两个峰。 */
    Dual,
}

/**
 * 配置沿形状轮廓绘制的受光边缘：一条平坦的描边带，加上由两个方向 [EdgeLightSource] 照亮的
 * 半球形边缘法线。
 *
 * @property width 紧贴外缘的平坦描边带宽度。
 * @property bevel 向内的光晕深度，即边缘法线的作用范围。
 * @property color 平坦描边带；其 alpha 控制强度。
 * @property primary 门控到边缘第一段弧的光源；具体是哪段弧取决于 [peak]。
 * @property secondary 门控到边缘相对弧段的光源。
 * @property blendMode 描边与已绘内容的合成方式。
 * @property peak 各光源能照亮的弧段。
 */
@Immutable
data class EdgeStroke(
    val width: Dp,
    val bevel: Dp,
    val color: Color,
    val primary: EdgeLightSource,
    val secondary: EdgeLightSource,
    val blendMode: BlendMode = BlendMode.Plus,
    val peak: EdgePeak = EdgePeak.Vertical,
)

/**
 * 为 bloom 风格（OS3）材质调校的边缘描边。
 *
 * 命名格式为 `{Big,Middle,Small}{Light,Dark}`：尺寸决定光晕深度，后缀决定主题。
 */
object EdgeStrokes {

    @Stable
    val BigLight: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 3.5.dp,
        color = Color.White.copy(alpha = 0.05f),
        primary = EdgeLightSource(0.5f, 0.5f, -0.5f, intensity = 0.3f),
        secondary = EdgeLightSource(0.5f, 0.6f, -0.5f, intensity = 0.2f),
        peak = EdgePeak.Axial,
    )

    @Stable
    val MiddleLight: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 2.8.dp,
        color = Color.White.copy(alpha = 0.05f),
        primary = EdgeLightSource(0.5f, 0.5f, -0.5f, intensity = 0.4f),
        secondary = EdgeLightSource(0.5f, 0.8f, -0.5f, intensity = 0.25f),
        peak = EdgePeak.Axial,
    )

    @Stable
    val SmallLight: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 2.6.dp,
        color = Color.White.copy(alpha = 0.05f),
        primary = EdgeLightSource(0.5f, 0.5f, -0.5f, intensity = 0.6f),
        secondary = EdgeLightSource(0.5f, 0.95f, -0.5f, intensity = 0.35f),
        peak = EdgePeak.Axial,
    )

    @Stable
    val BigDark: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.7.dp,
        color = Color.White.copy(alpha = 0.05f),
        primary = EdgeLightSource(0.5f, 0.5f, -0.5f, intensity = 0.4f),
        secondary = EdgeLightSource(0.5f, 0.6f, -0.5f, intensity = 0.25f),
        peak = EdgePeak.Axial,
    )

    @Stable
    val MiddleDark: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 2f.dp,
        color = Color.White.copy(alpha = 0.06f),
        primary = EdgeLightSource(0.5f, 0.5f, -0.5f, intensity = 0.5f),
        secondary = EdgeLightSource(0.5f, 0.8f, -0.5f, intensity = 0.25f),
        peak = EdgePeak.Axial,
    )

    @Stable
    val SmallDark: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 2.3.dp,
        color = Color.White.copy(alpha = 0.08f),
        primary = EdgeLightSource(0.5f, 0.5f, -0.5f, intensity = 0.6f),
        secondary = EdgeLightSource(0.5f, 0.95f, -0.36f, intensity = 0.25f),
        peak = EdgePeak.Axial,
    )

    /** [MiddleLight] 的别名。 */
    @Stable
    val Default: EdgeStroke = MiddleLight
}

/** 为玻璃材质调校的边缘描边；命名规则与 [EdgeStrokes] 相同。 */
object GlassEdgeStrokes {

    @Stable
    val BigLight: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.2.dp,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = EdgeLightSource(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.6f)),
        secondary = EdgeLightSource(0.5f, 0.9f, -0.5f, Color(1f, 1f, 1f, 0.05f)),
    )

    @Stable
    val MiddleLight: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.2.dp,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = EdgeLightSource(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.5f)),
        secondary = EdgeLightSource(0.7f, 0.8f, 0f, Color(1f, 1f, 1f, 0.3f)),
    )

    @Stable
    val SmallLight: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.2.dp,
        color = Color(1f, 1f, 1f, 0.05f),
        primary = EdgeLightSource(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.6f)),
        secondary = EdgeLightSource(0.5f, 0.95f, -0.5f, Color(1f, 1f, 1f, 0.35f)),
    )

    @Stable
    val BigDark: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.2.dp,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = EdgeLightSource(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.4f)),
        secondary = EdgeLightSource(0.5f, 0.9f, -0.5f, Color(1f, 1f, 1f, 0.01f)),
    )

    @Stable
    val MiddleDark: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.2.dp,
        color = Color(1f, 1f, 1f, 0.1f),
        primary = EdgeLightSource(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.4f)),
        secondary = EdgeLightSource(0.7f, 0.8f, 0f, Color(1f, 1f, 1f, 0.2f)),
    )

    @Stable
    val SmallDark: EdgeStroke = EdgeStroke(
        width = 0.8.dp,
        bevel = 1.2.dp,
        color = Color(1f, 1f, 1f, 0.05f),
        primary = EdgeLightSource(0.2f, 0.5f, 0f, Color(1f, 1f, 1f, 0.6f)),
        secondary = EdgeLightSource(0.5f, 0.95f, -0.36f, Color(1f, 1f, 1f, 0.25f)),
    )

    /** 按主题选取 [light] 或 [dark]。 */
    @Stable
    fun forTheme(
        isDark: Boolean,
        light: EdgeStroke = MiddleLight,
        dark: EdgeStroke = MiddleDark,
    ): EdgeStroke = if (isDark) dark else light
}
