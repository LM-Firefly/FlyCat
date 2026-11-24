/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 在 [containerColor] 之上采样自身内容的背景层。
 * 玻璃控件需要采样对象。工作表（sheet）无壁纸层时向库传递 `backdrop = null`（面板绘制不透明颜色，不会对页面进行模糊），
 * 而页面主体可能是半透明的，因此两者在静止状态下都会使玻璃效果为空，直到内容滚动进入时才拾取效果。
 * 先绘制容器颜色可以为它们提供一个基础层；以颜色为键可在主题切换时重新记录。
 */
@Composable
fun rememberSurfaceBackdrop(containerColor: Color): LayerBackdrop = key(containerColor) {
    rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }
}

/**
 * 消费时实时合成两层录制内容的 [Backdrop]：先 [base]（如全局壁纸层）后 [overlay]（如正文内容层）。
 * 壁纸是动态渐变着色器，换色时自己的录制层会更新；若把它烘进正文录制层，页面静止时正文层不再重录，色带会停在旧色（色带残留）。在采样瞬间合成可让底图永远取到最新帧。
 */
@Stable
class CompositeBackdrop(private val base: Backdrop, private val overlay: Backdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = base.isCoordinatesDependent || overlay.isCoordinatesDependent
    override val offsetResidualX: Float get() = overlay.offsetResidualX
    override val offsetResidualY: Float get() = overlay.offsetResidualY
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(base) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(overlay) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}
