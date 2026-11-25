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

package com.github.lmfirefly.flycat.presentation.component.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppTextFieldDialog(
    show: Boolean,
    title: String,
    state: TextFieldState,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    renderInRootScaffold: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
) {
    AppDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        AppDialogColumn {
            val textFieldModifier = Modifier.fillMaxWidth().padding(bottom = if (supportingContent == null) UiDp.dp0 else UiDp.dp8)
            GlassTextField(
                state = state,
                modifier = textFieldModifier,
                label = label,
                useLabelAsPlaceholder = useLabelAsPlaceholder,
                lineLimits = lineLimits,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                trailingIcon = trailingIcon,
            )
            supportingContent?.invoke()
            DialogButtonRow(onCancel = onDismissRequest, onConfirm = onConfirm)
        }
    }
}
