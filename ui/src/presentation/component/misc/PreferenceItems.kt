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

package com.github.lmfirefly.flycat.presentation.component.misc

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import com.github.lmfirefly.flycat.presentation.component.input.EnumSelector
import com.github.lmfirefly.flycat.presentation.component.input.GlassSwitch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun PreferenceSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = {
            GlassSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        onClick = { onCheckedChange(!checked) },
        role = Role.Switch,
        enabled = enabled,
    )
}

@Composable
fun PreferenceArrowItem(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    holdDownState: Boolean = false,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: @Composable (() -> Unit)? = null,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        holdDownState = holdDownState,
        endActions = endActions ?: {},
        bottomAction = bottomAction,
        onClick = onClick,
    )
}

@Composable
fun PreferenceValueItem(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = endActions,
        onClick = onClick,
    )
}

@Composable
fun <T> PreferenceEnumItem(
    title: String,
    currentValue: T,
    items: List<String>,
    values: List<T>,
    onValueChange: (T) -> Unit,
    summary: String? = null,
) {
    EnumSelector(
        title = title,
        summary = summary,
        currentValue = currentValue,
        items = items,
        values = values,
        onValueChange = onValueChange,
    )
}
