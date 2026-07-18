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

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.contract.OverrideConfigRepository
import com.github.yumelira.yumebox.core.util.PendingImportUrlHolder
import com.github.yumelira.yumebox.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.yumelira.yumebox.runtime.api.contract.ProxyControlContract
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun ProfilesPager(mainInnerPadding: PaddingValues) {
    val profilesViewModel = koinViewModel<ProfilesViewModel>()
    val proxyFacade = koinInject<ProxyControlContract>()
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val isRunning by proxyFacade.isRunning.collectAsStateWithLifecycle()

    val overrideConfigRepository = koinInject<OverrideConfigRepository>()
    val userConfigs by overrideConfigRepository.getUserConfigsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val dialogs = rememberProfilesDialogState()
    val scope = rememberCoroutineScope()
    val pendingImportUrl by PendingImportUrlHolder.pendingImportUrl.collectAsStateWithLifecycle()

    LaunchedEffect(pendingImportUrl) {
        if (pendingImportUrl != null) {
            dialogs.importUrl = pendingImportUrl
            dialogs.profileToEdit = null
            dialogs.showAdd.value = true
            PendingImportUrlHolder.clear()
        }
    }

    ProfilesPageHost(
        mainInnerPadding = mainInnerPadding,
        profiles = profiles,
        state = dialogs,
        profilesViewModel = profilesViewModel,
        proxyFacade = proxyFacade,
        isRunning = isRunning,
        onUpdateAll = {
            if (!dialogs.isDownloading) {
                dialogs.isDownloading = true
                scope.launch {
                    profilesViewModel.updateAllUrlProfiles()
                    dialogs.isDownloading = false
                }
            }
        },
    )

    ProfilesDialogHost(
        state = dialogs,
        profilesViewModel = profilesViewModel,
        proxyFacade = proxyFacade,
        userConfigs = userConfigs,
        isRunning = isRunning,
    )
}
