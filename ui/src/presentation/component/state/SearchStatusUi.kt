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

package com.github.lmfirefly.flycat.presentation.component.state

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs

@Composable
fun SearchStatus.TopAppBarAnim(
    modifier: Modifier = Modifier,
    visible: Boolean = shouldCollapse(),
    content: @Composable () -> Unit,
) {
    val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(if (visible) AnimationSpecs.DURATION_SLOW else 0, easing = AnimationSpecs.StandardEasing),
            label = "SearchTopBarAlpha",
        )
    Box(
        // 顶栏背景由其自身的渐进色带负责（静止时透明、内容滚入渐显）；此处仅做整体淡出，勿垫不透明底色，否则静止时会盖住壁纸渐变形成灰白色块。
        modifier = modifier.fillMaxWidth().graphicsLayer {
            this.alpha = alpha
        }
    ) {
        content()
    }
}
