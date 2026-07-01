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

package com.github.yumelira.yumebox.di

import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.runtime.service.di.runtimeServiceModule
import com.github.yumelira.yumebox.feature.meta.di.featureMetaModules
import com.github.yumelira.yumebox.feature.override.di.featureOverrideModules
import com.github.yumelira.yumebox.feature.proxy.di.featureProxyModules
import com.github.yumelira.yumebox.feature.substore.di.featureSubStoreModules
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.log.LogViewModel
import com.github.yumelira.yumebox.screen.profiles.ProfilesViewModel
import com.github.yumelira.yumebox.screen.settings.AccessControlViewModel
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import com.github.yumelira.yumebox.screen.settings.MetaFeatureViewModel
import com.github.yumelira.yumebox.screen.settings.NetworkSettingsViewModel
import com.github.yumelira.yumebox.screen.settings.RemoteControllerViewModel
import com.github.yumelira.yumebox.update.GitHubUpdateManager
import com.github.yumelira.yumebox.update.GitHubUpdateViewModel
import com.github.yumelira.yumebox.update.UpdateBuildConfig
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appUpdateModule = module {
    single {
        UpdateBuildConfig(
            versionName = BuildConfig.VERSION_NAME,
            updateSource = BuildConfig.UPDATE_SOURCE,
            uiBuildId = BuildConfig.UI_BUILD_ID,
            updateRepository = BuildConfig.UPDATE_REPOSITORY,
            updateMirrorTemplates = BuildConfig.UPDATE_MIRROR_TEMPLATES,
        )
    }
    single { GitHubUpdateManager(androidContext(), get(), get()) }
    viewModel { GitHubUpdateViewModel(get()) }
}

val appViewModelModule = module {
    viewModel { AppSettingsViewModel(androidApplication(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { ProfilesViewModel(androidApplication(), get(), get(), get(), get()) }
    viewModel { NetworkSettingsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { RemoteControllerViewModel(androidApplication(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get(), get()) }
    viewModel { LogViewModel(get()) }
    viewModel { MetaFeatureViewModel(get(), get()) }
}

val appModule: List<Module> =
    coreDiModules +
        listOf(runtimeServiceModule, appUpdateModule, appViewModelModule) +
        featureSubStoreModules +
        featureProxyModules +
        featureOverrideModules +
        featureMetaModules
