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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.lmfirefly.flycat.presentation.component.background.LocalAppBackground
import com.github.lmfirefly.flycat.presentation.theme.LocalTopBarBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 路由级别的 [Scaffold]：正文滚入顶栏下方时，顶栏的渐进色带（采样全局壁纸层）随之渐显。
 * 创建一个 [top.yukonga.miuix.kmp.blur.LayerBackdrop] 来捕获主体内容（先绘制不透明的背景底层，这样半透明页面就不会透出来；以表面颜色作为 key，这样切换主题时会重新捕获），
 * 将其作为 [LocalTopBarBackdrop] 提供给 `TopBar`/`SmallTopBar` 的槽位按钮与菜单回退采样，并将捕获结果附加到主体包装器上。顶部栏在 Scaffold 的 topBar 插槽中组合，
 * 位于被捕获的子树之外，满足 miuix-blur 的同子树约束；没有 RuntimeShader 支持的设备则在顶部栏自身内部保留仅遮罩的回退方案。
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
    val backdrop = rememberSurfaceBackdrop(MiuixTheme.colorScheme.surface)
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
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content(innerPadding) }
                    contentOverlay(innerPadding)
                }
            }
        }
    }
}
