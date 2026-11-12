/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val SegmentedTabTextAnimationDurationMillis = 220
private const val SegmentedTabIndicatorAnimationDurationMillis = 280

private val SegmentedTabTextAnimationSpec = tween<Color>(durationMillis = SegmentedTabTextAnimationDurationMillis, easing = FastOutSlowInEasing)
private val SegmentedTabIndicatorAnimationSpec = tween<Dp>(durationMillis = SegmentedTabIndicatorAnimationDurationMillis, easing = FastOutSlowInEasing)
private val ScrollableSegmentedTabIndicatorAnimationSpec = tween<Float>(durationMillis = SegmentedTabIndicatorAnimationDurationMillis, easing = FastOutSlowInEasing)

/** zashboard 风格的分段标签页：40dp 高，13sp 字体，配色沿用底部栏——选中时主标签显示在半透明主色胶囊上，未选中时使用 onSurface 颜色，在 surfaceVariant 轨道上滑动。 */
@Composable
fun SegmentedTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // 默认为 [40.dp]；传入相同的高度值可与兄弟元素对齐（例如 42dp 的玻璃顶部栏）。
    tabHeight: Dp = 40.dp,
    // 默认采样全局壁纸层：有壁纸+shader 时渲染液态玻璃轨道，否则实色回退。
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    scrollable: Boolean = false,
    scrollState: ScrollState? = null,
    // 每个 tab 文字前拼接的图标插槽（如策略组图标），下标与 [tabs] 对应；不传时保持纯文字。
    tabIcon: (@Composable (Int) -> Unit)? = null,
) {
    if (tabs.isEmpty()) return
    if (scrollable) {
        ScrollableSegmentedTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
            tabHeight = tabHeight,
            backdrop = backdrop,
            scrollState = scrollState,
            tabIcon = tabIcon,
        )
        return
    }
    if (backdrop != null && isRuntimeShaderSupported()) {
        val colors = segmentedTabColors()
        GlassSegmentedTabRow(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            onSelect = onTabSelected,
            backdrop = backdrop,
            modifier = modifier,
            height = tabHeight,
            shadow = null,
            trackColor = colors.trackColor,
            indicatorColor = colors.selectedPillColor,
            selectedContentColor = colors.selectedTextColor,
            contentColor = colors.unselectedTextColor,
            tabIcon = tabIcon,
        )
        return
    }
    val colors = segmentedTabColors()
    val shape = RoundedCornerShape(100.dp)
    val indicatorInset = 4.dp
    val indicatorGap = 4.dp
    val textStyle = MiuixTheme.textStyles.main.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(tabHeight).clip(shape).background(colors.trackColor).padding(indicatorInset)) {
        val clampedSelectedTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
        val tabCount = tabs.size
        val tabWidth = remember(maxWidth, tabCount) { ((maxWidth - indicatorGap * (tabCount - 1)) / tabCount).coerceAtLeast(0.dp) }
        val targetOffset = remember(tabWidth, clampedSelectedTabIndex) { (tabWidth + indicatorGap) * clampedSelectedTabIndex }
        val indicatorOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = SegmentedTabIndicatorAnimationSpec,
            label = "segmented_tab_indicator_offset",
        )
        Box(modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).fillMaxHeight().clip(shape).background(colors.selectedPillColor))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(indicatorGap)) {
            tabs.forEachIndexed { index, tab ->
                SegmentedTabItem(
                    text = tab,
                    selected = clampedSelectedTabIndex == index,
                    colors = colors,
                    selectedTextStyle = textStyle,
                    unselectedTextStyle = textStyle,
                    shape = shape,
                    onClick = { onTabSelected(index) },
                    icon = { tabIcon?.invoke(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScrollableSegmentedTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
    tabHeight: Dp,
    backdrop: LayerBackdrop?,
    scrollState: ScrollState?,
    tabIcon: (@Composable (Int) -> Unit)? = null,
) {
    val colors = segmentedTabColors()
    val shape = GlassSegmentedTabRowDefaults.shape(tabHeight)
    val indicatorShape = GlassSegmentedTabRowDefaults.shape(tabHeight - GlassSegmentedTabRowDefaults.TrackPadding * 2)
    val selectedTextStyle = MiuixTheme.textStyles.subtitle
    val unselectedTextStyle = MiuixTheme.textStyles.body2
    val density = LocalDensity.current
    val resolvedScrollState = scrollState ?: rememberScrollState()
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
    val clampedSelectedTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val selectedBounds = tabBounds[clampedSelectedTabIndex]
    val indicatorLeft by animateFloatAsState(
        targetValue = selectedBounds?.left ?: 0f,
        animationSpec = ScrollableSegmentedTabIndicatorAnimationSpec,
        label = "scrollable_segmented_tab_indicator_left",
    )
    val indicatorWidth by animateFloatAsState(
        targetValue = selectedBounds?.width ?: 0f,
        animationSpec = ScrollableSegmentedTabIndicatorAnimationSpec,
        label = "scrollable_segmented_tab_indicator_width",
    )
    LaunchedEffect(tabs) { tabBounds.clear() }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(tabHeight).clip(shape).then(
            if (backdrop != null && isRuntimeShaderSupported()) {
                Modifier.glass(
                    backdrop = backdrop,
                    shape = shape,
                    style = GlassDefaults.Style,
                    material = GlassSegmentedTabRowDefaults.material(),
                    stroke = GlassSegmentedTabRowDefaults.stroke(),
                    shading = false,
                )
            } else { Modifier.background(colors.trackColor) }
        ).padding(GlassSegmentedTabRowDefaults.TrackPadding),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(
            clampedSelectedTabIndex,
            selectedBounds,
            resolvedScrollState.maxValue,
            viewportWidthPx,
        ) {
            if (selectedBounds != null) {
                val target = (selectedBounds.center.x - viewportWidthPx / 2f).roundToInt().coerceIn(0, resolvedScrollState.maxValue)
                resolvedScrollState.animateScrollTo(target)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().horizontalScroll(resolvedScrollState)) {
            Box(modifier = Modifier.fillMaxHeight()) {
                if (indicatorWidth > 0f) {
                    Box(modifier = Modifier.offset { IntOffset(indicatorLeft.roundToInt(), 0) }.width(with(density) { indicatorWidth.toDp() }).fillMaxHeight().clip(indicatorShape).background(colors.selectedPillColor))
                }
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(GlassSegmentedTabRowDefaults.TabGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        SegmentedTabItem(
                            text = tab,
                            selected = clampedSelectedTabIndex == index,
                            colors = colors,
                            selectedTextStyle = selectedTextStyle,
                            unselectedTextStyle = unselectedTextStyle,
                            shape = indicatorShape,
                            onClick = { onTabSelected(index) },
                            icon = { tabIcon?.invoke(index) },
                            horizontalPadding = GlassSegmentedTabRowDefaults.TabPaddingHorizontal,
                            onBoundsChanged = { bounds -> tabBounds[index] = bounds },
                        )
                    }
                }
            }
        }
    }
}

private data class SegmentedTabColors(
    val trackColor: Color,
    val selectedPillColor: Color,
    val selectedTextColor: Color,
    val unselectedTextColor: Color,
)

@Composable
private fun segmentedTabColors(): SegmentedTabColors {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity
    return SegmentedTabColors(
        trackColor = colorScheme.surfaceVariant,
        selectedPillColor = colorScheme.primary.copy(alpha = opacity.subtle),
        selectedTextColor = colorScheme.primary,
        // 未选中的标签使用突出的层级，而不是次要的层级。
        unselectedTextColor = colorScheme.onSurface.copy(alpha = opacity.prominentText),
    )
}

@Composable
private fun SegmentedTabItem(
    text: String,
    selected: Boolean,
    colors: SegmentedTabColors,
    selectedTextStyle: TextStyle,
    unselectedTextStyle: TextStyle,
    shape: Shape,
    onClick: () -> Unit,
    icon: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    onBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.selectedTextColor else colors.unselectedTextColor,
        animationSpec = SegmentedTabTextAnimationSpec,
        label = "segmented_tab_text_$text",
    )
    val boundsModifier = onBoundsChanged?.let { callback ->
        Modifier.onGloballyPositioned { coordinates ->
            callback(coordinates.boundsInParent())
        }
    } ?: Modifier
    Box(
        modifier = modifier.fillMaxHeight().then(boundsModifier).clip(shape).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ).padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            Text(
                text = text,
                style = if (selected) selectedTextStyle else unselectedTextStyle,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

/** 将分段的行固定在惰性列表的顶部。标签行的槽隙是透明的，在其下方滚动的行会透过来（玻璃语境下不再以实色条带遮挡，见下）。 */
fun LazyListScope.stickySegmentedTabs(
    key: Any,
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabHeight: Dp = 40.dp,
    // 行本身的水平内边距；传入 [0.dp] 可使行满宽显示。
    tabRowPadding: Dp = 12.dp,
) {
    stickyHeader(key = key) {
        // 玻璃语境下不再用实色 surface 遮挡（否则挡住壁纸），吸顶时由玻璃 tab 条自身浮显。
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            SegmentedTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                modifier = Modifier.padding(horizontal = tabRowPadding),
                tabHeight = tabHeight,
            )
        }
    }
}
