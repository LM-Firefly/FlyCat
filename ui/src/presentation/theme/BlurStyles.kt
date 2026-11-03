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

@file:Suppress("ConstPropertyName", "FunctionName")

package com.github.lmfirefly.flycat.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 活动表面是否呈现深色主题。 */
@Composable
fun isDarkTheme(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f

/**
 * 捕获的页面背景层为所有玻璃表面（底栏、Moe 侧边栏）提供效果。宿主通过 [top.yukonga.miuix.kmp.blur.layerBackdrop] 附加此实例并提供背景；
 * `null` 将禁用玻璃效果，所有消费者回退为纯色表面。
 * 玻璃消费者必须位于背景所捕获的子树（主分页器）**之外**：模糊节点采样覆盖其自身祖先的背景，会导致 HyperOS hwui 在 prepareTree 期间损坏 RenderNode 树（SIGSEGV，RenderThread）。
 * 因此，节点页面上的浮动延迟测试 FAB 使用纯色 chrome 而非采样 [LocalGlassBackdrop]。
 */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 每个路由的背景由 `GlassScaffold` 在 scaffold 主体上方捕获；顶栏槽位按钮与玻璃菜单回退采样该背景（顶栏渐进色带自身采样全局壁纸层，勿把本捕获传给色带）。
 * 顶部栏放置在 Scaffold 的 topBar 插槽中，位于被捕获的主体子树**之外**，因此符合 HyperOS 的同子树约束。
 * `null`（即屏幕未托管在 GlassScaffold 中，例如 Moe 主页 chrome）将仅保留纯遮罩顶部栏。
 */
val LocalTopBarBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** 锚定玻璃表面应采样的背景：当宿主提供了共享背景时，使用页面的共享背景，否则使用顶栏的背景。浮动菜单向此查询，而不是自行编写回退逻辑，因此“页面背景优先，栏背景其次”这一规则只维护一处。 */
@Composable
fun glassSurfaceBackdrop(): LayerBackdrop? = LocalGlassBackdrop.current ?: LocalTopBarBackdrop.current

/** 在 `miuix-blur` 之上提供模糊与玻璃效果。不支持 RuntimeShader 的设备（Android < 33）将所有玻璃表面渲染为纯色回退效果——绝不会直接透明显示。 */
object FlyBlur {
    /** 默认玻璃模糊半径（dp 浮点值，遵循 miuix-blur 惯例，上限为 150）。 */
    const val GlassBlurRadius = 26f
    /** 磨砂玻璃表面，模糊其后的页面 [backdrop]；当背景不存在或设备不支持 RuntimeShader 时，显示纯色 [fallbackColor]。[tint] 叠加在模糊效果之上（NexioSchedule 风格的半透明表面着色）。 */
    fun Modifier.glassSurface(backdrop: LayerBackdrop?, shape: Shape, fallbackColor: Color, blurRadius: Float = GlassBlurRadius, tint: Color? = null): Modifier = if (backdrop != null && isRuntimeShaderSupported()) {
        textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = if (tint != null) {
                BlurColors(blendColors = listOf(BlendColorEntry(color = tint)))
            } else {
                BlurColors()
            },
        )
    } else {
        background(fallbackColor, shape)
    }
}
