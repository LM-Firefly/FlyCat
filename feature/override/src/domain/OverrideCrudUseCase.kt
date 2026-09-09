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

package com.github.lmfirefly.flycat.feature.override.domain

import com.github.lmfirefly.flycat.core.contract.OverrideApplier
import com.github.lmfirefly.flycat.core.contract.OverrideConfigRepository
import com.github.lmfirefly.flycat.core.contract.ProfileBindingReader
import com.github.lmfirefly.flycat.core.contract.ProfileStoreReader
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.override.OverrideContentType
import com.github.lmfirefly.flycat.core.model.override.OverrideMetadata
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import com.github.lmfirefly.flycat.locale.FlyTxt
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import timber.log.Timber

/**
 * Encapsulates override config CRUD, import, and binding business logic
 * extracted from OverrideConfigViewModel.
 */
class OverrideCrudUseCase(
    private val configRepo: OverrideConfigRepository,
    private val bindingReader: ProfileBindingReader,
    private val activeProfileOverrideApplier: OverrideApplier,
    private val profileStore: ProfileStoreReader,
) {
    companion object {
        private const val NETWORK_IMPORT_CONNECT_TIMEOUT_MS = 15_000
        private const val NETWORK_IMPORT_READ_TIMEOUT_MS = 30_000
    }

    /** Load all configs (built-in + user). */
    suspend fun loadConfigs(): Pair<List<OverrideConfig>, List<OverrideConfig>> {
        val builtIns = configRepo.getBuiltInConfigs()
        val users = configRepo.getUserConfigs()
        return builtIns to users
    }

    /** Get config content by ID. */
    suspend fun getConfigContent(configId: String): String? = configRepo.getConfigContent(configId)

    /** Save config content and reapply if the active profile uses it. */
    suspend fun saveConfigContent(configId: String, content: String): Boolean {
        val saved = configRepo.saveConfigContent(configId, content)
        if (!saved) return false
        activeProfileOverrideApplier.reapplyActiveProfileIfUsingOverride(configId)
        return true
    }

    /** Create a new override config. Returns the created config. */
    suspend fun createConfig(name: String, description: String? = null, contentType: OverrideContentType): OverrideConfig {
        val now = System.currentTimeMillis()
        val config = OverrideConfig(
            id = OverrideMetadata.generateId(),
            name = name,
            description = description,
            contentType = contentType,
            content = "",
            createdAt = now,
            updatedAt = now,
        )
        configRepo.save(config)
        return config
    }

    /** Delete an override config and resync runtime if needed. */
    suspend fun deleteConfig(id: String): Boolean {
        val shouldResyncRuntime = activeProfileOverrideApplier.isActiveProfileUsingOverride(id)
        val deleted = configRepo.delete(id)
        if (deleted && shouldResyncRuntime) {
            activeProfileOverrideApplier.reapplyActiveProfileOverride()
        }
        return deleted
    }

    /** Duplicate an override config. Returns the duplicated config or null. */
    suspend fun duplicateConfig(id: String): OverrideConfig? = configRepo.duplicate(id)

    /** Reorder user configs by ID list. */
    suspend fun reorderUserConfigs(orderedIds: List<String>) {
        configRepo.reorderUserConfigs(orderedIds)
    }

    /**
     * Fetch content from a URL and import it as an override config.
     * This is the core network import logic extracted from the ViewModel.
     */
    suspend fun importConfigFromUrl(rawUrl: String): Result<OverrideConfig> = runCatching {
        val url = URL(rawUrl.trim())
        require(url.protocol.equals("http", ignoreCase = true) ||
            url.protocol.equals("https", ignoreCase = true)) {
            FlyTxt.Override.Import.InvalidUrl
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_IMPORT_CONNECT_TIMEOUT_MS
            readTimeout = NETWORK_IMPORT_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val responseCode = connection.responseCode
            require(responseCode in 200..299) {
                FlyTxt.Override.Import.HttpError.format(responseCode)
            }
            val contentTypeHeader = connection.contentType.orEmpty()
            val sourceName = resolveNetworkImportSourceName(
                url = url,
                contentDisposition = connection.getHeaderField("Content-Disposition"),
                contentType = contentTypeHeader,
            )
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val inferredContentType =
                OverrideContentType.fromFileName(sourceName)
                    ?: inferContentTypeFromHeader(contentTypeHeader)
                    ?: inferContentTypeFromContent(content)
            val normalizedSourceName = ensureSourceNameExtension(sourceName, inferredContentType)
            val config = buildImportConfigInternal(content = content, sourceName = normalizedSourceName, fallbackContentType = inferredContentType)
            configRepo.save(config)
            config
        } finally {
            connection.disconnect()
        }
    }

    /** Validate and build an import config (non-suspend). Does NOT save to repo. */
    fun buildImportConfig(content: String, sourceName: String?): Result<OverrideConfig> = runCatching {
        buildImportConfigInternal(content = content, sourceName = sourceName, fallbackContentType = null)
    }

    /** Save a config to the repo. */
    suspend fun saveConfig(config: OverrideConfig) {
        configRepo.save(config)
    }

    /** Import config from raw content string (validate + save). */
    suspend fun importConfig(content: String, sourceName: String?): Result<OverrideConfig> = runCatching {
        val config = buildImportConfigInternal(content = content, sourceName = sourceName, fallbackContentType = null)
        configRepo.save(config)
        config
    }

    /** Load the apply snapshot for binding an override to profiles. */
    suspend fun loadApplySnapshot(overrideId: String): OverrideApplySnapshot {
        val imported = profileStore.loadImported()
        val profiles = imported.map { imp ->
            Profile(
                uuid = imp.uuid, name = imp.name, type = imp.type, source = imp.source,
                active = false, interval = imp.interval, upload = imp.upload, download = imp.download,
                total = imp.total, expire = imp.expire, updatedAt = imp.createdAt, ageSecretKey = imp.ageSecretKey,
            )
        }
        val selectedProfileIds = mutableSetOf<String>()
        for (profile in profiles) {
            val profileId = profile.uuid.toString()
            val isBound = bindingReader.getBinding(profileId)?.overrideIds?.contains(overrideId) == true
            if (isBound) selectedProfileIds += profileId
        }
        return OverrideApplySnapshot(overrideId = overrideId, profiles = profiles, selectedProfileIds = selectedProfileIds)
    }

    /** Apply an override to the selected profiles and resync runtime. */
    suspend fun applyOverrideToProfiles(overrideId: String, selectedProfileIds: Set<String>) {
        val allProfiles = profileStore.loadImported()
        val allProfileIds = allProfiles.map { it.uuid.toString() }.toSet()

        for (profileId in allProfileIds) {
            val current = bindingReader.getBinding(profileId)
            val currentIds = current?.overrideIds?.toSet() ?: emptySet()
            val shouldBeBound = profileId in selectedProfileIds
            val isBound = overrideId in currentIds
            if (shouldBeBound && !isBound) {
                val updated = (current ?: ProfileBinding.disabled(profileId)).addOverride(overrideId)
                bindingReader.setBinding(updated)
            } else if (!shouldBeBound && isBound) {
                val updated = current!!.removeOverride(overrideId)
                bindingReader.setBinding(updated)
            }
        }
        activeProfileOverrideApplier.reapplyActiveProfileIfUsingOverride(overrideId)
    }

    /** Load usage counts for all configs. */
    suspend fun loadUsageCounts(configs: List<OverrideConfig>): Map<String, Int> {
        val countMap = mutableMapOf<String, Int>()
        for (config in configs) {
            countMap[config.id] = bindingReader.getOverrideUsageCount(config.id)
        }
        return countMap
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun buildImportConfigInternal(
        content: String,
        sourceName: String?,
        fallbackContentType: OverrideContentType?,
    ): OverrideConfig {
        val contentType = OverrideContentType.fromFileName(sourceName) ?: fallbackContentType
            ?: throw IllegalArgumentException(FlyTxt.Override.Import.Failed.format(FlyTxt.Override.Import.UnsupportedType))
        if (contentType == OverrideContentType.JavaScript && content.isBlank()) {
            throw IllegalArgumentException(FlyTxt.Override.Import.Failed.format(FlyTxt.Override.Import.EmptyJavaScript))
        }
        return OverrideConfig(
            id = OverrideMetadata.generateId(),
            name = normalizeImportedConfigSourceName(sourceName) ?: FlyTxt.Override.Save.ImportDefaultName,
            description = null,
            contentType = contentType,
            content = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    data class OverrideApplySnapshot(
        val overrideId: String,
        val profiles: List<Profile>,
        val selectedProfileIds: Set<String>,
    )
}

// ── Private utility functions (file-local) ───────────────────────────────────

private fun resolveNetworkImportSourceName(url: URL, contentDisposition: String?, contentType: String): String {
    parseFilenameFromContentDisposition(contentDisposition)?.let { return it }
    val pathName = url.path.substringAfterLast('/').takeIf(String::isNotBlank)
        ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }?.trim().orEmpty()
    if (pathName.isNotBlank()) return pathName
    val extension = inferContentTypeFromHeader(contentType)?.extension ?: OverrideContentType.Yaml.extension
    return "${url.host.ifBlank { FlyTxt.Override.Save.ImportDefaultName }}.$extension"
}

private fun parseFilenameFromContentDisposition(contentDisposition: String?): String? {
    val value = contentDisposition?.takeIf(String::isNotBlank) ?: return null
    val encodedMatch = Regex("""filename\*=([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(value)
    if (encodedMatch != null) {
        val charset = encodedMatch.groupValues[1].ifBlank { Charsets.UTF_8.name() }
        val encodedName = encodedMatch.groupValues[2].trim().trim('"', '\'')
        return runCatching { URLDecoder.decode(encodedName, charset).trim() }.getOrNull()?.takeIf(String::isNotBlank)
    }
    return Regex("""filename=([^;]+)""", RegexOption.IGNORE_CASE).find(value)
        ?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')?.takeIf(String::isNotBlank)
}

private fun inferContentTypeFromHeader(contentType: String): OverrideContentType? {
    val normalized = contentType.lowercase(Locale.ROOT)
    return when {
        "javascript" in normalized || "ecmascript" in normalized -> OverrideContentType.JavaScript
        "yaml" in normalized || "yml" in normalized -> OverrideContentType.Yaml
        else -> null
    }
}

private fun inferContentTypeFromContent(content: String): OverrideContentType =
    if (content.trimStart().startsWith("function") || "module.exports" in content) OverrideContentType.JavaScript
    else OverrideContentType.Yaml

private fun ensureSourceNameExtension(sourceName: String, contentType: OverrideContentType): String =
    if (OverrideContentType.fromFileName(sourceName) != null) sourceName
    else "$sourceName.${contentType.extension}"

internal fun normalizeImportedConfigSourceName(sourceName: String?): String? {
    var normalizedName = sourceName?.trim()?.takeIf(String::isNotBlank) ?: return null
    normalizedName = normalizedName.substringBeforeLast('.')
    return normalizedName.takeIf(String::isNotBlank)
}
