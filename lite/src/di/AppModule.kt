package com.github.yumelira.yumebox.di

import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.core.data.AppIdentityReader
import com.github.yumelira.yumebox.core.data.AppLogSettings
import com.github.yumelira.yumebox.core.data.AppSettingsReader
import com.github.yumelira.yumebox.core.data.FeatureStoreReader
import com.github.yumelira.yumebox.core.data.LogStoreReader
import com.github.yumelira.yumebox.core.data.NetworkInfoReader
import com.github.yumelira.yumebox.core.data.NetworkSettingsReader
import com.github.yumelira.yumebox.core.data.ProfileLinksReader
import com.github.yumelira.yumebox.core.data.ProvidersRepository
import com.github.yumelira.yumebox.core.data.ProxyDisplaySettingsReader
import com.github.yumelira.yumebox.core.data.RemoteControllerStoreReader
import com.github.yumelira.yumebox.core.data.SubStoreSettings
import com.github.yumelira.yumebox.core.data.TrafficStatisticsReader
import com.github.yumelira.yumebox.core.data.UpdateSettings
import com.github.yumelira.yumebox.core.domain.model.TrafficData
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.data.controller.AccessControlCommandExecutor
import com.github.yumelira.yumebox.data.controller.AccessControlController
import com.github.yumelira.yumebox.data.controller.AppIdentityResolver
import com.github.yumelira.yumebox.data.controller.AppSettingsController
import com.github.yumelira.yumebox.data.controller.AppTrafficStatisticsCollector
import com.github.yumelira.yumebox.data.controller.NetworkSettingsCommandExecutor
import com.github.yumelira.yumebox.data.controller.NetworkSettingsController
import com.github.yumelira.yumebox.data.controller.ProvidersController
import com.github.yumelira.yumebox.data.gateway.LogRecordGateway
import com.github.yumelira.yumebox.data.gateway.NetworkInfoService
import com.github.yumelira.yumebox.data.gateway.RuntimeLogWriter
import com.github.yumelira.yumebox.runtime.service.di.runtimeServiceModule
import com.github.yumelira.yumebox.data.logging.AppLogBuffer
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.AppStateManager
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.LogStore
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import com.github.yumelira.yumebox.data.store.ProfileBindingProvider
import com.github.yumelira.yumebox.data.store.ProfileBindingStore
import com.github.yumelira.yumebox.data.store.ProfileLinksStore
import com.github.yumelira.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.data.store.TrafficStatisticsStore
import com.github.yumelira.yumebox.data.store.room.createTrafficStatisticsDao
import com.github.yumelira.yumebox.lite.config.TunProfileSync
import com.github.yumelira.yumebox.platform.util.APPLICATION_SCOPE_NAME
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.importconfig.ImportConfigViewModel
import com.github.yumelira.yumebox.screen.log.LogViewModel
import com.github.yumelira.yumebox.screen.settings.AccessControlViewModel
import com.github.yumelira.yumebox.screen.settings.VpnSettingsViewModel
import com.github.yumelira.yumebox.update.GitHubUpdateManager
import com.github.yumelira.yumebox.update.GitHubUpdateViewModel
import com.github.yumelira.yumebox.update.UpdateBuildConfig
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val appFoundationModule = module {
    single<CoroutineScope>(named(APPLICATION_SCOPE_NAME)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single { MMKVProvider() }
    single<MMKV>(named("profiles")) { get<MMKVProvider>().getMMKV("profiles") }
    single<MMKV>(named("settings")) { get<MMKVProvider>().getMMKV("settings") }
    single<MMKV>(named("network_settings")) { get<MMKVProvider>().getMMKV("network_settings") }
    single<MMKV>(named("substore")) { get<MMKVProvider>().getMMKV("substore") }
    single<MMKV>(named("proxy_display")) { get<MMKVProvider>().getMMKV("proxy_display") }
    single<MMKV>(named("traffic_statistics")) { get<MMKVProvider>().getMMKV("traffic_statistics") }
    single<MMKV>(named("profile_links")) { get<MMKVProvider>().getMMKV("profile_links") }
    single { AppSettingsStore(get(named("settings"))) }
    single { NetworkSettingsStore(get(named("network_settings"))) }
    single { ProfileLinksStore(get(named("profile_links"))) }
    single { FeatureStore(get(named("substore"))) }
    single { ProxyDisplaySettingsStore(get(named("proxy_display"))) }
    single { createTrafficStatisticsDao(androidApplication()) }
    single { TrafficStatisticsStore(get(named("traffic_statistics")), get()) }
    single { RemoteControllerStore(get<MMKVProvider>().getMMKV("remote_controller")) }

    // Bind store reader interfaces for runtime:client consumption
    single<NetworkSettingsReader> { get<NetworkSettingsStore>() }
    single<AppSettingsReader> { get<AppSettingsStore>() }
    single<FeatureStoreReader> { get<FeatureStore>() }
    single<ProxyDisplaySettingsReader> { get<ProxyDisplaySettingsStore>() }
    single<TrafficStatisticsReader> { get<TrafficStatisticsStore>() }
    single<SubStoreSettings> { get<FeatureStore>() }
    single<RemoteControllerStoreReader> {
        get<RemoteControllerStore>().also {
            com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.configure(it)
        }
    }
    single<UpdateSettings> { get<AppSettingsStore>() }
    single<ProfileLinksReader> { get<ProfileLinksStore>() }
    single<NetworkInfoReader> { get<NetworkInfoService>() }
    single<LogStoreReader> { get<LogStore>() }
    single<AppLogSettings> { AppLogBuffer }

    single {
        AppStateManager(
            appSettingsStore = get(),
            networkSettingsStore = get(),
            featureStore = get(),
            profileLinksStore = get(),
            proxyDisplaySettingsStore = get(),
            trafficStatisticsStore = get(),
        )
    }
}

private val appDataRuntimeModule = module {
    single { AppSettingsController(get(), applyLanguage = AppLanguageManager::apply) }
    single {
        val proxyFacade = get<ProxyFacade>()
        val tunProfileSync = get<TunProfileSync>()
        NetworkSettingsCommandExecutor(
            store = get(),
            restartProxy = { mode -> proxyFacade.startProxy(mode) },
            beforeRestart = { targetMode ->
                if (targetMode == ProxyMode.Tun) {
                    tunProfileSync.syncActiveProfile()
                }
            },
        )
    }
    single {
        val proxyFacade = get<ProxyFacade>()
        NetworkSettingsController(
            store = get(),
            isRunning = { RuntimeStateMapper.isActuallyRunning(proxyFacade.runtimeSnapshot.value) },
            commandExecutor = get(),
        )
    }
    single {
        val proxyFacade = get<ProxyFacade>()
        AccessControlController(
            store = get(),
            isRunning = { proxyFacade.isRunning.value },
            resolveActiveMode = {
                RuntimeStateMapper.modeForOwner(proxyFacade.runtimeSnapshot.value.owner)
            },
            commandExecutor = get(),
        )
    }
    single {
        val proxyFacade = get<ProxyFacade>()
        val tunProfileSync = get<TunProfileSync>()
        AccessControlCommandExecutor(
            restartProxy = { mode -> proxyFacade.startProxy(mode) },
            beforeRestart = { targetMode ->
                if (targetMode == ProxyMode.Tun) {
                    tunProfileSync.syncActiveProfile()
                }
            },
        )
    }
    single { LogStore(androidApplication(), get()) }
    single { NetworkInfoService() }
    single {
        val appContext = androidContext()
        ProvidersController(appContext) {
            com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.connect(appContext)
            com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.clash().queryProviders()
        }
    }
    single { ProfileBindingStore(androidContext()) }
    single<ProfileBindingProvider> { get<ProfileBindingStore>() }
    single { OverrideConfigStore(androidContext(), get()) }

    single { TunProfileSync(androidContext(), get(), get()) }

    single { ProxyFacade(androidContext(), get(), get()) }
    single { AppIdentityResolver(androidContext()) }
    single<AppIdentityReader> { get<AppIdentityResolver>() }
    single<ProvidersRepository> { get<ProvidersController>() }
    single { ProfilesRepository(androidContext()) }
    single {
        val facade = get<ProxyFacade>()
        AppTrafficStatisticsCollector(
            isRunningFlow = facade.isRunning,
            currentProfileId = { facade.currentProfile.value?.uuid?.toString() },
            trafficStatisticsStore = get(),
            appIdentityResolver = get(),
            trafficTotalFlow = facade.trafficTotal,
            connectionSnapshotFlow = facade.connectionSnapshot,
            queryActiveProfileId = {
                facade.refreshCurrentProfile()
                facade.currentProfile.value?.uuid?.toString()
            },
        )
    }
}

private val appViewModelModule = module {
    viewModel { HomeViewModel(androidApplication(), get(), get(), get(), get()) }
    viewModel { ImportConfigViewModel(androidApplication(), get(), get(), get()) }
    viewModel { VpnSettingsViewModel(get(), get(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get(), get()) }
    viewModel { LogViewModel(get()) }
}

private val appUpdateModule = module {
    single {
        UpdateBuildConfig(
            versionName = BuildConfig.VERSION_NAME,
            updateSource = BuildConfig.UPDATE_SOURCE,
            uiBuildId = BuildConfig.UI_BUILD_ID,
            updateRepository = BuildConfig.UPDATE_REPOSITORY,
            updateMirrorTemplates = BuildConfig.UPDATE_MIRROR_TEMPLATES,
        )
    }
    single { GitHubUpdateManager(androidContext(), get(), get()) }
    viewModel { GitHubUpdateViewModel(get()) }
}

val appModule: List<Module> = listOf(runtimeServiceModule, appFoundationModule, appDataRuntimeModule, appViewModelModule, appUpdateModule)
