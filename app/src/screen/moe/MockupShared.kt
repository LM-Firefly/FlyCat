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

package com.github.yumelira.yumebox.screen.moe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared color roles the guide mockups derive from the theme — one place instead of a block per composable. */
internal data class MockupPalette(
    val mask: Color,
    val maskStrong: Color,
    val recessed: Color,
    val onHero: Color,
    val frameBorder: Color,
    val cardVariant: Color,
    val surface: Color,
)

@Composable
internal fun mockupPalette(): MockupPalette {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity
    return MockupPalette(
        mask = colorScheme.onSurface.copy(alpha = opacity.subtle),
        maskStrong = colorScheme.onSurface.copy(alpha = opacity.subtleStrong),
        recessed = colorScheme.onSurface.copy(alpha = opacity.verySubtle),
        onHero = colorScheme.surface.copy(alpha = opacity.secondaryText),
        frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft),
        cardVariant = colorScheme.surfaceVariant.copy(alpha = opacity.surfaceVariant),
        surface = colorScheme.surface,
    )
}

/** The rounded phone-frame shell all three mockups draw before their inner content. */
@Composable
internal fun MockupPhoneFrame(
    palette: MockupPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(0.64f)
                .aspectRatio(0.46f)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surface)
                .border(1.5.dp, palette.frameBorder, RoundedCornerShape(22.dp))
                .padding(7.dp),
        content = content,
    )
}
