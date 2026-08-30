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

package com.github.lmfirefly.flycat.feature.profiles.domain

import com.github.lmfirefly.flycat.core.model.FetchStatus
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.util.coroutine.safeRunSilent
import com.github.lmfirefly.flycat.runtime.api.contract.ProfileRepositoryContract
import com.github.lmfirefly.flycat.runtime.api.remote.IFetchObserver
import timber.log.Timber
import java.util.UUID

/**
 * Encapsulates profile CRUD and update business logic extracted from ProfilesViewModel.
 */
class ProfileCrudUseCase(
    private val profilesRepository: ProfileRepositoryContract,
) {
    /** Query all profiles from the repository. */
    suspend fun queryAllProfiles(): List<Profile> = profilesRepository.queryAllProfiles()

    /**
     * Create a new profile, optionally updating it with a fetch observer.
     * On failure, rolls back by deleting the created profile.
     *
     * @return The UUID of the created profile, or null if creation failed.
     */
    suspend fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        ageSecretKey: String = "",
        onProgress: ((FetchStatus) -> Unit)? = null,
    ): UUID? {
        var createdUuid: UUID? = null
        return try {
            val uuid = profilesRepository.createProfile(type, name, source, ageSecretKey)
            createdUuid = uuid
            val observer = IFetchObserver { status -> onProgress?.invoke(status) }
            profilesRepository.updateProfile(uuid, observer)
            uuid
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.e(error, "Failed to create profile")
            createdUuid?.let { uuid ->
                safeRunSilent("ProfileCrud", "Rollback profile creation: $uuid") {
                    profilesRepository.deleteProfile(uuid)
                }
            }
            null
        }
    }

    /** Clone an existing profile. Returns the new UUID or null on failure. */
    suspend fun cloneProfile(uuid: UUID): UUID? = try {
        profilesRepository.cloneProfile(uuid)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to clone profile")
        null
    }

    /** Delete a profile by UUID. Returns true on success. */
    suspend fun deleteProfile(uuid: UUID): Boolean = try {
        profilesRepository.deleteProfile(uuid)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to delete profile")
        false
    }

    /** Activate a profile by UUID. Returns true on success. */
    suspend fun activateProfile(uuid: UUID): Boolean = try {
        profilesRepository.setActiveProfile(uuid)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to activate profile")
        false
    }

    /**
     * Update a single profile with download progress reporting.
     * Returns true on success.
     */
    suspend fun updateProfile(
        uuid: UUID,
        onProgress: ((FetchStatus) -> Unit)? = null,
    ): Boolean = try {
        val observer = IFetchObserver { status -> onProgress?.invoke(status) }
        profilesRepository.updateProfile(uuid, observer)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to update profile")
        false
    }

    /**
     * Update all URL-type profiles with progress reporting.
     * Returns the number of profiles updated.
     */
    suspend fun updateAllUrlProfiles(
        profiles: List<Profile>,
        onProgress: ((FetchStatus) -> Unit)? = null,
    ): Int {
        var count = 0
        for (profile in profiles.filter { it.type == Profile.Type.Url }) {
            val observer = IFetchObserver { status -> onProgress?.invoke(status) }
            try {
                profilesRepository.updateProfile(profile.uuid, observer)
                count++
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Timber.e(error, "Failed to update profile ${profile.uuid}")
            }
        }
        return count
    }

    /** Patch profile properties. Returns true on success. */
    suspend fun patchProfile(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String? = null,
    ): Boolean = try {
        profilesRepository.patchProfile(uuid, name, source, interval, ageSecretKey)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to patch profile")
        false
    }

    /** Reorder profiles by UUID list. */
    suspend fun reorderProfiles(orderedUuids: List<UUID>) {
        profilesRepository.reorderProfiles(orderedUuids)
    }

    /** Toggle a profile's enabled state. Returns the new active state or null on failure. */
    suspend fun toggleProfileEnabled(uuid: UUID): Boolean? = try {
        val profile = profilesRepository.queryProfileByUUID(uuid) ?: error("Profile not found: $uuid")
        if (profile.active) {
            profilesRepository.clearActiveProfile(profile)
            false
        } else {
            profilesRepository.setActiveProfile(uuid)
            true
        }
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to toggle profile")
        null
    }

}
