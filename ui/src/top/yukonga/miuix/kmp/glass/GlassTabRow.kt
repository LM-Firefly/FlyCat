// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.drawGlassRim
import top.yukonga.miuix.kmp.material.withUnderlayBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * tab 在各交互状态下使用的颜色。
 *
 * @property selectedContainerColor 选中 tab 的填充色。
 * @property selectedContentColor 选中 tab 的文字颜色。
 * @property restingContainerColor 材质出现之前，未选中 tab 的填充色。
 * @property containerColor 材质出现之后，未选中 tab 的填充色。[Color.Transparent] 让玻璃本体透出。
 * @property contentColor 未选中 tab 的文字颜色。
 * @property pressedOverlayColor 手指按下的 tab 上的叠加层。
 * @property restingStrokeAlpha 材质出现之前额外的边缘描边强度。
 */
@Immutable
data class GlassTabColors(
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val restingContainerColor: Color,
    val containerColor: Color,
    val contentColor: Color,
    val pressedOverlayColor: Color,
    val restingStrokeAlpha: Float,
)

/** [GlassTabRow] 的默认值。 */
object GlassTabRowDefaults {

    /** tab 的高度。 */
    val Height: Dp = 40.dp

    /** 与 [neutralColors] 搭配的较矮高度。 */
    val NeutralHeight: Dp = 35.dp

    /** 两个 tab 之间的间距。 */
    val TabGap: Dp = 12.dp

    /** tab 内部的水平内边距。 */
    val TabPaddingHorizontal: Dp = 16.dp

    /** tab 内部的垂直内边距。 */
    val TabPaddingVertical: Dp = 6.dp

    /** 材质升起之前，未选中 tab 在暗色页面上承载的白色强度。 */
    private val RestingAlpha: Float = 0.067f

    /** 每个 tab 都是胶囊形：999dp 的圆角半径，即真正的半圆端帽。 */
    @Composable
    fun shape(height: Dp = Height): GlassShape = GlassShape(height / 2, smoothing = 0f)

    /** `filter-sort-view2-*-glass`：20dp 蒙版模糊，配 pured-thin-glass 颜色层。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.puredThinGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 选中与未选中 tab 的泛光描边。 */
    @Composable
    fun stroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /**
     * 强调色配色：选中 tab 承载主题自身的颜色，两种主题下相同。
     * 材质应用于每个 tab；未选中 tab 清空背景，使玻璃保持可见。
     */
    @Composable
    fun primaryColors(
        selectedContainerColor: Color = MiuixTheme.colorScheme.primary,
        containerColor: Color = Color.Transparent,
    ): GlassTabColors {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        return GlassTabColors(
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color.White,
            restingContainerColor = if (isDark) {
                Color.White.copy(alpha = RestingAlpha).compositeOver(MiuixTheme.colorScheme.surface)
            } else {
                Color.White
            },
            containerColor = containerColor,
            contentColor = if (isDark) {
                Color.White.copy(alpha = 0.5f)
            } else {
                Color.Black.copy(alpha = 0.6f)
            },
            pressedOverlayColor = if (isDark) {
                Color.White.copy(alpha = 0.16f)
            } else {
                Color.Black.copy(alpha = 0.05f)
            },
            restingStrokeAlpha = 1f,
        )
    }

    /**
     * 中性配色：选中 tab 使用强烈的中性色而非主题色，适用于内容本身已带强调色的页面。
     *
     * 它与强调色配色走同一条过渡曲线：静止时未选中 tab 是页面上的一层不透明灰色，材质升起后则把这块底色交给玻璃，自身变为透明。
     */
    @Composable
    fun neutralColors(): GlassTabColors {
        val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
        val page = MiuixTheme.colorScheme.surface
        val fill = (if (isDark) Color.White else Color.Black).copy(alpha = 0.06f).compositeOver(page)
        return GlassTabColors(
            selectedContainerColor = if (isDark) Color(0xFFE6E6E6) else Color(0xFF383838),
            selectedContentColor = if (isDark) Color.Black else Color.White,
            restingContainerColor = fill,
            containerColor = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onSurface,
            pressedOverlayColor = if (isDark) {
                Color.White.copy(alpha = 0.16f)
            } else {
                Color.Black.copy(alpha = 0.05f)
            },
            restingStrokeAlpha = 0f,
        )
    }
}

/**
 * 一排 tab，每个都是独立的胶囊，彼此之间没有游动的指示条。
 *
 * 自带两套配色，且不可互换：
 * [GlassTabRowDefaults.primaryColors] 用主题自身的颜色，
 * [GlassTabRowDefaults.neutralColors] 适用于内容本身已带强调色的页面。
 *
 * @param tabs 各 tab 的文字标签，按顺序排列。每个 tab 均分整行的宽度。
 * @param selectedIndex 当前 tab 的索引。
 * @param onSelect 点击某个 tab 时回调其索引。
 * @param backdrop 提供材质的 [Backdrop]。在 [GlassTopAppBar] 内部，优先使用顶栏自身的 backdrop 与有效模糊半径。在顶栏之外传 `null` 则使用静止填充色。
 * @param modifier 应用于整行的修饰符。
 * @param style 模糊样式，仅在 [material] 为 null 时使用。
 * @param alpha 材质颜色层、泛光描边与阴影的不透明度乘数。
 * @param surfaceAlpha 材质升起的程度，0 到 1。应由页面遮盖状态驱动，而非标题的展开状态。
 * @param stroke 沿每个 tab 边缘的可选泛光描边。默认为 Middle。
 * @param shadow 每个 tab 投下的阴影。`null` 表示移除。
 * @param material 每个 tab 自身的主体。`null` 表示 tab 保持 [style] 所选的模糊。
 * @param height tab 的高度。[GlassTabRowDefaults.NeutralHeight] 与中性配色搭配。
 * @param tabGap 两个 tab 之间的间距。
 * @param colors tab 使用的颜色。
 */
@Composable
fun GlassTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.Style,
    alpha: Float = 1f,
    surfaceAlpha: Float = 1f,
    stroke: EdgeStroke? = GlassTabRowDefaults.stroke(),
    shadow: GlassShadow? = GlassShadows.Float,
    material: GlassMaterial? = GlassTabRowDefaults.material(),
    height: Dp = GlassTabRowDefaults.Height,
    tabGap: Dp = GlassTabRowDefaults.TabGap,
    colors: GlassTabColors = GlassTabRowDefaults.primaryColors(),
) {
    if (tabs.isEmpty()) return
    val index = selectedIndex.coerceIn(0, tabs.lastIndex)
    val shape = GlassTabRowDefaults.shape(height)
    val ramp = surfaceAlpha.coerceIn(0f, 1f)
    val topBarContext = LocalGlassTopAppBarContext.current
    val resolvedBackdrop = topBarContext?.backdrop ?: backdrop
    val underlayMaterial = topBarContext?.underlayMaterial
    val resolvedMaterial = material?.withUnderlayBlur(underlayMaterial)

    Row(
        modifier = modifier.height(height).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(tabGap),
    ) {
        tabs.forEachIndexed { position, label ->
            key(label) {
                val selected = position == index
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val target = animateColorAsState(
                    targetValue = if (selected) colors.selectedContainerColor else colors.containerColor,
                    animationSpec = GlassMotion.navContent(),
                    label = "glassTabContainer",
                )
                val content by animateColorAsState(
                    targetValue = if (selected) colors.selectedContentColor else colors.contentColor,
                    animationSpec = GlassMotion.navContent(),
                    label = "glassTabContent",
                )
                val overlay = animateColorAsState(
                    targetValue = if (pressed) colors.pressedOverlayColor else Color.Transparent,
                    animationSpec = if (pressed) GlassMotion.navPressEnter() else GlassMotion.navPressExit(),
                    label = "glassTabOverlay",
                )
                val rim = animateFloatAsState(
                    targetValue = if (selected) 0f else colors.restingStrokeAlpha,
                    animationSpec = GlassMotion.navContentFloat(),
                    label = "glassTabRim",
                )

                fun containerColor(): Color {
                    val settled = target.value
                    return if (selected) settled else lerp(colors.restingContainerColor, settled, ramp)
                }
                val materialSurface = if (resolvedBackdrop != null && underlayMaterial != null) {
                    Modifier
                        .glassShadow(shape, shadow, alpha * ramp)
                        .glassOnActionBar(
                            backdrop = resolvedBackdrop,
                            shape = shape,
                            style = style,
                            alpha = alpha * ramp,
                            material = resolvedMaterial,
                            underlayMaterial = underlayMaterial,
                            stroke = stroke,
                        )
                } else {
                    Modifier.glassPanel(
                        backdrop = resolvedBackdrop,
                        shape = shape,
                        style = style,
                        alpha = alpha * ramp,
                        material = resolvedMaterial,
                        stroke = stroke,
                        shadow = shadow,
                        shading = false,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(materialSurface)
                        .drawBehind {
                            drawRect(containerColor())
                            drawRect(overlay.value)
                        }
                        .drawWithContent {
                            drawContent()
                            val restingRim = alpha * (1f - ramp) * rim.value
                            if (restingRim > 0.001f) {
                                if (stroke != null) {
                                    drawEdgeStroke(shape, stroke, restingRim)
                                }
                                drawGlassRim(shape, style, containerColor(), restingRim)
                            }
                        }
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onSelect(position) }
                        .padding(
                            horizontal = GlassTabRowDefaults.TabPaddingHorizontal,
                            vertical = GlassTabRowDefaults.TabPaddingVertical,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (selected) {
                            MiuixTheme.textStyles.subtitle
                        } else {
                            MiuixTheme.textStyles.body2
                        },
                        color = content,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** [GlassSegmentedTabRow] 的默认值。 */
object GlassSegmentedTabRowDefaults {

    /** 轨道的高度：34dp 的 tab，上下各有一份 [TrackPadding]。 */
    val Height: Dp = 40.dp

    /** 轨道边缘与其 tab 之间的内缩。 */
    val TrackPadding: Dp = 3.dp

    /** 两个 tab 之间的间距。 */
    val TabGap: Dp = 8.dp

    /** tab 内部的水平内边距。 */
    val TabPaddingHorizontal: Dp = 16.dp

    /** 轨道是胶囊形，其中的指示器也是。 */
    @Composable
    fun shape(height: Dp = Height): GlassShape = GlassShape(height / 2, smoothing = 0f)

    /** `filter-sort-view-glass`：20dp 蒙版模糊，配 pured-thin-glass 颜色层。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.puredThinGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 轨道的泛光描边。 */
    @Composable
    fun stroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /**
     * 材质升起之前轨道的填充色。不透明，静止时没有任何东西能透过控件显出：页面之上叠一层 [IndicatorAlpha]。
     */
    @Composable
    fun restingTrackColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = IndicatorAlpha).compositeOver(MiuixTheme.colorScheme.surface)
    } else {
        Color.White
    }

    /**
     * 当前 tab 背后指示器的填充色：同样的层，再叠在轨道之上。两者必须取同一个数值，否则指示器会无法与轨道分离。
     */
    @Composable
    fun indicatorColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = IndicatorAlpha)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    /**
     * 一层轨道在暗色页面上承载的反色调强度：字节 alpha `0x22`，亮色一侧的对应值是 `0x0F`。
     */
    private val IndicatorAlpha: Float = 0.133f
}

/**
 * 一排 tab 连成一条轨道，内部有一个滑动的指示器。
 *
 * @param tabs 各 tab 的文字标签，按顺序排列。每个 tab 均分轨道的宽度。
 * @param selectedIndex 当前 tab 的索引。
 * @param onSelect 点击某个 tab 时回调其索引。
 * @param backdrop 提供材质的 [Backdrop]。在 [GlassTopAppBar] 内部，优先使用顶栏自身的 backdrop 与有效模糊半径。
 * @param modifier 应用于轨道的修饰符。
 * @param style 模糊样式，仅在 [material] 为 null 时使用。
 * @param alpha 材质颜色层、泛光描边与阴影的不透明度乘数。
 * @param surfaceAlpha 材质强度，0 到 1。为 0 时轨道还原为 [trackColor]；为 1 时材质完全可见。
 * 指示器与文字全程保持可见。
 * @param stroke 沿轨道边缘的可选泛光描边。默认为 Middle。
 * @param shadow 轨道投下的阴影。`null` 表示移除。
 * @param material 轨道自身的主体。`null` 表示保持 [style] 所选的模糊。
 * @param height 轨道的高度。
 * @param trackColor 材质升起之前轨道的填充色。
 * @param indicatorColor 当前 tab 背后指示器的填充色。
 * @param selectedContentColor 当前 tab 文字的颜色。
 * @param contentColor 其余 tab 文字的颜色。
 * @param tabIcon 每个 tab 文字前拼接的图标插槽（FORK），下标与 [tabs] 对应；`null` 时保持纯文字。
 */
@Composable
fun GlassSegmentedTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.Style,
    alpha: Float = 1f,
    surfaceAlpha: Float = 1f,
    stroke: EdgeStroke? = GlassSegmentedTabRowDefaults.stroke(),
    shadow: GlassShadow? = GlassShadows.Float,
    material: GlassMaterial? = GlassSegmentedTabRowDefaults.material(),
    height: Dp = GlassSegmentedTabRowDefaults.Height,
    trackColor: Color = GlassSegmentedTabRowDefaults.restingTrackColor(),
    indicatorColor: Color = GlassSegmentedTabRowDefaults.indicatorColor(),
    selectedContentColor: Color = MiuixTheme.colorScheme.onSurface,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    // FORK（FlyCat）：标签文字前拼接的图标插槽（如策略组图标），下标与 [tabs] 对应。
    tabIcon: (@Composable (Int) -> Unit)? = null,
) {
    if (tabs.isEmpty()) return
    val index = selectedIndex.coerceIn(0, tabs.lastIndex)
    val ramp = surfaceAlpha.coerceIn(0f, 1f)
    val topBarContext = LocalGlassTopAppBarContext.current
    val resolvedBackdrop = topBarContext?.backdrop ?: backdrop
    val underlayMaterial = topBarContext?.underlayMaterial
    val resolvedMaterial = material?.withUnderlayBlur(underlayMaterial)
    val trackShape = GlassSegmentedTabRowDefaults.shape(height)
    val innerHeight = height - GlassSegmentedTabRowDefaults.TrackPadding * 2
    val indicatorShape = GlassSegmentedTabRowDefaults.shape(innerHeight)

    var trackWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gap = with(density) { GlassSegmentedTabRowDefaults.TabGap.toPx() }
    val slot = if (trackWidth <= 0) 0f else (trackWidth - gap * (tabs.size - 1)) / tabs.size
    val targetLeft = index * (slot + gap)
    val offset = remember { Animatable(0f) }
    val span = remember { Animatable(0f) }

    LaunchedEffect(targetLeft, slot) {
        if (slot <= 0f) return@LaunchedEffect
        if (span.value <= 0f) {
            offset.snapTo(targetLeft)
            span.snapTo(slot)
            return@LaunchedEffect
        }
        launch { offset.animateTo(targetLeft, GlassMotion.default()) }
        launch { span.animateTo(slot, GlassMotion.default()) }
    }

    val materialSurface = if (underlayMaterial != null) {
        Modifier.glassOnActionBar(
            backdrop = resolvedBackdrop,
            shape = trackShape,
            style = style,
            alpha = alpha * ramp,
            material = resolvedMaterial,
            underlayMaterial = underlayMaterial,
            stroke = stroke,
            enabled = ramp > 0f,
        )
    } else {
        Modifier.glass(
            backdrop = resolvedBackdrop,
            shape = trackShape,
            style = style,
            alpha = alpha * ramp,
            material = resolvedMaterial,
            stroke = stroke,
            enabled = ramp > 0f,
            shading = false,
        )
    }

    Box(
        modifier = modifier
            .height(height)
            .glassShadow(trackShape, shadow, alpha * ramp)
            .clip(trackShape)
            .then(materialSurface)
            .background(trackColor.copy(alpha = trackColor.alpha * (1f - ramp)))
            .padding(GlassSegmentedTabRowDefaults.TrackPadding)
            .onSizeChanged { trackWidth = it.width },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val width = span.value.roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative(IntOffset(offset.value.roundToInt(), 0))
                    }
                }
                .clip(indicatorShape)
                .background(indicatorColor),
        )
        Row(
            modifier = Modifier.fillMaxHeight().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(GlassSegmentedTabRowDefaults.TabGap),
        ) {
            tabs.forEachIndexed { position, label ->
                key(label) {
                    val selected = position == index
                    val content by animateColorAsState(
                        targetValue = if (selected) selectedContentColor else contentColor,
                        animationSpec = GlassMotion.navContent(),
                        label = "glassSegmentedTabContent",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(indicatorShape)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = null,
                                indication = null,
                            ) { onSelect(position) }
                            .padding(horizontal = GlassSegmentedTabRowDefaults.TabPaddingHorizontal),
                        contentAlignment = Alignment.Center,
                    ) {
                        // FORK（FlyCat）：文字前拼接图标插槽；插槽为空/未渲染时等价纯文字。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tabIcon?.invoke(position)
                            Text(
                                text = label,
                                style = if (selected) {
                                    MiuixTheme.textStyles.subtitle
                                } else {
                                    MiuixTheme.textStyles.body2
                                },
                                color = content,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
