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

package com.github.lmfirefly.flycat.runtime.service.di

import com.github.lmfirefly.flycat.core.contract.LogRecordGateway
import com.github.lmfirefly.flycat.core.contract.ProfileStoreReader
import com.github.lmfirefly.flycat.core.contract.RuntimeLogWriter
import com.github.lmfirefly.flycat.core.contract.ServiceStateReader
import com.github.lmfirefly.flycat.runtime.api.root.EbpfCapabilityProbe
import com.github.lmfirefly.flycat.runtime.api.wifi.WifiAutomationController
import com.github.lmfirefly.flycat.runtime.api.wifi.WifiSsidProvider
import com.github.lmfirefly.flycat.runtime.service.LogRecordServiceGateway
import com.github.lmfirefly.flycat.runtime.service.android.LogRecordService
import com.github.lmfirefly.flycat.runtime.service.android.WifiAutomationService
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.records.ProfileStore
import com.github.lmfirefly.flycat.runtime.service.root.EbpfCapabilityProbeImpl
import com.github.lmfirefly.flycat.runtime.service.wifi.WifiSsidProviderImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module that exposes runtime:service implementations through
 * core-defined interfaces, so that consuming modules only depend on
 * the interface layer and never import runtime:service internals.
 */
val runtimeServiceModule = module {
    single<ServiceStateReader> { ServiceStore() }
    single<ProfileStoreReader> { ProfileStore }
    single<LogRecordGateway> { LogRecordServiceGateway() }
    single<RuntimeLogWriter> { RuntimeLogWriter { line -> LogRecordService.writeLog(line) } }
    single<WifiSsidProvider> { WifiSsidProviderImpl(androidContext()) }
    single<EbpfCapabilityProbe> { EbpfCapabilityProbeImpl() }
    single<WifiAutomationController> {
        val ctx = androidContext()
        object : WifiAutomationController {
            override fun start() = WifiAutomationService.start(ctx)
            override fun stop() = WifiAutomationService.stop(ctx)
        }
    }
}
