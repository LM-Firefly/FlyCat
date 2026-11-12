// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.blur

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

/**
 * 把两层 [Backdrop] 合成一层采样：先画 [under]，再画 [over]。
 *
 * 供玻璃消费者同时采样两层录制（如壁纸层 + 页面内容层）：内容录制层在无内容区域是透明的，
 * 单独采样会让玻璃在这些区域整体透出（"全透"）；垫上不透明的底层后采样处处完整，
 * 模糊与颜色层在任何区域都一致生效。消费者必须位于两层录制子树之外（HyperOS hwui 自采样崩溃约束）。
 *
 * FORK（FlyCat）：拟上游化进 miuix-blur 的组合工具（上游 GlassIconDropdownMenu 现为内联同构实现）。
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
        // FORK（FlyCat）：0.9.4 Backdrop.drawBackdrop 的 downscaleFactor 为 Int（上游 #479 起为 Float）。
        downscaleFactor: Int,
    ) {
        with(under) { with(this@drawBackdrop) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) } }
        with(over) { with(this@drawBackdrop) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) } }
    }
}
