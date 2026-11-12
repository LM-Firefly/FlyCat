// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * 沿 [shape] 的轮廓绘制 [stroke]，覆盖在内容之上。
 *
 * 与 `Modifier.drawBackdrop` 搭配，为模糊表面的边缘打光：
 *
 * ```
 * Modifier
 *     .drawBackdrop(backdrop, shape = { shape }, effects = { blur(20f) })
 *     .edgeStroke(shape = shape, stroke = EdgeStrokes.MiddleLight)
 * ```
 *
 * [alpha] 是整体不透明度乘数（如按压进度）。形状在每次绘制时读取，因此动画中的形状始终保持最新。
 */
fun Modifier.edgeStroke(
    shape: Shape,
    stroke: EdgeStroke,
    alpha: Float = 1f,
): Modifier = this then EdgeStrokeElement(shape = shape, stroke = stroke, alpha = alpha)

private class EdgeStrokeElement(
    val shape: Shape,
    val stroke: EdgeStroke,
    val alpha: Float,
) : ModifierNodeElement<EdgeStrokeNode>() {

    override fun create(): EdgeStrokeNode = EdgeStrokeNode(
        shape = shape,
        stroke = stroke,
        alpha = alpha,
    )

    override fun update(node: EdgeStrokeNode) {
        node.shape = shape
        node.stroke = stroke
        node.alpha = alpha
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "edgeStroke"
        properties["stroke"] = stroke
        properties["alpha"] = alpha
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EdgeStrokeElement) return false
        if (shape != other.shape) return false
        if (stroke != other.stroke) return false
        if (alpha != other.alpha) return false
        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + stroke.hashCode()
        result = 31 * result + alpha.hashCode()
        return result
    }
}

private class EdgeStrokeNode(
    var shape: Shape,
    var stroke: EdgeStroke,
    var alpha: Float,
) : Modifier.Node(),
    DrawModifierNode {

    override fun ContentDrawScope.draw() {
        drawContent()
        drawEdgeStroke(shape = shape, stroke = stroke, alpha = alpha)
    }
}
