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

package com.github.lmfirefly.flycat.feature.override.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.OverrideApplier
import com.github.lmfirefly.flycat.core.contract.OverrideConfigRepository
import com.github.lmfirefly.flycat.core.contract.ProfileBindingReader
import com.github.lmfirefly.flycat.core.contract.ProfileStoreReader
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.override.OverrideContentType
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.feature.override.domain.OverrideCrudUseCase
import com.github.lmfirefly.flycat.locale.FlyTxt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class OverrideConfigViewModel(
    private val configRepo: OverrideConfigRepository,
    private val bindingReader: ProfileBindingReader,
    private val activeProfileOverrideApplier: OverrideApplier,
    private val profileStore: ProfileStoreReader,
    private val overrideCrud: OverrideCrudUseCase,
) : ViewModel() {
    companion object {
        private const val TAG = "OverrideConfigViewModel"
        private const val NETWORK_IMPORT_CONNECT_TIMEOUT_MS = 15_000
        private const val NETWORK_IMPORT_READ_TIMEOUT_MS = 30_000
    }

    private val _userConfigs = MutableStateFlow<List<OverrideConfig>>(emptyList())
    val userConfigs: StateFlow<List<OverrideConfig>> = _userConfigs.asStateFlow()

    private val _builtInConfigs = MutableStateFlow<List<OverrideConfig>>(emptyList())
    val builtInConfigs: StateFlow<List<OverrideConfig>> = _builtInConfigs.asStateFlow()

    /** Backed by [_userConfigs]; exposed for internal lookups (e.g. [getConfigById]). */
    private val configs: List<OverrideConfig> get() = _userConfigs.value

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _usageCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val usageCountMap: StateFlow<Map<String, Int>> = _usageCountMap.asStateFlow()

    private val _pendingRevealConfigId = MutableStateFlow<String?>(null)
    val pendingRevealConfigId: StateFlow<String?> = _pendingRevealConfigId.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            bindingReader.getAllBindingsFlow().collectLatest { loadUsageCounts() }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val builtIns = configRepo.getBuiltInConfigs()
                _builtInConfigs.value = builtIns
                val users = configRepo.getUserConfigs()
                _userConfigs.value = users
                loadUsageCounts()
            } catch (error: Exception) { // fault barrier: top-level ViewModel load handler, log and reset loading
                Timber.tag(TAG).e(error, "Failed to load overrides")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getConfigById(id: String): OverrideConfig? = configs.find { it.id == id }
        ?: _builtInConfigs.value.find { it.id == id }

    suspend fun getConfigContent(configId: String): String? = withContext(Dispatchers.IO) { configRepo.getConfigContent(configId) }

    suspend fun saveConfigContent(configId: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val saved = configRepo.saveConfigContent(configId, content)
        if (!saved) return@withContext false
        activeProfileOverrideApplier.reapplyActiveProfileIfUsingOverride(configId)
        refresh()
        true
    }

    fun createConfig(name: String, description: String? = null, contentType: OverrideContentType) {
        viewModelScope.launch {
            runCatching {
                val config = overrideCrud.createConfig(name, description, contentType)
                _pendingRevealConfigId.value = config.id
                refresh()
            }.onFailure { error -> Timber.tag(TAG).e(error, "Failed to create override") }
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            runCatching {
                overrideCrud.deleteConfig(id)
                refresh()
            }.onFailure { error -> Timber.tag(TAG).e(error, "Failed to delete override") }
        }
    }

    fun duplicateConfig(id: String) {
        viewModelScope.launch {
            runCatching {
                val duplicated = overrideCrud.duplicateConfig(id)
                if (duplicated != null) _pendingRevealConfigId.value = duplicated.id
                refresh()
            }.onFailure { error -> Timber.tag(TAG).e(error, "Failed to duplicate override") }
        }
    }

    fun reorderUserConfigs(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentConfigs = _userConfigs.value
            if (fromIndex !in currentConfigs.indices || fromIndex == toIndex) return@launch
            val reorderedConfigs =
                currentConfigs.toMutableList().also { configs ->
                    val moving = configs.removeAt(fromIndex)
                    configs.add(toIndex.coerceIn(0, configs.size), moving)
                }
            _userConfigs.value = reorderedConfigs

            runCatching { configRepo.reorderUserConfigs(reorderedConfigs.map(OverrideConfig::id)) }
                .onFailure { error -> Timber.tag(TAG).e(error, "Failed to reorder overrides") }
            refresh()
        }
    }

    fun importConfig(content: String, sourceName: String?): Result<OverrideConfig> {
        val result = overrideCrud.buildImportConfig(content, sourceName)
        result.onSuccess { config ->
            viewModelScope.launch {
                overrideCrud.saveConfig(config)
                _pendingRevealConfigId.value = config.id
                refresh()
            }
        }
        return result
    }

    suspend fun importConfigFromUrl(rawUrl: String): Result<OverrideConfig> {
        val result = overrideCrud.importConfigFromUrl(rawUrl)
        result.onSuccess { config ->
            _pendingRevealConfigId.value = config.id
            refresh()
        }
        return result
    }

    suspend fun isConfigInUse(id: String): Boolean = bindingReader.isOverrideInUse(id)

    data class OverrideApplySnapshot(
        val overrideId: String,
        val profiles: List<Profile>,
        val selectedProfileIds: Set<String>,
    )

    suspend fun loadApplySnapshot(overrideId: String): Result<OverrideApplySnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = overrideCrud.loadApplySnapshot(overrideId)
                OverrideApplySnapshot(
                    overrideId = snapshot.overrideId,
                    profiles = snapshot.profiles,
                    selectedProfileIds = snapshot.selectedProfileIds,
                )
            }.onFailure { error ->
                Timber.tag(TAG).e(error, "Failed to load apply snapshot for %s", overrideId)
            }
        }

    suspend fun applyOverrideToProfiles(
        overrideId: String,
        selectedProfileIds: Set<String>,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                overrideCrud.applyOverrideToProfiles(overrideId, selectedProfileIds)
                loadUsageCounts()
            }.onFailure { error ->
                Timber.tag(TAG).e(error, "Failed to apply override %s to profiles", overrideId)
            }
        }

    fun consumePendingRevealConfig(configId: String) {
        if (_pendingRevealConfigId.value == configId) {
            _pendingRevealConfigId.value = null
        }
    }

    private suspend fun loadUsageCounts() {
        val countMap = mutableMapOf<String, Int>()
        (configs + _builtInConfigs.value).forEach { config ->
            countMap[config.id] = bindingReader.getOverrideUsageCount(config.id)
        }
        _usageCountMap.value = countMap
    }
}
