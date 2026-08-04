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

package com.github.yumeyucca.yumebox.runtime.service.util

import com.github.yumeyucca.yumebox.data.model.AppIconStyle
import com.github.yumeyucca.yumebox.runtime.service.R
import com.tencent.mmkv.MMKV

object ServiceLogoIcons {
    private const val SETTINGS_STORE_ID = "settings"
    private const val APP_ICON_STYLE_KEY = "appIconStyle"

    fun resId(): Int =
        if (isClassic()) {
            R.drawable.ic_logo_service_classic
        } else {
            R.drawable.ic_logo_service
        }

    fun isClassic(): Boolean =
        runCatching {
                val settings = MMKV.mmkvWithID(SETTINGS_STORE_ID, MMKV.MULTI_PROCESS_MODE)
                settings.decodeString(APP_ICON_STYLE_KEY) == AppIconStyle.Classic.name
            }
            .getOrDefault(false)
}
