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

package com.github.lmfirefly.flycat

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.github.lmfirefly.flycat.BuildConfig
import com.github.lmfirefly.flycat.common.util.AppLanguageManager
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.FirstRunInitializer
import com.github.lmfirefly.flycat.core.Global
import com.github.lmfirefly.flycat.core.bridge.Bridge
import com.github.lmfirefly.flycat.core.contract.AppLogSettings
import com.github.lmfirefly.flycat.core.contract.AppShutdownHandler
import com.github.lmfirefly.flycat.core.contract.CustomRoutingInitializer
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.RuntimeLogWriter
import com.github.lmfirefly.flycat.core.contract.ServiceBootstrapHolder
import com.github.lmfirefly.flycat.core.model.LogMessage
import com.github.lmfirefly.flycat.core.util.AppForegroundState
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.core.util.coroutine.StartupTaskCoordinator
import com.github.lmfirefly.flycat.core.util.coroutine.safeRun
import com.github.lmfirefly.flycat.core.util.coroutine.safeRunSilent
import com.github.lmfirefly.flycat.core.util.path.APPLICATION_SCOPE_NAME
import com.github.lmfirefly.flycat.core.util.path.SubStorePaths
import com.github.lmfirefly.flycat.core.util.path.runtimeHomeDir
import com.github.lmfirefly.flycat.data.collector.AppTrafficStatisticsCollector
import com.github.lmfirefly.flycat.data.logging.AppLogBridge
import com.github.lmfirefly.flycat.data.logging.AppLogBuffer
import com.github.lmfirefly.flycat.data.logging.AppLogTree
import com.github.lmfirefly.flycat.data.logging.CrashHandler
import com.github.lmfirefly.flycat.data.store.AppSettingsStore
import com.github.lmfirefly.flycat.data.store.FeatureStore
import com.github.lmfirefly.flycat.di.ServiceBootstrapReaderImpl
import com.github.lmfirefly.flycat.di.appModule
import com.github.lmfirefly.flycat.feature.about.GitHubUpdateManager
import com.github.lmfirefly.flycat.feature.settings.presentation.util.MoeWallpaperImporter
import com.github.lmfirefly.flycat.runtime.api.constants.Components
import com.github.lmfirefly.flycat.runtime.api.constants.Intents
import com.github.lmfirefly.flycat.runtime.api.contract.AppScreenState
import com.github.lmfirefly.flycat.runtime.client.ProxyFacade
import com.github.lmfirefly.flycat.runtime.client.util.ProxyAutoStartUtils
import com.github.lmfirefly.flycat.runtime.service.android.WifiAutomationService
import com.tencent.mmkv.MMKV
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.tukaani.xz.XZInputStream
import timber.log.Timber

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
        // Stage 2: Apply user settings (deferred→language already wrapped in attachBaseContext)
        startupScope.launch {
            safeRun("App", "Apply user settings") { applyUserSettings(koin) }
        }
        // Stage 2a: Preload native bridge libraries on background thread
        startupScope.launch {
            withContext(Dispatchers.IO) {
                safeRun("App", "Bridge preload") { Bridge.preload() }
            }
        }
        // On-demand mihomo log→AppLogBuffer subscription.
        // Enabled only when log preview is visible or runtime log recording is active.
        val applicationScope = koin.get<CoroutineScope>(named(APPLICATION_SCOPE_NAME))
        applicationScope.launch {
            var streamJob: Job? = null
            AppLogBridge.mihomoSubscriptionDemand.collect { needed ->
                if (!needed) {
                    streamJob?.cancelAndJoin()
                    streamJob = null
                    Clash.unsubscribeLogcat()
                    return@collect
                }
                if (streamJob?.isActive == true) {
                    return@collect
                }
                streamJob =
                    launch(Dispatchers.IO) {
                        safeRunSilent("App", "Mihomo on-demand log subscription") {
                            val channel = Clash.subscribeLogcat()
                            try {
                                for (msg in channel) {
                                    val priority =
                                        when (msg.level) {
                                            LogMessage.Level.Error -> android.util.Log.ERROR
                                            LogMessage.Level.Warning -> android.util.Log.WARN
                                            LogMessage.Level.Info -> android.util.Log.INFO
                                            LogMessage.Level.Debug -> android.util.Log.DEBUG
                                            LogMessage.Level.Silent -> android.util.Log.VERBOSE
                                            LogMessage.Level.Unknown -> android.util.Log.DEBUG
                                        }
                                    AppLogBridge.mihomoLogSink?.invoke(priority, "mihomo", msg.message)
                                }
                            } finally {
                                channel.cancel()
                                Clash.unsubscribeLogcat()
                            }
                        }
                    }
            }
        }
        // Stage 2b: Recover from incomplete file operations (atomic rename leftovers)
        startupScope.launch {
            withContext(Dispatchers.IO) {
                safeRun("App", "Startup recovery") { recoverIncompleteOperations() }
            }
        }
        // Stage 3: Start update check coroutine
        safeRun("App", "Schedule update check") { startUpdateCheck(koin, applicationScope) }
        // Stage 4: Deferred runtime tasks (geo assets, traffic collector, auto-start)
        safeRun("App", "Schedule deferred startup tasks") { scheduleDeferredStartupTasks(koin) }
        // Stage 5: Independent lifecycle observers (always safe to start)
        applicationScope.launch { observeAndBroadcastForegroundState() }
    }
    /**
     * Stage 1: Initialize logging, crash handler, Global context, MMKV, and Koin DI.
     * Returns the Koin instance on success; throws on fatal failure.
     */
    private fun initCoreInfrastructure(): Koin {
        if (Timber.forest().isEmpty()) Timber.plant(AppLogTree())
        CrashHandler.init(this)
        AppLogBridge.runtimeLogWriter = null
        AppLogBridge.mihomoLogSink = { priority, tag, message ->
            AppLogBuffer.forceAdd(priority, tag, message)
        }
        Global.init(this)
        com.github.lmfirefly.flycat.core.BuildConfigHolder.init(
            versionName = BuildConfig.VERSION_NAME,
            applicationId = BuildConfig.APPLICATION_ID,
            uiBuildId = BuildConfig.UI_BUILD_ID,
            kernelGitVersion = BuildConfig.KERNEL_GIT_VERSION,
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
    private fun startUpdateCheck(koin: Koin, applicationScope: CoroutineScope) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        val updateManager: GitHubUpdateManager = koin.get()
        applicationScope.launch {
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
                safeRun("App", "Prepare geo assets") { ensureGeoAssetsPrepared() }
            }
            safeRun("App", "Bootstrap custom routing defaults") { koin.get<CustomRoutingInitializer>().ensureDefaultContent() }
            safeRun("App", "Ensure Moe wallpaper local copy") { ensureMoeWallpaperLocalCopy(koin.get()) }
            safeRun("App", "Init traffic collector") { koin.get<AppTrafficStatisticsCollector>() }
            safeRun("App", "Proxy preview warm-up") { koin.get<ProxyFacade>().awaitProxyGroupWarmUp() }
            if (featureStore.isFirstTimeOpen()) {
                withContext(Dispatchers.IO) {
                    safeRun("App", "First-open asset initialization") {
                        koin.getAll<FirstRunInitializer>().forEach { it.initialize() }
                        featureStore.markFirstOpenHandled()
                    }
                }
            }
        }
        val networkSettingsStorage = koin.get<NetworkSettingsReader>()
        // Use applicationScope so auto-start survives startupScope cancellation (e.g. onTrimMemory).
        val applicationScope = koin.get<CoroutineScope>(named(APPLICATION_SCOPE_NAME))
        applicationScope.launch {
            StartupTaskCoordinator.awaitWarmup()
            // Restore Wi-Fi automation service if the user previously enabled it.
            safeRun("App", "Restore Wi-Fi automation") {
                if (networkSettingsStorage.wifiAutomationEnabled.value) {
                    WifiAutomationService.start(this@App)
                }
            }
            if (!AutoStartSessionGate.tryBeginAutoActions()) return@launch
            var handled = false
            try {
                ProxyAutoStartUtils.checkAndAutoStart(
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
     * Recover from incomplete atomic file operations. If the process was killed between rename(old→bak) and rename(new→target), the .bak directory is orphaned and the target is missing.
     * This restores .bak→target on startup.
     *
     * Covers:
     * - Profile updates: imported/{uuid}.bak→imported/{uuid}
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
        val subStoreDataDir = SubStorePaths.dataDir
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
     * Best-effort self-heal for the Moe wallpaper: if the persisted [AppSettingsStore.moeWallpaperUri] points at a local file:// copy that no longer exists, but the original source is still recorded and readable, re-import it so the home screen keeps rendering the user's choice after a cache or data wipe.
     * Silent no-op otherwise; the render path falls back to the bundled asset.
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
                safeRunSilent("App", "Broadcast foreground state") {
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
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_COMPLETE) {
            // Process is about to be killed→cancel startupScope to release resources promptly.
            safeRunSilent("App", "Cancel startup scope on trim memory") { startupScope.cancel() }
        }
    }
    override fun onTerminate() {
        safeRunSilent("App", "Execute shutdown handlers") {
            val koin = GlobalContext.getOrNull()
            koin?.getAll<AppShutdownHandler>()?.forEach { handler ->
                safeRunSilent("App", "Execute shutdown handler") { handler.onShutdown() }
            }
        }
        safeRunSilent("App", "Cancel startup scope on terminate") { startupScope.cancel() }
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
