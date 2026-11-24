/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 */

package com.github.lmfirefly.flycat.feature.home.presentation.screen.moe

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.github.lmfirefly.flycat.presentation.component.misc.BUNDLED_WALLPAPER_URI
import com.github.lmfirefly.flycat.presentation.component.misc.WallpaperDecodeCache
import com.github.lmfirefly.flycat.presentation.component.misc.calculateWallpaperViewportLayout
import com.github.lmfirefly.flycat.presentation.component.misc.readWallpaperBounds
import com.github.lmfirefly.flycat.presentation.component.misc.resolveWallpaperModel
import com.github.lmfirefly.flycat.presentation.component.misc.wallpaperCacheKey
import com.github.lmfirefly.flycat.presentation.component.misc.wallpaperImageRequest
import com.github.panpf.sketch.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MoeWallpaperBackground(
    wallpaperUri: String,
    wallpaperZoom: Float = 1f,
    wallpaperBiasX: Float = 0f,
    wallpaperBiasY: Float = 0f,
    qualityMode: MoeWallpaperQualityMode = MoeWallpaperQualityMode.Foreground,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val model by produceState(wallpaperUri.ifBlank { BUNDLED_WALLPAPER_URI }, wallpaperUri) { value = withContext(Dispatchers.IO) { resolveWallpaperModel(context, wallpaperUri) } }
    // 图片头读取（毫秒级）：完整画幅的显示宽高，供等比解码与偏置取景计算共用。
    val sourceBounds = remember(model) { readWallpaperBounds(context, model) }
    // 每次组合都取内容版本（一次 stat）：文件被覆盖写入而 URI 不变时，remember 住的旧键会让旧位图常驻。
    val cacheKey = wallpaperCacheKey(model)
    BoxWithConstraints(modifier = modifier) {
        val width = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val height = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        // 首帧同步路径：预热已入槽则直接取位图；未入槽则当场同步解码（耗时被 Splash 吸收），不出现「垫色 → 淡入」。
        val cachedBitmap = remember(model, cacheKey, sourceBounds, width, height, qualityMode) {
            WallpaperDecodeCache.imageBitmap(
                context = context,
                model = model,
                cacheKey = cacheKey,
                bounds = sourceBounds,
                boxWidth = width.toInt(),
                boxHeight = height.toInt(),
                multiplier = if (qualityMode == MoeWallpaperQualityMode.BackgroundBlur) 1.2f else 1f,
            )
        }
        val painter: Painter = if (cachedBitmap != null) { remember(cachedBitmap) { BitmapPainter(cachedBitmap) } } else { rememberWallpaperPainter(context, model, cacheKey, sourceBounds, width, height, qualityMode) }
        val intrinsic = painter.intrinsicSize
        val layout = calculateWallpaperViewportLayout(
            containerWidthPx = width,
            containerHeightPx = height,
            imageWidthPx = sourceBounds?.first?.toFloat() ?: intrinsic.width.takeIf { it > 0f && it.isFinite() },
            imageHeightPx = sourceBounds?.second?.toFloat() ?: intrinsic.height.takeIf { it > 0f && it.isFinite() },
            zoom = wallpaperZoom.coerceIn(1f, 5f),
            biasX = wallpaperBiasX,
            biasY = wallpaperBiasY,
        )
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(layout.biasX, layout.biasY),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(),
        )
    }
}

@Composable
private fun rememberWallpaperPainter(context: Context, model: String, cacheKey: String, bounds: Pair<Int, Int>?, width: Float, height: Float, quality: MoeWallpaperQualityMode): Painter = rememberAsyncImagePainter(
    request = remember(model, cacheKey, bounds, width, height, quality) {
        wallpaperImageRequest(
            context = context,
            model = model,
            cacheKey = cacheKey,
            bounds = bounds,
            boxWidth = width.toInt(),
            boxHeight = height.toInt(),
            multiplier = if (quality == MoeWallpaperQualityMode.BackgroundBlur) 1.2f else 1f,
        )
    },
)
