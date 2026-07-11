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

package com.github.yumelira.yumebox.feature.settings.di

import com.github.yumelira.yumebox.feature.settings.presentation.backup.BackupArchiveManager
import com.github.yumelira.yumebox.feature.settings.presentation.backup.BackupRepository
import com.github.yumelira.yumebox.feature.settings.presentation.backup.BackupRestoreViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.AccessControlViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.MetaFeatureViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.RemoteControllerViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureSettingsViewModelModule = module {
    viewModel { AppSettingsViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { NetworkSettingsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { RemoteControllerViewModel(androidApplication(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get(), get()) }
    viewModel { MetaFeatureViewModel(get(), get(), get()) }
    viewModel { BackupRestoreViewModel(androidApplication(), get()) }
}

val featureSettingsModules = listOf(featureSettingsViewModelModule)
