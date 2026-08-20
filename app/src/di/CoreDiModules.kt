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

package com.github.lmfirefly.flycat.di

import com.github.lmfirefly.flycat.common.util.AppLanguageManager
import com.github.lmfirefly.flycat.core.contract.AccessControlControllerContract
import com.github.lmfirefly.flycat.core.contract.AppIdentityReader
import com.github.lmfirefly.flycat.core.contract.AppLogSettings
import com.github.lmfirefly.flycat.core.contract.AppSettingsControllerContract
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.contract.AppShutdownHandler
import com.github.lmfirefly.flycat.core.contract.BroadcastNotifier
import com.github.lmfirefly.flycat.core.contract.BulkStoreReset
import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
import com.github.lmfirefly.flycat.core.contract.FeatureStoreReader
import com.github.lmfirefly.flycat.core.contract.LanguageApplier
import com.github.lmfirefly.flycat.core.contract.LogStoreReader
import com.github.lmfirefly.flycat.core.contract.NetworkInfoReader
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsControllerContract
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.OverrideApplier
import com.github.lmfirefly.flycat.core.contract.OverrideApplyExecutor
import com.github.lmfirefly.flycat.core.contract.OverrideConfigRepository
import com.github.lmfirefly.flycat.core.contract.ProfileBindingReader
import com.github.lmfirefly.flycat.core.contract.ProvidersRepository
import com.github.lmfirefly.flycat.core.contract.ProxyDisplaySettingsReader
import com.github.lmfirefly.flycat.core.contract.ProxyGroupRepository
import com.github.lmfirefly.flycat.core.contract.RemoteControllerStoreReader
import com.github.lmfirefly.flycat.core.contract.RuntimeLifecycleCommand
import com.github.lmfirefly.flycat.core.contract.RuntimeRuleRepository
import com.github.lmfirefly.flycat.core.contract.ServiceBootstrapReader
import com.github.lmfirefly.flycat.core.contract.StoreSynchronizer
import com.github.lmfirefly.flycat.core.contract.SubStoreSettings
import com.github.lmfirefly.flycat.core.contract.TrafficStatisticsRepository
import com.github.lmfirefly.flycat.core.contract.UpdateSettings
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.util.path.APPLICATION_SCOPE_NAME
import com.github.lmfirefly.flycat.data.collector.AppTrafficStatisticsCollector
import com.github.lmfirefly.flycat.data.controller.AccessControlCommandExecutor
import com.github.lmfirefly.flycat.data.controller.AccessControlController
import com.github.lmfirefly.flycat.data.controller.AppSettingsController
import com.github.lmfirefly.flycat.data.controller.NetworkSettingsCommandExecutor
import com.github.lmfirefly.flycat.data.controller.NetworkSettingsController
import com.github.lmfirefly.flycat.data.controller.ProvidersController
import com.github.lmfirefly.flycat.data.datasource.NetworkInfoService
import com.github.lmfirefly.flycat.data.executor.ActiveProfileOverrideApplier
import com.github.lmfirefly.flycat.data.executor.OverrideApplicator
import com.github.lmfirefly.flycat.data.logging.AppLogBuffer
import com.github.lmfirefly.flycat.data.repository.AppIdentityResolver
import com.github.lmfirefly.flycat.data.repository.OverrideBindingRepository
import com.github.lmfirefly.flycat.data.store.AppSettingsStore
import com.github.lmfirefly.flycat.data.store.AppStateManager
import com.github.lmfirefly.flycat.data.store.BuiltInOverrideFileStore
import com.github.lmfirefly.flycat.data.store.FeatureStore
import com.github.lmfirefly.flycat.data.store.LogStore
import com.github.lmfirefly.flycat.data.store.MetadataIndexStore
import com.github.lmfirefly.flycat.data.store.MMKVProvider
import com.github.lmfirefly.flycat.data.store.NetworkSettingsStore
import com.github.lmfirefly.flycat.data.store.OverrideConfigStore
import com.github.lmfirefly.flycat.data.store.ProfileBindingProvider
import com.github.lmfirefly.flycat.data.store.ProfileBindingStore
import com.github.lmfirefly.flycat.data.store.ProxyDisplaySettingsStore
import com.github.lmfirefly.flycat.data.store.RemoteControllerStore
import com.github.lmfirefly.flycat.data.store.TrafficStatisticsStore
import com.github.lmfirefly.flycat.data.store.room.createTrafficStatisticsDao
import com.github.lmfirefly.flycat.runtime.api.constants.Intents.actionOverrideChanged
import com.github.lmfirefly.flycat.runtime.api.constants.Intents.actionProfileChanged
import com.github.lmfirefly.flycat.runtime.api.contract.AutoStartExecutionGate
import com.github.lmfirefly.flycat.runtime.api.contract.ProfileRepositoryContract
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeStateMapper
import com.github.lmfirefly.flycat.runtime.client.ProfilesRepository
import com.github.lmfirefly.flycat.runtime.client.ProxyFacade
import com.github.lmfirefly.flycat.runtime.client.remote.ServiceClient
import com.github.lmfirefly.flycat.runtime.client.root.RootTunReloadScheduler
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
    // ── Context-dependent stores (need androidApplication) ────────────────────
    single { RemoteControllerStore(get(named(MMKVProvider.ID_REMOTE_CONTROLLER))) }
    single<RemoteControllerStoreReader> {
        get<RemoteControllerStore>().also {
            ServiceClient.configure(it)
        }
    }
    single { createTrafficStatisticsDao(androidApplication()) }
    single { LogStore(androidApplication(), get()) }
    single<LogStoreReader> { get<LogStore>() }
    single { NetworkInfoService() }
    single<NetworkInfoReader> { get<NetworkInfoService>() }
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
// appDataModule — data-layer bindings (stores, controllers, repositories)
// ─────────────────────────────────────────────────────────────────────────────

val appDataModule = module {
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
                    actionOverrideChanged(appContext.packageName)
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
    single { AppIdentityResolver(androidContext()) }
}

// ─────────────────────────────────────────────────────────────────────────────
// appRuntimeModule — runtime-layer bindings (proxy facade, profiles, traffic)
// ─────────────────────────────────────────────────────────────────────────────

val appRuntimeModule = module {
    // ── Proxy Runtime ─────────────────────────────────────────────────────────
    single { ProxyFacade(androidContext(), get(), get()) }
    single<ProxyGroupRepository> { get<ProxyFacade>() }
    single<ConnectionRepository> { get<ProxyFacade>() }
    single<RuntimeRuleRepository> { get<ProxyFacade>() }
    single<ProxyControlContract> { get<ProxyFacade>() }
    single<RuntimeLifecycleCommand> {
        val facade = get<ProxyFacade>()
        object : RuntimeLifecycleCommand {
            override suspend fun stopProxy() = facade.stopProxy()
            override suspend fun reconcileRuntimeState() = facade.reconcileRuntimeState()
            override suspend fun applyRemoteControllerState() = facade.applyRemoteControllerState()
        }
    }
    single<BroadcastNotifier> {
        val ctx = androidContext()
        object : BroadcastNotifier {
            override fun notifyProfileChanged() {
                ctx.sendBroadcast(
                    android.content.Intent(
                        actionProfileChanged(ctx.packageName)
                    ).setPackage(ctx.packageName)
                )
            }
            override fun notifyOverrideChanged() {
                ctx.sendBroadcast(
                    android.content.Intent(
                        actionOverrideChanged(ctx.packageName)
                    ).setPackage(ctx.packageName)
                )
            }
        }
    }
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
            connectionJoinFlow = facade.reliableConnectionJoinEvents,
            connectionCloseFlow = facade.reliableConnectionCloseEvents,
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

val coreDiModules: List<Module> = listOf(appFoundationModule, appDataModule, appRuntimeModule)
