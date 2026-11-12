// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.material.internal.GLASS_MASK_SHADER
import top.yukonga.miuix.kmp.material.internal.GLASS_MASK_SHADER_KEY
import top.yukonga.miuix.kmp.material.internal.GLASS_RIM_SHADER
import top.yukonga.miuix.kmp.material.internal.GLASS_RIM_SHADER_KEY
import top.yukonga.miuix.kmp.material.internal.GLASS_SHADOW_SHADER
import top.yukonga.miuix.kmp.material.internal.GLASS_SHADOW_SHADER_KEY
import top.yukonga.miuix.kmp.material.internal.glassEdgeShaderKey
import top.yukonga.miuix.kmp.material.internal.glassEdgeShaderSource
import top.yukonga.miuix.kmp.material.internal.setEdgeStrokeUniforms
import top.yukonga.miuix.kmp.material.internal.sharedShaderCache
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min

/**
 * 沿轮廓以全分辨率绘制边缘光照。`surface` 是边缘下方内容的替身；
 * 共享 shader 在每次绘制前即时设置各自的全部 uniform。
 */
fun DrawScope.drawGlassRim(
    shape: GlassShape,
    style: GlassStyle,
    surface: Color,
    alpha: Float,
) {
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    if (style.background.unShade >= 0.999f) return
    if (!isRuntimeShaderSupported()) return
    val shader = sharedShaderCache.obtainRuntimeShader(GLASS_RIM_SHADER_KEY, GLASS_RIM_SHADER)

    // 微小表面优先于名义最小带宽。
    val edge = (style.edge.width * density / GlassDefaults.SourceDensity)
        .coerceAtLeast(1f)
        .coerceAtMost(size.minDimension * 0.5f)

    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    shader.setRimUniforms(style, surface, alpha, edge)

    drawRect(brush = shader.asBrush(), blendMode = BlendMode.Plus)
}

/**
 * 一趟同时绘制边缘光与边缘描边，替代 [drawEdgeStroke] + [drawGlassRim]：
 * 两者都是沿轮廓的加色光照。[drawGlassMask] 仍在其后运行。
 */
fun DrawScope.drawGlassEdge(
    shape: GlassShape,
    stroke: EdgeStroke,
    style: GlassStyle,
    surface: Color,
    alpha: Float,
) {
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    if (!isRuntimeShaderSupported()) return
    val shader = sharedShaderCache.obtainRuntimeShader(
        glassEdgeShaderKey(stroke.peak),
        glassEdgeShaderSource(stroke.peak),
    )

    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    shader.setEdgeStrokeUniforms(
        stroke = stroke,
        alpha = alpha,
        density = this,
        widthPx = size.width,
        heightPx = size.height,
    )
    // 微小表面优先于名义最小带宽。
    val edge = (style.edge.width * density / GlassDefaults.SourceDensity)
        .coerceAtLeast(1f)
        .coerceAtMost(size.minDimension * 0.5f)
    shader.setRimUniforms(style, surface, alpha, edge)

    drawRect(brush = shader.asBrush(), blendMode = BlendMode.Plus)
}

/** 设置玻璃边缘程序读取的边缘光 uniform。 */
private fun RuntimeShader.setRimUniforms(
    style: GlassStyle,
    surface: Color,
    alpha: Float,
    edge: Float,
) {
    // 完全 unshade 的样式不渲染边缘；把光照清零而不是跳过整趟处理，这样描边仍能画出。
    val rimAlpha = if (style.background.unShade >= 0.999f) 0f else alpha
    setFloatUniform("in_rimEdge", edge, style.edge.pow.coerceAtLeast(0.01f), rimAlpha, 0f)
    setFloatUniform(
        "in_lightDir",
        style.light.directionX,
        style.light.directionY,
        style.light.directionZ,
        style.light.angleRange * PI.toFloat(),
    )
    setFloatUniform("in_lightAmt", style.light.intensity, style.light.oppositeIntensity, 0f, 0f)
    setFloatUniform("in_surface", surface.red, surface.green, surface.blue, 1f)
}

/** 把表面按 [shape] 裁剪，全分辨率。 */
fun DrawScope.drawGlassMask(shape: GlassShape) {
    if (size.width <= 0f || size.height <= 0f) return
    if (!isRuntimeShaderSupported()) return
    val shader = sharedShaderCache.obtainRuntimeShader(GLASS_MASK_SHADER_KEY, GLASS_MASK_SHADER)
    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    drawRect(brush = shader.asBrush(), blendMode = BlendMode.DstIn)
}

/** 把 [shadow] 绘制在 [shape] 边界之外。 */
fun DrawScope.drawGlassShadow(
    shape: GlassShape,
    shadow: GlassShadow,
    alpha: Float,
) {
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    if (!isRuntimeShaderSupported()) return
    val shader = sharedShaderCache.obtainRuntimeShader(GLASS_SHADOW_SHADER_KEY, GLASS_SHADOW_SHADER)

    val sourceScale = density / GlassDefaults.SourceDensity
    val reach = (shadow.radius * sourceScale).coerceAtLeast(1f)
    val offsetX = shadow.offsetX * sourceScale
    val offsetY = shadow.offsetY * sourceScale

    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    shader.setFloatUniform("in_shadowOffset", offsetX, offsetY)
    shader.setFloatUniform("in_shadowShape", reach, shadow.dispersion)
    val color = shadow.color
    shader.setFloatUniform(
        "in_shadowColor",
        color.red,
        color.green,
        color.blue,
        color.alpha * alpha,
    )

    drawRect(
        brush = shader.asBrush(),
        topLeft = Offset(-reach + offsetX, -reach + offsetY),
        size = Size(size.width + reach * 2f, size.height + reach * 2f),
    )
}

/**
 * 为在 [scale] 下绘制的轮廓（1 = 全分辨率；> 1 = 降采样图层，所有像素空间值都要除以该层级）
 * 设置共享距离场的尺寸、圆角半径与平滑度 uniform。边缘光、边缘描边、蒙版、阴影以及
 * `miuix-glass` 中的折射 shader 都在此解析这些值：RTL 下起终点互换、圆角半径截断到短边的一半、
 * 平滑度截断到 0..1。
 */
fun RuntimeShader.setSilhouetteUniforms(
    shape: GlassShape,
    layoutDirection: LayoutDirection,
    size: Size,
    density: Density,
    scale: Float = 1f,
) = setSilhouetteUniforms(
    shape = shape,
    layoutDirection = layoutDirection,
    size = size,
    density = density,
    smoothing = shape.smoothing,
    scale = scale,
)

/** 适用于任意圆角形状的 [setSilhouetteUniforms]；圆形圆角时 [smoothing] 为 0。 */
internal fun RuntimeShader.setSilhouetteUniforms(
    shape: CornerBasedShape,
    layoutDirection: LayoutDirection,
    size: Size,
    density: Density,
    smoothing: Float,
    scale: Float = 1f,
) {
    val halfMin = min(size.width, size.height) * 0.5f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val start = shape.topStart.toPx(size, density)
    val end = shape.topEnd.toPx(size, density)
    val bottomEnd = shape.bottomEnd.toPx(size, density)
    val bottomStart = shape.bottomStart.toPx(size, density)

    setFloatUniform("in_size", size.width / scale, size.height / scale)
    setFloatUniform(
        "in_radii",
        (if (isLtr) start else end).coerceIn(0f, halfMin) / scale,
        (if (isLtr) end else start).coerceIn(0f, halfMin) / scale,
        (if (isLtr) bottomEnd else bottomStart).coerceIn(0f, halfMin) / scale,
        (if (isLtr) bottomStart else bottomEnd).coerceIn(0f, halfMin) / scale,
    )
    setFloatUniform("in_smoothing", smoothing.coerceIn(0f, 1f))
}
