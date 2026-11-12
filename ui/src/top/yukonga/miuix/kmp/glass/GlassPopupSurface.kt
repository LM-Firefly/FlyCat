// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("ktlint:compose:multiple-emitters-check")

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.drawGlassShadow
import kotlin.math.roundToInt

/**
 * 玻璃弹出层的尺寸限制与窗口边距。
 *
 * @property minWidth 面板最小宽度。
 * @property maxWidth 面板最大宽度。
 * @property maxHeight 内容开始滚动前的面板最大高度。
 * @property safeMargin 距窗口安全区以及弹出层宿主边缘的最小边距。
 */
@Immutable
data class GlassPopupSizing(
    val minWidth: Dp = GlassPopupDefaults.MinWidth,
    val maxWidth: Dp = GlassPopupDefaults.MaxWidth,
    val maxHeight: Dp = GlassPopupDefaults.MaxHeight,
    val safeMargin: Dp = GlassPopupDefaults.SafeMargin,
)

/**
 * 玻璃弹出层的表面外观。
 *
 * @property style 提供模糊与边缘几何的玻璃样式；玻璃着色被禁用。
 * @property alpha 材质不透明度乘数。
 * @property stroke 可选泛光描边。
 * @property shadow 可选面板阴影。
 * @property material 面板材质。
 * @property containerColor 没有 backdrop 时使用的不透明填充；描边与阴影保留。
 */
@Immutable
data class GlassPopupVisuals(
    val style: GlassStyle,
    val alpha: Float,
    val stroke: EdgeStroke?,
    val shadow: GlassShadow?,
    val material: GlassMaterial?,
    val containerColor: Color,
)

/** 动画某一帧的面板边界与圆角半径。 */
@Immutable
data class GlassPopupFrame(
    val rect: Rect,
    val cornerRadius: Dp,
)

/** 弹出层最终边界与垂直锚点对齐方式。 */
@Immutable
class GlassPopupPlacement internal constructor(
    val rect: Rect,
    /** 面板是对齐到锚点顶部还是底部。 */
    val alignTop: Boolean,
)

/**
 * 将弹出层对齐到锚点的末端边，并约束到窗口安全边距之内。
 * 下方空间不足且上方空间更充裕时向上打开。
 * 所有边界、尺寸与边距均使用窗口坐标与像素单位。
 */
internal fun placeGlassPopup(
    anchor: Rect,
    size: Size,
    margin: Float,
    window: Rect,
    direction: LayoutDirection,
): GlassPopupPlacement {
    val endAlignedLeft = if (direction == LayoutDirection.Ltr) {
        anchor.right - size.width
    } else {
        anchor.left
    }
    val minLeft = window.left + margin
    val left = endAlignedLeft
        .coerceIn(minLeft, (window.right - size.width - margin).coerceAtLeast(minLeft))
    val below = window.bottom - margin - anchor.top
    val above = anchor.bottom - window.top - margin
    val alignTop = size.height <= below || below >= above
    val wanted = if (alignTop) anchor.top else anchor.bottom - size.height
    val minTop = window.top + margin
    val top = wanted.coerceIn(minTop, (window.bottom - size.height - margin).coerceAtLeast(minTop))
    return GlassPopupPlacement(Rect(left, top, left + size.width, top + size.height), alignTop)
}

/** 窗口安全区，使用窗口坐标，排除显示挖孔与系统栏。 */
@Composable
internal fun rememberGlassWindowBounds(): IntRect {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val displayCutout = WindowInsets.displayCutout
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val captionBar = WindowInsets.captionBar
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(layoutDirection, density, displayCutout, statusBars, navigationBars, captionBar, containerSize) {
        with(density) {
            IntRect(
                left = displayCutout.getLeft(this, layoutDirection),
                top = statusBars.getTop(this),
                right = containerSize.width - displayCutout.getRight(this, layoutDirection),
                bottom = containerSize.height - navigationBars.getBottom(this) - captionBar.getBottom(this),
            )
        }
    }
}

/**
 * 共享的玻璃弹出层 surface，支持点击外部关闭，过渡期间内容保持固定尺寸。
 *
 * @param onDismissRequest 点击外部应当关闭菜单时调用。
 * @param backdrop 玻璃背后的 [Backdrop]。`null` 时使用 [GlassPopupVisuals.containerColor]，
 *   同时保留配置的泛光描边与阴影。
 * @param modifier 应用于面板的修饰符。
 * @param sizing 面板尺寸限制与窗口边距。
 * @param visuals 面板表面外观。
 * @param surface 可选的背景层，绘制在面板轮廓内各行的后方。
 * @param contentPadding 各行周围的内边距。
 * @param panelLayer 应用于整个面板的图层属性。
 * @param overlay 绘制在面板及其各行之上、轮廓之内。
 * @param frame 给定面板最终尺寸与窗口安全区，给出当前面板边界与圆角半径。
 *   边界使用窗口坐标；surface 将其映射到自身宿主。
 * @param contentLayer 行图层属性，给定面板最终尺寸与当前窗口边界。
 * @param reveal 可选的打开裁剪，例如 [popupClipReveal]。
 * @param onMeasured 每次测量时以面板最终尺寸回调。
 * @param interactive 面板各行是否可接收输入与无障碍操作。
 * @param scrollable 各行是否在受安全区约束的视口内滚动。
 * @param content 各行。
 */
// 全页关闭层与定位后的面板必须是弹出层宿主中的兄弟节点。
@Composable
internal fun GlassPopupSurface(
    onDismissRequest: () -> Unit,
    backdrop: Backdrop?,
    sizing: GlassPopupSizing,
    visuals: GlassPopupVisuals,
    contentPadding: PaddingValues,
    panelLayer: GraphicsLayerScope.() -> Unit,
    overlay: DrawScope.() -> Unit,
    frame: Density.(end: Size, windowBounds: Rect) -> GlassPopupFrame,
    contentLayer: GraphicsLayerScope.(end: Size, rect: Rect) -> Unit,
    modifier: Modifier = Modifier,
    reveal: Modifier = Modifier,
    surface: (@Composable BoxScope.(GlassShape) -> Unit)? = null,
    onMeasured: ((Size) -> Unit)? = null,
    interactive: Boolean = true,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val windowRect = rememberGlassWindowBounds().run {
        Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
    // 窗口坐标下的定位必须换算到此宿主的空间，与 ListPopupLayout 相同。
    val hostOrigin = remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // 系统取消是一个被消费的抬起事件，而不是一次外部释放。
                        val released = change.changedToUp()
                        change.consume()
                        if (!change.pressed) {
                            if (released) currentOnDismissRequest()
                            break
                        }
                    }
                }
            },
    )

    val density = LocalDensity.current
    val shadowOutsets = remember(visuals.shadow, density) {
        with(density) { popupShadowOutsets(visuals.shadow) }
    }
    val measured = remember { MeasuredPanel() }
    val shape = remember(measured) {
        val corner = MeasuredCorner(measured)
        GlassShape(corner, corner, corner, corner)
    }

    Layout(
        // 放在定位修饰符之外，使其汇报宿主原点而非面板偏移。
        modifier = modifier
            .onGloballyPositioned { hostOrigin.value = it.positionInWindow() }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (measured.rect.left - hostOrigin.value.x).roundToInt(),
                        (measured.rect.top - hostOrigin.value.y).roundToInt(),
                    )
                }
            }
            .graphicsLayer {
                panelLayer()
                // 把阴影像素计入 alpha 低于 1 时使用的离屏缓冲。
                outsets = shadowOutsets
            }
            .then(reveal)
            .glassShadow(shape, visuals.shadow, visuals.alpha)
            .clip(shape)
            .drawWithContent {
                drawContent()
                overlay()
            }
            .then(
                if (interactive) {
                    Modifier
                } else {
                    Modifier
                        .clearAndSetSemantics { }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                },
            ),
        content = {
            Box {
                if (surface != null) {
                    surface(shape)
                } else {
                    GlassPopupBackground(backdrop, shape, visuals)
                }
            }
            GlassPopupColumn(
                modifier = Modifier
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(contentPadding),
                content = content,
            )
        },
    ) { measurables, constraints ->
        val safeInset = if (scrollable) sizing.safeMargin.roundToPx() * 2 else 0
        val widest = sizing.maxWidth.roundToPx().coerceAtMost((constraints.maxWidth - safeInset).coerceAtLeast(0))
        val narrowest = sizing.minWidth.roundToPx().coerceAtMost(widest)
        val tallest = sizing.maxHeight.roundToPx().coerceAtMost((constraints.maxHeight - safeInset).coerceAtLeast(0))
        val width = measurables[1].maxIntrinsicWidth(tallest).coerceIn(narrowest, widest)
        val viewportHeight = if (scrollable) {
            measurables[1].minIntrinsicHeight(width).coerceAtMost(tallest)
        } else {
            tallest
        }
        val foreground = measurables[1].measure(
            Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = viewportHeight),
        )
        measured.end = Size(foreground.width.toFloat(), foreground.height.toFloat())
        onMeasured?.invoke(measured.end)
        // 同时保持在页内宿主之内；宽布局会裁剪它周围的窗格。
        val origin = hostOrigin.value
        val hostRight = origin.x + constraints.maxWidth
        val hostBottom = origin.y + constraints.maxHeight
        val placementBounds = Rect(
            left = maxOf(windowRect.left, origin.x),
            top = maxOf(windowRect.top, origin.y),
            right = minOf(windowRect.right, hostRight).coerceAtLeast(windowRect.left),
            bottom = minOf(windowRect.bottom, hostBottom).coerceAtLeast(windowRect.top),
        )
        val current = frame(measured.end, placementBounds)
        measured.rect = current.rect
        measured.cornerRadius = current.cornerRadius
        val panelWidth = current.rect.width.roundToInt().coerceAtLeast(0)
        val panelHeight = current.rect.height.roundToInt().coerceAtLeast(0)
        val localLeft = current.rect.left - origin.x
        val localTop = current.rect.top - origin.y
        // 材质与边缘光跟随动画轮廓；各行保持其静止尺寸。
        val background = measurables[0].measure(Constraints.fixed(panelWidth, panelHeight))
        layout(panelWidth, panelHeight) {
            background.place(0, 0)
            foreground.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                contentLayer(measured.end, measured.rect)
                // 使用本帧的边界（含滚动视口），且运动不作取整。
                translationX = localLeft - localLeft.roundToInt()
                translationY = localTop - localTop.roundToInt()
            }
        }
    }
}

/** 跟随面板、但不对其前景内容做变换的背景层。 */
@Composable
internal fun BoxScope.GlassPopupBackground(
    backdrop: Backdrop?,
    shape: GlassShape,
    visuals: GlassPopupVisuals,
    modifier: Modifier = Modifier,
    underlayMaterial: GlassMaterial? = null,
) {
    Box(
        modifier = Modifier.matchParentSize().then(modifier).then(
            if (backdrop == null) {
                Modifier.background(visuals.containerColor).drawWithContent {
                    drawContent()
                    visuals.stroke?.let { drawEdgeStroke(shape, it, visuals.alpha) }
                }
            } else if (underlayMaterial != null) {
                Modifier.glassOnActionBar(
                    backdrop = backdrop,
                    shape = shape,
                    style = visuals.style,
                    alpha = visuals.alpha,
                    material = visuals.material,
                    underlayMaterial = underlayMaterial,
                    stroke = visuals.stroke,
                )
            } else {
                Modifier.glass(
                    backdrop = backdrop,
                    shape = shape,
                    style = visuals.style,
                    alpha = visuals.alpha,
                    material = visuals.material,
                    stroke = visuals.stroke,
                    shading = false,
                )
            },
        ),
    )
}

/** 与 drawGlassShadow 的边界一致，包括其密度换算与偏移。 */
internal fun Density.popupShadowOutsets(shadow: GlassShadow?): LayerOutsets {
    if (shadow == null) return LayerOutsets.Zero
    val sourceScale = density / GlassDefaults.SourceDensity
    val reach = (shadow.radius * sourceScale).coerceAtLeast(1f)
    val x = shadow.offsetX * sourceScale
    val y = shadow.offsetY * sourceScale
    return LayerOutsets(
        left = (reach - x).coerceAtLeast(0f).toDp(),
        top = (reach - y).coerceAtLeast(0f).toDp(),
        right = (reach + x).coerceAtLeast(0f).toDp(),
        bottom = (reach + y).coerceAtLeast(0f).toDp(),
    )
}

/**
 * 测量通道内共享的几何数据。它不是快照状态：测量期间的写入不得使该通道失效，
 * 也不得把几何更新推迟到下一帧。
 */
private class MeasuredPanel {
    var end: Size = Size.Zero
    var rect: Rect = Rect.Zero
    var cornerRadius: Dp = 0.dp
}

/** 在绘制时解析动画半径，避免一帧延迟。 */
private class MeasuredCorner(private val measured: MeasuredPanel) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = with(density) { measured.cornerRadius.toPx() }

    override fun toString(): String = "MeasuredCorner"
}
