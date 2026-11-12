// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import top.yukonga.miuix.kmp.material.GlassShadows

/**
 * 排布玻璃导航胶囊与一个独立的尾随操作。
 *
 * 在测量 [content] 之前先预留出操作的测量宽度与 [spacing]，因此两者在同一约束内整体居中。
 * [trailingAction] 为 null 时，其退出动画完成后不再预留空间。宽度、间距、缩放与不透明度
 * 一起动画。排布跟随当前布局方向。
 *
 * @param modifier 应用于组合布局的修饰符。
 * @param spacing 胶囊与操作之间的间距。
 * @param trailingAction 可选的独立操作，例如图标按钮。
 * @param content 导航胶囊，不含外边距与系统栏嵌入。
 */
@Composable
internal fun GlassNavigationBarLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = GlassNavigationBarDefaults.ActionSpacing,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val shadowOutsets = remember(density) { with(density) { popupShadowOutsets(GlassShadows.Regular) } }
    val retainedAction = remember { mutableStateOf(trailingAction) }
    val actionProgress = animateFloatAsState(
        targetValue = if (trailingAction != null) 1f else 0f,
        animationSpec = GlassMotion.navShowHide(),
        label = "glassNavigationAction",
    )
    SideEffect {
        if (trailingAction != null || actionProgress.value == 0f) retainedAction.value = trailingAction
    }
    val presentedAction = trailingAction ?: retainedAction.value.takeIf { actionProgress.value > 0f }
    Layout(
        modifier = modifier,
        content = {
            Box(propagateMinConstraints = true) { content() }
            if (presentedAction != null) {
                Box(
                    Modifier.focusProperties { canFocus = trailingAction != null }.graphicsLayer {
                        // 让图标按钮的阴影保留在离屏 alpha 图层之外。
                        outsets = shadowOutsets
                        val fraction = actionProgress.value.coerceIn(0f, 1f)
                        scaleX = fraction
                        scaleY = fraction
                        alpha = fraction
                    }.then(
                        if (trailingAction == null) {
                            Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                            }
                        } else {
                            Modifier
                        },
                    ),
                ) { presentedAction() }
            }
        },
    ) { measurables, constraints ->
        val action = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val fraction = actionProgress.value.coerceIn(0f, 1f)
        val actionWidth = ((action?.width ?: 0) * fraction).toInt()
        val gap = if (action != null) {
            (spacing.toPx() * fraction).toInt().coerceIn(0, (constraints.maxWidth - actionWidth).coerceAtLeast(0))
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
            action?.placeRelative(start + bar.width + gap + (actionWidth - action.width) / 2, (height - action.height) / 2)
        }
    }
}
