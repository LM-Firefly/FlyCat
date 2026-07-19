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
import com.github.yumelira.yumebox.runtime.api.IFetchObserver
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.service.util.importedDir
import com.github.yumelira.yumebox.runtime.service.util.sendProfileChanged
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import com.tencent.mmkv.MMKV
import java.io.File
import java.net.URLDecoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

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

    /**
     * Downloads a subscription URL to `stagingDir/config.yaml` and reports progress, replacing the
     * old native fetchAndValid. Parses the `subscription-userinfo` header into a SubscriptionInfo
     * status; deep config validation happens later at compile time.
     */
    private suspend fun fetchSubscription(
        stagingDir: File,
        url: String,
        onStatus: (FetchStatus) -> Unit,
    ) {
        onStatus(FetchStatus(FetchStatus.Action.FetchConfiguration, listOf(url), 0, 1))
        val client =
            HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 60_000 }
                followRedirects = true
            }
        try {
            // Airports gate the real config (proxies + subscription-userinfo/profile-title headers)
            // on a recognized Clash-client User-Agent; "YumeBox" gets a crippled response, so send the
            // user's custom UA or the same default the Sub-Store client uses.
            val response =
                client.get(url) { header(HttpHeaders.UserAgent, resolveSubscriptionUserAgent()) }
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
                decodeSubscriptionTitle(headers["profile-title"] ?: headers["subscription-title"])
            val filename = parseContentDispositionFilename(headers["content-disposition"])
            val interval =
                (headers["profile-update-interval"] ?: headers["subscription-update-interval"])
                    ?.trim()
                    ?.toLongOrNull()

            // Emit the subscription-info status unconditionally: a server may send profile-title
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
                    subExpire = fields["expire"]?.toLongOrNull(),
                    subUpdateInterval = interval,
                    subTitle = title,
                    subFilename = filename,
                )
            )
        } finally {
            client.close()
        }
    }

    private const val DEFAULT_SUBSCRIPTION_UA = "ClashMetaForAndroid"

    /** The user's configured User-Agent (settings store), or the airport-recognized default. */
    private fun resolveSubscriptionUserAgent(): String {
        val custom =
            runCatching {
                    MMKV.mmkvWithID("settings", MMKV.MULTI_PROCESS_MODE)
                        .decodeString("customUserAgent")
                }
                .getOrNull()
                ?.trim()
        return custom?.takeIf { it.isNotEmpty() } ?: DEFAULT_SUBSCRIPTION_UA
    }

    /** Decodes a `profile-title` header: plain, `base64:…`, RFC 5987 (`UTF-8''…`), or url-encoded. */
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
                ?.let { return it }
        }
        runCatching { URLDecoder.decode(value, "UTF-8").trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != value }
            ?.let { return it }
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
     * Extracts the filename from a Content-Disposition header. Handles RFC 5987 `filename*=charset''…`
     * (url-decoded with the given charset) and plain `filename=…`, taking only the value up to the next
     * `;` — a naive `substringAfter("filename=")` would swallow trailing params and yield a garbled name.
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
                            val safeCharset =
                                runCatching { java.nio.charset.Charset.forName(charset).name() }
                                    .getOrDefault("UTF-8")
                            URLDecoder.decode(encoded, safeCharset).trim()
                        }
                } else {
                    Regex("""filename=([^;]+)""", RegexOption.IGNORE_CASE)
                        .find(cd)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                        ?.trim('"', '\'')
                }?.takeIf { it.isNotBlank() }
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
    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
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
                        // Sentinel for "this profile has already committed a config", used below to
                        // decide whether a failed update may roll back (delete) the profile. The
                        // source profile file is config.yaml (written by fetchAndValid on a
                        // successful fetch); runtime.yaml is no longer produced by any path, so it
                        // can never serve as this sentinel.
                        hasCommittedConfig = targetDir.resolve("config.yaml").isFile,
                    )
                }

                var cb = callback
                var subInfo: SubscriptionInfo? = null

                try {
                    // The age secret key is applied per-profile inside the compiler (via the compile
                    // request), not as global core state, so nothing to set here.
                    // Only Url profiles are fetched over HTTP. A File profile's config.yaml was already
                    // written into its dir at import time (copyProfileImport) and copied into staging
                    // above; HTTP-getting its empty/local source would just clobber it.
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

                            context.sendProfileChanged(snapshot.imported.uuid)
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
                            context.sendProfileChanged(snapshot.imported.uuid)
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
                ImportedDao.remove(uuid)

                val imported = context.importedDir.resolve(uuid.toString())
                imported.deleteRecursively()

                context.sendProfileChanged(uuid)
            }
        }
    }
}
