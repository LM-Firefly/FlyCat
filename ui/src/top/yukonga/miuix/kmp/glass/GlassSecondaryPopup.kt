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
 * 从主菜单行展开的玻璃子菜单，在 LTR 与 RTL 下均与其起始边对齐。
 *
 * 过渡期间各行保持尺寸不变，并在请求关闭后立即停止接受输入。预测性返回（Predictive Back）
 * 的预览会收起，取消时恢复，完成时关闭。
 *
 * @param show 二级菜单是否展开。收起过程中请保持此调用处于组合状态。
 * @param onDismissRequest 点击外部或按返回键时调用，以回到主菜单。
 * @param anchorBounds 静止时触发行的边界，使用窗口坐标（`boundsInWindow()`）。
 *   请在设置主 [GlassTransformPopup] 的 `stacked` 标志之前将其冻结。
 * @param backdrop 菜单采样的背景。包含主弹出层但不包含此子菜单。`null` 使用锚点的背景。
 * @param modifier 应用于面板的修饰符。
 * @param materialAnchor 主弹出层的按钮锚点。传入同一锚点可在两级之间共享其展开后的外观。
 * @param sizing 面板尺寸限制。将 `minWidth` 设为主面板的测量宽度。
 * @param visuals 表面外观，在没有主弹出层外观时使用。
 * @param cornerRadius 整个过渡期间使用的圆角裁剪半径。
 * @param contentPadding 菜单行周围的内边距，收起的行周围也包含在内。
 * @param onDismissFinished 收起动画完成时调用。
 * @param content 头部行，随后是二级选项。头部应当请求收起。
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
        sizing = sizing,
        visuals = resolvedVisuals,
        contentPadding = contentPadding,
        panelLayer = {},
        overlay = {},
        frame = { end, windowBounds ->
            val settled = placeGlassSecondaryPopup(anchorBounds, end, sizing.safeMargin.toPx(), windowBounds, direction)
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
        modifier = modifier,
        interactive = show,
        scrollable = true,
        content = content,
    )
}

/** 先按起始边对齐，再上移以适配窗口安全区。 */
internal fun placeGlassSecondaryPopup(anchor: Rect, size: Size, margin: Float, window: Rect, direction: LayoutDirection): Rect {
    val width = size.width.coerceAtMost((window.width - margin * 2f).coerceAtLeast(0f))
    val height = size.height.coerceAtMost((window.height - margin * 2f).coerceAtLeast(0f))
    val x = if (direction == LayoutDirection.Ltr) anchor.left else anchor.right - width
    val minLeft = window.left + margin
    val left = x.coerceIn(minLeft, (window.right - margin - width).coerceAtLeast(minLeft))
    val minTop = window.top + margin
    val top = anchor.top.coerceIn(minTop, (window.bottom - margin - height).coerceAtLeast(minTop))
    return Rect(left, top, left + width, top + height)
}

/** 全尺寸内容随圆角裁剪一起移动，该裁剪从带内边距的触发行向外长大。 */
internal fun secondaryPopupRect(anchor: Rect, end: Rect, topPadding: Float, bottomPadding: Float, fraction: Float): Rect {
    val t = fraction.coerceIn(0f, 1f)
    return Rect(
        lerp(anchor.left, end.left, t),
        lerp(anchor.top - topPadding, end.top, t),
        lerp(anchor.right, end.right, t),
        lerp(anchor.bottom + bottomPadding, end.bottom, t),
    )
}
