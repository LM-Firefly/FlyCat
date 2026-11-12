// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.noiseDither
import top.yukonga.miuix.kmp.glass.internal.GLASS_COLOR_BLEND_SHADER_KEY
import top.yukonga.miuix.kmp.glass.internal.colorBlendEffect
import top.yukonga.miuix.kmp.glass.internal.glassEffect
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassInner
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.GlassStyles
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.drawGlassEdge
import top.yukonga.miuix.kmp.material.drawGlassMask
import top.yukonga.miuix.kmp.material.drawGlassRim
import top.yukonga.miuix.kmp.material.drawGlassShadow

/**
 * 在 [backdrop] 之上施加玻璃表面。
 *
 * @param backdrop 玻璃背后的内容。此修饰符必须位于 backdrop 所录制子树的外部；
 *   放在其内部会造成采样循环。
 * @param shape 表面轮廓，用于裁剪、[stroke] 与渲染几何。
 * @param style 玻璃外观。使用 [GlassStyles] 或 [GlassDefaults.Style] 中的预定义样式。
 * @param alpha 颜色层、着色与描边的不透明度乘数。
 * @param tint 覆盖 [GlassInner.tint]。其 alpha 取代样式的着色强度。
 *   [Color.Unspecified] 保留样式的着色。
 * @param material 表面主体：模糊半径与颜色层。其模糊半径覆盖 [style]。
 *   `null` 表示以模糊后的 backdrop 作为主体。
 * @param noiseCoefficient backdrop 模糊的噪声抖动系数。0 表示禁用抖动。
 * @param stroke 沿轮廓的可选泛光描边。`null` 表示禁用。
 * @param contentBlendMode 绘制在玻璃之上的内容所用的混合模式。
 * @param enabled 玻璃效果是否激活。为 false 时内容以无效果的方式绘制。
 * @param shading 是否对 backdrop 进行折射和着色。主体由 [material] 提供的条和菜单请使用 `false`。
 */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: GlassShape,
    style: GlassStyle = GlassDefaults.Style,
    alpha: Float = 1f,
    tint: Color = Color.Unspecified,
    material: GlassMaterial? = null,
    noiseCoefficient: Float = GlassDefaults.NoiseCoefficient,
    stroke: EdgeStroke? = null,
    contentBlendMode: BlendMode = BlendMode.SrcOver,
    enabled: Boolean = true,
    shading: Boolean = true,
): Modifier = glassImpl(
    backdrop = backdrop,
    shape = shape,
    style = style,
    alpha = alpha,
    tint = tint,
    material = material,
    underlayMaterial = null,
    noiseCoefficient = noiseCoefficient,
    stroke = stroke,
    contentBlendMode = contentBlendMode,
    enabled = enabled,
    shading = shading,
)

/** 在操作栏经过模糊与色彩处理的表面之上施加子材质。 */
internal fun Modifier.glassOnActionBar(
    backdrop: Backdrop,
    shape: GlassShape,
    style: GlassStyle,
    alpha: Float,
    material: GlassMaterial?,
    underlayMaterial: GlassMaterial,
    stroke: EdgeStroke?,
    enabled: Boolean = true,
): Modifier = glassImpl(
    backdrop = backdrop,
    shape = shape,
    style = style,
    alpha = alpha,
    tint = Color.Unspecified,
    material = material,
    underlayMaterial = underlayMaterial,
    noiseCoefficient = GlassDefaults.NoiseCoefficient,
    stroke = stroke,
    contentBlendMode = BlendMode.SrcOver,
    enabled = enabled,
    shading = false,
)

private fun Modifier.glassImpl(
    backdrop: Backdrop,
    shape: GlassShape,
    style: GlassStyle,
    alpha: Float,
    tint: Color,
    material: GlassMaterial?,
    underlayMaterial: GlassMaterial?,
    noiseCoefficient: Float,
    stroke: EdgeStroke?,
    contentBlendMode: BlendMode,
    enabled: Boolean,
    shading: Boolean,
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        noiseDither(noiseCoefficient)
        val radius = material?.blurRadius?.value ?: (style.blur.small / GlassDefaults.SourceDensity)
        blur(radius.coerceIn(0f, BlurDefaults.MaxBlurRadius) * density)
        if (underlayMaterial != null) {
            colorBlendEffect(underlayMaterial, 1f, "${GLASS_COLOR_BLEND_SHADER_KEY}ActionBar")
        }
        if (material != null) colorBlendEffect(material, alpha)
        if (shading) glassEffect(style, shape, alpha, tint)
    },
    onDrawFront = {
        // 描边与边缘光是沿同一轮廓的叠加光效：一次通道同时绘制两者，再由遮罩修剪。
        // 不发光或混合方式不同的描边保留自己的独立通道。
        val fuse = stroke != null && shading &&
            stroke.width.value > 0f &&
            stroke.blendMode == BlendMode.Plus
        if (fuse) {
            drawGlassEdge(
                shape = shape,
                stroke = stroke,
                style = style,
                surface = if (tint.isSpecified) tint else style.inner.tint,
                alpha = alpha,
            )
            drawGlassMask(shape)
        } else {
            if (stroke != null) drawEdgeStroke(shape, stroke, alpha)
            drawGlassMask(shape)
            if (shading) {
                drawGlassRim(shape, style, if (tint.isSpecified) tint else style.inner.tint, alpha)
            }
        }
    },
    contentBlendMode = contentBlendMode,
    enabled = enabled,
)

/**
 * 使用 [shape] 在此可组合项下方绘制 [shadow]。
 *
 * @param shape 阴影轮廓。请与对应的 [glass] 表面使用相同的形状。
 * @param shadow 阴影配置。使用 [GlassShadows] 中的值，或 `null` 表示禁用。
 * @param alpha 阴影的不透明度乘数。
 */
fun Modifier.glassShadow(
    shape: GlassShape,
    shadow: GlassShadow? = GlassShadows.Regular,
    alpha: Float = 1f,
): Modifier = glassShadow(shape, shadow) { alpha }

internal fun Modifier.glassShadow(
    shape: GlassShape,
    shadow: GlassShadow?,
    alpha: () -> Float,
): Modifier = if (shadow == null) {
    this
} else {
    this.drawBehind { drawGlassShadow(shape, shadow, alpha()) }
}
