// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material.internal

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.material.DefaultRuntimeShaderCache
import top.yukonga.miuix.kmp.material.EdgeLightSource
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.RuntimeShader
import top.yukonga.miuix.kmp.material.asBrush
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.setSilhouetteUniforms
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 材质自用 shader 的共享缓存：数量少、只持有编译产物，且不依赖任何组合。
 */
internal val sharedShaderCache: DefaultRuntimeShaderCache = DefaultRuntimeShaderCache()

/** 把 [EdgeLightSource] 转换为 3D 方向时使用的参考原点（归一化 UV）。 */
internal const val LIGHT_REF_X = 0.5f
internal const val LIGHT_REF_Y = 0.7f

/**
 * 沿 [shape] 的轮廓绘制 [stroke]，与蒙版共享 [setSilhouetteUniforms]。
 */
internal fun DrawScope.drawEdgeStrokeShape(
    shape: CornerBasedShape,
    smoothing: Float,
    stroke: EdgeStroke,
    alpha: Float,
) {
    if (stroke.width.value <= 0f || alpha <= 0f) return
    if (!isRuntimeShaderSupported()) return
    val widthPx = size.width
    val heightPx = size.height
    if (widthPx <= 0f || heightPx <= 0f) return

    val shader = sharedShaderCache.obtainRuntimeShader(
        edgeStrokeShaderKey(stroke.peak),
        edgeStrokeShaderSource(stroke.peak),
    )
    shader.setSilhouetteUniforms(shape, layoutDirection, size, this, smoothing)
    shader.setEdgeStrokeUniforms(
        widthPx = widthPx,
        heightPx = heightPx,
        stroke = stroke,
        alpha = alpha,
        density = this,
    )

    drawRect(brush = shader.asBrush(), blendMode = stroke.blendMode)
}

/**
 * 设置描边自身的 uniform——band、颜色与两个光源。轮廓来自 `setSilhouetteUniforms`，必须先执行。
 */
internal fun RuntimeShader.setEdgeStrokeUniforms(
    widthPx: Float,
    heightPx: Float,
    stroke: EdgeStroke,
    alpha: Float,
    density: Density,
) {
    val maxRadius = (widthPx * 0.5f).coerceAtMost(heightPx * 0.5f)
    setFloatUniform("in_halfViewFloor", floor(widthPx * 0.5f), floor(heightPx * 0.5f))
    val band = with(density) { stroke.width.toPx() }
    val bevel = with(density) { stroke.bevel.toPx() }
    // 保留半像素描边宽度偏置，且不取整。
    setFloatUniform(
        "in_strokeBand",
        (band + 0.5f).coerceAtMost(maxRadius),
        bevel + 0.5f,
    )
    val color = stroke.color
    setFloatUniform("in_strokeColor", color.red, color.green, color.blue, color.alpha)
    setFloatUniform("in_strokeAlpha", alpha)
    setEdgeLightUniform("in_light1", "in_light1Color", stroke.primary)
    setEdgeLightUniform("in_light2", "in_light2Color", stroke.secondary)
    when (stroke.peak) {
        EdgePeak.Vertical, EdgePeak.Dual -> Unit

        EdgePeak.Axial -> {
            setEdgeAxisUniform("axis1", stroke.primary, -1f)
            setEdgeAxisUniform("axis2", stroke.secondary, 1f)
        }
    }
}

/** 把光源的 UV 位置转换为单位方向，并把其强度折入 `w`。 */
internal fun RuntimeShader.setEdgeLightUniform(
    directionName: String,
    colorName: String,
    light: EdgeLightSource,
) {
    val dx = light.x - LIGHT_REF_X
    val dy = light.y - LIGHT_REF_Y
    val dz = light.z
    val length = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
    setFloatUniform(
        directionName,
        dx / length,
        dy / length,
        dz / length,
        light.color.alpha * light.intensity,
    )
    setFloatUniform(colorName, light.color.red, light.color.green, light.color.blue, 0f)
}

/** 设置轴向光源的 XY 投影门控，退化时回退到竖直轴。 */
internal fun RuntimeShader.setEdgeAxisUniform(name: String, light: EdgeLightSource, fallbackY: Float) {
    val dx = light.x - LIGHT_REF_X
    val dy = light.y - LIGHT_REF_Y
    val length = sqrt(dx * dx + dy * dy)
    if (length > 1e-3f) {
        setFloatUniform(name, dx / length, dy / length)
    } else {
        // 光源纯沿 Z 方向：回退到经典的上/下分割。
        setFloatUniform(name, 0f, fallbackY)
    }
}
