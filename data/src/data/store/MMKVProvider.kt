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

package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.core.contract.BulkStoreReset
import com.github.yumelira.yumebox.core.contract.StoreSynchronizer
import com.tencent.mmkv.MMKV

class MMKVProvider : BulkStoreReset, StoreSynchronizer {
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
        const val ID_CHINA_APP_DETECTOR_CACHE = "china_app_detector_cache_v2"
        const val ID_REMOTE_CONTROLLER = "remote_controller"
    }
    private val activeInstances = mutableMapOf<String, MMKV>()
    fun getMMKV(id: String): MMKV = activeInstances.getOrPut(id) { MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE) }
    override fun clearStore(id: String) { getMMKV(id).clearAll() }
    /** Synchronize all MMKV data to disk. Call before [kotlin.system.exitProcess]. */
    override fun syncAll() {
        activeInstances.values.forEach { runCatching { it.sync() } }
        // Also sync the default instance which may hold data from non-managed stores
        runCatching { MMKV.defaultMMKV().sync() }
    }
}
