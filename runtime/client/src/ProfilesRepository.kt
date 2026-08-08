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

package com.github.yumelira.yumebox.runtime.client

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.contract.RepositoryUtils.safeApiCall
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.runtime.api.contract.ProfileRepositoryContract
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.api.service.remote.IFetchObserver
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import com.github.yumelira.yumebox.runtime.client.root.RootTunReloadScheduler
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Repository for managing Profile CRUD operations.
 *
 * Communicates with ServiceClient (IPC to VPN service) to perform profile operations. All methods
 * return Result<T> for consistent error handling.
 */
class ProfilesRepository(private val context: Context) : ProfileRepositoryContract {
    private val appContext = context.appContextOrSelf

    override suspend fun createProfile(
        type: Profile.Type,
        name: String,
        source: String,
        ageSecretKey: String,
    ): UUID =
        safeApiCall(TAG, "createProfile") {
                Timber.d("Creating profile: type=$type, name=$name")
                profileService().create(type, name, source, ageSecretKey)
            }
            .getOrThrow()

    override suspend fun cloneProfile(uuid: UUID): UUID =
        safeApiCall(TAG, "cloneProfile") {
                Timber.d("Cloning profile: uuid=$uuid")
                profileService().clone(uuid)
            }
            .getOrThrow()

    override suspend fun deleteProfile(uuid: UUID) {
        safeApiCall(TAG, "deleteProfile") {
                Timber.d("Deleting profile: uuid=$uuid")
                profileService().delete(uuid)
            }
            .getOrThrow()
    }

    override suspend fun queryAllProfiles(): List<Profile> =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryAllProfiles") {
                    profileService().queryAll()
                }
                .getOrThrow()
        }

    override suspend fun queryActiveProfile(): Profile? =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryActiveProfile") {
                    profileService().queryActive()
                }
                .getOrThrow()
        }

    override suspend fun queryProfileByUUID(uuid: UUID): Profile? =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryProfileByUUID") {
                    profileService().queryByUUID(uuid)
                }
                .getOrThrow()
        }

    override suspend fun setActiveProfile(uuid: UUID) {
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "setActiveProfile") {
                    val startedAt = System.currentTimeMillis()
                    Timber.d("Setting active profile: uuid=$uuid")
                    val profileManager = profileService()

                    val profile =
                        profileManager.queryByUUID(uuid)
                            ?: throw IllegalArgumentException("Profile not found: $uuid")

                    profileManager.setActive(profile)

                    notifyRuntimeOverrideChanged()

                    if (isRootTunActive()) {
                        RootTunReloadScheduler.schedule(
                            appContext,
                            RootTunReloadScheduler.Reason.PROFILE_CHANGED,
                        )
                    }

                    Timber.d(
                        "Active profile applied: uuid=$uuid cost=${System.currentTimeMillis() - startedAt}ms"
                    )
                }
                .getOrThrow()
        }
    }

    override suspend fun clearActiveProfile(profile: Profile) {
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "clearActiveProfile") {
                Timber.d("Clearing active profile: uuid=${profile.uuid}")
                profileService().clearActive(profile)
                notifyRuntimeOverrideChanged()
            }
            .getOrThrow()
        }
    }

    override suspend fun reorderProfiles(orderedUuids: List<UUID>) {
        safeApiCall(TAG, "reorderProfiles") {
                Timber.d("Reordering profiles: count=${orderedUuids.size}")
                profileService().reorder(orderedUuids)
            }
            .getOrThrow()
    }

    override suspend fun updateProfile(uuid: UUID, callback: IFetchObserver?) {
        safeApiCall(TAG, "updateProfile") {
                Timber.d("Updating profile: uuid=$uuid")
                profileService().update(uuid, callback)
            }
            .getOrThrow()
    }

    override suspend fun patchProfile(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String?,
    ) {
        safeApiCall(TAG, "patchProfile") {
                Timber.d("Patching profile: uuid=$uuid")
                profileService()
                    .patch(
                        uuid = uuid,
                        name = name,
                        source = source,
                        interval = interval,
                        ageSecretKey = ageSecretKey,
                    )
            }
            .getOrThrow()
    }

    private fun isRootTunActive(): Boolean {
        val status = RootTunStatusFlow.current(appContext)
        return status.state.isActiveOrStopping || status.runtimeReady
    }

    private suspend fun profileService(): com.github.yumelira.yumebox.runtime.api.service.remote.IProfileManager {
        ServiceClient.connect(context)
        return ServiceClient.profile()
    }

    private fun notifyRuntimeOverrideChanged() {
        appContext.sendBroadcast(
            Intent(Intents.actionOverrideChanged(appContext.packageName))
                .setPackage(appContext.packageName)
        )
    }

    companion object {
        private const val TAG = "ProfilesRepository"
    }
}
