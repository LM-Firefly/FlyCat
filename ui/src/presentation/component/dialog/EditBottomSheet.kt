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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Text
import com.github.lmfirefly.flycat.presentation.component.GlassTextButton

@Composable
fun WarningBottomSheet(
    show: MutableState<Boolean>,
    title: String,
    messages: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = { show.value = false },
) {
    AppActionBottomSheet(show = show.value, title = title, onDismissRequest = onDismiss) {
        Column {
            messages.forEachIndexed { index, message ->
                Text(message)
                if (index < messages.lastIndex) {
                    Spacer(modifier = Modifier.height(UiDp.dp8))
                }
            }
            Spacer(modifier = Modifier.height(UiDp.dp16))
            GlassTextButton(
                text = FlyTxt.Component.Button.Confirm,
                onClick = {
                    onConfirm()
                    show.value = false
                },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
            Spacer(modifier = Modifier.height(UiDp.dp16))
        }
    }
}
