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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop

/** [GlassDropdownPopup] 的默认值。 */
object GlassDropdownDefaults {

    /** 展开后面板的圆角半径。 */
    val CornerRadius: Dp = 24.dp

    /** 初始面板圆角半径。 */
    val StartCornerRadius: Dp = 4.dp
}

/**
 * 锚定到设置行的玻璃下拉菜单。
 *
 * 请保持其组合状态直到退出动画完成。
 *
 * @param show 面板是否打开。此项变为 false 时，行的输入与无障碍操作立即停止，
 *   退出动画则继续完成。
 * @param onDismissRequest 点击外部或按返回键请求关闭时调用。
 * @param anchorBounds 行的边界，使用窗口坐标（`boundsInWindow()`）。
 * @param backdrop 面板采样的背景。排除弹出层自身。`null` 时使用不透明填充，
 *   同时保留配置的泛光描边与阴影。
 * @param modifier 应用于面板的修饰符。
 * @param anchor 可选的行锚点。打开时隐藏所显示的值，并按 [glassPopupAnchorValue] 报告的
 *   边界水平对齐面板；不可用时使用 [anchorBounds]。
 * @param sizing 面板尺寸限制与窗口边距。
 * @param visuals 面板表面外观。
 * @param cornerRadius 展开后面板的圆角半径。
 * @param contentPadding 各行周围的内边距。
 * @param content 各行。带 `selected` 的 [GlassPopupItem] 会显示对勾。
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

/** 共享的下拉渲染器；surface 把窗口边界映射进宿主。 */
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
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Scaffold 宿主在请求打开之后才组合；从关闭状态开始以保留进入过渡。
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
                // Scaffold 移除此弹出层宿主后，锚点的淡出仍在继续。
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
        sizing = sizing,
        visuals = visuals,
        contentPadding = contentPadding,
        panelLayer = {
            alpha = if (show && geometryProgress() <= GlassMotion.ARC_VISIBLE_FRACTION) 0f else fadeProgress()
            val blur = (1f - sharpnessProgress()) * GlassMotion.ARC_EXIT_BLUR_DP * this.density
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        },
        overlay = {},
        frame = { end, windowBounds ->
            arcFrame(
                anchorBounds,
                anchor?.dropdownValueBounds,
                end,
                windowBounds,
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
        modifier = modifier,
        interactive = show,
        scrollable = true,
        content = content,
    )
}

/** 为下拉的曲线运动独立插值尺寸与中心。 */
private fun arcFrame(
    anchor: Rect,
    valueAnchor: Rect?,
    end: Size,
    window: Rect,
    margin: Float,
    sizeFraction: Float,
    positionFraction: Float,
    startRadius: Dp,
    endRadius: Dp,
    layoutDirection: LayoutDirection,
): GlassPopupFrame {
    val placement = placeGlassDropdownPopup(anchor, valueAnchor, end, margin, window, layoutDirection)
    val startWidth = end.width * GlassMotion.ARC_START_WIDTH
    val startHeight = startWidth * GlassMotion.ARC_START_RATIO
    val width = startWidth + (end.width - startWidth) * sizeFraction
    val endRatio = if (end.width > 0f) end.height / end.width else 1f
    val ratio = GlassMotion.ARC_START_RATIO + (endRatio - GlassMotion.ARC_START_RATIO) * sizeFraction
    val height = ratio * width

    val alignLeft = valueAnchor?.let { it.center.x <= window.center.x } ?: (layoutDirection == LayoutDirection.Rtl)
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
    window: Rect,
    direction: LayoutDirection = LayoutDirection.Ltr,
): GlassPopupPlacement {
    if (valueAnchor == null) return placeGlassPopup(row, size, margin, window, direction)
    // 对齐到值在窗口中最近的水平边。
    val wantedLeft = if (valueAnchor.center.x <= window.center.x) {
        valueAnchor.left
    } else {
        valueAnchor.right - size.width
    }
    val minLeft = window.left + margin
    val left = wantedLeft.coerceIn(minLeft, (window.right - size.width - margin).coerceAtLeast(minLeft))
    val verticalPlacement = placeGlassPopup(row, size, margin, window, LayoutDirection.Ltr)
    return GlassPopupPlacement(
        rect = Rect(left, verticalPlacement.rect.top, left + size.width, verticalPlacement.rect.bottom),
        alignTop = verticalPlacement.alignTop,
    )
}
