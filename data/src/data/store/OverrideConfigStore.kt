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
import com.github.yumelira.yumebox.core.contract.OverrideConfigRepository
import com.github.yumelira.yumebox.core.model.MetadataIndex
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.OverrideContentType
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.core.model.OverrideMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File

class OverrideConfigStore(
    private val context: Context,
    private val metadataIndexStore: MetadataIndexStore,
) : OverrideConfigRepository {
    companion object {
        const val INTERNAL_RUNTIME_PREFIX = "__runtime__"
        fun isInternalRuntimeConfig(id: String): Boolean = id.startsWith(INTERNAL_RUNTIME_PREFIX)
    }
    private val configsDir: File get() = metadataIndexStore.getConfigsDirectory()
    private val configExtensions = setOf("yaml", "yml", "js")
    private val cleanupExtensions = configExtensions
    private val configsFlow = MutableStateFlow<List<OverrideConfig>>(emptyList())

    private fun updateConfigsFlowSnapshot(
        metadataIndex: MetadataIndex,
        userConfigsById: Map<String, OverrideConfig>,
    ) {
        configsFlow.value =
            metadataIndex.sortedUserMetadata().mapNotNull { metadata ->
                userConfigsById[metadata.id]
            }
    }

    suspend fun getAll(): List<OverrideConfig> =
        withContext(Dispatchers.IO) { loadUserConfigs() }

    fun getAllFlow(): Flow<List<OverrideConfig>> = configsFlow.asStateFlow()

    override suspend fun getById(id: String): OverrideConfig? =
        withContext(Dispatchers.IO) {
            val metadata = metadataIndexStore.getIndex().getById(id) ?: return@withContext null
            loadConfigContent(metadata)
        }

    override suspend fun getUserConfigs(): List<OverrideConfig> =
        withContext(Dispatchers.IO) {
            loadUserConfigs().filter {
                it.id != OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
            }
        }

    override fun getUserConfigsFlow(): Flow<List<OverrideConfig>> =
        configsFlow.asStateFlow().map { configs ->
            configs.filter { it.id != OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID }
        }.onStart {
            // Ensure configs are loaded on first collection. Without this,
            // configsFlow remains empty until the first write operation.
            if (configsFlow.value.isEmpty()) {
                val loaded = loadUserConfigs()
                configsFlow.value = loaded
            }
        }

    override suspend fun save(config: OverrideConfig) =
        withContext(Dispatchers.IO) {
            configsDir.mkdirs()
            cleanupStaleConfigFiles(config.id, keepExtension = config.contentType.extension)
            resolveConfigFile(config.id, config.contentType).writeText(config.content)
            val updatedIndex = metadataIndexStore.updateConfigs { existingConfigs ->
                val existingMetadata = existingConfigs[config.id]
                val metadata = OverrideMetadata(
                    id = config.id,
                    name = config.name,
                    description = config.description,
                    contentType = config.contentType,
                    createdAt = config.createdAt,
                    updatedAt = config.updatedAt,
                    sortOrder = existingMetadata?.sortOrder ?: MetadataIndex(existingConfigs).nextUserSortOrder(),
                )
                existingConfigs + (config.id to metadata)
            }
            val userConfigsById =
                configsFlow.value.associateBy(OverrideConfig::id).toMutableMap().apply {
                    put(config.id, config)
                }
            updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
        }

    override suspend fun delete(id: String): Boolean =
        withContext(Dispatchers.IO) {
            cleanupStaleConfigFiles(id)
            val currentIndex = metadataIndexStore.getIndex()
            if (currentIndex.getById(id) == null) {
                configsFlow.value = loadUserConfigs()
                return@withContext false
            }
            val updatedIndex = metadataIndexStore.removeConfigWithBindings(id)
            val userConfigsById =
                configsFlow.value.associateBy(OverrideConfig::id).toMutableMap().apply {
                    remove(id)
                }
            updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
            true
        }

    override suspend fun duplicate(id: String): OverrideConfig? =
        withContext(Dispatchers.IO) {
            val original = getById(id) ?: return@withContext null
            val newMetadata = original.toMetadata().duplicateAsUser()
            val duplicated =
                original.copy(
                    id = newMetadata.id,
                    name = newMetadata.name,
                    createdAt = newMetadata.createdAt,
                    updatedAt = newMetadata.updatedAt,
                )
            save(duplicated)
            duplicated
        }

    suspend fun exists(id: String): Boolean =
        withContext(Dispatchers.IO) {
            metadataIndexStore.canRecoverConfig(id)
        }

    override suspend fun loadCustomRoutingContent(): String? =
        withContext(Dispatchers.IO) {
            val file = metadataIndexStore.findConfigFile(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID) ?: resolveConfigFile(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID, OverrideContentType.Yaml)
            if (!file.exists()) return@withContext null
            file.readText().takeIf(String::isNotBlank)
        }

    override suspend fun saveCustomRoutingContent(content: String) =
        withContext(Dispatchers.IO) {
            if (content.isBlank()) {
                delete(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID)
                return@withContext
            }

            val existing = getById(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID)
            save(
                OverrideConfig(
                    id = OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID,
                    name = OverrideInternalConstants.CUSTOM_ROUTING_FILE_NAME,
                    description = null,
                    contentType = OverrideContentType.Yaml,
                    content = content,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }

    override fun getConfigContent(id: String): String? {
        val metadata = metadataIndexStore.getCachedIndex()?.getById(id) ?: return null
        val file = findConfigFile(metadata) ?: return null
        return runCatching { file.readText() }.getOrNull()
    }

    override fun saveConfigContent(id: String, content: String): Boolean {
        val metadataIndex = metadataIndexStore.getCachedIndex() ?: return false
        val metadata = metadataIndex.getById(id) ?: return false
        return runCatching {
            val file = findConfigFile(metadata) ?: resolveConfigFile(id, metadata.contentType)
            file.parentFile?.mkdirs()
            file.writeText(content)
            val updatedIndex = metadataIndexStore.updateConfigsSync(metadataIndex.upsert(metadata.copy(updatedAt = System.currentTimeMillis())))
            // Build the updated config directly from the content we just wrote,
            // avoiding a redundant read-back from disk.
            val now = updatedIndex.getById(id)?.updatedAt ?: metadata.updatedAt
            val updatedConfig = OverrideConfig(id = metadata.id, name = metadata.name, description = metadata.description, contentType = metadata.contentType, content = content, createdAt = metadata.createdAt, updatedAt = now)
            val userConfigsById = configsFlow.value.associateBy(OverrideConfig::id).toMutableMap().apply { put(id, updatedConfig) }
            updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
            true
        }.isSuccess
    }

    fun getConfigFilePath(id: String): File? {
        val metadata = metadataIndexStore.getCachedIndex()?.getById(id)
        return if (metadata != null) findConfigFile(metadata)
        else metadataIndexStore.findConfigFile(id)
    }

    fun getConfigsDirectory(): File = configsDir

    override suspend fun reorderUserConfigs(orderedIds: List<String>) =
        withContext(Dispatchers.IO) {
            if (orderedIds.isEmpty()) return@withContext
            val metadataIndex = metadataIndexStore.getIndex()
            val sortedUserMetadata = metadataIndex.sortedUserMetadata()
            if (sortedUserMetadata.isEmpty()) return@withContext
            val userMetadataById = sortedUserMetadata.associateBy(OverrideMetadata::id)
            val reorderedIds = orderedIds.filter(userMetadataById::containsKey)
            if (reorderedIds.isEmpty()) return@withContext
            val remainingIds = sortedUserMetadata.map(OverrideMetadata::id).filterNot(reorderedIds::contains)
            val finalOrder = reorderedIds + remainingIds
            val updatedConfigs = metadataIndex.configs.toMutableMap()
            var hasChanges = false
            finalOrder.forEachIndexed { index, id ->
                val metadata = userMetadataById[id] ?: return@forEachIndexed
                val newSortOrder = index.toLong() + 1L
                if (metadata.sortOrder != newSortOrder) {
                    updatedConfigs[id] = metadata.copy(sortOrder = newSortOrder)
                    hasChanges = true
                }
            }
            if (hasChanges) {
                val updatedIndex = metadataIndexStore.updateConfigs { _ -> updatedConfigs }
                val userConfigsById = configsFlow.value.associateBy(OverrideConfig::id)
                updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
            }
        }

    private suspend fun loadUserConfigs(): List<OverrideConfig> {
        if (!configsDir.exists()) return emptyList()
        return metadataIndexStore.getIndex().sortedUserMetadata().mapNotNull { metadata ->
            if (isInternalRuntimeConfig(metadata.id)) return@mapNotNull null
            loadConfigContent(metadata)
        }
    }

    private fun loadConfigContent(metadata: OverrideMetadata): OverrideConfig? {
        val file = findConfigFile(metadata) ?: return null
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        return OverrideConfig(
            id = metadata.id,
            name = metadata.name,
            description = metadata.description,
            contentType = metadata.contentType,
            content = content,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt,
        )
    }

    private fun resolveConfigFile(id: String, contentType: OverrideContentType): File =
        configsDir.resolve("$id.${contentType.extension}")

    private fun findConfigFile(metadata: OverrideMetadata): File? = metadataIndexStore.findConfigFile(metadata.id, metadata.contentType)

    private fun cleanupStaleConfigFiles(id: String, keepExtension: String? = null) {
        cleanupExtensions.forEach { extension ->
            if (keepExtension != null && extension == keepExtension) return@forEach
            runCatching { configsDir.resolve("$id.$extension").delete() }
        }
    }

    private fun OverrideConfig.toMetadata(): OverrideMetadata =
        OverrideMetadata(
            id = id,
            name = name,
            description = description,
            contentType = contentType,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
