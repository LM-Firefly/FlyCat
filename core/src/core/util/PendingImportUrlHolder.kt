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

package com.github.yumelira.yumebox.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared holder for pending import URL from deep links / intents.
 * Extracted from MainActivity to allow feature modules to observe without depending on :app.
 */
object PendingImportUrlHolder {
    private val _pendingImportUrl = MutableStateFlow<String?>(null)
    val pendingImportUrl: StateFlow<String?> = _pendingImportUrl.asStateFlow()

    fun set(url: String?) { _pendingImportUrl.value = url }

    fun clear() { _pendingImportUrl.value = null }
}
