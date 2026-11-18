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

package com.github.lmfirefly.flycat.presentation.component.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 路由顶栏：委托库 [GlassTopAppBar]（对齐 miuix 液态玻璃——渐变色带 + 材质上下文 + 折叠大标题）。
 * 材质可见性跟随滚动（静止时无内容位于顶栏之下，色带透明；内容滚入即渐显），
 * 顶栏槽位按钮委托库 GlassIconButton（材质与描边随滚动渐显，弹窗锚点由其发布）。
 *
 * @param title 标题，顶栏折叠时居中显示。
 * @param scrollBehavior 驱动折叠的滚动行为。
 * @param modifier 应用于顶栏的修饰符。
 * @param titlePadding 标题与大标题的水平内缩。
 * @param navigationIconPadding 首端控件的起始内缩。
 * @param actionIconPadding 末端控件的末端内缩。
 * @param navigationIcon 首端控件槽位。
 * @param actions 末端控件槽位。
 * @param titleContent 非空时改用钉住模式（[SmallTopBar]），标题块随滚动缩放，位于 [bottomContent] 之上。
 * @param bottomContent 钉在顶栏内标题下方的内容。
 * @param backdrop 顶栏材质上下文采样的背景；默认为全局壁纸录制层。
 * @param transparent 完全透明顶栏（无色带/材质背景），用于背景自身即装饰的页面（如关于页渐变壁纸）；优先级高于 [backdrop]。
 */
@Composable
fun TopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIconPadding: Dp = UiDp.dp24,
    actionIconPadding: Dp = UiDp.dp24,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    transparent: Boolean = false,
) {
    if (titleContent != null) {
        SmallTopBar(
            title = " ",
            scrollBehavior = scrollBehavior,
            modifier = modifier,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            navigationIcon = navigationIcon,
            actions = actions,
            backdrop = backdrop,
            transparent = transparent,
            bottomContent = {
                // 标题块（随滚动缩放）与 bottomContent 纵向堆叠；bottomContent 在缩放层之外，保持原位不随标题缩小。
                Column(Modifier.fillMaxWidth()) {
                    // contentOffset 在钉住模式下仍随列表滚动更新。
                    val shrink = (abs(scrollBehavior.state.contentOffset) / 160f).coerceIn(0f, 1f)
                    val scale = 1f - 0.28f * shrink
                    Box(
                        Modifier
                            // 布局高度与绘制缩放同步收窄，下方代理链路/列表才能跟着上移；只做 graphicsLayer 的话占位不变，列表可见区域不会变多。
                            .layout { measurable, constraints ->
                                // 测量宽度按 scale 反向放大再等比缩小绘制，让一言按缩小后的视觉字号重新排版铺满行宽（graphicsLayer 纯压小不重排，右侧会留白）。
                                val inflatedMaxWidth = (constraints.maxWidth / scale).toInt().coerceIn(constraints.minWidth, Constraints.Infinity)
                                val placeable = measurable.measure(constraints.copy(maxWidth = inflatedMaxWidth))
                                val width = (placeable.width * scale).toInt().coerceAtLeast(0)
                                val height = (placeable.height * scale).toInt().coerceAtLeast(0)
                                layout(width, height) {
                                    placeable.place(0, 0)
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                            // 标题下缘留白收紧，与链路条/卡片的 12dp 纵向节奏对称。
                            .padding(bottom = UiDp.dp2),
                    ) {
                        titleContent()
                    }
                    bottomContent()
                }
            },
        )
        return
    }
    val scrollState = rememberTopBarScrollState(scrollBehavior)
    GlassTopAppBar(
        title = title,
        isContentScrolled = scrollState.isContentScrolled,
        modifier = modifier.clipToBounds(),
        progressiveBlur = !transparent,
        scrollBehavior = scrollBehavior,
        visualContentOffset = scrollState.visualContentOffset,
        backdrop = backdrop,
        bandBrush = GlassTopAppBarDefaults.bandBrush(barBandColor(transparent)),
        backgroundAlpha = { if (transparent) 0f else 1f },
        navigationIcon = { Box(Modifier.offset(x = navigationIconPadding - GlassTopAppBarDefaults.HorizontalPadding)) { navigationIcon() } },
        actions = { Row(Modifier.offset(x = GlassTopAppBarDefaults.HorizontalPadding - actionIconPadding)) { actions() } },
        bottomContent = bottomContent,
    )
}

/**
 * 钉住的小顶栏：不折叠、无大标题，紧凑标题常显（库 [GlassTopAppBar] 的 `pinned` 模式）。
 * 参数语义同 [TopBar]。
 */
@Composable
fun SmallTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIconPadding: Dp = UiDp.dp24,
    actionIconPadding: Dp = UiDp.dp24,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    transparent: Boolean = false,
) {
    val scrollState = rememberTopBarScrollState(scrollBehavior)
    GlassTopAppBar(
        title = title,
        isContentScrolled = scrollState.isContentScrolled,
        modifier = modifier.clipToBounds(),
        expandable = false,
        progressiveBlur = !transparent,
        scrollBehavior = scrollBehavior,
        visualContentOffset = scrollState.visualContentOffset,
        backdrop = backdrop,
        bandBrush = GlassTopAppBarDefaults.bandBrush(barBandColor(transparent)),
        backgroundAlpha = { if (transparent) 0f else 1f },
        navigationIcon = { Box(Modifier.offset(x = navigationIconPadding - GlassTopAppBarDefaults.HorizontalPadding)) { navigationIcon() } },
        actions = { Row(Modifier.offset(x = GlassTopAppBarDefaults.HorizontalPadding - actionIconPadding)) { actions() } },
        bottomContent = bottomContent,
    )
}

/** 顶栏色带的基色：透明顶栏不画色带，否则用表面色（内容滚入时压暗，同 miuix 液态玻璃语义）。 */
@Composable
private fun barBandColor(transparent: Boolean): Color = if (transparent) {
    Color.Transparent
} else {
    MiuixTheme.colorScheme.surface
}

/**
 * 顶栏折叠的显式状态（fork 补偿，随迁移从 vendored 版回收）：
 * 1. isContentScrolled 取 contentOffset < 0 || collapsedFraction > 0——列表不满一整页时，弹性 overscroll 会把顶栏推入折叠却不产生 contentOffset，官方仅按 contentOffset 判定会漏判。
 * 2. visualContentOffset 在折叠度上合成负向偏移再与真实偏移取更小值，等价 fork 色带渐显的 maxOf(scrollRamp, collapsedFraction)——避免「折叠小标题但色带全透、与正文重叠」。
 */
@Composable
private fun rememberTopBarScrollState(scrollBehavior: ScrollBehavior?): TopBarScrollState {
    val density = LocalDensity.current
    val isContentScrolled by remember(scrollBehavior) { derivedStateOf { scrollBehavior?.state?.let { it.contentOffset < 0f || it.collapsedFraction > 0f } ?: true } }
    val visualContentOffset = remember(scrollBehavior, density) {
        {
            val state = scrollBehavior?.state
            if (state == null) { 0f } else {
                val collapseBias = -state.collapsedFraction * with(density) { GlassTopAppBarDefaults.ProgressiveBlurRampDistance.toPx() }
                minOf(state.visualContentOffset, collapseBias)
            }
        }
    }
    return remember(isContentScrolled, visualContentOffset) { TopBarScrollState(isContentScrolled, visualContentOffset) }
}

private class TopBarScrollState(
    val isContentScrolled: Boolean,
    val visualContentOffset: () -> Float,
)
