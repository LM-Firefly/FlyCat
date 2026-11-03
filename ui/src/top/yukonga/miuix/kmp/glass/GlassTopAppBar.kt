// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.internal.BlurTopAppBar
import top.yukonga.miuix.kmp.glass.internal.drawGlassMask
import top.yukonga.miuix.kmp.glass.internal.drawGlassStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** [GlassTopAppBar] 与 [GlassIconButton] 的默认值。 */
object GlassTopAppBarDefaults {

    /** 玻璃控件的起始/末端内缩，与页面的卡片和标签页对齐。 */
    val HorizontalPadding: Dp = 12.dp

    /** 阴影过渡曲线的滚动起始阈值。材质可见性单独驱动。 */
    val RampStart: Dp = 0.dp

    /**
     * 保留的 Compose 阴影过渡曲线对应的滚动距离。蒙版与按钮材质使用与该距离无关的定时过渡。
     */
    val RampDistance: Dp = 32.dp

    /** 大标题折叠时的模糊程度。 */
    val LargeTitleBlurRadius: Dp = 12.dp

    /** 顶栏按钮背后玻璃胶囊的直径。 */
    val ButtonSize: Dp = 44.dp

    /** 渐进背景色带的最大模糊半径。 */
    val ProgressiveBlurRadius: Dp = 80.dp

    /** 渐进色带淡入所对应的滚动距离。 */
    val ProgressiveBlurRampDistance: Dp = 48.dp

    /** 按住操作栏按钮时图标的不透明度。 */
    val PressedContentAlpha: Float = 0.6f

    /**
     * 顶栏已折叠的程度，取值 `[0, 1]`。
     *
     * @param scrollBehavior 驱动顶栏的滚动行为。传 `null` 表示顶栏完全折叠，这正是不可滚动内容之上的顶栏所需要的。
     */
    @Composable
    fun collapseRamp(scrollBehavior: ScrollBehavior?): State<Float> {
        val density = LocalDensity.current
        val startPx = with(density) { RampStart.toPx() }
        val rampPx = with(density) { RampDistance.toPx() }
        return remember(scrollBehavior, startPx, rampPx) {
            if (scrollBehavior == null) {
                mutableFloatStateOf(1f)
            } else {
                derivedStateOf {
                    val scrollRamp = ((-scrollBehavior.state.contentOffset - startPx) / rampPx).coerceIn(0f, 1f)
                    // 内容不足时 overscroll 会把顶栏推入折叠却不产生 contentOffset；用折叠度兜底，避免 chrome 停在「折叠小标题但透明」坏态。
                    maxOf(scrollRamp, scrollBehavior.state.collapsedFraction)
                }
            }
        }
    }

    /** 背景材质不可用时使用的传统填充色。 */
    @Composable
    fun buttonFill(): Color = if (isDarkTheme()) Color(0xFF2C2C2C) else Color.White

    /** 操作按钮材质 `internal-pured-thin-glass`。 */
    @Composable
    fun buttonMaterial(): GlassMaterial = GlassMaterials.puredThinGlass(isDarkTheme())

    /** 垫在子级按钮与标签页之下的衬底材质。 */
    @Composable
    internal fun actionBarUnderlayMaterial(): GlassMaterial = GlassMaterials.actionBarMask(isDarkTheme())

    /** 与 [buttonMaterial] 搭配的小号泛光描边。 */
    @Composable
    fun buttonStroke(): GlassStroke = GlassStrokes.forTheme(
        isDark = isDarkTheme(),
        light = GlassStrokes.SmallLight,
        dark = GlassStrokes.SmallDark,
    )

    /** 按住操作栏图标时绘制在其背后的状态叠加层。 */
    @Composable
    fun buttonPressedOverlay(): Color = if (isDarkTheme()) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    /**
     * 内容从顶栏下方经过时将其压暗的色带：把页面自身的颜色重新铺在内容之上，顶边完全不透明，到底边渐隐至无。
     *
     * @param color 页面的背景色。
     */
    @Stable
    fun bandBrush(color: Color): Brush = Brush.verticalGradient(
        0.00f to color,
        0.45f to color,
        0.58f to color.copy(alpha = 0.82f),
        0.72f to color.copy(alpha = 0.58f),
        0.86f to color.copy(alpha = 0.34f),
        1.00f to color.copy(alpha = 0f),
    )

    /** 色带向下超出顶栏底部的距离。 */
    val BandOverhang: Dp = 28.dp

    @Composable
    private fun isDarkTheme(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f
}

internal data class GlassTopAppBarContext(
    val backdrop: Backdrop?,
    val buttonBackdrop: Backdrop?,
    val material: GlassMaterial,
    val underlayMaterial: GlassMaterial?,
    val buttonUnderlayMaterial: GlassMaterial?,
    val floating: Boolean,
    val alpha: Float,
    val ramp: State<Float>,
    val materialProgress: State<Float>,
    val keepMaterial: Boolean,
    val style: GlassStyle,
    val size: Dp,
    val shape: GlassShape,
    val fill: Color,
    val stroke: GlassStroke?,
    val shadow: GlassShadow?,
)

internal val LocalGlassTopAppBarContext = staticCompositionLocalOf<GlassTopAppBarContext?> { null }

/**
 * 材质可见性跟随 [ScrollBehavior] 的顶栏。
 * 页面能准确报告其重叠状态时，改用带 [isContentScrolled] 的重载。
 *
 * @param title 紧凑标题，顶栏折叠时居中。
 * @param backdrop 按钮采样的背景。`null` 使用 [fill]。
 * @param modifier 应用于顶栏的修饰符。
 * @param largeTitle 内容滚动前显示的大标题。默认取 [title]。
 * @param subtitle 标题下方的可选副标题。
 * @param largeTitleBlurRadius 大标题折叠时的模糊程度。
 * @param scrollBehavior 驱动折叠的滚动行为。不传时顶栏保持完全强度。
 * @param bandBrush 把顶栏下方经过的内容压暗的色带。
 * @param bandOverhang 色带向下超出顶栏的距离。
 * @param style 转发给渲染器的玻璃样式。
 * @param alpha 按钮胶囊的不透明度乘数。
 * @param buttonShape 按钮胶囊的轮廓。
 * @param buttonSize 按钮胶囊的直径。
 * @param buttonShadow 按钮胶囊下方的阴影。`null` 表示移除。
 * @param fill 玻璃渲染不可用时的传统按钮填充色。
 * @param stroke 按钮胶囊上可选的泛光描边。
 * @param defaultWindowInsetsPadding 是否应用横向的挖孔屏与导航栏内缩。
 * @param titlePadding 标题与大标题的水平内缩。
 * @param navigationIconPadding 首端控件的起始内缩。
 * @param actionIconPadding 末端控件的末端内缩。
 * @param pinned 钉住模式（FORK）：无大标题、不折叠，紧凑标题常显（供应用 SmallTopBar 使用）。
 * @param bandFollowsScroll 色带/渐进背景是否随滚动渐显；`false` 时常驻显示（FORK）。
 * @param contentModifier 前景顶栏的修饰符；背景色带保持不动。
 * @param progressiveBlur 绘制渐进背景，并让按钮采样该渲染场景。需要受支持且非 `null` 的 [backdrop]。背景不包含按钮及其他顶栏内容。
 * @param material 按钮材质，启用时铺在渲染出的渐进色带之上。
 * @param backgroundAlpha 背景色带绘制阶段的不透明度乘数。
 * @param titleAlpha 标题容器绘制阶段的不透明度。
 * @param navigationIcon 首端控件槽位。玻璃按钮请用 [GlassIconButton]。
 * @param actions 末端控件槽位。玻璃按钮请用 [GlassIconButton]。
 * @param bottomContent 钉在顶栏内标题下方的内容。
 */
@Composable
fun GlassTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    progressiveBlur: Boolean = false,
    backdrop: Backdrop? = null,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    bandBrush: Brush = GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface),
    bandOverhang: Dp = GlassTopAppBarDefaults.BandOverhang,
    largeTitleBlurRadius: Dp = GlassTopAppBarDefaults.LargeTitleBlurRadius,
    style: GlassStyle = GlassDefaults.Style,
    material: GlassMaterial = GlassTopAppBarDefaults.buttonMaterial(),
    alpha: Float = 1f,
    buttonSize: Dp = GlassTopAppBarDefaults.ButtonSize,
    buttonShape: GlassShape = GlassShape(buttonSize / 2),
    fill: Color = GlassTopAppBarDefaults.buttonFill(),
    stroke: GlassStroke? = GlassTopAppBarDefaults.buttonStroke(),
    buttonShadow: GlassShadow? = GlassShadows.Regular,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    actionIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    pinned: Boolean = false,
    bandFollowsScroll: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    backgroundAlpha: () -> Float = { 1f },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val isContentScrolled by remember(scrollBehavior) {
        derivedStateOf {
            // 与 collapseRamp/色带同款兜底：内容不足被推入折叠但无 contentOffset 时，按钮材质也要随 chrome 浮现，否则只剩阴影涂抹。
            scrollBehavior?.state?.let { it.contentOffset < 0f || it.collapsedFraction > 0f } ?: true
        }
    }
    GlassTopAppBar(
        title = title,
        isContentScrolled = isContentScrolled,
        modifier = modifier,
        progressiveBlur = progressiveBlur,
        backdrop = backdrop,
        largeTitle = largeTitle,
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        bandBrush = bandBrush,
        bandOverhang = bandOverhang,
        largeTitleBlurRadius = largeTitleBlurRadius,
        style = style,
        material = material,
        alpha = alpha,
        buttonSize = buttonSize,
        buttonShape = buttonShape,
        fill = fill,
        stroke = stroke,
        buttonShadow = buttonShadow,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        pinned = pinned,
        bandFollowsScroll = bandFollowsScroll,
        contentModifier = contentModifier,
        titleAlpha = titleAlpha,
        backgroundAlpha = backgroundAlpha,
        navigationIcon = navigationIcon,
        actions = actions,
        bottomContent = bottomContent,
    )
}

/**
 * 页面重叠状态由调用方显式提供的玻璃顶栏。
 *
 * @param title 紧凑标题。
 * @param isContentScrolled 页面内容是否已离开其静止位置。惰性列表请传 `listState.canScrollBackward`。
 */
@Composable
fun GlassTopAppBar(
    title: String,
    isContentScrolled: Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    progressiveBlur: Boolean = false,
    backdrop: Backdrop? = null,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    bandBrush: Brush = GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface),
    bandOverhang: Dp = GlassTopAppBarDefaults.BandOverhang,
    largeTitleBlurRadius: Dp = GlassTopAppBarDefaults.LargeTitleBlurRadius,
    style: GlassStyle = GlassDefaults.Style,
    material: GlassMaterial = GlassTopAppBarDefaults.buttonMaterial(),
    alpha: Float = 1f,
    buttonSize: Dp = GlassTopAppBarDefaults.ButtonSize,
    buttonShape: GlassShape = GlassShape(buttonSize / 2),
    fill: Color = GlassTopAppBarDefaults.buttonFill(),
    stroke: GlassStroke? = GlassTopAppBarDefaults.buttonStroke(),
    buttonShadow: GlassShadow? = GlassShadows.Regular,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    actionIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    pinned: Boolean = false,
    bandFollowsScroll: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    backgroundAlpha: () -> Float = { 1f },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val ramp = GlassTopAppBarDefaults.collapseRamp(scrollBehavior)
    val floating = isContentScrolled
    val transition = updateTransition(floating, label = "glassTopBarFloat")
    val maskProgress = transition.animateFloat(
        transitionSpec = { GlassMotion.topBarMask() },
        label = "glassTopBarMask",
    ) { if (it) 1f else 0f }
    val materialProgress = transition.animateFloat(
        transitionSpec = { GlassMotion.topBarButtonFloat() },
        label = "glassTopBarMaterial",
    ) { if (it) 1f else 0f }
    val keepMaterial by remember(floating, materialProgress) {
        derivedStateOf { floating || materialProgress.value > 0f }
    }
    val underlayMaterial = GlassTopAppBarDefaults.actionBarUnderlayMaterial().takeIf { keepMaterial && backdrop != null }
    val rawBackdrop = backdrop?.takeIf { isRuntimeShaderSupported() }
    var backgroundCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val progressiveBackdrop = if (progressiveBlur && rawBackdrop != null) {
        rememberLayerBackdrop {
            val coordinates = backgroundCoordinates?.takeIf { it.isAttached }
            with(rawBackdrop) { drawBackdrop(this@rememberLayerBackdrop, coordinates) }
            drawContent()
        }
    } else {
        null
    }
    val buttonUnderlay = underlayMaterial.takeIf { progressiveBackdrop == null }
    val resolvedMaterial = material.withUnderlayBlur(buttonUnderlay)

    Box(modifier = modifier) {
        if (progressiveBackdrop != null && rawBackdrop != null) {
            Box(
                // FORK（FlyCat）：色带严格限定在顶栏自身高度内（含收缩时随之收窄），不向下越界盖住页面内容顶部。
                modifier = Modifier.matchParentSize().onGloballyPositioned { backgroundCoordinates = it }.layerBackdrop(progressiveBackdrop).graphicsLayer {
                    val ramp = scrollBehavior?.let {
                        val scrollRamp = (-it.state.contentOffset / GlassTopAppBarDefaults.ProgressiveBlurRampDistance.toPx()).coerceIn(0f, 1f)
                        // 内容不足时 overscroll 会把顶栏推入折叠却不产生 contentOffset；用折叠度兜底，避免「折叠小标题但色带透明、与首项重叠」。
                        maxOf(scrollRamp, it.state.collapsedFraction)
                    } ?: maskProgress.value
                    // FORK（FlyCat）：bandFollowsScroll=false 时渐进背景常驻显示。
                    this.alpha = (if (bandFollowsScroll) ramp else 1f) * backgroundAlpha().coerceIn(0f, 1f)
                }
                .progressiveTextureBlur(
                    backdrop = rawBackdrop,
                    shape = RectangleShape,
                    gradient = ProgressiveBlur.Top.copy(curve = 0.5f),
                    blurRadius = GlassTopAppBarDefaults.ProgressiveBlurRadius.value,
                ),
            )
        } else {
            val overhangPx = with(LocalDensity.current) { bandOverhang.toPx() }
            Box(
                modifier = Modifier.matchParentSize().graphicsLayer {
                    this.alpha = (if (bandFollowsScroll) maskProgress.value else 1f) * backgroundAlpha().coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleY = if (size.height > 0f) (size.height + overhangPx) / size.height else 1f
                }
                .background(bandBrush),
            )
        }
        CompositionLocalProvider(
            LocalGlassTopAppBarContext provides GlassTopAppBarContext(
                backdrop = rawBackdrop,
                buttonBackdrop = progressiveBackdrop ?: rawBackdrop,
                material = resolvedMaterial,
                underlayMaterial = underlayMaterial,
                buttonUnderlayMaterial = buttonUnderlay,
                floating = floating,
                alpha = alpha,
                ramp = ramp,
                materialProgress = materialProgress,
                keepMaterial = keepMaterial,
                style = style,
                size = buttonSize,
                shape = buttonShape,
                fill = fill,
                stroke = stroke,
                shadow = buttonShadow,
            ),
        ) {
            BlurTopAppBar(
                title = title,
                modifier = contentModifier,
                titleAlpha = titleAlpha,
                largeTitle = largeTitle,
                largeTitleBlurRadius = largeTitleBlurRadius,
                subtitle = subtitle,
                color = Color.Transparent,
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = defaultWindowInsetsPadding,
                titlePadding = titlePadding,
                navigationIconPadding = navigationIconPadding,
                actionIconPadding = actionIconPadding,
                pinned = pinned,
                navigationIcon = navigationIcon,
                actions = actions,
                bottomContent = bottomContent,
            )
        }
    }
}

/**
 * 用于顶栏首端或末端控件的圆形玻璃按钮。
 *
 * 挂接 [glassPopupAnchor] 后，按钮会把解析出的背景与材质发布给 [GlassTransformPopup] 的折叠端点。面板在展开过程中融入按钮自身的材质。
 *
 * @param onClick 点击按钮时回调。
 * @param backdrop 玻璃背后可选的 [Backdrop]。在 [GlassTopAppBar] 内部，按钮继承顶栏的背景与有效模糊半径；在其他位置传 `null` 则使用传统 [fill]。
 * @param modifier 应用于按钮的修饰符。
 * @param inheritTopBarMaterial 是否继承顶栏的背景与衬底材质。为 `false` 时，按钮在 [backdrop] 之上使用自身的材质，缺失时回退到顶栏的背景。
 * @param surfaceAlpha 胶囊的不透明度。在 [GlassTopAppBar] 内部保持默认值即可。
 * @param style 转发给渲染器的玻璃样式。
 * @param material 显式指定的按钮材质。传 `null` 时，若 [inheritTopBarMaterial] 为 `true` 则继承顶栏的材质，否则使用 [GlassTopAppBarDefaults.buttonMaterial]。继承自顶栏的衬底会把其模糊贡献给显式指定的材质。
 * @param size 按钮直径。
 * @param shape 按钮轮廓。默认为以 [size] 为直径的圆形。
 * @param shadow 按钮下方的阴影。`null` 表示移除。
 * @param stroke 沿边缘的可选泛光描边。
 * @param content 内部的图标。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inheritTopBarMaterial: Boolean = true,
    backdrop: Backdrop? = null,
    surfaceAlpha: Float = 1f,
    style: GlassStyle = LocalGlassTopAppBarContext.current?.style ?: GlassDefaults.Style,
    material: GlassMaterial? = null,
    size: Dp = LocalGlassTopAppBarContext.current?.size ?: GlassTopAppBarDefaults.ButtonSize,
    shape: GlassShape = LocalGlassTopAppBarContext.current?.shape ?: GlassShape(size / 2),
    fill: Color = LocalGlassTopAppBarContext.current?.fill ?: GlassTopAppBarDefaults.buttonFill(),
    stroke: GlassStroke? = LocalGlassTopAppBarContext.current.let { if (it != null) it.stroke else GlassTopAppBarDefaults.buttonStroke() },
    shadow: GlassShadow? = LocalGlassTopAppBarContext.current.let { if (it != null) it.shadow else GlassShadows.Regular },
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val topBarContext = LocalGlassTopAppBarContext.current
    val materialContext = topBarContext.takeIf { inheritTopBarMaterial }
    val resolvedSurfaceAlpha = surfaceAlpha * (topBarContext?.alpha ?: 1f)
    val topBarRamp = topBarContext?.ramp
    GlassButtonSurface(
        backdrop = materialContext?.buttonBackdrop ?: backdrop ?: topBarContext?.buttonBackdrop,
        floating = topBarContext?.floating ?: (surfaceAlpha > 0.01f),
        surfaceAlpha = resolvedSurfaceAlpha,
        shadowAlpha = { resolvedSurfaceAlpha * (topBarRamp?.value ?: 1f) },
        style = style,
        material = material?.withUnderlayBlur(materialContext?.buttonUnderlayMaterial)
            ?: materialContext?.material ?: GlassTopAppBarDefaults.buttonMaterial(),
        underlayMaterial = materialContext?.buttonUnderlayMaterial,
        shape = shape,
        size = size,
        fill = fill,
        stroke = stroke,
        shadow = shadow,
        sharedProgress = topBarContext?.materialProgress,
        sharedKeepMaterial = topBarContext?.keepMaterial,
        pressed = pressed,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

/** [GlassIconButton] 在两种顶栏槽位中使用的表面与按压反馈。 */
@Composable
// foldIn 只读取锚点标记；该修饰符仍然只应用于唯一的一个根 Box。
@Suppress("ktlint:compose:modifier-reused-check", "ktlint:compose:state-param-check")
private fun GlassButtonSurface(
    backdrop: Backdrop?,
    floating: Boolean,
    surfaceAlpha: Float,
    shadowAlpha: () -> Float,
    style: GlassStyle,
    material: GlassMaterial,
    underlayMaterial: GlassMaterial?,
    fill: Color,
    shape: GlassShape,
    size: Dp,
    stroke: GlassStroke?,
    shadow: GlassShadow?,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    // 保留 State 供绘制阶段读取；共享动画时钟由顶栏持有。
    sharedProgress: State<Float>? = null,
    sharedKeepMaterial: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val popupAnchor = modifier.foldIn<GlassPopupAnchor?>(null) { found, element ->
        if (element is GlassPopupAnchorElement) element.anchor else found
    }
    DisposableEffect(popupAnchor) {
        onDispose { popupAnchor?.surface = null }
    }
    val progress = sharedProgress ?: run {
        val floatingTransition = updateTransition(targetState = floating, label = "glassTopBarButtonFloat")
        floatingTransition.animateFloat(
            transitionSpec = { GlassMotion.topBarButtonFloat() },
            label = "glassTopBarButtonMaterial",
        ) { active ->
            if (active) 1f else 0f
        }
    }
    val ownKeepMaterial by remember(floating, progress) {
        derivedStateOf { floating || progress.value > 0f }
    }
    val keepMaterial = sharedKeepMaterial ?: ownKeepMaterial
    val opacity = surfaceAlpha.coerceIn(0f, 1f)
    val anchorSurface = remember(backdrop, style, material, underlayMaterial, stroke, fill) {
        GlassAnchorSurface(backdrop, style, material, underlayMaterial, stroke, fill)
    }
    SideEffect {
        val anchor = popupAnchor ?: return@SideEffect
        // 采样受限兄弟纯色层的按钮不得把其表面当作折叠端点，否则展开面板里会留下按钮大小的不透明方块；此类锚点以纯玻璃形态生长。
        anchor.surface = if (anchor.suppressTransformSurface) null else anchorSurface
        anchor.surfaceProgress = progress
        anchor.surfaceOpacity = opacity
        anchor.surfaceFloating = floating
    }
    val pressTransition = updateTransition(targetState = pressed, label = "glassTopBarButtonPress")
    val contentAlpha = pressTransition.animateFloat(
        transitionSpec = { if (targetState) GlassMotion.pressDown() else GlassMotion.pressUp() },
        label = "glassTopBarButtonContentAlpha",
    ) { down ->
        if (down) GlassTopAppBarDefaults.PressedContentAlpha else 1f
    }
    val overlayAlpha = pressTransition.animateFloat(
        transitionSpec = { if (targetState) GlassMotion.pressDown() else GlassMotion.pressUp() },
        label = "glassTopBarButtonOverlayAlpha",
    ) { down ->
        if (down) 1f else 0f
    }
    val pressedOverlay = GlassTopAppBarDefaults.buttonPressedOverlay()

    Box(
        modifier = modifier
            .size(size)
            .glassShadow(shape, shadow, shadowAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = opacity * progress.value }
                .then(
                    if (backdrop != null) {
                        if (underlayMaterial != null) {
                            Modifier.glassOnActionBar(
                                backdrop = backdrop,
                                shape = shape,
                                style = style,
                                alpha = 1f,
                                material = material,
                                underlayMaterial = underlayMaterial,
                                stroke = stroke.takeIf { keepMaterial },
                                enabled = keepMaterial,
                            )
                        } else {
                            Modifier.glass(
                                backdrop = backdrop,
                                shape = shape,
                                style = style,
                                // 把解析出的表面作为一层整体淡出：在禁用着色时，仅靠颜色层的 alpha 无法隐藏不透明的背景图像。
                                alpha = 1f,
                                material = material,
                                stroke = stroke.takeIf { keepMaterial },
                                enabled = keepMaterial,
                                shading = false,
                            )
                        }
                    } else {
                        Modifier
                            .then(
                                if (isRuntimeShaderSupported()) {
                                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                } else {
                                    Modifier.clip(shape)
                                },
                            )
                            .background(fill)
                            .drawWithContent {
                                drawContent()
                                if (stroke != null && keepMaterial) {
                                    drawGlassStroke(shape, layoutDirection, stroke, 1f)
                                }
                                drawGlassMask(shape, layoutDirection)
                            }
                    },
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .drawBehind {
                    drawRect(pressedOverlay.copy(alpha = pressedOverlay.alpha * overlayAlpha.value.coerceIn(0f, 1f)))
                },
        )
        Box(
            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value.coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
