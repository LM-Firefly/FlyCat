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

package com.github.yumelira.yumebox.runtime.service

import android.content.Context
import com.github.yumelira.yumebox.core.importedDir
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.runtime.api.service.remote.IFetchObserver
import com.github.yumelira.yumebox.runtime.api.service.remote.IProfileManager
import com.github.yumelira.yumebox.runtime.service.runtime.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.runtime.entity.Imported
import com.github.yumelira.yumebox.runtime.service.runtime.records.ImportedDao
import com.github.yumelira.yumebox.runtime.service.runtime.records.ProfileStore
import com.github.yumelira.yumebox.runtime.service.runtime.util.directoryLastModified
import com.github.yumelira.yumebox.runtime.service.runtime.util.generateProfileUUID
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendProfileChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.UUID

class ProfileManager(private val context: Context) :
    IProfileManager {
    private val store = ServiceStore()

    init {
        context.importedDir.mkdirs()
    }

    override suspend fun create(type: Profile.Type, name: String, source: String, ageSecretKey: String): UUID {
        val uuid = generateProfileUUID()
        val normalizedName = name.trim().ifBlank { "New Profile" }
        val now = System.currentTimeMillis()

        val imported =
            Imported(
                uuid = uuid,
                name = normalizedName,
                type = type,
                source = source,
                interval = 0,
                upload = 0,
                total = 0,
                download = 0,
                expire = 0,
                createdAt = now,
                ageSecretKey = ageSecretKey,
            )

        ImportedDao.insert(imported)

        return uuid
    }

    override suspend fun clone(uuid: UUID): UUID {
        val newUUID = generateProfileUUID()

        val imported =
            ImportedDao.queryByUUID(uuid) ?: throw FileNotFoundException("profile $uuid not found")

        val now = System.currentTimeMillis()
        val newImported =
            Imported(
                uuid = newUUID,
                name = imported.name,
                type = Profile.Type.File,
                source = imported.source,
                interval = imported.interval,
                upload = imported.upload,
                total = imported.total,
                download = imported.download,
                expire = imported.expire,
                createdAt = now,
                ageSecretKey = imported.ageSecretKey,
            )

        val sourceDir = context.importedDir.resolve(uuid.toString())
        val targetDir = context.importedDir.resolve(newUUID.toString())

        if (!sourceDir.exists()) throw FileNotFoundException("profile $uuid not found")

        targetDir.deleteRecursively()
        sourceDir.copyRecursively(targetDir)

        ImportedDao.insert(newImported)

        return newUUID
    }

    override suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String?) {
        val imported =
            ImportedDao.queryByUUID(uuid) ?: throw FileNotFoundException("profile $uuid not found")

        val updated = imported.copy(
            name = name,
            source = source,
            interval = interval,
            ageSecretKey = ageSecretKey ?: imported.ageSecretKey,
        )

        ImportedDao.update(updated)
        context.sendProfileChanged(uuid)
    }

    override suspend fun update(uuid: UUID, callback: IFetchObserver?) {
        ProfileProcessor.update(context, uuid, callback)
    }

    override suspend fun delete(uuid: UUID) {
        ProfileProcessor.delete(context, uuid)
    }

    override suspend fun queryByUUID(uuid: UUID): Profile? {
        return resolveProfile(uuid)
    }

    override suspend fun queryAll(): List<Profile> {
        val uuids = withContext(Dispatchers.IO) { ImportedDao.queryAllUUIDs() }

        val orderIndex =
            ProfileStore.loadProfileOrder().withIndex().associate { it.value to it.index }

        return uuids
            .mapNotNull { resolveProfile(it) }
            .sortedBy { orderIndex[it.uuid] ?: Int.MAX_VALUE }
    }

    override suspend fun queryActive(): Profile? {
        val active = store.activeProfile ?: return null

        return if (ImportedDao.exists(active)) {
            resolveProfile(active)
        } else {
            store.activeProfile = null
            null
        }
    }

    override suspend fun setActive(profile: Profile) {
        store.activeProfile = profile.uuid
        StatusProvider.currentProfile = profile.toString()
        context.sendProfileChanged(profile.uuid)
    }

    override suspend fun clearActive(profile: Profile) {
        store.activeProfile = null
        StatusProvider.currentProfile = null
        context.sendProfileChanged(profile.uuid)
    }

    override suspend fun reorder(uuids: List<UUID>) {
        val existing = ImportedDao.queryAllUUIDs()
        val existingSet = existing.toSet()

        val normalized = buildList {
            uuids.forEach { uuid -> if (uuid in existingSet && uuid !in this) add(uuid) }
            existing.forEach { uuid -> if (uuid !in this) add(uuid) }
        }

        ProfileStore.saveProfileOrder(normalized)
    }

    private suspend fun resolveProfile(uuid: UUID): Profile? {
        val imported = ImportedDao.queryByUUID(uuid) ?: return null

        val active = store.activeProfile
        val name = ProfileNameUtils.resolveDisplayName(imported.name, imported.source)

        return Profile(
            uuid,
            name,
            imported.type,
            imported.source,
            active != null && imported.uuid == active,
            imported.interval,
            imported.upload,
            imported.download,
            imported.total,
            imported.expire,
            resolveUpdatedAt(uuid),
            imported.ageSecretKey,
        )
    }

    private fun resolveUpdatedAt(uuid: UUID): Long {
        return context.importedDir.resolve(uuid.toString()).directoryLastModified ?: -1
    }
}
