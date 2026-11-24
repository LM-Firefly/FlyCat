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

package com.github.lmfirefly.flycat.presentation.background

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.github.lmfirefly.flycat.core.model.AppBackgroundStyle
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings.Companion.DIR_BOTTOM
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings.Companion.DIR_LEFT
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings.Companion.DIR_RIGHT
import com.github.lmfirefly.flycat.presentation.component.background.AppBackgroundSpec
import com.github.lmfirefly.flycat.presentation.component.background.BgEffectBackground
import com.github.lmfirefly.flycat.presentation.component.misc.WallpaperDecodeCache
import com.github.lmfirefly.flycat.presentation.component.misc.calculateWallpaperViewportLayout
import com.github.lmfirefly.flycat.presentation.component.misc.readWallpaperBounds
import com.github.lmfirefly.flycat.presentation.component.misc.wallpaperCacheKey
import com.github.lmfirefly.flycat.presentation.component.misc.wallpaperImageRequest
import com.github.lmfirefly.flycat.presentation.theme.LocalBlurTheme
import com.github.lmfirefly.flycat.presentation.theme.blurBlendToken
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import com.github.panpf.sketch.PainterState
import com.github.panpf.sketch.rememberAsyncImagePainter
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 把背景设置解析为注入 `GlassScaffold` 的 [AppBackgroundSpec]：关闭 / OS2、OS3 动态渐变 / 用户壁纸图。
 *  三种背景统一覆盖一层渐进模糊（「壁纸模糊强度」页的配置，见 [progressiveBackgroundSpec]）。
 *  [wallpaperBiasX]/[wallpaperBiasY] 为壁纸的显示范围偏置（裁剪屏「应用」保存的取景）。 */
@Composable
fun rememberAppBackgroundSpec(style: AppBackgroundStyle, wallpaperUri: String, wallpaperBiasX: Float = 0f, wallpaperBiasY: Float = 0f): AppBackgroundSpec = when (style) {
    AppBackgroundStyle.Off -> remember { AppBackgroundSpec(enabled = false) }
    AppBackgroundStyle.DynamicGradientOs2 -> remember { progressiveBackgroundSpec(isOs3 = false) }
    AppBackgroundStyle.DynamicGradientOs3 -> remember { progressiveBackgroundSpec(isOs3 = true) }
    AppBackgroundStyle.Wallpaper -> if (wallpaperUri.isBlank()) { remember { progressiveBackgroundSpec(isOs3 = true) } } else {
        remember(wallpaperUri, wallpaperBiasX, wallpaperBiasY) { progressiveBackgroundSpec(wallpaperUri = wallpaperUri, wallpaperBiasX = wallpaperBiasX, wallpaperBiasY = wallpaperBiasY) }
    }
}

/**
 * 全局背景 + 渐进模糊层：[com.github.lmfirefly.flycat.core.model.BlurThemeSettings.ProgressiveBlurStyle]（「壁纸模糊强度」设置页）作用于 OS2/OS3 动态渐变与壁纸图片——背景先录进捕获层，再按方向/混色/半径/噪点/梯度覆盖一层渐进模糊（随主题页实时变化）。
 * 渐进层合入壁纸录制层，玻璃采样与所见一致。动态渐变的动画开关遵循主题 Foreground Blur 组的 Dynamic Background。
 */
private fun progressiveBackgroundSpec(isOs3: Boolean = true, wallpaperUri: String? = null, wallpaperBiasX: Float = 0f, wallpaperBiasY: Float = 0f): AppBackgroundSpec = AppBackgroundSpec(enabled = true) { bgModifier ->
    val blurTheme = LocalBlurTheme.current
    val progressive = blurTheme.progressive
    val dark = isDarkTheme()
    val token = remember(progressive.blendMode, dark) { blurBlendToken(progressive.blendMode, dark, foreground = false) }
    val capture = rememberLayerBackdrop()
    val gradient = remember(progressive.direction, progressive.start, progressive.end, progressive.curve) {
        val base = when (progressive.direction) {
            DIR_BOTTOM -> ProgressiveBlur.Bottom
            DIR_LEFT -> ProgressiveBlur.Left
            DIR_RIGHT -> ProgressiveBlur.Right
            else -> ProgressiveBlur.Top
        }
        base.copy(startFraction = progressive.start, endFraction = progressive.end, curve = progressive.curve)
    }
    Box(modifier = Modifier.fillMaxSize().then(bgModifier)) {
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(capture)) {
            if (wallpaperUri != null) {
                // 解码结果统一走 WallpaperDecodeCache（版本化缓存键 + 启动预热）：首帧即同步出图，不再出现「垫色 → 淡入」；仅当源图尺寸未知或同步解码失败时才走异步 painter 兜底，垫主题底色、就绪后淡入。
                val context = LocalContext.current
                val config = LocalConfiguration.current
                val density = LocalDensity.current
                val targetW = (config.screenWidthDp * density.density).toInt().coerceAtLeast(1)
                val targetH = (config.screenHeightDp * density.density).toInt().coerceAtLeast(1)
                val sourceBounds = remember(wallpaperUri) { readWallpaperBounds(context, wallpaperUri) }
                // 每次组合都取内容版本（一次 stat）：文件被覆盖写入而 URI 不变时，remember 住的旧键会让旧位图常驻。
                val cacheKey = wallpaperCacheKey(wallpaperUri)
                // 显示范围（裁剪屏保存的取景偏置）：按源图完整画幅计算可平移窗口。
                val layout = calculateWallpaperViewportLayout(
                    containerWidthPx = targetW.toFloat(),
                    containerHeightPx = targetH.toFloat(),
                    imageWidthPx = sourceBounds?.first?.toFloat(),
                    imageHeightPx = sourceBounds?.second?.toFloat(),
                    zoom = 1f,
                    biasX = wallpaperBiasX,
                    biasY = wallpaperBiasY,
                )
                val wallpaperAlignment = BiasAlignment(layout.biasX, layout.biasY)
                // 首帧同步路径：预热已入槽则直接取位图；未入槽则当场同步解码（耗时被 Splash 吸收），不出现「垫色 → 淡入」。
                val cachedBitmap = remember(wallpaperUri, cacheKey, sourceBounds, targetW, targetH) {
                    WallpaperDecodeCache.imageBitmap(
                        context = context,
                        model = wallpaperUri,
                        cacheKey = cacheKey,
                        bounds = sourceBounds,
                        boxWidth = targetW,
                        boxHeight = targetH,
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface))
                if (cachedBitmap != null) {
                    Image(
                        painter = remember(cachedBitmap) { BitmapPainter(cachedBitmap) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = wallpaperAlignment,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val painter = rememberAsyncImagePainter(
                        request = remember(wallpaperUri, targetW, targetH, sourceBounds, cacheKey) {
                            wallpaperImageRequest(
                                context = context,
                                model = wallpaperUri,
                                cacheKey = cacheKey,
                                bounds = sourceBounds,
                                boxWidth = targetW,
                                boxHeight = targetH,
                            )
                        },
                    )
                    val wallpaperAlpha by animateFloatAsState(
                        targetValue = if (painter.state.painterState is PainterState.Success) 1f else 0f,
                        label = "wallpaperFadeIn",
                    )
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = wallpaperAlignment,
                        modifier = Modifier.fillMaxSize().alpha(wallpaperAlpha),
                    )
                }
            } else {
                BgEffectBackground(
                    dynamicBackground = blurTheme.foreground.dynamicBackground,
                    isOs3Effect = isOs3,
                    modifier = Modifier.fillMaxSize(),
                    isFullSize = true,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().progressiveTextureBlur(
                backdrop = capture,
                shape = RectangleShape,
                blurRadius = progressive.blurRadius,
                gradient = gradient,
                noiseCoefficient = progressive.noise,
                colors = BlurDefaults.blurColors(blendColors = token),
            ),
        )
    }
}
