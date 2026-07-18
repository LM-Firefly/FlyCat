/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the License.
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

package com.github.lmfirefly.flycat.data.store

import android.content.Context
import com.github.lmfirefly.flycat.core.model.override.MetadataIndex
import com.github.lmfirefly.flycat.core.model.override.OverrideContentType
import com.github.lmfirefly.flycat.core.model.override.OverrideMetadata
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import com.github.lmfirefly.flycat.core.util.YamlCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Single source of truth for `overrides/metadata.yaml`.
 *
 * Both [OverrideConfigStore] and [ProfileBindingStore] delegate all metadata
 * index reads and writes to this store, eliminating the dual-cache data race
 * that previously existed when both stores independently cached, sanitized,
 * and wrote the same YAML file.
 *
 * All mutating operations are serialized via [Mutex] to guarantee atomic
 * read-modify-write semantics.
 */
class MetadataIndexStore(context: Context) {
    private val overridesDir = File(context.filesDir, "overrides")
    private val metadataFile = File(overridesDir, "metadata.yaml")
    private val configsDir = File(overridesDir, "configs")
    private val mutex = Mutex()
    @Volatile
    private var cachedIndex: MetadataIndex? = null
    // ── Read-only access ─────────────────────────────────────────────────
    /**
     * Returns the cached metadata index, loading from disk on first access.
     * The returned snapshot is immutable; callers must not assume it stays
     * current across suspension points.
     */
    suspend fun getIndex(): MetadataIndex = mutex.withLock { loadIndex() }
    /**
     * Synchronous read of the cached index. Returns `null` if the index has
     * not been loaded yet (i.e. [getIndex] was never called).
     */
    fun getCachedIndex(): MetadataIndex? = cachedIndex
    // ── Atomic config mutations ──────────────────────────────────────────
    /**
     * Atomically loads the index, transforms the `configs` map, and saves.
     * Returns the updated index, or the original if [transform] produced no change.
     */
    suspend fun updateConfigs(transform: (Map<String, OverrideMetadata>) -> Map<String, OverrideMetadata>): MetadataIndex = mutex.withLock {
        val index = loadIndex()
        val updatedConfigs = transform(index.configs)
        if (updatedConfigs === index.configs) return@withLock index
        val updated = index.copy(configs = updatedConfigs)
        saveIndex(updated)
        updated
    }
    /**
     * Atomically removes a config entry **and** strips that override ID from
     * every profile binding chain — all in a single write.
     */
    suspend fun removeConfigWithBindings(configId: String): MetadataIndex = mutex.withLock {
        val index = loadIndex()
        val updated = index.remove(configId).removeOverrideFromProfileChains(configId)
        if (updated == index) return@withLock index
        saveIndex(updated)
        updated
    }
    /**
     * Non-suspending variant of [updateConfigs] for use in non-suspend contexts
     * (e.g. [OverrideConfigStore.saveConfigContent]). Operates on the cached index
     * without acquiring the mutex; safe only when the caller is on a single thread
     * or the operation is idempotent.
     */
    fun updateConfigsSync(updatedIndex: MetadataIndex): MetadataIndex {saveIndex(updatedIndex); return updatedIndex}
    // ── Atomic profile-chain mutations ───────────────────────────────────
    /**
     * Atomically loads the index, transforms the `profileChains` map, and saves.
     */
    suspend fun updateProfileChains(transform: (Map<String, ProfileBinding>) -> Map<String, ProfileBinding>): MetadataIndex = mutex.withLock {
        val index = loadIndex()
        val updatedChains = transform(index.profileChains)
        if (updatedChains === index.profileChains) return@withLock index
        val updated = index.copy(profileChains = updatedChains)
        saveIndex(updated)
        updated
    }
    /**
     * Atomically removes [overrideId] from every profile binding chain.
     */
    suspend fun removeOverrideFromAllBindings(overrideId: String): MetadataIndex = updateProfileChains { chains ->
        chains.mapValues { (_, binding) -> binding.removeOverride(overrideId) }
    }
    // ── File resolution helpers ──────────────────────────────────────────
    fun getConfigsDirectory(): File = configsDir
    /**
     * Finds the on-disk file for [overrideId], checking the expected extension
     * first then falling back to all known extensions.
     */
    fun findConfigFile(overrideId: String, contentType: OverrideContentType? = null): File? {
        if (contentType != null) {
            val expected = configsDir.resolve("$overrideId.${contentType.extension}")
            if (expected.exists()) return expected
        }
        return CONFIG_EXTENSIONS.asSequence().map { ext -> configsDir.resolve("$overrideId.$ext") }.firstOrNull(File::exists)
    }
    /**
     * Returns `true` if the config metadata exists in the index **and** its
     * backing file is present on disk, or if the file can be recovered.
     */
    suspend fun canRecoverConfig(configId: String): Boolean {
        val index = getIndex()
        val metadata = index.getById(configId)
        if (metadata != null) return findConfigFile(configId, metadata.contentType) != null
        // Check if any file with that ID exists (recovery scenario)
        return CONFIG_EXTENSIONS.any { ext -> configsDir.resolve("$configId.$ext").exists() }
    }
    // ── Internal ─────────────────────────────────────────────────────────
    private fun loadIndex(): MetadataIndex {
        cachedIndex?.let { return it }
        val raw = readFromDisk()
        val recovered = recoverConfigsIfNeeded(raw)
        val sanitized = sanitize(recovered)
        val normalized = sanitized.normalizeUserSortOrders()
        if (normalized != raw) { saveIndex(normalized) } else { cachedIndex = normalized }
        return normalized
    }
    private fun readFromDisk(): MetadataIndex {
        if (!metadataFile.exists()) return MetadataIndex()
        return runCatching { YamlCodec.decode(MetadataIndex.serializer(), metadataFile.readText()) }.getOrElse { error ->
            Timber.w(error, "Failed to decode override metadata index")
            MetadataIndex()
        }
    }
    private fun saveIndex(index: MetadataIndex) {
        overridesDir.mkdirs()
        metadataFile.writeText(YamlCodec.encode(MetadataIndex.serializer(), index))
        cachedIndex = index
    }
    /**
     * If the index has no configs but config files exist on disk, recover
     * metadata entries from the files.
     */
    private fun recoverConfigsIfNeeded(index: MetadataIndex): MetadataIndex {
        if (index.configs.isNotEmpty()) return index
        if (!configsDir.exists()) return index
        val recovered = configsDir.listFiles()?.asSequence()?.filter(File::isFile)?.mapNotNull { file ->
            val ext = file.extension.lowercase()
            val contentType = OverrideContentType.fromExtension(ext) ?: return@mapNotNull null
            val id = file.nameWithoutExtension.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val ts = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            id to OverrideMetadata(
                id = id, name = id, description = null,
                contentType = contentType, createdAt = ts, updatedAt = ts, sortOrder = 0L,
            )
        }?.toMap()?: emptyMap()
        if (recovered.isEmpty()) return index
        Timber.i("Recovered override metadata index from config files: %d entries", recovered.size)
        return index.copy(configs = recovered)
    }
    /**
     * Strips legacy `preset-*` entries from both configs and profile chains.
     */
    private fun sanitize(index: MetadataIndex): MetadataIndex {
        val configs = index.configs.filterValues { !isLegacyPresetId(it.id) }
        val chains = index.profileChains.mapValues { (_, binding) -> binding.copy(overrideIds = binding.overrideIds.filterNot(::isLegacyPresetId)) }
        return if (configs === index.configs && chains === index.profileChains) index
        else index.copy(configs = configs, profileChains = chains)
    }
    private fun isLegacyPresetId(id: String): Boolean = id.startsWith(OverrideMetadata.LEGACY_SYSTEM_PREFIX)
    companion object { private val CONFIG_EXTENSIONS = setOf("yaml", "yml", "js") }
}
