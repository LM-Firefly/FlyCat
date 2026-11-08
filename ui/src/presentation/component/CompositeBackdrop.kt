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

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 把两层 [Backdrop] 合成一层采样：先画 [under]，再画 [over]。
 *
 * 供底栏玻璃同时采样壁纸录制层与页面内容录制层：内容录制层在无内容区域是透明的，单独采样会让玻璃在这些区域整体透出（"全透"，issue #220）；
 * 垫上不透明的壁纸层后采样处处完整，模糊与颜色层在任何区域都一致生效。消费者必须位于两层录制子树之外（HyperOS hwui 自采样崩溃约束）。
 */
@Stable
class CompositeBackdrop(private val under: Backdrop, private val over: Backdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean = under.isCoordinatesDependent || over.isCoordinatesDependent
    override val offsetResidualX: Float get() = over.offsetResidualX
    override val offsetResidualY: Float get() = over.offsetResidualY
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(under) { with(this@drawBackdrop) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) } }
        with(over) { with(this@drawBackdrop) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) } }
    }
}
