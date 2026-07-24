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

@file:Suppress("UnusedSymbol", "IfThenToElvis")

package com.github.yumelira.yumebox.data.store

import android.content.Context
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.data.model.MetadataIndex
import com.github.yumelira.yumebox.data.model.OverrideMetadata
import com.github.yumelira.yumebox.data.model.ProfileBinding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

class ProfileBindingStore(context: Context) : ProfileBindingProvider {
    private val metadataFile = File(context.filesDir, "overrides/metadata.yaml")

    private val bindingsStateFlow = MutableStateFlow<Map<String, ProfileBinding>>(emptyMap())

    init {
        bindingsStateFlow.value = loadBindings()
    }

    override suspend fun getBinding(profileId: String): ProfileBinding? =
        withContext(Dispatchers.IO) { loadBindings()[profileId] }

    override fun getBindingFlow(profileId: String): Flow<ProfileBinding?> =
        bindingsStateFlow.asStateFlow().map { bindings -> bindings[profileId] }

    override suspend fun setBinding(binding: ProfileBinding) =
        withContext(Dispatchers.IO) {
            synchronized(OverrideMetadataFileLock.monitor) {
                val index = loadMetadataIndex()
                val sanitizedBinding = sanitizeBinding(binding, index)
                val bindings = index.profileChains + (binding.profileId to sanitizedBinding)
                saveMetadataIndex(index.copy(profileChains = bindings))
                bindingsStateFlow.value = bindings
            }
        }

    override suspend fun removeBinding(profileId: String) =
        withContext(Dispatchers.IO) {
            synchronized(OverrideMetadataFileLock.monitor) {
                val index = loadMetadataIndex()
                val bindings = index.profileChains - profileId
                saveMetadataIndex(index.copy(profileChains = bindings))
                bindingsStateFlow.value = bindings
            }
        }

    override suspend fun getAllBindings(): List<ProfileBinding> =
        withContext(Dispatchers.IO) { loadBindings().values.toList() }

    override fun getAllBindingsFlow(): Flow<List<ProfileBinding>> =
        bindingsStateFlow.asStateFlow().map { bindings -> bindings.values.toList() }

    override suspend fun getProfilesUsingOverride(overrideId: String): List<String> =
        withContext(Dispatchers.IO) {
            loadBindings()
                .values
                .filter { binding -> isOverrideApplied(binding, overrideId) }
                .map { it.profileId }
        }

    override suspend fun isOverrideInUse(overrideId: String): Boolean =
        withContext(Dispatchers.IO) {
            loadBindings().values.any { binding -> isOverrideApplied(binding, overrideId) }
        }

    override suspend fun getOverrideUsageCount(overrideId: String): Int =
        withContext(Dispatchers.IO) {
            loadBindings().values.count { binding -> isOverrideApplied(binding, overrideId) }
        }

    override suspend fun addOverride(profileId: String, overrideId: String, index: Int?) {
        val existing = getBinding(profileId)
        val binding =
            existing?.addOverride(overrideId, index)
                ?: ProfileBinding.withOverride(profileId, overrideId)
        setBinding(binding)
    }

    override suspend fun removeOverride(profileId: String, overrideId: String) {
        val existing = getBinding(profileId) ?: return
        setBinding(existing.removeOverride(overrideId))
    }

    override suspend fun removeOverrideFromAllBindings(overrideId: String) =
        withContext(Dispatchers.IO) {
            synchronized(OverrideMetadataFileLock.monitor) {
                val currentIndex = loadMetadataIndex()
                val updatedIndex = currentIndex.removeOverrideFromProfileChains(overrideId)
                if (updatedIndex != currentIndex) {
                    saveMetadataIndex(updatedIndex)
                }
                bindingsStateFlow.value = updatedIndex.profileChains
            }
        }

    override suspend fun clearOverrides(profileId: String) {
        val existing = getBinding(profileId) ?: return
        setBinding(existing.clearOverrides())
    }

    suspend fun clearAll() =
        withContext(Dispatchers.IO) {
            synchronized(OverrideMetadataFileLock.monitor) {
                val index = loadMetadataIndex()
                saveMetadataIndex(index.copy(profileChains = emptyMap()))
                bindingsStateFlow.value = emptyMap()
            }
        }

    suspend fun setOverrides(profileId: String, overrideIds: List<String>) {
        val existing = getBinding(profileId)
        val binding =
            if (existing != null) {
                existing.setOverrides(overrideIds)
            } else {
                ProfileBinding.withOverrides(profileId, overrideIds)
            }
        setBinding(binding)
    }

    suspend fun moveOverride(profileId: String, fromIndex: Int, toIndex: Int) {
        val existing = getBinding(profileId) ?: return
        setBinding(existing.moveOverride(fromIndex, toIndex))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadBindings(): Map<String, ProfileBinding> =
        try {
            loadMetadataIndex().profileChains
        } catch (
            error: Exception) { // fault barrier: any metadata read/decode failure degrades to empty
            Timber.w(error, "Failed to load bindings from metadata.yaml, returning empty map")
            emptyMap()
        }

    private fun loadMetadataIndex(): MetadataIndex =
        synchronized(OverrideMetadataFileLock.monitor) {
            if (!metadataFile.exists()) return@synchronized MetadataIndex()
            val index = runCatching {
                YamlCodec.decode(MetadataIndex.serializer(), metadataFile.readText())
            }
                .getOrElse { error ->
                    Timber.w(error, "Failed to decode override metadata index")
                    MetadataIndex()
                }
            val sanitized = sanitizeMetadataIndex(index)
            if (sanitized != index) {
                saveMetadataIndex(sanitized)
            }
            sanitized
        }

    private fun saveMetadataIndex(index: MetadataIndex) {
        synchronized(OverrideMetadataFileLock.monitor) {
            metadataFile.parentFile?.mkdirs()
            metadataFile.writeText(YamlCodec.encode(MetadataIndex.serializer(), index))
        }
    }

    private fun sanitizeMetadataIndex(index: MetadataIndex): MetadataIndex {
        val normalizedConfigs =
            index.configs.mapValues { (id, metadata) ->
                if (metadata.id.isBlank()) metadata.copy(id = id) else metadata
            }
        val normalizedIndex = index.copy(configs = normalizedConfigs)
        return normalizedIndex.copy(
            profileChains =
                normalizedIndex.profileChains.mapValues { (profileId, binding) ->
                    sanitizeBinding(
                        binding =
                            if (binding.profileId.isBlank()) {
                                binding.copy(profileId = profileId)
                            } else {
                                binding
                            },
                        index = normalizedIndex,
                    )
                }
        )
    }

    private fun sanitizeBinding(binding: ProfileBinding, index: MetadataIndex): ProfileBinding =
        binding.copy(
            overrideIds =
                binding.overrideIds.filterNot { overrideId ->
                    isLegacyPresetOverrideId(overrideId) ||
                        (overrideId.startsWith(OverrideMetadata.ID_PREFIX) &&
                            overrideId !in index.configs)
                }
        )

    private fun isLegacyPresetOverrideId(overrideId: String): Boolean =
        overrideId.startsWith(OverrideMetadata.LEGACY_SYSTEM_PREFIX)

    private fun isOverrideApplied(binding: ProfileBinding, overrideId: String): Boolean =
        binding.overrideIds.contains(overrideId)
}
