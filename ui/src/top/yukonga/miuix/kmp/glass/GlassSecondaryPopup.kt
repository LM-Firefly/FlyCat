// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 从主菜单的行生长出来的次级菜单，而不是从边缘展开的普通弹出层。
 *
 * 该行加上 [contentPadding] 构成初始裁剪矩形；四条边一起运动到测量出的菜单边界，各行保持
 * 自身尺寸并随裁剪移动。起始边与该行对齐，RTL 下镜像。展开过程中各行可接收输入，一旦请求
 * 关闭立即停止。预测性返回会预览收起过程，取消时恢复，完成时关闭。
 *
 * @param show 次级菜单是否展开。收起过程中请保持此调用处于组合状态。
 * @param onDismissRequest 点击外部或按返回键回到主菜单时调用。
 * @param anchorBounds 触发行的静止边界，与本弹出层处于同一根坐标系。在设置主 [GlassTransformPopup] 的 `stacked` 标志之前先冻结这些边界。
 * @param backdrop 此菜单背后采样的背景。把主弹出层也放进这一层（使其渲染内容在下方被模糊），但排除本子菜单。`null` 使用锚点的背景采样。
 * @param modifier 应用于面板的修饰符。
 * @param materialAnchor 主弹出层的按钮锚点。传入同一锚点可在两层之间共享其展开态弹出外观。
 * @param sizing 面板限制。把 `minWidth` 设为主面板的测量宽度。
 * @param visuals 表面外观，在拿不到主弹出层外观时使用。
 * @param cornerRadius 整个过渡期间使用的圆角裁剪半径。
 * @param contentPadding 菜单各行周围的内边距，收起状态的行周围也包含它。
 * @param onDismissFinished 收起动画完成时调用。
 * @param content 先是标题行，然后是次级选项。标题行应请求收起。
 */
@Composable
fun BoxScope.GlassSecondaryPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    materialAnchor: GlassPopupAnchor? = null,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    cornerRadius: Dp = GlassPopupDefaults.CornerRadius,
    contentPadding: PaddingValues = PaddingValues(vertical = GlassPopupDefaults.ContentPaddingVertical),
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = show
    val transition = rememberTransition(state, label = "glassSecondaryPopup")
    val progress = transition.animateFloat(
        transitionSpec = { GlassMotion.secondaryPopup(targetState) },
        label = "glassSecondaryBounds",
    ) { if (it) 1f else 0f }
    val active = show || state.currentState || !state.isIdle
    val backProgress = rememberGlassPopupBackProgress(
        show = show,
        active = active,
        enabled = show,
        retainWhenInactive = false,
        resetSpec = GlassMotion.secondaryPopup(true),
        onDismissRequest = onDismissRequest,
    )
    DisposableEffect(materialAnchor, backProgress) {
        materialAnchor?.secondaryBackProgressState = backProgress
        onDispose {
            if (materialAnchor?.secondaryBackProgressState === backProgress) {
                materialAnchor.secondaryBackProgressState = null
            }
        }
    }
    val onFinished by rememberUpdatedState(onDismissFinished)
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            hasOpened = true
        } else if (hasOpened) {
            snapshotFlow { state.isIdle }.first { it }
            hasOpened = false
            onFinished?.invoke()
        }
    }
    if (!active) return
    val direction = LocalLayoutDirection.current
    val source = materialAnchor?.surface
    val resolvedVisuals = materialAnchor?.popupVisuals ?: visuals
    GlassPopupSurface(
        onDismissRequest = onDismissRequest,
        backdrop = backdrop ?: source?.backdrop,
        modifier = modifier,
        sizing = sizing,
        visuals = resolvedVisuals,
        interactive = show,
        scrollable = true,
        contentPadding = contentPadding,
        panelLayer = {},
        overlay = {},
        frame = { end, page ->
            val settled = placeGlassSecondaryPopup(anchorBounds, end, sizing.safeMargin.toPx(), page, direction)
            GlassPopupFrame(
                secondaryPopupRect(
                    anchorBounds,
                    settled,
                    contentPadding.calculateTopPadding().toPx(),
                    contentPadding.calculateBottomPadding().toPx(),
                    popupFractionWithBack(progress.value, backProgress.value),
                ),
                cornerRadius,
            )
        },
        contentLayer = { _, _ -> },
        content = content,
    )
}

/** 起始边对齐，然后整体上移以放入页面内。 */
internal fun placeGlassSecondaryPopup(anchor: Rect, size: Size, margin: Float, page: Size, direction: LayoutDirection): Rect {
    val width = size.width.coerceAtMost((page.width - margin * 2f).coerceAtLeast(0f))
    val height = size.height.coerceAtMost((page.height - margin * 2f).coerceAtLeast(0f))
    val x = if (direction == LayoutDirection.Ltr) anchor.left else anchor.right - width
    val left = x.coerceIn(margin, (page.width - margin - width).coerceAtLeast(margin))
    val top = anchor.top.coerceIn(margin, (page.height - margin - height).coerceAtLeast(margin))
    return Rect(left, top, left + width, top + height)
}

/** 全尺寸内容随一个从带内边距的触发行生长出的圆角裁剪一起移动。 */
internal fun secondaryPopupRect(anchor: Rect, end: Rect, topPadding: Float, bottomPadding: Float, fraction: Float): Rect {
    val t = fraction.coerceIn(0f, 1f)
    return Rect(
        lerp(anchor.left, end.left, t),
        lerp(anchor.top - topPadding, end.top, t),
        lerp(anchor.right, end.right, t),
        lerp(anchor.bottom + bottomPadding, end.bottom, t),
    )
}
