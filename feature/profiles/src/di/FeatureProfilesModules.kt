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

package com.github.lmfirefly.flycat.feature.profiles.di

import com.github.lmfirefly.flycat.feature.profiles.domain.ProfileCrudUseCase
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureProfilesDomainModule = module {
    single { ProfileCrudUseCase(get()) }
}

val featureProfilesViewModelModule = module {
    viewModel { ProfilesViewModel(androidApplication(), get(), get(), get(), get(), get(), get()) }
}

val featureProfilesModules = listOf(featureProfilesDomainModule, featureProfilesViewModelModule)
