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

package com.github.yumelira.yumebox.feature.override.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.contract.OverrideApplier
import com.github.yumelira.yumebox.core.contract.OverrideConfigRepository
import com.github.yumelira.yumebox.core.contract.ProfileBindingReader
import com.github.yumelira.yumebox.core.contract.ProfileStoreReader
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.OverrideContentType
import com.github.yumelira.yumebox.core.model.OverrideMetadata
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProfileBinding
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tf.gal.yumebox.locale.FlyTxt
import timber.log.Timber

class OverrideConfigViewModel(
    private val configRepo: OverrideConfigRepository,
    private val bindingReader: ProfileBindingReader,
    private val activeProfileOverrideApplier: OverrideApplier,
    private val profileStore: ProfileStoreReader,
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
                    val now = System.currentTimeMillis()
                    val config =
                        OverrideConfig(
                            id = OverrideMetadata.generateId(),
                            name = name,
                            description = description,
                            contentType = contentType,
                            content = "",
                            createdAt = now,
                            updatedAt = now,
                        )
                    configRepo.save(config)
                    _pendingRevealConfigId.value = config.id
                    refresh()
                }
                .onFailure { error -> Timber.tag(TAG).e(error, "Failed to create override") }
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            runCatching {
                    val shouldResyncRuntime =
                        activeProfileOverrideApplier.isActiveProfileUsingOverride(id)
                    val deleted = configRepo.delete(id)
                    if (deleted && shouldResyncRuntime) {
                        activeProfileOverrideApplier.reapplyActiveProfileOverride()
                    }
                    refresh()
                }
                .onFailure { error -> Timber.tag(TAG).e(error, "Failed to delete override") }
        }
    }

    fun duplicateConfig(id: String) {
        viewModelScope.launch {
            runCatching {
                    val duplicated = configRepo.duplicate(id)
                    if (duplicated != null) {
                        _pendingRevealConfigId.value = duplicated.id
                    }
                    refresh()
                }
                .onFailure { error -> Timber.tag(TAG).e(error, "Failed to duplicate override") }
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
        return importConfig(
            content = content,
            sourceName = sourceName,
            fallbackContentType = null,
        )
    }

    private fun importConfig(
        content: String,
        sourceName: String?,
        fallbackContentType: OverrideContentType?,
    ): Result<OverrideConfig> {
        val contentType =
            OverrideContentType.fromFileName(sourceName) ?: fallbackContentType
                ?: return Result.failure(
                    IllegalArgumentException(
                        FlyTxt.Override.Import.Failed.format(FlyTxt.Override.Import.UnsupportedType)
                    )
                )
        if (contentType == OverrideContentType.JavaScript && content.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    FlyTxt.Override.Import.Failed.format(FlyTxt.Override.Import.EmptyJavaScript)
                )
            )
        }
        val config =
            OverrideConfig(
                id = OverrideMetadata.generateId(),
                name =
                    normalizeImportedConfigSourceName(sourceName)
                        ?: FlyTxt.Override.Save.ImportDefaultName,
                description = null,
                contentType = contentType,
                content = content,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        viewModelScope.launch {
            runCatching {
                    configRepo.save(config)
                    _pendingRevealConfigId.value = config.id
                    refresh()
                }
                .onFailure { error -> Timber.tag(TAG).e(error, "Failed to import override") }
        }
        return Result.success(config)
    }

    suspend fun importConfigFromUrl(rawUrl: String): Result<OverrideConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
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
                        val sourceName =
                            resolveNetworkImportSourceName(
                                url = url,
                                contentDisposition = connection.getHeaderField("Content-Disposition"),
                                contentType = contentTypeHeader,
                            )
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        val inferredContentType =
                            OverrideContentType.fromFileName(sourceName)
                                ?: inferContentTypeFromHeader(contentTypeHeader)
                                ?: inferContentTypeFromContent(content)
                        val normalizedSourceName =
                            ensureSourceNameExtension(sourceName, inferredContentType)
                        importConfig(
                            content = content,
                            sourceName = normalizedSourceName,
                            fallbackContentType = inferredContentType,
                        ).getOrThrow()
                    } finally {
                        connection.disconnect()
                    }
                }
                .fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { Result.failure(it) },
                )
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
                val imported = profileStore.loadImported()
                val profiles = imported.map { imp ->
                    Profile(
                        uuid = imp.uuid,
                        name = imp.name,
                        type = imp.type,
                        source = imp.source,
                        active = false,
                        interval = imp.interval,
                        upload = imp.upload,
                        download = imp.download,
                        total = imp.total,
                        expire = imp.expire,
                        updatedAt = imp.createdAt,
                        ageSecretKey = imp.ageSecretKey,
                    )
                }
                val selectedProfileIds = mutableSetOf<String>()
                for (profile in profiles) {
                    val profileId = profile.uuid.toString()
                    val isBound =
                        bindingReader.getBinding(profileId)?.overrideIds?.contains(overrideId) == true
                    if (isBound) {
                        selectedProfileIds += profileId
                    }
                }
                OverrideApplySnapshot(
                    overrideId = overrideId,
                    profiles = profiles,
                    selectedProfileIds = selectedProfileIds,
                )
            }
            .onFailure { error ->
                Timber.tag(TAG).e(error, "Failed to load apply snapshot for %s", overrideId)
            }
        }

    suspend fun applyOverrideToProfiles(
        overrideId: String,
        selectedProfileIds: Set<String>,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
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

                val changedProfileIds = mutableSetOf<String>()
                for (profileId in allProfileIds) {
                    val current = bindingReader.getBinding(profileId)
                    val isBound = overrideId in (current?.overrideIds ?: emptyList())
                    val shouldBeBound = profileId in selectedProfileIds
                    if (isBound != shouldBeBound) { changedProfileIds += profileId }
                }

                if (changedProfileIds.isNotEmpty() || selectedProfileIds.isNotEmpty()) {
                    runCatching { activeProfileOverrideApplier.reapplyActiveProfileIfUsingOverride(overrideId) }
                }
                loadUsageCounts()
            }
            .onFailure { error ->
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

private fun resolveNetworkImportSourceName(
    url: URL,
    contentDisposition: String?,
    contentType: String,
): String {
    parseFilenameFromContentDisposition(contentDisposition)?.let { return it }
    val pathName =
        url.path
            .substringAfterLast('/')
            .takeIf(String::isNotBlank)
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            ?.trim()
            .orEmpty()
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
        return runCatching { URLDecoder.decode(encodedName, charset).trim() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
    }
    return Regex("""filename=([^;]+)""", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trim('"', '\'')
        ?.takeIf(String::isNotBlank)
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
    if (content.trimStart().startsWith("function") || "module.exports" in content) {
        OverrideContentType.JavaScript
    } else {
        OverrideContentType.Yaml
    }

private fun ensureSourceNameExtension(
    sourceName: String,
    contentType: OverrideContentType,
): String =
    if (OverrideContentType.fromFileName(sourceName) != null) {
        sourceName
    } else {
        "$sourceName.${contentType.extension}"
    }

internal fun normalizeImportedConfigSourceName(sourceName: String?): String? {
    var normalizedName =
        sourceName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
    val removableSuffixes = listOf(".yaml", ".yml", ".js")
    while (true) {
        val matchedSuffix =
            removableSuffixes.firstOrNull { suffix ->
                normalizedName.length > suffix.length &&
                    normalizedName.endsWith(suffix, ignoreCase = true)
            } ?: break
        normalizedName = normalizedName.dropLast(matchedSuffix.length).trimEnd()
    }
    return normalizedName.takeIf(String::isNotBlank)
}
