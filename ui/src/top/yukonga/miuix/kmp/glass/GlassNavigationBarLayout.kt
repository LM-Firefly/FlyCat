// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * 玻璃导航胶囊与一个独立尾部操作的摆放。
 *
 * 该操作拥有自己的尺寸和表面。在测量 [content] 之前，会先预留其测得的宽度和 [spacing]，以便两者在相同约束内容纳并作为一组居中。
 * 为 null 的 [trailingAction] 不预留任何空间。摆放遵循当前的布局方向。
 *
 * @param modifier 应用于组合布局的修饰符。
 * @param spacing 胶囊与操作之间的间距。
 * @param trailingAction 可选的独立操作，例如图标按钮。
 * @param content 导航胶囊，不包含外部边距和系统栏内缩。
 */
@Composable
internal fun GlassNavigationBarLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = GlassNavigationBarDefaults.ActionSpacing,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(propagateMinConstraints = true) { content() }
            if (trailingAction != null) {
                Box { trailingAction() }
            }
        },
    ) { measurables, constraints ->
        val action = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val actionWidth = action?.width ?: 0
        val gap = if (action != null) {
            spacing.roundToPx().coerceIn(0, (constraints.maxWidth - actionWidth).coerceAtLeast(0))
        } else {
            0
        }
        val bar = measurables[0].measure(
            constraints.copy(
                minWidth = (constraints.minWidth - actionWidth - gap).coerceAtLeast(0),
                maxWidth = if (constraints.hasBoundedWidth) {
                    (constraints.maxWidth - actionWidth - gap).coerceAtLeast(0)
                } else {
                    constraints.maxWidth
                },
            ),
        )
        val groupWidth = bar.width + gap + actionWidth
        val width = constraints.constrainWidth(groupWidth)
        val height = constraints.constrainHeight(maxOf(bar.height, action?.height ?: 0))
        layout(width, height) {
            val start = (width - groupWidth) / 2
            bar.placeRelative(start, (height - bar.height) / 2)
            action?.placeRelative(start + bar.width + gap, (height - action.height) / 2)
        }
    }
}
