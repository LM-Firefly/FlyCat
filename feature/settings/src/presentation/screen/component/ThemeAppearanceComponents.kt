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

package com.github.yumelira.yumebox.feature.settings.presentation.screen.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.EnumSelector
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Palette
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.theme.colorFromArgb
import com.github.yumelira.yumebox.presentation.theme.colorToArgbLong
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField

@Composable
internal fun ThemeModeAndColorItems(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeSeedColorArgb: Long,
    onThemeSeedColorChange: (Long) -> Unit,
) {
    ThemeModeSelectorItem(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
    ThemeColorPickerItem(
        themeSeedColorArgb = themeSeedColorArgb,
        onThemeSeedColorChange = onThemeSeedColorChange,
    )
}

@Composable
internal fun ThemeModeSelectorItem(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    EnumSelector(
        title = FlyTxt.AppSettings.Interface.ThemeModeTitle,
        summary = FlyTxt.AppSettings.Interface.ThemeModeSummary,
        currentValue = themeMode,
        items =
            listOf(
                FlyTxt.AppSettings.Interface.ThemeModeSystem,
                FlyTxt.AppSettings.Interface.ThemeModeLight,
                FlyTxt.AppSettings.Interface.ThemeModeDark,
            ),
        values = ThemeMode.entries,
        onValueChange = onThemeModeChange,
    )
}

@Composable
internal fun ThemeColorPickerItem(
    themeSeedColorArgb: Long,
    onThemeSeedColorChange: (Long) -> Unit,
) {
    ThemeColorPickerItem(
        themeSeedColorArgb = themeSeedColorArgb,
        onThemeSeedColorChange = onThemeSeedColorChange,
        showBottomSheetInPlace = true,
    )
}

@Composable
internal fun ThemeColorPickerItem(
    themeSeedColorArgb: Long,
    onThemeSeedColorChange: (Long) -> Unit,
    showBottomSheetInPlace: Boolean,
    onOpenPickerRequest: (() -> Unit)? = null,
) {
    val showThemeColorPicker = remember { mutableStateOf(false) }

    BasicComponent(
        title = FlyTxt.AppSettings.Interface.ColorThemeTitle,
        summary =
            FlyTxt.AppSettings.Interface.ColorThemeCustomSummary.format(
                formatThemeSeedHex(themeSeedColorArgb)
            ),
        onClick = {
            if (showBottomSheetInPlace) {
                showThemeColorPicker.value = true
            } else {
                onOpenPickerRequest?.invoke()
            }
        },
        endActions = {
            val previewColor =
                remember(themeSeedColorArgb) {
                    runCatching { colorFromArgb(themeSeedColorArgb) }.getOrDefault(Color.White)
                }
            Icon(
                Yume.Palette,
                tint = previewColor,
                contentDescription = null,
                modifier = Modifier.padding(end = UiDp.dp12),
            )
        },
    )

    if (showBottomSheetInPlace) {
        ThemeColorPickerSheet(
            show = showThemeColorPicker.value,
            initialSeedColorArgb = themeSeedColorArgb,
            onDismissRequest = { showThemeColorPicker.value = false },
            onConfirm = { argb ->
                onThemeSeedColorChange(argb)
                showThemeColorPicker.value = false
            },
        )
    }
}

@Composable
internal fun ThemeColorPickerSheet(
    show: Boolean,
    initialSeedColorArgb: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
    renderInRootScaffold: Boolean = true,
) {
    val pickerColor =
        remember(show, initialSeedColorArgb) {
            mutableStateOf(
                runCatching { colorFromArgb(initialSeedColorArgb) }.getOrDefault(Color.White)
            )
        }
    val hexField =
        remember(show, initialSeedColorArgb) {
            val hex = formatThemeSeedHex(initialSeedColorArgb)
            mutableStateOf(TextFieldValue(hex, TextRange(hex.length)))
        }

    AppActionBottomSheet(
        show = show,
        modifier = Modifier,
        title = FlyTxt.AppSettings.Interface.ColorThemePickerTitle,
        onDismissRequest = onDismissRequest,
        enableNestedScroll = true,
        renderInRootScaffold = renderInRootScaffold,
        defaultWindowInsetsPadding = false,
        startAction = { AppBottomSheetCloseAction(onClick = onDismissRequest) },
        endAction = {
            AppBottomSheetConfirmAction(onClick = { onConfirm(colorToArgbLong(pickerColor.value)) })
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                ColorPicker(
                    color = pickerColor.value,
                    onColorChanged = { color ->
                        pickerColor.value = color
                        val hex = formatThemeSeedHex(colorToArgbLong(color))
                        hexField.value = TextFieldValue(hex, TextRange(hex.length))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = hexField.value,
                    onValueChange = { updated ->
                        val upper = updated.copy(text = updated.text.uppercase())
                        hexField.value = upper
                        parseThemeHexColorOrNull(upper.text)?.let { pickerColor.value = it }
                    },
                    label = FlyTxt.AppSettings.Interface.ColorThemeCodeLabel,
                    modifier = Modifier.fillMaxWidth().padding(top = UiDp.dp8),
                )
            }
        },
    )
}

private fun formatThemeSeedHex(argb: Long): String {
    val hex = (argb and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
    return "#$hex"
}

private fun parseThemeHexColorOrNull(input: String): Color? {
    val body = input.removePrefix("#").removePrefix("0x").uppercase()
    val argb = when (body.length) {
        6 -> 0xFF000000L or (body.toLongOrNull(16) ?: return null)
        8 -> body.toLongOrNull(16) ?: return null
        else -> return null
    }
    return colorFromArgb(argb)
}
