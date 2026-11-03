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
import top.yukonga.miuix.kmp.glass.internal.drawGlassMask
import top.yukonga.miuix.kmp.glass.internal.drawGlassRim
import top.yukonga.miuix.kmp.glass.internal.drawGlassShadow
import top.yukonga.miuix.kmp.glass.internal.drawGlassStroke
import top.yukonga.miuix.kmp.glass.internal.glassEffect

/**
 * 将此修饰符渲染为覆盖在 [backdrop] 之上的玻璃层。
 * @param backdrop 玻璃背后的内容。此修饰符必须位于 backdrop 所记录的子树*之外*——在其内部两者会形成循环引用。
 * @param shape 轮廓形状。驱动裁剪、[stroke] 和着色器，确保三者保持一致。
 * @param style 材质样式。从 [GlassStyles] 中选择一种，或从 [GlassDefaults.Style] 开始。
 * @param alpha 颜色层、着色和描边的不透明度乘数。
 * @param tint 覆盖 [GlassInner.tint]；其 alpha 值将替换样式中的着色强度。 [Color.Unspecified] 保留样式自身的着色。
 * @param material 表面自身的主体：一个模糊半径及其上的颜色层。它会覆盖 [style] 所请求的模糊半径。`null` 表示以模糊后的背景作为整个主体。
 * @param noiseCoefficient 背景模糊的噪声抖动，用于防止在大面积平坦区域出现色带。0 表示禁用。
 * @param stroke 沿边缘的可选泛光描边。`null` 表示跳过。
 * @param contentBlendMode 被修饰内容如何与玻璃进行合成。
 * @param enabled 材质是否激活。为 false 时，内容自行绘制。
 * @param shading 表面是否对其覆盖的内容进行折射和着色。控件设为 `true`，主体仅为 [material] 的条或菜单则设为 `false`。
 */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: GlassShape,
    style: GlassStyle = GlassDefaults.Style,
    alpha: Float = 1f,
    tint: Color = Color.Unspecified,
    material: GlassMaterial? = null,
    noiseCoefficient: Float = GlassDefaults.NoiseCoefficient,
    stroke: GlassStroke? = null,
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

/** 在操作栏已经模糊并做过颜色处理的表面上叠加一层子材质。*/
internal fun Modifier.glassOnActionBar(
    backdrop: Backdrop,
    shape: GlassShape,
    style: GlassStyle,
    alpha: Float,
    material: GlassMaterial?,
    underlayMaterial: GlassMaterial,
    stroke: GlassStroke?,
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
    stroke: GlassStroke?,
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
        if (underlayMaterial != null) { colorBlendEffect(underlayMaterial, 1f, "${GLASS_COLOR_BLEND_SHADER_KEY}ActionBar") }
        if (material != null) colorBlendEffect(material, alpha)
        if (shading) glassEffect(style, shape, alpha, tint)
    },
    onDrawFront = {
        if (stroke != null) drawGlassStroke(shape, layoutDirection, stroke, alpha)
        drawGlassMask(shape, layoutDirection)
        if (shading) { drawGlassRim(shape, layoutDirection, style, if (tint.isSpecified) tint else style.inner.tint, alpha) }
    },
    contentBlendMode = contentBlendMode,
    enabled = enabled,
)

/**
 * 在被修饰内容下方投射 [shadow]，形状为 [shape]。
 * @param shape 投射阴影的轮廓。使用与 [glass] 相同的轮廓。
 * @param shadow 阴影。从 [GlassShadows] 中选择一个。`null` 表示跳过。
 * @param alpha 不透明度倍数，用于使阴影随其所属表面一同淡出。
 */
fun Modifier.glassShadow(shape: GlassShape, shadow: GlassShadow? = GlassShadows.Regular, alpha: Float = 1f): Modifier = glassShadow(shape, shadow) { alpha }

internal fun Modifier.glassShadow(shape: GlassShape, shadow: GlassShadow?, alpha: () -> Float): Modifier = if (shadow == null) { this } else { this.drawBehind { drawGlassShadow(shape, layoutDirection, shadow, alpha()) } }
