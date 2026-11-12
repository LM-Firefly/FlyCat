// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.drawEdgeStroke

/**
 * 将玻璃表面和阴影施加到 [backdrop] 之上。
 *
 * 如果运行时着色器不可用或 [backdrop] 为 `null`，则由 [fallback] 定义表面。
 *
 * @param backdrop 玻璃背后的背景。`null` 使用 [fallback]。
 * @param shape 轮廓。它同时驱动阴影、裁剪和渲染几何。
 * @param style 玻璃外观。
 * @param alpha 表面和阴影的不透明度乘数。
 * @param material 表面主体。`null` 表示以模糊后的背景作为主体。
 * @param stroke 沿轮廓的可选泛光描边。
 * @param shadow 阴影配置。`null` 表示禁用阴影。
 * @param shading 是否对背景进行折射和着色。条和菜单请使用 `false`。
 * @param fallback 没有背景时使用的表面修饰符。默认裁剪到 [shape]，
 *   并让调用方后续的内容作为主体。
 */
fun Modifier.glassPanel(
    backdrop: Backdrop?,
    shape: GlassShape,
    style: GlassStyle = GlassDefaults.Style,
    alpha: Float = 1f,
    material: GlassMaterial? = null,
    stroke: EdgeStroke? = null,
    shadow: GlassShadow? = GlassShadows.Regular,
    shading: Boolean = true,
    fallback: Modifier = Modifier.clip(shape),
): Modifier = if (backdrop == null) {
    this
        .graphicsLayer { this.alpha = alpha }
        .glassShadow(shape, shadow)
        .then(fallback)
        .drawWithContent {
            drawContent()
            stroke?.let { drawEdgeStroke(shape, it, 1f) }
        }
} else {
    this
        .glassShadow(shape, shadow, alpha)
        .glass(
            backdrop = backdrop,
            shape = shape,
            style = style,
            alpha = alpha,
            material = material,
            stroke = stroke,
            shading = shading,
        )
}
