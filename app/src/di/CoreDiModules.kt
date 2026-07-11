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
import com.github.yumelira.yumebox.core.data.AppIdentityReader
import com.github.yumelira.yumebox.core.data.AppLogSettings
import com.github.yumelira.yumebox.core.data.AppSettingsReader
import com.github.yumelira.yumebox.core.data.ConnectionRepository
import com.github.yumelira.yumebox.core.data.FeatureStoreReader
import com.github.yumelira.yumebox.core.data.LogStoreReader
import com.github.yumelira.yumebox.core.data.NetworkInfoReader
import com.github.yumelira.yumebox.core.data.NetworkSettingsReader
import com.github.yumelira.yumebox.core.data.OverrideApplier
import com.github.yumelira.yumebox.core.data.OverrideConfigRepository
import com.github.yumelira.yumebox.core.data.ProfileBindingReader
import com.github.yumelira.yumebox.core.data.ProfileStoreReader
import com.github.yumelira.yumebox.core.data.ProvidersRepository
import com.github.yumelira.yumebox.core.data.ProxyDisplaySettingsReader
import com.github.yumelira.yumebox.core.data.ProxyGroupRepository
import com.github.yumelira.yumebox.core.data.RemoteControllerStoreReader
import com.github.yumelira.yumebox.core.data.ServiceStateReader
import com.github.yumelira.yumebox.core.data.SubStoreSettings
import com.github.yumelira.yumebox.core.data.TrafficStatisticsRepository
import com.github.yumelira.yumebox.core.data.UpdateSettings
import com.github.yumelira.yumebox.data.controller.AccessControlCommandExecutor
import com.github.yumelira.yumebox.data.controller.AccessControlController
import com.github.yumelira.yumebox.data.controller.ActiveProfileOverrideApplier
import com.github.yumelira.yumebox.data.controller.AppIdentityResolver
import com.github.yumelira.yumebox.data.controller.AppSettingsController
import com.github.yumelira.yumebox.data.controller.AppTrafficStatisticsCollector
import com.github.yumelira.yumebox.data.controller.NetworkSettingsCommandExecutor
import com.github.yumelira.yumebox.data.controller.NetworkSettingsController
import com.github.yumelira.yumebox.data.controller.OverrideApplicator
import com.github.yumelira.yumebox.data.controller.OverrideBindingRepository
import com.github.yumelira.yumebox.data.controller.ProvidersController
import com.github.yumelira.yumebox.data.gateway.NetworkInfoService
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
import com.github.yumelira.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.data.store.TrafficStatisticsStore
import com.github.yumelira.yumebox.data.store.room.createTrafficStatisticsDao
import com.github.yumelira.yumebox.platform.util.APPLICATION_SCOPE_NAME
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import com.github.yumelira.yumebox.runtime.client.root.RootTunReloadScheduler
import com.github.yumelira.yumebox.runtime.service.runtime.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.runtime.records.ProfileStore
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appFoundationModule = module {
    single<CoroutineScope>(named(APPLICATION_SCOPE_NAME)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single { MMKVProvider() }
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

    single { AppSettingsStore(get<MMKV>(named(MMKVProvider.ID_SETTINGS))) }
    single { NetworkSettingsStore(get(named(MMKVProvider.ID_NETWORK_SETTINGS))) }
    single { RemoteControllerStore(get(named("remote_controller"))) }
    single { FeatureStore(get(named(MMKVProvider.ID_SUBSTORE))) }
    single { ProxyDisplaySettingsStore(get(named(MMKVProvider.ID_PROXY_DISPLAY))) }
    single { createTrafficStatisticsDao(androidApplication()) }
    single { TrafficStatisticsStore(get(named(MMKVProvider.ID_TRAFFIC_STATISTICS)), get()) }

    // Bind store reader interfaces for runtime:client consumption
    single<NetworkSettingsReader> { get<NetworkSettingsStore>() }
    single<AppSettingsReader> { get<AppSettingsStore>() }
    single<FeatureStoreReader> { get<FeatureStore>() }
    single<ProxyDisplaySettingsReader> { get<ProxyDisplaySettingsStore>() }
    single<TrafficStatisticsRepository> { get<TrafficStatisticsStore>() }
    single<SubStoreSettings> { get<FeatureStore>() }
    single<RemoteControllerStoreReader> {
        get<RemoteControllerStore>().also {
            com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.configure(it)
        }
    }
    single<UpdateSettings> { get<AppSettingsStore>() }
    single<NetworkInfoReader> { get<NetworkInfoService>() }
    single<LogStoreReader> { get<LogStore>() }
    single<AppLogSettings> { AppLogBuffer }
    single<ServiceStateReader> { ServiceStore() }
    single<ProfileStoreReader> { ProfileStore }

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

val appDataRuntimeModule = module {
    single { AppSettingsController(get(), applyLanguage = AppLanguageManager::apply) }
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
    single { LogStore(androidApplication(), get()) }
    single { NetworkInfoService() }
    single {
        val appContext = androidContext()
        ProvidersController(
            context = appContext,
            queryProvidersAction = {
                com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.connect(appContext)
                com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.clash().queryProviders()
            },
            updateProviderAction = { type, name ->
                com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.connect(appContext)
                com.github.yumelira.yumebox.runtime.client.remote.ServiceClient.clash().updateProvider(type, name)
            },
        )
    }

    single { ProfileBindingStore(androidContext()) }
    single<ProfileBindingProvider> { get<ProfileBindingStore>() }

    single { OverrideConfigStore(androidContext(), get()) }

    single { OverrideBindingRepository(get(), get()) }
    single {
        val appContext = androidContext()
        OverrideApplicator(get()) {
            appContext.sendBroadcast(
                android.content.Intent(
                    com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
                        .actionOverrideChanged(appContext.packageName)
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
    single<OverrideConfigRepository> { get<OverrideConfigStore>() }
    single<ProfileBindingReader> { get<OverrideBindingRepository>() }
    single<ProvidersRepository> { get<ProvidersController>() }
    single<AppIdentityReader> { get<AppIdentityResolver>() }

    single { ProxyFacade(androidContext(), get(), get()) }
    single<ProxyGroupRepository> { get<ProxyFacade>() }
    single<ConnectionRepository> { get<ProxyFacade>() }
    single { AppIdentityResolver(androidContext()) }
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

val coreDiModules: List<Module> = listOf(appFoundationModule, appDataRuntimeModule)
