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
import com.github.yumelira.yumebox.core.model.FetchStatus
import com.github.yumelira.yumebox.core.util.PROXY_PROVIDER_SCOPE
import com.github.yumelira.yumebox.core.util.RULE_PROVIDER_SCOPE
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.core.util.profileProviderScopeDir
import com.github.yumelira.yumebox.runtime.api.FetchObserver
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.service.config.ServiceStore
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
import timber.log.Timber

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()

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
     * the runtime config. This is deliberately best-effort: mihomo remains the fallback owner and
     * downloads a missing resource itself, so one failed provider must never reject an import.
     */
    private suspend fun fetchExternalProviders(
        stagingDir: File,
        profileDir: File,
        onStatus: (FetchStatus) -> Unit,
    ) {
        val config = stagingDir.resolve("config.yaml")
        if (!config.isFile) return
        val providers = runCatching {
            collectExternalProviders(
                root = YamlCodec.loadMap(config.readText()),
                stagingDir = stagingDir,
                profileDir = profileDir,
            )
        }
            .onFailure { Timber.w(it, "Skip external provider prefetch: config parse failed") }
            .getOrDefault(emptyList())
        if (providers.isEmpty()) return

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
                        .onFailure { error ->
                            Timber.w(error, "Skip external provider download: %s", provider.url)
                        }
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectExternalProviders(
        root: Map<String, Any?>,
        stagingDir: File,
        profileDir: File,
    ): List<ExternalProvider> = buildList {
        listOf(
                "rule-providers" to RULE_PROVIDER_SCOPE,
                "proxy-providers" to PROXY_PROVIDER_SCOPE,
            )
            .forEach { (field, scope) ->
                val definitions = root[field] as? Map<*, *> ?: return@forEach
                definitions.forEach { (rawName, rawDefinition) ->
                    val definition = rawDefinition as? Map<*, *> ?: return@forEach
                    val type = definition["type"]?.toString()?.trim().orEmpty()
                    val url = definition["url"]?.toString()?.trim().orEmpty()
                    if (!type.equals("http", ignoreCase = true) || !url.isHttpUrl()) return@forEach

                    val extension =
                        if (
                            scope == RULE_PROVIDER_SCOPE &&
                                definition["format"]
                                    ?.toString()
                                    ?.equals("mrs", ignoreCase = true) == true
                        ) {
                            "mrs"
                        } else {
                            "yaml"
                        }
                    val target =
                        profileProviderScopeDir(stagingDir, scope)
                            .resolve(
                                providerRelativePath(
                                    path = definition["path"]?.toString().orEmpty(),
                                    url = url,
                                    extension = extension,
                                    profileProviderDir = profileProviderScopeDir(profileDir, scope),
                                )
                            )
                    val name =
                        rawName?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: target.name
                    add(
                        ExternalProvider(
                            name = name,
                            url = url,
                            target = target,
                            headers = providerHeaders(definition),
                        )
                    )
                }
            }
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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private val providerPathPrefixes =
        setOf("providers", "provider", "clash", "ruleset", "rules", "proxies")

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
                    }

                    fetchExternalProviders(stagingDir, targetDir) { status ->
                        try {
                            cb?.updateStatus(status)
                        } catch (error: Exception) {
                            cb = null
                            Timber.w(error, "Report provider fetch status: %s", error.message)
                        }
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
