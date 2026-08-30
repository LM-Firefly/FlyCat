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

package com.github.lmfirefly.flycat.feature.dashboard.di

import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
import com.github.lmfirefly.flycat.core.contract.CustomRoutingInitializer
import com.github.lmfirefly.flycat.core.contract.OverrideConfigRepository
import com.github.lmfirefly.flycat.feature.dashboard.domain.ConnectionPollingUseCase
import com.github.lmfirefly.flycat.feature.dashboard.domain.CustomRoutingBootstrapper
import com.github.lmfirefly.flycat.feature.dashboard.domain.RuleToggleUseCase
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.ConnectionViewModel
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.CustomRoutingViewModel
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.RulesViewModel
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.TrafficStatisticsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureDashboardDomainModule = module {
    single { ConnectionPollingUseCase(get(), get()) }
    single { RuleToggleUseCase(get()) }
}

val featureDashboardViewModelModule = module {
    viewModel { ConnectionViewModel(get<ConnectionRepository>(), get(), get()) }
    viewModel { TrafficStatisticsViewModel(get()) }
    viewModel { CustomRoutingViewModel(get(), get()) }
    viewModel { RulesViewModel(get()) }
    single<CustomRoutingInitializer> { CustomRoutingBootstrapper(get<OverrideConfigRepository>()) }
}

val featureDashboardModules = listOf(featureDashboardDomainModule, featureDashboardViewModelModule)
