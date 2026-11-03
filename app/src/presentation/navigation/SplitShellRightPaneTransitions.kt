/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs

/**
 * 平板分屏右侧面板的垂直滑动 + 淡入淡出过渡效果。
 * 前进（push）从底部向上滑入内容；后退（pop）从顶部向下滑出。
 */
internal fun splitShellRightPaneTransform(forward: Boolean): ContentTransform {
    val enterOffset = { height: Int -> if (forward) height / 10 else -height / 14 }
    val exitOffset = { height: Int -> if (forward) -height / 14 else height / 10 }
    return ContentTransform(
        targetContentEnter = slideInVertically(
            animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.Legacy),
            initialOffsetY = enterOffset,
        ) + fadeIn(animationSpec = tween(AnimationSpecs.DURATION_FAST)),
        initialContentExit = slideOutVertically(
            animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.Legacy),
            targetOffsetY = exitOffset,
        ) + fadeOut(animationSpec = tween(AnimationSpecs.DURATION_FAST)),
        sizeTransform = SizeTransform(clip = false),
    )
}
