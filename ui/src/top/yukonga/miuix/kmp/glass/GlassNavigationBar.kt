// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * [GlassNavigationBar] 中的一个目的地。
 *
 * @property icon 为此目的地绘制的图标。
 * @property label 图标下方的可选说明文字。
 * @property contentDescription 屏幕阅读器为其播报的内容。
 */
@Immutable
data class GlassNavigationItem(
    val icon: ImageVector,
    val label: String? = null,
    val contentDescription: String? = null,
)

/** [GlassNavigationBar] 的默认值。 */
object GlassNavigationBarDefaults {

    /** 最小高度；换行的标签文字会使底栏更高。 */
    val Height: Dp = 54.dp

    /** 玻璃胶囊与可选尾部操作之间的间距。 */
    val ActionSpacing: Dp = 12.dp

    /** 图标的尺寸。 */
    val IconSize: Dp = 28.dp

    /** 标签文字尺寸（dp）。 */
    val LabelSize: Dp = 11.dp

    /** 系统字体缩放达到 1.6 时使用的标签文字尺寸。 */
    val LargeLabelSize: Dp = 16.dp

    /** 每个图标与标签文字组上下的间距。 */
    val ContentPaddingVertical: Dp = 6.dp

    /** 图标与其标签文字之间的间隙。 */
    val LabelSpacing: Dp = 0.dp

    /** 底栏顶部、底部与指示器之间的内缩。 */
    val IndicatorPaddingVertical: Dp = 3.dp

    /** 底栏自身的水平内缩：目的地从此处开始布局。 */
    val ContentPaddingHorizontal: Dp = 8.dp

    /** 胶囊在每一侧超出其目的地所占份额的距离。 */
    val IndicatorOverhang: Dp = 5.dp

    /** 手指下方目的地的不透明度。 */
    val PressedAlpha: Float = 0.6f

    /** 沿底栏描出的边框。 */
    @Composable
    fun stroke(): GlassStroke = GlassStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 底栏自身的主体。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.puredThinGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 胶囊的静止填充：同一颜色三个透明度中的第一个。 */
    @Composable
    fun indicatorColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    /** 手指按压下胶囊的填充：同一颜色、约两倍透明度。 */
    @Composable
    fun indicatorPressedColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.26f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }
}

/**
 * 玻璃上的悬浮底栏。
 *
 * 指示器在拖动时跟随指针，在释放时从当前位置和速度平滑落定，不会越过最外侧目的地。
 *
 * @param items 按顺序排列的目的地列表。
 * @param selectedIndex 当前目的地的索引。
 * @param onSelect 在按下和拖动时，以手指下方的索引被调用。
 * @param backdrop 提供玻璃背后内容的 [Backdrop]。
 * @param modifier 应用于底栏的修饰符。
 * @param style 玻璃材质。
 * @param shape 底栏的轮廓。默认为 [height] 的胶囊形状。
 * @param alpha 材质的不透明度倍数。
 * @param visible 底栏是否在屏幕上。关闭它并不会简单地隐藏：底栏会一起收缩、淡出并模糊消失，短暂延迟后再回来。
 * @param stroke 沿边缘的可选泛光描边。
 * @param shadow 悬浮胶囊投射的阴影。`null` 移除阴影。
 * @param height 底栏的最小高度。内容可以在两行或大字号标签时增长。
 * @param material 底栏自身的主体 —— 模糊半径及其上的颜色层。`null` 表示不叠加显式材质层，背景采样仍然保留。
 * @param indicatorPressedColor 手指按在胶囊上时的填充色。
 * @param indicatorColor 选中目的地后方胶囊的填充色。
 * @param selectedColor 选中图标和标签的着色。
 * @param unselectedColor 其他项目的着色。它们不会变暗；仅由指示器表明哪个是当前项。
 * @param trailingAction 玻璃胶囊外部的可选独立操作。它拥有自己的尺寸和材质，并共享底栏的显示/隐藏过渡。使用 [GlassIconButton] 实现圆形操作。
 */
@Composable
fun GlassNavigationBar(
    items: List<GlassNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.Style,
    shape: GlassShape = GlassShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50)),
    alpha: Float = 1f,
    visible: Boolean = true,
    stroke: GlassStroke? = GlassNavigationBarDefaults.stroke(),
    shadow: GlassShadow? = GlassShadows.Float,
    height: Dp = GlassNavigationBarDefaults.Height,
    material: GlassMaterial? = GlassNavigationBarDefaults.material(),
    indicatorColor: Color = GlassNavigationBarDefaults.indicatorColor(),
    indicatorPressedColor: Color = GlassNavigationBarDefaults.indicatorPressedColor(),
    selectedColor: Color = MiuixTheme.colorScheme.onBackground,
    unselectedColor: Color = MiuixTheme.colorScheme.onBackground,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val index = selectedIndex.coerceIn(0, items.lastIndex)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val labelSize = with(density) { if (fontScale >= 1.6f) GlassNavigationBarDefaults.LargeLabelSize.toSp() else GlassNavigationBarDefaults.LabelSize.toSp() }
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
            delay(GlassMotion.NAV_SHOW_DELAY_MS)
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
        // 所有动画共享同一对 Animatable。通过 animateTo 重新定位，使它们当前的位置和速度在拖动、反向、选择和释放等中断中得以保留。
        // 这些任务属于组合本身，而非某个会重启的选择副作用。
        scope.launch { left.animateTo(newLeft, leftSpring) }
        scope.launch { right.animateTo(newRight, rightSpring) }
    }
    val slot = if (barWidth <= 0) 0f else (barWidth - contentInset * 2f) / items.size
    val chipWidth = slot + overhang * 2f
    val targetLeft = contentInset - overhang + index * slot
    val targetRight = targetLeft + chipWidth
    LaunchedEffect(targetLeft, targetRight) {
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
    GlassNavigationBarLayout(
        modifier = modifier
            .fillMaxWidth()
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
                )
                .onSizeChanged { barWidth = it.width }
                .then(
                    if (interactionEnabled) {
                        Modifier.pointerInput(items.size, contentInset, overhang, layoutDirection) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val width = size.width.toFloat()
                                if (width <= contentInset * 2f) return@awaitEachGesture
                                fun logicalX(x: Float) = if (layoutDirection == LayoutDirection.Rtl) width - x else x
                                val span = (width - contentInset * 2f) / items.size
                                fun indexAt(x: Float) = if (span <= 0f) {
                                    0
                                } else {
                                    ((x - contentInset) / span).toInt().coerceIn(0, items.lastIndex)
                                }
                                val chipW = span + overhang * 2f
                                val minLeft = contentInset - overhang
                                val maxLeft = (width - chipW).coerceAtLeast(0f)
                                val downX = logicalX(down.position.x)
                                var lastX = downX
                                var current = indexAt(downX)
                                pressedIndex = current
                                onSelectState(current)
                                var grabOffset = Float.NaN
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed || change.isConsumed) break
                                        val x = logicalX(change.position.x)
                                        if (grabOffset.isNaN()) {
                                            if (abs(x - downX) < viewConfiguration.touchSlop) {
                                                pressedIndex = indexAt(x)
                                                continue
                                            }
                                            val bounds = navigationIndicatorBounds(left.value, right.value, width, minLeft)
                                            grabOffset = x - bounds.x
                                            lastX = x
                                            dragging.value = true
                                        }
                                        val chipLeft = (x - grabOffset).coerceIn(0f, maxLeft)
                                        val next = indexAt(x)
                                        val target = navigationDragTarget(chipLeft, chipW, width, x - lastX, next != current)
                                        animateIndicator(target.left, target.right, target.leftSpring, target.rightSpring)
                                        if (next != current) {
                                            current = next
                                            onSelectState(next)
                                        }
                                        pressedIndex = next
                                        lastX = x
                                        change.consume()
                                    }
                                } finally {
                                    pressedIndex = -1
                                    dragging.value = false
                                    if (!grabOffset.isNaN()) {
                                        val homeLeft = minLeft + current * span
                                        animateIndicator(homeLeft, homeLeft + chipW)
                                    }
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
                        // 两条边在每一帧都会被钳制，而不仅在目标处。保持弹簧处于活动状态，使另一条边仍能回弹。
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
