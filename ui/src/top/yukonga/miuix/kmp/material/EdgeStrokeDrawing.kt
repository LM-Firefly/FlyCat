// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import top.yukonga.miuix.kmp.material.internal.drawEdgeStrokeShape

/**
 * 沿 [shape] 的轮廓绘制 [stroke]，覆盖在之前绘制的内容之上。
 *
 * 轮廓是圆角超椭圆，因此 [shape] 必须是圆角形状，例如 `RoundedCornerShape` 或 [GlassShape]。
 * [GlassShape] 还会贡献其连续圆角平滑度。[alpha] 是整体不透明度乘数（如按压进度）。
 * 当平台不支持 runtime shader、或 [shape] 没有可跟随的圆角轮廓时跳过绘制。
 */
fun DrawScope.drawEdgeStroke(
    shape: Shape,
    stroke: EdgeStroke,
    alpha: Float = 1f,
) {
    if (stroke.width.value <= 0f || alpha <= 0f) return
    if (shape is CutCornerShape) return
    val cornerShape = shape as? CornerBasedShape ?: return
    drawEdgeStrokeShape(
        shape = cornerShape,
        smoothing = (shape as? GlassShape)?.smoothing ?: 0f,
        stroke = stroke,
        alpha = alpha,
    )
}
