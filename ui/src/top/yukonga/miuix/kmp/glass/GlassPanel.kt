// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.internal.drawGlassStroke

/**
 * 将此可组合项变成覆盖在 [backdrop] 之上的一片玻璃，并带上它投下的阴影。
 *
 * 没有运行时着色器的设备没有可采样的背景，此时由 [fallback] 说明表面是什么。
 *
 * @param backdrop 玻璃背后的 [Backdrop]。`null` 使用 [fallback]。
 * @param shape 轮廓。它同时驱动阴影、裁剪和着色器。
 * @param style 材质样式。
 * @param alpha 整个表面（含阴影）的不透明度乘数。
 * @param material 面板自身的主体。`null` 表示以模糊后的背景作为整个主体。
 * @param stroke 沿边缘的可选泛光描边。
 * @param shadow 阴影。`null` 表示移除。
 * @param shading 表面是否对其所覆盖的内容进行折射和着色。控件设为 `true`，条或菜单设为 `false`。
 * @param fallback 没有背景时表面的形态。默认裁剪到 [shape]，并让调用方接着绘制的内容作为主体。
 */
fun Modifier.glassPanel(
    backdrop: Backdrop?,
    shape: GlassShape,
    style: GlassStyle = GlassDefaults.Style,
    alpha: Float = 1f,
    material: GlassMaterial? = null,
    stroke: GlassStroke? = null,
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
            stroke?.let { drawGlassStroke(shape, layoutDirection, it, 1f) }
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
