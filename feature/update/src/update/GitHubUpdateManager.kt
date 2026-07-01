package com.github.yumelira.yumebox.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.github.yumelira.yumebox.core.data.UpdateSettings
import dev.oom_wg.purejoy.mlang.MLang
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import timber.log.Timber

data class UpdateBuildConfig(
    val versionName: String,
    val updateSource: String,
    val uiBuildId: String,
    val updateRepository: String,
    val updateMirrorTemplates: String,
    val userAgentAppName: String = "FlyCat",
)

enum class UpdateSource(val key: String, val tag: String?) {
    Latest("latest", null),
    Prerelease("prerelease", "Pre-release"),
    Smart("smart", "Smart"),
    ;
    companion object {
        fun fromKey(raw: String?): UpdateSource {
            val key = raw?.trim().orEmpty()
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: Latest
        }
    }
}

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    val size: Long = 0L,
)

@Serializable
data class GitHubRelease(
    val id: Long = 0L,
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("target_commitish")
    val targetCommitish: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class UpdateManifestPackage(
    val abi: String = "",
    val fileName: String = "",
    val downloadUrl: String = "",
    val size: Long = 0L,
    val sha256: String = "",
    val isUniversal: Boolean = false,
)

@Serializable
data class UpdateManifest(
    val schemaVersion: Int = 0,
    val manifestUrl: String = "",
    val channel: String = "",
    val module: String = "",
    val artifactPrefix: String = "",
    val tag: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val publishedAt: String = "",
    val commitSha: String = "",
    val packages: List<UpdateManifestPackage> = emptyList(),
)

data class UpdateCandidate(
    val manifest: UpdateManifest,
) {
    val tag: String get() = manifest.tag
    val versionName: String get() = manifest.versionName
    val releaseNotes: String get() = manifest.releaseNotes
}

data class UpdateDownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val message: String = "",
)

class GitHubUpdateManager(
    context: Context,
    private val appSettingsStore: UpdateSettings,
    private val buildConfig: UpdateBuildConfig,
    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
        engine {
            connectTimeout = 15_000
            socketTimeout = 60_000
        }
    },
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val preferences = appContext.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
    private val packageProfile = PackageProfile.fromPackageName(appContext.packageName)
    private val _downloadProgress = MutableStateFlow(UpdateDownloadProgress())
    @Volatile
    private var activeDownloadJob: Job? = null
    @Volatile
    private var downloadCancelRequested: Boolean = false
    @Volatile
    private var autoCheckJob: Job? = null
    val downloadProgress: StateFlow<UpdateDownloadProgress> = _downloadProgress.asStateFlow()
    fun getSelectedSource(): UpdateSource {
        val stored = appSettingsStore.updateSourceKey.value.ifBlank { buildConfig.updateSource }
        return UpdateSource.fromKey(stored)
    }
    fun setSelectedSource(source: UpdateSource) {
        appSettingsStore.updateSourceKey.set(source.key)
    }
    suspend fun checkForUpdate(
        source: UpdateSource = getSelectedSource(),
        isManualCheck: Boolean = false,
    ): Result<UpdateCandidate?> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.i("Update check start: source=%s manual=%s", source.key, isManualCheck)
            val release = fetchRelease(source)
            val candidate = release.toUpdateCandidate(source)
            Timber.i(
                "Update release parsed: source=%s tag=%s version=%s assets=%d",
                source.key,
                candidate.tag,
                candidate.versionName,
                candidate.manifest.packages.size,
            )
            if (source == UpdateSource.Latest && !isLatestNewer(candidate.versionName, candidate.tag)) {
                Timber.i(
                    "Update check no newer version (latest): local=%s remoteVersion=%s remoteTag=%s",
                    buildConfig.versionName,
                    candidate.versionName,
                    candidate.tag,
                )
                return@runCatching null
            }
            if (source != UpdateSource.Latest && !isNonLatestNewer(candidate)) {
                Timber.i(
                    "Update check no newer build (non-latest): localBuildId=%s remoteTag=%s",
                    buildConfig.uiBuildId,
                    candidate.tag,
                )
                return@runCatching null
            }
            if (!isManualCheck && source != UpdateSource.Latest && shouldSuppressRepeatedPrompt(source, candidate)) {
                return@runCatching null
            }
            candidate.also {
                Timber.i("Update check candidate ready: source=%s tag=%s version=%s", source.key, it.tag, it.versionName)
                if (source != UpdateSource.Latest) {
                    markPrompted(source, it)
                }
            }
        }
    }
    fun startAutoCheck(scope: CoroutineScope, intervalMs: Long = AUTO_CHECK_INTERVAL_MS) {
        if (autoCheckJob?.isActive == true) return
        synchronized(this) {
            if (autoCheckJob?.isActive == true) return
            autoCheckJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    runCatching {
                        checkForUpdate(getSelectedSource())
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        Timber.w(throwable, "Update auto check failed")
                    }
                    delay(intervalMs)
                }
            }
        }
    }
    fun stopAutoCheck() {
        synchronized(this) {
            autoCheckJob?.cancel()
            autoCheckJob = null
        }
    }
    suspend fun downloadAndInstall(
        candidate: UpdateCandidate,
        selectedPackage: UpdateManifestPackage? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (_downloadProgress.value.isDownloading) {
            return@withContext Result.failure(IllegalStateException(MLang.Component.Update.Message.DownloadAlreadyRunning))
        }
        val targets = candidate.resolveDownloadTargets(selectedPackage)
        if (targets.isEmpty()) {
            return@withContext Result.failure(IllegalStateException(MLang.Component.Update.Message.NoCompatibleAsset))
        }
        val outputFile = File(appContext.cacheDir, "updates/${targets.first().fileName}")
        runCatching {
            downloadCancelRequested = false
            _downloadProgress.value = UpdateDownloadProgress(
                isDownloading = true,
                progress = 0,
                message = MLang.Component.Update.Message.Preparing,
            )
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
            val errors = mutableListOf<String>()
            for (target in targets) {
                if (downloadCancelRequested) throw UpdateDownloadCancelledException()
                coroutineContext.ensureActive()
                val downloaded = runCatching {
                    downloadToFile(target.url, outputFile)
                    if (downloadCancelRequested) throw UpdateDownloadCancelledException()
                    true
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (throwable.isUpdateDownloadCancelled()) throw throwable
                    Timber.e(throwable, "Update download failed: ${target.url}")
                    errors += throwable.message ?: target.url
                    if (outputFile.exists()) outputFile.delete()
                }.getOrDefault(false)
                if (downloaded) {
                    _downloadProgress.value = UpdateDownloadProgress(
                        isDownloading = false,
                        progress = 100,
                        message = MLang.Component.Update.Message.DownloadReady,
                    )
                    openInstaller(outputFile)
                    return@runCatching
                }
            }
            error(errors.firstOrNull() ?: MLang.Component.Update.Message.Error)
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            _downloadProgress.value = UpdateDownloadProgress(
                isDownloading = false,
                progress = 0,
                message = if (throwable.isUpdateDownloadCancelled()) {
                    MLang.Component.Button.Cancel
                } else {
                    throwable.message ?: MLang.Component.Update.Message.Error
                },
            )
            if (throwable.isUpdateDownloadCancelled() && outputFile.exists()) {
                outputFile.delete()
            }
        }.also {
            downloadCancelRequested = false
        }
    }
    fun cancelDownload() {
        downloadCancelRequested = true
        activeDownloadJob?.cancel()
        _downloadProgress.value = UpdateDownloadProgress(
            isDownloading = false,
            progress = 0,
            message = MLang.Component.Button.Cancel,
        )
    }
    private suspend fun fetchRelease(source: UpdateSource): GitHubRelease {
        val endpoint = resolveReleaseEndpoint(source)
        Timber.i("Update fetch release endpoint: source=%s endpoint=%s", source.key, endpoint)
        val response = client.get(endpoint) {
            header("User-Agent", updateUserAgent())
            header("Accept", "application/vnd.github+json")
        }
        Timber.i("Update fetch release response: source=%s code=%d", source.key, response.status.value)
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value}")
        }
        val body = response.bodyAsText()
        return json.decodeFromString<GitHubRelease>(body)
    }
    private fun resolveReleaseEndpoint(source: UpdateSource): String {
        val repository = buildConfig.updateRepository.trim().trim('/')
        if (!repository.contains('/')) {
            error("Invalid UPDATE_REPOSITORY: $repository")
        }
        val apiBase = "https://api.github.com/repos/$repository/releases"
        return when (source) {
            UpdateSource.Latest -> "$apiBase/latest"
            else -> "$apiBase/tags/${Uri.encode(source.tag ?: "")}"
        }
    }
    private fun GitHubRelease.toUpdateCandidate(source: UpdateSource): UpdateCandidate {
        val allowedPrefixes = releasePrefixesFor(source)
        val normalizedAssets = assets
            .filter { asset ->
                asset.browserDownloadUrl.isNotBlank() &&
                    asset.name.endsWith(".apk", ignoreCase = true) &&
                    allowedPrefixes.any { asset.name.lowercase().startsWith(it) }
            }
            .map { asset ->
                val inferredAbi = inferAbiFromAssetName(asset.name)
                UpdateManifestPackage(
                    abi = inferredAbi,
                    fileName = asset.name,
                    downloadUrl = asset.browserDownloadUrl,
                    size = asset.size,
                    isUniversal = inferredAbi == "universal",
                )
            }
        val notes = body.orEmpty().ifBlank { MLang.Component.Update.Message.Available }
        val manifest = UpdateManifest(
            schemaVersion = 1,
            manifestUrl = "",
            channel = "",
            module = "app",
            artifactPrefix = "",
            tag = tagName,
            versionName = name.orEmpty().ifBlank { tagName },
            versionCode = id,
            releaseNotes = notes,
            releaseUrl = htmlUrl,
            publishedAt = publishedAt.orEmpty(),
            commitSha = targetCommitish.orEmpty(),
            packages = normalizedAssets,
        )
        return UpdateCandidate(manifest)
    }
    private fun releasePrefixesFor(source: UpdateSource): List<String> = when (source) {
        UpdateSource.Latest -> listOf("flycat-stable-", "flycat-stable-lite-")
        UpdateSource.Prerelease -> listOf("flycat-pre-", "flycat-lite-alpha-")
        UpdateSource.Smart -> listOf("flycat-smart-", "flycat-lite-smart-")
    }
    private fun inferAbiFromAssetName(name: String): String {
        val lower = name.lowercase()
        return when {
            "arm64-v8a" in lower -> "arm64-v8a"
            "armeabi-v7a" in lower -> "armeabi-v7a"
            Regex("(^|[^a-z])x86_64([^a-z]|$)").containsMatchIn(lower) -> "x86_64"
            Regex("(^|[^a-z])x86([^a-z]|$)").containsMatchIn(lower) -> "x86"
            "universal" in lower -> "universal"
            else -> "universal"
        }
    }
    private fun isLatestNewer(versionName: String, tag: String): Boolean {
        val remote = normalizeVersion(versionName).ifBlank { normalizeVersion(tag) }
        val local = normalizeVersion(buildConfig.versionName)
        if (remote.isBlank() || local.isBlank()) {
            return !tag.equals(buildConfig.versionName, ignoreCase = true)
        }
        return compareSemanticVersion(remote, local) > 0
    }
    private fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
    }
    private fun compareSemanticVersion(a: String, b: String): Int {
        val left = a.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val right = b.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val lv = left.getOrElse(index) { 0 }
            val rv = right.getOrElse(index) { 0 }
            if (lv != rv) return lv.compareTo(rv)
        }
        return 0
    }
    private fun isNonLatestNewer(candidate: UpdateCandidate): Boolean {
        val remoteAssetName = candidate.manifest.resolveBestPackageFileName().ifBlank {
            candidate.manifest.packages.firstOrNull()?.fileName.orEmpty()
        }
        val remoteBuildId = extractBuildIdFromFileName(remoteAssetName)
        if (remoteBuildId == null) return true
        val localBuildId = buildConfig.uiBuildId.trim().ifBlank { return true }
        return compareBuildId(remoteBuildId, localBuildId) > 0
    }
    private fun extractBuildIdFromFileName(fileName: String): String? {
        if (fileName.isBlank()) return null
        val match = BUILD_ID_REGEX.find(fileName) ?: return null
        return match.groupValues.getOrNull(1)
    }
    private fun compareBuildId(remote: String, local: String): Int {
        val remoteStamp = remote.substringBefore('-').trim()
        val localStamp = local.substringBefore('-').trim()
        return remoteStamp.compareTo(localStamp)
    }
    private fun mirrorUrlsFor(sourceUrl: String, templates: String): List<String> {
        if (sourceUrl.isBlank() || templates.isBlank()) return emptyList()
        return templates.split('\n', ',', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { template ->
                when {
                    "{url}" in template -> template.replace("{url}", sourceUrl)
                    "{encodedUrl}" in template -> template.replace("{encodedUrl}", Uri.encode(sourceUrl))
                    else -> template.trimEnd('/') + "/" + sourceUrl
                }
            }
    }
    private data class DownloadTarget(
        val url: String,
        val fileName: String,
    )
    private fun UpdateCandidate.resolveDownloadTargets(selectedPackage: UpdateManifestPackage? = null): List<DownloadTarget> {
        val urls = resolveDownloadUrls(selectedPackage)
        val fileName = resolveDownloadFileName(selectedPackage)
        return urls.filter(String::isNotBlank).map { DownloadTarget(url = it, fileName = fileName) }
    }
    private fun UpdateCandidate.resolveDownloadUrls(selectedPackage: UpdateManifestPackage? = null): List<String> {
        val directUrl = selectedPackage?.downloadUrl?.takeIf { it.isNotBlank() } ?: manifest.resolveBestPackageUrl()
        if (directUrl.isNotBlank()) {
            val urls = linkedSetOf<String>()
            urls += mirrorUrlsFor(directUrl, buildConfig.updateMirrorTemplates)
            urls += directUrl
            return urls.filter(String::isNotBlank)
        }
        val downloadUrl = manifest.toLegacyApkDownloadUrl()
        val urls = linkedSetOf<String>()
        urls += mirrorUrlsFor(downloadUrl, buildConfig.updateMirrorTemplates)
        urls += downloadUrl
        return urls.filter(String::isNotBlank)
    }
    private fun UpdateCandidate.resolveDownloadFileName(selectedPackage: UpdateManifestPackage? = null): String {
        val packageFileName = selectedPackage?.fileName?.takeIf { it.isNotBlank() } ?: manifest.resolveBestPackageFileName()
        if (packageFileName.isNotBlank()) return packageFileName
        return FALLBACK_APK_FILE_NAME
    }
    private fun UpdateManifest.resolveBestPackageUrl(): String {
        val target = resolveBestPackageTarget() ?: return ""
        return target.downloadUrl
    }
    private fun UpdateManifest.resolveBestPackageFileName(): String {
        val target = resolveBestPackageTarget() ?: return ""
        if (target.fileName.isNotBlank()) return target.fileName
        return target.downloadUrl.substringAfterLast('/').substringBefore('?')
    }
    private fun UpdateManifest.resolveBestPackageTarget(): UpdateManifestPackage? {
        val available = packages.filter { it.downloadUrl.isNotBlank() }
        if (available.isEmpty()) return null
        val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val exactMatch = supportedAbis.firstNotNullOfOrNull { abi ->
            available
                .asSequence()
                .filter { it.abi.equals(abi, ignoreCase = true) }
                .sortedByDescending { packageScore(it.fileName) }
                .firstOrNull()
        }
        if (exactMatch != null) return exactMatch
        val universal = available
            .asSequence()
            .filter { it.isUniversal || it.abi.equals("universal", ignoreCase = true) }
            .sortedByDescending { packageScore(it.fileName) }
            .firstOrNull()
        if (universal != null) return universal
        return available.maxByOrNull { packageScore(it.fileName) }
    }
    private fun packageScore(fileName: String): Int {
        val lower = fileName.lowercase()
        val isLite = "lite" in lower
        val isExtension = "extension" in lower
        val isStandalone = "standalone" in lower
        var score = 0
        if (isLite == packageProfile.lite) score += 8
        if (isExtension == packageProfile.extension) score += 6
        if (isStandalone == packageProfile.standalone) score += 4
        if (!packageProfile.lite && !isLite) score += 3
        if (!packageProfile.extension && !isExtension) score += 2
        if (!packageProfile.standalone && !isStandalone) score += 1
        return score
    }
    private fun shouldSuppressRepeatedPrompt(source: UpdateSource, candidate: UpdateCandidate): Boolean {
        val current = candidatePromptFingerprint(candidate)
        if (current.isBlank()) return false
        val cached = preferences.getString(promptedKey(source), "").orEmpty()
        return cached == current
    }
    private fun markPrompted(source: UpdateSource, candidate: UpdateCandidate) {
        val fingerprint = candidatePromptFingerprint(candidate)
        if (fingerprint.isBlank()) return
        preferences.edit().putString(promptedKey(source), fingerprint).apply()
    }
    private fun promptedKey(source: UpdateSource): String = "prompted_${source.key}"
    private fun candidatePromptFingerprint(candidate: UpdateCandidate): String {
        val assetName = candidate.manifest.resolveBestPackageFileName().ifBlank { "no-asset" }
        return "${candidate.tag}|$assetName"
    }
    private fun UpdateManifest.toLegacyApkDownloadUrl(): String {
        val releaseUrl = releaseUrl.trim().trimEnd('/')
        val tag = tag.trim()
        if (releaseUrl.isBlank() || tag.isBlank()) {
            error(MLang.Component.Update.Message.MissingReleaseMetadata)
        }
        val marker = "/releases/"
        val markerIndex = releaseUrl.indexOf(marker)
        if (markerIndex < 0) error(MLang.Component.Update.Message.MissingReleaseMetadata)
        val repoUrl = releaseUrl.substring(0, markerIndex)
        return "$repoUrl/releases/download/${Uri.encode(tag)}/${Uri.encode(FALLBACK_APK_FILE_NAME)}"
    }
    private suspend fun downloadToFile(url: String, outputFile: File) {
        val downloadJob = coroutineContext[Job]
        activeDownloadJob = downloadJob
        try {
            val response = client.get(url) {
                header("User-Agent", updateUserAgent())
            }
            if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
            val contentLength = response.contentLength() ?: -1
            response.bodyAsChannel().toInputStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var total = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        if (downloadCancelRequested) throw UpdateDownloadCancelledException()
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        total += read
                        val progress = if (contentLength > 0) {
                            ((total * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        _downloadProgress.value = UpdateDownloadProgress(
                            isDownloading = true,
                            progress = progress,
                            message = if (progress > 0) {
                                MLang.Component.Update.Message.DownloadingWithProgress.format(progress)
                            } else {
                                MLang.Component.Update.Message.Downloading
                            },
                        )
                    }
                }
            }
        } catch (throwable: CancellationException) {
            if (downloadCancelRequested) {
                throw UpdateDownloadCancelledException()
            }
            throw throwable
        } finally {
            if (activeDownloadJob === downloadJob) {
                activeDownloadJob = null
            }
        }
    }
    private fun openInstaller(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
    private fun updateUserAgent(): String {
        return "${buildConfig.userAgentAppName}/${buildConfig.versionName}"
    }
    private companion object {
        const val FALLBACK_APK_FILE_NAME = "FlyCat-release.apk"
        const val AUTO_CHECK_INTERVAL_MS = 1 * 60 * 60 * 1000L
        const val PREFERENCE_FILE = "update_preferences"
        val BUILD_ID_REGEX = Regex("([0-9]{12}-[0-9a-fA-F]{5,8})\\.apk$")
    }
}

private data class PackageProfile(
    val lite: Boolean,
    val extension: Boolean,
    val standalone: Boolean,
) {
    companion object {
        fun fromPackageName(packageName: String): PackageProfile {
            val lower = packageName.lowercase()
            return PackageProfile(
                lite = ".lite" in lower || "lite" in lower,
                extension = ".extension" in lower || "extension" in lower,
                standalone = ".standalone" in lower || "standalone" in lower,
            )
        }
    }
}

internal class UpdateDownloadCancelledException : IOException(MLang.Component.Button.Cancel)

internal fun Throwable.isUpdateDownloadCancelled(): Boolean =
    this is UpdateDownloadCancelledException || message == MLang.Component.Button.Cancel
