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

package com.github.yumelira.yumebox.runtime.api.service.remote

import com.github.yumelira.yumebox.core.model.Profile
import java.util.UUID

interface IProfileManager {
    suspend fun create(type: Profile.Type, name: String, source: String = "", ageSecretKey: String = ""): UUID

    suspend fun clone(uuid: UUID): UUID

    suspend fun delete(uuid: UUID)

    suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String? = null)

    suspend fun update(uuid: UUID, callback: IFetchObserver? = null)

    suspend fun queryByUUID(uuid: UUID): Profile?

    suspend fun queryAll(): List<Profile>

    suspend fun queryActive(): Profile?

    suspend fun setActive(profile: Profile)

    suspend fun clearActive(profile: Profile)

    suspend fun reorder(uuids: List<UUID>)
}
