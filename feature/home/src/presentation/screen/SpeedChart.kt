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

package com.github.lmfirefly.flycat.feature.home.presentation.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.lmfirefly.flycat.core.model.traffic.TrafficData
import com.github.lmfirefly.flycat.presentation.component.chart.TrafficChartConfig
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SPEED_CHART_SAMPLE_LIMIT = 24
private const val SPEED_CHART_IDLE_WAVE_AMPLITUDE = 0.022f
private const val SPEED_CHART_IDLE_WAVE_SPAN = 4f

@Composable
fun SpeedChart(
    speedHistory: List<TrafficData>,
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity
    val downloadColor = MiuixTheme.colorScheme.primary
    val uploadColor = Color(0xFF52C41A)
    val fractions = remember(speedHistory) { buildSpeedChartFractions(speedHistory = speedHistory) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isLifecycleStarted by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { isLifecycleStarted = true }
        isLifecycleStarted = false
    }
    val idlePhase = remember { Animatable(0f) }
    val shouldAnimateIdle = isActive && !isRunning && isLifecycleStarted
    LaunchedEffect(shouldAnimateIdle) {
        if (shouldAnimateIdle) {
            while (true) {
                idlePhase.snapTo(0f)
                idlePhase.animateTo(
                    targetValue = SPEED_CHART_SAMPLE_LIMIT.toFloat(),
                    animationSpec = tween(durationMillis = 1600, easing = LinearEasing),
                )
            }
        } else {
            idlePhase.snapTo(0f)
        }
    }
    val currentPhase = idlePhase.value % SPEED_CHART_SAMPLE_LIMIT.toFloat()

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(UiDp.dp130)
                .clip(RoundedCornerShape(UiDp.dp12))
                .clickable(onClick = onClick)
    ) {
        val barGapPx = componentSizes.speedChartBarGap.toPx()
        val barCornerRadiusPx = componentSizes.speedChartBarCornerRadius.toPx()
        val chartBarCount = SPEED_CHART_SAMPLE_LIMIT

        val totalGapWidth = barGapPx * (chartBarCount - 1)
        val barWidthPx = ((size.width - totalGapWidth) / chartBarCount).coerceAtLeast(0f)
        if (barWidthPx <= 0f) {
            return@Canvas
        }

        val downloadBarColor = downloadColor.copy(alpha = opacity.mediumStrong)
        val uploadBarColor = uploadColor.copy(alpha = opacity.mediumStrong)

        if (!isRunning) {
            drawIdleBars(
                fractions = fractions,
                barWidthPx = barWidthPx,
                barGapPx = barGapPx,
                barCornerRadiusPx = barCornerRadiusPx,
                downloadBarColor = downloadBarColor,
                uploadBarColor = uploadBarColor,
                wavePhase = currentPhase
            )
        } else {
            drawStaticBars(
                fractions = fractions,
                barWidthPx = barWidthPx,
                barGapPx = barGapPx,
                barCornerRadiusPx = barCornerRadiusPx,
                downloadBarColor = downloadBarColor,
                uploadBarColor = uploadBarColor
            )
        }
    }
}

internal fun buildSpeedChartFractions(
    speedHistory: List<TrafficData>,
    sampleLimit: Int = SPEED_CHART_SAMPLE_LIMIT,
): SpeedChartFractions {
    require(sampleLimit > 0) { "sampleLimit must be greater than 0" }

    val downloadFractions = FloatArray(sampleLimit) { TrafficChartConfig.minimumVisibleFraction }
    val uploadFractions = FloatArray(sampleLimit) { TrafficChartConfig.minimumVisibleFraction }
    val recentHistory = speedHistory.takeLast(sampleLimit)
    val offset = (sampleLimit - recentHistory.size).coerceAtLeast(0)
    recentHistory.forEachIndexed { index, sample ->
        downloadFractions[offset + index] = TrafficChartConfig.calculateBarFraction(sample.download)
        uploadFractions[offset + index] = TrafficChartConfig.calculateBarFraction(sample.upload)
    }
    return SpeedChartFractions(download = downloadFractions, upload = uploadFractions)
}

internal data class SpeedChartFractions(
    val download: FloatArray,
    val upload: FloatArray
)

private fun DrawScope.drawStaticBars(
    fractions: SpeedChartFractions,
    barWidthPx: Float,
    barGapPx: Float,
    barCornerRadiusPx: Float,
    downloadBarColor: Color,
    uploadBarColor: Color
) {
    val barCornerRadius =
        createBarCornerRadius(barWidthPx = barWidthPx, barCornerRadiusPx = barCornerRadiusPx)
    for (index in fractions.download.indices) {
        val barLeftPx = index * (barWidthPx + barGapPx)
        if (barLeftPx >= size.width || barLeftPx + barWidthPx <= 0f) {
            continue
        }
        drawOverlayBar(
            leftPx = barLeftPx,
            downloadFraction = fractions.download[index],
            uploadFraction = fractions.upload[index],
            barWidthPx = barWidthPx,
            downloadBarColor = downloadBarColor,
            uploadBarColor = uploadBarColor,
            barCornerRadius = barCornerRadius
        )
    }
}

private fun DrawScope.drawIdleBars(
    fractions: SpeedChartFractions,
    barWidthPx: Float,
    barGapPx: Float,
    barCornerRadiusPx: Float,
    downloadBarColor: Color,
    uploadBarColor: Color,
    wavePhase: Float
) {
    val barCornerRadius =
        createBarCornerRadius(barWidthPx = barWidthPx, barCornerRadiusPx = barCornerRadiusPx)
    for (index in fractions.download.indices) {
        val barLeftPx = index * (barWidthPx + barGapPx)
        if (barLeftPx >= size.width || barLeftPx + barWidthPx <= 0f) {
            continue
        }
        drawOverlayBar(
            leftPx = barLeftPx,
            downloadFraction = applyIdleWave(fraction = fractions.download[index], index = index, phase = wavePhase),
            uploadFraction = applyIdleWave(fraction = fractions.upload[index], index = index, phase = wavePhase),
            barWidthPx = barWidthPx,
            downloadBarColor = downloadBarColor,
            uploadBarColor = uploadBarColor,
            barCornerRadius = barCornerRadius
        )
    }
}

private fun DrawScope.createBarCornerRadius(
    barWidthPx: Float,
    barCornerRadiusPx: Float,
): CornerRadius =
    CornerRadius(
        x = barCornerRadiusPx.coerceAtMost(barWidthPx / 2f),
        y = barCornerRadiusPx.coerceAtMost(size.height / 2f),
    )

private fun DrawScope.drawOverlayBar(
    leftPx: Float,
    downloadFraction: Float,
    uploadFraction: Float,
    barWidthPx: Float,
    downloadBarColor: Color,
    uploadBarColor: Color,
    barCornerRadius: CornerRadius
) {
    val clampedDownload = downloadFraction.coerceIn(TrafficChartConfig.minimumVisibleFraction, 1f)
    val clampedUpload = uploadFraction.coerceIn(TrafficChartConfig.minimumVisibleFraction, 1f)
    var downloadHeightPx = size.height * clampedDownload
    var uploadHeightPx = size.height * clampedUpload
    val totalHeightPx = downloadHeightPx + uploadHeightPx
    if (totalHeightPx > size.height) { val scale = size.height / totalHeightPx; downloadHeightPx *= scale; uploadHeightPx *= scale }
    drawRect(color = downloadBarColor, topLeft = Offset(x = leftPx, y = size.height - downloadHeightPx), size = Size(width = barWidthPx, height = downloadHeightPx))
    drawTopRoundedRect(color = uploadBarColor, leftPx = leftPx, topPx = size.height - downloadHeightPx - uploadHeightPx, widthPx = barWidthPx, heightPx = uploadHeightPx, radius = barCornerRadius)
}

private fun DrawScope.drawTopRoundedRect(color: Color, leftPx: Float, topPx: Float, widthPx: Float, heightPx: Float, radius: CornerRadius) {
    if (heightPx <= 0f) return
    val rX = radius.x.coerceAtMost(widthPx / 2f)
    val rY = radius.y.coerceAtMost(heightPx / 2f)
    // 优化：通过使用 drawRoundRect 以及在底部绘制扁平矩形（drawRect）来避免路径分配。
    drawRoundRect(
        color = color,
        topLeft = Offset(leftPx, topPx),
        size = Size(widthPx, heightPx),
        cornerRadius = CornerRadius(rX, rY)
    )
    if (heightPx > rY) {
        drawRect(
            color = color,
            topLeft = Offset(leftPx, topPx + heightPx - rY),
            size = Size(widthPx, rY)
        )
    }
}

private fun applyIdleWave(fraction: Float, index: Int, phase: Float): Float {
    val distance = kotlin.math.abs(index - phase)
    val wrappedDistance =
        minOf(
            distance,
            distance + SPEED_CHART_SAMPLE_LIMIT,
            kotlin.math.abs(index + SPEED_CHART_SAMPLE_LIMIT - phase),
        )
    val normalized = (1f - wrappedDistance / SPEED_CHART_IDLE_WAVE_SPAN).coerceIn(0f, 1f)
    val wave = normalized * normalized
    return fraction + wave * SPEED_CHART_IDLE_WAVE_AMPLITUDE
}
