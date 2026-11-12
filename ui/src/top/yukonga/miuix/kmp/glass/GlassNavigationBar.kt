// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * [GlassNavigationBar] 中的一个目标。
 *
 * @property icon 此目标的图标。
 * @property label 图标下方的可选标题。
 * @property contentDescription 屏幕阅读器朗读的无障碍描述。
 */
@Immutable
data class GlassNavigationItem(
    val icon: ImageVector,
    val label: String? = null,
    val contentDescription: String? = null,
)

/** [GlassNavigationBar] 的默认值。 */
object GlassNavigationBarDefaults {

    /** 最小高度；换行的标题可以让条更高。 */
    val Height: Dp = 54.dp

    /** 玻璃胶囊与可选尾随操作之间的间距。 */
    val ActionSpacing: Dp = 12.dp

    /** 图标尺寸。 */
    val IconSize: Dp = 25.dp

    /** 标题字号，单位 dp。 */
    val LabelSize: Dp = 11.dp

    /** 系统字体缩放达到 1.6 时使用的标题字号。 */
    val LargeLabelSize: Dp = 16.dp

    /** 每个图标-标题组上下的间距。 */
    val ContentPaddingVertical: Dp = 6.dp

    /** 图标与其标题之间的间隙。 */
    val LabelSpacing: Dp = 0.dp

    /** 条的上下边缘与指示器之间的内距。 */
    val IndicatorPaddingVertical: Dp = 3.dp

    /** 条自身的水平内距：各目标从此处开始排布。 */
    val ContentPaddingHorizontal: Dp = 8.dp

    /** 胶囊在每一侧超出其目标所占条宽的距离。 */
    val IndicatorOverhang: Dp = 5.dp

    /** 手指之下目标的不透明度。 */
    val PressedAlpha: Float = 0.6f

    /** 沿条描出的边缘光。 */
    @Composable
    fun stroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 条自身的主体。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.puredThinGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 静止时指示器的填充；仅在动态取色主题中使用主要色。 */
    @Composable
    fun indicatorColor(): Color = indicatorTint().copy(
        alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.12f else 0.06f,
    )

    /** 按压时指示器的填充；以更高不透明度使用同一主题色调。 */
    @Composable
    fun indicatorPressedColor(): Color = indicatorTint().copy(
        alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.26f else 0.16f,
    )

    @Composable
    private fun indicatorTint(): Color = when {
        MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.primary
        MiuixTheme.colorScheme.background.luminance() < 0.5f -> Color.White
        else -> Color.Black
    }
}

/**
 * 玻璃上的浮动底部条。
 *
 * 拖动时指示器跟随手指，释放时从其当前位置与速度归位，且永不过冲到最外侧目标之外。
 *
 * @param items 各目标，按顺序排列。
 * @param selectedIndex 当前目标的索引。
 * @param onSelect 以手指之下的索引回调，在按下与拖动期间触发。
 * @param backdrop 提供玻璃背后内容的 [Backdrop]。`null` 禁用采样，
 *   并以主题的 surfaceContainer 颜色填充胶囊。
 * @param modifier 应用于条的修饰符。
 * @param defaultWindowInsetsPadding 是否将默认的浮动底部边距与导航栏/标题栏嵌入应用到
 *   整个组。调用方自行处理时请禁用。
 * @param style 玻璃材质。
 * @param shape 条的轮廓。默认为 [height] 高的胶囊形。
 * @param alpha 材质的不透明度乘数。
 * @param visible 条是否在屏幕上。关闭它并非简单隐藏：条会同时收缩、淡出并模糊消失，
 *   稍作延迟后再回来。
 * @param stroke 沿边缘的可选泛光描边。
 * @param shadow 浮动胶囊投下的阴影。`null` 表示移除。
 * @param height 条的最小高度。两行或大字体标题时内容可以变高。
 * @param material 采样 backdrop 时条自身的主体。`null` 表示省略显式材质层，
 *   而不是禁用采样。[backdrop] 为 `null` 时忽略。
 * @param indicatorPressedColor 手指按住时胶囊的填充。
 * @param indicatorColor 选中目标之后胶囊的填充。
 * @param selectedColor 选中图标与标签的色调。动态取色主题使用主要色；其他主题使用 onBackground。
 * @param unselectedColor 未选中图标与标签的色调，不附加压暗。
 * @param trailingAction 玻璃胶囊之外的可选独立操作。它自行管理尺寸与材质，
 *   并与条的显示/隐藏过渡同步。添加或移除它会动画其尺寸、不透明度与预留空间。
 *   圆形操作请使用 [GlassIconButton]。
 */
@Composable
fun GlassNavigationBar(
    items: List<GlassNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    defaultWindowInsetsPadding: Boolean = true,
    style: GlassStyle = GlassDefaults.Style,
    shape: GlassShape = GlassShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50)),
    alpha: Float = 1f,
    visible: Boolean = true,
    stroke: EdgeStroke? = GlassNavigationBarDefaults.stroke(),
    shadow: GlassShadow? = GlassShadows.Float,
    height: Dp = GlassNavigationBarDefaults.Height,
    material: GlassMaterial? = GlassNavigationBarDefaults.material(),
    indicatorColor: Color = GlassNavigationBarDefaults.indicatorColor(),
    indicatorPressedColor: Color = GlassNavigationBarDefaults.indicatorPressedColor(),
    selectedColor: Color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
    unselectedColor: Color = MiuixTheme.colorScheme.onBackground,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val index = selectedIndex.coerceIn(0, items.lastIndex)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val labelSize = with(density) {
        if (fontScale >= 1.6f) GlassNavigationBarDefaults.LargeLabelSize.toSp() else GlassNavigationBarDefaults.LabelSize.toSp()
    }
    val contentInset = with(density) { GlassNavigationBarDefaults.ContentPaddingHorizontal.toPx() }
    val overhang = with(density) { GlassNavigationBarDefaults.IndicatorOverhang.toPx() }

    var barWidth by remember { mutableIntStateOf(0) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val dragging = remember { mutableStateOf(false) }

    val showScale = remember { Animatable(if (visible) 1f else GlassMotion.NAV_HIDE_SCALE) }
    var interactionEnabled by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            interactionEnabled = true
            delay(GlassMotion.NAV_SHOW_DELAY_MS.milliseconds)
            showScale.animateTo(1f, GlassMotion.navShowHide())
        } else {
            showScale.animateTo(GlassMotion.NAV_HIDE_SCALE, GlassMotion.navShowHide())
            interactionEnabled = false
        }
    }

    val scope = rememberCoroutineScope()
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    fun animateIndicator(
        newLeft: Float,
        newRight: Float,
        leftSpring: SpringSpec<Float> = GlassMotion.edgeSpring(newLeft <= left.value),
        rightSpring: SpringSpec<Float> = GlassMotion.edgeSpring(newLeft > left.value),
    ) {
        // 所有运动共用这两个 Animatable。通过 animateTo 重新定向，使当前位置与速度在拖动、
        // 反转、选择和释放等打断中得以保留。这些任务属于组合，而不是一个会重启的选择效果。
        scope.launch { left.animateTo(newLeft, leftSpring) }
        scope.launch { right.animateTo(newRight, rightSpring) }
    }

    val slot = if (barWidth <= 0) 0f else (barWidth - contentInset * 2f) / items.size
    val chipWidth = slot + overhang * 2f
    val targetLeft = contentInset - overhang + index * slot
    val targetRight = targetLeft + chipWidth

    LaunchedEffect(targetLeft, targetRight, dragging.value) {
        if (slot <= 0f) return@LaunchedEffect
        if (dragging.value) return@LaunchedEffect
        if (right.value <= left.value) {
            left.snapTo(targetLeft)
            right.snapTo(targetRight)
            return@LaunchedEffect
        }
        animateIndicator(targetLeft, targetRight)
    }

    val chipHeldPx = with(density) {
        (height - GlassNavigationBarDefaults.IndicatorPaddingVertical * 2).toPx()
    }
    val chipPressed = pressedIndex >= 0
    val chipScale by animateFloatAsState(
        targetValue = if (chipPressed) glassPressScale(chipHeldPx, density.density) else 1f,
        animationSpec = if (chipPressed) GlassMotion.pressDown() else GlassMotion.pressUp(),
        label = "glassNavigationIndicatorPress",
    )
    val chipFill = animateColorAsState(
        targetValue = if (chipPressed) indicatorPressedColor else indicatorColor,
        animationSpec = if (chipPressed) GlassMotion.navPressEnter() else GlassMotion.navPressExit(),
        label = "glassNavigationIndicatorFill",
    )

    val onSelectState by rememberUpdatedState(onSelect)
    val bottomPadding = if (defaultWindowInsetsPadding) {
        val navigationBarBottom = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            .asPaddingValues().calculateBottomPadding()
        val floatingMargin = if (platform() == Platform.IOS) {
            36.dp
        } else if (navigationBarBottom == 0.dp) {
            32.dp
        } else {
            16.dp + navigationBarBottom
        }
        val captionBarBottom = WindowInsets.captionBar.only(WindowInsetsSides.Bottom)
            .asPaddingValues().calculateBottomPadding()
        val animatedCaptionBarHeight by animateDpAsState(
            targetValue = captionBarBottom.coerceAtLeast(0.dp),
            animationSpec = tween(durationMillis = 300),
            label = "glassNavigationCaptionBarHeight",
        )
        floatingMargin + animatedCaptionBarHeight
    } else {
        0.dp
    }
    GlassNavigationBarLayout(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .graphicsLayer {
                val show = showScale.value
                scaleX = show
                scaleY = show
                this.alpha = ((show - GlassMotion.NAV_HIDE_SCALE) / (1f - GlassMotion.NAV_HIDE_SCALE))
                    .coerceIn(0f, 1f)
            }
            .blur(
                radius = (
                    (1f - ((showScale.value - GlassMotion.NAV_HIDE_SCALE) / (1f - GlassMotion.NAV_HIDE_SCALE)))
                        .coerceIn(0f, 1f) * GlassMotion.NAV_HIDE_BLUR_DP
                    ).dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            ),
        trailingAction = trailingAction?.let { action ->
            {
                Box(
                    modifier = Modifier
                        .then(if (interactionEnabled) Modifier else Modifier.clearAndSetSemantics { })
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (interactionEnabled) placeable.placeRelative(0, 0)
                            }
                        },
                ) { action() }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .glassPanel(
                    backdrop = backdrop,
                    shape = shape,
                    style = style,
                    alpha = alpha,
                    material = material,
                    stroke = stroke,
                    shadow = shadow,
                    shading = false,
                    fallback = Modifier.clip(shape).background(MiuixTheme.colorScheme.surfaceContainer),
                )
                .onSizeChanged { barWidth = it.width }
                .then(
                    if (interactionEnabled) {
                        Modifier.pointerInput(items.size, contentInset, overhang, layoutDirection) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val selectionWidth = size.width.toFloat()
                                if (selectionWidth <= contentInset * 2f) return@awaitEachGesture
                                // 冻结本次手势的命中区域：切换目标可能改变胶囊尺寸，
                                // 不应因此选中另一个目标。
                                fun logicalX(x: Float) = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                                fun indexAt(x: Float): Int {
                                    val span = (selectionWidth - contentInset * 2f) / items.size
                                    return if (span <= 0f) {
                                        0
                                    } else {
                                        ((x - contentInset) / span).toInt().coerceIn(0, items.lastIndex)
                                    }
                                }

                                val minLeft = contentInset - overhang
                                val downX = logicalX(down.position.x)
                                var selectionX = downX
                                var current = indexAt(downX)
                                pressedIndex = current
                                onSelectState(current)
                                var grabOffset = Float.NaN
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed || change.isConsumed) break
                                        // 手势仍在进行时，目标可能添加或移除尾随操作。
                                        // 读取其当前几何。
                                        val width = size.width.toFloat()
                                        val chipW = (width - contentInset * 2f) / items.size + overhang * 2f
                                        val maxLeft = (width - chipW).coerceAtLeast(0f)
                                        val x = logicalX(change.position.x)
                                        // 两个位置都使用当前局部坐标，因此其增量不包含
                                        // 胶囊自身定位造成的移动。
                                        val pointerDelta = change.position.x - change.previousPosition.x
                                        val delta = if (layoutDirection == LayoutDirection.Rtl) -pointerDelta else pointerDelta
                                        selectionX += delta
                                        if (grabOffset.isNaN()) {
                                            if (abs(selectionX - downX) < viewConfiguration.touchSlop) {
                                                pressedIndex = indexAt(selectionX)
                                                continue
                                            }
                                            val bounds = navigationIndicatorBounds(left.value, right.value, width, minLeft)
                                            grabOffset = x - bounds.x
                                            dragging.value = true
                                        }
                                        val chipLeft = (x - grabOffset).coerceIn(0f, maxLeft)
                                        val next = indexAt(selectionX)
                                        val target = navigationDragTarget(chipLeft, chipW, width, delta, next != current)
                                        animateIndicator(target.left, target.right, target.leftSpring, target.rightSpring)
                                        if (next != current) {
                                            current = next
                                            onSelectState(next)
                                        }
                                        pressedIndex = next
                                        change.consume()
                                    }
                                } finally {
                                    pressedIndex = -1
                                    dragging.value = false
                                    // 释放时用最新尺寸重启选择效果。不要依据按下时
                                    // 捕获的几何进行归位。
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = GlassNavigationBarDefaults.IndicatorPaddingVertical)
                    .layout { measurable, constraints ->
                        // 每帧都会对两条边作钳制，而不只是在目标处。保持弹簧存活，
                        // 使另一条边仍能回弹。
                        val bounds = navigationIndicatorBounds(
                            left.value,
                            right.value,
                            constraints.maxWidth.toFloat(),
                            contentInset - overhang,
                        )
                        val start = bounds.x.roundToInt()
                        val width = (bounds.y.roundToInt() - start).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(IntOffset(start, 0))
                        }
                    }
                    .graphicsLayer {
                        scaleX = chipScale
                        scaleY = chipScale
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .drawBehind { drawRect(chipFill.value) },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = GlassNavigationBarDefaults.ContentPaddingHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { position, item ->
                    key(item) {
                        val selected = position == index
                        val tint by animateColorAsState(
                            targetValue = if (selected) selectedColor else unselectedColor,
                            animationSpec = GlassMotion.navContent(),
                            label = "glassNavigationTint",
                        )
                        val pressAlpha = animateFloatAsState(
                            targetValue = if (position == pressedIndex) {
                                GlassNavigationBarDefaults.PressedAlpha
                            } else {
                                1f
                            },
                            animationSpec = GlassMotion.navContentFloat(),
                            label = "glassNavigationPress",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    horizontal = GlassNavigationBarDefaults.IndicatorOverhang,
                                    vertical = GlassNavigationBarDefaults.ContentPaddingVertical,
                                )
                                .then(
                                    if (interactionEnabled) {
                                        Modifier
                                            .semantics(mergeDescendants = true) {
                                                this.role = Role.Tab
                                                this.selected = selected
                                                (item.contentDescription ?: item.label)?.let {
                                                    this.contentDescription = it
                                                }
                                                onClick {
                                                    onSelectState(position)
                                                    true
                                                }
                                            }
                                            .onKeyEvent { event ->
                                                val activationKey = event.key == Key.Enter ||
                                                    event.key == Key.NumPadEnter ||
                                                    event.key == Key.Spacebar
                                                if (activationKey) {
                                                    if (event.type == KeyEventType.KeyUp) onSelectState(position)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .focusable()
                                    } else {
                                        Modifier.clearAndSetSemantics { }
                                    },
                                )
                                .graphicsLayer { this.alpha = pressAlpha.value },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(GlassNavigationBarDefaults.LabelSpacing),
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(GlassNavigationBarDefaults.IconSize),
                                    tint = tint,
                                )
                                if (item.label != null) {
                                    Text(
                                        text = item.label,
                                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = labelSize),
                                        color = tint,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
