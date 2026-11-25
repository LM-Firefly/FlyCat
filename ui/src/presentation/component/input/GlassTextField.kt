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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.themeEdgeStroke
import top.yukonga.miuix.kmp.glass.GlassTextField as MiuixGlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GlassTextFieldShape: RoundedCornerShape = RoundedCornerShape(UiDp.dp18)
private val PasswordOutputMask = OutputTransformation {
    if (length > 0) replace(0, length, "•".repeat(length))
}

/**
 * 液态玻璃输入框：官方 miuix GlassTextField 去底 + [glassCardSurface] 采样全局壁纸层
 * （无壁纸/无 shader 时回退 [MiuixTheme.colorScheme.secondaryContainer] 实色），与玻璃卡片同配方。
 * 弹层内容通过 `LocalGlassBackdrop` 提供页面壁纸层，消费者不在该录制子树内，满足 HyperOS 同子树约束。
 */
@Composable
fun GlassTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    secure: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    MiuixGlassTextField(
        state = state,
        modifier = modifier.glassTextFieldSurface(focused),
        colors = GlassTextFieldDefaults.textFieldColors(
            backgroundColor = Color.Transparent,
            labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
            borderColor = Color.Transparent,
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = inputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        outputTransformation = if (secure) PasswordOutputMask else null,
        trailingIcon = trailingIcon,
        lineLimits = lineLimits,
        interactionSource = interactionSource,
    )
}

@Composable
private fun Modifier.glassTextFieldSurface(focused: Boolean): Modifier {
    // 焦点态以主题色重染边缘光，避免库默认聚焦描边形成第二圈边界。
    return clip(GlassTextFieldShape).glassCardSurface(
        GlassTextFieldShape,
        fallbackColor = MiuixTheme.colorScheme.secondaryContainer,
    ).themeEdgeStroke(
        shape = GlassTextFieldShape,
        tint = if (focused) MiuixTheme.colorScheme.primary else null,
        emphasized = focused,
    )
}
