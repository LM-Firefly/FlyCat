// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.internal.drawGlassStroke
import kotlin.math.roundToInt

/**
 * 菜单面板允许的宽高范围，以及它与页面边缘保持的间距。
 *
 * @property minWidth 面板允许的最小宽度。
 * @property maxWidth 面板允许的最大宽度。
 * @property maxHeight 面板允许的最大高度，内部列表在此之前开始滚动。
 * @property safeMargin 面板与页面边缘保持的间距。
 */
@Immutable
data class GlassPopupSizing(
    val minWidth: Dp = GlassPopupDefaults.MinWidth,
    val maxWidth: Dp = GlassPopupDefaults.MaxWidth,
    val maxHeight: Dp = GlassPopupDefaults.MaxHeight,
    val safeMargin: Dp = GlassPopupDefaults.SafeMargin,
)

/**
 * 菜单表面的材质构成。
 *
 * @property style 玻璃样式。只取用其中的模糊与边缘参数——菜单是材质而非玻璃，因此不启用着色。
 * @property alpha 材质的不透明度乘数。
 * @property stroke 沿边缘的可选泛光描边。
 * @property shadow 面板投射的阴影。`null` 表示移除阴影。
 * @property material 面板自身的主体材质。
 * @property containerColor 没有背景采样时使用的不透明填充色；描边与阴影仍然保留。
 */
@Immutable
data class GlassPopupVisuals(
    val style: GlassStyle,
    val alpha: Float,
    val stroke: GlassStroke?,
    val shadow: GlassShadow?,
    val material: GlassMaterial?,
    val containerColor: Color,
)

/** 菜单面板在某一时刻占据的矩形，以及此刻所用的圆角半径。 */
@Immutable
data class GlassPopupFrame(
    val rect: Rect,
    val cornerRadius: Dp,
)

/** 菜单最终停靠的位置，以及它在垂直方向上与锚点的对齐方式。 */
@Immutable
class GlassPopupPlacement internal constructor(
    val rect: Rect,
    /** 面板是否停在锚点下方。`false` 表示下方空间不够，只能向上展开。 */
    val alignTop: Boolean,
)

/**
 * 计算菜单的归属位置：让它的两条边贴住锚点，并夹在页面范围内。所有边界、尺寸与边距均使用同一坐标空间与像素单位。
 *
 * 普通菜单把末端边缘对齐到锚点的末端边缘。顶边对齐锚点顶边，除非下方放不下而上方空间更多。
 * 次级菜单改用 [placeGlassSecondaryPopup]：起始边对齐并整体上移，而不翻转展开方向。
 *
 * @param anchor 菜单所属的控件或行。
 * @param size 面板最终停靠的尺寸。
 * @param margin 面板与页面边缘保持的间距。
 * @param page 面板所在的页面。
 * @param direction 用于解析锚点末端边缘的当前布局方向。
 */
internal fun placeGlassPopup(
    anchor: Rect,
    size: Size,
    margin: Float,
    page: Size,
    direction: LayoutDirection,
): GlassPopupPlacement {
    val endAlignedLeft = if (direction == LayoutDirection.Ltr) {
        anchor.right - size.width
    } else {
        anchor.left
    }
    val left = endAlignedLeft
        .coerceIn(margin, (page.width - size.width - margin).coerceAtLeast(margin))
    val below = page.height - margin - anchor.top
    val above = anchor.bottom - margin
    val alignTop = size.height <= below || below >= above
    val wanted = if (alignTop) anchor.top else anchor.bottom - size.height
    val top = wanted.coerceIn(margin, (page.height - size.height - margin).coerceAtLeast(margin))
    return GlassPopupPlacement(Rect(left, top, left + size.width, top + size.height), alignTop)
}

/**
 * 所有玻璃菜单共用的面板。
 *
 * 它承载三种开场动画共享的部分：点击外部关闭的捕获层、定位、阴影、材质，以及把各行冻结在面板最终停靠尺寸上的测量。每种开场动画只提供自己的几何——[frame] 与 [contentLayer]。
 *
 * @param onDismissRequest 点击面板外部应关闭菜单时调用。
 * @param backdrop 玻璃背后的 [Backdrop]。`null` 时使用 [GlassPopupVisuals.containerColor]，同时保留已配置的泛光描边和阴影。
 * @param modifier 应用于面板的修饰符。
 * @param sizing 面板的尺寸限制与页面边距。
 * @param visuals 面板表面的材质构成。
 * @param surface 可选的背景图层，绘制在面板轮廓内、各行之后。
 * @param contentPadding 各行周围的内边距。
 * @param panelLayer 作用于面板整体：不透明度、模糊、缩放。
 * @param overlay 绘制在面板及其各行之上、轮廓之内。
 * @param frame 给定面板最终停靠的尺寸和它所在的页面，给出面板此刻的矩形与圆角半径。
 * @param contentLayer 作用于各行，给定面板最终停靠的尺寸及其当前位置。
 * @param reveal 开场时裁剪面板。开场动画采用库自带几何的菜单在此传入开场裁剪；自带几何的菜单则留空。
 * @param onMeasured 每次测量时以面板最终停靠的尺寸回调。
 * @param interactive 面板的各行是否接收输入与无障碍操作。
 * @param scrollable 各行是否在受安全区约束的次级视口内滚动。
 * @param content 各行。
 */
@Composable
internal fun BoxScope.GlassPopupSurface(
    onDismissRequest: () -> Unit,
    backdrop: Backdrop?,
    sizing: GlassPopupSizing,
    visuals: GlassPopupVisuals,
    contentPadding: PaddingValues,
    panelLayer: GraphicsLayerScope.() -> Unit,
    overlay: DrawScope.() -> Unit,
    frame: Density.(end: Size, page: Size) -> GlassPopupFrame,
    contentLayer: GraphicsLayerScope.(end: Size, rect: Rect) -> Unit,
    modifier: Modifier = Modifier,
    reveal: Modifier = Modifier,
    surface: (@Composable BoxScope.(GlassShape) -> Unit)? = null,
    onMeasured: ((Size) -> Unit)? = null,
    interactive: Boolean = true,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) { onDismissRequest() },
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
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(measured.rect.left.roundToInt(), measured.rect.top.roundToInt())
                }
            }
            .graphicsLayer {
                panelLayer()
                // alpha 小于 1 会创建离屏缓冲。把着色器阴影画在缓冲内的外部像素区，
                // 而不是把它们裁剪到面板矩形里。
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
        val page = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val current = frame(measured.end, page)
        measured.rect = current.rect
        measured.cornerRadius = current.cornerRadius
        val panelWidth = current.rect.width.roundToInt().coerceAtLeast(0)
        val panelHeight = current.rect.height.roundToInt().coerceAtLeast(0)
        // 材质与边缘跟随动画中的轮廓；各行保持其停靠尺寸。
        val background = measurables[0].measure(Constraints.fixed(panelWidth, panelHeight))
        layout(panelWidth, panelHeight) {
            background.place(0, 0)
            foreground.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                contentLayer(measured.end, measured.rect)
                // 用这一帧的边界（含滚动视口），但不对其运动取整。
                translationX = measured.rect.left - measured.rect.left.roundToInt()
                translationY = measured.rect.top - measured.rect.top.roundToInt()
            }
        }
    }
}

/** 跟随面板的背景图层，且不对其前景内容做任何变换。 */
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
                    visuals.stroke?.let { drawGlassStroke(shape, layoutDirection, it, visuals.alpha) }
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

/** 与 drawGlassShadow 的边界保持一致，包括其密度换算与偏移。 */
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
 * 面板的测量尺寸与当前矩形。
 *
 * 在测量阶段写入，并在同一遍布局中被包在外层的布局读回。刻意不使用快照状态：
 * 在测量中途写入的状态要么滞后一帧，要么会使写入它的那遍布局失效。
 */
private class MeasuredPanel {
    var end: Size = Size.Zero
    var rect: Rect = Rect.Zero
    var cornerRadius: Dp = 0.dp
}

/**
 * 面板的圆角，在绘制的那一刻才解析。
 *
 * 半径随面板一起变化，在组合期构建的形状会滞后一帧。
 */
private class MeasuredCorner(private val measured: MeasuredPanel) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = with(density) { measured.cornerRadius.toPx() }

    override fun toString(): String = "MeasuredCorner"
}
