package com.github.yumelira.yumebox

import android.app.Application
import com.github.yumelira.yumebox.core.Global
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.util.AppScreenState
import com.github.yumelira.yumebox.core.util.StartupTaskCoordinator
import com.github.yumelira.yumebox.data.controller.AppIdentityResolver
import com.github.yumelira.yumebox.data.gateway.RuntimeLogWriter
import com.github.yumelira.yumebox.data.logging.AppLogBridge
import com.github.yumelira.yumebox.data.logging.AppLogBuffer
import com.github.yumelira.yumebox.data.logging.AppLogTree
import com.github.yumelira.yumebox.data.logging.CrashHandler
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.di.appModule
import com.github.yumelira.yumebox.feature.proxy.di.featureProxyModules
import com.github.yumelira.yumebox.platform.runtime.StartupGate
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Components
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.update.GitHubUpdateManager
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import timber.log.Timber

class App : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        if (Timber.forest().isEmpty()) {
            Timber.plant(AppLogTree())
        }
        CrashHandler.init(this)
        AppLogBridge.runtimeLogWriter = null

        AppScreenState.init(this)
        StartupGate.loadPrimary()
        Global.init(this)
        Components.register(
            mainActivityClassName = MainActivity::class.java.name,
            proxySheetActivityClassName = ProxySheetActivity::class.java.name,
        )
        MMKV.initialize(this)

        val koin =
            startKoin {
                androidContext(this@App)
                modules(appModule + featureProxyModules)
            }.koin

        AppLogBridge.runtimeLogWriter = koin.get<RuntimeLogWriter>()::writeLog

        val updateManager: GitHubUpdateManager = koin.get()
        bootstrapDefaults(
            appSettings = koin.get(),
            networkSettings = koin.get(),
            networkSettingsStore = koin.get(named("network_settings")),
        )
        startupScope.launch {
            koin.get<AppSettingsStore>().autoCheckAppUpdate.state.collect { enabled ->
                if (enabled) {
                    updateManager.startAutoCheck(this)
                } else {
                    updateManager.stopAutoCheck()
                }
            }
        }
        scheduleWarmup(koin.get())
    }

    private fun bootstrapDefaults(
        appSettings: AppSettingsStore,
        networkSettings: NetworkSettingsStore,
        networkSettingsStore: MMKV,
    ) {
        AppLogBuffer.minLogLevel = appSettings.logLevel.value
        networkSettings.proxyMode.set(ProxyMode.Tun)
        if (!networkSettingsStore.containsKey("bypassPrivateNetwork")) {
            networkSettings.bypassPrivateNetwork.set(false)
        }
        if (!networkSettingsStore.containsKey("dnsHijack")) {
            networkSettings.dnsHijack.set(true)
        }
        if (!networkSettingsStore.containsKey("allowBypass")) {
            networkSettings.allowBypass.set(false)
        }

        if (!appSettings.initialSetupCompleted.value) {
            appSettings.initialSetupCompleted.set(true)
            appSettings.privacyPolicyAccepted.set(true)
        }
    }

    private fun scheduleWarmup(proxyFacade: ProxyFacade) {
        StartupTaskCoordinator.startWarmup(startupScope) {
            try {
                proxyFacade.awaitProxyGroupWarmUp()
            } catch (error: Exception) {
                Timber.w(error, "Proxy preview warm-up skipped")
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
}
