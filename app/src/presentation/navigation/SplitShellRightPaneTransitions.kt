/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

private const val SLIDE_DURATION = 300
private const val FADE_DURATION = 200
private val slideEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

/**
 * Vertical slide + fade transition for the tablet split-shell right pane.
 * Forward (push) slides content up from the bottom; backward (pop) slides down.
 */
internal fun splitShellRightPaneTransform(forward: Boolean): ContentTransform {
    val enterOffset = { height: Int -> if (forward) height / 10 else -height / 14 }
    val exitOffset = { height: Int -> if (forward) -height / 14 else height / 10 }

    return ContentTransform(
        targetContentEnter =
            slideInVertically(
                animationSpec = tween(SLIDE_DURATION, easing = slideEasing),
                initialOffsetY = enterOffset,
            ) + fadeIn(animationSpec = tween(FADE_DURATION)),
        initialContentExit =
            slideOutVertically(
                animationSpec = tween(SLIDE_DURATION, easing = slideEasing),
                targetOffsetY = exitOffset,
            ) + fadeOut(animationSpec = tween(FADE_DURATION)),
        sizeTransform = SizeTransform(clip = false),
    )
}
