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

package com.github.yumelira.yumebox.feature.substore.di

import com.github.yumelira.yumebox.core.FirstRunInitializer
import com.github.yumelira.yumebox.core.util.AssetDownloader
import com.github.yumelira.yumebox.feature.substore.presentation.viewmodel.FeatureViewModel
import com.github.yumelira.yumebox.feature.substore.presentation.viewmodel.SettingViewModel
import com.github.yumelira.yumebox.feature.substore.service.ExtensionStatusService
import com.github.yumelira.yumebox.feature.substore.util.AppUtil
import com.github.yumelira.yumebox.feature.substore.util.SubStoreDownloadClient
import com.github.yumelira.yumebox.platform.util.APPLICATION_SCOPE_NAME
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureSubStoreViewModelModule = module {
    single { SubStoreDownloadClient(androidApplication(), get()) }
    single<AssetDownloader> { get<SubStoreDownloadClient>() }
    single { ExtensionStatusService(androidApplication()) }
    single<FirstRunInitializer> { AppUtil }
    viewModel { SettingViewModel(get()) }
    viewModel { FeatureViewModel(get(), androidApplication(), get(), get(), get(named(APPLICATION_SCOPE_NAME))) }
}

val featureSubStoreModules: List<Module> = listOf(
    featureSubStoreViewModelModule,
)
