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

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_FILE
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_URL

@Stable
internal class ProfileAddFormState {
    val typeIndex = mutableIntStateOf(0)
    val name = mutableStateOf(TextFieldValue())
    val url = mutableStateOf(TextFieldValue())
    val filePath = mutableStateOf("")
    val fileName = mutableStateOf(TextFieldValue())
    val ageSecretKey = mutableStateOf(TextFieldValue())
    val error = mutableStateOf("")
    val isDownloading = mutableStateOf(false)
    val hasShownComplete = mutableStateOf(false)
    val stableHeightPx = mutableIntStateOf(0)

    fun reset() {
        name.value = textValueAtEnd("")
        url.value = textValueAtEnd("")
        filePath.value = ""
        fileName.value = textValueAtEnd("")
        ageSecretKey.value = TextFieldValue()
        error.value = ""
        isDownloading.value = false
        hasShownComplete.value = false
    }

    fun clearTypeInput() {
        when (typeIndex.intValue) {
            PROFILE_IMPORT_TYPE_URL -> url.value = textValueAtEnd("")
            PROFILE_IMPORT_TYPE_FILE -> {
                filePath.value = ""
                fileName.value = textValueAtEnd("")
            }
        }
        error.value = ""
    }
}

@Composable
internal fun rememberProfileAddFormState(): ProfileAddFormState =
    remember { ProfileAddFormState() }

internal fun textValueAtEnd(text: String): TextFieldValue =
    TextFieldValue(text = text, selection = TextRange(text.length))
