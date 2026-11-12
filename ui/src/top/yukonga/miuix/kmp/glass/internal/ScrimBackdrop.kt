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

/** 在模糊之前对带边距的背景录制层施加遮罩。 */
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
        // FORK（FlyCat）：0.9.4 Backdrop.drawBackdrop 的 downscaleFactor 为 Int（上游 #479 起为 Float）。
        downscaleFactor: Int,
    ) {
        with(backdrop) {
            drawBackdrop(
                density = density,
                coordinates = coordinates,
                layerBlock = {
                    layerBlock?.invoke(this)
                    // 宿主已经应用了该缩放。只在采样时反向抵消。
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
            // 精确边距：录制会偏移 `padding / level`，取整会留下一条缝。
            val scaledPadding = padding / downscaleFactor
            // 盖住录制边距；SrcAtop 保留透明的场景像素以防止光晕。
            drawRect(color, topLeft = Offset(-scaledPadding, -scaledPadding), size = size, blendMode = BlendMode.SrcAtop)
        }
    }
}
