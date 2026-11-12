// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.internal.BlurTopAppBar
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.withUnderlayBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** [GlassTopAppBar] 与 [GlassIconButton] 的默认值。 */
object GlassTopAppBarDefaults {

    /** 玻璃控件的起始/末端内缩，与页面卡片和标签栏对齐。 */
    val HorizontalPadding: Dp = 12.dp

    /** 保留阴影渐变的滚动起始阈值。 */
    val RampStart: Dp = 0.dp

    /** 保留阴影渐变的滚动距离。材质过渡使用各自的时序。 */
    val RampDistance: Dp = 32.dp

    /** 大标题折叠时的模糊程度。 */
    val LargeTitleBlurRadius: Dp = 12.dp

    /** 栏内按钮玻璃胶囊的直径。 */
    val ButtonSize: Dp = 44.dp

    /** 渐进背景色带的最大模糊半径。 */
    val ProgressiveBlurRadius: Dp = 80.dp

    /** 渐进色带淡入所用的滚动距离。 */
    val ProgressiveBlurRampDistance: Dp = 48.dp

    /** 操作栏按钮被按住时的图标不透明度。 */
    val PressedContentAlpha: Float = 0.6f

    /**
     * 顶栏折叠程度，取值 `[0, 1]`。
     *
     * @param scrollBehavior 驱动顶栏的行为。传 `null` 时报告为完全折叠，适合内容不滚动的顶栏。
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
                    ((-scrollBehavior.state.contentOffset - startPx) / rampPx).coerceIn(0f, 1f)
                }
            }
        }
    }

    /** 背景材质不可用时按钮的回退填充色。 */
    @Composable
    fun buttonFill(): Color = if (isDarkTheme()) Color(0xFF2C2C2C) else Color.White

    /** 操作栏按钮的默认材质。 */
    @Composable
    fun buttonMaterial(): GlassMaterial = GlassMaterials.puredThinGlass(isDarkTheme())

    /** 施加在子按钮与标签栏之下的垫底材质。 */
    @Composable
    internal fun actionBarUnderlayMaterial(): GlassMaterial = GlassMaterials.actionBarMask(isDarkTheme())

    /** 与 [buttonMaterial] 搭配的小泛光描边。 */
    @Composable
    fun buttonStroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = isDarkTheme(),
        light = GlassEdgeStrokes.SmallLight,
        dark = GlassEdgeStrokes.SmallDark,
    )

    /** 按住操作栏图标时绘制在图标后的状态叠层。 */
    @Composable
    fun buttonPressedOverlay(): Color = if (isDarkTheme()) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    /** 压暗顶栏下方内容的渐变。
     *
     * @param color 页面背景色。
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

    /** 色带超出顶栏底部的延伸距离。 */
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
    val stroke: EdgeStroke?,
    val shadow: GlassShadow?,
)

internal val LocalGlassTopAppBarContext = staticCompositionLocalOf<GlassTopAppBarContext?> { null }

/**
 * 材质可见性跟随 [ScrollBehavior] 的玻璃顶栏。
 * 页面能精确报告其重叠状态时，使用带 [isContentScrolled] 的重载。
 *
 * @param title 紧凑标题，顶栏折叠时居中显示。
 * @param backdrop 按钮采样的背景。`null` 时使用 [fill]。
 * @param modifier 应用于顶栏的修饰符。
 * @param largeTitle 内容滚动前显示的大标题，默认为 [title]。
 * @param subtitle 可选的标题下方副标题。
 * @param largeTitleBlurRadius 大标题折叠时施加的模糊。
 * @param scrollBehavior 驱动折叠的行为。不传时顶栏保持完全显形。
 * @param bandBrush 压暗顶栏下方内容的渐变。
     * @param bandOverhang 渐变超出顶栏底部的距离。
 * @param style 传递给渲染器的玻璃样式。
 * @param alpha 按钮颜色层与描边的不透明度乘数。
 * @param buttonShape 按钮胶囊轮廓。
 * @param buttonSize 按钮胶囊直径。
 * @param buttonShadow 按钮胶囊下方的阴影；`null` 关闭阴影。
 * @param fill 玻璃渲染不可用时按钮的回退填充色。
 * @param stroke 按钮胶囊上可选的泛光描边。
 * @param defaultWindowInsetsPadding 是否应用横向挖孔屏与导航栏窗口衬垫。
 * @param contentModifier 前景栏的修饰符；背景色带保持不动。
 * @param progressiveBlur 绘制渐进背景并让按钮采样该渲染场景。
 *   需要受支持且非空的 [backdrop]。背景不包含按钮及其他栏内内容。
 * @param material 按钮材质；启用渐进背景时叠加在渲染的色带之上。
 * @param backgroundAlpha 背景色带的绘制阶段不透明度乘数。
 * @param titleAlpha 标题容器的绘制阶段不透明度。
 * @param navigationIcon 首端控件槽位。玻璃按钮请用 [GlassIconButton]。
 * @param actions 末端控件槽位。玻璃按钮请用 [GlassIconButton]。
 * @param bottomContent 钉在顶栏内标题下方的内容。
 * @param titlePadding FORK（FlyCat）：标题与大标题的水平内缩。
 * @param navigationIconPadding FORK（FlyCat）：首端控件的起始内缩。
 * @param actionIconPadding FORK（FlyCat）：末端控件的末端内缩。
 * @param pinned FORK（FlyCat）：钉住模式——无大标题、不折叠，紧凑标题常显（SmallTopBar 语义）。
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
    stroke: EdgeStroke? = GlassTopAppBarDefaults.buttonStroke(),
    buttonShadow: GlassShadow? = GlassShadows.Regular,
    defaultWindowInsetsPadding: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    backgroundAlpha: () -> Float = { 1f },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    // FORK（FlyCat）：内缩透传与钉住模式（详见 KDoc）。
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    actionIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    pinned: Boolean = false,
) {
    val isContentScrolled by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior?.state?.let { it.contentOffset < 0f } ?: true }
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
        contentModifier = contentModifier,
        titleAlpha = titleAlpha,
        backgroundAlpha = backgroundAlpha,
        navigationIcon = navigationIcon,
        actions = actions,
        bottomContent = bottomContent,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        pinned = pinned,
    )
}

/**
 * 带显式页面重叠状态的玻璃顶栏。
 *
 * @param title 紧凑标题。
 * @param isContentScrolled 页面内容是否已离开静止位置。懒列表可传 `listState.canScrollBackward`。
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
    stroke: EdgeStroke? = GlassTopAppBarDefaults.buttonStroke(),
    buttonShadow: GlassShadow? = GlassShadows.Regular,
    defaultWindowInsetsPadding: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    backgroundAlpha: () -> Float = { 1f },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    // FORK（FlyCat）：内缩透传与钉住模式（详见第一重载 KDoc）。
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    actionIconPadding: Dp = GlassTopAppBarDefaults.HorizontalPadding,
    pinned: Boolean = false,
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
            val topInsets = WindowInsets.statusBars
            val blurColors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.3f))),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // FORK（FlyCat）：色带可视高度裁剪到顶栏自身高度（随折叠收窄，不越界盖住正文顶部）；内层渐进模糊节点仍保持上游固定高度不随收缩重排，避免壁纸副本错位。
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        // 标签栏自带材质；色带跟随折叠态的工具栏（固定高度，不随收缩动画改变几何）。
                        val barHeight = topInsets.getTop(this) + TopAppBarDefaults.CollapsedHeight.toPx()
                        val height = ((barHeight + 20.dp.toPx()) * 1.2f).roundToInt()
                        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
                    }
                    .onGloballyPositioned { backgroundCoordinates = it }
                    .layerBackdrop(progressiveBackdrop)
                    .graphicsLayer {
                        val ramp = scrollBehavior?.let {
                            val scrollRamp = (-it.state.contentOffset / GlassTopAppBarDefaults.ProgressiveBlurRampDistance.toPx()).coerceIn(0f, 1f)
                            // FORK（FlyCat）：内容不足时 overscroll 会把顶栏推入折叠却不产生 contentOffset；用折叠度兜底，避免「折叠小标题但色带透明、与首项重叠」。
                            maxOf(scrollRamp, it.state.collapsedFraction)
                        } ?: maskProgress.value
                        this.alpha = ramp * backgroundAlpha().coerceIn(0f, 1f)
                    }
                    .progressiveTextureBlur(
                        backdrop = rawBackdrop,
                        shape = RectangleShape,
                        gradient = ProgressiveBlur.Top.copy(curve = 0.5f),
                        blurRadius = GlassTopAppBarDefaults.ProgressiveBlurRadius.value,
                        colors = blurColors,
                    ),
            )
        } else {
            val overhangPx = with(LocalDensity.current) { bandOverhang.toPx() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.alpha = maskProgress.value * backgroundAlpha().coerceIn(0f, 1f)
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
                // FORK（FlyCat）：内缩透传与钉住模式。
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
