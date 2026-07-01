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

package com.github.yumelira.yumebox.runtime.client

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.data.RepositoryUtils.safeApiCall
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.api.service.remote.IFetchObserver
import com.github.yumelira.yumebox.runtime.client.remote.ServiceClient
import com.github.yumelira.yumebox.runtime.client.root.RootTunReloadScheduler
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

/**
 * Repository for managing Profile CRUD operations.
 *
 * Communicates with ServiceClient (IPC to VPN service) to perform profile operations. All methods
 * return Result<T> for consistent error handling.
 */
class ProfilesRepository(private val context: Context) {
    private val appContext = context.appContextOrSelf

    suspend fun createProfile(type: Profile.Type, name: String, source: String = "", ageSecretKey: String = ""): UUID =
        safeApiCall(TAG, "createProfile") {
                Timber.d("Creating profile: type=$type, name=$name")
                profileService().create(type, name, source, ageSecretKey)
            }
            .getOrThrow()

    suspend fun cloneProfile(uuid: UUID): UUID =
        safeApiCall(TAG, "cloneProfile") {
                Timber.d("Cloning profile: uuid=$uuid")
                profileService().clone(uuid)
            }
            .getOrThrow()

    suspend fun deleteProfile(uuid: UUID) {
        safeApiCall(TAG, "deleteProfile") {
                Timber.d("Deleting profile: uuid=$uuid")
                profileService().delete(uuid)
            }
            .getOrThrow()
    }

    suspend fun queryAllProfiles(): List<Profile> =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryAllProfiles") {
                    profileService().queryAll()
                }
                .getOrThrow()
        }

    suspend fun queryActiveProfile(): Profile? =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryActiveProfile") {
                    profileService().queryActive()
                }
                .getOrThrow()
        }

    suspend fun queryProfileByUUID(uuid: UUID): Profile? =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryProfileByUUID") {
                    profileService().queryByUUID(uuid)
                }
                .getOrThrow()
        }

    suspend fun setActiveProfile(uuid: UUID) {
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

    suspend fun clearActiveProfile(profile: Profile) {
        safeApiCall(TAG, "clearActiveProfile") {
                Timber.d("Clearing active profile: uuid=${profile.uuid}")
                profileService().clearActive(profile)
                notifyRuntimeOverrideChanged()
            }
            .getOrThrow()
    }

    suspend fun reorderProfiles(uuids: List<UUID>) {
        safeApiCall(TAG, "reorderProfiles") {
                Timber.d("Reordering profiles: count=${uuids.size}")
                profileService().reorder(uuids)
            }
            .getOrThrow()
    }

    suspend fun updateProfile(uuid: UUID, callback: IFetchObserver? = null) {
        safeApiCall(TAG, "updateProfile") {
                Timber.d("Updating profile: uuid=$uuid")
                profileService().update(uuid, callback)
            }
            .getOrThrow()
    }

    suspend fun patchProfile(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String? = null) {
        safeApiCall(TAG, "patchProfile") {
                Timber.d("Patching profile: uuid=$uuid")
                profileService().patch(uuid, name, source, interval, ageSecretKey)
            }
            .getOrThrow()
    }

    private suspend fun profileService(): com.github.yumelira.yumebox.runtime.api.service.remote.IProfileManager {
        ServiceClient.connect(context)
        return ServiceClient.profile()
    }

    private fun isRootTunActive(): Boolean {
        val status = RootTunStatusFlow.current(appContext)
        return status.state.isActiveOrStopping || status.runtimeReady
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
