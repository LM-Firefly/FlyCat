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

package com.github.lmfirefly.flycat.feature.substore.di

import com.github.lmfirefly.flycat.core.FirstRunInitializer
import com.github.lmfirefly.flycat.core.contract.SubStoreBackupSupport
import com.github.lmfirefly.flycat.core.contract.SubStoreNavigationHandler
import com.github.lmfirefly.flycat.core.util.AssetDownloader
import com.github.lmfirefly.flycat.core.util.path.APPLICATION_SCOPE_NAME
import com.github.lmfirefly.flycat.feature.substore.SubStoreBackupSupportImpl
import com.github.lmfirefly.flycat.feature.substore.presentation.viewmodel.FeatureViewModel
import com.github.lmfirefly.flycat.feature.substore.presentation.viewmodel.SettingViewModel
import com.github.lmfirefly.flycat.feature.substore.presentation.viewmodel.SubStoreNavigationHandlerImpl
import com.github.lmfirefly.flycat.feature.substore.util.AppUtils
import com.github.lmfirefly.flycat.feature.substore.util.SubStoreDownloadClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureSubStoreViewModelModule = module {
    single { SubStoreDownloadClient(androidApplication(), get()) }
    single<AssetDownloader> { get<SubStoreDownloadClient>() }
    single<FirstRunInitializer> { AppUtils.apply { application = androidApplication() } }
    single<SubStoreBackupSupport> { SubStoreBackupSupportImpl() }
    single { SubStoreNavigationHandlerImpl() }
    single<SubStoreNavigationHandler> { get<SubStoreNavigationHandlerImpl>() }
    viewModel { SettingViewModel(get(), get()) }
    viewModel { FeatureViewModel(get(), androidApplication(), get(), get(named(APPLICATION_SCOPE_NAME))) }
}

val featureSubStoreModules: List<Module> = listOf(
    featureSubStoreViewModelModule,
)
