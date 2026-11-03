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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop

/** [GlassDropdownPopup] 的默认值 */
object GlassDropdownDefaults {
    /** 面板停靠时的圆角半径：`miuix_appcompat_drop_down_menu_radius`。 */
    val CornerRadius: Dp = 24.dp
    /** 面板展开起始的圆角半径。 */
    val StartCornerRadius: Dp = 4.dp
}

/**
 * 从设置行下方弹出的选项列表，悬浮在玻璃层之上。
 * 三个开口中的第三个，也是唯一一个不再保持边缘静止的开口：面板的尺寸及其中心由两个不同速度的弹簧驱动，因此它沿弧线移动，而不是从固定的角落生长出来。
 * @param show 面板是否处于打开状态。当其变为 false 时，行输入和无障碍操作会立即停止，同时退出动画继续完成。
 * @param onDismissRequest 当点击面板外部或按返回键请求关闭时调用。
 * @param anchorBounds 该行的边界，位于此组合函数自身的坐标空间中。
 * @param backdrop 玻璃层背后的 [Backdrop]，须排除弹出层自身。`null` 将使用不透明填充，同时保留已配置的辉光描边和 Compose 阴影。
 * @param modifier 应用于面板的修饰符。
 * @param anchor 该行的可选句柄。若提供了句柄，面板打开时会让该行显示的值淡出让位，并水平对齐到 [glassPopupAnchorValue] 报告的值边界；若没有报告的值，则使用 [anchorBounds] 进行定位。
 * @param sizing 面板允许的最大宽度和高度。
 * @param visuals 面板表面的材质构成。
 * @param cornerRadius 面板最终停留的圆角半径。
 * @param contentPadding 行周围的内边距。
 * @param content 各行。使用 `selected` 的 [GlassPopupItem] 会显示一个勾选标记。
 */
@Composable
fun BoxScope.GlassDropdownPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    anchor: GlassPopupAnchor? = null,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    cornerRadius: Dp = GlassDropdownDefaults.CornerRadius,
    contentPadding: PaddingValues = PaddingValues(vertical = GlassPopupDefaults.ContentPaddingVertical),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassDropdownPopupContent(
        show = show,
        onDismissRequest = onDismissRequest,
        anchorBounds = anchorBounds,
        backdrop = backdrop,
        modifier = modifier,
        anchor = anchor,
        sizing = sizing,
        visuals = visuals,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        content = content,
    )
}

/** 共享下拉渲染器；Scaffold 宿主以根坐标提供其原点。 */
@Composable
internal fun BoxScope.GlassDropdownPopupContent(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    anchor: GlassPopupAnchor? = null,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    cornerRadius: Dp = GlassDropdownDefaults.CornerRadius,
    contentPadding: PaddingValues = PaddingValues(vertical = GlassPopupDefaults.ContentPaddingVertical),
    coordinateOrigin: Offset = Offset.Zero,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Scaffold 宿主的内容是在行已请求打开之后才首次组合的。保留关闭的初始状态，以便首次组合仍具有进入过渡动画。
    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(show) {
        visibility.targetState = show
    }
    val transition = rememberTransition(visibility, label = "glassDropdown")
    val size = transition.animateFloat(
        transitionSpec = { GlassMotion.popupProgressSpring(GlassMotion.arcBounds(targetState)) },
        label = "glassDropdownSize",
    ) { if (it) 1f else 0f }
    val position by transition.animateFloat(
        transitionSpec = { GlassMotion.popupProgressSpring(GlassMotion.arcPosition(targetState)) },
        label = "glassDropdownPosition",
    ) { if (it) 1f else 0f }
    val fade by transition.animateFloat(
        transitionSpec = { GlassMotion.arcFade(targetState) },
        label = "glassDropdownFade",
    ) { if (it) 1f else 0f }
    val sharpness by transition.animateFloat(
        transitionSpec = { GlassMotion.arcBlur(targetState) },
        label = "glassDropdownBlur",
    ) { if (it) 1f else 0f }

    val active by remember(show, size) {
        derivedStateOf { show || size.value > 0.0001f }
    }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    LaunchedEffect(show, active) {
        if (!show && !active) currentOnDismissFinished?.invoke()
    }
    val layoutDirection = LocalLayoutDirection.current
    val backProgress = rememberGlassPopupBackProgress(
        show = show,
        active = active,
        enabled = show,
        retainWhenInactive = true,
        resetSpec = GlassMotion.popupProgressSpring(GlassMotion.arcBounds(true)),
        onDismissRequest = onDismissRequest,
    )
    DisposableEffect(anchor, backProgress) {
        anchor?.dropdownBackProgressState = backProgress
        anchor?.dropdownRetainedValueAlpha = 0f
        onDispose {
            if (anchor?.dropdownBackProgressState === backProgress) {
                // 锚点的淡出会在 Scaffold 移除本弹出宿主之后继续。
                anchor.dropdownRetainedValueAlpha = backProgress.value.coerceIn(0f, 1f)
                anchor.dropdownBackProgressState = null
            }
        }
    }
    fun geometryProgress() = popupFractionWithBack(size.value, backProgress.value)
    fun positionProgress() = popupFractionWithBack(position, backProgress.value)
    fun fadeProgress() = fade * (1f - backProgress.value.coerceIn(0f, 1f))
    fun sharpnessProgress() = sharpness * (1f - backProgress.value.coerceIn(0f, 1f))
    val standingDown = active
    DisposableEffect(anchor, standingDown) {
        anchor?.contentHidden = standingDown
        onDispose { anchor?.contentHidden = false }
    }
    if (!standingDown) return
    val startRadius = GlassDropdownDefaults.StartCornerRadius
    GlassPopupSurface(
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        sizing = sizing,
        visuals = visuals,
        interactive = show,
        contentPadding = contentPadding,
        panelLayer = {
            alpha = if (show && geometryProgress() <= GlassMotion.ARC_VISIBLE_FRACTION) 0f else fadeProgress()
            val blur = (1f - sharpnessProgress()) * GlassMotion.ARC_EXIT_BLUR_DP * this.density
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        },
        overlay = {},
        frame = { end, page ->
            arcFrame(
                anchorBounds.translate(-coordinateOrigin),
                anchor?.dropdownValueBounds?.translate(-coordinateOrigin),
                end,
                page,
                sizing.safeMargin.toPx(),
                geometryProgress(),
                positionProgress(),
                startRadius,
                cornerRadius,
                layoutDirection,
            )
        },
        contentLayer = { _, _ ->
            val fraction = GlassMotion.ARC_START_WIDTH + (1f - GlassMotion.ARC_START_WIDTH) * geometryProgress()
            val scale = fraction.coerceAtLeast(0f)
            scaleX = scale
            scaleY = scale
        },
        scrollable = true,
        content = content,
    )
}

/** 沿弧线部分的面板矩形。由两个分数驱动，而非一个：宽度和宽高比遵循 [sizeFraction]，而中心位置遵循更快的 [positionFraction]。 */
private fun arcFrame(
    anchor: Rect,
    valueAnchor: Rect?,
    end: Size,
    page: Size,
    margin: Float,
    sizeFraction: Float,
    positionFraction: Float,
    startRadius: Dp,
    endRadius: Dp,
    layoutDirection: LayoutDirection,
): GlassPopupFrame {
    val placement = placeGlassDropdownPopup(anchor, valueAnchor, end, margin, page, layoutDirection)
    val startWidth = end.width * GlassMotion.ARC_START_WIDTH
    val startHeight = startWidth * GlassMotion.ARC_START_RATIO
    val width = startWidth + (end.width - startWidth) * sizeFraction
    val endRatio = if (end.width > 0f) end.height / end.width else 1f
    val ratio = GlassMotion.ARC_START_RATIO + (endRatio - GlassMotion.ARC_START_RATIO) * sizeFraction
    val height = ratio * width
    val alignLeft = valueAnchor?.let { it.center.x <= page.width / 2f } ?: (layoutDirection == LayoutDirection.Rtl)
    val startCenterX = if (alignLeft) {
        placement.rect.left + startWidth / 2f
    } else {
        placement.rect.right - startWidth / 2f
    }
    val startCenterY = if (placement.alignTop) {
        placement.rect.top + startHeight / 2f
    } else {
        placement.rect.bottom - startHeight / 2f
    }
    val centerX = startCenterX + (placement.rect.center.x - startCenterX) * positionFraction
    val centerY = startCenterY + (placement.rect.center.y - startCenterY) * positionFraction
    val radius = startRadius + (endRadius - startRadius) * sizeFraction
    return GlassPopupFrame(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
        ),
        cornerRadius = radius,
    )
}

internal fun placeGlassDropdownPopup(
    row: Rect,
    valueAnchor: Rect?,
    size: Size,
    margin: Float,
    page: Size,
    direction: LayoutDirection = LayoutDirection.Ltr,
): GlassPopupPlacement {
    if (valueAnchor == null) return placeGlassPopup(row, size, margin, page, direction)
    // 选择器根据控件值位于页面哪一半，决定面板的左边缘还是右边缘与该值对齐。
    val wantedLeft = if (valueAnchor.center.x <= page.width / 2f) {
        valueAnchor.left
    } else {
        valueAnchor.right - size.width
    }
    val left = wantedLeft.coerceIn(margin, (page.width - size.width - margin).coerceAtLeast(margin))
    val verticalPlacement = placeGlassPopup(row, size, margin, page, LayoutDirection.Ltr)
    return GlassPopupPlacement(
        rect = Rect(left, verticalPlacement.rect.top, left + size.width, verticalPlacement.rect.bottom),
        alignTop = verticalPlacement.alignTop,
    )
}
