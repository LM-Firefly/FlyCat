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
import com.github.yumelira.yumebox.core.contract.AppSettingsReader
import com.github.yumelira.yumebox.core.contract.UpdateSettings
import com.github.yumelira.yumebox.core.contract.WebDavSettingsReader
import com.github.yumelira.yumebox.core.model.AppColorTheme
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.tencent.mmkv.MMKV

class AppSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), AppSettingsReader, UpdateSettings {
    override val initialSetupCompleted by boolFlow(false)
    override val privacyPolicyAccepted by boolFlow(false)
    override val themeMode by enumFlow(ThemeMode.Auto)
    override val appLanguage by enumFlow(AppLanguage.System)
    override val colorTheme by enumFlow(AppColorTheme.ClassicMonochrome)
    override val themeAccentColorArgb by longFlow(0xFF138A74L)
    override val invertOnPrimaryColors by boolFlow(false)
    override val homePreviewGuideShown by boolFlow(false)
    override val automaticRestart by boolFlow(false)
    override val autoUpdateCurrentProfileOnStart by boolFlow(true)
    override val hideAppIcon by boolFlow(false)
    override val excludeFromRecents by boolFlow(false)
    override val showTrafficNotification by boolFlow(true)
    override val bottomBarAutoHide by boolFlow(true)
    override val topBarBlurEnabled by boolFlow(false)
    override val classicHomeEnabled by boolFlow(false)
    override val homeHitokotoEnabled by boolFlow(false)
    override val moeWallpaperUri by strFlow("")
    override val moeWallpaperSourceUri by strFlow("")
    override val moeWallpaperZoom by floatFlow(1.0f)
    override val moeWallpaperBiasX by floatFlow(0.0f)
    override val moeWallpaperBiasY by floatFlow(0.0f)
    override val moeHomeQuote by strFlow("一个人走 默守一隅清欢")
    override val moeHomeQuoteAuthor by strFlow("Firefly")
    override val moeSidebarExpanded by boolFlow(true)
    override val pageScale by floatFlow(1.0f)
    override val logLevel by intFlow(Log.INFO)
    override val autoCheckAppUpdate by boolFlow(false)
    override val updateSourceKey by strFlow("Stable")
    override val customUserAgent by strFlow("")
    override val webDav: WebDavSettingsReader = WebDavSettings(externalMmkv)

    init { migrateLegacyHomeKeys() }

    /**
     * One-time rename migration: pre-rename builds persisted the home/wallpaper preferences under
     * `acg*` keys. Copy any legacy value onto the new `moe*` key when the new key is still absent, so
     * upgrading users keep their saved quote, author, wallpaper and crop framing.
     */
    private fun migrateLegacyHomeKeys() {
        if (mmkv.decodeBool("_legacyHomeKeysMigrated", false)) return
        fun moveString(old: String, new: String) { if (mmkv.containsKey(old) && !mmkv.containsKey(new)) { mmkv.decodeString(old)?.let { mmkv.encode(new, it) } } }
        fun moveFloat(old: String, new: String) { if (mmkv.containsKey(old) && !mmkv.containsKey(new)) { mmkv.encode(new, mmkv.decodeFloat(old, 0f)) } }
        fun moveBool(old: String, new: String) { if (mmkv.containsKey(old) && !mmkv.containsKey(new)) { mmkv.encode(new, mmkv.decodeBool(old, false)) } }
        moveString("acgWallpaperUri", "moeWallpaperUri")
        moveString("acgWallpaperSourceUri", "moeWallpaperSourceUri")
        moveString("acgHomeQuote", "moeHomeQuote")
        moveString("acgHomeQuoteAuthor", "moeHomeQuoteAuthor")
        moveFloat("acgWallpaperZoom", "moeWallpaperZoom")
        moveFloat("acgWallpaperBiasX", "moeWallpaperBiasX")
        moveFloat("acgWallpaperBiasY", "moeWallpaperBiasY")
        moveBool("acgSidebarExpanded", "moeSidebarExpanded")
        mmkv.encode("_legacyHomeKeysMigrated", true)
    }
}

/**
 * WebDAV backup settings, isolated to feature/meta consumers.
 */
class WebDavSettings(mmkv: MMKV) : MMKVPreference(externalMmkv = mmkv), WebDavSettingsReader {
    override val webDavUrl by strFlow("")
    override val webDavAccount by strFlow("")
    override val webDavPassword by strFlow("")
    override val webDavDir by strFlow("FlyCat")
}

class AppStateManager(
    val appSettingsStore: AppSettingsStore,
    val networkSettingsStore: NetworkSettingsStore,
    val featureStore: FeatureStore,
    val proxyDisplaySettingsStore: ProxyDisplaySettingsStore,
    val trafficStatisticsStore: TrafficStatisticsStore,
)
