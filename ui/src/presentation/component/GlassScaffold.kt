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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.lmfirefly.flycat.presentation.component.background.LocalAppBackground
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.LocalTopBarBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 路由级别的 [Scaffold]：正文滚入顶栏下方时，顶栏对正文做渐进模糊、色带随之渐显（对齐 miuix 示例 app）。
 * 正文内容录进 [rememberLayerBackdrop]（透明底、只录内容）；顶栏采样的是 [CompositeBackdrop] ——壁纸层在采样瞬间实时叠合，动态渐变换色永不过期（烘进录制层会在页面静止时留下旧色带）。
 * 将其作为 [LocalTopBarBackdrop] 提供给 `TopBar`/`SmallTopBar` 作为渐进模糊采样源与槽位按钮采样，并将捕获结果附加到主体包装器上。无壁纸时回退 [rememberSurfaceBackdrop] 的表面底录制（旧观）。
 * 顶部栏在 Scaffold 的 topBar 插槽中组合，位于被捕获的子树之外，满足 miuix-blur 的同子树约束；没有 RuntimeShader 支持的设备则在顶部栏自身内部保留仅遮罩的回退方案。
 */
@Composable
fun GlassScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    // body背景的消费者必须是记录的body的兄弟节点，而不能是其后代节点。
    contentOverlay: @Composable (PaddingValues) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val wallpaperBackdrop = LocalGlassBackdrop.current
    val contentCapture = rememberLayerBackdrop()
    val surfaceCapture = rememberSurfaceBackdrop(MiuixTheme.colorScheme.surface)
    val bodyCapture = wallpaperBackdrop?.let { contentCapture } ?: surfaceCapture
    val backdrop: Backdrop = if (wallpaperBackdrop != null) { remember(wallpaperBackdrop, contentCapture) { CompositeBackdrop(wallpaperBackdrop, contentCapture) } } else { surfaceCapture }
    val appBackground = LocalAppBackground.current
    // 壁纸录制层已上提到 AppNavContainer（全局 LocalGlassBackdrop）；此处仅提供顶栏 body 录制层。
    CompositionLocalProvider(LocalTopBarBackdrop provides backdrop) {
        // 壁纸由导航根铺满整屏；Scaffold 透明容器让其透出，玻璃卡片/顶栏采样全局壁纸层。
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = topBar,
                modifier = modifier,
                floatingActionButton = floatingActionButton,
                containerColor = if (appBackground.enabled) Color.Transparent else MiuixTheme.colorScheme.surface,
            ) { innerPadding ->
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(bodyCapture)) { content(innerPadding) }
                    contentOverlay(innerPadding)
                }
            }
        }
    }
}
