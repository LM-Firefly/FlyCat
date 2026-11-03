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

package com.github.lmfirefly.flycat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.common.util.AppLanguageManager
import com.github.lmfirefly.flycat.feature.proxy.presentation.screen.ProxySheetContent
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.presentation.background.rememberAppBackgroundSpec
import com.github.lmfirefly.flycat.presentation.component.background.LocalAppBackground
import com.github.lmfirefly.flycat.presentation.theme.FlyTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.ProvideAndroidPlatformTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

class ProxySheetActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguageManager.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 统一走 dismissSheet 清理链（含弹窗收尾），外点直接 finish 会绕过清理并打断关闭动画。
        setFinishOnTouchOutside(false)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
        setContent {
            val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
            val themeMode = appSettingsViewModel.themeMode.state.collectAsStateWithLifecycle().value
            val themeSeedColorArgb = appSettingsViewModel.themeSeedColorArgb.state.collectAsStateWithLifecycle().value
            val invertOnPrimaryColors = appSettingsViewModel.invertOnPrimaryColors.state.collectAsStateWithLifecycle().value
            val backgroundStyle = appSettingsViewModel.appBackgroundStyle.state.collectAsStateWithLifecycle().value
            val wallpaperUri = appSettingsViewModel.moeWallpaperUri.state.collectAsStateWithLifecycle().value
            val wallpaperBlur = appSettingsViewModel.appBackgroundWallpaperBlur.state.collectAsStateWithLifecycle().value
            ProvideAndroidPlatformTheme {
                FlyTheme(
                    themeMode = themeMode,
                    themeSeedColorArgb = themeSeedColorArgb,
                    invertOnPrimaryColors = invertOnPrimaryColors,
                ) {
                    // 壁纸录制层与主应用同构：面板玻璃与节点卡片 glassCardSurface 采样之（背景关闭时回退实色）。
                    val appBackground = rememberAppBackgroundSpec(backgroundStyle, wallpaperUri, wallpaperBlur)
                    val shaderSupported = isRuntimeShaderSupported()
                    val wallpaperBackdrop = rememberLayerBackdrop { drawContent() }
                    var sheetTopPx by remember { mutableStateOf<Float?>(null) }
                    var rootHeightPx by remember { mutableStateOf(0) }
                    val glassBackdrop = if (appBackground.enabled && shaderSupported) wallpaperBackdrop else null
                    CompositionLocalProvider(LocalAppBackground provides appBackground, LocalGlassBackdrop provides glassBackdrop) {
                        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { rootHeightPx = it.size.height }) {
                            if (appBackground.enabled && shaderSupported) {
                                // 壁纸只铺弹窗遮挡区域（同圆角裁剪，顶边与实测弹窗顶边对齐）：弹窗外零像素继续透出桌面。
                                // 几何状态只在测量期读取（触发重测量而非重组）：转场时弹窗高度逐帧变化，若读在组合期会引发重组风暴拖垮帧时钟（按钮切换迟滞/叠影）。
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        // 裁剪必须包在自定义 layout 之外：layout 会把子项量成窗高（整窗壁纸几何），clip 在内只会裁到整窗=不裁。
                                        .clip(RoundedCornerShape(topStart = UiDp.dp18, topEnd = UiDp.dp18))
                                        .layout { measurable, constraints ->
                                            val sheetTop = sheetTopPx
                                            val boxHeight = if (sheetTop != null && rootHeightPx > sheetTop) (rootHeightPx - sheetTop).roundToInt() else 0
                                            // 壁纸按整窗几何测量（窗高）后整体上移到窗顶：裁剪框随弹窗高度变化仍保持同一裁切，采样配色不漂移。
                                            val placeable = measurable.measure(constraints.copy(minHeight = rootHeightPx, maxHeight = rootHeightPx))
                                            layout(placeable.width, boxHeight) {
                                                placeable.placeRelative(0, boxHeight - placeable.height)
                                            }
                                        }
                                        .layerBackdrop(wallpaperBackdrop),
                                ) {
                                    appBackground.render(Modifier.fillMaxSize())
                                }
                            }
                            // 透明无内嵌 Scaffold：登记式弹层（OverlayBottomSheet）与玻璃弹窗都由 Scaffold 的弹窗宿主渲染——本 Activity 此前没有 Scaffold，排序弹窗登记后无人渲染（点击无反应）。
                            Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                                ProxySheetContent(
                                    onDismiss = {
                                        finish()
                                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                                            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                                        } else {
                                            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                                        }
                                    },
                                    onSheetTopPositioned = { sheetTopPx = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
