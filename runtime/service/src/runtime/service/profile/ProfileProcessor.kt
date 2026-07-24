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

package com.github.yumelira.yumebox.runtime.service.profile

import android.content.Context
import android.net.Uri
import com.github.yumelira.yumebox.core.bridge.Compiler
import com.github.yumelira.yumebox.core.model.CompileRequest
import com.github.yumelira.yumebox.core.model.CompileResult
import com.github.yumelira.yumebox.core.model.FetchStatus
import com.github.yumelira.yumebox.core.util.PROXY_PROVIDER_SCOPE
import com.github.yumelira.yumebox.core.util.RULE_PROVIDER_SCOPE
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.core.util.profileProviderScopeDir
import com.github.yumelira.yumebox.runtime.api.FetchObserver
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.service.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.session.CompiledConfigPipeline
import com.github.yumelira.yumebox.runtime.service.util.importedDir
import com.github.yumelira.yumebox.runtime.service.util.sendProfileChanged
import com.tencent.mmkv.MMKV
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()

    private val compilerJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private data class SubscriptionInfo(
        val upload: Long? = null,
        val download: Long? = null,
        val total: Long? = null,
        val expire: Long? = null,
        val title: String? = null,
        val filename: String? = null,
        val updateInterval: Long? = null,
    )

    private data class UpdateSnapshot(
        val imported: Imported,
        val hasCommittedConfig: Boolean,
    )

    private data class ExternalProvider(
        val name: String,
        val url: String,
        val target: File,
        val headers: List<Pair<String, String>>,
    )

    /**
     * Downloads a subscription URL to `stagingDir/config.yaml` with progress, parsing the
     * `subscription-userinfo` header into a SubscriptionInfo (deep config validation happens at
     * compile time).
     */
    private suspend fun fetchSubscription(
        stagingDir: File,
        url: String,
        onStatus: (FetchStatus) -> Unit,
    ) {
        onStatus(FetchStatus(FetchStatus.Action.FetchConfiguration, listOf(url), 0, 1))
        HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 60_000 }
                followRedirects = true
            }
            .use { client ->
                // Airports gate the real config on a recognized Clash-client User-Agent; "YumeBox"
                // gets a
                // crippled response, so send the user's custom UA or the Sub-Store client's
                // default.
                val response =
                    client.get(url) {
                        header(HttpHeaders.UserAgent, resolveSubscriptionUserAgent())
                    }
                check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                val body = response.bodyAsText()
                stagingDir.mkdirs()
                File(stagingDir, "config.yaml").writeText(body)
                onStatus(FetchStatus(FetchStatus.Action.Verifying, emptyList(), 1, 1))

                val headers = response.headers
                val fields =
                    headers["subscription-userinfo"]
                        ?.split(';')
                        ?.mapNotNull { part ->
                            val kv = part.split('=', limit = 2)
                            if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
                        }
                        ?.toMap()
                        .orEmpty()
                val title =
                    decodeSubscriptionTitle(
                        headers["profile-title"] ?: headers["subscription-title"]
                    )
                val filename = parseContentDispositionFilename(headers["content-disposition"])
                val interval =
                    (headers["profile-update-interval"] ?: headers["subscription-update-interval"])
                        ?.trim()
                        ?.toLongOrNull()

                // Emit the subscription-info status unconditionally: a server may send
                // profile-title
                // (airport name) without subscription-userinfo, and vice versa.
                onStatus(
                    FetchStatus(
                        action = FetchStatus.Action.SubscriptionInfo,
                        args = emptyList(),
                        progress = 1,
                        max = 1,
                        subUpload = fields["upload"]?.toLongOrNull(),
                        subDownload = fields["download"]?.toLongOrNull(),
                        subTotal = fields["total"]?.toLongOrNull(),
                        // `expire` here is a Unix timestamp in SECONDS, but the UI renders
                        // Profile.expire
                        // as epoch MILLIS — convert, or a real future date reads as 1970
                        // ("expired").
                        subExpire =
                            fields["expire"]?.toLongOrNull()?.takeIf { it > 0 }?.let { it * 1000L },
                        subUpdateInterval = interval,
                        subTitle = title,
                        subFilename = filename,
                    )
                )
            }
    }

    private const val DEFAULT_SUBSCRIPTION_UA = "ClashMetaForAndroid"

    /**
     * Pre-fetches HTTP providers into the same profile-private paths that liboverride emits into
     * the runtime config. Path rewriting goes through Compiler.nativeCompile first (same as the
     * old FetchAndValid → UnmarshalAndPatch → download sequence) so Kotlin never invents a second
     * provider-path scheme. Best-effort: one failed provider must never reject an import.
     */
    private suspend fun fetchExternalProviders(
        context: Context,
        uuid: UUID,
        stagingDir: File,
        profileDir: File,
        ageSecretKey: String?,
        onStatus: (FetchStatus) -> Unit,
    ) {
        val config = stagingDir.resolve("config.yaml")
        if (!config.isFile || config.length() <= 0L) {
            Timber.e("Skip external provider prefetch: missing config.yaml under %s", stagingDir)
            return
        }

        val configText = readConfigText(config)
        onStatus(
            FetchStatus(
                action = FetchStatus.Action.FetchProviders,
                args = emptyList(),
                progress = 0,
                max = 1,
            )
        )

        // Source config is the source of truth for *which* providers to download. liboverride is
        // only used to obtain the rewritten path that runtime will later resolve. Never treat an
        // empty liboverride result as "no providers" — that previously skipped local imports.
        val rewrittenPaths =
            loadLiboverrideProviderPaths(
                context = context,
                uuid = uuid,
                stagingDir = stagingDir,
                profileDir = profileDir,
                ageSecretKey = ageSecretKey,
            )
        val providers =
            collectDownloadableProviders(
                configText = configText,
                stagingDir = stagingDir,
                profileDir = profileDir,
                rewrittenPaths = rewrittenPaths,
            )

        if (providers.isEmpty()) {
            val declaresProviders =
                configText.contains("proxy-providers") || configText.contains("rule-providers")
            val hasHttpUrl = HTTP_URL_IN_TEXT.containsMatchIn(configText)
            if (declaresProviders && hasHttpUrl) {
                Timber.e(
                    "Provider prefetch collected 0 items but config declares providers with http(s) urls " +
                        "(staging=%s size=%d rewritten=%d)",
                    stagingDir,
                    config.length(),
                    rewrittenPaths.size,
                )
            } else {
                Timber.i(
                    "No downloadable external providers under %s (declaresProviders=%s hasHttpUrl=%s)",
                    stagingDir,
                    declaresProviders,
                    hasHttpUrl,
                )
            }
            return
        }
        Timber.i(
            "Prefetching %d external providers staging=%s profileDir=%s rewritten=%d",
            providers.size,
            stagingDir,
            profileDir,
            rewrittenPaths.size,
        )

        HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 60_000
                }
                followRedirects = true
            }
            .use { client ->
                providers.forEachIndexed { index, provider ->
                    onStatus(
                        FetchStatus(
                            action = FetchStatus.Action.FetchProviders,
                            args = listOf(provider.name),
                            progress = index + 1,
                            max = providers.size,
                        )
                    )
                    runCatching { downloadExternalProvider(client, provider) }
                        .onSuccess {
                            Timber.i(
                                "Downloaded provider %s -> %s (%d bytes)",
                                provider.name,
                                provider.target,
                                provider.target.length(),
                            )
                        }
                        .onFailure { error ->
                            Timber.e(
                                error,
                                "Provider download failed: name=%s url=%s target=%s",
                                provider.name,
                                provider.url,
                                provider.target,
                            )
                        }
                }
            }
    }

    /**
     * Compiles the staged config and returns a map of `scope:name -> relative path under
     * providers/{scope}/`. Used only for path alignment with runtime; URL discovery always reads
     * the source config so a liboverride empty result cannot skip downloads.
     */
    private fun loadLiboverrideProviderPaths(
        context: Context,
        uuid: UUID,
        stagingDir: File,
        profileDir: File,
        ageSecretKey: String?,
    ): Map<String, String> {
        if (!ageSecretKey.isNullOrBlank()) {
            Timber.i("Encrypted profile %s: skip liboverride path map, use source paths", uuid)
            return emptyMap()
        }
        return runCatching {
                val overrides =
                    runCatching {
                            CompiledConfigPipeline(context).resolveOverrideSpecs(uuid.toString())
                        }
                        .onFailure { Timber.w(it, "Resolve overrides for provider path map failed") }
                        .getOrDefault(emptyList())
                val request =
                    CompileRequest(
                        profileUuid = uuid.toString(),
                        profileDir = profileDir.absolutePath,
                        profilePath = stagingDir.resolve("config.yaml").absolutePath,
                        overrides = overrides,
                        outputPath = "",
                        ageSecretKey = null,
                    )
                val result =
                    compilerJson.decodeFromString(
                        CompileResult.serializer(),
                        Compiler.nativeCompile(
                            compilerJson.encodeToString(CompileRequest.serializer(), request)
                        ),
                    )
                check(result.success) {
                    result.error ?: "liboverride compile failed for provider path map"
                }
                extractProviderPathMap(result.finalYaml)
            }
            .onFailure { Timber.w(it, "liboverride provider path map failed; using source paths") }
            .getOrDefault(emptyMap())
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractProviderPathMap(finalYaml: String): Map<String, String> {
        val root = parseConfigMap(finalYaml) ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        listOf(
                "proxy-providers" to PROXY_PROVIDER_SCOPE,
                "rule-providers" to RULE_PROVIDER_SCOPE,
            )
            .forEach { (field, scope) ->
                val definitions = root[field] as? Map<*, *> ?: return@forEach
                definitions.forEach { (rawName, rawDefinition) ->
                    val name = rawName?.toString()?.trim().orEmpty()
                    val definition = rawDefinition as? Map<*, *> ?: return@forEach
                    if (name.isEmpty()) return@forEach
                    val rewritten = definition["path"]?.toString()?.trim().orEmpty()
                    val relative = providerRelativeFromRewritten(rewritten, scope) ?: return@forEach
                    out["$scope:$name"] = relative
                }
            }
        return out
    }

    private fun providerRelativeFromRewritten(rewrittenPath: String, scope: String): String? {
        val normalized = rewrittenPath.replace('\\', '/').trim()
        if (normalized.isEmpty()) return null
        val match = PROVIDER_PATH_TAIL.find(normalized) ?: return null
        if (match.groupValues[1] != scope) return null
        return match.groupValues[2].takeIf { it.isNotBlank() }
    }

    /**
     * Discovers downloadable providers from the source config. Prefer liboverride-rewritten
     * relative paths when available; otherwise use the same normalization runtime expects.
     */
    private fun collectDownloadableProviders(
        configText: String,
        stagingDir: File,
        profileDir: File,
        rewrittenPaths: Map<String, String>,
    ): List<ExternalProvider> {
        val root = parseConfigMap(configText)
        val fromYaml =
            if (root != null) {
                collectProvidersFromRoot(root, stagingDir, profileDir, rewrittenPaths)
            } else {
                emptyList()
            }
        if (fromYaml.isNotEmpty()) return fromYaml

        val fromScan = collectProvidersByTextScan(configText, stagingDir, profileDir, rewrittenPaths)
        if (fromScan.isNotEmpty()) {
            Timber.w(
                "YAML map parse found 0 providers; text scan recovered %d downloadable providers",
                fromScan.size,
            )
        }
        return fromScan
    }

    private fun parseConfigMap(content: String): Map<String, Any?>? =
        runCatching {
                val loaded = YamlCodec.loadMap(content)
                loaded.takeIf { it.isNotEmpty() }
            }
            .onFailure { Timber.w(it, "YAML parse failed for provider collection") }
            .getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun collectProvidersFromRoot(
        root: Map<String, Any?>,
        stagingDir: File,
        profileDir: File,
        rewrittenPaths: Map<String, String>,
    ): List<ExternalProvider> = buildList {
        listOf(
                "proxy-providers" to PROXY_PROVIDER_SCOPE,
                "rule-providers" to RULE_PROVIDER_SCOPE,
            )
            .forEach { (field, scope) ->
                val definitions = asStringKeyedMap(root[field]) ?: return@forEach
                definitions.forEach { (name, rawDefinition) ->
                    val definition = asStringKeyedMap(rawDefinition) ?: return@forEach
                    val provider =
                        buildExternalProvider(
                            name = name,
                            definition = definition,
                            scope = scope,
                            stagingDir = stagingDir,
                            profileDir = profileDir,
                            rewrittenPaths = rewrittenPaths,
                        ) ?: return@forEach
                    add(provider)
                }
            }
    }

    private fun buildExternalProvider(
        name: String,
        definition: Map<String, Any?>,
        scope: String,
        stagingDir: File,
        profileDir: File,
        rewrittenPaths: Map<String, String>,
    ): ExternalProvider? {
        val type = definition["type"]?.toString()?.trim().orEmpty()
        val url = definition["url"]?.toString().orEmpty().trim().trim('"', '\'').trim()
        // Old FetchAndValid: any provider with url+path after patch is downloadable.
        // Inline payloads are already in-config; everything else with an http(s) URL is fetched.
        if (!url.isHttpUrl() || type.equals("inline", ignoreCase = true)) return null

        val extension =
            if (
                scope == RULE_PROVIDER_SCOPE &&
                    definition["format"]?.toString()?.equals("mrs", ignoreCase = true) == true
            ) {
                "mrs"
            } else {
                "yaml"
            }

        val relative =
            rewrittenPaths["$scope:$name"]
                ?: providerRelativePath(
                    path = definition["path"]?.toString().orEmpty(),
                    url = url,
                    extension = extension,
                    profileProviderDir = profileProviderScopeDir(profileDir, scope),
                )
        val target = profileProviderScopeDir(stagingDir, scope).resolve(relative)
        val resolvedName = name.trim().ifEmpty { target.name }
        return ExternalProvider(
            name = resolvedName,
            url = url,
            target = target,
            headers = providerHeaders(definition),
        )
    }

    /**
     * Last-resort extractor when SnakeYAML cannot load the document as a map (merge keys / odd
     * tags). Walks proxy-providers / rule-providers blocks and pulls `url:` entries.
     */
    private fun collectProvidersByTextScan(
        configText: String,
        stagingDir: File,
        profileDir: File,
        rewrittenPaths: Map<String, String>,
    ): List<ExternalProvider> {
        val out = linkedMapOf<String, ExternalProvider>()
        listOf(
                "proxy-providers" to PROXY_PROVIDER_SCOPE,
                "rule-providers" to RULE_PROVIDER_SCOPE,
            )
            .forEach { (field, scope) ->
                val section = extractTopLevelSection(configText, field) ?: return@forEach
                var currentName: String? = null
                var currentUrl: String? = null
                var currentPath: String? = null
                var currentFormat: String? = null
                var currentType: String? = null

                fun flush() {
                    val name = currentName?.trim().orEmpty()
                    val url = currentUrl.orEmpty().trim().trim('"', '\'').trim()
                    val type = currentType.orEmpty().trim()
                    if (name.isNotEmpty() && url.isHttpUrl() && !type.equals("inline", true)) {
                        val extension =
                            if (
                                scope == RULE_PROVIDER_SCOPE &&
                                    currentFormat?.equals("mrs", ignoreCase = true) == true
                            ) {
                                "mrs"
                            } else {
                                "yaml"
                            }
                        val relative =
                            rewrittenPaths["$scope:$name"]
                                ?: providerRelativePath(
                                    path = currentPath.orEmpty(),
                                    url = url,
                                    extension = extension,
                                    profileProviderDir =
                                        profileProviderScopeDir(profileDir, scope),
                                )
                        val target = profileProviderScopeDir(stagingDir, scope).resolve(relative)
                        out["$scope:$name"] =
                            ExternalProvider(
                                name = name,
                                url = url,
                                target = target,
                                headers = emptyList(),
                            )
                    }
                    currentUrl = null
                    currentPath = null
                    currentFormat = null
                    currentType = null
                }

                section.lineSequence().forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    val indent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                    val content = line.trim()
                    if (content.isEmpty() || content.startsWith("#")) return@forEach

                    // New provider entry at indent 2 (or any non-nested map key with trailing ':')
                    val nameMatch = PROVIDER_ENTRY_NAME.find(content)
                    if (indent <= 2 && nameMatch != null && !content.contains(": ") && content.endsWith(":")) {
                        flush()
                        currentName = nameMatch.groupValues[1].trim().trim('"', '\'')
                        return@forEach
                    }
                    if (currentName == null) return@forEach

                    val kv = content.split(":", limit = 2)
                    if (kv.size != 2) return@forEach
                    val key = kv[0].trim()
                    val value = kv[1].trim().trim('"', '\'')
                    when (key) {
                        "url" -> currentUrl = value
                        "path" -> currentPath = value
                        "format" -> currentFormat = value
                        "type" -> currentType = value
                    }
                }
                flush()
            }
        return out.values.toList()
    }

    private fun extractTopLevelSection(configText: String, key: String): String? {
        val header = Regex("^$key\\s*:\\s*(?:#.*)?$", RegexOption.MULTILINE).find(configText)
            ?: return null
        val start = header.range.last + 1
        val rest = configText.substring(start)
        val nextTop = Regex("^[A-Za-z0-9_.-]+\\s*:", RegexOption.MULTILINE).find(rest)
        return if (nextTop == null) rest else rest.substring(0, nextTop.range.first)
    }

    @Suppress("UNCHECKED_CAST")
    private fun asStringKeyedMap(value: Any?): Map<String, Any?>? {
        val map = value as? Map<*, *> ?: return null
        if (map.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Any?>(map.size)
        map.forEach { (rawKey, rawValue) ->
            val key = rawKey?.toString()?.trim().orEmpty()
            if (key.isNotEmpty()) out[key] = rawValue
        }
        return out
    }

    private fun providerRelativePath(
        path: String,
        url: String,
        extension: String,
        profileProviderDir: File,
    ): String {
        val raw = path.trim()
        val segments =
            if (raw.isBlank()) {
                emptyList()
            } else if (File(raw).isAbsolute) {
                absoluteProviderPathTail(raw, profileProviderDir) ?: listOf(File(raw).name)
            } else {
                raw.replace('\\', '/').split('/')
            }
        val cleaned = mutableListOf<String>()
        segments.forEach { segment ->
            when (segment) {
                "",
                "." -> Unit
                ".." -> if (cleaned.isNotEmpty()) cleaned.removeAt(cleaned.lastIndex)
                else -> cleaned += segment
            }
        }
        while (cleaned.firstOrNull() in providerPathPrefixes) cleaned.removeAt(0)
        val relative = cleaned.joinToString("/")
        val withExtension =
            when {
                relative.isBlank() -> "${sha256(url)}.$extension"
                File(relative).extension.isNotBlank() -> relative
                else -> "$relative.$extension"
            }
        return withExtension
    }

    private fun absoluteProviderPathTail(path: String, providerDir: File): List<String>? {
        val normalizedPath = path.replace('\\', '/').trimEnd('/')
        val normalizedBase = providerDir.absolutePath.replace('\\', '/').trimEnd('/')
        if (!normalizedPath.startsWith("$normalizedBase/")) return null
        return normalizedPath.removePrefix("$normalizedBase/").split('/')
    }

    private fun providerHeaders(definition: Map<*, *>): List<Pair<String, String>> {
        val rawHeaders = definition["header"] as? Map<*, *> ?: return emptyList()
        return buildList {
            rawHeaders.forEach { (rawName, rawValue) ->
                val name = rawName?.toString()?.trim().orEmpty()
                if (name.isBlank()) return@forEach
                when (rawValue) {
                    is Iterable<*> ->
                        rawValue.forEach { value ->
                            value?.toString()?.takeIf(String::isNotBlank)?.let { add(name to it) }
                        }

                    else ->
                        rawValue?.toString()?.takeIf(String::isNotBlank)?.let { add(name to it) }
                }
            }
        }
    }

    private suspend fun downloadExternalProvider(client: HttpClient, provider: ExternalProvider) {
        val temporary = File(provider.target.parentFile, ".${provider.target.name}.download")
        provider.target.parentFile?.mkdirs()
        temporary.delete()
        try {
            client
                .get(provider.url) {
                    header(HttpHeaders.UserAgent, resolveSubscriptionUserAgent())
                    provider.headers.forEach { (name, value) -> header(name, value) }
                }
                .let { response ->
                    check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                    response.bodyAsChannel().toInputStream().use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                }
            temporary.copyTo(provider.target, overwrite = true)
        } finally {
            temporary.delete()
        }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

    private fun readConfigText(config: File): String =
        config.readText().removePrefix("\uFEFF")

    /**
     * Materialize a local profile's config.yaml into [stagingDir], matching the old native
     * `FetchAndValid(force=true)` path that re-opened `content://` / `file://` sources.
     *
     * Prefer the original source URI when still readable so a missing or partial copy from
     * [copyProfileImport] cannot silently leave staging without a config (and therefore without
     * provider prefetch). Fall back to whatever is already staged.
     */
    private fun materializeLocalConfig(context: Context, stagingDir: File, imported: Imported) {
        val config = stagingDir.resolve("config.yaml")
        val temporary = stagingDir.resolve(".config.yaml.local")
        val source = imported.source.trim()
        if (source.isEmpty()) {
            if (!config.isFile || config.length() <= 0L) {
                Timber.w("Local profile %s has empty source and no staged config", imported.uuid)
            }
            return
        }

        val reloaded =
            runCatching {
                    stagingDir.mkdirs()
                    temporary.delete()
                    when {
                        source.startsWith("content:", ignoreCase = true) ||
                            source.startsWith("file:", ignoreCase = true) -> {
                            val uri = Uri.parse(source)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                temporary.outputStream().use { output -> input.copyTo(output) }
                                true
                            } ?: false
                        }
                        File(source).isFile -> {
                            File(source).inputStream().use { input ->
                                temporary.outputStream().use { output -> input.copyTo(output) }
                            }
                            true
                        }
                        else -> false
                    }
                        .also { copied ->
                            if (copied) {
                                check(temporary.isFile && temporary.length() > 0L) {
                                    "Local profile source is empty: $source"
                                }
                                temporary.copyTo(config, overwrite = true)
                            }
                        }
                }
                .onFailure { error ->
                    Timber.w(error, "Failed to re-read local profile source: %s", source)
                }
                .getOrDefault(false)
        temporary.delete()

        if (!reloaded && (!config.isFile || config.length() <= 0L)) {
            Timber.w(
                "Local profile %s has no config.yaml after materialize (source=%s)",
                imported.uuid,
                source,
            )
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private val providerPathPrefixes =
        setOf("providers", "provider", "clash", "ruleset", "rules", "proxies")

    private val PROVIDER_PATH_TAIL =
        Regex("""(?:^|/)providers/(proxies|rules)/(.+)$""")

    private val HTTP_URL_IN_TEXT =
        Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    private val PROVIDER_ENTRY_NAME =
        Regex("""^([^:#{}\[\]]+)\s*:$""")

    /** The user's configured User-Agent (settings store), or the airport-recognized default. */
    private fun resolveSubscriptionUserAgent(): String {
        val custom = runCatching {
            MMKV.mmkvWithID("settings", MMKV.MULTI_PROCESS_MODE).decodeString("customUserAgent")
        }
            .getOrNull()
            ?.trim()
        return custom?.takeIf { it.isNotEmpty() } ?: DEFAULT_SUBSCRIPTION_UA
    }

    /**
     * Decodes a `profile-title` header: plain, `base64:…`, RFC 5987 (`UTF-8''…`), or url-encoded.
     */
    private fun decodeSubscriptionTitle(raw: String?): String? {
        val value = raw?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() } ?: return null
        if (value.startsWith("base64:", ignoreCase = true)) {
            return decodeBase64OrNull(value.substringAfter(':')) ?: value
        }
        Regex("""^([^']*)'[^']*'(.*)$""").find(value)?.let { match ->
            val charset = match.groupValues[1].ifBlank { "UTF-8" }
            runCatching { URLDecoder.decode(match.groupValues[2], charset).trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    return it
                }
        }
        runCatching { URLDecoder.decode(value, "UTF-8").trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != value }
            ?.let {
                return it
            }
        return decodeBase64OrNull(value) ?: value
    }

    private fun decodeBase64OrNull(encoded: String): String? {
        val candidate = encoded.trim().trim('"', '\'')
        if (candidate.isBlank() || !candidate.matches(Regex("^[A-Za-z0-9+/=]+$"))) return null
        return runCatching { String(Base64.getDecoder().decode(candidate), Charsets.UTF_8).trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts the filename from a Content-Disposition header — RFC 5987 `filename*=charset''…`
     * (url-decoded) and plain `filename=…`, stopping at the next `;` so trailing params aren't
     * swallowed.
     */
    private fun parseContentDispositionFilename(contentDisposition: String?): String? {
        val cd = contentDisposition?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            if (cd.contains("filename*=", ignoreCase = true)) {
                    Regex("""filename\*=([^']*)'([^']*)'([^;]+)""", RegexOption.IGNORE_CASE)
                        .find(cd)
                        ?.let { match ->
                            val charset = match.groupValues[1].ifBlank { "UTF-8" }
                            val encoded = match.groupValues[3].trim().trim('"', '\'')
                            val safeCharset = runCatching {
                                java.nio.charset.Charset.forName(charset).name()
                            }.getOrDefault("UTF-8")
                            URLDecoder.decode(encoded, safeCharset).trim()
                        }
                } else {
                    Regex("""filename=([^;]+)""", RegexOption.IGNORE_CASE)
                        .find(cd)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                        ?.trim('"', '\'')
                }
                ?.takeIf { it.isNotBlank() }
        }
            .getOrNull()
    }

    private fun FetchStatus.toSubscriptionInfo(): SubscriptionInfo? {
        if (action != FetchStatus.Action.SubscriptionInfo) return null
        return SubscriptionInfo(
            upload = subUpload,
            download = subDownload,
            total = subTotal,
            expire = subExpire,
            title = subTitle,
            filename = subFilename,
            updateInterval = subUpdateInterval,
        )
    }

    private fun resolveSubscriptionName(
        snapshotName: String,
        snapshotSource: String,
        subInfo: SubscriptionInfo?,
    ): String {
        if (!ProfileNameUtils.isAutoGeneratedProfileName(snapshotName)) return snapshotName

        val headerTitle = subInfo?.title?.takeIf { it.isNotBlank() }
        val filename = subInfo?.filename?.substringBeforeLast(".")?.takeIf { it.isNotBlank() }
        val sourceName = ProfileNameUtils.extractSourceBaseName(snapshotSource)

        if (headerTitle != null) return headerTitle
        if (filename != null) return filename
        if (sourceName != null) return sourceName
        return snapshotName
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun update(context: Context, uuid: UUID, callback: FetchObserver?) {
        withContext(Dispatchers.IO + NonCancellable) {
            processLock.withLock {
                val targetDir = context.importedDir.resolve(uuid.toString())
                val stagingDir =
                    context.cacheDir.resolve("profile-staging").resolve(uuid.toString())
                val snapshot = profileLock.withLock {
                    val imported =
                        ImportedDao.queryByUUID(uuid)
                            ?: throw IllegalArgumentException("profile $uuid not found")

                    stagingDir.deleteRecursively()
                    stagingDir.mkdirs()

                    if (targetDir.exists()) {
                        targetDir.copyRecursively(stagingDir, overwrite = true)
                    }

                    UpdateSnapshot(
                        imported = imported,
                        // Sentinel "this profile already committed a config", used below to decide
                        // whether a failed update may roll back (delete) it. The source file is
                        // config.yaml (runtime.yaml is no longer produced by any path).
                        hasCommittedConfig = targetDir.resolve("config.yaml").isFile,
                    )
                }

                var cb = callback
                var subInfo: SubscriptionInfo? = null

                try {
                    // Age secret key is applied per-profile inside the compiler (compile request),
                    // not
                    // as global core state — nothing to set here.
                    // Only Url profiles are fetched: a File profile's config.yaml was already
                    // written at
                    // import time, so HTTP-getting its local source would just clobber it.
                    if (snapshot.imported.type == Profile.Type.Url) {
                        fetchSubscription(stagingDir, snapshot.imported.source) { status ->
                            val fetchedSubInfo = status.toSubscriptionInfo()
                            if (fetchedSubInfo != null) {
                                subInfo = fetchedSubInfo
                                return@fetchSubscription
                            }
                            try {
                                cb?.updateStatus(status)
                            } catch (error: Exception) {
                                // fault barrier: the observer may live across a binder; reporting
                                // failures must not abort the profile fetch itself.
                                cb = null
                                Timber.w(error, "Report fetch status: %s", error.message)
                            }
                        }
                    } else {
                        // Local/file profiles still need provider prefetch. Re-materialize the
                        // original local source into staging (old native force-fetch behavior)
                        // so a missing copy cannot skip every proxy/rule provider download.
                        materializeLocalConfig(context, stagingDir, snapshot.imported)
                    }

                    fetchExternalProviders(
                        context = context,
                        uuid = snapshot.imported.uuid,
                        stagingDir = stagingDir,
                        profileDir = targetDir,
                        ageSecretKey = snapshot.imported.ageSecretKey,
                    ) { status ->
                        try {
                            cb?.updateStatus(status)
                        } catch (error: Exception) {
                            cb = null
                            Timber.w(error, "Report provider fetch status: %s", error.message)
                        }
                    }

                    val stagedConfig = stagingDir.resolve("config.yaml")
                    check(stagedConfig.isFile && stagedConfig.length() > 0L) {
                        "Profile update produced no config.yaml: ${snapshot.imported.uuid}"
                    }

                    profileLock.withLock {
                        if (ImportedDao.exists(snapshot.imported.uuid)) {
                            targetDir.deleteRecursively()
                            stagingDir.copyRecursively(targetDir, overwrite = true)

                            val finalName =
                                if (snapshot.imported.type == Profile.Type.Url) {
                                    resolveSubscriptionName(
                                        snapshot.imported.name,
                                        snapshot.imported.source,
                                        subInfo,
                                    )
                                } else {
                                    snapshot.imported.name
                                }

                            val updated =
                                Imported(
                                    snapshot.imported.uuid,
                                    finalName,
                                    snapshot.imported.type,
                                    snapshot.imported.source,
                                    subInfo?.updateInterval ?: snapshot.imported.interval,
                                    subInfo?.upload ?: snapshot.imported.upload,
                                    subInfo?.download ?: snapshot.imported.download,
                                    subInfo?.total ?: snapshot.imported.total,
                                    subInfo?.expire ?: snapshot.imported.expire,
                                    snapshot.imported.createdAt,
                                    ageSecretKey = snapshot.imported.ageSecretKey,
                                )
                            ImportedDao.update(updated)

                            context.sendProfileChanged(
                                snapshot.imported.uuid,
                                affectsRuntime =
                                    ServiceStore().activeProfile == snapshot.imported.uuid,
                            )
                        }
                    }
                } catch (error: Exception) {
                    // fault barrier: core fetch runs through the JNI bridge; roll back the staged
                    // update atomically, then rethrow (with a friendlier message for age errors).
                    profileLock.withLock {
                        if (
                            !snapshot.hasCommittedConfig &&
                                ImportedDao.exists(snapshot.imported.uuid)
                        ) {
                            ImportedDao.remove(snapshot.imported.uuid)
                            targetDir.deleteRecursively()
                            context.sendProfileChanged(
                                snapshot.imported.uuid,
                                affectsRuntime =
                                    ServiceStore().activeProfile == snapshot.imported.uuid,
                            )
                        }
                    }
                    // Provide a more user-friendly error message for age decryption failures
                    val errorMessage = error.message ?: ""
                    if (
                        errorMessage.contains("no identities specified") ||
                            errorMessage.contains("decrypt config error")
                    ) {
                        throw IllegalArgumentException(
                            "This config is encrypted with age. Please provide the age secret key when importing the config.",
                            error,
                        )
                    } else {
                        throw error
                    }
                } finally {
                    stagingDir.deleteRecursively()
                }
            }
        }
    }

    suspend fun delete(context: Context, uuid: UUID) {
        withContext(Dispatchers.IO + NonCancellable) {
            profileLock.withLock {
                val affectsRuntime = ServiceStore().activeProfile == uuid
                ImportedDao.remove(uuid)

                val imported = context.importedDir.resolve(uuid.toString())
                imported.deleteRecursively()

                context.sendProfileChanged(uuid, affectsRuntime = affectsRuntime)
            }
        }
    }
}
