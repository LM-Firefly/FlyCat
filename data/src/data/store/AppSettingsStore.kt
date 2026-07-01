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

import android.util.Log
import com.github.yumelira.yumebox.core.data.AppSettingsReader
import com.github.yumelira.yumebox.core.data.UpdateSettings
import com.github.yumelira.yumebox.core.model.AppColorTheme
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.tencent.mmkv.MMKV

/**
 * WebDAV backup settings, isolated to feature/meta consumers.
 */
class WebDavSettings(mmkv: MMKV) : MMKVPreference(externalMmkv = mmkv) {
    val webDavUrl by strFlow("")
    val webDavAccount by strFlow("")
    val webDavPassword by strFlow("")
    val webDavDir by strFlow("FlyCat")
}

class AppSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), AppSettingsReader, UpdateSettings {
    override val initialSetupCompleted by boolFlow(false)
    val privacyPolicyAccepted by boolFlow(false)

    val themeMode by enumFlow(ThemeMode.Auto)
    val appLanguage by enumFlow(AppLanguage.System)
    val colorTheme by enumFlow(AppColorTheme.ClassicMonochrome)
    val themeAccentColorArgb by longFlow(0xFF138A74L)
    val invertOnPrimaryColors by boolFlow(false)
    val homePreviewGuideShown by boolFlow(false)
    override val automaticRestart by boolFlow(false)
    override val autoUpdateCurrentProfileOnStart by boolFlow(true)
    val hideAppIcon by boolFlow(false)
    override val excludeFromRecents by boolFlow(false)
    val showTrafficNotification by boolFlow(true)
    val bottomBarAutoHide by boolFlow(true)
    val topBarBlurEnabled by boolFlow(false)
    val classicHomeEnabled by boolFlow(false)
    val moeWallpaperUri by strFlow("")
    val moeWallpaperSourceUri by strFlow("")
    val moeWallpaperZoom by floatFlow(1.0f)
    val moeWallpaperBiasX by floatFlow(0.0f)
    val moeWallpaperBiasY by floatFlow(0.0f)
    val moeHomeQuote by strFlow("时间一分一秒流逝而去 终结一步一步迎面而来")
    val moeHomeQuoteAuthor by strFlow("恋文")
    val moeSidebarExpanded by boolFlow(true)
    val pageScale by floatFlow(1.0f)
    override val singleNodeTest by boolFlow(true)
    val logLevel by intFlow(Log.INFO)
    override val autoCheckAppUpdate by boolFlow(false)
    override val updateSourceKey by strFlow("Stable")
    override val customUserAgent by strFlow("")

    val webDav = WebDavSettings(externalMmkv)

    init {
        migrateLegacyHomeKeys()
    }

    /**
     * One-time rename migration: pre-rename builds persisted the home/wallpaper preferences under
     * `acg*` keys. Copy any legacy value onto the new `moe*` key when the new key is still absent, so
     * upgrading users keep their saved quote, author, wallpaper and crop framing.
     */
    private fun migrateLegacyHomeKeys() {
        fun moveString(old: String, new: String) {
            if (mmkv.containsKey(old) && !mmkv.containsKey(new)) {
                mmkv.decodeString(old)?.let { mmkv.encode(new, it) }
            }
        }
        fun moveFloat(old: String, new: String) {
            if (mmkv.containsKey(old) && !mmkv.containsKey(new)) {
                mmkv.encode(new, mmkv.decodeFloat(old, 0f))
            }
        }
        fun moveBool(old: String, new: String) {
            if (mmkv.containsKey(old) && !mmkv.containsKey(new)) {
                mmkv.encode(new, mmkv.decodeBool(old, false))
            }
        }
        moveString("acgWallpaperUri", "moeWallpaperUri")
        moveString("acgWallpaperSourceUri", "moeWallpaperSourceUri")
        moveString("acgHomeQuote", "moeHomeQuote")
        moveString("acgHomeQuoteAuthor", "moeHomeQuoteAuthor")
        moveFloat("acgWallpaperZoom", "moeWallpaperZoom")
        moveFloat("acgWallpaperBiasX", "moeWallpaperBiasX")
        moveFloat("acgWallpaperBiasY", "moeWallpaperBiasY")
        moveBool("acgSidebarExpanded", "moeSidebarExpanded")
    }
}

class AppStateManager(
    val appSettingsStore: AppSettingsStore,
    val networkSettingsStore: NetworkSettingsStore,
    val featureStore: FeatureStore,
    val profileLinksStore: ProfileLinksStore,
    val proxyDisplaySettingsStore: ProxyDisplaySettingsStore,
    val trafficStatisticsStore: TrafficStatisticsStore,
)
