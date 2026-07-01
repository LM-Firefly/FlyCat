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

package com.github.yumelira.yumebox.data.store

import com.tencent.mmkv.MMKV

class MMKVProvider {

    companion object {
        const val ID_PROFILES = "profiles"
        const val ID_SETTINGS = "settings"
        const val ID_NETWORK_SETTINGS = "network_settings"
        const val ID_SUBSTORE = "substore"
        const val ID_PROXY_DISPLAY = "proxy_display"
        const val ID_TRAFFIC_STATISTICS = "traffic_statistics"
        const val ID_PROFILE_LINKS = "profile_links"
        const val ID_SERVICE_CACHE = "service_cache"
        const val ID_OVERRIDE_BINDINGS = "override_bindings"
    }

    fun getDefaultMMKV(): MMKV = MMKV.defaultMMKV()

    fun getMMKV(id: String): MMKV = MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE)
}
