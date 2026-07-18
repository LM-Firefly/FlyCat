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

package com.github.yumelira.yumebox.core.model.backup

import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.model.AppColorTheme
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.Imported
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.PROXY_SHEET_HEIGHT_FRACTION_DEFAULT
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.core.model.ProxySortMode
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.TunStack
import java.util.UUID
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupManifest(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appId: String = "",
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val plaintext: Boolean = true,
    val includes: List<String> = emptyList(),
)

@Serializable
data class BackupPayload(
    val appSettings: AppSettingsBackup = AppSettingsBackup(),
    val networkSettings: NetworkSettingsBackup = NetworkSettingsBackup(),
    val featureSettings: FeatureSettingsBackup = FeatureSettingsBackup(),
    val proxyDisplaySettings: ProxyDisplaySettingsBackup = ProxyDisplaySettingsBackup(),
    val remoteController: RemoteControllerBackup = RemoteControllerBackup(),
    val webDav: WebDavBackup = WebDavBackup(),
    val profiles: ProfilesBackup = ProfilesBackup(),
    val service: ServiceBackup = ServiceBackup(),
)

@Serializable
data class AppSettingsBackup(
    val themeMode: ThemeMode = ThemeMode.Auto,
    val appLanguage: AppLanguage = AppLanguage.System,
    val colorTheme: AppColorTheme = AppColorTheme.ClassicMonochrome,
    val themeAccentColorArgb: Long = 0xFF138A74L,
    val invertOnPrimaryColors: Boolean = false,
    val homePreviewGuideShown: Boolean = false,
    val automaticRestart: Boolean = false,
    val autoUpdateCurrentProfileOnStart: Boolean = true,
    val hideAppIcon: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val showTrafficNotification: Boolean = true,
    val bottomBarAutoHide: Boolean = true,
    val topBarBlurEnabled: Boolean = false,
    val classicHomeEnabled: Boolean = false,
    val homeHitokotoEnabled: Boolean = false,
    val moeWallpaperUri: String = "",
    val moeWallpaperSourceUri: String = "",
    val moeWallpaperZoom: Float = 1.0f,
    val moeWallpaperBiasX: Float = 0.0f,
    val moeWallpaperBiasY: Float = 0.0f,
    val moeHomeQuote: String = "",
    val moeHomeQuoteAuthor: String = "",
    val moeSidebarExpanded: Boolean = true,
    val pageScale: Float = 1.0f,
    val logLevel: Int = 2, // Log.INFO
    val autoCheckAppUpdate: Boolean = false,
    val updateSourceKey: String = "Stable",
    val customUserAgent: String = "",
)

@Serializable
data class NetworkSettingsBackup(
    val runMode: RunMode = RunMode.VpnService,
    val bypassPrivateNetwork: Boolean = true,
    val dnsHijack: Boolean = true,
    val allowBypass: Boolean = true,
    val enableIPv6: Boolean = false,
    val systemProxy: Boolean = true,
    val tunStack: TunStack = TunStack.System,
    val tunRouteExcludeAddress: List<String> = emptyList(),
    val tunIfName: String = "FlyCat",
    val tunMtu: Int = 9000,
    val tunAutoRoute: Boolean = true,
    val tunStrictRoute: Boolean = true,
    val tunAutoRedirect: Boolean = true,
    val tunIncludeAndroidUser: List<Int> = listOf(0, 10),
    val tunDnsMode: TunDnsMode = TunDnsMode.RedirHost,
    val tunFakeIpRange: String = "198.18.0.1/16",
    val tunFakeIpRange6: String = "fc00::/18",
    val accessControlMode: AccessControlMode = AccessControlMode.ALLOW_ALL,
    val accessControlPackages: Set<String> = emptySet(),
    val accessControlSelectedFirst: Boolean = true,
    val accessControlShowSystemApps: Boolean = false,
)

@Serializable
data class FeatureSettingsBackup(
    val allowLanAccess: Boolean = false,
    val backendPort: Int = 8081,
    val frontendPort: Int = 8080,
    val selectedPanelType: Int = 0,
    val panelOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    val showWebControlInProxy: Boolean = false,
    val exitUiWhenBackground: Boolean = false,
    val isFirstOpen: Boolean = true,
)

@Serializable
data class ProxyDisplaySettingsBackup(
    val sortMode: ProxySortMode = ProxySortMode.DEFAULT,
    val displayMode: ProxyDisplayMode = ProxyDisplayMode.DOUBLE_DETAILED,
    val proxyMode: TunnelState.Mode = TunnelState.Mode.Rule,
    val sheetHeightFraction: Float = PROXY_SHEET_HEIGHT_FRACTION_DEFAULT,
)

@Serializable
data class RemoteControllerBackup(
    val controllerEnabled: Boolean = false,
    val backends: List<RemoteBackend> = emptyList(),
    val activeBackendId: String = "",
)

@Serializable
data class WebDavBackup(
    val url: String = "",
    val account: String = "",
    val password: String = "",
    val directory: String = "FlyCat",
)

@Serializable
data class ProfilesBackup(
    val imported: List<ImportedBackup> = emptyList(),
    val profileOrder: List<String> = emptyList(),
)

@Serializable
data class ImportedBackup(
    val uuid: String,
    val name: String,
    val type: Profile.Type,
    val source: String,
    val interval: Long,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long,
    val createdAt: Long,
    val ageSecretKey: String = "",
) {
    fun toImported(): Imported =
        Imported(
            uuid = UUID.fromString(uuid),
            name = name,
            type = type,
            source = source,
            interval = interval,
            upload = upload,
            download = download,
            total = total,
            expire = expire,
            createdAt = createdAt,
            ageSecretKey = ageSecretKey,
        )

    companion object {
        fun from(imported: Imported): ImportedBackup =
            ImportedBackup(
                uuid = imported.uuid.toString(),
                name = imported.name,
                type = imported.type,
                source = imported.source,
                interval = imported.interval,
                upload = imported.upload,
                download = imported.download,
                total = imported.total,
                expire = imported.expire,
                createdAt = imported.createdAt,
                ageSecretKey = imported.ageSecretKey,
            )
    }
}

@Serializable
data class ServiceBackup(
    val activeProfile: String? = null,
)
