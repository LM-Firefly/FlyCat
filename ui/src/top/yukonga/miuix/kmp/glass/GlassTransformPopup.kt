// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassStyle
import kotlin.math.roundToInt

/**
 * [GlassTransformPopup] 或 [GlassDropdownPopup] 的锚点几何与外观。
 *
 * 用 [rememberGlassPopupAnchor] 创建，把 [glassPopupAnchor] 附加到控件上，并可选用
 * [glassPopupAnchorContent] 标明过渡期间被复制的内容。
 */
@Stable
class GlassPopupAnchor {

    internal var secondaryBackProgressState: State<Float>? by mutableStateOf(null)

    /** 由已挂接的 [GlassDropdownPopup] 发布的预测性返回进度。 */
    internal var dropdownBackProgressState: State<Float>? by mutableStateOf(null)

    /** 让恢复后的值在下拉的弹出宿主被销毁之后的淡出期间保持可见。 */
    internal var dropdownRetainedValueAlpha: Float by mutableFloatStateOf(0f)

    /**
     * 次级菜单的预测性返回进度，包含取消时的恢复与退出时保留的比例。
     * 在绘制/图层回调中读取它，可让自定义箭头跟随菜单旋转：
     * `arrowRotation = { rotation * (1f - anchor.secondaryBackProgress) }`。
     */
    val secondaryBackProgress: Float get() = secondaryBackProgressState?.value ?: 0f

    /** 由已挂接的玻璃按钮提供的材质，含其解析出的背景采样。 */
    internal var surface: GlassAnchorSurface? by mutableStateOf(null)

    /** 展开态菜单的外观，与次级弹出菜单共享。 */
    internal var popupVisuals: GlassPopupVisuals? by mutableStateOf(null)

    /** 按钮表面的进度，独立于阴影的不透明度。 */
    internal var surfaceProgress: State<Float>? by mutableStateOf(null)
    internal var surfaceMaterialAlpha: Float by mutableFloatStateOf(1f)
    internal val surfaceVisibility: Float get() = surfaceProgress?.value ?: 1f
    internal var surfaceFloating: Boolean by mutableStateOf(false)

    /** 控件的外边界，位于窗口坐标系中。 */
    internal var containerBounds: Rect by mutableStateOf(Rect.Zero)

    /** 控件内容的边界，位于窗口坐标系中。 */
    internal var contentBounds: Rect by mutableStateOf(Rect.Zero)

    /** 下拉框值文本与箭头图标的边界，位于窗口坐标系中。 */
    internal var dropdownValueBounds: Rect? by mutableStateOf(null)

    /** 收起端点处使用的控件圆角半径。 */
    internal var cornerRadius: Dp by mutableStateOf(0.dp)

    /** 面板过渡期间是否保持不透明，而不是随图标一起淡出。 */
    internal var floating: Boolean by mutableStateOf(false)

    /** 弹出层打开期间隐藏整个变换锚点，或只隐藏下拉值。 */
    internal var contentHidden: Boolean by mutableStateOf(false)

    /** 下拉值的不透明度。 */
    internal var valueAlpha: Float by mutableFloatStateOf(1f)

    /** 预测性返回收起期间的值不透明度。 */
    internal val dropdownValueAlpha: Float
        get() = (dropdownBackProgressState?.value ?: dropdownRetainedValueAlpha).coerceIn(0f, 1f)

    /**
     * FORK（FlyCat）：抑制变换收起端点复用按钮表面。
     * 当按钮采样的是受限的兄弟纯色背景（而非全页背景，如嵌在录制子树内、为规避自采样崩溃而改用按钮大小的纯色层的列表头按钮）时，
     * 复用该表面会让 [GlassTransformPopup] 把这块小背景铺满生长中的面板，在按钮原位留下一块不透明方块。
     * 置位后 [GlassButtonSurface] 不再发布表面，面板改以纯玻璃形态生长。
     */
    internal var suppressTransformSurface: Boolean = false
}

/** 按钮在其菜单收起端点上的背景采样与色彩处理。 */
internal data class GlassAnchorSurface(
    val backdrop: Backdrop?,
    val style: GlassStyle,
    val material: GlassMaterial,
    val underlayMaterial: GlassMaterial?,
    val stroke: EdgeStroke?,
    val fill: Color,
)

/** 标记按钮的修饰符，使其渲染器能把真实的表面发布到此锚点。 */
internal data class GlassPopupAnchorElement(val anchor: GlassPopupAnchor) : Modifier.Element

/**
 * 记住一个 [GlassPopupAnchor] 及其值的淡入淡出——后者在弹出层退出后仍须继续。
 *
 * @param suppressTransformSurface FORK（FlyCat）：当锚点按钮采样的是受限的兄弟纯色背景时置 `true`，
 * 避免展开面板里出现不透明方块（见 [GlassPopupAnchor.suppressTransformSurface]）。
 */
@Composable
fun rememberGlassPopupAnchor(suppressTransformSurface: Boolean = false): GlassPopupAnchor {
    val anchor = remember { GlassPopupAnchor() }
    // FORK（FlyCat）：把抑制标记写入锚点，供按钮渲染器决定是否发布表面（详见 KDoc）。
    anchor.suppressTransformSurface = suppressTransformSurface
    val alpha = animateFloatAsState(
        targetValue = if (anchor.contentHidden) 0f else 1f,
        animationSpec = GlassMotion.default(),
        label = "glassPopupAnchorValue",
    ).value
    SideEffect { anchor.valueAlpha = alpha }
    return anchor
}

/**
 * 把控件的边界上报给 [anchor]，并在其菜单打开时隐藏该控件。
 *
 * 把它加在控件最外层节点上，而不是其图标上。用在 [GlassIconButton] 上时，它还会为弹出层的
 * 收起端点提供按钮的背景采样与表面外观。
 *
 * @param anchor 弹出层锚点。
 * @param cornerRadius 控件圆角半径；胶囊形状为高度的一半。
 * @param floating 控件是否悬浮于页面之上。
 */
@Stable
fun Modifier.glassPopupAnchor(
    anchor: GlassPopupAnchor,
    cornerRadius: Dp,
    floating: Boolean = false,
): Modifier = this
    .then(GlassPopupAnchorElement(anchor))
    .onGloballyPositioned {
        anchor.containerBounds = it.boundsInWindow()
        anchor.cornerRadius = cornerRadius
        anchor.floating = floating
    }
    .graphicsLayer { alpha = if (anchor.contentHidden) 0f else 1f }

/**
 * 标明弹出过渡期间被复制的内容。
 *
 * 当弹出层接管了控件的背景时，把它加在图标上，以免背景被复制两份。
 * 不加此修饰符时，复制整个控件。
 *
 * @param anchor 弹出层锚点。
 */
@Stable
fun Modifier.glassPopupAnchorContent(anchor: GlassPopupAnchor): Modifier = this.onGloballyPositioned { anchor.contentBounds = it.boundsInWindow() }

/**
 * 为 [GlassDropdownPopup] 上报行的边界，但不隐藏该行。
 *
 * @param anchor 要上报到的锚点。
 */
@Stable
fun Modifier.glassPopupAnchorRow(anchor: GlassPopupAnchor): Modifier = this.onGloballyPositioned { anchor.containerBounds = it.boundsInWindow() }

/**
 * 上报行内值的边界供下拉定位，并在选项打开期间让该值淡出。
 *
 * 把它加在值文本和箭头图标上，而不是整行上。预测性返回完成后，值会保持恢复后的不透明度
 * 直到弹出层销毁，随后淡入至完全不透明。
 *
 * @param anchor 该行上报自身时所用的锚点。
 */
@Stable
fun Modifier.glassPopupAnchorValue(anchor: GlassPopupAnchor): Modifier = this
    .onGloballyPositioned { anchor.dropdownValueBounds = it.boundsInWindow() }
    .graphicsLayer {
        alpha = maxOf(anchor.valueAlpha, anchor.dropdownValueAlpha)
    }

/**
 * 从锚定控件展开、内容交叉淡入淡出的玻璃菜单。
 *
 * 与锚点放在同一个根 [BoxScope] 中，并在退出完成前保持组合。
 * 打开期间各行可接收输入，除非 [stacked]。预测性返回会预览收起回锚点，取消时恢复；
 * 叠放的次级菜单则代为处理返回手势。
 *
 * @param show 菜单是否打开。
 * @param onDismissRequest 点击外部或返回请求关闭时调用。
 * @param anchor 用作收起端点的控件；[glassPopupAnchor] 以窗口坐标上报其边界。
 * @param backdrop 展开菜单采样的场景，包含栏但不含弹出层。`null` 时回退到按钮的背景采样。收起端点使用按钮的表面。
 * @param anchorContent 控件的副本，或由 [glassPopupAnchorContent] 标明的那部分。
 * @param modifier 应用于面板的修饰符。
 * @param simplified 是否在开场过渡中省略锚点内容。回程过渡中它仍会出现。
 * @param stacked 是否有次级菜单打开。本面板会缩小并蒙上 [maskColor]。
 * @param maskColor [stacked] 期间的罩色。
 * @param sizing 面板的尺寸限制与窗口边距。
 * @param visuals 展开面板的外观，由 [GlassIconButton] 锚点的表面混合而成。
 * @param anchorAlpha 没有共享按钮表面的锚点所用的背景不透明度。
 * @param cornerRadius 面板展开后的圆角半径。
 * @param gap 控件与面板之间的间隙。
 * @param alignToPageEnd FORK（FlyCat）：停靠后的面板是否把末端边缘对齐到页面安全边距，而不是锚点的末端边缘。
 * 用于锚点之后还有其他控件的溢出菜单，使菜单仍贴着页面边缘展开。
 * @param contentPadding 各项周围的内边距。
 * @param onMeasured 以面板最终尺寸回调。用其宽度作为次级菜单的最小宽度。
 * @param onDismissFinished FORK（FlyCat）：弹出层完全退场（收起动画结束）后调用。
 * @param content 各项。
 */
@Composable
fun BoxScope.GlassTransformPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassPopupAnchor,
    backdrop: Backdrop?,
    anchorContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    simplified: Boolean = false,
    stacked: Boolean = false,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    anchorAlpha: Float = 1f,
    maskColor: Color = GlassPopupDefaults.maskColor(),
    cornerRadius: Dp = GlassPopupDefaults.CornerRadius,
    gap: Dp = 0.dp,
    // FORK（FlyCat）：停靠对齐（详见 KDoc）。
    alignToPageEnd: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(vertical = GlassPopupDefaults.ContentPaddingVertical),
    onMeasured: ((Size) -> Unit)? = null,
    // FORK（FlyCat）：弹出层完全退场后回调（详见 KDoc）。
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(show, label = "glassTransformPopup")
    val bounds = transition.animateFloat(
        transitionSpec = { GlassMotion.transformBounds(targetState) },
        label = "glassTransformBounds",
    ) { if (it) 1f else 0f }
    val center = transition.animateFloat(
        transitionSpec = { GlassMotion.transformCenter(targetState) },
        label = "glassTransformCenter",
    ) { if (it) 1f else 0f }
    val iconMaterial = transition.animateFloat(
        transitionSpec = { GlassMotion.transformIconMaterial(targetState) },
        label = "glassTransformIcon",
    ) { if (it) 1f else 0f }
    val contentMaterial = transition.animateFloat(
        transitionSpec = { GlassMotion.transformContentMaterial(targetState) },
        label = "glassTransformContent",
    ) { if (it) 1f else 0f }

    val pushedBack = animateFloatAsState(
        targetValue = if (stacked) 1f else 0f,
        animationSpec = GlassMotion.secondaryPopup(stacked),
        label = "glassTransformStacked",
    )
    val maskAlpha = animateFloatAsState(
        targetValue = if (stacked) 1f else 0f,
        animationSpec = GlassMotion.secondaryPopupMask(stacked),
        label = "glassTransformMask",
    )
    val active = show || transition.currentState || transition.isRunning
    // FORK（FlyCat）：弹出层完全退场（收起动画结束）后回调。
    androidx.compose.runtime.LaunchedEffect(active) {
        if (!active) onDismissFinished?.invoke()
    }
    val backProgress = rememberGlassPopupBackProgress(
        show = show,
        active = active,
        enabled = show && !stacked,
        retainWhenInactive = false,
        resetSpec = GlassMotion.transformBounds(true),
        onDismissRequest = onDismissRequest,
    )
    SideEffect { anchor.popupVisuals = visuals }
    DisposableEffect(anchor) {
        onDispose { anchor.popupVisuals = null }
    }
    DisposableEffect(active, anchor) {
        anchor.contentHidden = active
        onDispose { anchor.contentHidden = false }
    }
    if (!active) return

    val anchorSurface = anchor.surface
    val anchorVisuals = anchorSurface?.let {
        visuals.copy(
            style = it.style,
            material = it.material,
            stroke = it.stroke,
            containerColor = it.fill.copy(alpha = it.fill.alpha * anchor.surfaceMaterialAlpha),
            alpha = anchor.surfaceMaterialAlpha,
        )
    }
    val menuBackdrop = backdrop ?: anchorSurface?.backdrop
    val materialProgress = remember(bounds, backProgress) {
        derivedStateOf { popupFractionWithBack(bounds.value, backProgress.value).coerceIn(0f, 1f) }
    }
    val drawAnchorSurface by remember(anchorSurface, materialProgress) {
        derivedStateOf { anchorSurface != null && materialProgress.value < 1f }
    }
    val drawMenuSurface by remember(anchorSurface, materialProgress) {
        derivedStateOf { anchorSurface == null || materialProgress.value > 0f }
    }

    val startRect = anchor.containerBounds
    val layoutDirection = LocalLayoutDirection.current
    val iconRect = anchor.contentBounds.takeUnless { it.isEmpty } ?: startRect
    val startRadius = anchor.cornerRadius
    fun geometryProgress() = popupFractionWithBack(bounds.value, backProgress.value)
    fun centerProgress() = popupFractionWithBack(center.value, backProgress.value)
    fun iconProgress() = popupFractionWithBack(iconMaterial.value, backProgress.value)
    fun stackedProgress() = popupFractionWithBack(pushedBack.value, anchor.secondaryBackProgress)
    fun panelAlpha() = transformPanelAlpha(
        floating = if (anchorSurface != null) anchor.surfaceFloating || anchor.surfaceVisibility > 0f else anchor.floating,
        anchorAlpha = if (anchorSurface != null) {
            anchor.surfaceVisibility
        } else {
            anchorAlpha
        },
        geometryProgress = geometryProgress(),
        iconMaterial = iconProgress(),
    )
    val travel = remember { TransformTravel() }
    val hostOrigin = remember { mutableStateOf(Offset.Zero) }

    GlassPopupSurface(
        onDismissRequest = onDismissRequest,
        backdrop = menuBackdrop,
        sizing = sizing,
        visuals = visuals,
        contentPadding = contentPadding,
        panelLayer = {
            alpha = panelAlpha()
            transformOrigin = TransformOrigin(
                if (startRect.center.x < travel.endCenterX) 0f else 1f,
                if (startRect.center.y < travel.endCenterY) 0f else 1f,
            )
            val s = 1f - 0.05f * stackedProgress()
            scaleX = s
            scaleY = s
        },
        overlay = {
            val fraction = popupFractionWithBack(maskAlpha.value, anchor.secondaryBackProgress).coerceIn(0f, 1f)
            if (fraction > GlassMotion.POPUP_MASK_MIN_VISIBLE_CHANGE) {
                drawRect(maskColor.copy(alpha = maskColor.alpha * fraction))
            }
        },
        frame = { end, windowBounds ->
            val marginPx = sizing.safeMargin.toPx()
            // FORK（FlyCat）：停靠对齐（alignToPageEnd）——把面板末端边缘对齐到页面安全边距（windowBounds.width - margin）
            // 而不是锚点的末端边缘；用于锚点之后还有其他控件的溢出菜单，使菜单仍贴着页面边缘展开。
            val placementAnchor = if (alignToPageEnd) {
                if (layoutDirection == LayoutDirection.Ltr) {
                    startRect.copy(right = maxOf(startRect.right, windowBounds.width - marginPx))
                } else {
                    startRect.copy(left = minOf(startRect.left, marginPx))
                }
            } else {
                startRect
            }
            val frame = transformFrame(
                anchor = startRect,
                placementAnchor = placementAnchor,
                end = end,
                window = windowBounds,
                margin = marginPx,
                gap = gap.toPx(),
                sizeFraction = geometryProgress(),
                positionFraction = centerProgress(),
                startRadius = startRadius,
                endRadius = cornerRadius,
                layoutDirection = layoutDirection,
            )
            val settled = placeGlassPopup(
                placementAnchor.translate(0f, gap.toPx()),
                end,
                marginPx,
                windowBounds,
                layoutDirection,
            )
            travel.endCenterX = settled.rect.center.x
            travel.endCenterY = settled.rect.center.y
            travel.endWidth = end.width
            frame
        },
        contentLayer = { end, _ ->
            val width = startRect.width + (end.width - startRect.width) * geometryProgress()
            val scale = if (end.width > 0f) (width / end.width).coerceAtLeast(0f) else 1f
            scaleX = scale
            scaleY = scale
            val fraction = popupFractionWithBack(contentMaterial.value, backProgress.value)
            alpha = fraction
            val blur = GlassMotion.TRANSFORM_BLUR_PX * (1f - fraction)
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        },
        modifier = modifier,
        surface = { shape ->
            if (drawAnchorSurface && anchorSurface != null && anchorVisuals != null) {
                GlassPopupBackground(
                    backdrop = anchorSurface.backdrop,
                    shape = shape,
                    visuals = anchorVisuals,
                    underlayMaterial = anchorSurface.underlayMaterial,
                )
            }
            if (drawMenuSurface) {
                GlassPopupBackground(
                    backdrop = menuBackdrop,
                    shape = shape,
                    visuals = visuals,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (anchorSurface != null) materialProgress.value else 1f
                    },
                )
            }
        },
        onMeasured = onMeasured,
        interactive = isTransformPopupInteractive(show, stacked),
        scrollable = true,
        content = content,
    )

    val renderAnchorContent by remember(simplified, show, iconMaterial, backProgress) {
        derivedStateOf {
            shouldRenderAnchorContent(simplified, show && backProgress.value == 0f) &&
                popupFractionWithBack(iconMaterial.value, backProgress.value) < 0.999f
        }
    }
    if (renderAnchorContent) {
        Box(
            modifier = Modifier
                // 放在定位修饰符之外，使其汇报宿主原点而非副本偏移。
                .onGloballyPositioned { hostOrigin.value = it.positionInWindow() }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            width = iconRect.width.roundToInt().coerceAtLeast(0),
                            height = iconRect.height.roundToInt().coerceAtLeast(0),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (iconRect.left - hostOrigin.value.x).roundToInt(),
                            (iconRect.top - hostOrigin.value.y).roundToInt(),
                        )
                    }
                }
                .graphicsLayer {
                    translationX = (travel.endCenterX - startRect.center.x) * centerProgress()
                    translationY = (travel.endCenterY - startRect.center.y) * centerProgress()
                    val width = startRect.width + (travel.endWidth - startRect.width) * geometryProgress()
                    val growth = if (startRect.width > 0f) width / startRect.width else 1f
                    scaleX = growth
                    scaleY = growth
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    val fraction = iconProgress()
                    alpha = 1f - fraction
                    val blur = GlassMotion.TRANSFORM_BLUR_PX * fraction
                    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                },
            content = { anchorContent() },
        )
    }
}

/** 简化锚点跳过出场时的副本，但回程时仍会绘制它。 */
internal fun shouldRenderAnchorContent(simplified: Boolean, show: Boolean): Boolean = !simplified || !show

/** 退出期间保留的内容只是视觉效果，即便在过渡播放第一个退出帧之前也不接收输入。 */
internal fun isTransformPopupInteractive(show: Boolean, stacked: Boolean): Boolean = show && !stacked

/** 可见度跟随锚点与运动，独立于材质色彩层的不透明度。 */
internal fun transformPanelAlpha(
    floating: Boolean,
    anchorAlpha: Float,
    geometryProgress: Float,
    iconMaterial: Float,
): Float = if (floating) {
    lerp(anchorAlpha, 1f, geometryProgress.coerceIn(0f, 1f))
} else {
    iconMaterial
}

/** 把边界从锚点插值到展开的菜单。 */
private fun transformFrame(
    anchor: Rect,
    // FORK（FlyCat）：停靠对齐时实际用于摆放的锚矩形（见 GlassTransformPopup 的 alignToPageEnd）。
    placementAnchor: Rect,
    end: Size,
    window: Rect,
    margin: Float,
    gap: Float,
    sizeFraction: Float,
    positionFraction: Float,
    startRadius: Dp,
    endRadius: Dp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): GlassPopupFrame {
    val placement = placeGlassPopup(placementAnchor.translate(0f, gap), end, margin, window, layoutDirection)
    val width = anchor.width + (end.width - anchor.width) * sizeFraction
    val height = anchor.height + (end.height - anchor.height) * sizeFraction
    val centerX = anchor.center.x + (placement.rect.center.x - anchor.center.x) * positionFraction
    val centerY = anchor.center.y + (placement.rect.center.y - anchor.center.y) * positionFraction
    return GlassPopupFrame(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
        ),
        cornerRadius = startRadius + (endRadius - startRadius) * sizeFraction,
    )
}

/**
 * 在测量阶段写入、绘制时读取以对齐锚点内容的几何数据。
 * 它不是快照状态：测量途中的写入不能使该趟失效。
 */
private class TransformTravel {
    var endCenterX: Float = 0f
    var endCenterY: Float = 0f
    var endWidth: Float = 0f
}
