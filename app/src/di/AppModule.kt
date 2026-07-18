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

package com.github.lmfirefly.flycat.di

import com.github.lmfirefly.flycat.BuildConfig
import com.github.lmfirefly.flycat.data.di.dataStoreModule
import com.github.lmfirefly.flycat.feature.about.UpdateBuildConfig
import com.github.lmfirefly.flycat.feature.about.di.featureUpdateModules
import com.github.lmfirefly.flycat.feature.home.di.featureHomeModules
import com.github.lmfirefly.flycat.feature.log.di.featureLogModules
import com.github.lmfirefly.flycat.feature.meta.di.featureMetaModules
import com.github.lmfirefly.flycat.feature.override.di.featureOverrideModules
import com.github.lmfirefly.flycat.feature.profiles.di.featureProfilesModules
import com.github.lmfirefly.flycat.feature.proxy.di.featureProxyModules
import com.github.lmfirefly.flycat.feature.settings.di.featureSettingsModules
import com.github.lmfirefly.flycat.feature.substore.di.featureSubStoreModules
import com.github.lmfirefly.flycat.runtime.service.di.runtimeServiceModule
import org.koin.core.module.Module
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
}

val appModule: List<Module> =
    coreDiModules +
        listOf(dataStoreModule, runtimeServiceModule, appUpdateModule) +
        featureUpdateModules +
        featureHomeModules +
        featureLogModules +
        featureProfilesModules +
        featureSettingsModules +
        featureSubStoreModules +
        featureProxyModules +
        featureOverrideModules +
        featureMetaModules
