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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalTopBarHazeState = compositionLocalOf<HazeState?> { null }
val LocalTopBarHazeStyle = compositionLocalOf<HazeBlurStyle?> { null }

private fun Modifier.topBarHazeEffect(state: HazeState?, style: HazeBlurStyle?): Modifier {
    if (state == null || style == null) return this
    return hazeBlur(
        input = HazeInput.Sources(state),
        style = style.then {
            blurRadius(UiDp.dp20)
            noiseFactor(0f)
        },
        performanceMode = HazePerformanceMode.Fixed(0.35f),
    )
}

@Composable
fun TopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = UiDp.dp24,
    actionIconPadding: Dp = UiDp.dp24,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    val hazeState = LocalTopBarHazeState.current
    val hazeStyle = LocalTopBarHazeStyle.current
    val hazeEnabled = hazeState != null && hazeStyle != null
    if (titleContent != null) {
        SmallTopBar(
            title = " ",
            scrollBehavior = scrollBehavior,
            modifier = modifier,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = {
                // contentOffset 在 SmallTopBar 钉高时仍随列表滚动更新。
                val shrink = (kotlin.math.abs(scrollBehavior.state.contentOffset) / 160f).coerceIn(0f, 1f)
                val scale = 1f - 0.28f * shrink
                Box(
                    Modifier
                        // 布局高度与绘制缩放同步收窄，下方代理链路/列表才能跟着上移；只做 graphicsLayer 的话占位不变，列表可见区域不会变多。
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = (placeable.height * scale).toInt().coerceAtLeast(0)
                            layout(placeable.width, height) {
                                placeable.place(0, 0)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
//                            alpha = 1f - 0.20f * shrink
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .padding(bottom = TopAppBarDefaults.LargeTitleBottomPadding),
                ) {
                    titleContent()
                }
                bottomContent()
            },
        )
        return
    }
    TopAppBar(
        title = title,
        modifier = modifier.topBarHazeEffect(hazeState, hazeStyle),
        color = if (hazeEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        bottomContent = bottomContent,
    )
}

@Composable
fun SmallTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = UiDp.dp24,
    actionIconPadding: Dp = UiDp.dp24,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val hazeState = LocalTopBarHazeState.current
    val hazeStyle = LocalTopBarHazeStyle.current
    val hazeEnabled = hazeState != null && hazeStyle != null
    SmallTopAppBar(
        title = title,
        modifier = modifier.topBarHazeEffect(hazeState, hazeStyle),
        color = if (hazeEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        bottomContent = bottomContent,
    )
}
