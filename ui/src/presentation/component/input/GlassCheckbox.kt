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

package com.github.lmfirefly.flycat.presentation.component.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.theme.themeEdgeStroke
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GlassCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colorScheme = MiuixTheme.colorScheme
    val checked = state != ToggleableState.Off
    val fallbackColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colorScheme.disabledPrimary
            !enabled -> colorScheme.disabledSecondary
            checked -> colorScheme.primary
            else -> colorScheme.secondary
        },
        label = "GlassCheckboxFallbackColor",
    )
    val selectedTint by animateColorAsState(targetValue = if (checked && enabled) colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent, label = "GlassCheckboxTint")
    Box(
        modifier = modifier.size(26.dp).clip(CircleShape).glassCardSurface(shape = CircleShape, fallbackColor = fallbackColor, glassTint = selectedTint).then(if (enabled) {
                Modifier.themeEdgeStroke(
                    shape = CircleShape,
                    tint = colorScheme.primary.takeIf { checked },
                    emphasized = checked,
                )
            } else {
                Modifier
            },
        ),
    ) {
        Checkbox(
            state = state,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.matchParentSize(),
            colors = CheckboxDefaults.checkboxColors(
                checkedBackgroundColor = Color.Transparent,
                uncheckedBackgroundColor = Color.Transparent,
                disabledCheckedBackgroundColor = Color.Transparent,
                disabledUncheckedBackgroundColor = Color.Transparent,
            ),
        )
    }
}
