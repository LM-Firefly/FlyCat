// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import kotlin.math.min

/**
 * 按压时表面缩小的幅度。
 *
 * 从表面减去固定像素数而非固定比例，因此小控件的相对缩放比大控件更多，且有下限。
 *
 * @param shorterSidePx 表面两侧中较短的一边，单位为像素。
 * @param density 每 dp 的像素数。
 */
@Stable
fun glassPressScale(shorterSidePx: Float, density: Float): Float = if (shorterSidePx <= 0f) {
    1f
} else {
    max((shorterSidePx - GlassMotion.PRESS_INSET_DP * density) / shorterSidePx, GlassMotion.PRESS_SCALE_MIN)
}

/**
 * 每个可点击表面都有的按压反馈。
 *
 * @param interactionSource 其按压状态驱动缩放的交互源。
 * @param enabled 是否做出反应。为 false 时此修饰符不添加任何效果。
 */
@Composable
fun Modifier.glassPress(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current.density
    var shorterSide by remember { mutableFloatStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) glassPressScale(shorterSide, density) else 1f,
        animationSpec = if (pressed) GlassMotion.pressDown() else GlassMotion.pressUp(),
        label = "glassPress",
    )

    return this
        .onSizeChanged { shorterSide = min(it.width, it.height).toFloat() }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
