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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.util.screenSize
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.misc.calculateWallpaperViewportLayout
import com.github.lmfirefly.flycat.presentation.component.misc.readWallpaperBounds
import com.github.lmfirefly.flycat.presentation.component.misc.wallpaperCacheKey
import com.github.lmfirefly.flycat.presentation.component.misc.wallpaperImageRequest
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlin.math.max
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MoeWallpaperCropScreen(navigator: Navigator, wallpaperUri: String, initialZoom: Float = 1f, initialBiasX: Float = 0f, initialBiasY: Float = 0f) {
    val viewModel = koinViewModel<AppSettingsViewModel>()
    val context = LocalContext.current
    var biasX by remember(wallpaperUri, initialBiasX) { mutableFloatStateOf(initialBiasX.coerceIn(-1f, 1f)) }
    var biasY by remember(wallpaperUri, initialBiasY) { mutableFloatStateOf(initialBiasY.coerceIn(-1f, 1f)) }
    // 取景数学以源图完整画幅为准（EXIF 修正）：painter 的 intrinsicSize 解码前为 Unspecified、解码后也只是输出尺寸，仅作兜底。
    val sourceBounds = remember(wallpaperUri) { readWallpaperBounds(context, wallpaperUri) }
    val cacheKey = wallpaperCacheKey(wallpaperUri)
    val painter = rememberAsyncImagePainter(
        request = remember(wallpaperUri, sourceBounds, cacheKey) {
            wallpaperImageRequest(
                context = context,
                model = wallpaperUri,
                cacheKey = cacheKey,
                bounds = sourceBounds,
                boxWidth = context.screenSize().width,
                boxHeight = context.screenSize().height,
            )
        }
    )
    val density = LocalDensity.current
    Scaffold { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val painterIntrinsic = painter.intrinsicSize
            val imageWidthPx = sourceBounds?.first?.toFloat() ?: painterIntrinsic.width.takeIf { it > 0f && it.isFinite() }
            val imageHeightPx = sourceBounds?.second?.toFloat() ?: painterIntrinsic.height.takeIf { it > 0f && it.isFinite() }
            val viewportLayout = calculateWallpaperViewportLayout(
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                imageWidthPx = imageWidthPx,
                imageHeightPx = imageHeightPx,
                zoom = initialZoom,
                biasX = biasX,
                biasY = biasY,
            )
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(
                    viewportLayout.maxShiftX,
                    viewportLayout.maxShiftY,
                ) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (viewportLayout.maxShiftX > 0.5f) {
                            val currentTranslationX = biasX.coerceIn(-1f, 1f) * viewportLayout.maxShiftX
                            val nextTranslationX = (currentTranslationX - dragAmount.x).coerceIn(
                                -viewportLayout.maxShiftX,
                                viewportLayout.maxShiftX,
                            )
                            biasX = (nextTranslationX / viewportLayout.maxShiftX).coerceIn(-1f, 1f)
                        } else {
                            biasX = 0f
                        }
                        if (viewportLayout.maxShiftY > 0.5f) {
                            val currentTranslationY = biasY.coerceIn(-1f, 1f) * viewportLayout.maxShiftY
                            val nextTranslationY = (currentTranslationY - dragAmount.y).coerceIn(
                                -viewportLayout.maxShiftY,
                                viewportLayout.maxShiftY,
                            )
                            biasY = (nextTranslationY / viewportLayout.maxShiftY).coerceIn(-1f, 1f)
                        } else {
                            biasY = 0f
                        }
                    }
                }
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(viewportLayout.biasX, viewportLayout.biasY),
                    modifier = Modifier.align(Alignment.Center).fillMaxSize(),
                )
            }
            val bottomInsetPx = with(density) {
                max(
                    WindowInsets.navigationBars.getBottom(this),
                    WindowInsets.systemGestures.getBottom(this),
                ).toFloat()
            }
            Button(
                modifier = Modifier.align(Alignment.BottomCenter).padding(
                    start = UiDp.dp16,
                    end = UiDp.dp16,
                    bottom = with(density) { bottomInsetPx.toDp() } + UiDp.dp12,
                ).fillMaxWidth().height(UiDp.dp52).clip(RoundedCornerShape(UiDp.dp12)),
                colors = ButtonDefaults.buttonColorsPrimary(),
                onClick = {
                    viewModel.onMoeWallpaperCropChange(
                        zoom = 1f,
                        biasX = viewportLayout.biasX,
                        biasY = viewportLayout.biasY,
                    )
                    viewModel.applyMoeWallpaper(sourceUri = wallpaperUri) {
                        navigator.pop()
                    }
                },
            ) {
                Text(
                    text = FlyTxt.AppSettings.Button.Apply,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
