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
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.themeEdgeStroke
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液态玻璃动作按钮（16dp 胶囊）：按钮底透明 + [glassCardSurface] 采样全局壁纸层（无壁纸/无 shader 时回退实色）。
 * 主色态参照选中卡片：低透明度主题色染玻璃、主题色文字与强调边缘光；危险态仅染文字；禁用态不染色。
 */
@Composable
fun GlassTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, danger: Boolean = false) {
    val colorScheme = MiuixTheme.colorScheme
    val colors = when {
        primary -> ButtonDefaults.textButtonColorsPrimary(color = Color.Transparent, disabledColor = Color.Transparent, textColor = colorScheme.primary)
        danger -> ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent, textColor = colorScheme.error)
        else -> ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent)
    }
    val fallbackColor = when {
        primary && enabled -> colorScheme.background
        primary -> colorScheme.disabledPrimaryButton
        enabled -> colorScheme.secondaryVariant
        else -> colorScheme.disabledSecondaryVariant
    }
    val emphasized = primary && enabled
    val glassTint = if (emphasized) colorScheme.primary.copy(alpha = AppTheme.opacity.selectedSurface) else Color.Transparent
    TextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.glassButtonSurface(fallbackColor = fallbackColor, glassTint = glassTint, edgeTint = colorScheme.primary.takeIf { emphasized }, emphasized = emphasized),
    )
}

@Composable
private fun Modifier.glassButtonSurface(fallbackColor: Color, glassTint: Color, edgeTint: Color?, emphasized: Boolean): Modifier {
    val shape = RoundedCornerShape(ButtonDefaults.CornerRadius)
    // 边缘光沿胶囊内侧一圈光晕发丝，标识可点击按钮区（与玻璃圆钮同配方，miuix EdgeStroke）。
    return clip(shape).glassCardSurface(shape, fallbackColor = fallbackColor, glassTint = glassTint).themeEdgeStroke(shape = shape, tint = edgeTint, emphasized = emphasized)
}
