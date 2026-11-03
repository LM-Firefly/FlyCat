// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassShadow
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassStroke
import top.yukonga.miuix.kmp.glass.GlassStrokeLight
import top.yukonga.miuix.kmp.glass.GlassStyle
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

/**
 * 各覆盖层着色器在各次绘制之间均无状态——每个 uniform 都在读取它的那次绘制之前立即写入——
 * 因此进程内所有玻璃表面共用各自唯一的一个实例。
 */
private val maskShader: RuntimeShader? by lazy {
    if (isRuntimeShaderSupported()) RuntimeShader(GLASS_MASK_SHADER) else null
}

private val strokeShader: RuntimeShader? by lazy {
    if (isRuntimeShaderSupported()) RuntimeShader(GLASS_STROKE_SHADER) else null
}

private val rimShader: RuntimeShader? by lazy {
    if (isRuntimeShaderSupported()) RuntimeShader(GLASS_RIM_SHADER) else null
}

/**
 * 以全分辨率在表面上绘制材质自身的边缘光。
 *
 * @param shape 边缘光沿循的轮廓。
 * @param layoutDirection 解析轮廓的起始/结束角。
 * @param style 提供边缘几何与光源参数的材质。
 * @param surface 材质自身颜色，代表边缘下方的内容。
 * @param alpha 表面透明度，使边缘光随所属玻璃一同淡出。
 */
internal fun DrawScope.drawGlassRim(
    shape: GlassShape,
    layoutDirection: LayoutDirection,
    style: GlassStyle,
    surface: Color,
    alpha: Float,
) {
    val shader = rimShader ?: return
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    if (style.background.unShade >= 0.999f) return

    // 极小表面优先于名义上的最小带宽。
    val edge = (style.edge.width * density / GlassDefaults.SourceDensity)
        .coerceAtLeast(1f)
        .coerceAtMost(size.minDimension * 0.5f)

    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    shader.setFloatUniform("in_rimEdge", edge, style.edge.pow.coerceAtLeast(0.01f), alpha, 0f)
    shader.setFloatUniform(
        "in_lightDir",
        style.light.directionX,
        style.light.directionY,
        style.light.directionZ,
        style.light.angleRange * PI.toFloat(),
    )
    shader.setFloatUniform("in_lightAmt", style.light.intensity, style.light.oppositeIntensity, 0f, 0f)
    shader.setFloatUniform("in_surface", surface.red, surface.green, surface.blue, 1f)

    drawRect(brush = shader.asBrush(), blendMode = BlendMode.Plus)
}

/**
 * 以全分辨率在表面上绘制泛光描边。
 *
 * @param shape 描边沿循的轮廓。
 * @param layoutDirection 解析轮廓的起始/结束角。
 * @param stroke 要绘制的描边。
 * @param alpha 表面透明度，使描边随所属玻璃一同淡出。
 */
internal fun DrawScope.drawGlassStroke(
    shape: GlassShape,
    layoutDirection: LayoutDirection,
    stroke: GlassStroke,
    alpha: Float,
) {
    val shader = strokeShader ?: return
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return

    // setBloomStrokeConfig 使用标量 setBloomStrokeWithDp 重载，它保留半像素偏置，
    // 而不是像数组重载那样取整。
    val width = (stroke.width * density + 0.5f)
        .coerceAtLeast(0.5f)
        .coerceAtMost(size.minDimension * 0.5f)
    val bevel = (stroke.bevel * density + 0.5f).coerceAtLeast(0.5f)

    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    shader.setFloatUniform("in_halfViewFloor", floor(size.width * 0.5f), floor(size.height * 0.5f))
    shader.setFloatUniform("in_strokeBand", width, bevel)
    val color = stroke.color
    shader.setFloatUniform("in_strokeColor", color.red, color.green, color.blue, color.alpha)
    shader.setFloatUniform("in_strokeAlpha", alpha)
    shader.setLightUniforms("in_light1", "in_light1Color", stroke.primary)
    shader.setLightUniforms("in_light2", "in_light2Color", stroke.secondary)

    drawRect(brush = shader.asBrush(), blendMode = BlendMode.Plus)
}

/**
 * 以全分辨率把表面裁剪为 [shape]。
 *
 * @param shape 要裁剪出的轮廓。
 * @param layoutDirection 解析轮廓的起始/结束角。
 */
internal fun DrawScope.drawGlassMask(shape: GlassShape, layoutDirection: LayoutDirection) {
    val shader = maskShader ?: return
    if (size.width <= 0f || size.height <= 0f) return
    shader.setSilhouetteUniforms(shape, layoutDirection, size, this)
    drawRect(brush = shader.asBrush(), blendMode = BlendMode.DstIn)
}

/** 上传 `GLASS_SDF_SOURCE` 所读取的尺寸、各角半径与平滑度。 */
private fun RuntimeShader.setSilhouetteUniforms(
    shape: GlassShape,
    layoutDirection: LayoutDirection,
    size: Size,
    density: Density,
) {
    val halfMin = min(size.width, size.height) * 0.5f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val start = shape.topStart.toPx(size, density)
    val end = shape.topEnd.toPx(size, density)
    val bottomEnd = shape.bottomEnd.toPx(size, density)
    val bottomStart = shape.bottomStart.toPx(size, density)

    setFloatUniform("in_size", size.width, size.height)
    setFloatUniform(
        "in_radii",
        (if (isLtr) start else end).coerceIn(0f, halfMin),
        (if (isLtr) end else start).coerceIn(0f, halfMin),
        (if (isLtr) bottomEnd else bottomStart).coerceIn(0f, halfMin),
        (if (isLtr) bottomStart else bottomEnd).coerceIn(0f, halfMin),
    )
    setFloatUniform("in_smoothing", shape.smoothing.coerceIn(0f, 1f))
}

/** 把光源位置换算成描边着色器所需的单位方向向量与权重。 */
private fun RuntimeShader.setLightUniforms(
    directionName: String,
    colorName: String,
    light: GlassStrokeLight,
) {
    val dx = light.x - LIGHT_REF_X
    val dy = light.y - LIGHT_REF_Y
    val dz = light.z
    val length = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
    setFloatUniform(directionName, dx / length, dy / length, dz / length, light.color.alpha)
    setFloatUniform(colorName, light.color.red, light.color.green, light.color.blue, 0f)
}

private val shadowShader: RuntimeShader? by lazy {
    if (isRuntimeShaderSupported()) RuntimeShader(GLASS_SHADOW_SHADER) else null
}

/**
 * 绘制表面投下的阴影，其范围可超出表面自身边界。
 *
 * @param shape 投射阴影的轮廓。
 * @param layoutDirection 解析轮廓的起始/结束角。
 * @param shadow 要投射的阴影。
 * @param alpha 表面透明度，使阴影随所属表面一同淡出。
 */
internal fun DrawScope.drawGlassShadow(
    shape: GlassShape,
    layoutDirection: LayoutDirection,
    shadow: GlassShadow,
    alpha: Float,
) {
    val shader = shadowShader ?: return
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return

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
