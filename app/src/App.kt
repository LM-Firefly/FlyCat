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

package com.github.yumelira.yumebox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.core.FirstRunInitializer
import com.github.yumelira.yumebox.core.Global
import com.github.yumelira.yumebox.core.contract.AppLogSettings
import com.github.yumelira.yumebox.core.contract.AppShutdownHandler
import com.github.yumelira.yumebox.core.contract.RuntimeLogWriter
import com.github.yumelira.yumebox.core.contract.ServiceBootstrapHolder
import com.github.yumelira.yumebox.core.domain.CustomRoutingInitializer
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.AppScreenState
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.StartupTaskCoordinator
import com.github.yumelira.yumebox.core.util.SubStorePaths.dataDir
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.data.collector.AppTrafficStatisticsCollector
import com.github.yumelira.yumebox.data.logging.AppLogBridge
import com.github.yumelira.yumebox.data.logging.AppLogTree
import com.github.yumelira.yumebox.data.logging.CrashHandler
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.di.ServiceBootstrapReaderImpl
import com.github.yumelira.yumebox.di.appModule
import com.github.yumelira.yumebox.feature.settings.presentation.util.MoeWallpaperImporter
import com.github.yumelira.yumebox.feature.update.GitHubUpdateManager
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Components
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.client.common.util.ProxyAutoStartHelper
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.tukaani.xz.XZInputStream
import timber.log.Timber
import java.io.File

class App : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        lateinit var instance: App
            private set
        private const val GEO_EXTRACT_MAX_FAILURES = 3
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        AppScreenState.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = AppForegroundState.onActivityResumed()
            override fun onActivityPaused(activity: Activity) = AppForegroundState.onActivityPaused()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // Stage 1: Core infrastructure (logging, crash handler, native globals)
        val koin = try {
            initCoreInfrastructure()
        } catch (e: Exception) {
            Timber.e(e, "Fatal error during core infrastructure init")
            CrashHandler.init(this)
            return
        }
        // Stage 2: Apply user settings (deferred — language already wrapped in attachBaseContext)
        startupScope.launch {
            runCatching { applyUserSettings(koin) }
                .onFailure { Timber.e(it, "Error applying user settings") }
        }
        // Stage 2a: Preload native bridge libraries on background thread
        startupScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { com.github.yumelira.yumebox.core.bridge.Bridge.preload() }
                    .onFailure { Timber.e(it, "Bridge preload failed") }
            }
        }
        // Stage 2b: Recover from incomplete file operations (atomic rename leftovers)
        startupScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { recoverIncompleteOperations() }
                    .onFailure { Timber.w(it, "Startup recovery skipped") }
            }
        }
        // Stage 3: Start update check coroutine
        runCatching { startUpdateCheck(koin) }
            .onFailure { Timber.w(it, "Update check scheduling skipped") }
        // Stage 4: Deferred runtime tasks (geo assets, traffic collector, auto-start)
        runCatching { scheduleDeferredStartupTasks(koin) }
            .onFailure { Timber.w(it, "Deferred startup tasks scheduling skipped") }
        // Stage 5: Independent lifecycle observers (always safe to start)
        startupScope.launch { observeAndBroadcastForegroundState() }
    }
    /**
     * Stage 1: Initialize logging, crash handler, Global context, MMKV, and Koin DI.
     * Returns the Koin instance on success; throws on fatal failure.
     */
    private fun initCoreInfrastructure(): Koin {
        if (Timber.forest().isEmpty()) Timber.plant(AppLogTree())
        CrashHandler.init(this)
        AppLogBridge.runtimeLogWriter = null
        Global.init(this)
        com.github.yumelira.yumebox.core.BuildConfigHolder.init(
            versionName = BuildConfig.VERSION_NAME,
            applicationId = BuildConfig.APPLICATION_ID,
            uiBuildId = BuildConfig.UI_BUILD_ID,
        )
        Components.register(
            mainActivityClassName = MainActivity::class.java.name,
            proxySheetActivityClassName = ProxySheetActivity::class.java.name,
        )
        MMKV.disableProcessModeChecker()
        MMKV.initialize(this)
        Timber.d("Core infrastructure initialized")
        val koin = startKoin {
            androidContext(this@App)
            modules(appModule)
        }.koin
        AppLogBridge.runtimeLogWriter = koin.get<RuntimeLogWriter>()::writeLog
        ServiceBootstrapHolder.initialize(
            ServiceBootstrapReaderImpl(
                appSettingsStore = koin.get(),
                featureStore = koin.get(),
                networkSettingsStore = koin.get(),
                mmkvProvider = koin.get(),
            )
        )
        return koin
    }
    /**
     * Stage 2: Apply persisted user preferences (language, log level, predictive back, feature version).
     */
    private fun applyUserSettings(koin: Koin) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        val appLogSettings: AppLogSettings = koin.get()
        appLogSettings.minLogLevel = appSettingsStorage.logLevel.value
        AppLanguageManager.apply(appSettingsStorage.appLanguage.value)
        val featureStore: FeatureStore = koin.get()
        featureStore.syncAppVersion(BuildConfig.VERSION_CODE)
    }
    /**
     * Stage 3: Observe auto-check-update preference and start/stop accordingly.
     */
    private fun startUpdateCheck(koin: Koin) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        val updateManager: GitHubUpdateManager = koin.get()
        startupScope.launch {
            appSettingsStorage.autoCheckAppUpdate.state
                .collect { enabled ->
                    if (enabled) {
                        updateManager.startAutoCheck(this)
                    } else {
                        updateManager.stopAutoCheck()
                    }
                }
        }
    }
    /**
     * Stage 4: Schedule deferred tasks on background scope (geo assets, warm-up, auto-start).
     */
    private fun scheduleDeferredStartupTasks(koin: Koin) {
        val featureStore: FeatureStore = koin.get()
        StartupTaskCoordinator.startWarmup(startupScope) {
            withContext(Dispatchers.IO) {
                runCatching { ensureGeoAssetsPrepared() }
                    .onFailure { Timber.e(it, "Geo assets preparation failed, continuing startup") }
            }
            runCatching { koin.get<CustomRoutingInitializer>().ensureDefaultContent() }
                .onFailure { Timber.e(it, "Failed to bootstrap custom routing default content") }
            runCatching { ensureMoeWallpaperLocalCopy(koin.get()) }
                .onFailure { Timber.e(it, "Failed to ensure Moe wallpaper local copy") }
            runCatching { koin.get<AppTrafficStatisticsCollector>() }
                .onFailure { Timber.w(it, "App traffic collector init skipped") }
            runCatching { koin.get<ProxyFacade>().awaitProxyGroupWarmUp() }
                .onFailure { Timber.w(it, "Proxy preview warm-up skipped") }
            if (featureStore.isFirstTimeOpen()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        koin.getAll<FirstRunInitializer>().forEach { it.initialize() }
                        featureStore.markFirstOpenHandled()
                    }.onFailure { error ->
                        Timber.w(error, "First-open asset initialization failed")
                    }
                }
            }
        }
        startupScope.launch {
            StartupTaskCoordinator.awaitWarmup()
            if (!AutoStartSessionGate.tryBeginAutoActions()) return@launch
            var handled = false
            try {
                ProxyAutoStartHelper.checkAndAutoStart(
                    context = this@App,
                    featureStore = featureStore,
                    proxyFacade = koin.get(),
                    profilesRepository = koin.get(),
                    appSettingsStorage = koin.get(),
                    networkSettingsStorage = koin.get(),
                    serviceCache = koin.get(qualifier = named("service_cache")),
                )
                handled = true
            } finally {
                AutoStartSessionGate.finishAutoActions(markHandled = handled)
            }
        }
    }
    /**
     * Recover from incomplete atomic file operations. If the process was killed between
     * rename(old→.bak) and rename(new→target), the .bak directory is orphaned and the target
     * is missing. This restores .bak → target on startup.
     *
     * Covers:
     * - Profile updates: imported/{uuid}.bak → imported/{uuid}
     * - Backup restores: imported.bak, overrides.bak, substore-data.bak
     */
    private fun recoverIncompleteOperations() {
        // Recover per-profile directories (from ProfileProcessor.update)
        val importedDir = filesDir.resolve("imported")
        if (importedDir.isDirectory) {
            importedDir.listFiles()
                ?.filter { it.isDirectory && it.name.endsWith(".bak") }
                ?.forEach { bakDir ->
                    val targetName = bakDir.name.removeSuffix(".bak")
                    val target = importedDir.resolve(targetName)
                    if (!target.exists()) {
                        bakDir.renameTo(target)
                        Timber.i("Startup recovery: restored imported/$targetName")
                    } else {
                        bakDir.deleteRecursively()
                    }
                }
        }
        // Recover top-level directories (from BackupRepository.replaceBackupDirectory)
        val subStoreDataDir = com.github.yumelira.yumebox.core.util.SubStorePaths.dataDir
        val recoverableDirs = listOf(
            filesDir.resolve("imported") to filesDir.resolve("imported.bak"),
            filesDir.resolve("overrides") to filesDir.resolve("overrides.bak"),
            subStoreDataDir to File(subStoreDataDir.parentFile, subStoreDataDir.name + ".bak"),
        )
        for ((target, bak) in recoverableDirs) {
            if (bak.isDirectory && !target.exists()) {
                bak.renameTo(target)
                Timber.i("Startup recovery: restored ${target.name}")
            } else if (bak.exists()) {
                bak.deleteRecursively()
            }
        }
    }

    /**
     * Best-effort self-heal for the Moe wallpaper: if the persisted
     * [AppSettingsStore.moeWallpaperUri] points at a local file:// copy that no longer exists, but
     * the original source is still recorded and readable, re-import it so the home screen keeps
     * rendering the user's choice after a cache or data wipe. Silent no-op otherwise; the render
     * path falls back to the bundled asset.
     */
    private suspend fun ensureMoeWallpaperLocalCopy(appSettings: AppSettingsStore) {
        val stored = appSettings.moeWallpaperUri.value
        if (!stored.startsWith("file://")) return
        val localPath = stored.removePrefix("file://")
        if (localPath.startsWith("/android_asset/")) return
        if (File(localPath).exists()) return
        val source = appSettings.moeWallpaperSourceUri.value
        if (source.isBlank()) return
        val reimported = MoeWallpaperImporter.importToLocal(this, source)
        if (reimported != null) {
            appSettings.moeWallpaperUri.set(reimported)
        }
    }
    private suspend fun observeAndBroadcastForegroundState() {
        AppForegroundState.foreground
            .collect { isForeground ->
                runCatching {
                    val intent = android.content.Intent(
                        Intents.actionAppForeground(packageName)
                    ).apply {
                        setPackage(packageName)
                        putExtra(Intents.EXTRA_APP_FOREGROUND, isForeground)
                    }
                    sendBroadcast(intent)
                }
            }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguageManager.refreshSystemLanguage()
    }
    override fun onTerminate() {
        runCatching {
            val koin = GlobalContext.getOrNull()
            koin?.getAll<AppShutdownHandler>()?.forEach { handler ->
                runCatching { handler.onShutdown() }
            }
        }
        runCatching { startupScope.cancel() }
        super.onTerminate()
    }
    private fun ensureGeoAssetsPrepared() {
        val homeDir = runtimeHomeDir.apply { mkdirs() }
        val mmkv = MMKV.mmkvWithID("geo_extract_state")
        val geoNames = listOf("geoip.metadb", "geosite.dat", "ASN.mmdb")
        geoNames.forEach { name ->
            val target = File(homeDir, name)
            if (target.exists() && target.length() > 0L) return@forEach
            val failureKey = "fail_count_$name"
            val failCount = mmkv.getInt(failureKey, 0)
            if (failCount >= GEO_EXTRACT_MAX_FAILURES) {
                Timber.w(
                    "Geo asset %s.xz skipped: failed %d times previously",
                    name, failCount,
                )
                return@forEach
            }
            val prepared = extractXzAsset("$name.xz", target)
            if (prepared) {
                mmkv.removeValueForKey(failureKey)
            } else {
                val newCount = failCount + 1
                mmkv.putInt(failureKey, newCount)
                Timber.w("Geo asset decompress failed: %s.xz (attempt %d/%d)", name, newCount, GEO_EXTRACT_MAX_FAILURES)
            }
        }
    }
    private fun extractXzAsset(assetName: String, target: File): Boolean {
        return runCatching {
            target.parentFile?.mkdirs()
            assets.open(assetName).use { input ->
                XZInputStream(input.buffered()).use { xz ->
                    target.outputStream().buffered().use { output ->
                        xz.copyTo(output)
                    }
                }
            }
            true
        }.getOrElse { error ->
            runCatching { if (target.exists()) target.delete() }
            Timber.w(error, "Geo asset extract failed: %s", assetName)
            false
        }
    }
    private fun copyAsset(name: String, target: File) {
        target.parentFile?.mkdirs()
        assets.open(name).use { it.copyTo(target.outputStream()) }
    }
}
