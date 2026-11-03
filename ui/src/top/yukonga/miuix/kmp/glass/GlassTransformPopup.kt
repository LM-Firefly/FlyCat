// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.blur.Backdrop
import kotlin.math.roundToInt

/**
 * [GlassTransformPopup] 由此控件生长出来。
 *
 * 面板从控件自身的矩形、位置和圆角出发，一边移动一边扩展到菜单应有的位置；控件的内容随之淡出，面板的内容随之淡入。
 * 用 [rememberGlassPopupAnchor] 持有一个锚点，用 [glassPopupAnchor] 登记控件本身、用 [glassPopupAnchorContent] 登记其内容，然后把它交给 [GlassTransformPopup]。
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

    /** 按钮表面的进度与不透明度，独立于阴影的不透明度。 */
    internal var surfaceProgress: State<Float>? by mutableStateOf(null)
    internal var surfaceOpacity: Float by mutableFloatStateOf(1f)
    internal val surfaceAlpha: Float get() = surfaceOpacity * (surfaceProgress?.value ?: 1f)
    internal var surfaceFloating: Boolean by mutableStateOf(false)

    /** 控件的外边界，位于根坐标系中。 */
    internal var containerBounds: Rect by mutableStateOf(Rect.Zero)

    /** 控件内容的边界，位于根坐标系中。 */
    internal var contentBounds: Rect by mutableStateOf(Rect.Zero)

    /** 下拉框值文本与箭头图标的边界，位于根坐标系中。 */
    internal var dropdownValueBounds: Rect? by mutableStateOf(null)

    /** 控件自身的圆角半径。面板的圆角由此出发，逐渐过渡到面板自身的圆角。 */
    internal var cornerRadius: Dp by mutableStateOf(0.dp)

    /** 控件是悬浮在页面之上，还是嵌在页面之中。悬浮控件的面板在整个运动过程中以全强度绘制；嵌在页面中的控件，其面板透明度则与图标一起渐变。 */
    internal var floating: Boolean by mutableStateOf(false)

    /** 控件是否正在为菜单让位。[GlassTransformPopup] 让整个控件隐去；[GlassDropdownPopup] 只让行内显示的值隐去。[glassPopupAnchor] 直接读取它，值的淡入淡出也由它触发；控件本身无需关心。 */
    internal var contentHidden: Boolean by mutableStateOf(false)

    /** 行内显示的值当前的不透明度。由 [GlassDropdownPopup] 的让位状态经 [rememberGlassPopupAnchor] 驱动。 */
    internal var valueAlpha: Float by mutableFloatStateOf(1f)

    /** 预测性返回收起期间的值不透明度：菜单回位时，显示的值随之重新出现。 */
    internal val dropdownValueAlpha: Float
        get() = (dropdownBackProgressState?.value ?: dropdownRetainedValueAlpha).coerceIn(0f, 1f)

    /**
     * 抑制变换收起端点复用按钮表面。
     * 当按钮采样的是受限的兄弟纯色背景（而非全页背景，如嵌在录制子树内、为规避自采样崩溃而改用按钮大小的纯色层的列表头按钮）时，
     * 复用该表面会让 [GlassTransformPopup] 把这块小背景铺满生长中的面板，在按钮原位留下一块不透明方块。
     * 置位后 [GlassButtonSurface] 不再发布表面，面板改以纯玻璃形态生长。
     */
    internal var suppressTransformSurface: Boolean = false
}

/** 按钮在菜单收起端点上的背景采样与色彩处理。 */
internal data class GlassAnchorSurface(
    val backdrop: Backdrop?,
    val style: GlassStyle,
    val material: GlassMaterial,
    val underlayMaterial: GlassMaterial?,
    val stroke: GlassStroke?,
    val fill: Color,
)

/** 标记按钮的修饰符，使其渲染器能把真实的表面发布到此锚点。 */
internal data class GlassPopupAnchorElement(val anchor: GlassPopupAnchor) : Modifier.Element

/**
 * 记住一个 [GlassPopupAnchor]。
 *
 * [glassPopupAnchorValue] 绘制所用的淡入淡出在这里驱动，而不是放在菜单内部——菜单恰好在控件该回来的时候停止组合。
 *
 * @param suppressTransformSurface 当锚点按钮采样的是受限的兄弟纯色背景时置 `true`，避免展开面板里出现不透明方块（见 [GlassPopupAnchor.suppressTransformSurface]）。
 */
@Composable
fun rememberGlassPopupAnchor(suppressTransformSurface: Boolean = false): GlassPopupAnchor {
    val anchor = remember { GlassPopupAnchor() }
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
 * 把控件登记到 [anchor]，并在其菜单打开时让控件隐去。
 *
 * 把它加在控件最外层节点上——是胶囊（pill）本身，而不是里面的图标；动画期间面板会取代图标的画面。
 * 用在 [GlassIconButton] 上时，它还会把按钮的背景采样、模糊与色彩处理提供给 [GlassTransformPopup] 的收起端点。
 *
 * @param anchor 要登记到的锚点。
 * @param cornerRadius 控件自身的圆角半径。胶囊形状为控件高度的一半。
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
        anchor.containerBounds = it.boundsInRoot()
        anchor.cornerRadius = cornerRadius
        anchor.floating = floating
    }
    .graphicsLayer { alpha = if (anchor.contentHidden) 0f else 1f }

/**
 * 把菜单复制的内容收窄到控件的某一部分。
 *
 * 当面板已经接管了控件的背景时，把它加在图标上；否则连胶囊一起复制会叠出两层背景。
 * 不加时，菜单复制整个控件。
 *
 * @param anchor 要登记到的锚点。
 */
@Stable
fun Modifier.glassPopupAnchorContent(anchor: GlassPopupAnchor): Modifier = this.onGloballyPositioned { anchor.contentBounds = it.boundsInRoot() }

/**
 * 把一行登记到 [anchor]，供 [GlassDropdownPopup] 使用。不会像 [glassPopupAnchor] 那样让行隐去。
 *
 * @param anchor 要登记到的锚点。
 */
@Stable
fun Modifier.glassPopupAnchorRow(anchor: GlassPopupAnchor): Modifier = this.onGloballyPositioned { anchor.containerBounds = it.boundsInRoot() }

/**
 * 登记行内值的边界，供下拉定位使用，并在选项列表打开期间让该值淡出。
 *
 * 把它加在值文本和旁边的箭头图标上，而不是整行上：即将打开的列表*就是*这个值的选项，所以值在列表打开时让位，直到列表完全离开后才回来。
 * 预测性返回完成后，值会带着恢复后的不透明度一直保持到弹出层销毁之后，直到淡入回到完全不透明。
 *
 * @param anchor 该行登记自身时所用的锚点。
 */
@Stable
fun Modifier.glassPopupAnchorValue(anchor: GlassPopupAnchor): Modifier = this
    .onGloballyPositioned { anchor.dropdownValueBounds = it.boundsInRoot() }
    .graphicsLayer {
        alpha = maxOf(anchor.valueAlpha, anchor.dropdownValueAlpha)
    }

/**
 * 从所属控件上生长出来的玻璃菜单。
 *
 * 三种开场动画中的第二种，也是能参与动画的控件所获得的那种。四样东西同时运动，各有各的曲线：
 *
 * - **面板矩形**沿同一根弹簧从控件的矩形过渡到菜单的矩形，圆角随之一起变化。
 * - **面板中心**跑在更快的弹簧上，因此在面板长成之前就先到位。
 * - **控件内容**被复制一份，副本随中心移动、随面板放大，同时淡出并*逐渐加重*模糊。
 * - **面板内容**随面板宽度缩放、淡入并*逐渐减轻*模糊，比前者晚 50ms。
 *
 * 打开期间各行可接收输入，除非上方叠着次级菜单。预测性返回让变形沿原路退回锚点，取消时弹回；
 * [stacked] 期间则由次级菜单接管返回手势。
 *
 * @param show 菜单是否打开。
 * @param onDismissRequest 点击外部或返回请求关闭时调用。
 * @param anchor 菜单所生长出来的控件。
 * @param backdrop 展开的菜单所采样的场景，包含栏但不含弹出层自身。`null` 时回退到按钮的背景采样。收起端点使用按钮的表面。
 * @param anchorContent 控件的副本——连同背景和图标，除非 [glassPopupAnchorContent] 指定了更小的部分。
 * @param modifier 应用于面板的修饰符。
 * @param simplified 控件内容不参与开场变形，让紧凑的图标直接交给面板，而不是先长到面板宽度。
 * 溢出按钮等紧凑图标可选用此项。回程变形时内容仍会参与。
 * @param stacked 是否有第二个菜单挡在本菜单之前。本菜单会缩小并蒙上一层罩色。
 * @param maskColor [stacked] 期间蒙在本菜单上的罩色。
 * @param sizing 面板的尺寸限制与窗口边距。
 * @param visuals 展开面板的外观。[GlassIconButton] 锚点会提供收起端点，沿现有几何动画将其混合进该外观。
 * @param anchorAlpha 没有共享按钮表面的锚点所用的背景不透明度。
 * @param cornerRadius 面板最终停留的圆角半径。
 * @param gap 控件与面板之间的间隙。
 * @param alignToPageEnd 停靠后的面板是否把末端边缘对齐到页面安全边距，而不是锚点的末端边缘。
 * 用于锚点之后还有其他控件的溢出菜单，使菜单仍贴着页面边缘展开。
 * @param contentPadding 各项周围的内边距。
 * @param onMeasured 以面板最终停靠的尺寸回调。从它的行中打开的第二个菜单至少按此宽度测量。
 * @param onDismissFinished 弹出层完全退场（收起动画结束）后调用。
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
    alignToPageEnd: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(vertical = GlassPopupDefaults.ContentPaddingVertical),
    onMeasured: ((Size) -> Unit)? = null,
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
    if (anchor.containerBounds.isEmpty) return

    DisposableEffect(active, anchor) {
        anchor.contentHidden = active
        onDispose { anchor.contentHidden = false }
    }
    if (!active) return

    val anchorSurface = anchor.surface
    val anchorVisuals = anchorSurface?.let {
        visuals.copy(style = it.style, material = it.material, stroke = it.stroke, containerColor = it.fill, alpha = 1f)
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
    fun geometryProgress() = popupFractionWithBack(bounds.value, backProgress.value).coerceIn(0f, 1f)
    fun centerProgress() = popupFractionWithBack(center.value, backProgress.value)
    fun iconProgress() = popupFractionWithBack(iconMaterial.value, backProgress.value)
    fun stackedProgress() = popupFractionWithBack(pushedBack.value, anchor.secondaryBackProgress)
    fun panelAlpha() = transformPanelAlpha(
        visualAlpha = visuals.alpha,
        floating = if (anchorSurface != null) anchor.surfaceFloating || anchor.surfaceAlpha > 0f else anchor.floating,
        // transformPanelAlpha 会再乘上 visualAlpha；而发布的表面 alpha 已经包含了按钮自身的不透明度，所以在锚点端点要把这个外层因子除掉。
        anchorAlpha = if (anchorSurface != null) {
            if (visuals.alpha > 0f) anchor.surfaceAlpha / visuals.alpha else 0f
        } else {
            anchorAlpha
        },
        geometryProgress = geometryProgress(),
        iconMaterial = iconProgress(),
    )
    val travel = remember { TransformTravel() }

    GlassPopupSurface(
        onDismissRequest = onDismissRequest,
        backdrop = menuBackdrop,
        modifier = modifier,
        sizing = sizing,
        visuals = visuals.copy(alpha = 1f),
        interactive = isTransformPopupInteractive(show, stacked),
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
                    visuals = visuals.copy(alpha = 1f),
                    modifier = Modifier.graphicsLayer {
                        alpha = if (anchorSurface != null) materialProgress.value else 1f
                    },
                )
            }
        },
        contentPadding = contentPadding,
        onMeasured = onMeasured,
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
        frame = { end, page ->
            val marginPx = sizing.safeMargin.toPx()
            val placementAnchor = if (alignToPageEnd) {
                if (layoutDirection == LayoutDirection.Ltr) {
                    startRect.copy(right = maxOf(startRect.right, page.width - marginPx))
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
                page = page,
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
                page,
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
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            width = iconRect.width.roundToInt().coerceAtLeast(0),
                            height = iconRect.height.roundToInt().coerceAtLeast(0),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(iconRect.left.roundToInt(), iconRect.top.roundToInt())
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

/** 悬浮透明度作用于弹出层的整个容器，而不仅是面板。 */
internal fun transformPanelAlpha(
    visualAlpha: Float,
    floating: Boolean,
    anchorAlpha: Float,
    geometryProgress: Float,
    iconMaterial: Float,
): Float = visualAlpha * if (floating) {
    lerp(anchorAlpha, 1f, geometryProgress.coerceIn(0f, 1f))
} else {
    iconMaterial
}

/** 面板矩形从控件自身向菜单矩形过渡的中间状态。 */
private fun transformFrame(
    anchor: Rect,
    placementAnchor: Rect,
    end: Size,
    page: Size,
    margin: Float,
    gap: Float,
    sizeFraction: Float,
    positionFraction: Float,
    startRadius: Dp,
    endRadius: Dp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): GlassPopupFrame {
    val placement = placeGlassPopup(placementAnchor.translate(0f, gap), end, margin, page, layoutDirection)
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
 * 面板离控件移动了多远、变大了多少。
 *
 * 在测量阶段写入，绘制时由控件内容的副本读取，两者因此同步移动。它是普通容器而非快照状态：
 * 写入发生在测量中途。
 */
private class TransformTravel {
    var endCenterX: Float = 0f
    var endCenterY: Float = 0f
    var endWidth: Float = 0f
}
