// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** 未缩放的拖动目标边界（单位像素），位于指示器现有按压变换之前。 */
internal data class NavigationDragTarget(
    val left: Float,
    val right: Float,
    val following: Boolean,
    val movingRight: Boolean,
) {
    val leftSpring: SpringSpec<Float>
        get() = if (following) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(!movingRight)

    val rightSpring: SpringSpec<Float>
        get() = if (following) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(movingRight)
}

/**
 * 同一项的拖动拖尾与跨项的边缘弹簧目标。
 *
 * 60px 的拖尾上限故意不随像素密度缩放。原始弹簧目标可能超出容器；
 * [navigationIndicatorBounds] 会限制实际渲染的边界。当导向目标到达容器边界时，拉伸会被抑制。
 */
internal fun navigationDragTarget(
    left: Float,
    width: Float,
    containerWidth: Float,
    delta: Float,
    changedItem: Boolean,
): NavigationDragTarget {
    val start = left.coerceIn(0f, (containerWidth - width).coerceAtLeast(0f))
    val end = start + width
    val trail = if (changedItem) 0f else (abs(delta) * 4f).coerceAtMost(60f)
    return NavigationDragTarget(
        left = if (delta > 0f && end < containerWidth) start - trail else start,
        right = if (delta < 0f && start > 0f) end + trail else end,
        following = !changedItem,
        movingRight = delta > 0f,
    )
}

/** 将每一条渲染边界限制在最外侧的项内，而不停止底层的弹簧。 */
internal fun navigationIndicatorBounds(left: Float, right: Float, containerWidth: Float, inset: Float): Offset {
    val min = inset.coerceIn(0f, containerWidth.coerceAtLeast(0f) / 2f)
    val max = (containerWidth - min).coerceAtLeast(min)
    val start = left.coerceIn(min, max)
    return Offset(start, right.coerceIn(start, max))
}
