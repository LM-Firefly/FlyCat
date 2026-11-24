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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.LocalTopBarBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 路由顶栏：委托库 [GlassTopAppBar]，对齐 miuix 示例 app 的渐进模糊（AdaptiveTopAppBar 的玻璃路径）。
 * 内容滚入顶栏下方时，顶栏背景对正文录制层做渐进模糊，色带随折叠渐显，槽位按钮采样同一渲染场景。
 * 滚动状态由库从 scrollBehavior.state 的 contentOffset/overscrollOffset 推导，渐进模糊与折叠动画由库内部处理。
 *
 * @param title 标题，顶栏折叠时居中显示。
 * @param scrollBehavior 驱动折叠与材质渐显的滚动行为。
 * @param modifier 应用于顶栏的修饰符。
 * @param navigationIconPadding 首端控件的起始内缩。
 * @param actionIconPadding 末端控件的末端内缩。
 * @param navigationIcon 首端控件槽位。
 * @param actions 末端控件槽位。
 * @param titleContent 非空时改用钉住模式（[SmallTopBar]），标题块随滚动缩放，位于 [bottomContent] 之上。
 * @param bottomContent 钉在顶栏内标题下方的内容。
 * @param backdrop 渐进模糊与按钮采样的背景；默认为 GlassScaffold 正文录制层，无则回退全局壁纸录制层。
 * @param transparent 完全透明顶栏（无色带/渐进模糊/材质背景），用于背景自身即装饰的页面（如关于页渐变壁纸）；优先级高于 [backdrop]。
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
    titleCollapsed: Boolean = false,
    bottomContent: @Composable () -> Unit = {},
    backdrop: Backdrop? = LocalTopBarBackdrop.current ?: LocalGlassBackdrop.current,
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
            titleCollapsed = titleCollapsed,
            bottomContent = {
                // 标题块（随滚动缩放）与 bottomContent 纵向堆叠；bottomContent 在缩放层之外，保持原位不随标题缩小。
                Column(Modifier.fillMaxWidth()) {
                    // 标题字号只保留两档（展开/收起）：由调用方以列表滚动位置驱动（[titleCollapsed]，回顶自然归零）。
                    // 旧实现读 contentOffset 的 consumed 累计，下拉回顶时被链条上的其他 nestedScroll 消费掉一部分而归不了零，会卡在小档。
                    val scale by animateFloatAsState(targetValue = if (titleCollapsed) 0.72f else 1f, label = "titleShrink")
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
    GlassTopAppBar(
        title = title,
        modifier = modifier,
        progressiveBlur = !transparent,
        scrollBehavior = scrollBehavior,
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
    titleCollapsed: Boolean = false,
    bottomContent: @Composable () -> Unit = {},
    backdrop: Backdrop? = LocalTopBarBackdrop.current ?: LocalGlassBackdrop.current,
    transparent: Boolean = false,
) {
    GlassTopAppBar(
        title = title,
        modifier = modifier,
        expandable = false,
        progressiveBlur = !transparent,
        scrollBehavior = scrollBehavior,
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
