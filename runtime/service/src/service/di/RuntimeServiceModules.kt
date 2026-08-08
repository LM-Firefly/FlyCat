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

package com.github.yumelira.yumebox.runtime.service.di

import com.github.yumelira.yumebox.core.contract.LogRecordGateway
import com.github.yumelira.yumebox.core.contract.ProfileStoreReader
import com.github.yumelira.yumebox.core.contract.RuntimeLogWriter
import com.github.yumelira.yumebox.core.contract.ServiceStateReader
import com.github.yumelira.yumebox.runtime.service.LogRecordService
import com.github.yumelira.yumebox.runtime.service.LogRecordServiceGateway
import com.github.yumelira.yumebox.runtime.service.runtime.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.runtime.records.ProfileStore
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
}
