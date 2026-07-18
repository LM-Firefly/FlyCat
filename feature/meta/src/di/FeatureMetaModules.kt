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

package com.github.yumelira.yumebox.feature.meta.di

import com.github.yumelira.yumebox.core.contract.ConnectionRepository
import com.github.yumelira.yumebox.core.contract.OverrideConfigRepository
import com.github.yumelira.yumebox.core.domain.CustomRoutingInitializer
import com.github.yumelira.yumebox.feature.meta.presentation.util.CustomRoutingBootstrapper
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.ConnectionViewModel
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.CustomRoutingViewModel
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.TrafficStatisticsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureMetaViewModelModule = module {
    viewModel { ConnectionViewModel(get<ConnectionRepository>(), get()) }
    viewModel { TrafficStatisticsViewModel(get()) }
    viewModel { CustomRoutingViewModel(get(), get()) }
    single<CustomRoutingInitializer> { CustomRoutingBootstrapper(get<OverrideConfigRepository>()) }
}

val featureMetaModules = listOf(featureMetaViewModelModule)
