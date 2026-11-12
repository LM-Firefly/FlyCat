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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液态玻璃动作按钮（16dp 胶囊）：按钮底透明 + [glassCardSurface] 采样全局壁纸层（无壁纸/无 shader 时回退实色）。
 * 主色态在玻璃上叠半透明主色染色，危险态仅染文字；禁用态回退对应禁用色且不染色。
 */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false,
) {
    val colors = when {
        primary -> ButtonDefaults.textButtonColorsPrimary(color = Color.Transparent, disabledColor = Color.Transparent)
        danger -> ButtonDefaults.textButtonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            textColor = MiuixTheme.colorScheme.error,
        )
        else -> ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent)
    }
    val fallbackColor = when {
        primary -> if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.disabledPrimaryButton
        else -> if (enabled) MiuixTheme.colorScheme.secondaryVariant else MiuixTheme.colorScheme.disabledSecondaryVariant
    }
    val glassTint = if (primary && enabled) MiuixTheme.colorScheme.primary.copy(alpha = GlassButtonTintAlpha) else Color.Transparent
    TextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.glassButtonSurface(fallbackColor = fallbackColor, glassTint = glassTint),
    )
}

private const val GlassButtonTintAlpha = 0.55f

@Composable
private fun Modifier.glassButtonSurface(fallbackColor: Color, glassTint: Color): Modifier {
    val shape = RoundedCornerShape(ButtonDefaults.CornerRadius)
    // 边缘光沿胶囊内侧一圈光晕发丝，标识可点击按钮区（与玻璃圆钮同配方，miuix EdgeStroke）。
    return clip(shape)
        .glassCardSurface(shape, fallbackColor = fallbackColor, glassTint = glassTint)
        .edgeStroke(
            shape = shape,
            stroke = GlassEdgeStrokes.forTheme(isDark = isDarkTheme(), light = GlassEdgeStrokes.SmallLight, dark = GlassEdgeStrokes.SmallDark),
        )
}
