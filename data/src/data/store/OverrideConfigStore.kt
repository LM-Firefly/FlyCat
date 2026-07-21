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
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.data.model.BuiltInOverrideCatalog
import com.github.yumelira.yumebox.data.model.BuiltInOverrideDefinition
import com.github.yumelira.yumebox.data.model.MetadataIndex
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.OverrideContentType
import com.github.yumelira.yumebox.data.model.OverrideMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class OverrideConfigStore(
    private val context: Context,
    private val bindingProvider: ProfileBindingProvider,
) : OverrideConfigProvider {
    companion object {
        const val INTERNAL_RUNTIME_PREFIX = "__runtime__"

        fun isInternalRuntimeConfig(id: String): Boolean = id.startsWith(INTERNAL_RUNTIME_PREFIX)
    }

    private val overridesDir = File(context.filesDir, "overrides")
    private val configsDir = File(overridesDir, "configs")
    private val metadataFile = File(overridesDir, "metadata.yaml")

    private val configExtensions = setOf("yaml", "yml", "js")
    private val cleanupExtensions = configExtensions

    private val configsFlow = MutableStateFlow<List<OverrideConfig>>(emptyList())

    private suspend fun refreshConfigsFlow() {
        configsFlow.value = getAll()
    }

    private fun updateConfigsFlowSnapshot(
        metadataIndex: MetadataIndex,
        userConfigsById: Map<String, OverrideConfig>,
    ) {
        configsFlow.value =
            metadataIndex.sortedUserMetadata().mapNotNull { metadata ->
                userConfigsById[metadata.id]
            }
    }

    override suspend fun getAll(): List<OverrideConfig> =
        withContext(Dispatchers.IO) { loadUserConfigs() }

    override fun getAllFlow(): Flow<List<OverrideConfig>> = configsFlow.asStateFlow()

    override suspend fun getById(id: String): OverrideConfig? =
        withContext(Dispatchers.IO) {
            if (BuiltInOverrideCatalog.isBuiltIn(id)) {
                return@withContext loadBuiltInConfig(id)
            }
            val metadata = loadMetadataIndex().getById(id) ?: return@withContext null
            loadConfigContent(metadata)
        }

    override suspend fun getUserConfigs(): List<OverrideConfig> =
        withContext(Dispatchers.IO) {
            loadUserConfigs().filter(::isUserOwnedConfig)
        }

    /** Bundled templates, always listed above user imports. Materializes assets on first read. */
    suspend fun getBuiltInConfigs(): List<OverrideConfig> =
        withContext(Dispatchers.IO) {
            BuiltInOverrideCatalog.all.mapNotNull { def -> loadBuiltInConfig(def.id) }
        }

    override fun getUserConfigsFlow(): Flow<List<OverrideConfig>> =
        flow {
                emit(
                    loadUserConfigs().filter(::isUserOwnedConfig)
                )
            }
            .flowOn(Dispatchers.IO)

    override suspend fun save(config: OverrideConfig) =
        withContext(Dispatchers.IO) {
            if (BuiltInOverrideCatalog.isBuiltIn(config.id)) {
                // Built-ins are not metadata-owned; only rewrite the materialized file.
                configsDir.mkdirs()
                cleanupStaleConfigFiles(config.id, keepExtension = config.contentType.extension)
                resolveConfigFile(config.id, config.contentType).writeText(config.content)
                return@withContext
            }

            synchronized(OverrideMetadataFileLock.monitor) {
                configsDir.mkdirs()
                cleanupStaleConfigFiles(config.id, keepExtension = config.contentType.extension)
                resolveConfigFile(config.id, config.contentType).writeText(config.content)

                val metadataIndex = loadMetadataIndex()
                val existingMetadata = metadataIndex.getById(config.id)
                val metadata =
                    OverrideMetadata(
                        id = config.id,
                        name = config.name,
                        description = config.description,
                        contentType = config.contentType,
                        createdAt = config.createdAt,
                        updatedAt = config.updatedAt,
                        sortOrder =
                            existingMetadata?.sortOrder ?: metadataIndex.nextUserSortOrder(),
                    )
                val updatedIndex = metadataIndex.upsert(metadata)
                saveMetadataIndex(updatedIndex)

                val userConfigsById =
                    configsFlow.value.associateBy(OverrideConfig::id).toMutableMap().apply {
                        put(config.id, config)
                    }
                updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
            }
        }

    override suspend fun delete(id: String): Boolean =
        withContext(Dispatchers.IO) {
            // Built-ins cannot be deleted from the store (UI also hides the action).
            if (BuiltInOverrideCatalog.isBuiltIn(id)) {
                return@withContext false
            }
            val updatedIndex =
                synchronized(OverrideMetadataFileLock.monitor) {
                    val currentIndex = loadMetadataIndex()
                    if (currentIndex.getById(id) == null) {
                        return@synchronized null
                    }

                    val updated = currentIndex.remove(id).removeOverrideFromProfileChains(id)
                    saveMetadataIndex(updated)
                    cleanupStaleConfigFiles(id)
                    updated
                }
            if (updatedIndex == null) {
                refreshConfigsFlow()
                return@withContext false
            }
            // The persistent snapshot is already clean; this synchronizes the binding StateFlow.
            bindingProvider.removeOverrideFromAllBindings(id)
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

    override suspend fun exists(id: String): Boolean =
        withContext(Dispatchers.IO) {
            if (BuiltInOverrideCatalog.isBuiltIn(id)) {
                return@withContext loadBuiltInConfig(id) != null
            }
            loadMetadataIndex().getById(id)?.let(::findConfigFile) != null
        }

    suspend fun loadCustomRoutingContent(): String? =
        withContext(Dispatchers.IO) {
            val file =
                getConfigFilePath(OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID)
                    ?: resolveConfigFile(
                        OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID,
                        OverrideContentType.Yaml,
                    )
            if (!file.exists()) return@withContext null
            file.readText().takeIf(String::isNotBlank)
        }

    suspend fun saveCustomRoutingContent(content: String) =
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

    fun getConfigContent(id: String): String? {
        if (BuiltInOverrideCatalog.isBuiltIn(id)) {
            return materializeBuiltInFile(id)?.let { file ->
                runCatching { file.readText() }.getOrNull()
            }
        }
        val metadata = loadMetadataIndex().getById(id) ?: return null
        val file = findConfigFile(metadata) ?: return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun saveConfigContent(id: String, content: String): Boolean {
        if (BuiltInOverrideCatalog.isBuiltIn(id)) {
            val def = BuiltInOverrideCatalog.find(id) ?: return false
            return runCatching {
                    configsDir.mkdirs()
                    cleanupStaleConfigFiles(id, keepExtension = def.contentType.extension)
                    resolveConfigFile(id, def.contentType).writeText(content)
                    true
                }
                .getOrDefault(false)
        }
        return synchronized(OverrideMetadataFileLock.monitor) {
            val metadata = loadMetadataIndex().getById(id) ?: return@synchronized false
            runCatching {
                    val file = findConfigFile(metadata) ?: resolveConfigFile(id, metadata.contentType)
                    file.parentFile?.mkdirs()
                    file.writeText(content)

                    val updatedIndex =
                        loadMetadataIndex()
                            .upsert(metadata.copy(updatedAt = System.currentTimeMillis()))
                    saveMetadataIndex(updatedIndex)
                    val userConfigsById =
                        configsFlow.value.associateBy(OverrideConfig::id).toMutableMap().apply {
                            loadConfigContent(
                                    metadata.copy(
                                        updatedAt =
                                            updatedIndex.getById(id)?.updatedAt
                                                ?: metadata.updatedAt
                                    )
                                )
                                ?.let { put(id, it) }
                        }
                    updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
                    true
                }
                .isSuccess
        }
    }

    fun getConfigFilePath(id: String): File? {
        if (BuiltInOverrideCatalog.isBuiltIn(id)) {
            return materializeBuiltInFile(id)
        }
        val metadata = loadMetadataIndex().getById(id) ?: return null
        return findConfigFile(metadata)
    }

    fun getConfigsDirectory(): File = configsDir

    suspend fun reorderUserConfigs(orderedIds: List<String>) =
        withContext(Dispatchers.IO) {
            synchronized(OverrideMetadataFileLock.monitor) {
                if (orderedIds.isEmpty()) return@synchronized

                val metadataIndex = loadMetadataIndex()
                val sortedUserMetadata = metadataIndex.sortedUserMetadata()
                if (sortedUserMetadata.isEmpty()) return@synchronized

                val userMetadataById = sortedUserMetadata.associateBy(OverrideMetadata::id)
                val reorderedIds = orderedIds.filter(userMetadataById::containsKey)
                if (reorderedIds.isEmpty()) return@synchronized

                val remainingIds =
                    sortedUserMetadata.map(OverrideMetadata::id).filterNot(reorderedIds::contains)
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
                    val updatedIndex = metadataIndex.copy(configs = updatedConfigs)
                    saveMetadataIndex(updatedIndex)
                    val userConfigsById = configsFlow.value.associateBy(OverrideConfig::id)
                    updateConfigsFlowSnapshot(updatedIndex, userConfigsById)
                }
            }
        }

    private fun loadUserConfigs(): List<OverrideConfig> {
        // Never short-circuit on a missing configsDir — imported overrides live in metadata and
        // may still resolve after the directory is recreated (e.g. first built-in materialize).
        if (!configsDir.exists()) {
            configsDir.mkdirs()
        }
        return loadMetadataIndex().sortedUserMetadata().mapNotNull { metadata ->
            if (isInternalRuntimeConfig(metadata.id) || BuiltInOverrideCatalog.isBuiltIn(metadata.id)) {
                return@mapNotNull null
            }
            loadConfigContent(metadata)
        }
    }

    private fun isUserOwnedConfig(config: OverrideConfig): Boolean =
        config.id != OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID &&
            !BuiltInOverrideCatalog.isBuiltIn(config.id) &&
            !isInternalRuntimeConfig(config.id)

    private fun loadBuiltInConfig(id: String): OverrideConfig? {
        val def = BuiltInOverrideCatalog.find(id) ?: return null
        // Prefer the materialized file (may include local edits); fall back to reading the APK asset
        // so the list never goes empty when file materialization fails.
        val file = materializeBuiltInFile(def)
        val content =
            file?.let { runCatching { it.readText() }.getOrNull() }
                ?: readBuiltInAsset(def)
                ?: return null
        val now = System.currentTimeMillis()
        return OverrideConfig(
            id = def.id,
            name = def.name,
            description = def.description,
            contentType = def.contentType,
            content = content,
            createdAt = now,
            updatedAt = file?.lastModified()?.takeIf { it > 0L } ?: now,
        )
    }

    private fun readBuiltInAsset(def: BuiltInOverrideDefinition): String? =
        runCatching {
                context.assets.open(def.assetPath).bufferedReader().use { it.readText() }
            }
            .onFailure { error ->
                Timber.w(error, "Failed to read built-in override asset: %s", def.assetPath)
            }
            .getOrNull()

    /**
     * Copy the asset into `configsDir` if missing so fork/exec override resolution can open a real
     * path. Existing files (including user edits) are preserved.
     */
    private fun materializeBuiltInFile(id: String): File? {
        val def = BuiltInOverrideCatalog.find(id) ?: return null
        return materializeBuiltInFile(def)
    }

    private fun materializeBuiltInFile(def: BuiltInOverrideDefinition): File? {
        val target = resolveConfigFile(def.id, def.contentType)
        if (target.exists()) return target
        return runCatching {
                configsDir.mkdirs()
                context.assets.open(def.assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
            .onFailure { error ->
                Timber.w(error, "Failed to materialize built-in override: %s", def.id)
            }
            .getOrNull()
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

    private fun loadMetadataIndex(): MetadataIndex =
        synchronized(OverrideMetadataFileLock.monitor) {
            val metadataIndex =
                if (!metadataFile.exists()) {
                    MetadataIndex()
                } else {
                    runCatching {
                            YamlCodec.decode(MetadataIndex.serializer(), metadataFile.readText())
                        }
                        .getOrElse { error ->
                            Timber.w(
                                error,
                                "Failed to decode override metadata: %s",
                                metadataFile.absolutePath,
                            )
                            MetadataIndex()
                        }
                }
            val sanitizedIndex = sanitizeMetadataIndex(metadataIndex)
            val normalizedIndex = sanitizedIndex.normalizeUserSortOrders()
            if (normalizedIndex != metadataIndex) {
                saveMetadataIndex(normalizedIndex)
            }
            normalizedIndex
        }

    private fun saveMetadataIndex(index: MetadataIndex) {
        synchronized(OverrideMetadataFileLock.monitor) {
            overridesDir.mkdirs()
            metadataFile.writeText(YamlCodec.encode(MetadataIndex.serializer(), index))
        }
    }

    private fun resolveConfigFile(id: String, contentType: OverrideContentType): File =
        configsDir.resolve("$id.${contentType.extension}")

    private fun findConfigFile(metadata: OverrideMetadata): File? {
        val expectedFile = resolveConfigFile(metadata.id, metadata.contentType)
        if (expectedFile.exists()) return expectedFile

        return configExtensions
            .asSequence()
            .map { extension -> configsDir.resolve("${metadata.id}.$extension") }
            .firstOrNull(File::exists)
    }

    private fun cleanupStaleConfigFiles(id: String, keepExtension: String? = null) {
        cleanupExtensions.forEach { extension ->
            if (keepExtension != null && extension == keepExtension) {
                return@forEach
            }
            runCatching { configsDir.resolve("$id.$extension").delete() }
        }
    }

    private fun sanitizeMetadataIndex(index: MetadataIndex): MetadataIndex {
        val sanitizedConfigs =
            index.configs.filterValues { metadata ->
                !isLegacySystemPresetId(metadata.id) && !BuiltInOverrideCatalog.isBuiltIn(metadata.id)
            }
        val sanitizedProfileChains =
            index.profileChains.mapValues { (_, binding) ->
                binding.copy(
                    overrideIds =
                        binding.overrideIds.filterNot { id ->
                            isLegacySystemPresetId(id) ||
                                (id.startsWith(OverrideMetadata.ID_PREFIX) &&
                                    id !in sanitizedConfigs)
                        }
                )
            }
        return if (
            sanitizedConfigs == index.configs && sanitizedProfileChains == index.profileChains
        ) {
            index
        } else {
            index.copy(configs = sanitizedConfigs, profileChains = sanitizedProfileChains)
        }
    }

    private fun isLegacySystemPresetId(id: String): Boolean =
        id.startsWith(OverrideMetadata.LEGACY_SYSTEM_PREFIX)

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
