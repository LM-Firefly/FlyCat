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

package com.github.lmfirefly.flycat.data.backup

import android.app.Application
import com.github.lmfirefly.flycat.core.BuildConfigHolder.versionName
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.contract.BackupDataSource
import com.github.lmfirefly.flycat.core.contract.BroadcastNotifier
import com.github.lmfirefly.flycat.core.contract.BulkStoreReset
import com.github.lmfirefly.flycat.core.contract.FeatureStoreReader
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.Preference
import com.github.lmfirefly.flycat.core.contract.ProfileStoreReader
import com.github.lmfirefly.flycat.core.contract.ProxyDisplaySettingsReader
import com.github.lmfirefly.flycat.core.contract.RemoteControllerStoreReader
import com.github.lmfirefly.flycat.core.contract.RuntimeLifecycleCommand
import com.github.lmfirefly.flycat.core.contract.ServiceStateReader
import com.github.lmfirefly.flycat.core.contract.SubStoreBackupSupport
import com.github.lmfirefly.flycat.core.contract.SubStoreSettings
import com.github.lmfirefly.flycat.core.importedDir
import com.github.lmfirefly.flycat.core.model.RemoteBackend
import com.github.lmfirefly.flycat.core.model.WifiAutomationRule
import com.github.lmfirefly.flycat.core.model.backup.AppSettingsBackup
import com.github.lmfirefly.flycat.core.model.backup.BackupManifest
import com.github.lmfirefly.flycat.core.model.backup.BackupPayload
import com.github.lmfirefly.flycat.core.model.backup.BackupRestoreStrategy
import com.github.lmfirefly.flycat.core.model.backup.FeatureSettingsBackup
import com.github.lmfirefly.flycat.core.model.backup.ImportedBackup
import com.github.lmfirefly.flycat.core.model.backup.NetworkSettingsBackup
import com.github.lmfirefly.flycat.core.model.backup.ProfilesBackup
import com.github.lmfirefly.flycat.core.model.backup.ProxyDisplaySettingsBackup
import com.github.lmfirefly.flycat.core.model.backup.RemoteControllerBackup
import com.github.lmfirefly.flycat.core.model.backup.ServiceBackup
import com.github.lmfirefly.flycat.core.model.backup.WebDavBackup
import com.github.lmfirefly.flycat.core.model.profile.LinkOpenMode
import com.github.lmfirefly.flycat.core.util.backup.BackupArchiveManager
import com.github.lmfirefly.flycat.core.util.path.moeWallpaperFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(
    private val application: Application,
    private val appSettings: AppSettingsReader,
    private val networkSettings: NetworkSettingsReader,
    private val featureSettings: FeatureStoreReader,
    private val subStoreSettings: SubStoreSettings,
    private val proxyDisplaySettings: ProxyDisplaySettingsReader,
    private val remoteController: RemoteControllerStoreReader,
    private val runtimeCommand: RuntimeLifecycleCommand,
    private val broadcastNotifier: BroadcastNotifier,
    private val bulkStoreReset: BulkStoreReset,
    private val serviceState: ServiceStateReader,
    private val profileStore: ProfileStoreReader,
    private val subStoreBackupSupport: SubStoreBackupSupport,
    private val archiveManager: BackupArchiveManager = BackupArchiveManager(),
) : BackupDataSource {
    override suspend fun exportBackup(output: OutputStream) = withContext(Dispatchers.IO) { writeCurrentBackup(output) }

    override suspend fun restoreBackup(input: InputStream, strategy: BackupRestoreStrategy) = withContext(Dispatchers.IO) {
        val extractDir = freshCacheDir("backup-restore")
        val rollbackFile = application.cacheDir.resolve("backup-restore-rollback.zip")
        var rollbackDir: File? = null
        try {
            val extracted = archiveManager.readArchive(input, extractDir)
            require(extracted.manifest.appId.isBlank() || extracted.manifest.appId == application.packageName) { "Backup belongs to ${extracted.manifest.appId}" }
            // 合并模式需要在写入前快照本机数据，用于“冲突时保留本机内容”。
            val currentPayload = if (strategy == BackupRestoreStrategy.Merge) collectPayload() else null
            rollbackFile.outputStream().use(::writeCurrentBackup)
            try {
                stopRuntimeBeforeRestore()
                applyBackup(extracted, strategy, currentPayload)
                notifyRestored()
            } catch (error: Exception) {
                try {
                    val stagingDir = freshCacheDir("backup-rollback")
                    rollbackDir = stagingDir
                    val rollbackExtracted = rollbackFile.inputStream().use { stream ->
                        archiveManager.readArchive(stream, stagingDir)
                    }
                    applyBackup(rollbackExtracted)
                } catch (_: Exception) { /* 尽力回滚 */ }
                throw error
            }
        } finally {
            rollbackFile.delete()
            extractDir.deleteRecursively()
            rollbackDir?.deleteRecursively()
        }
    }

    override fun defaultBackupFileName(now: Long): String {
        val timestamp = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withLocale(Locale.US))
        return "FlyCat-backup-$timestamp.zip"
    }

    private fun writeCurrentBackup(output: OutputStream) {
        archiveManager.writeArchive(
            output = output,
            manifest = BackupManifest(
                appId = application.packageName,
                appVersion = com.github.lmfirefly.flycat.core.BuildConfigHolder.versionName,
                createdAt = System.currentTimeMillis(),
                plaintext = true,
                includes = listOf(
                    "settings",
                    "network_settings",
                    "feature_settings",
                    "proxy_display",
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

    private fun collectPayload(): BackupPayload = BackupPayload(
        appSettings = AppSettingsBackup(
            themeMode = appSettings.themeMode.value,
            appLanguage = appSettings.appLanguage.value,
            colorTheme = appSettings.colorTheme.value,
            themeAccentColorArgb = appSettings.themeAccentColorArgb.value,
            invertOnPrimaryColors = appSettings.invertOnPrimaryColors.value,
            homePreviewGuideShown = appSettings.homePreviewGuideShown.value,
            automaticRestart = appSettings.automaticRestart.value,
            autoUpdateCurrentProfileOnStart = appSettings.autoUpdateCurrentProfileOnStart.value,
            hideAppIcon = appSettings.hideAppIcon.value,
            excludeFromRecents = appSettings.excludeFromRecents.value,
            showTrafficNotification = appSettings.showTrafficNotification.value,
            superIslandEnabled = appSettings.superIslandEnabled.value,
            liveNotificationEnabled = appSettings.liveNotificationEnabled.value,
            notificationFirstLine = appSettings.notificationFirstLine.value,
            notificationSecondLine = appSettings.notificationSecondLine.value,
            notificationThirdLine = appSettings.notificationThirdLine.value,
            notificationFourthLine = appSettings.notificationFourthLine.value,
            notificationFirstLineText = appSettings.notificationFirstLineText.value,
            notificationSecondLineText = appSettings.notificationSecondLineText.value,
            notificationThirdLineText = appSettings.notificationThirdLineText.value,
            notificationFourthLineText = appSettings.notificationFourthLineText.value,
            bottomBarAutoHide = appSettings.bottomBarAutoHide.value,
            classicHomeEnabled = appSettings.classicHomeEnabled.value,
            homeHitokotoEnabled = appSettings.homeHitokotoEnabled.value,
            moeWallpaperUri = appSettings.moeWallpaperUri.value,
            moeWallpaperSourceUri = appSettings.moeWallpaperSourceUri.value,
            moeWallpaperZoom = appSettings.moeWallpaperZoom.value,
            moeWallpaperBiasX = appSettings.moeWallpaperBiasX.value,
            moeWallpaperBiasY = appSettings.moeWallpaperBiasY.value,
            moeHomeQuote = appSettings.moeHomeQuote.value,
            moeHomeQuoteAuthor = appSettings.moeHomeQuoteAuthor.value,
            moeSidebarExpanded = appSettings.moeSidebarExpanded.value,
            moeWallpaperScrimEnabled = appSettings.moeWallpaperScrimEnabled.value,
            appBackgroundStyle = appSettings.appBackgroundStyle.value,
            appBackgroundWallpaperBlur = appSettings.appBackgroundWallpaperBlur.value,
            pageScale = appSettings.pageScale.value,
            predictiveBackEnabled = appSettings.predictiveBackEnabled.value,
            predictiveBackMaxProgress = appSettings.predictiveBackMaxProgress.value,
            logLevel = appSettings.logLevel.value,
            autoCheckAppUpdate = appSettings.autoCheckAppUpdate.value,
            updateSourceKey = appSettings.updateSourceKey.value,
            customUserAgent = appSettings.customUserAgent.value,
        ),
        networkSettings = NetworkSettingsBackup(
            runMode = networkSettings.runMode.value,
            bypassPrivateNetwork = networkSettings.bypassPrivateNetwork.value,
            dnsHijack = networkSettings.dnsHijack.value,
            allowBypass = networkSettings.allowBypass.value,
            enableIPv6 = networkSettings.enableIPv6.value,
            systemProxy = networkSettings.systemProxy.value,
            tunStack = networkSettings.tunStack.value,
            tunRouteExcludeAddress = networkSettings.tunRouteExcludeAddress.value,
            tunIfName = networkSettings.tunIfName.value,
            tunMtu = networkSettings.tunMtu.value,
            tunAutoRoute = networkSettings.tunAutoRoute.value,
            tunStrictRoute = networkSettings.tunStrictRoute.value,
            tunAutoRedirect = networkSettings.tunAutoRedirect.value,
            tunIncludeAndroidUser = networkSettings.tunIncludeAndroidUser.value,
            tunDnsMode = networkSettings.tunDnsMode.value,
            tunFakeIpRange = networkSettings.tunFakeIpRange.value,
            tunFakeIpRange6 = networkSettings.tunFakeIpRange6.value,
            accessControlMode = networkSettings.accessControlMode.value,
            accessControlImportStrategy = networkSettings.accessControlImportStrategy.value,
            accessControlPackages = networkSettings.accessControlPackages.value,
            accessControlSelectedFirst = networkSettings.accessControlSelectedFirst.value,
            accessControlShowSystemApps = networkSettings.accessControlShowSystemApps.value,
            disableAllOverride = networkSettings.disableAllOverride.value,
            ebpfBypassCn = networkSettings.ebpfBypassCn.value,
            wifiAutomationEnabled = networkSettings.wifiAutomationEnabled.value,
            wifiAutomationRules = networkSettings.wifiAutomationRules.value,
            wifiAutomationOtherWifiAction = networkSettings.wifiAutomationOtherWifiAction.value,
            wifiAutomationNoWifiAction = networkSettings.wifiAutomationNoWifiAction.value,
            wifiAutomationOtherWifiProfileUuid = networkSettings.wifiAutomationOtherWifiProfileUuid.value,
            wifiAutomationNoWifiProfileUuid = networkSettings.wifiAutomationNoWifiProfileUuid.value,
        ),
        featureSettings = FeatureSettingsBackup(
            allowLanAccess = subStoreSettings.allowLanAccess.value,
            backendPort = subStoreSettings.backendPort.value,
            frontendPort = subStoreSettings.frontendPort.value,
            selectedPanelType = subStoreSettings.selectedPanelType.value,
            panelOpenMode = subStoreSettings.panelOpenMode.value,
            showWebControlInProxy = featureSettings.showWebControlInProxy.value,
            exitUiWhenBackground = featureSettings.exitUiWhenBackground.value,
            isFirstOpen = featureSettings.isFirstOpen,
        ),
        proxyDisplaySettings = ProxyDisplaySettingsBackup(
            sortMode = proxyDisplaySettings.sortMode.value,
            displayMode = proxyDisplaySettings.displayMode.value,
            groupTabsMode = proxyDisplaySettings.groupTabsMode.value,
            proxyMode = proxyDisplaySettings.proxyMode.value,
            sheetHeightFraction = proxyDisplaySettings.sheetHeightFraction.value,
        ),
        remoteController = RemoteControllerBackup(
            controllerEnabled = remoteController.controllerEnabled.value,
            backends = remoteController.backends.value,
            activeBackendId = remoteController.activeBackendId.value,
        ),
        webDav = WebDavBackup(
            url = appSettings.webDav.webDavUrl.value,
            account = appSettings.webDav.webDavAccount.value,
            password = appSettings.webDav.webDavPassword.value,
            directory = appSettings.webDav.webDavDir.value,
        ),
        profiles = ProfilesBackup(
            imported = profileStore.loadImported().map(ImportedBackup::from),
            profileOrder = profileStore.loadProfileOrder().map(UUID::toString),
        ),
        service = ServiceBackup(activeProfile = serviceState.activeProfile?.toString()),
    )

    private suspend fun applyBackup(extracted: BackupArchiveManager.ExtractedBackup, strategy: BackupRestoreStrategy = BackupRestoreStrategy.Overwrite, currentPayload: BackupPayload? = null) {
        val wallpaperFile = extracted.moeWallpaperFile.takeIf(File::isFile)
        val payload = when (strategy) {
            BackupRestoreStrategy.Overwrite -> extracted.payload
            BackupRestoreStrategy.Merge ->
                mergeBackupPayload(
                    local = requireNotNull(currentPayload),
                    backup = extracted.payload,
                )
        }
        val localWallpaperFile = application.moeWallpaperFile().takeIf(File::isFile)
        val restoredWallpaperFile = if (strategy == BackupRestoreStrategy.Merge) localWallpaperFile ?: wallpaperFile
            else wallpaperFile
        val appBackup = if (restoredWallpaperFile != null) { payload.appSettings.copy(moeWallpaperUri = "file://${application.moeWallpaperFile().absolutePath}") } else { payload.appSettings }

        // 第 1 步：先替换文件系统目录。
        // 如果进程在操作中途死亡，MMKV 仍然拥有有效的旧数据作为回退。
        when (strategy) {
            BackupRestoreStrategy.Overwrite -> {
                replaceBackupDirectory(extracted.importedDir, application.importedDir)
                replaceBackupDirectory(extracted.overridesDir, application.filesDir.resolve("overrides"))
                replaceBackupDirectory(extracted.subStoreDataDir, subStoreBackupSupport.dataDir)
            }
            BackupRestoreStrategy.Merge -> {
                mergeBackupDirectory(extracted.importedDir, application.importedDir)
                mergeBackupDirectory(extracted.overridesDir, application.filesDir.resolve("overrides"))
                mergeBackupDirectory(extracted.subStoreDataDir, subStoreBackupSupport.dataDir)
            }
        }
        if (
            wallpaperFile != null &&
                (strategy == BackupRestoreStrategy.Overwrite || localWallpaperFile == null)
        ) {
            application.moeWallpaperFile().also { target ->
                target.parentFile?.mkdirs()
                wallpaperFile.copyTo(target, overwrite = true)
            }
        } else if (strategy == BackupRestoreStrategy.Overwrite && wallpaperFile == null) {
            application.moeWallpaperFile().delete()
        }

        // 第 2 步：文件安全就位后更新 MMKV 存储。仅清除批量写入的存储。
        // 设置存储使用 Preference.set()，它会检查内存流的相等性——如果先清除 MMKV，当备份值与过期的内存值相匹配时，set() 会跳过写入操作。
        clearBulkStores()
        applyAppSettings(appBackup)
        applyNetworkSettings(payload.networkSettings)
        applyFeatureSettings(payload.featureSettings)
        applyProxyDisplaySettings(payload.proxyDisplaySettings)
        applyRemoteController(payload.remoteController)
        applyWebDav(payload.webDav)
        applyProfiles(payload.profiles)
        serviceState.activeProfile = payload.service.activeProfile?.let(UUID::fromString)
    }

    private suspend fun stopRuntimeBeforeRestore() {
        runCatching { runtimeCommand.stopProxy() }
        runCatching { subStoreBackupSupport.stopService(application) }
    }

    private suspend fun notifyRestored() {
        broadcastNotifier.notifyProfileChanged()
        broadcastNotifier.notifyOverrideChanged()
        runCatching { runtimeCommand.reconcileRuntimeState() }
    }

    private fun clearBulkStores() {
        listOf(
                "profiles",
                "service",
                "override_bindings",
            )
            .forEach { id -> bulkStoreReset.clearStore(id) }
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
            appSettings.classicHomeEnabled,
            appSettings.homeHitokotoEnabled,
            appSettings.moeWallpaperUri,
            appSettings.moeWallpaperSourceUri,
            appSettings.moeWallpaperZoom,
            appSettings.moeWallpaperBiasX,
            appSettings.moeWallpaperBiasY,
            appSettings.moeHomeQuote,
            appSettings.moeHomeQuoteAuthor,
            appSettings.moeSidebarExpanded,
            appSettings.moeWallpaperScrimEnabled,
            appSettings.appBackgroundStyle,
            appSettings.appBackgroundWallpaperBlur,
            appSettings.superIslandEnabled,
            appSettings.liveNotificationEnabled,
            appSettings.notificationFirstLine,
            appSettings.notificationSecondLine,
            appSettings.notificationThirdLine,
            appSettings.notificationFourthLine,
            appSettings.notificationFirstLineText,
            appSettings.notificationSecondLineText,
            appSettings.notificationThirdLineText,
            appSettings.notificationFourthLineText,
            appSettings.predictiveBackEnabled,
            appSettings.predictiveBackMaxProgress,
            appSettings.pageScale,
            appSettings.customUserAgent,
        )
        refreshAfterRawStoreClear(
            networkSettings.runMode,
            networkSettings.bypassPrivateNetwork,
            networkSettings.dnsHijack,
            networkSettings.allowBypass,
            networkSettings.enableIPv6,
            networkSettings.systemProxy,
            networkSettings.tunStack,
            networkSettings.tunRouteExcludeAddress,
            networkSettings.tunIfName,
            networkSettings.tunMtu,
            networkSettings.tunAutoRoute,
            networkSettings.tunStrictRoute,
            networkSettings.tunAutoRedirect,
            networkSettings.tunIncludeAndroidUser,
            networkSettings.tunRouteExcludeAddress,
            networkSettings.tunDnsMode,
            networkSettings.tunFakeIpRange,
            networkSettings.tunFakeIpRange6,
            networkSettings.accessControlMode,
            networkSettings.accessControlImportStrategy,
            networkSettings.accessControlPackages,
            networkSettings.accessControlSelectedFirst,
            networkSettings.accessControlShowSystemApps,
            networkSettings.disableAllOverride,
            networkSettings.ebpfBypassCn,
            networkSettings.wifiAutomationEnabled,
            networkSettings.wifiAutomationRules,
            networkSettings.wifiAutomationOtherWifiAction,
            networkSettings.wifiAutomationNoWifiAction,
            networkSettings.wifiAutomationOtherWifiProfileUuid,
            networkSettings.wifiAutomationNoWifiProfileUuid,
        )
        refreshAfterRawStoreClear(
            subStoreSettings.allowLanAccess,
            subStoreSettings.backendPort,
            subStoreSettings.frontendPort,
            subStoreSettings.selectedPanelType,
            subStoreSettings.panelOpenMode,
            featureSettings.showWebControlInProxy,
            featureSettings.exitUiWhenBackground,
        )
        refreshAfterRawStoreClear(
            proxyDisplaySettings.sortMode,
            proxyDisplaySettings.displayMode,
            proxyDisplaySettings.groupTabsMode,
            proxyDisplaySettings.proxyMode,
            proxyDisplaySettings.sheetHeightFraction,
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
        appSettings.superIslandEnabled.set(value.superIslandEnabled)
        appSettings.liveNotificationEnabled.set(value.liveNotificationEnabled)
        appSettings.notificationFirstLine.set(value.notificationFirstLine)
        appSettings.notificationSecondLine.set(value.notificationSecondLine)
        appSettings.notificationThirdLine.set(value.notificationThirdLine)
        appSettings.notificationFourthLine.set(value.notificationFourthLine)
        appSettings.notificationFirstLineText.set(value.notificationFirstLineText)
        appSettings.notificationSecondLineText.set(value.notificationSecondLineText)
        appSettings.notificationThirdLineText.set(value.notificationThirdLineText)
        appSettings.notificationFourthLineText.set(value.notificationFourthLineText)
        appSettings.bottomBarAutoHide.set(value.bottomBarAutoHide)
        appSettings.classicHomeEnabled.set(value.classicHomeEnabled)
        appSettings.homeHitokotoEnabled.set(value.homeHitokotoEnabled)
        appSettings.moeWallpaperUri.set(value.moeWallpaperUri)
        appSettings.moeWallpaperSourceUri.set(value.moeWallpaperSourceUri)
        appSettings.moeWallpaperZoom.set(value.moeWallpaperZoom)
        appSettings.moeWallpaperBiasX.set(value.moeWallpaperBiasX)
        appSettings.moeWallpaperBiasY.set(value.moeWallpaperBiasY)
        appSettings.moeHomeQuote.set(value.moeHomeQuote)
        appSettings.moeHomeQuoteAuthor.set(value.moeHomeQuoteAuthor)
        appSettings.moeSidebarExpanded.set(value.moeSidebarExpanded)
        appSettings.moeWallpaperScrimEnabled.set(value.moeWallpaperScrimEnabled)
        appSettings.appBackgroundStyle.set(value.appBackgroundStyle)
        appSettings.appBackgroundWallpaperBlur.set(value.appBackgroundWallpaperBlur)
        appSettings.pageScale.set(value.pageScale)
        appSettings.predictiveBackEnabled.set(value.predictiveBackEnabled)
        appSettings.predictiveBackMaxProgress.set(value.predictiveBackMaxProgress)
        appSettings.logLevel.set(value.logLevel)
        appSettings.autoCheckAppUpdate.set(value.autoCheckAppUpdate)
        appSettings.updateSourceKey.set(value.updateSourceKey)
        appSettings.customUserAgent.set(value.customUserAgent)
    }

    private fun applyNetworkSettings(value: NetworkSettingsBackup) {
        networkSettings.runMode.set(value.runMode)
        networkSettings.bypassPrivateNetwork.set(value.bypassPrivateNetwork)
        networkSettings.dnsHijack.set(value.dnsHijack)
        networkSettings.allowBypass.set(value.allowBypass)
        networkSettings.enableIPv6.set(value.enableIPv6)
        networkSettings.systemProxy.set(value.systemProxy)
        networkSettings.tunStack.set(value.tunStack)
        networkSettings.tunRouteExcludeAddress.set(value.tunRouteExcludeAddress)
        networkSettings.tunIfName.set(value.tunIfName)
        networkSettings.tunMtu.set(value.tunMtu)
        networkSettings.tunAutoRoute.set(value.tunAutoRoute)
        networkSettings.tunStrictRoute.set(value.tunStrictRoute)
        networkSettings.tunAutoRedirect.set(value.tunAutoRedirect)
        networkSettings.tunIncludeAndroidUser.set(value.tunIncludeAndroidUser)
        networkSettings.tunRouteExcludeAddress.set(value.tunRouteExcludeAddress)
        networkSettings.tunDnsMode.set(value.tunDnsMode)
        networkSettings.tunFakeIpRange.set(value.tunFakeIpRange)
        networkSettings.tunFakeIpRange6.set(value.tunFakeIpRange6)
        networkSettings.accessControlMode.set(value.accessControlMode)
        networkSettings.accessControlImportStrategy.set(value.accessControlImportStrategy)
        networkSettings.accessControlPackages.set(value.accessControlPackages)
        networkSettings.accessControlSelectedFirst.set(value.accessControlSelectedFirst)
        networkSettings.accessControlShowSystemApps.set(value.accessControlShowSystemApps)
        networkSettings.disableAllOverride.set(value.disableAllOverride)
        networkSettings.ebpfBypassCn.set(value.ebpfBypassCn)
        networkSettings.wifiAutomationEnabled.set(value.wifiAutomationEnabled)
        networkSettings.wifiAutomationRules.set(value.wifiAutomationRules)
        networkSettings.wifiAutomationOtherWifiAction.set(value.wifiAutomationOtherWifiAction)
        networkSettings.wifiAutomationNoWifiAction.set(value.wifiAutomationNoWifiAction)
        networkSettings.wifiAutomationOtherWifiProfileUuid.set(value.wifiAutomationOtherWifiProfileUuid)
        networkSettings.wifiAutomationNoWifiProfileUuid.set(value.wifiAutomationNoWifiProfileUuid)
    }

    private fun applyFeatureSettings(value: FeatureSettingsBackup) {
        subStoreSettings.allowLanAccess.set(value.allowLanAccess)
        subStoreSettings.backendPort.set(value.backendPort)
        subStoreSettings.frontendPort.set(value.frontendPort)
        subStoreSettings.selectedPanelType.set(value.selectedPanelType)
        subStoreSettings.panelOpenMode.set(value.panelOpenMode)
        featureSettings.showWebControlInProxy.set(value.showWebControlInProxy)
        featureSettings.exitUiWhenBackground.set(value.exitUiWhenBackground)
    }

    private fun applyProxyDisplaySettings(value: ProxyDisplaySettingsBackup) {
        proxyDisplaySettings.sortMode.set(value.sortMode)
        proxyDisplaySettings.displayMode.set(value.displayMode.normalized())
        proxyDisplaySettings.groupTabsMode.set(value.groupTabsMode)
        proxyDisplaySettings.proxyMode.set(value.proxyMode)
        proxyDisplaySettings.sheetHeightFraction.set(value.sheetHeightFraction)
    }

    private suspend fun applyRemoteController(value: RemoteControllerBackup) {
        remoteController.backends.set(value.backends)
        remoteController.activeBackendId.set(value.activeBackendId)
        remoteController.controllerEnabled.set(value.controllerEnabled)
        runtimeCommand.applyRemoteControllerState()
    }

    private fun applyWebDav(value: WebDavBackup) {
        appSettings.webDav.webDavUrl.set(value.url)
        appSettings.webDav.webDavAccount.set(value.account)
        appSettings.webDav.webDavPassword.set(value.password)
        appSettings.webDav.webDavDir.set(value.directory)
    }

    private fun applyProfiles(value: ProfilesBackup) {
        profileStore.saveImported(value.imported.map(ImportedBackup::toImported))
        profileStore.saveProfileOrder(value.profileOrder.map(UUID::fromString))
    }

    private fun currentFiles(): BackupArchiveManager.BackupFiles = BackupArchiveManager.BackupFiles(
        importedDir = application.importedDir,
        overridesDir = application.filesDir.resolve("overrides"),
        subStoreDataDir = subStoreBackupSupport.dataDir,
        moeWallpaperFile = application.moeWallpaperFile(),
    )

    private fun freshCacheDir(name: String): File = application.cacheDir.resolve(name).apply {
        deleteRecursively()
        mkdirs()
    }
}

internal fun replaceBackupDirectory(source: File, target: File) {
    if (!source.exists()) return
    // 原子交换：将目标重命名为 .bak，将源重命名为目标，然后进行清理。如果进程在操作过程中终止，启动恢复会还原 .bak 文件。
    val backup = File(target.parentFile, target.name + ".bak")
    backup.deleteRecursively()
    if (target.exists()) {
        if (!target.renameTo(backup)) {
            backup.deleteRecursively()
            target.copyRecursively(backup, overwrite = true)
            target.deleteRecursively()
        }
    }
    if (!source.renameTo(target)) {
        target.deleteRecursively()
        source.copyRecursively(target, overwrite = true)
        source.deleteRecursively()
    }
    backup.deleteRecursively()
}

/** 合并模式的目录处理：只补充本机缺失的文件，不覆盖已存在的内容。 */
internal fun mergeBackupDirectory(source: File, target: File) {
    if (!source.exists()) return
    source.mergeIntoBackupTarget(target)
}

private fun File.mergeIntoBackupTarget(target: File) {
    if (isDirectory) {
        if (target.exists() && !target.isDirectory) return
        target.mkdirs()
        listFiles().orEmpty().forEach { file ->
            file.mergeIntoBackupTarget(target.resolve(file.name))
        }
    } else if (!target.exists()) {
        target.parentFile?.mkdirs()
        copyTo(target)
    }
}

/** 合并恢复的 payload 归并：标量设置一律保留本机值（冲突时保留本机内容），列表类数据本机优先去重后补充备份中的缺失项。 */
internal fun mergeBackupPayload(local: BackupPayload, backup: BackupPayload): BackupPayload = local.copy(
    networkSettings = local.networkSettings.copy(
        tunRouteExcludeAddress = mergeLocalFirst(
            local.networkSettings.tunRouteExcludeAddress,
            backup.networkSettings.tunRouteExcludeAddress,
        ),
        tunIncludeAndroidUser = mergeLocalFirst(
            local.networkSettings.tunIncludeAndroidUser,
            backup.networkSettings.tunIncludeAndroidUser,
        ),
        accessControlPackages = local.networkSettings.accessControlPackages + backup.networkSettings.accessControlPackages,
        wifiAutomationRules = mergeLocalFirst(
            local.networkSettings.wifiAutomationRules,
            backup.networkSettings.wifiAutomationRules,
            WifiAutomationRule::ssid,
        ),
    ),
    remoteController = local.remoteController.copy(
        backends = mergeLocalFirst(
            local.remoteController.backends,
            backup.remoteController.backends,
            RemoteBackend::id,
        ),
        activeBackendId = local.remoteController.activeBackendId.ifBlank { backup.remoteController.activeBackendId },
    ),
    profiles = local.profiles.copy(
        imported = mergeLocalFirst(
                local.profiles.imported,
                backup.profiles.imported,
                ImportedBackup::uuid,
            ),
        profileOrder = mergeLocalFirst(local.profiles.profileOrder, backup.profiles.profileOrder),
    ),
    service = local.service.copy(activeProfile = local.service.activeProfile ?: backup.service.activeProfile),
)

private fun <T> mergeLocalFirst(local: List<T>, backup: List<T>): List<T> = (local + backup).distinct()

private fun <T, K> mergeLocalFirst(local: List<T>, backup: List<T>, key: (T) -> K): List<T> = (local + backup).distinctBy(key)

internal fun refreshAfterRawStoreClear(vararg preferences: Preference<*>) {
    preferences.forEach { it.refresh() }
}
