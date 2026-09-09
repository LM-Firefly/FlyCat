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

package com.github.lmfirefly.flycat.feature.settings.di

import com.github.lmfirefly.flycat.feature.settings.domain.InstalledAppsUseCase
import com.github.lmfirefly.flycat.feature.settings.presentation.backup.BackupRestoreViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.util.ChinaAppDetector
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AccessControlViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.MetaFeatureViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.RemoteControllerViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.WifiAutomationViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureSettingsDomainModule = module {
    single { InstalledAppsUseCase(androidApplication(), get()) }
    single { ChinaAppDetector(androidApplication(), get(named("chinaAppCache"))) }
}

val featureSettingsViewModelModule = module {
    viewModel { AppSettingsViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { NetworkSettingsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { WifiAutomationViewModel(androidApplication(), get(), get(), get()) }
    viewModel { RemoteControllerViewModel(androidApplication(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { MetaFeatureViewModel(get(), get(), get()) }
    viewModel { BackupRestoreViewModel(androidApplication(), get()) }
}

val featureSettingsModules = listOf(featureSettingsDomainModule, featureSettingsViewModelModule)
