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

package com.github.yumeyucca.yumebox.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.github.yumeyucca.yumebox.presentation.theme.AnimationSpecs
import com.github.yumeyucca.yumebox.presentation.theme.verticalBounceContentTransform

internal fun splitShellRightPaneTransform(forward: Boolean): ContentTransform =
    verticalBounceContentTransform(forward)

/** The shell changes owners here; keep that hand-off free of a second spatial transition. */
internal fun splitShellPaneSwapTransform(): ContentTransform =
    fadeIn(
        animationSpec = tween(durationMillis = 120, easing = AnimationSpecs.StrongEaseOut)
    ) togetherWith
            fadeOut(animationSpec = tween(durationMillis = 90, easing = AnimationSpecs.StrongEaseOut))
