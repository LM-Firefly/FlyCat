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

package com.github.lmfirefly.flycat.feature.home.domain

import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.runtime.api.contract.ProfileRepositoryContract
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

/**
 * Encapsulates profile query, switch, and reload business logic
 * extracted from HomeViewModel.
 */
class ProfileManagementUseCase(
    private val profilesRepository: ProfileRepositoryContract,
    private val proxyFacade: ProxyControlContract,
) {
    /** Query all profiles and the currently active profile. */
    suspend fun queryProfiles(): ProfileSnapshot = try {
        val all = profilesRepository.queryAllProfiles()
        val active = profilesRepository.queryActiveProfile()
        ProfileSnapshot(profiles = all, recommendedProfile = active, loaded = true)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to query profiles")
        ProfileSnapshot(loaded = true)
    }

    /** Switch the active profile; if proxy is running, restart with the new profile. */
    suspend fun switchActiveProfile(
        profileId: String,
        isRunning: Boolean,
        runMode: RunMode,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        try {
            val uuid = UUID.fromString(profileId)
            if (proxyFacade.currentProfile.value?.uuid == uuid) return
            withContext(Dispatchers.IO) { profilesRepository.setActiveProfile(uuid) }
            onSuccess()
            if (isRunning) {
                withContext(Dispatchers.IO) {
                    AutoStartSessionGate.clearManualPaused()
                    proxyFacade.startProxy(runMode)
                }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.e(error, "Failed to switch active profile")
            onError(error.message ?: "Unknown error")
        }
    }

    /** Reload the current profile; if proxy is running, restart. */
    suspend fun reloadProfile(
        isRunning: Boolean,
        runMode: RunMode,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        try {
            onSuccess()
            if (isRunning) {
                withContext(Dispatchers.IO) { proxyFacade.startProxy(runMode) }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.e(error, "Failed to reload profile")
            onError(error.message ?: "Unknown error")
        }
    }

    data class ProfileSnapshot(
        val profiles: List<Profile> = emptyList(),
        val recommendedProfile: Profile? = null,
        val loaded: Boolean = false,
    )
}
