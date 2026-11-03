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

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 在 [containerColor] 之上采样自身内容的背景层。
 * 玻璃控件需要采样对象。工作表（sheet）无壁纸层时向库传递 `backdrop = null`（面板绘制不透明颜色，不会对页面进行模糊），
 * 而页面主体可能是半透明的，因此两者在静止状态下都会使玻璃效果为空，直到内容滚动进入时才拾取效果。
 * 先绘制容器颜色可以为它们提供一个基础层；以颜色为键可在主题切换时重新记录。
 */
@Composable
fun rememberSurfaceBackdrop(containerColor: Color): LayerBackdrop = key(containerColor) {
    rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }
}
