/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
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
 *
 */

package com.github.yumeyucca.yumebox.presentation.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/** A restrained vertical hand-off for adjacent content in the split-shell detail pane. */
fun verticalBounceContentTransform(forward: Boolean): ContentTransform {
    val enterSpec = tween<IntOffset>(durationMillis = 180, easing = AnimationSpecs.StrongEaseOut)
    val exitSpec = tween<IntOffset>(durationMillis = 120, easing = AnimationSpecs.StrongEaseOut)
    val fadeInSpec = tween<Float>(durationMillis = 120, easing = AnimationSpecs.StrongEaseOut)
    val fadeOutSpec = tween<Float>(durationMillis = 90, easing = AnimationSpecs.StrongEaseOut)
    val transform =
        if (forward) {
            (slideInVertically(animationSpec = enterSpec) { fullHeight ->
                fullHeight / 18
            } + fadeIn(animationSpec = fadeInSpec)) togetherWith
                    (slideOutVertically(animationSpec = exitSpec) { fullHeight ->
                        -fullHeight / 24
                    } + fadeOut(animationSpec = fadeOutSpec))
        } else {
            (slideInVertically(animationSpec = enterSpec) { fullHeight ->
                -fullHeight / 18
            } + fadeIn(animationSpec = fadeInSpec)) togetherWith
                    (slideOutVertically(animationSpec = exitSpec) { fullHeight ->
                        fullHeight / 24
                    } + fadeOut(animationSpec = fadeOutSpec))
        }
    return ContentTransform(
        targetContentEnter = transform.targetContentEnter,
        initialContentExit = transform.initialContentExit,
        sizeTransform = null,
    )
}
