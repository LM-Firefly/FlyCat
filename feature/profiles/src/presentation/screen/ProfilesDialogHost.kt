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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.contract.CustomRoutingInitializer
import com.github.lmfirefly.flycat.core.contract.OverrideApplyExecutor
import com.github.lmfirefly.flycat.core.contract.ProfileBindingReader
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.override.OverrideInternalConstants
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import com.github.lmfirefly.flycat.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
internal fun ProfilesDialogHost(
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
    builtInConfigs: List<OverrideConfig>,
    userConfigs: List<OverrideConfig>,
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bindingProvider: ProfileBindingReader = koinInject()
    val OverrideApplyExecutor: OverrideApplyExecutor = koinInject()
    val routingBootstrapper: CustomRoutingInitializer = koinInject()

    AddProfileSheet(
        show = state.showAdd,
        profileToEdit = state.profileToEdit,
        importUrl = state.importUrl ?: state.scannedUrl,
        onAddProfile = { name, source, type, interval, fileUri, ageSecretKey ->
            profilesViewModel.createProfile(type, name, source, interval, fileUri, ageSecretKey)
        },
        onUpdateProfile = { uuid, name, source, interval, ageSecretKey ->
            profilesViewModel.patchProfile(uuid, name, source, interval, ageSecretKey)
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
        builtInConfigs = builtInConfigs,
        userConfigs = userConfigs,
        bindingProvider = bindingProvider,
        OverrideApplyExecutor = OverrideApplyExecutor,
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
                state.binding = bindingProvider.getBinding(profile.uuid.toString())
            }
            state.showSettings.value = true
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
    builtInConfigs: List<OverrideConfig>,
    userConfigs: List<OverrideConfig>,
    bindingProvider: ProfileBindingReader,
    OverrideApplyExecutor: OverrideApplyExecutor,
    routingBootstrapper: CustomRoutingInitializer,
    isRunning: Boolean,
) {
    val profile = state.profileToEdit ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentProfile by profilesViewModel.currentProfile.collectAsStateWithLifecycle()

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
                    name = update.name,
                    source = update.source,
                    interval = profile.interval,
                    ageSecretKey = update.ageSecretKey,
                )
            }
        },
        onSaveOverrideSettings = { ids ->
            scope.launch {
                state.binding =
                    saveProfileOverrides(
                        profile = profile,
                        selectedIds = ids,
                        binding = state.binding,
                        bindingProvider = bindingProvider,
                        routingBootstrapper = routingBootstrapper,
                        OverrideApplyExecutor = OverrideApplyExecutor,
                        currentProfileUuid = currentProfile?.uuid,
                        isRunning = isRunning,
                        context = context,
                    )
            }
        },
    )
}

private suspend fun saveProfileOverrides(
    profile: Profile,
    selectedIds: List<String>,
    binding: ProfileBinding?,
    bindingProvider: ProfileBindingReader,
    routingBootstrapper: CustomRoutingInitializer,
    OverrideApplyExecutor: OverrideApplyExecutor,
    currentProfileUuid: java.util.UUID?,
    isRunning: Boolean,
    context: android.content.Context,
): ProfileBinding? {
    val profileId = profile.uuid.toString()
    val overrideIds = selectedIds.distinct()
    val updatedBinding =
        binding?.copy(overrideIds = overrideIds)
            ?: ProfileBinding(profileId = profileId, overrideIds = overrideIds)

    bindingProvider.setBinding(updatedBinding)
    val refreshedBinding = bindingProvider.getBinding(profileId)

    if (OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID in overrideIds) {
        runCatching { routingBootstrapper.ensureDefaultContent() }
            .onFailure { error ->
                Timber.e(error, "Failed to generate custom routing content for profile %s", profileId)
                context.toast(error.message ?: FlyTxt.ProfilesPage.SettingsDialog.CustomRouting)
            }
    }

    if (isRunning && currentProfileUuid == profile.uuid) {
        OverrideApplyExecutor.applyOverride(profileId)
    }
    return refreshedBinding
}
