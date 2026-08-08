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

package com.github.yumelira.yumebox.feature.settings.di

import com.github.yumelira.yumebox.core.contract.BackupDataSource
import com.github.yumelira.yumebox.core.util.backup.BackupArchiveManager
import com.github.yumelira.yumebox.data.backup.BackupRepository
import com.github.yumelira.yumebox.feature.settings.presentation.backup.BackupRestoreViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.AccessControlViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.MetaFeatureViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.RemoteControllerViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.WifiAutomationViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureSettingsViewModelModule = module {
    viewModel { AppSettingsViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { NetworkSettingsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { WifiAutomationViewModel(androidApplication(), get()) }
    viewModel { RemoteControllerViewModel(androidApplication(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get(), get()) }
    viewModel { MetaFeatureViewModel(get(), get(), get()) }
    viewModel { BackupRestoreViewModel(androidApplication(), get()) }
}

val featureSettingsBackupModule = module {
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
            proxyFacade = get(),
            bulkStoreReset = get(),
            serviceState = get(),
            profileStore = get(),
            subStoreBackupSupport = get(),
            archiveManager = get(),
        )
    }
    single<BackupDataSource> { get<BackupRepository>() }
}

val featureSettingsModules = listOf(featureSettingsViewModelModule, featureSettingsBackupModule)
