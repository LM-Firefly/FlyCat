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

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.runtime.service.util


import android.content.Context

object CoreRuntimeConfig {
    private const val SETTINGS_STORE_ID = "settings"
    private const val CUSTOM_USER_AGENT_KEY = "customUserAgent"

    fun applyCustomUserAgentIfPresent(context: Context) {
        // The custom User-Agent is now injected into the compiled config (global-ua) rather than
        // pushed to an in-process core.
    }
}
