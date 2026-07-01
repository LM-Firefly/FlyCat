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
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.AppScreenState
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.StartupTaskCoordinator
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.data.controller.AppIdentityResolver
import com.github.yumelira.yumebox.data.controller.AppTrafficStatisticsCollector
import com.github.yumelira.yumebox.data.gateway.RuntimeLogWriter
import com.github.yumelira.yumebox.data.logging.AppLogBridge
import com.github.yumelira.yumebox.data.logging.AppLogBuffer
import com.github.yumelira.yumebox.data.logging.AppLogTree
import com.github.yumelira.yumebox.data.logging.CrashHandler
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.di.appModule
import com.github.yumelira.yumebox.feature.meta.presentation.util.CustomRoutingBootstrapper
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Components
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.client.common.util.ProxyAutoStartHelper
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.screen.settings.MoeWallpaperImporter
import com.github.yumelira.yumebox.update.GitHubUpdateManager
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
        // Stage 2: Apply user settings (non-fatal if partially fails)
        runCatching { applyUserSettings(koin) }
            .onFailure { Timber.e(it, "Error applying user settings") }
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
        return koin
    }
    /**
     * Stage 2: Apply persisted user preferences (language, log level, predictive back, feature version).
     */
    private fun applyUserSettings(koin: Koin) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        AppLogBuffer.minLogLevel = appSettingsStorage.logLevel.value
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
                ensureGeoAssetsPrepared()
            }
            runCatching { koin.get<CustomRoutingBootstrapper>().ensureDefaultContent() }
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

    override fun onTerminate() {
        runCatching {
            val koin = GlobalContext.getOrNull()
            koin?.getOrNull<ProxyFacade>()?.shutdown()
            koin?.getOrNull<AppIdentityResolver>()?.close()
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
}
