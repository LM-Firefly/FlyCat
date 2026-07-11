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

import com.github.yumelira.yumebox.core.data.LogRecordGateway
import com.github.yumelira.yumebox.core.data.RuntimeLogWriter
import com.github.yumelira.yumebox.core.data.ServiceBootstrapReader
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.api.autostart.AutoStartExecutionGate
import com.github.yumelira.yumebox.runtime.service.LogRecordService
import com.github.yumelira.yumebox.runtime.service.LogRecordServiceGateway
import org.koin.dsl.module

/**
 * [ServiceBootstrapReader] implementation backed by concrete data stores.
 *
 * Created once in [App] and registered in [ServiceBootstrapHolder] so that
 * Android framework-instantiated Services can read settings without directly
 * depending on data-store implementations.
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

    override val proxyMode: ProxyMode
        get() = networkSettingsStore.proxyMode.value

    override fun isRemoteControllerActive(): Boolean = RemoteControllerStore.isActive()

    override fun consumePostUpdateColdStartPending(): Boolean =
        featureStore.consumePostUpdateColdStartPending()

    override fun markAutoStartStarted() {
        AutoStartExecutionGate.markStarted(serviceCache)
    }

    override fun clearAutoStart() {
        AutoStartExecutionGate.clear(serviceCache)
    }
}

/**
 * Bridges runtime:service log implementation to data:gateway contracts.
 *
 * Moved from runtime:service to app so that runtime:service does not
 * depend on data.gateway types directly.
 */
val runtimeServiceModule = module {
    single<LogRecordGateway> { LogRecordServiceGateway() }
    single<RuntimeLogWriter> { RuntimeLogWriter { line -> LogRecordService.writeLog(line) } }
}
