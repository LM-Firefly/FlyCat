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

package com.github.lmfirefly.flycat.feature.editor.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.lmfirefly.flycat.presentation.theme.AppColors
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

object EditorColorSynchronizer {
    fun createColorScheme(isDark: Boolean, appColors: AppColors = AppColors()): EditorColorScheme {
        val editorColors = appColors.editor
        return object : EditorColorScheme(isDark) {
            override fun applyDefault() {
                super.applyDefault()
                // 背景遵循全局设置：主背景透明，由页面容器（全局背景：壁纸/动态渐变/纯色 surface）透出。
                setColor(WHOLE_BACKGROUND, Color.Transparent.toArgb())
                setColor(
                    TEXT_NORMAL,
                    if (isDark) editorColors.darkText.toArgb() else editorColors.lightText.toArgb(),
                )
                setColor(
                    LINE_NUMBER,
                    if (isDark) {
                        editorColors.darkLineNumber.toArgb()
                    } else {
                        editorColors.lightLineNumber.toArgb()
                    },
                )
                setColor(LINE_NUMBER_BACKGROUND, Color.Transparent.toArgb())
                setColor(
                    CURRENT_LINE,
                    if (isDark) {
                        editorColors.darkCurrentLine.toArgb()
                    } else {
                        editorColors.lightCurrentLine.toArgb()
                    },
                )
                setColor(SELECTION_INSERT, editorColors.accent.toArgb())
                setColor(SELECTION_HANDLE, editorColors.accent.toArgb())
                setColor(
                    SELECTED_TEXT_BACKGROUND,
                    if (isDark) {
                        editorColors.darkSelectionBackground.toArgb()
                    } else {
                        editorColors.lightSelectionBackground.toArgb()
                    },
                )
                setColor(
                    TEXT_ACTION_WINDOW_BACKGROUND,
                    if (isDark) {
                        editorColors.darkTextActionBackground.toArgb()
                    } else {
                        editorColors.lightTextActionBackground.toArgb()
                    },
                )
                setColor(
                    TEXT_ACTION_WINDOW_ICON_COLOR,
                    if (isDark) {
                        editorColors.darkTextActionIcon.toArgb()
                    } else {
                        editorColors.lightTextActionIcon.toArgb()
                    },
                )
                setColor(
                    HIGHLIGHTED_DELIMITERS_FOREGROUND,
                    if (isDark) {
                        editorColors.delimiterDark.toArgb()
                    } else {
                        editorColors.delimiterLight.toArgb()
                    },
                )
            }
        }
    }
    fun updateColors(editor: CodeEditor, isDark: Boolean, appColors: AppColors = AppColors()) {
        val editorColors = appColors.editor
        val scheme = editor.colorScheme
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, editorColors.accent.toArgb())
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, editorColors.accent.toArgb())
        scheme.setColor(
            EditorColorScheme.SELECTED_TEXT_BACKGROUND,
            if (isDark) {
                editorColors.darkSelectionBackground.toArgb()
            } else {
                editorColors.lightSelectionBackground.toArgb()
            },
        )
        scheme.setColor(
            EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND,
            if (isDark) {
                editorColors.darkTextActionBackground.toArgb()
            } else {
                editorColors.lightTextActionBackground.toArgb()
            },
        )
        scheme.setColor(
            EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR,
            if (isDark) {
                editorColors.darkTextActionIcon.toArgb()
            } else {
                editorColors.lightTextActionIcon.toArgb()
            },
        )
        scheme.setColor(
            EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND,
            if (isDark) {
                editorColors.delimiterDark.toArgb()
            } else {
                editorColors.delimiterLight.toArgb()
            },
        )
        scheme.setColor(
            EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND,
            editorColors.delimiterBackground.toArgb(),
        )
        // 主题切换后重申背景透明（TextMate 主题自带实色背景，onThemeChanged 会重新着色）。
        makeBackgroundTransparent(scheme)
    }
    /**
     * 背景透明化：主背景与行号栏背景置透明，让页面容器透出全局背景（AppBackground 开启时为壁纸/动态渐变，关闭时为纯色 surface）。
     * TextMate 主题自带实色背景，创建/换主题后需调用本函数覆盖。
     */
    fun makeBackgroundTransparent(scheme: EditorColorScheme) {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.Transparent.toArgb())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color.Transparent.toArgb())
    }
}
