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

import com.github.yumelira.yumebox.core.contract.FeatureStoreReader
import com.github.yumelira.yumebox.core.contract.SubStoreSettings
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.tencent.mmkv.MMKV

class FeatureStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), FeatureStoreReader, SubStoreSettings {
    private companion object {
        const val KEY_LAST_APP_VERSION_CODE = "last_app_version_code"
        const val KEY_POST_UPDATE_COLD_START_PENDING = "post_update_cold_start_pending"
    }

    override val allowLanAccess by boolFlow(false)
    override val backendPort by intFlow(8081)
    override val frontendPort by intFlow(8080)
    override val selectedPanelType by intFlow(0)
    override val panelOpenMode by enumFlow(LinkOpenMode.IN_APP)
    override val showWebControlInProxy by boolFlow(false)
    override val exitUiWhenBackground by boolFlow(false)
    override val subStoreAutoCloseModeOrdinal by intFlow(-1)

    override var isFirstOpen by bool(true)

    fun markFirstOpenHandled() {
        isFirstOpen = false
    }

    fun isFirstTimeOpen(): Boolean = isFirstOpen

    fun syncAppVersion(versionCode: Int): Boolean {
        val previousVersionCode = mmkv.decodeInt(KEY_LAST_APP_VERSION_CODE, Int.MIN_VALUE)
        val isUpdated = previousVersionCode != Int.MIN_VALUE && previousVersionCode != versionCode
        if (isUpdated) {
            mmkv.encode(KEY_POST_UPDATE_COLD_START_PENDING, true)
        }
        mmkv.encode(KEY_LAST_APP_VERSION_CODE, versionCode)
        return isUpdated
    }

    /**
     * Read-and-clear post-update cold-start marker. Returns true only for the first consumer after
     * an app update.
     */
    override fun consumePostUpdateColdStartPending(): Boolean {
        val pending = mmkv.decodeBool(KEY_POST_UPDATE_COLD_START_PENDING, false)
        if (pending) {
            mmkv.removeValueForKey(KEY_POST_UPDATE_COLD_START_PENDING)
        }
        return pending
    }

    fun setLibraryCacheVersion(libraryName: String, version: Int) {
        mmkv.encode("library_version_" + libraryName, version)
    }

    fun getLibraryCacheVersion(libraryName: String): Int =
        mmkv.decodeInt("library_version_" + libraryName, -1)

    fun setAssetCacheVersion(assetPath: String, version: Int) {
        mmkv.encode("asset_version_" + assetPath, version)
    }

    fun getAssetCacheVersion(assetPath: String): Int =
        mmkv.decodeInt("asset_version_" + assetPath, -1)

    fun clearAll() {
        mmkv.edit().clear().apply()
    }
}
