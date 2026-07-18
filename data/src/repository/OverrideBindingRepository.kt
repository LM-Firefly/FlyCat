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

package com.github.lmfirefly.flycat.data.repository

import com.github.lmfirefly.flycat.core.contract.ProfileBindingReader
import com.github.lmfirefly.flycat.core.model.OverrideSpec
import com.github.lmfirefly.flycat.core.model.override.OverrideMetadata
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import com.github.lmfirefly.flycat.data.store.OverrideConfigStore
import com.github.lmfirefly.flycat.data.store.ProfileBindingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OverrideBindingRepository(private val configStore: OverrideConfigStore, private val bindingProvider: ProfileBindingProvider) : ProfileBindingReader {
    override fun getAllBindingsFlow(): Flow<List<ProfileBinding>> = bindingProvider.getAllBindingsFlow()

    suspend fun resolveIds(profileId: String): List<String> { val binding = bindingProvider.getBinding(profileId); return resolveBindingIds(binding) }

    suspend fun resolveSpecs(overrideIds: List<String>): List<OverrideSpec> = resolveOrderedSpecs(overrideIds)

    suspend fun getProfilesUsingOverride(overrideId: String): List<String> = bindingProvider.getProfilesUsingOverride(overrideId)

    override suspend fun isOverrideInUse(overrideId: String): Boolean = bindingProvider.isOverrideInUse(overrideId)

    override suspend fun getOverrideUsageCount(overrideId: String): Int = bindingProvider.getOverrideUsageCount(overrideId)

    override suspend fun getBinding(profileId: String) = bindingProvider.getBinding(profileId)

    suspend fun bindOverride(profileId: String, overrideId: String, index: Int? = null) { bindingProvider.addOverride(profileId, overrideId, index) }

    suspend fun setOverrides(profileId: String, overrideIds: List<String>) {
        val binding = bindingProvider.getBinding(profileId)
        if (binding != null) { bindingProvider.setBinding(binding.setOverrides(overrideIds)) } else { bindingProvider.setBinding(ProfileBinding.withOverrides(profileId, overrideIds)) }
    }

    suspend fun clearBinding(profileId: String) { bindingProvider.removeBinding(profileId) }

    override suspend fun setBinding(binding: ProfileBinding) = bindingProvider.setBinding(binding)

    private suspend fun resolveBindingIds(binding: ProfileBinding?): List<String> {
        if (binding == null) { return emptyList() }
        return withContext(Dispatchers.IO) {
            buildList {
                binding.overrideIds.forEach { overrideId ->
                    if (isLegacyPresetOverrideId(overrideId) || OverrideConfigStore.isInternalRuntimeConfig(overrideId)) { return@forEach }
                    if (configStore.getConfigFilePath(overrideId) != null) { add(overrideId) }
                }
            }.distinct()
        }
    }

    private suspend fun resolveOrderedSpecs(overrideIds: List<String>): List<OverrideSpec> {
        return withContext(Dispatchers.IO) {
            overrideIds.mapNotNull { overrideId ->
                val config = configStore.getById(overrideId) ?: return@mapNotNull null
                val file = configStore.getConfigFilePath(overrideId) ?: return@mapNotNull null
                OverrideSpec(path = file.absolutePath, ext = config.contentType.extension)
            }
        }
    }

    private fun isLegacyPresetOverrideId(overrideId: String): Boolean =
        overrideId.startsWith(OverrideMetadata.LEGACY_SYSTEM_PREFIX)
}
