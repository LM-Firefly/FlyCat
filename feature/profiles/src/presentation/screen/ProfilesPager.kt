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

package com.github.lmfirefly.flycat.feature.profiles.presentation.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.util.PendingImportUrlHolder
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfilesPager(mainInnerPadding: PaddingValues) {
    val profilesViewModel = koinViewModel<ProfilesViewModel>()
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val isRunning by profilesViewModel.isRunning.collectAsStateWithLifecycle()
    val userConfigs by profilesViewModel.userConfigs.collectAsStateWithLifecycle()
    val builtInConfigs by profilesViewModel.builtInConfigs.collectAsStateWithLifecycle()

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
        builtInConfigs = builtInConfigs,
        userConfigs = userConfigs,
        isRunning = isRunning,
    )
}
