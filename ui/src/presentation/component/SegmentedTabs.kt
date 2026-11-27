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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val SegmentedTabTextAnimationDurationMillis = 220
private const val SegmentedTabIndicatorAnimationDurationMillis = 280

private val SegmentedTabTextAnimationSpec = tween<Color>(durationMillis = SegmentedTabTextAnimationDurationMillis, easing = FastOutSlowInEasing)
private val ScrollableSegmentedTabIndicatorAnimationSpec = tween<Float>(durationMillis = SegmentedTabIndicatorAnimationDurationMillis, easing = FastOutSlowInEasing)

private data class SegmentedTabStyle(val backdrop: Backdrop, val surfaceAlpha: Float, val trackColor: Color, val selectedPillColor: Color, val selectedTextColor: Color, val unselectedTextColor: Color)

@Composable
private fun rememberSegmentedTabStyle(): SegmentedTabStyle {
    val backdrop = LocalGlassBackdrop.current
    val fallbackBackdrop = rememberLayerBackdrop()
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity
    return remember(backdrop, fallbackBackdrop, colorScheme, opacity) {
        SegmentedTabStyle(
            backdrop = backdrop ?: fallbackBackdrop,
            surfaceAlpha = if (backdrop != null) 1f else 0f,
            trackColor = colorScheme.surfaceVariant,
            selectedPillColor = colorScheme.primary.copy(alpha = opacity.subtle),
            selectedTextColor = colorScheme.primary,
            unselectedTextColor = colorScheme.onSurface.copy(alpha = opacity.prominentText),
        )
    }
}

@Composable
fun AppSegmentedTabRow(tabs: List<String>, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier, tabHeight: Dp = 40.dp) {
    val style = rememberSegmentedTabStyle()
    GlassSegmentedTabRow(
        tabs = tabs,
        selectedIndex = selectedTabIndex,
        onSelect = onTabSelected,
        backdrop = style.backdrop,
        surfaceAlpha = style.surfaceAlpha,
        height = tabHeight,
        shadow = null,
        trackColor = style.trackColor,
        indicatorColor = style.selectedPillColor,
        selectedContentColor = style.selectedTextColor,
        contentColor = style.unselectedTextColor,
        modifier = modifier,
    )
}

/** 可横滑的图标标签页；官方滚动标签不提供图标插槽，因此保留此实现。 */
@Composable
fun ScrollableSegmentedTabRow(tabs: List<String>, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, tabIcon: @Composable (Int) -> Unit, modifier: Modifier = Modifier, tabHeight: Dp = 40.dp) {
    if (tabs.isEmpty()) return
    val style = rememberSegmentedTabStyle()
    val shape = GlassSegmentedTabRowDefaults.shape(tabHeight)
    val indicatorShape = GlassSegmentedTabRowDefaults.shape(tabHeight - GlassSegmentedTabRowDefaults.TrackPadding * 2)
    val selectedTextStyle = MiuixTheme.textStyles.subtitle
    val unselectedTextStyle = MiuixTheme.textStyles.body2
    val density = LocalDensity.current
    val resolvedScrollState = rememberScrollState()
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
    val clampedSelectedTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val selectedBounds = tabBounds[clampedSelectedTabIndex]
    val indicatorLeft by animateFloatAsState(targetValue = selectedBounds?.left ?: 0f, animationSpec = ScrollableSegmentedTabIndicatorAnimationSpec, label = "scrollable_segmented_tab_indicator_left")
    val indicatorWidth by animateFloatAsState(targetValue = selectedBounds?.width ?: 0f, animationSpec = ScrollableSegmentedTabIndicatorAnimationSpec, label = "scrollable_segmented_tab_indicator_width")
    LaunchedEffect(tabs) { tabBounds.clear() }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(tabHeight).clip(shape).then(
            if (style.surfaceAlpha > 0f && isRuntimeShaderSupported()) {
                Modifier.glass(
                    backdrop = style.backdrop,
                    shape = shape,
                    style = GlassDefaults.Style,
                    material = GlassSegmentedTabRowDefaults.material(),
                    stroke = GlassSegmentedTabRowDefaults.stroke(),
                    shading = false,
                )
            } else { Modifier.background(style.trackColor) }
        ).padding(GlassSegmentedTabRowDefaults.TrackPadding),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(clampedSelectedTabIndex, selectedBounds, resolvedScrollState.maxValue, viewportWidthPx) {
            if (selectedBounds != null) {
                val target = (selectedBounds.center.x - viewportWidthPx / 2f).roundToInt().coerceIn(0, resolvedScrollState.maxValue)
                resolvedScrollState.animateScrollTo(target)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().horizontalScroll(resolvedScrollState)) {
            Box(modifier = Modifier.fillMaxHeight()) {
                if (indicatorWidth > 0f) {
                    Box(modifier = Modifier.offset { IntOffset(indicatorLeft.roundToInt(), 0) }.width(with(density) { indicatorWidth.toDp() }).fillMaxHeight().clip(indicatorShape).background(style.selectedPillColor))
                }
                Row(modifier = Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(GlassSegmentedTabRowDefaults.TabGap), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { index, tab ->
                        SegmentedTabItem(
                            text = tab,
                            selected = clampedSelectedTabIndex == index,
                            style = style,
                            selectedTextStyle = selectedTextStyle,
                            unselectedTextStyle = unselectedTextStyle,
                            shape = indicatorShape,
                            onClick = { onTabSelected(index) },
                            icon = { tabIcon(index) },
                            horizontalPadding = GlassSegmentedTabRowDefaults.TabPaddingHorizontal,
                            onBoundsChanged = { bounds -> tabBounds[index] = bounds },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabItem(
    text: String,
    selected: Boolean,
    style: SegmentedTabStyle,
    selectedTextStyle: TextStyle,
    unselectedTextStyle: TextStyle,
    shape: Shape,
    onClick: () -> Unit,
    icon: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    onBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val textColor by animateColorAsState(targetValue = if (selected) style.selectedTextColor else style.unselectedTextColor, animationSpec = SegmentedTabTextAnimationSpec, label = "segmented_tab_text_$text")
    val boundsModifier = onBoundsChanged?.let { callback -> Modifier.onGloballyPositioned { coordinates -> callback(coordinates.boundsInParent()) } } ?: Modifier
    Box(
        modifier = modifier.fillMaxHeight().then(boundsModifier).clip(shape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(text = text, style = if (selected) selectedTextStyle else unselectedTextStyle, color = textColor, maxLines = 1)
        }
    }
}

/** 将分段标签行固定在惰性列表顶部；槽隙保持透明，供下方内容透出。 */
fun LazyListScope.stickySegmentedTabs(key: Any, tabs: List<String>, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, tabHeight: Dp = 40.dp, tabRowPadding: Dp = 12.dp) {
    stickyHeader(key = key) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            AppSegmentedTabRow(tabs = tabs, selectedTabIndex = selectedTabIndex, onTabSelected = onTabSelected, tabHeight = tabHeight, modifier = Modifier.padding(horizontal = tabRowPadding))
        }
    }
}
