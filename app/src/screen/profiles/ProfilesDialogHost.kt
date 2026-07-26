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

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.screen.profiles


import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.data.controller.OverrideService
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.ProfileBinding
import com.github.yumelira.yumebox.data.store.ProfileBindingProvider
import com.github.yumelira.yumebox.feature.meta.presentation.util.CustomRoutingBootstrapper
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.client.ProfilePatch
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
internal fun ProfilesDialogHost(
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
    homeViewModel: HomeViewModel,
    builtInConfigs: List<OverrideConfig>,
    userConfigs: List<OverrideConfig>,
    refreshOverrides: suspend () -> Unit,
    isRunning: Boolean,
) {
    val scope = rememberCoroutineScope()
    val bindingProvider: ProfileBindingProvider = koinInject()
    val overrideService: OverrideService = koinInject()
    val routingBootstrapper: CustomRoutingBootstrapper = koinInject()

    AddProfileSheet(
        show = state.showAdd,
        profileToEdit = state.profileToEdit,
        importUrl = state.importUrl ?: state.scannedUrl,
        onAddProfile = { name, source, type, interval, fileUri, ageSecretKey ->
            profilesViewModel.createProfile(type, name, source, interval, fileUri, ageSecretKey)
        },
        onUpdateProfile = { uuid, name, source, interval ->
            profilesViewModel.patchProfile(
                uuid,
                ProfilePatch(name = name, source = source, interval = interval),
            )
        },
        onDownloadComplete = {
            state.isDownloading = false
            state.showAdd.value = false
            profilesViewModel.clearDownloadProgress()
        },
        profilesViewModel = profilesViewModel,
    )

    ProfileDeleteDialog(state, profilesViewModel)
    ProfileSettingsDialogHost(
        state = state,
        profilesViewModel = profilesViewModel,
        homeViewModel = homeViewModel,
        builtInConfigs = builtInConfigs,
        userConfigs = userConfigs,
        bindingProvider = bindingProvider,
        overrideService = overrideService,
        routingBootstrapper = routingBootstrapper,
        isRunning = isRunning,
    )
    ProfileShareDialog(
        profile = state.shareTarget,
        show = state.showShare.value,
        onDismiss = { state.showShare.value = false },
        onDismissFinished = { state.shareTarget = null },
    )
    ProfileEditOptionsDialogHost(
        profile = state.editOptionsTarget,
        show = state.showEditOptions,
        onDismiss = { state.showEditOptions = false },
        onDismissFinished = { state.editOptionsTarget = null },
        onSettingsRequested = { profile ->
            state.profileToEdit = profile
            scope.launch {
                refreshOverrides()
                state.binding = bindingProvider.getBinding(profile.uuid.toString())
                state.showSettings.value = true
            }
        },
    )
}

@Composable
private fun ProfileDeleteDialog(
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
) {
    val profile = state.deleteTarget ?: return
    DeleteConfirmDialog(
        show = state.showDelete,
        profileName = profile.name,
        onConfirm = {
            state.showDelete = false
            profilesViewModel.deleteProfile(profile.uuid)
        },
        onDismiss = { state.showDelete = false },
        onDismissFinished = { state.deleteTarget = null },
    )
}

@Composable
private fun ProfileSettingsDialogHost(
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
    homeViewModel: HomeViewModel,
    builtInConfigs: List<OverrideConfig>,
    userConfigs: List<OverrideConfig>,
    bindingProvider: ProfileBindingProvider,
    overrideService: OverrideService,
    routingBootstrapper: CustomRoutingBootstrapper,
    isRunning: Boolean,
) {
    val profile = state.profileToEdit ?: return
    ProfileSettingsDialog(
        show = state.showSettings.value,
        profile = profile,
        builtInConfigs = builtInConfigs,
        userConfigs = userConfigs,
        binding = state.binding,
        onDismiss = { state.showSettings.value = false },
        onDismissFinished = {
            state.profileToEdit = null
            state.binding = null
        },
        onSaveProfileMeta = { update ->
            if (update.name.isNotBlank() && update.source.isNotBlank()) {
                profilesViewModel.patchProfile(
                    uuid = profile.uuid,
                    patch =
                        ProfilePatch(
                            name = update.name,
                            source = update.source,
                            interval = profile.interval,
                            updateAgeSecretKey = update.updateAgeSecretKey,
                            ageSecretKey = update.ageSecretKey,
                        ),
                )
            }
        },
        onSaveOverrideSettings = { ids ->
            state.binding =
                saveProfileOverrides(
                    profile = profile,
                    selectedIds = ids,
                    binding = state.binding,
                    bindingProvider = bindingProvider,
                    routingBootstrapper = routingBootstrapper,
                    overrideService = overrideService,
                    homeViewModel = homeViewModel,
                    isRunning = isRunning,
                )
        },
    )
}

private suspend fun saveProfileOverrides(
    profile: Profile,
    selectedIds: List<String>,
    binding: ProfileBinding?,
    bindingProvider: ProfileBindingProvider,
    routingBootstrapper: CustomRoutingBootstrapper,
    overrideService: OverrideService,
    homeViewModel: HomeViewModel,
    isRunning: Boolean,
): ProfileBinding? {
    val profileId = profile.uuid.toString()
    val overrideIds = selectedIds.distinct()
    val previousBinding = bindingProvider.getBinding(profileId)
    val updatedBinding =
        previousBinding?.copy(overrideIds = overrideIds)
            ?: binding?.copy(overrideIds = overrideIds)
            ?: ProfileBinding(profileId = profileId, overrideIds = overrideIds)

    if (OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID in overrideIds) {
        routingBootstrapper.ensureDefaultContent()
    }

    return try {
        bindingProvider.setBinding(updatedBinding)
        val shouldApplyRuntime =
            (isRunning || profile.active) &&
                (profile.active || homeViewModel.isCurrentProfile(profile.uuid))
        if (shouldApplyRuntime) {
            check(overrideService.applyOverride(profileId)) {
                "覆写配置未能应用到当前代理"
            }
        }
        bindingProvider.getBinding(profileId) ?: updatedBinding
    } catch (error: Throwable) {
        withContext(NonCancellable) {
            if (previousBinding == null) {
                bindingProvider.removeBinding(profileId)
            } else {
                bindingProvider.setBinding(previousBinding)
            }
        }
        throw error
    }
}
