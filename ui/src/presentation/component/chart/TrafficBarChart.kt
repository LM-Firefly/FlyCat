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

package com.github.lmfirefly.flycat.presentation.component.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.core.util.format.formatBytes
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class BarChartItem(
    val label: String,
    val value: Long,
    val isHighlighted: Boolean = false,
)

@Composable
fun TrafficBarChart(
    items: List<BarChartItem>,
    modifier: Modifier = Modifier,
    maxDisplayValue: Long? = null,
    onItemClick: ((Int) -> Unit)? = null,
    selectedIndex: Int = -1,
    barColor: Color = MiuixTheme.colorScheme.primary.copy(alpha = AppTheme.opacity.medium),
    highlightColor: Color = MiuixTheme.colorScheme.primary,
    chartHeight: Dp = AppTheme.sizes.trafficChartHeight,
    barWidth: Dp = AppTheme.sizes.trafficBarWidth,
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val componentSizes = AppTheme.sizes

    val computedMaxValue = maxDisplayValue ?: items.maxOfOrNull { it.value } ?: 1L
    val safeMaxValue = if (computedMaxValue <= 0L) 1L else computedMaxValue

    val displayItems =
        remember(items) {
            if (items.size <= 7) {
                items + List(7 - items.size) { BarChartItem("", 0L) }
            } else {
                items.take(7)
            }
        }

    // Single Animatable replaces 9 animateFloatAsState calls for max + bar heights.
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(safeMaxValue, displayItems) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        )
    }
    val animatedMaxValue = safeMaxValue.toFloat() * animProgress.value

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(componentSizes.trafficBarLabelHeight),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatBytes(animatedMaxValue.toLong()),
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.height(spacing.space4))

        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            displayItems.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex || item.isHighlighted
                val isValidItem = item.label.isNotEmpty()

                val targetHeight =
                    if (animatedMaxValue > 0 && item.value > 0) {
                        (item.value.toFloat() / animatedMaxValue).coerceIn(0.04f, 1f)
                    } else {
                        0.04f
                    }

                val animatedHeight = if (isValidItem) targetHeight * animProgress.value else 0f

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (isValidItem && animatedHeight > 0f) {
                        Spacer(
                            modifier =
                                Modifier.width(barWidth)
                                    .fillMaxHeight(animatedHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = radii.radius4,
                                            topEnd = radii.radius4,
                                        )
                                    )
                                    .background(if (isSelected) highlightColor else barColor)
                                    .then(
                                        if (onItemClick != null) {
                                            Modifier.clickable { onItemClick(index) }
                                        } else {
                                            Modifier
                                        }
                                    )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.space8))

        Row(
            modifier = Modifier.fillMaxWidth().height(componentSizes.trafficBarLabelHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            displayItems.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex || item.isHighlighted
                Text(
                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally),
                    text = item.label,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 9.sp),
                    color =
                        if (isSelected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

data class TimeSlotTrafficItem(
    val slotIndex: Int,
    val upload: Long,
    val download: Long,
)

@Composable
fun TimeSlotBarChart(
    items: List<TimeSlotTrafficItem>,
    labels: List<String>,
    downloadColor: Color,
    uploadColor: Color,
    averageLineColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = UiDp.dp200,
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val componentSizes = AppTheme.sizes
    val maxValue = remember(items) {
        items.maxOfOrNull { it.upload + it.download }?.coerceAtLeast(1L) ?: 1L
    }
    val averageValue = remember(items) {
        val nonZero = items.filter { it.upload + it.download > 0L }
        if (nonZero.isEmpty()) 0L
        else nonZero.sumOf { it.upload + it.download } / nonZero.size
    }

    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(items) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .width(componentSizes.speedChartBarCornerRadius)
                    .height(componentSizes.speedChartBarCornerRadius)
                    .padding(end = spacing.space4),
            )
            Canvas(modifier = Modifier.width(UiDp.dp12).height(UiDp.dp4)) {
                drawRoundRect(
                    color = downloadColor,
                    cornerRadius = CornerRadius(radii.radius4.toPx()),
                    size = size,
                )
            }
            Spacer(modifier = Modifier.width(spacing.space4))
            Text(
                text = FlyTxt.TrafficStatistics.Metric.Download,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.width(spacing.space12))
            Canvas(modifier = Modifier.width(UiDp.dp12).height(UiDp.dp4)) {
                drawRoundRect(
                    color = uploadColor,
                    cornerRadius = CornerRadius(radii.radius4.toPx()),
                    size = size,
                )
            }
            Spacer(modifier = Modifier.width(spacing.space4))
            Text(
                text = FlyTxt.TrafficStatistics.Metric.Upload,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(spacing.space8))
        // Chart
        var selectedIndex by remember { mutableIntStateOf(-1) }
        var selectedOffsetY by remember { mutableStateOf(0f) }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val containerWidth = maxWidth
            val density = LocalDensity.current
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(items.size) {
                        detectTapGestures { offset ->
                            val slotWidth = size.width.toFloat() / items.size
                            val tappedIndex = (offset.x / slotWidth).toInt()
                                .coerceIn(0, items.size - 1)
                            selectedIndex = if (selectedIndex == tappedIndex) -1 else tappedIndex
                            selectedOffsetY = offset.y
                        }
                    },
            ) {
                val barAreaWidth = size.width
                val slotWidth = barAreaWidth / items.size
                val barWidthPx = slotWidth * 0.55f
                val cornerRadiusPx = (barWidthPx / 4f).coerceAtMost(UiDp.dp10.toPx())
                val progress = animationProgress.value
                // Average line
                if (averageValue > 0) {
                    val avgY = size.height * (1f - averageValue.toFloat() / maxValue.toFloat())
                    drawLine(
                        color = averageLineColor,
                        start = Offset(0f, avgY),
                        end = Offset(size.width, avgY),
                        strokeWidth = UiDp.dp1.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(UiDp.dp6.toPx(), UiDp.dp4.toPx()),
                        ),
                    )
                }
                // Stacked bars
                items.forEachIndexed { index, item ->
                    val hasUpload = item.upload > 0L
                    val hasDownload = item.download > 0L
                    val total = item.upload + item.download
                    val isSelected = selectedIndex == index
                    val barAlpha = if (selectedIndex < 0 || isSelected) 1f else 0.3f
                    val centerX = slotWidth * index + slotWidth / 2f
                    val left = centerX - barWidthPx / 2f
                    if (total <= 0L) {
                        // Empty slot 鈥?small default pill bar
                        val defaultHeight = (size.height * 0.03f).coerceAtLeast(cornerRadiusPx * 2)
                        val r = cornerRadiusPx.coerceAtMost(defaultHeight / 2f)
                        val pillColor = downloadColor.copy(alpha = barAlpha * 0.25f)
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(left, size.height - defaultHeight),
                            size = Size(barWidthPx, defaultHeight),
                            cornerRadius = CornerRadius(r, r),
                        )
                        return@forEachIndexed
                    }
                    val totalHeight = (total.toFloat() / maxValue.toFloat()) * size.height * progress
                    val downloadHeight = if (hasDownload && hasUpload) {
                        totalHeight * (item.download.toFloat() / total.toFloat())
                    } else if (hasDownload) totalHeight else 0f
                    val uploadHeight = totalHeight - downloadHeight
                    // Download (bottom) 鈥?plain rect only when upload sits above
                    if (hasDownload && hasUpload && downloadHeight > 0.5f) {
                        drawRect(
                            color = downloadColor.copy(alpha = barAlpha),
                            topLeft = Offset(left, size.height - downloadHeight),
                            size = Size(barWidthPx, downloadHeight),
                        )
                    }
                    // Upload (top segment) 鈥?rounded top only, flat bottom
                    if (hasUpload && uploadHeight > 0.5f) {
                        val uploadTop = size.height - totalHeight
                        val r = cornerRadiusPx.coerceAtMost(uploadHeight / 2f)
                        val right = left + barWidthPx
                        val bottom = uploadTop + uploadHeight
                        val path = Path().apply {
                            moveTo(left, bottom)
                            lineTo(left, uploadTop + r)
                            cubicTo(left, uploadTop, left, uploadTop, left + r, uploadTop)
                            lineTo(right - r, uploadTop)
                            cubicTo(right, uploadTop, right, uploadTop, right, uploadTop + r)
                            lineTo(right, bottom)
                            close()
                        }
                        drawPath(path, color = uploadColor.copy(alpha = barAlpha), style = Fill)
                    }
                    // Only download 鈥?rounded top only, flat bottom
                    if (hasDownload && !hasUpload && downloadHeight > 0.5f) {
                        val r = cornerRadiusPx.coerceAtMost(downloadHeight / 2f)
                        val right = left + barWidthPx
                        val top = size.height - downloadHeight
                        val bottom = size.height
                        val path = Path().apply {
                            moveTo(left, bottom)
                            lineTo(left, top + r)
                            cubicTo(left, top, left, top, left + r, top)
                            lineTo(right - r, top)
                            cubicTo(right, top, right, top, right, top + r)
                            lineTo(right, bottom)
                            close()
                        }
                        drawPath(path, color = downloadColor.copy(alpha = barAlpha), style = Fill)
                    }
                }
            }
            // Tooltip for selected bar
            if (selectedIndex in items.indices && items[selectedIndex].upload + items[selectedIndex].download > 0L) {
                val selectedItem = items[selectedIndex]
                val selectedLabel = labels.getOrElse(selectedIndex) { "" }
                val slotWidthDp = containerWidth / items.size
                val tapDp = with(density) { selectedOffsetY.toDp() }
                val pad = 14.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (slotWidthDp * selectedIndex + slotWidthDp / 2 - 48.dp)
                                .coerceIn(pad, containerWidth - 68.dp - pad),
                            y = tapDp.coerceIn(pad, chartHeight - 68.dp - pad),
                        )
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(radii.radius8),
                            ambientColor = Color.Black.copy(alpha = 0.42f),
                            spotColor = Color.Black.copy(alpha = 0.42f),
                        )
                        .clip(RoundedCornerShape(radii.radius8))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = spacing.space12, vertical = spacing.space8),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(IntrinsicSize.Max)) {
                        Text(
                            text = selectedLabel,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(spacing.space2))
                        TooltipRow(
                            label = FlyTxt.TrafficStatistics.Metric.Upload,
                            value = formatBytes(selectedItem.upload),
                            valueColor = uploadColor,
                        )
                        TooltipRow(
                            label = FlyTxt.TrafficStatistics.Metric.Download,
                            value = formatBytes(selectedItem.download),
                            valueColor = downloadColor,
                        )
                        TooltipRow(
                            label = FlyTxt.TrafficStatistics.Metric.Total,
                            value = formatBytes(selectedItem.upload + selectedItem.download),
                            valueColor = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(spacing.space4))
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally),
                    text = label,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 8.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TooltipRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 9.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 9.sp),
            color = valueColor,
        )
    }
}
