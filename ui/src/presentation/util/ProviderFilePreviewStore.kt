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

package com.github.yumelira.yumebox.presentation.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.yumelira.yumebox.presentation.language.LanguageScope

object ProviderFilePreviewStore {
    var title by mutableStateOf("")
        private set

    var content by mutableStateOf("")
        private set

    var language by mutableStateOf(LanguageScope.Yaml)
        private set

    fun setup(
        title: String,
        content: String,
        language: LanguageScope = LanguageScope.Yaml,
    ) {
        this.title = title
        this.content = content
        this.language = language
    }
}
