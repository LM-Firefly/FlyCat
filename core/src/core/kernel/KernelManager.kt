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

package com.github.lmfirefly.flycat.core.kernel

import android.content.Context
import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.tukaani.xz.XZInputStream
import timber.log.Timber

/** 反序列化可能以 "ebpf"（字符串）或 ["ebpf"]（数组）形式出现的能力。 */
private object FlexCapabilitiesSerializer : KSerializer<Set<String>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexCapabilities", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Set<String> {
        val json = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return emptySet()
        return when (json) {
            is JsonArray -> json.map { it.jsonPrimitive.content }.filter { it.isNotEmpty() }.toSet()
            is JsonPrimitive -> if (json.content.isEmpty()) emptySet() else setOf(json.content)
            else -> emptySet()
        }
    }

    override fun serialize(encoder: Encoder, value: Set<String>) = encoder.encodeString(value.joinToString(","))
}

/** 可下载的 `libmihomo.so` 目录和安装程序。捆绑的 Alpha 版本仍作为备用方案。 */
object KernelManager {
    const val INDEX_URL = "https://github.com/LM-Firefly/Kernel-Builder/releases/download/kernel/kernel-index.json"
    const val BUNDLED_ALPHA_ID = "bundled-alpha"
    private const val CORE_FILE_PREFIX = "libmihomo-"
    private const val CORE_METADATA_SUFFIX = ".json"
    private const val ACTIVE_FILE = "active-kernel"
    private const val MAX_PLUGIN_BYTES = 128L * 1024 * 1024
    private const val CUSTOM_PLUGIN_FORMAT = "kernel-plugin-v1"
    private const val CERTIFIED_WORKFLOW = ".github/workflows/build-kernels.yml"
    const val CAPABILITY_EBPF = "ebpf"
    private const val LEGACY_EBPF_ID = "ebpf"

    private val json = Json { ignoreUnknownKeys = true }
    private val installMutex = Mutex()
    private val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) { expectSuccess = false }
    }

    /** 返回 ABI 特定的清单 URL (例如 `kernel-index-x86_64.json`)。 */
    fun indexUrlForAbi(abi: String): String {
        val base = INDEX_URL.substringBeforeLast("/")
        return if (abi == DEFAULT_ABI) INDEX_URL else "$base/kernel-index-$abi.json"
    }

    private val DEFAULT_ABI = "arm64-v8a"

    private fun currentAbi(): String {
        val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: return DEFAULT_ABI
        return when {
            primary.startsWith("arm64") -> "arm64-v8a"
            primary.startsWith("armeabi") -> "armeabi-v7a"
            primary == "x86_64" -> "x86_64"
            primary == "x86" -> "x86"
            else -> DEFAULT_ABI
        }
    }

    @Serializable
    data class ReleaseMetadata(val kind: String = "official")

    @Serializable
    data class BuilderMetadata(
        val repository: String = "",
        val workflow: String = "",
        val runId: String = "",
        val commit: String = "",
        val format: String = "",
    )

    @Serializable
    data class Index(
        val release: ReleaseMetadata = ReleaseMetadata(),
        val builder: BuilderMetadata? = null,
        val schemaVersion: Int,
        val defaultKernel: String,
        val abi: String,
        val shellAbi: Int,
        val kernels: List<Kernel>,
    )

    @Serializable
    data class Kernel(
        val id: String,
        val name: String,
        val version: String,
        val commit: String,
        val abi: String,
        val shellAbi: Int,
        val asset: String,
        val downloadUrl: String,
        val sha256: String,
        val sizeBytes: Long,
        val compression: String,
        @Serializable(with = FlexCapabilitiesSerializer::class) val capabilities: Set<String> = emptySet(),
    )

    @Serializable
    private data class InstalledKernel(
        val id: String,
        val name: String,
        val version: String,
        val commit: String,
        val capabilities: Set<String> = emptySet(),
    )

    suspend fun fetchIndex(context: Context, url: String = indexUrlForAbi(currentAbi()), forceNetwork: Boolean = false,): Index = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Kernel index must use HTTPS" }
        // 如果缓存索引仍然有效且未被强制更新，则返回该缓存索引。
        if (!forceNetwork) {
            readCachedIndex(context)?.let { return@withContext it }
        }
        val response = client.get(url)
        check(response.status.isSuccess()) { "Kernel index request failed: ${response.status}" }
        val index = json.decodeFromString<Index>(response.bodyAsText())
        validateIndex(index)
        writeCachedIndex(context, index)
        index
    }

    private const val INDEX_CACHE_FILE = "kernel-index-cache.json"
    private const val INDEX_CACHE_TTL_MS = 60 * 60 * 1000L // 1小时

    private fun readCachedIndex(context: Context): Index? = runCatching {
        val file = File(context.filesDir, INDEX_CACHE_FILE)
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > INDEX_CACHE_TTL_MS) return null
        val index = json.decodeFromString<Index>(file.readText())
        validateIndex(index)
        index
    }.getOrNull()

    private fun writeCachedIndex(context: Context, index: Index) {
        runCatching {
            val file = File(context.filesDir, INDEX_CACHE_FILE)
            file.writeText(json.encodeToString(index))
        }
    }

    suspend fun install(context: Context, kernel: Kernel): File = installMutex.withLock {
        withContext(Dispatchers.IO) {
            validateKernel(kernel)
            libraryDirectory(context).mkdirs()
            val archive =
                File(libraryDirectory(context), "$CORE_FILE_PREFIX${kernel.id}.so.xz.download")
            try {
                download(kernel, archive)
                installArchive(context, kernel, archive)
            } finally {
                archive.delete()
            }
        }
    }

    suspend fun installPlugin(context: Context, source: InputStream): Kernel = installMutex.withLock {
        withContext(Dispatchers.IO) {
            val plugin = File(context.cacheDir, "kernel-plugin-${System.nanoTime()}.zip")
            source.use { input ->
                plugin.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            try {
                check(plugin.length() in 1..MAX_PLUGIN_BYTES) { "Invalid kernel plugin size" }
                installPluginFile(context, plugin)
            } finally {
                plugin.delete()
            }
        }
    }

    suspend fun installPluginUrl(context: Context, url: String): Kernel = installMutex.withLock {
        withContext(Dispatchers.IO) {
            require(url.startsWith("https://")) { "Kernel plugin must use HTTPS" }
            val plugin = File(context.cacheDir, "kernel-plugin-${System.nanoTime()}.zip")
            try {
                val response = client.get(url)
                check(response.status.isSuccess()) { "Kernel plugin request failed: ${response.status}" }
                response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { length ->
                    check(length in 1..MAX_PLUGIN_BYTES) { "Invalid kernel plugin size" }
                }
                plugin.outputStream().buffered()
                    .use { output -> response.bodyAsChannel().copyTo(output) }
                check(plugin.length() in 1..MAX_PLUGIN_BYTES) { "Invalid kernel plugin size" }
                installPluginFile(context, plugin)
            } finally {
                plugin.delete()
            }
        }
    }

    private fun installPluginFile(context: Context, plugin: File): Kernel {
        ZipFile(plugin).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            check("kernel-index.json" in names) { "Kernel plugin index is missing" }
            val index = json.decodeFromString<Index>(
                zip.getInputStream(zip.getEntry("kernel-index.json")).bufferedReader()
                    .use { it.readText() },
            )
            validateCustomIndex(index)
            check(names == setOf("kernel-index.json", index.kernels.single().asset)) {
                "Kernel plugin contains unexpected files"
            }
            val kernel = index.kernels.single()
            libraryDirectory(context).mkdirs()
            val archive =
                File(libraryDirectory(context), "$CORE_FILE_PREFIX${kernel.id}.so.xz.download")
            try {
                zip.getInputStream(zip.getEntry(kernel.asset)).use { input ->
                    archive.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                check(archive.length() == kernel.sizeBytes) { "Kernel plugin archive size mismatch" }
                installArchive(context, kernel, archive)
                return kernel
            } finally {
                archive.delete()
            }
        }
    }

    private fun installArchive(context: Context, kernel: Kernel, archive: File): File {
        validateKernel(kernel)
        check(sha256(archive) == kernel.sha256) { "Kernel archive checksum mismatch" }
        val directory = libraryDirectory(context).apply { mkdirs() }
        val expanded = File(directory, "$CORE_FILE_PREFIX${kernel.id}.so.expanded")
        val target = libraryFile(context, kernel.id)
        try {
            XZInputStream(archive.inputStream().buffered()).use { input ->
                expanded.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(expanded.length() > 0L) { "Kernel archive is empty" }
            check(isValidCore(expanded, kernel.abi)) { "Downloaded file is not a valid Android core" }
            val metadata = metadataFile(context, kernel.id)
            if (metadata.exists()) check(metadata.delete()) { "Unable to replace kernel metadata" }
            replace(target, expanded)
            runCatching { writeInstalledKernel(context, kernel) }.onFailure {
                Timber.tag("KernelManager").w(it, "Unable to save installed kernel version")
            }
        } finally {
            expanded.delete()
        }
        return target
    }

    fun libraryFile(context: Context, id: String): File =
        File(libraryDirectory(context), "$CORE_FILE_PREFIX$id.so")

    fun activeKernelId(context: Context): String =
        runCatching {
            File(libraryDirectory(context), ACTIVE_FILE).takeIf(File::isFile)?.readText()?.trim()
        }
            .getOrNull()
            ?.takeIf { it == BUNDLED_ALPHA_ID || (isKernelId(it) && isInstalled(context, it)) }
            ?: BUNDLED_ALPHA_ID

    fun activate(context: Context, id: String) {
        require(id == BUNDLED_ALPHA_ID || isKernelId(id)) { "Invalid kernel id" }
        if (id != BUNDLED_ALPHA_ID) {
            check(libraryFile(context, id).isFile) { "Kernel is not downloaded" }
        }
        val directory = libraryDirectory(context).apply { mkdirs() }
        val marker = File(directory, ACTIVE_FILE)
        val pending = File(directory, "$ACTIVE_FILE.pending")
        pending.writeText(id)
        check(pending.renameTo(marker)) { "Unable to select active kernel" }
    }

    fun isEbpfKernelActive(context: Context): Boolean {
        val id = activeKernelId(context)
        if (id == BUNDLED_ALPHA_ID || !isInstalled(context, id)) return false
        return CAPABILITY_EBPF in readInstalledKernel(context, id)?.capabilities.orEmpty() ||
            id == LEGACY_EBPF_ID
    }

    fun installedKernelIds(context: Context): List<String> =
        libraryDirectory(context).listFiles().orEmpty()
            .asSequence()
            .map { it.name }
            .filter { it.startsWith(CORE_FILE_PREFIX) && it.endsWith(CORE_METADATA_SUFFIX) }
            .map { it.removePrefix(CORE_FILE_PREFIX).removeSuffix(CORE_METADATA_SUFFIX) }
            .filter { isKernelId(it) && isInstalled(context, it) }
            .distinct()
            .sorted()
            .toList()

    fun installedName(context: Context, id: String): String =
        readInstalledKernel(context, id)?.name?.takeIf { it.isNotBlank() } ?: id

    fun installedCommit(context: Context, id: String): String? {
        if (id == BUNDLED_ALPHA_ID || !isInstalled(context, id)) return null
        return readInstalledKernel(context, id)?.commit
    }

    fun installed(context: Context): File? =
        activeKernelId(context)
            .takeIf { it != BUNDLED_ALPHA_ID }
            ?.let { libraryFile(context, it).takeIf(File::isFile) }

    fun isInstalled(context: Context, id: String): Boolean =
        id == BUNDLED_ALPHA_ID || (isKernelId(id) && libraryFile(context, id).isFile)

    private suspend fun download(kernel: Kernel, target: File) {
        target.delete()
        val response = client.get(kernel.downloadUrl)
        check(response.status.isSuccess()) { "Kernel download failed: ${response.status}" }
        response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { length ->
            check(length == kernel.sizeBytes) { "Kernel archive size does not match the index" }
        }
        target.outputStream().buffered().use { output ->
            response.bodyAsChannel().copyTo(output)
        }
        check(target.length() == kernel.sizeBytes) { "Kernel archive size does not match the index" }
    }

    private fun replace(target: File, replacement: File) {
        val backup = File(target.parentFile, "${target.name}.previous")
        backup.delete()
        val hadPrevious = target.exists()
        if (hadPrevious) check(target.renameTo(backup)) { "Unable to back up active kernel" }
        try {
            check(replacement.renameTo(target)) { "Unable to install downloaded kernel" }
            target.setReadable(true, true)
            target.setWritable(false, false) // Android 16及以上拒绝可写的.so文件
            backup.delete()
        } catch (error: Throwable) {
            if (hadPrevious) backup.renameTo(target)
            throw error
        }
    }

    private fun validateIndex(index: Index) {
        check(index.schemaVersion == 3) { "Unsupported kernel index version" }
        check(index.abi == currentAbi()) { "Kernel index ABI ${index.abi} does not match device ${currentAbi()}" }
        check(index.kernels.isNotEmpty()) { "Kernel index contains no kernels" }
        check(index.kernels.any { it.id == index.defaultKernel }) { "Invalid default kernel" }
        index.kernels.forEach(::validateKernel)
    }

    private fun validateCustomIndex(index: Index) {
        validateIndex(index)
        check(index.release.kind == "custom") { "Kernel plugin must be a custom release" }
        val builder = index.builder ?: error("Kernel plugin builder metadata is missing")
        check(builder.format == CUSTOM_PLUGIN_FORMAT && builder.workflow == CERTIFIED_WORKFLOW) {
            "Kernel plugin was not produced by the certified workflow"
        }
        check(builder.repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Kernel plugin builder repository is invalid"
        }
        check(index.kernels.size == 1) { "Kernel plugin must contain exactly one kernel" }
    }

    private fun validateKernel(kernel: Kernel) {
        check(isKernelId(kernel.id)) { "Invalid kernel channel" }
        // 接受“kernel-{id}.so.xz”（默认ABI）和“kernel-{id}-{abi}.so.xz”（其他ABI）两种格式。
        check(
            kernel.asset == "kernel-${kernel.id}.so.xz" ||
            kernel.asset.matches(Regex("kernel-${kernel.id}-[a-z0-9_-]+\\.so\\.xz"))
        ) { "Invalid kernel asset" }
        check(kernel.compression == "xz") { "Unsupported kernel compression" }
        check(kernel.sizeBytes > 0 && kernel.sizeBytes <= MAX_PLUGIN_BYTES) { "Invalid kernel size" }
        check(kernel.downloadUrl.startsWith("https://")) { "Kernel download must use HTTPS" }
        check(kernel.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid kernel checksum" }
        check(kernel.commit.matches(Regex("[0-9a-f]{40}"))) { "Invalid kernel commit" }
        check(kernel.capabilities.all(::isCapability)) { "Invalid kernel capability" }
    }

    private fun isKernelId(id: String): Boolean =
        id.matches(Regex("[a-z0-9][a-z0-9-]*")) && id.length <= 48

    private fun isCapability(value: String): Boolean =
        value.matches(Regex("[a-z0-9][a-z0-9-]*")) && value.length <= 48

    private fun metadataFile(context: Context, id: String): File =
        File(libraryDirectory(context), "$CORE_FILE_PREFIX$id$CORE_METADATA_SUFFIX")

    private fun readInstalledKernel(context: Context, id: String): InstalledKernel? =
        runCatching {
            json.decodeFromString<InstalledKernel>(metadataFile(context, id).readText())
        }.getOrNull()?.takeIf { metadata ->
            metadata.id == id && metadata.commit.matches(Regex("[0-9a-f]{6,40}"))
        }

    private fun writeInstalledKernel(context: Context, kernel: Kernel) =
        writeInstalledKernel(
            context,
            InstalledKernel(
                kernel.id,
                kernel.name,
                kernel.version,
                kernel.commit,
                kernel.capabilities,
            ),
        )

    private fun writeInstalledKernel(context: Context, metadata: InstalledKernel) {
        val target = metadataFile(context, metadata.id)
        val pending = File(target.parentFile, "${target.name}.pending")
        pending.writeText(json.encodeToString(metadata))
        if (target.exists()) check(target.delete()) { "Unable to replace kernel metadata" }
        check(pending.renameTo(target)) { "Unable to install kernel metadata" }
    }

    private fun libraryDirectory(context: Context): File = context.filesDir.resolve("lib")

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    /**
     * 验证 [file] 是适用于 [abi] 的有效 mihomo 内核二进制。
     * 检查 ELF 头、机器类型，并扫描识别 `MihomoMain` / `coreInit` 导出符号。
     */
    private fun isValidCore(file: File, abi: String): Boolean = runCatching {
        if (!file.isFile || file.length() < 64) return false
        val data = file.inputStream().use { it.readNBytes(64) }
        if (data.size < 64 || !data.sliceArray(0..3).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
            return false
        }
        val elfClass = data[4].toInt() and 0xff
        val expected = when (abi) {
            "armeabi-v7a" -> 1; "arm64-v8a" -> 2; "x86" -> 1; "x86_64" -> 2
            else -> return false
        }
        if (elfClass != expected) return false

        val machine = ((data[19].toInt() and 0xff) shl 8) or (data[18].toInt() and 0xff)
        val expectedMachine = when (abi) {
            "armeabi-v7a" -> 40; "arm64-v8a" -> 183; "x86" -> 3; "x86_64" -> 62
            else -> return false
        }
        if (machine != expectedMachine) return false

        // 扫描查找已识别的 Go cgo 导出符号
        val buffer = ByteArray(64 * 1024)
        var carry = ""
        file.inputStream().buffered().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val chunk = carry + String(buffer, 0, count, StandardCharsets.ISO_8859_1)
                if (chunk.contains("MihomoMain") || chunk.contains("coreInit")) return true
                carry = chunk.takeLast(80)
            }
        }
        false
    }.onFailure {
        Timber.tag("KernelManager").w(it, "Kernel verification failed")
    }.getOrDefault(false)
}
