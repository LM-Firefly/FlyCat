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
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.github.lmfirefly.flycat.BuildConfig
import com.github.lmfirefly.flycat.common.util.AppLanguageManager
import com.github.lmfirefly.flycat.core.FirstRunInitializer
import com.github.lmfirefly.flycat.core.Global
import com.github.lmfirefly.flycat.core.bridge.Bridge
import com.github.lmfirefly.flycat.core.contract.AppLogSettings
import com.github.lmfirefly.flycat.core.contract.AppShutdownHandler
import com.github.lmfirefly.flycat.core.contract.CustomRoutingInitializer
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.RuntimeLogWriter
import com.github.lmfirefly.flycat.core.contract.ServiceBootstrapHolder
import com.github.lmfirefly.flycat.core.util.AppForegroundState
import com.github.lmfirefly.flycat.core.util.NativeLibraryLoader
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.core.util.coroutine.StartupTaskCoordinator
import com.github.lmfirefly.flycat.core.util.coroutine.safeRun
import com.github.lmfirefly.flycat.core.util.coroutine.safeRunSilent
import com.github.lmfirefly.flycat.core.util.path.APPLICATION_SCOPE_NAME
import com.github.lmfirefly.flycat.core.util.path.SubStorePaths
import com.github.lmfirefly.flycat.core.util.path.runtimeHomeDir
import com.github.lmfirefly.flycat.data.collector.AppTrafficStatisticsCollector
import com.github.lmfirefly.flycat.data.logging.AppLogBridge
import com.github.lmfirefly.flycat.data.logging.AppLogTree
import com.github.lmfirefly.flycat.data.logging.CrashHandler
import com.github.lmfirefly.flycat.data.store.AppSettingsStore
import com.github.lmfirefly.flycat.data.store.FeatureStore
import com.github.lmfirefly.flycat.di.ServiceBootstrapReaderImpl
import com.github.lmfirefly.flycat.di.appModule
import com.github.lmfirefly.flycat.feature.about.GitHubUpdateManager
import com.github.lmfirefly.flycat.feature.settings.presentation.util.MoeWallpaperImporter
import com.github.lmfirefly.flycat.presentation.component.misc.WallpaperDecodeCache
import com.github.lmfirefly.flycat.presentation.component.misc.resolveWallpaperModel
import com.github.lmfirefly.flycat.runtime.api.constants.Components
import com.github.lmfirefly.flycat.runtime.api.constants.Intents
import com.github.lmfirefly.flycat.runtime.api.contract.AppScreenState
import com.github.lmfirefly.flycat.runtime.client.ProxyFacade
import com.github.lmfirefly.flycat.runtime.client.util.ProxyAutoStartUtils
import com.github.lmfirefly.flycat.runtime.service.android.RuntimeRecoveryWorker
import com.github.lmfirefly.flycat.runtime.service.android.WifiAutomationService
import com.github.lmfirefly.flycat.runtime.service.profile.ProfileProcessor
import com.tencent.mmkv.MMKV
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.tukaani.xz.XZInputStream
import timber.log.Timber

class App : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    companion object { private const val GEO_EXTRACT_MAX_FAILURES = 3 }
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        /** priv-kit 接入要求：全局豁免 hidden API。
         * 配对协议反射平台 conscrypt 的隐藏方法 (Conscrypt.exportKeyingMaterial)，被 hidden API enforcement 屏蔽时表现为 NoSuchMethodError。
         * release 的 pack 加载器也会豁免，此处幂等兼容 debug 构建。 */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { runCatching { HiddenApiBypass.addHiddenApiExemptions("L") } }
        // 只压 so 方案：解载 XZ 原生载荷并注入 classloader 搜索路径（必须在 HiddenApiBypass 之后）。
        NativeLibraryLoader.installCompressedNativeLibs(this)
    }
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) { Timber.plant(Timber.DebugTree()) }
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
        // 阶段1：核心基础架构（logging, crash handler, native globals）
        val koin = try { initCoreInfrastructure() } catch (e: Exception) {
            Timber.e(e, "Fatal error during core infrastructure init")
            CrashHandler.init(this)
            return
        }
        // 阶段2：应用用户设置
        startupScope.launch { safeRun("App", "Apply user settings") { applyUserSettings(koin) } }
        // 阶段2a：在后台线程上预加载原生桥接库
        startupScope.launch { withContext(Dispatchers.IO) { safeRun("App", "Bridge preload") { Bridge.preload() } } }
        // 阶段2b：预热壁纸解码。必须并行尽早启动（不要排进启动任务链——geo 等慢任务会让预热赶不上 UI 首帧）。
        // 首帧经 WallpaperDecodeCache.imageBitmap 取图：预热入槽则直接命中；未赶上则在首帧组合中同步解码（耗时被 Splash 吸收），两条路径都保证用户看到的第一帧已带壁纸，预热只是把解码开销从首帧挪到后台。
        startupScope.launch {
            withContext(Dispatchers.IO) {
                safeRun("App", "Pre-decode Moe wallpaper") {
                    val appSettings: AppSettingsStore = koin.get()
                    val model = resolveWallpaperModel(this@App, appSettings.moeWallpaperUri.value)
                    val metrics = resources.displayMetrics
                    WallpaperDecodeCache.warmUp(this@App, model, metrics.widthPixels, metrics.heightPixels)
                }
            }
        }
        // mihomo 日志订阅由 MihomoLogViewModel 在预览可见时直接持有（Clash.subscribeLogcat），不再经此中转。
        val applicationScope = koin.get<CoroutineScope>(named(APPLICATION_SCOPE_NAME))
        // 阶段2c：从不完整的文件操作（原子重命名残留物）中恢复
        startupScope.launch { withContext(Dispatchers.IO) { safeRun("App", "Startup recovery") { recoverIncompleteOperations() } } }
        // 阶段3：调度更新检查与运行时恢复观察者
        safeRun("App", "Schedule update check") { startUpdateCheck(koin, applicationScope) }
        safeRun("App", "Schedule runtime recovery") { observeRuntimeRecoveryScheduling(koin, applicationScope) }
        // 阶段4：延迟运行时任务（geo 资产、自定义路由默认值、壁纸本地副本、流量采集、代理预热、配置自动更新、首次打开初始化、Wi-Fi 自动化与代理自动启动）
        safeRun("App", "Schedule deferred startup tasks") { scheduleDeferredStartupTasks(koin) }
        // 阶段5：独立生命周期观察者（始终安全可启动）
        applicationScope.launch { observeAndBroadcastForegroundState() }
    }
    /** 阶段 1：初始化日志记录、崩溃处理器、全局上下文、MMKV 和 Koin 依赖注入。成功时返回 Koin 实例；严重失败时抛出异常。 */
    private fun initCoreInfrastructure(): Koin {
        // AppLogTree 必须始终植入（App 日志缓冲与崩溃日志附带的「最近日志」都取自它）： debug 构建在 onCreate 已植入裸 DebugTree，旧 isEmpty 守卫会在此跳过 AppLogTree，
        // 导致 AppLog 完全不采集（录制文件恒 0B、预览/导出恒空）。
        if (Timber.forest().none { it is AppLogTree }) {
            Timber.plant(AppLogTree())
            // AppLogTree 继承 DebugTree（同样输出 logcat）；移除裸 DebugTree 避免 debug 构建日志重复。
            Timber.forest().filter { it is Timber.DebugTree && it !is AppLogTree }.forEach(Timber::uproot)
        }
        CrashHandler.init(this)
        AppLogBridge.runtimeLogWriter = null
        Global.init(this)
        // MMKV 必须在使用前初始化，ContentProvider 在 Application.onCreate 之前执行
        MMKV.initialize(this)
        MMKV.disableProcessModeChecker()
        com.github.lmfirefly.flycat.core.BuildConfigHolder.init(
            versionName = BuildConfig.VERSION_NAME,
            applicationId = BuildConfig.APPLICATION_ID,
            uiBuildId = BuildConfig.UI_BUILD_ID,
            kernelGitVersion = BuildConfig.KERNEL_GIT_VERSION,
        )
        Components.register(mainActivityClassName = MainActivity::class.java.name, proxySheetActivityClassName = ProxySheetActivity::class.java.name)
        Timber.d("Core infrastructure initialized")
        val koin = startKoin {
            androidContext(this@App)
            modules(appModule)
        }.koin
        AppLogBridge.runtimeLogWriter = koin.get<RuntimeLogWriter>()::writeLog
        ServiceBootstrapHolder.initialize(ServiceBootstrapReaderImpl(appSettingsStore = koin.get(), featureStore = koin.get(), networkSettingsStore = koin.get(), mmkvProvider = koin.get()))
        return koin
    }
    /** 阶段 2：应用已保存的用户偏好设置（日志级别、语言、功能版本）。 */
    private fun applyUserSettings(koin: Koin) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        val appLogSettings: AppLogSettings = koin.get()
        appLogSettings.minLogLevel = appSettingsStorage.logLevel.value
        AppLanguageManager.apply(appSettingsStorage.appLanguage.value)
        val featureStore: FeatureStore = koin.get()
        featureStore.syncAppVersion(BuildConfig.VERSION_CODE)
    }
    /** 阶段 3：观察自动检查更新偏好，并相应地启动/停止自动更新检查。 */
    private fun startUpdateCheck(koin: Koin, applicationScope: CoroutineScope) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        val updateManager: GitHubUpdateManager = koin.get()
        applicationScope.launch {
            appSettingsStorage.autoCheckAppUpdate.state.collect { enabled -> if (enabled) { updateManager.startAutoCheck(this) } else { updateManager.stopAutoCheck() } }
        }
    }
    /** 仅在"自动重启"开启时调度周期性运行时恢复检查，关闭即取消，避免无谓的后台唤醒。*/
    private fun observeRuntimeRecoveryScheduling(koin: Koin, applicationScope: CoroutineScope) {
        val appSettingsStorage: AppSettingsStore = koin.get()
        applicationScope.launch {
            appSettingsStorage.automaticRestart.state.collect { enabled -> if (enabled) { RuntimeRecoveryWorker.schedule(this@App) } else { RuntimeRecoveryWorker.cancel(this@App) } }
        }
    }
    /** 阶段 4：在后台范围调度延迟任务（geo 资产、自定义路由默认值、壁纸本地副本、流量采集、代理预热、配置自动更新、首次打开初始化），预热链结束后再恢复 Wi-Fi 自动化并执行代理自动启动。 */
    private fun scheduleDeferredStartupTasks(koin: Koin) {
        val featureStore: FeatureStore = koin.get()
        StartupTaskCoordinator.startWarmup(startupScope) {
            withContext(Dispatchers.IO) { safeRun("App", "Prepare geo assets") { ensureGeoAssetsPrepared() } }
            safeRun("App", "Bootstrap custom routing defaults") { koin.get<CustomRoutingInitializer>().ensureDefaultContent() }
            safeRun("App", "Ensure Moe wallpaper local copy") { ensureMoeWallpaperLocalCopy(koin.get()) }
            safeRun("App", "Init traffic collector") { koin.get<AppTrafficStatisticsCollector>() }
            safeRun("App", "Proxy preview warm-up") { koin.get<ProxyFacade>().awaitProxyGroupWarmUp() }
            safeRun("App", "Schedule profile auto-update") { ProfileProcessor.AutoUpdate.scheduleAll(this@App) }
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
        // 使用 applicationScope 以便自动启动能够在 startupScope 被取消后存活（例如 onTrimMemory）。
        val applicationScope = koin.get<CoroutineScope>(named(APPLICATION_SCOPE_NAME))
        applicationScope.launch {
            StartupTaskCoordinator.awaitWarmup()
            // 如果用户之前已启用过，则恢复Wi-Fi自动化服务。
            safeRun("App", "Restore Wi-Fi automation") { if (networkSettingsStorage.wifiAutomationEnabled.value) { WifiAutomationService.start(this@App) } }
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
            } finally { AutoStartSessionGate.finishAutoActions(markHandled = handled) }
        }
    }
    /**
     *  从不完整的原子文件操作中恢复。如果进程在 rename(old→bak) 与 rename(new→target) 之间被终止，则 .bak 目录会被孤立，且目标文件丢失。
     *  此机制在启动时会恢复 .bak→target。
     *  涵盖范围：
     *  - 配置文件更新：imported/{uuid}.bak→imported/{uuid}
     *  - 备份还原：imported.bak, overrides.bak, substore-data.bak
     */
    private fun recoverIncompleteOperations() {
        // 恢复每个配置的目录（来自 ProfileProcessor.update）
        val importedDir = filesDir.resolve("imported")
        if (importedDir.isDirectory) {
            importedDir.listFiles()?.filter { it.isDirectory && it.name.endsWith(".bak") }?.forEach { bakDir ->
                val targetName = bakDir.name.removeSuffix(".bak")
                val target = importedDir.resolve(targetName)
                if (!target.exists()) {
                    moveDirectoryFallback(bakDir, target)
                    Timber.i("Startup recovery: restored imported/$targetName")
                } else { bakDir.deleteRecursively() }
            }
        }
        // 恢复顶层目录（来自 BackupRepository.replaceBackupDirectory）
        val subStoreDataDir = SubStorePaths.dataDir
        val recoverableDirs = listOf(
            filesDir.resolve("imported") to filesDir.resolve("imported.bak"),
            filesDir.resolve("overrides") to filesDir.resolve("overrides.bak"),
            subStoreDataDir to File(subStoreDataDir.parentFile, subStoreDataDir.name + ".bak"),
        )
        for ((target, bak) in recoverableDirs) {
            if (bak.isDirectory && !target.exists()) {
                moveDirectoryFallback(bak, target)
                Timber.i("Startup recovery: restored ${target.name}")
            } else if (bak.exists()) { bak.deleteRecursively() }
        }
    }
    /** 将 [source] 移动到 [target]，当 renameTo 静默失败时回退到复制操作。 */
    private fun moveDirectoryFallback(source: File, target: File) {
        if (source.renameTo(target)) return
        target.deleteRecursively()
        source.copyRecursively(target, overwrite = true)
        source.deleteRecursively()
    }
    /**
     * Moe 壁纸尽力自愈：如果持久化的 [AppSettingsStore.moeWallpaperUri] 指向一个已不存在的本地 file:// 副本，但原始来源仍被记录且可读取，则重新导入该文件，以便主屏幕在缓存或数据清除后仍能继续渲染用户的选择。
     * 其余情况静默无操作；副本确实无法恢复时，渲染路径（resolveWallpaperModel）回退到内置壁纸。
     */
    private suspend fun ensureMoeWallpaperLocalCopy(appSettings: AppSettingsStore) {
        val stored = appSettings.moeWallpaperUri.value
        if (!stored.startsWith("file://")) return
        val localPath = stored.removePrefix("file://").substringBefore('#')
        if (localPath.startsWith("/android_asset/")) return
        if (File(localPath).exists()) return
        val source = appSettings.moeWallpaperSourceUri.value
        if (source.isBlank()) return
        val reimported = MoeWallpaperImporter.importToLocal(this, source)
        if (reimported != null) { appSettings.moeWallpaperUri.set(reimported) }
    }
    private suspend fun observeAndBroadcastForegroundState() {
        AppForegroundState.foreground.collect { isForeground ->
            safeRunSilent("App", "Broadcast foreground state") {
                val intent = android.content.Intent(Intents.actionAppForeground(packageName)).apply {
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
            // 进程即将被终止→取消startupScope以及时释放资源。
            safeRunSilent("App", "Cancel startup scope on trim memory") { startupScope.cancel() }
        }
    }
    override fun onTerminate() {
        safeRunSilent("App", "Execute shutdown handlers") {
            val koin = GlobalContext.getOrNull()
            koin?.getAll<AppShutdownHandler>()?.forEach { handler -> safeRunSilent("App", "Execute shutdown handler") { handler.onShutdown() } }
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
                Timber.w("Geo asset %s.xz skipped: failed %d times previously", name, failCount)
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
            assets.open(assetName).use { input -> XZInputStream(input.buffered()).use { xz -> target.outputStream().buffered().use { output -> xz.copyTo(output) } } }
            true
        }.getOrElse { error ->
            runCatching { if (target.exists()) target.delete() }
            Timber.w(error, "Geo asset extract failed: %s", assetName)
            false
        }
    }
}
