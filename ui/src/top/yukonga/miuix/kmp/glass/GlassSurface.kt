// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.GlassShape

/**
 * 一片玻璃，以库自身组件索取表面时所用的形式呈现。
 *
 * 把它交给浮层组件的 `surface` 槽位，即可让该组件变为玻璃——没有第二套实现。
 *
 * ```
 * OverlayIconCascadingDropdownMenu(
 *     entries = entries,
 *     surface = glassSurface(backdrop, GlassPopupDefaults.visuals()),
 * ) { Icon(…) }
 * ```
 *
 * @param backdrop 提供玻璃背后内容的 [Backdrop]。`null` 时回退到 [visuals] 中的平涂填充色，
 *   这正是没有运行时着色器的设备所得到的效果。
 * @param visuals 表面的构成。
 * @param cornerRadius 当宿主提供的 [Shape] 无法安全转换为 [GlassShape] 时使用的圆角半径。
 *   圆角矩形会原样保留其提供的四个角。
 */
fun glassSurface(
    backdrop: Backdrop?,
    visuals: GlassPopupVisuals,
    cornerRadius: Dp = GlassPopupDefaults.CornerRadius,
): @Composable (Shape) -> Modifier = { providedShape ->
    Modifier.glassPanel(
        backdrop = backdrop,
        shape = resolveGlassSurfaceShape(providedShape, cornerRadius),
        style = visuals.style,
        alpha = visuals.alpha,
        material = visuals.material,
        stroke = visuals.stroke,
        shadow = visuals.shadow,
        shading = false,
        fallback = Modifier.clip(providedShape).background(visuals.containerColor),
    )
}

/** 在着色器、描边和阴影通道中保留宿主的圆角轮廓。 */
internal fun resolveGlassSurfaceShape(providedShape: Shape, fallbackCornerRadius: Dp): GlassShape = when (providedShape) {
    is GlassShape -> providedShape

    is RoundedCornerShape -> GlassShape(
        topStart = providedShape.topStart,
        topEnd = providedShape.topEnd,
        bottomEnd = providedShape.bottomEnd,
        bottomStart = providedShape.bottomStart,
    )

    else -> GlassShape(fallbackCornerRadius)
}
