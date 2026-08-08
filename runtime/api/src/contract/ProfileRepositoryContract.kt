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

package com.github.yumelira.yumebox.runtime.api.contract

import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.runtime.api.service.remote.IFetchObserver
import java.util.UUID

/**
 * Contract for profile CRUD operations exposed to feature modules.
 * Implemented by [com.github.yumelira.yumebox.runtime.client.ProfilesRepository].
 */
interface ProfileRepositoryContract {
    suspend fun queryAllProfiles(): List<Profile>
    suspend fun queryActiveProfile(): Profile?
    suspend fun queryProfileByUUID(uuid: UUID): Profile?
    suspend fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        ageSecretKey: String = "",
    ): UUID
    suspend fun updateProfile(uuid: UUID, callback: IFetchObserver? = null)
    suspend fun deleteProfile(uuid: UUID)
    suspend fun cloneProfile(uuid: UUID): UUID
    suspend fun reorderProfiles(orderedUuids: List<UUID>)
    suspend fun setActiveProfile(uuid: UUID)
    suspend fun clearActiveProfile(profile: Profile)
    suspend fun patchProfile(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String? = null,
    )
}
