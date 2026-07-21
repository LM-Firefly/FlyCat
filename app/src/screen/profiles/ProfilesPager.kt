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

package com.github.yumelira.yumebox.screen.profiles

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.github.yumelira.yumebox.MainActivity
import com.github.yumelira.yumebox.presentation.viewmodel.OverrideConfigViewModel
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@SuppressLint("UseKtx")
@Composable
fun ProfilesPager(mainInnerPadding: PaddingValues) {
    val profilesViewModel = koinViewModel<ProfilesViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val profiles by profilesViewModel.profiles.collectAsState()
    val isRunning by homeViewModel.isRunning.collectAsState()

    val overrideConfigViewModel = koinViewModel<OverrideConfigViewModel>()
    val builtInConfigs by overrideConfigViewModel.builtInConfigs.collectAsState()
    val userConfigs by overrideConfigViewModel.userConfigs.collectAsState()
    val dialogs = rememberProfilesDialogState()
    val pendingImportUrl by MainActivity.pendingImportUrl.collectAsState()
    LaunchedEffect(pendingImportUrl) {
        if (pendingImportUrl != null) {
            dialogs.importUrl = pendingImportUrl
            dialogs.profileToEdit = null
            dialogs.showAdd.value = true
            MainActivity.clearPendingImportUrl()
        }
    }

    LaunchedEffect(dialogs.showSettings.value) {
        if (dialogs.showSettings.value) {
            overrideConfigViewModel.refresh()
        }
    }

    ProfilesPageHost(
        mainInnerPadding = mainInnerPadding,
        profiles = profiles,
        state = dialogs,
        profilesViewModel = profilesViewModel,
        homeViewModel = homeViewModel,
        isRunning = isRunning,
    )

    ProfilesDialogHost(
        state = dialogs,
        profilesViewModel = profilesViewModel,
        homeViewModel = homeViewModel,
        builtInConfigs = builtInConfigs,
        userConfigs = userConfigs,
        isRunning = isRunning,
    )
}
