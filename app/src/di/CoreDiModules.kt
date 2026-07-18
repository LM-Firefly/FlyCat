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

package com.github.yumelira.yumebox.di

import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.core.contract.AccessControlControllerContract
import com.github.yumelira.yumebox.core.contract.AppIdentityReader
import com.github.yumelira.yumebox.core.contract.AppLogSettings
import com.github.yumelira.yumebox.core.contract.AppShutdownHandler
import com.github.yumelira.yumebox.core.contract.AppSettingsControllerContract
import com.github.yumelira.yumebox.core.contract.AppSettingsReader
import com.github.yumelira.yumebox.core.contract.BulkStoreReset
import com.github.yumelira.yumebox.core.contract.ConnectionRepository
import com.github.yumelira.yumebox.core.contract.FeatureStoreReader
import com.github.yumelira.yumebox.core.contract.LanguageApplier
import com.github.yumelira.yumebox.core.contract.LogStoreReader
import com.github.yumelira.yumebox.core.contract.NetworkInfoReader
import com.github.yumelira.yumebox.core.contract.NetworkSettingsControllerContract
import com.github.yumelira.yumebox.core.contract.NetworkSettingsReader
import com.github.yumelira.yumebox.core.contract.OverrideApplier
import com.github.yumelira.yumebox.core.contract.OverrideApplyExecutor
import com.github.yumelira.yumebox.core.contract.OverrideConfigRepository
import com.github.yumelira.yumebox.core.contract.ProfileBindingReader
import com.github.yumelira.yumebox.core.contract.ProvidersRepository
import com.github.yumelira.yumebox.core.contract.ProxyDisplaySettingsReader
import com.github.yumelira.yumebox.core.contract.ProxyGroupRepository
import com.github.yumelira.yumebox.core.contract.RemoteControllerStoreReader
import com.github.yumelira.yumebox.core.contract.ServiceBootstrapReader
import com.github.yumelira.yumebox.core.contract.StoreSynchronizer
import com.github.yumelira.yumebox.core.contract.SubStoreSettings
import com.github.yumelira.yumebox.core.contract.TrafficStatisticsRepository
import com.github.yumelira.yumebox.core.contract.UpdateSettings
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.APPLICATION_SCOPE_NAME
import com.github.yumelira.yumebox.data.collector.AppTrafficStatisticsCollector
import com.github.yumelira.yumebox.data.controller.AccessControlCommandExecutor
import com.github.yumelira.yumebox.data.controller.AccessControlController
import com.github.yumelira.yumebox.data.controller.AppSettingsController
import com.github.yumelira.yumebox.data.controller.NetworkSettingsCommandExecutor
import com.github.yumelira.yumebox.data.controller.NetworkSettingsController
import com.github.yumelira.yumebox.data.executor.ActiveProfileOverrideApplier
import com.github.yumelira.yumebox.data.executor.OverrideApplicator
import com.github.yumelira.yumebox.data.gateway.NetworkInfoService
import com.github.yumelira.yumebox.data.logging.AppLogBuffer
import com.github.yumelira.yumebox.data.repository.AppIdentityResolver
import com.github.yumelira.yumebox.data.repository.OverrideBindingRepository
import com.github.yumelira.yumebox.data.repository.ProvidersController
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.AppStateManager
import com.github.yumelira.yumebox.data.store.BuiltInOverrideFileStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.LogStore
import com.github.yumelira.yumebox.data.store.MetadataIndexStore
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import com.github.yumelira.yumebox.data.store.ProfileBindingProvider
import com.github.yumelira.yumebox.data.store.ProfileBindingStore
import com.github.yumelira.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.data.store.TrafficStatisticsStore
import com.github.yumelira.yumebox.data.store.room.createTrafficStatisticsDao
import com.github.yumelira.yumebox.runtime.api.autostart.AutoStartExecutionGate
import com.github.yumelira.yumebox.runtime.api.contract.ProfileRepositoryContract
import com.github.yumelira.yumebox.runtime.api.contract.ProxyControlContract
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import com.github.yumelira.yumebox.runtime.client.root.RootTunReloadScheduler
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// ─────────────────────────────────────────────────────────────────────────────
// ServiceBootstrapReaderImpl (merged from ServiceBootstrapReaderImpl.kt)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * [ServiceBootstrapReader] implementation backed by concrete data stores.
 *
 * Created once in [App] and registered in [ServiceBootstrapHolder] so that Android framework-instantiated Services can read settings without directly depending on data-store implementations.
 */
class ServiceBootstrapReaderImpl(
    private val appSettingsStore: AppSettingsStore,
    private val featureStore: FeatureStore,
    private val networkSettingsStore: NetworkSettingsStore,
    mmkvProvider: MMKVProvider,
) : ServiceBootstrapReader {
    private val serviceCache = mmkvProvider.getMMKV("service_cache")
    override val automaticRestart: Boolean
        get() = appSettingsStore.automaticRestart.value
    override val autoUpdateCurrentProfileOnStart: Boolean
        get() = appSettingsStore.autoUpdateCurrentProfileOnStart.value
    override val runMode: RunMode
        get() = networkSettingsStore.runMode.value
    override fun isRemoteControllerActive(): Boolean = RemoteControllerStore.isActive()
    override fun consumePostUpdateColdStartPending(): Boolean = featureStore.consumePostUpdateColdStartPending()
    override fun markAutoStartStarted() = AutoStartExecutionGate.markStarted(serviceCache)
    override fun clearAutoStart() = AutoStartExecutionGate.clear(serviceCache)
}

// ─────────────────────────────────────────────────────────────────────────────
// appFoundationModule (merged from FoundationModule.kt)
// ─────────────────────────────────────────────────────────────────────────────

val appFoundationModule = module {
    // ── Infrastructure ────────────────────────────────────────────────────────
    single<CoroutineScope>(named(APPLICATION_SCOPE_NAME)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single { MMKVProvider() }
    single<BulkStoreReset> { get<MMKVProvider>() }
    single<StoreSynchronizer> { get<MMKVProvider>() }
    // ── MMKV Instances ────────────────────────────────────────────────────────
    single<MMKV>(named(MMKVProvider.ID_PROFILES)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_PROFILES) }
    single<MMKV>(named(MMKVProvider.ID_SETTINGS)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_SETTINGS) }
    single<MMKV>(named(MMKVProvider.ID_NETWORK_SETTINGS)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_NETWORK_SETTINGS) }
    single<MMKV>(named(MMKVProvider.ID_SUBSTORE)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_SUBSTORE) }
    single<MMKV>(named(MMKVProvider.ID_PROXY_DISPLAY)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_PROXY_DISPLAY) }
    single<MMKV>(named(MMKVProvider.ID_TRAFFIC_STATISTICS)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_TRAFFIC_STATISTICS) }
    single<MMKV>(named(MMKVProvider.ID_PROFILE_LINKS)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_PROFILE_LINKS) }
    single<MMKV>(named(MMKVProvider.ID_SERVICE_CACHE)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_SERVICE_CACHE) }
    single<MMKV>(named(MMKVProvider.ID_OVERRIDE_BINDINGS)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_OVERRIDE_BINDINGS) }
    single<MMKV>(named("remote_controller")) { get<MMKVProvider>().getMMKV("remote_controller") }
    // ── Store Implementations ─────────────────────────────────────────────────
    single { AppSettingsStore(get<MMKV>(named(MMKVProvider.ID_SETTINGS))) }
    single { NetworkSettingsStore(get(named(MMKVProvider.ID_NETWORK_SETTINGS))) }
    single { RemoteControllerStore(get(named("remote_controller"))) }
    single { FeatureStore(get(named(MMKVProvider.ID_SUBSTORE))) }
    single { ProxyDisplaySettingsStore(get(named(MMKVProvider.ID_PROXY_DISPLAY))) }
    single { createTrafficStatisticsDao(androidApplication()) }
    single { TrafficStatisticsStore(get(named(MMKVProvider.ID_TRAFFIC_STATISTICS)), get()) }
    // ── Contract Bindings (store → core.contract interfaces) ──────────────────
    single<NetworkSettingsReader> { get<NetworkSettingsStore>() }
    single<AppSettingsReader> { get<AppSettingsStore>() }
    single<FeatureStoreReader> { get<FeatureStore>() }
    single<ProxyDisplaySettingsReader> { get<ProxyDisplaySettingsStore>() }
    single<TrafficStatisticsRepository> { get<TrafficStatisticsStore>() }
    single<SubStoreSettings> { get<FeatureStore>() }
    single<RemoteControllerStoreReader> {
        get<RemoteControllerStore>().also {
            ServiceClient.configure(it)
        }
    }
    single<UpdateSettings> { get<AppSettingsStore>() }
    single<NetworkInfoReader> { get<NetworkInfoService>() }
    single<LogStoreReader> { get<LogStore>() }
    single<AppLogSettings> { AppLogBuffer }
    // ServiceStateReader, ProfileStoreReader, LogRecordGateway, RuntimeLogWriter are registered in runtime:service's runtimeServiceModule
    single {
        AppStateManager(
            appSettingsStore = get(),
            networkSettingsStore = get(),
            featureStore = get(),
            proxyDisplaySettingsStore = get(),
            trafficStatisticsStore = get(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// appDataRuntimeModule (merged from RuntimeModule.kt)
// ─────────────────────────────────────────────────────────────────────────────

val appDataRuntimeModule = module {
    // ── Settings Controllers ──────────────────────────────────────────────────
    single { AppSettingsController(get(), languageApplier = LanguageApplier(AppLanguageManager::apply)) }
    single<AppSettingsControllerContract> { get<AppSettingsController>() }
    single {
        NetworkSettingsCommandExecutor(
            store = get(),
            restartProxy = { mode -> get<ProxyFacade>().startProxy(mode) },
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
    single<NetworkSettingsControllerContract> { get<NetworkSettingsController>() }
    single {
        val proxyFacade = get<ProxyFacade>()
        AccessControlCommandExecutor(
            restartProxy = { mode -> proxyFacade.startProxy(mode) },
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
    single<AccessControlControllerContract> { get<AccessControlController>() }
    // ── Data Services ─────────────────────────────────────────────────────────
    single { LogStore(androidApplication(), get()) }
    single { NetworkInfoService() }
    single {
        val appContext = androidContext()
        ProvidersController(
            context = appContext,
            queryProvidersAction = {
                ServiceClient.connect(appContext)
                ServiceClient.clash().queryProviders()
            },
            updateProviderAction = { type, name ->
                ServiceClient.connect(appContext)
                ServiceClient.clash().updateProvider(type, name)
            },
        )
    }
    // ── Override & Profile Binding Stores ─────────────────────────────────────
    single { MetadataIndexStore(androidContext()) }
    single { ProfileBindingStore(androidContext(), get()) }
    single<ProfileBindingProvider> { get<ProfileBindingStore>() }
    single { BuiltInOverrideFileStore(androidContext()) }
    single { OverrideConfigStore(androidContext(), get()) }
    single { OverrideBindingRepository(get(), get()) }
    single {
        val appContext = androidContext()
        OverrideApplicator(get()) {
            appContext.sendBroadcast(
                android.content.Intent(
                    Intents.actionOverrideChanged(appContext.packageName)
                ).setPackage(appContext.packageName)
            )
            RootTunReloadScheduler.schedule(
                appContext,
                RootTunReloadScheduler.Reason.PROFILE_OVERRIDE_CHANGED,
            )
        }
    }
    single {
        val profilesRepository = get<ProfilesRepository>()
        ActiveProfileOverrideApplier(
            queryActiveProfile = { profilesRepository.queryActiveProfile() },
            bindingProvider = get(),
            overrideApplicator = get(),
        )
    }
    // Bind controller reader interfaces
    single<OverrideApplier> { get<ActiveProfileOverrideApplier>() }
    single<OverrideApplyExecutor> { get<OverrideApplicator>() }
    single<OverrideConfigRepository> { get<OverrideConfigStore>() }
    single<ProfileBindingReader> { get<OverrideBindingRepository>() }
    single<ProvidersRepository> { get<ProvidersController>() }
    single<AppIdentityReader> { get<AppIdentityResolver>() }
    single { ProxyFacade(androidContext(), get(), get()) }
    single<ProxyGroupRepository> { get<ProxyFacade>() }
    single<ConnectionRepository> { get<ProxyFacade>() }
    single<ProxyControlContract> { get<ProxyFacade>() }
    single { AppIdentityResolver(androidContext()) }
    single { ProfilesRepository(androidContext()) }
    single<ProfileRepositoryContract> { get<ProfilesRepository>() }
    single {
        val facade = get<ProxyFacade>()
        AppTrafficStatisticsCollector(
            isRunningFlow = facade.isRunning,
            currentProfileId = { facade.currentProfile.value?.uuid?.toString() },
            trafficStatisticsStore = get(),
            appIdentityResolver = get(),
            trafficTotalFlow = facade.trafficTotal,
            connectionJoinFlow = facade.connectionJoinEvents,
            connectionCloseFlow = facade.connectionCloseEvents,
            queryActiveProfileId = {
                facade.refreshCurrentProfile()
                facade.currentProfile.value?.uuid?.toString()
            },
        )
    }
    // ── Shutdown Handlers ─────────────────────────────────────────────────────
    single<AppShutdownHandler>(qualifier = named("proxy_facade_shutdown")) {
        AppShutdownHandler { get<ProxyFacade>().shutdown() }
    }
    single<AppShutdownHandler>(qualifier = named("identity_resolver_shutdown")) {
        AppShutdownHandler { get<AppIdentityResolver>().close() }
    }
    single<AppShutdownHandler>(qualifier = named("network_info_shutdown")) {
        AppShutdownHandler { get<NetworkInfoService>().close() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Combined module list
// ─────────────────────────────────────────────────────────────────────────────

val coreDiModules: List<Module> = listOf(appFoundationModule, appDataRuntimeModule)
