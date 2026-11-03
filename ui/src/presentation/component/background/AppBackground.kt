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

package com.github.lmfirefly.flycat.presentation.component.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 全局页面背景规格：由 app 层（持有设置）注入，导航根（AppNavContainer）消费它铺背景并录制壁纸层。
 * [render] 用传入的 [Modifier] 绘制背景（宿主会附加 `layerBackdrop` 以供玻璃卡片采样）。
 * [enabled] 为 false 时不铺背景、`GlassScaffold` 用实色容器，玻璃回退纯色（保持旧观）。
 */
@Stable
class AppBackgroundSpec(
    val enabled: Boolean = false,
    val render: @Composable (Modifier) -> Unit = {},
)

val LocalAppBackground = compositionLocalOf { AppBackgroundSpec() }
