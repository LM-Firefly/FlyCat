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

package com.github.yumelira.yumebox.screen.settings.backup

import android.app.Application
import android.content.Intent
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.core.util.moeWallpaperFile
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.Preference
import com.github.yumelira.yumebox.data.store.ProfileLinksStore
import com.github.yumelira.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.service.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.profile.ProfileStore
import com.github.yumelira.yumebox.runtime.service.util.importedDir
import com.github.yumelira.yumebox.substore.SubStorePaths
import com.github.yumelira.yumebox.substore.SubStoreServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BackupRepository(
    private val application: Application,
    private val appSettings: AppSettingsStore,
    private val networkSettings: NetworkSettingsStore,
    private val featureSettings: FeatureStore,
    private val proxyDisplaySettings: ProxyDisplaySettingsStore,
    private val profileLinks: ProfileLinksStore,
    private val remoteController: RemoteControllerStore,
    private val proxyFacade: ProxyFacade,
    private val mmkvProvider: MMKVProvider,
    private val archiveManager: BackupArchiveManager = BackupArchiveManager(),
) {
    suspend fun exportBackup(output: OutputStream) =
        withContext(Dispatchers.IO) {
            writeCurrentBackup(output)
        }

    suspend fun restoreBackup(input: InputStream) =
        withContext(Dispatchers.IO) {
            val extractDir = freshCacheDir("backup-restore")
            val rollbackFile = application.cacheDir.resolve("backup-restore-rollback.zip")
            var rollbackDir: File? = null

            try {
                val extracted = archiveManager.readArchive(input, extractDir)
                require(
                    extracted.manifest.appId.isBlank() ||
                        extracted.manifest.appId == application.packageName
                ) {
                    "Backup belongs to ${extracted.manifest.appId}"
                }

                rollbackFile.outputStream().use(::writeCurrentBackup)
                try {
                    stopRuntimeBeforeRestore()
                    applyBackup(extracted)
                    notifyRestored()
                } catch (error: Exception) {
                    runCatching {
                        val stagingDir = freshCacheDir("backup-rollback")
                        rollbackDir = stagingDir
                        rollbackFile.inputStream().use { input ->
                            archiveManager.readArchive(input, stagingDir).also(::applyBackup)
                        }
                    }
                    throw error
                }
            } finally {
                rollbackFile.delete()
                extractDir.deleteRecursively()
                rollbackDir?.deleteRecursively()
            }
        }

    fun defaultBackupFileName(now: Long = System.currentTimeMillis()): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        return "YumeBox-backup-$timestamp.zip"
    }

    private fun writeCurrentBackup(output: OutputStream) {
        archiveManager.writeArchive(
            output = output,
            manifest =
                BackupManifest(
                    appId = application.packageName,
                    appVersion = BuildConfig.VERSION_NAME,
                    createdAt = System.currentTimeMillis(),
                    plaintext = true,
                    includes =
                        listOf(
                            "settings",
                            "network_settings",
                            "feature_settings",
                            "proxy_display",
                            "profile_links",
                            "remote_controller",
                            "profiles",
                            "active_profile",
                            "overrides",
                            "substore_data",
                            "moe_wallpaper",
                        ),
                ),
            payload = collectPayload(),
            files = currentFiles(),
        )
    }

    private fun collectPayload(): BackupPayload =
        BackupPayload(
            appSettings =
                AppSettingsBackup(
                    themeMode = appSettings.themeMode.value,
                    appLanguage = appSettings.appLanguage.value,
                    colorTheme = appSettings.colorTheme.value,
                    themeAccentColorArgb = appSettings.themeAccentColorArgb.value,
                    invertOnPrimaryColors = appSettings.invertOnPrimaryColors.value,
                    homePreviewGuideShown = appSettings.homePreviewGuideShown.value,
                    automaticRestart = appSettings.automaticRestart.value,
                    autoUpdateCurrentProfileOnStart =
                        appSettings.autoUpdateCurrentProfileOnStart.value,
                    hideAppIcon = appSettings.hideAppIcon.value,
                    excludeFromRecents = appSettings.excludeFromRecents.value,
                    showTrafficNotification = appSettings.showTrafficNotification.value,
                    bottomBarAutoHide = appSettings.bottomBarAutoHide.value,
                    topBarBlurEnabled = appSettings.topBarBlurEnabled.value,
                    classicHomeEnabled = appSettings.classicHomeEnabled.value,
                    moeWallpaperUri = appSettings.moeWallpaperUri.value,
                    moeWallpaperSourceUri = appSettings.moeWallpaperSourceUri.value,
                    moeWallpaperZoom = appSettings.moeWallpaperZoom.value,
                    moeWallpaperBiasX = appSettings.moeWallpaperBiasX.value,
                    moeWallpaperBiasY = appSettings.moeWallpaperBiasY.value,
                    moeHomeQuote = appSettings.moeHomeQuote.value,
                    moeHomeQuoteAuthor = appSettings.moeHomeQuoteAuthor.value,
                    moeSidebarExpanded = appSettings.moeSidebarExpanded.value,
                    pageScale = appSettings.pageScale.value,
                    singleNodeTest = appSettings.singleNodeTest.value,
                    customUserAgent = appSettings.customUserAgent.value,
                ),
            networkSettings =
                NetworkSettingsBackup(
                    proxyMode = networkSettings.proxyMode.value,
                    bypassPrivateNetwork = networkSettings.bypassPrivateNetwork.value,
                    dnsHijack = networkSettings.dnsHijack.value,
                    allowBypass = networkSettings.allowBypass.value,
                    enableIPv6 = networkSettings.enableIPv6.value,
                    systemProxy = networkSettings.systemProxy.value,
                    tunStack = networkSettings.tunStack.value,
                    tunRouteExcludeAddress = networkSettings.tunRouteExcludeAddress.value,
                    rootTunIfName = networkSettings.rootTunIfName.value,
                    rootTunMtu = networkSettings.rootTunMtu.value,
                    rootTunAutoRoute = networkSettings.rootTunAutoRoute.value,
                    rootTunStrictRoute = networkSettings.rootTunStrictRoute.value,
                    rootTunAutoRedirect = networkSettings.rootTunAutoRedirect.value,
                    rootTunIncludeAndroidUser = networkSettings.rootTunIncludeAndroidUser.value,
                    rootTunRouteExcludeAddress = networkSettings.rootTunRouteExcludeAddress.value,
                    rootTunDnsMode = networkSettings.rootTunDnsMode.value,
                    rootTunFakeIpRange = networkSettings.rootTunFakeIpRange.value,
                    rootTunFakeIpRange6 = networkSettings.rootTunFakeIpRange6.value,
                    accessControlMode = networkSettings.accessControlMode.value,
                    accessControlPackages = networkSettings.accessControlPackages.value,
                    accessControlSelectedFirst = networkSettings.accessControlSelectedFirst.value,
                    accessControlShowSystemApps = networkSettings.accessControlShowSystemApps.value,
                    accessControlSortMode = networkSettings.accessControlSortMode.value,
                ),
            featureSettings =
                FeatureSettingsBackup(
                    allowLanAccess = featureSettings.allowLanAccess.value,
                    backendPort = featureSettings.backendPort.value,
                    frontendPort = featureSettings.frontendPort.value,
                    selectedPanelType = featureSettings.selectedPanelType.value,
                    panelOpenMode = featureSettings.panelOpenMode.value,
                    showWebControlInProxy = featureSettings.showWebControlInProxy.value,
                    exitUiWhenBackground = featureSettings.exitUiWhenBackground.value,
                    isFirstOpen = featureSettings.isFirstOpen,
                ),
            proxyDisplaySettings =
                ProxyDisplaySettingsBackup(
                    sortMode = proxyDisplaySettings.sortMode.value,
                    displayMode = proxyDisplaySettings.displayMode.value,
                    proxyMode = proxyDisplaySettings.proxyMode.value,
                    sheetHeightFraction = proxyDisplaySettings.sheetHeightFraction.value,
                ),
            profileLinks =
                ProfileLinksBackup(
                    linkOpenMode = profileLinks.linkOpenMode.value,
                    links = profileLinks.links.value,
                    defaultLinkId = profileLinks.defaultLinkId.value,
                ),
            remoteController =
                RemoteControllerBackup(
                    controllerEnabled = remoteController.controllerEnabled.value,
                    backends = remoteController.backends.value,
                    activeBackendId = remoteController.activeBackendId.value,
                ),
            profiles =
                ProfilesBackup(
                    imported = ProfileStore.loadImported().map(ImportedBackup::from),
                    profileOrder = ProfileStore.loadProfileOrder().map(UUID::toString),
                ),
            service = ServiceBackup(activeProfile = ServiceStore().activeProfile?.toString()),
        )

    private fun applyBackup(extracted: BackupArchiveManager.ExtractedBackup) {
        val wallpaperFile = extracted.moeWallpaperFile.takeIf(File::isFile)
        val appBackup =
            if (wallpaperFile != null) {
                extracted.payload.appSettings.copy(
                    moeWallpaperUri = "file://${application.moeWallpaperFile().absolutePath}"
                )
            } else {
                extracted.payload.appSettings
            }

        clearConfigurationStores()
        applyAppSettings(appBackup)
        applyNetworkSettings(extracted.payload.networkSettings)
        applyFeatureSettings(extracted.payload.featureSettings)
        applyProxyDisplaySettings(extracted.payload.proxyDisplaySettings)
        applyProfileLinks(extracted.payload.profileLinks)
        applyRemoteController(extracted.payload.remoteController)
        applyProfiles(extracted.payload.profiles)
        ServiceStore().activeProfile = extracted.payload.service.activeProfile?.let(UUID::fromString)

        replaceBackupDirectory(extracted.importedDir, application.importedDir)
        replaceBackupDirectory(extracted.overridesDir, application.filesDir.resolve("overrides"))
        replaceBackupDirectory(extracted.subStoreDataDir, SubStorePaths.dataDir)
        if (wallpaperFile != null) {
            application.moeWallpaperFile().also { target ->
                target.parentFile?.mkdirs()
                wallpaperFile.copyTo(target, overwrite = true)
            }
        } else {
            application.moeWallpaperFile().delete()
        }
    }

    private suspend fun stopRuntimeBeforeRestore() {
        runCatching { proxyFacade.stopProxy() }
        runCatching { SubStoreServiceController.stopService(application) }
    }

    private suspend fun notifyRestored() {
        application.sendBroadcast(
            Intent(Intents.actionProfileChanged(application.packageName)).setPackage(
                application.packageName
            )
        )
        application.sendBroadcast(
            Intent(Intents.actionOverrideChanged(application.packageName)).setPackage(
                application.packageName
            )
        )
        runCatching { proxyFacade.reconcileRuntimeState() }
    }

    private fun clearConfigurationStores() {
        listOf(
                "settings",
                "network_settings",
                "substore",
                "proxy_display",
                "profile_links",
                "remote_controller",
                "profiles",
                "service",
                "override_bindings",
            )
            .forEach { id -> mmkvProvider.getMMKV(id).clearAll() }
        refreshPreferenceCachesAfterRawClear()
    }

    private fun refreshPreferenceCachesAfterRawClear() {
        refreshAfterRawStoreClear(
            appSettings.themeMode,
            appSettings.appLanguage,
            appSettings.colorTheme,
            appSettings.themeAccentColorArgb,
            appSettings.invertOnPrimaryColors,
            appSettings.homePreviewGuideShown,
            appSettings.automaticRestart,
            appSettings.autoUpdateCurrentProfileOnStart,
            appSettings.hideAppIcon,
            appSettings.excludeFromRecents,
            appSettings.showTrafficNotification,
            appSettings.bottomBarAutoHide,
            appSettings.topBarBlurEnabled,
            appSettings.classicHomeEnabled,
            appSettings.moeWallpaperUri,
            appSettings.moeWallpaperSourceUri,
            appSettings.moeWallpaperZoom,
            appSettings.moeWallpaperBiasX,
            appSettings.moeWallpaperBiasY,
            appSettings.moeHomeQuote,
            appSettings.moeHomeQuoteAuthor,
            appSettings.moeSidebarExpanded,
            appSettings.pageScale,
            appSettings.singleNodeTest,
            appSettings.customUserAgent,
        )
        refreshAfterRawStoreClear(
            networkSettings.proxyMode,
            networkSettings.bypassPrivateNetwork,
            networkSettings.dnsHijack,
            networkSettings.allowBypass,
            networkSettings.enableIPv6,
            networkSettings.systemProxy,
            networkSettings.tunStack,
            networkSettings.tunRouteExcludeAddress,
            networkSettings.rootTunIfName,
            networkSettings.rootTunMtu,
            networkSettings.rootTunAutoRoute,
            networkSettings.rootTunStrictRoute,
            networkSettings.rootTunAutoRedirect,
            networkSettings.rootTunIncludeAndroidUser,
            networkSettings.rootTunRouteExcludeAddress,
            networkSettings.rootTunDnsMode,
            networkSettings.rootTunFakeIpRange,
            networkSettings.rootTunFakeIpRange6,
            networkSettings.accessControlMode,
            networkSettings.accessControlPackages,
            networkSettings.accessControlSelectedFirst,
            networkSettings.accessControlShowSystemApps,
            networkSettings.accessControlSortMode,
        )
        refreshAfterRawStoreClear(
            featureSettings.allowLanAccess,
            featureSettings.backendPort,
            featureSettings.frontendPort,
            featureSettings.selectedPanelType,
            featureSettings.panelOpenMode,
            featureSettings.showWebControlInProxy,
            featureSettings.exitUiWhenBackground,
        )
        refreshAfterRawStoreClear(
            proxyDisplaySettings.sortMode,
            proxyDisplaySettings.displayMode,
            proxyDisplaySettings.proxyMode,
            proxyDisplaySettings.sheetHeightFraction,
        )
        refreshAfterRawStoreClear(
            profileLinks.linkOpenMode,
            profileLinks.links,
            profileLinks.defaultLinkId,
        )
        refreshAfterRawStoreClear(
            remoteController.controllerEnabled,
            remoteController.backends,
            remoteController.activeBackendId,
        )
    }

    private fun applyAppSettings(value: AppSettingsBackup) {
        appSettings.themeMode.set(value.themeMode)
        appSettings.appLanguage.set(value.appLanguage)
        appSettings.colorTheme.set(value.colorTheme)
        appSettings.themeAccentColorArgb.set(value.themeAccentColorArgb)
        appSettings.invertOnPrimaryColors.set(value.invertOnPrimaryColors)
        appSettings.homePreviewGuideShown.set(value.homePreviewGuideShown)
        appSettings.automaticRestart.set(value.automaticRestart)
        appSettings.autoUpdateCurrentProfileOnStart.set(value.autoUpdateCurrentProfileOnStart)
        appSettings.hideAppIcon.set(value.hideAppIcon)
        appSettings.excludeFromRecents.set(value.excludeFromRecents)
        appSettings.showTrafficNotification.set(value.showTrafficNotification)
        appSettings.bottomBarAutoHide.set(value.bottomBarAutoHide)
        appSettings.topBarBlurEnabled.set(value.topBarBlurEnabled)
        appSettings.classicHomeEnabled.set(value.classicHomeEnabled)
        appSettings.moeWallpaperUri.set(value.moeWallpaperUri)
        appSettings.moeWallpaperSourceUri.set(value.moeWallpaperSourceUri)
        appSettings.moeWallpaperZoom.set(value.moeWallpaperZoom)
        appSettings.moeWallpaperBiasX.set(value.moeWallpaperBiasX)
        appSettings.moeWallpaperBiasY.set(value.moeWallpaperBiasY)
        appSettings.moeHomeQuote.set(value.moeHomeQuote)
        appSettings.moeHomeQuoteAuthor.set(value.moeHomeQuoteAuthor)
        appSettings.moeSidebarExpanded.set(value.moeSidebarExpanded)
        appSettings.pageScale.set(value.pageScale)
        appSettings.singleNodeTest.set(value.singleNodeTest)
        appSettings.customUserAgent.set(value.customUserAgent)
    }

    private fun applyNetworkSettings(value: NetworkSettingsBackup) {
        networkSettings.proxyMode.set(value.proxyMode)
        networkSettings.bypassPrivateNetwork.set(value.bypassPrivateNetwork)
        networkSettings.dnsHijack.set(value.dnsHijack)
        networkSettings.allowBypass.set(value.allowBypass)
        networkSettings.enableIPv6.set(value.enableIPv6)
        networkSettings.systemProxy.set(value.systemProxy)
        networkSettings.tunStack.set(value.tunStack)
        networkSettings.tunRouteExcludeAddress.set(value.tunRouteExcludeAddress)
        networkSettings.rootTunIfName.set(value.rootTunIfName)
        networkSettings.rootTunMtu.set(value.rootTunMtu)
        networkSettings.rootTunAutoRoute.set(value.rootTunAutoRoute)
        networkSettings.rootTunStrictRoute.set(value.rootTunStrictRoute)
        networkSettings.rootTunAutoRedirect.set(value.rootTunAutoRedirect)
        networkSettings.rootTunIncludeAndroidUser.set(value.rootTunIncludeAndroidUser)
        networkSettings.rootTunRouteExcludeAddress.set(value.rootTunRouteExcludeAddress)
        networkSettings.rootTunDnsMode.set(value.rootTunDnsMode)
        networkSettings.rootTunFakeIpRange.set(value.rootTunFakeIpRange)
        networkSettings.rootTunFakeIpRange6.set(value.rootTunFakeIpRange6)
        networkSettings.accessControlMode.set(value.accessControlMode)
        networkSettings.accessControlPackages.set(value.accessControlPackages)
        networkSettings.accessControlSelectedFirst.set(value.accessControlSelectedFirst)
        networkSettings.accessControlShowSystemApps.set(value.accessControlShowSystemApps)
        networkSettings.accessControlSortMode.set(value.accessControlSortMode)
    }

    private fun applyFeatureSettings(value: FeatureSettingsBackup) {
        featureSettings.allowLanAccess.set(value.allowLanAccess)
        featureSettings.backendPort.set(value.backendPort)
        featureSettings.frontendPort.set(value.frontendPort)
        featureSettings.selectedPanelType.set(value.selectedPanelType)
        featureSettings.panelOpenMode.set(value.panelOpenMode)
        featureSettings.showWebControlInProxy.set(value.showWebControlInProxy)
        featureSettings.exitUiWhenBackground.set(value.exitUiWhenBackground)
        featureSettings.isFirstOpen = value.isFirstOpen
    }

    private fun applyProxyDisplaySettings(value: ProxyDisplaySettingsBackup) {
        proxyDisplaySettings.sortMode.set(value.sortMode)
        proxyDisplaySettings.displayMode.set(value.displayMode)
        proxyDisplaySettings.proxyMode.set(value.proxyMode)
        proxyDisplaySettings.sheetHeightFraction.set(value.sheetHeightFraction)
    }

    private fun applyProfileLinks(value: ProfileLinksBackup) {
        profileLinks.linkOpenMode.set(value.linkOpenMode)
        profileLinks.links.set(value.links)
        profileLinks.defaultLinkId.set(value.defaultLinkId)
    }

    private fun applyRemoteController(value: RemoteControllerBackup) {
        remoteController.backends.set(value.backends)
        remoteController.activeBackendId.set(value.activeBackendId)
        remoteController.controllerEnabled.set(value.controllerEnabled)
        proxyFacade.applyRemoteControllerState()
    }

    private fun applyProfiles(value: ProfilesBackup) {
        ProfileStore.saveImported(value.imported.map(ImportedBackup::toImported))
        ProfileStore.saveProfileOrder(value.profileOrder.map(UUID::fromString))
    }

    private fun currentFiles(): BackupArchiveManager.BackupFiles =
        BackupArchiveManager.BackupFiles(
            importedDir = application.importedDir,
            overridesDir = application.filesDir.resolve("overrides"),
            subStoreDataDir = SubStorePaths.dataDir,
            moeWallpaperFile = application.moeWallpaperFile(),
        )

    private fun freshCacheDir(name: String): File =
        application.cacheDir.resolve(name).apply {
            deleteRecursively()
            mkdirs()
        }

}

internal fun replaceBackupDirectory(source: File, target: File) {
    target.deleteRecursively()
    if (!source.exists()) return
    source.copyToBackupTarget(target)
}

private fun File.copyToBackupTarget(target: File) {
    if (isDirectory) {
        target.mkdirs()
        listFiles().orEmpty().forEach { file ->
            file.copyToBackupTarget(target.resolve(file.name))
        }
    } else {
        target.parentFile?.mkdirs()
        copyTo(target, overwrite = true)
    }
}

internal fun refreshAfterRawStoreClear(vararg preferences: Preference<*>) {
    preferences.forEach { it.refresh() }
}
