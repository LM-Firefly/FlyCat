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

package com.github.lmfirefly.flycat.data.di

import com.github.lmfirefly.flycat.core.contract.AppLogSettings
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.contract.BackupDataSource
import com.github.lmfirefly.flycat.core.contract.FeatureStoreReader
import com.github.lmfirefly.flycat.core.contract.KeyValueCache
import com.github.lmfirefly.flycat.core.contract.NetworkInfoReader
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.contract.ProxyDisplaySettingsReader
import com.github.lmfirefly.flycat.core.contract.SubStoreSettings
import com.github.lmfirefly.flycat.core.contract.TrafficStatisticsRepository
import com.github.lmfirefly.flycat.core.contract.UpdateSettings
import com.github.lmfirefly.flycat.core.util.backup.BackupArchiveManager
import com.github.lmfirefly.flycat.data.backup.BackupRepository
import com.github.lmfirefly.flycat.data.datasource.NetworkInfoService
import com.github.lmfirefly.flycat.data.logging.AppLogBuffer
import com.github.lmfirefly.flycat.data.store.AppSettingsStore
import com.github.lmfirefly.flycat.data.store.FeatureStore
import com.github.lmfirefly.flycat.data.store.MMKVKeyValueCache
import com.github.lmfirefly.flycat.data.store.MMKVProvider
import com.github.lmfirefly.flycat.data.store.NetworkSettingsStore
import com.github.lmfirefly.flycat.data.store.ProxyDisplaySettingsStore
import com.github.lmfirefly.flycat.data.store.TrafficStatisticsStore
import com.tencent.mmkv.MMKV
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataStoreModule: Module = module {
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
    single<MMKV>(named(MMKVProvider.ID_REMOTE_CONTROLLER)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_REMOTE_CONTROLLER) }
    single<MMKV>(named(MMKVProvider.ID_CHINA_APP_DETECTOR_CACHE)) { get<MMKVProvider>().getMMKV(MMKVProvider.ID_CHINA_APP_DETECTOR_CACHE) }
    // ── Store Implementations ─────────────────────────────────────────────────
    single { AppSettingsStore(get<MMKV>(named(MMKVProvider.ID_SETTINGS))) }
    single { NetworkSettingsStore(get(named(MMKVProvider.ID_NETWORK_SETTINGS))) }
    single { FeatureStore(get(named(MMKVProvider.ID_SUBSTORE))) }
    single { ProxyDisplaySettingsStore(get(named(MMKVProvider.ID_PROXY_DISPLAY))) }
    single { TrafficStatisticsStore(get(named(MMKVProvider.ID_TRAFFIC_STATISTICS)), get()) }
    // ── Contract Bindings (store → core.contract interfaces) ──────────────────
    single<NetworkSettingsReader> { get<NetworkSettingsStore>() }
    single<AppSettingsReader> { get<AppSettingsStore>() }
    single<FeatureStoreReader> { get<FeatureStore>() }
    single<ProxyDisplaySettingsReader> { get<ProxyDisplaySettingsStore>() }
    single<TrafficStatisticsRepository> { get<TrafficStatisticsStore>() }
    single<SubStoreSettings> { get<FeatureStore>() }
    single<UpdateSettings> { get<AppSettingsStore>() }
    single<AppLogSettings> { AppLogBuffer }
    single<KeyValueCache>(named("chinaAppCache")) { MMKVKeyValueCache(get<MMKV>(named(MMKVProvider.ID_CHINA_APP_DETECTOR_CACHE))) }
}

val dataBackupModule: Module = module {
    single { BackupArchiveManager() }
    single {
        BackupRepository(
            application = androidApplication(),
            appSettings = get(),
            networkSettings = get(),
            featureSettings = get(),
            subStoreSettings = get(),
            proxyDisplaySettings = get(),
            remoteController = get(),
            runtimeCommand = get(),
            broadcastNotifier = get(),
            bulkStoreReset = get(),
            serviceState = get(),
            profileStore = get(),
            subStoreBackupSupport = get(),
            archiveManager = get(),
        )
    }
    single<BackupDataSource> { get<BackupRepository>() }
}
