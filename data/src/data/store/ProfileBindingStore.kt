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

package com.github.yumelira.yumebox.data.store

import android.content.Context
import com.github.yumelira.yumebox.core.model.ProfileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ProfileBindingStore(context: Context, private val metadataIndexStore: MetadataIndexStore) : ProfileBindingProvider, java.io.Closeable {
    private val bindingsStateFlow = MutableStateFlow<Map<String, ProfileBinding>>(emptyMap())
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initJob: Job = storeScope.launch { bindingsStateFlow.value = loadBindings() }

    override fun close() { storeScope.cancel() }

    override suspend fun getBinding(profileId: String): ProfileBinding? =
        withContext(Dispatchers.IO) { initJob.join(); bindingsStateFlow.value[profileId] }

    override fun getBindingFlow(profileId: String): Flow<ProfileBinding?> =
        bindingsStateFlow.asStateFlow().map { bindings -> bindings[profileId] }

    override suspend fun setBinding(binding: ProfileBinding) =
        withContext(Dispatchers.IO) {
            initJob.join()
            val map = bindingsStateFlow.value.toMutableMap()
            map[binding.profileId] = binding
            saveBindings(map)
        }

    override suspend fun removeBinding(profileId: String) =
        withContext(Dispatchers.IO) {
            initJob.join()
            val map = bindingsStateFlow.value.toMutableMap()
            map.remove(profileId)
            saveBindings(map)
        }

    override suspend fun getAllBindings(): List<ProfileBinding> =
        withContext(Dispatchers.IO) { bindingsStateFlow.value.values.toList() }

    override fun getAllBindingsFlow(): Flow<List<ProfileBinding>> =
        bindingsStateFlow.asStateFlow().map { bindings -> bindings.values.toList() }

    override suspend fun getProfilesUsingOverride(overrideId: String): List<String> =
        withContext(Dispatchers.IO) {
            bindingsStateFlow.value.values
                .filter { binding -> isOverrideApplied(binding, overrideId) }
                .map { it.profileId }
        }

    override suspend fun isOverrideInUse(overrideId: String): Boolean =
        withContext(Dispatchers.IO) {
            bindingsStateFlow.value.values.any { binding -> isOverrideApplied(binding, overrideId) }
        }

    override suspend fun getOverrideUsageCount(overrideId: String): Int =
        withContext(Dispatchers.IO) {
            bindingsStateFlow.value.values.count { binding -> isOverrideApplied(binding, overrideId) }
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
            initJob.join()
            val updatedIndex = metadataIndexStore.removeOverrideFromAllBindings(overrideId)
            bindingsStateFlow.value = updatedIndex.profileChains
        }

    override suspend fun clearOverrides(profileId: String) {
        val existing = getBinding(profileId) ?: return
        setBinding(existing.clearOverrides())
    }

    suspend fun clearAll() =
        withContext(Dispatchers.IO) {
            initJob.join()
            val updatedIndex = metadataIndexStore.updateProfileChains { emptyMap() }
            bindingsStateFlow.value = updatedIndex.profileChains
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
    private suspend fun loadBindings(): Map<String, ProfileBinding> =
        try {
            metadataIndexStore.getIndex().profileChains
        } catch (error: Exception) { // fault barrier: any metadata read/decode failure degrades to empty
            Timber.w(error, "Failed to load bindings from metadata.yaml, returning empty map")
            emptyMap()
        }

    private suspend fun saveBindings(map: Map<String, ProfileBinding>) {
        metadataIndexStore.updateProfileChains { _ -> map }
        bindingsStateFlow.value = map
    }

    private fun isOverrideApplied(binding: ProfileBinding, overrideId: String): Boolean =
        binding.overrideIds.contains(overrideId)
}
