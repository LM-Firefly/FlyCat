// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass.internal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope

/** 在模糊处理之前，向含边距填充的背景录制施加一层蒙层（scrim）。 */
@Stable
internal class ScrimBackdrop(
    private val backdrop: Backdrop,
    private val scrimColor: State<Color>,
    private val scale: State<Float>? = null,
) : Backdrop by backdrop {

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(backdrop) {
            drawBackdrop(
                density = density,
                coordinates = coordinates,
                layerBlock = {
                    layerBlock?.invoke(this)
                    // 宿主已应用该缩放，采样期间只需反向抵消。
                    scale?.let {
                        scaleX *= it.value
                        scaleY *= it.value
                    }
                },
                downscaleFactor = downscaleFactor,
            )
        }
        val color = scrimColor.value
        if (color.alpha > 0f) {
            val padding = (density as? BackdropEffectScope)?.padding ?: 0f
            val scaledPadding = if (downscaleFactor > 1) {
                (padding / downscaleFactor).toInt().toFloat()
            } else {
                padding
            }
            // 录制已平移了 padding，这段边距也要覆盖，但不要填充场景外的像素：
            // SrcAtop 保留它们的 alpha，从而避免产生暗色光晕。
            drawRect(color, topLeft = Offset(-scaledPadding, -scaledPadding), size = size, blendMode = BlendMode.SrcAtop)
        }
    }
}
