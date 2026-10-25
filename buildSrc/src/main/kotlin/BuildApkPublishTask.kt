import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

private val MMKV_64_ABIS = setOf("arm64-v8a", "x86_64")

/**
 * 自检并发布最终 APK 与 `.apk.json` 身份边车到 `output_apk/<buildType>/`（打包契约自 kotlin-toolchain 管线回移）。
 *
 * 每个产物依次校验：
 * 1. zip 完整性：逐条目读取并核对 CRC；
 * 2. 必备原生库：`lib/<abi>/<pattern>` 按通配符匹配（如 libmihomo.so / liboverride.so / libmmkv.so / libjavet*.so）；
 * 3. 身份：`aapt2 dump badging` 解析 applicationId/versionCode/versionName 与预期一致；
 * 4. 对齐：`zipalign -c -P 16 4`（16 KB 页对齐原生库 + 4 字节通用对齐）；
 * 5. 签名：`apksigner verify`；提供签名身份时另比对证书 SHA-256 指纹与 keystore 一致。
 *
 * 发布走临时文件 + ATOMIC_MOVE，消费者不会读到半成品。AGP 的 zipflinger 已对条目时间戳归一化 （恒定 1981-01-01），无需重打包即具备确定性 zip；因此这里不做解包重签，只做校验与发布。
 */
@DisableCachingByDefault(because = "Verification depends on the local Android SDK tools and signing identity")
abstract class BuildApkPublishTask : DefaultTask() {
    /** 带签名的最终 APK 目录（SingleArtifact.APK）。 */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkDir: DirectoryProperty
    /** abi（split 标识；分线合包产物为 universal32/universal64，4 ABI 总 universal 已弃用）→ 文件名。 */
    @get:Input
    abstract val outputNames: MapProperty<String, String>
    /** 本次运行所属 MMKV 产物线要发布的 ABI 集合（另一条线的 ABI 由另一轮构建发布）。 */
    @get:Input
    abstract val publishAbis: SetProperty<String>
    /** 该模块的 ABI 全集（universal/universal32/universal64 产物的必备库校验范围）。 */
    @get:Input
    abstract val universalAbis: ListProperty<String>
    /** 每个目标 ABI 下必须存在的原生库通配模式（如 libmihomo.so、libjavet*.so）。 */
    @get:Input
    abstract val requiredNativePatterns: ListProperty<String>
    @get:Input
    abstract val applicationId: Property<String>
    @get:Input
    abstract val versionCode: Property<String>
    @get:Input
    abstract val versionName: Property<String>
    @get:Input
    abstract val moduleName: Property<String>
    @get:Input
    abstract val variantName: Property<String>
    /** geo 资产标签（app 内置 geo 为 "builtin"，extension 为 "none"）。 */
    @get:Input
    abstract val geoLabel: Property<String>
    /** 发布产物对应的 MMKV 版本（extension 无 MMKV，为 "none"）。 */
    @get:Input
    abstract val mmkvVersion: Property<String>
    @get:InputDirectory
    abstract val buildToolsDir: DirectoryProperty
    @get:Input
    @get:Optional
    abstract val signingAlias: Property<String>
    @get:Input
    @get:Optional
    abstract val signingStorePassword: Property<String>
    @get:Input
    @get:Optional
    abstract val signingKeyPassword: Property<String>
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val signingStore: RegularFileProperty
    @get:OutputDirectory
    abstract val publishDir: DirectoryProperty
    @TaskAction
    fun publish() {
        val buildTools = buildToolsDir.get().asFile
        val zipalign = tool(buildTools, "zipalign")
        val aapt2 = tool(buildTools, "aapt2")
        val apksigner = File(buildTools, "lib/apksigner.jar")
        require(zipalign.isFile && aapt2.isFile && apksigner.isFile) { "Android build-tools incomplete under $buildTools (zipalign/aapt2/apksigner)" }
        val java = File(System.getProperty("java.home"), "bin/" + if (isWindows()) "java.exe" else "java")
        val signing = signingIdentity()
        val outputs = outputNames.get().toSortedMap()
        val targetDir = publishDir.get().asFile.apply { mkdirs() }
        val published = mutableListOf<String>()
        outputs.forEach { (abi, fileName) ->
            if (abi !in publishAbis.get()) {
                logger.lifecycle("Skip $fileName (belongs to the other MMKV line's packaging run)")
                return@forEach
            }
            val source = apkDir.file(fileName).get().asFile
            require(source.isFile) { "Expected APK missing: $source; directory contains: ${apkDir.get().asFile.listFiles()?.joinToString { it.name }}" }
            verifyZipEntries(source)
            verifyRequiredNatives(source, abi)
            verifyBadging(source, aapt2)
            runTool(zipalign, "-c", "-P", "16", "4", source.path)
            val signed = verifySignature(source, apksigner, java, signing)
            val digest = sha256(source)
            val sidecar = buildString {
                appendLine("{")
                appendLine("""  "module": ${jsonString(moduleName.get())},""")
                appendLine("""  "variant": ${jsonString(variantName.get())},""")
                appendLine("""  "geo": ${jsonString(geoLabel.get())},""")
                appendLine("""  "abi": ${jsonString(abi)},""")
                appendLine("""  "mmkv": ${jsonString(mmkvVersion.get())},""")
                appendLine("""  "signed": $signed,""")
                appendLine("""  "sha256": ${jsonString(digest)},""")
                appendLine("""  "builder": "gradle-agp"""")
                appendLine("}")
            }
            val target = File(targetDir, source.name)
            val temporary = File(targetDir, ".publishing-${UUID.randomUUID()}-${source.name}")
            try {
                source.copyTo(temporary, overwrite = true)
                atomicMove(temporary, target)
            } finally {
                temporary.delete()
            }
            val sidecarTarget = File(targetDir, source.name + ".json")
            val sidecarTemporary = File(targetDir, ".publishing-${UUID.randomUUID()}-${source.name}.json")
            try {
                sidecarTemporary.writeText(sidecar)
                atomicMove(sidecarTemporary, sidecarTarget)
            } finally {
                sidecarTemporary.delete()
            }
            published += target.name
            logger.lifecycle("Verified APK: ${target.path} (sha256=$digest, signed=$signed)")
        }
        if (published.isEmpty()) {
            // 正常出现在双线循环里不持有产物的那一轮（如 :extension 在 mmkv32 线没有产物）。
            logger.lifecycle("No ${moduleName.get()}/${variantName.get()} APK belongs to this MMKV line (publishAbis=${publishAbis.get()}, produced=${outputs.keys})")
        }
    }
    private fun verifyZipEntries(source: File) {
        ZipFile(source).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val crc = CRC32()
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        crc.update(buffer, 0, read)
                    }
                }
                require(entry.crc == -1L || crc.value == entry.crc) { "Corrupt APK entry ${entry.name} in ${source.name}" }
            }
        }
    }
    private fun verifyRequiredNatives(source: File, abi: String) {
        val targetAbis = when (abi) {
            "universal32" -> universalAbis.get().filterNot { it in MMKV_64_ABIS }
            "universal64" -> universalAbis.get().filter { it in MMKV_64_ABIS }
            else -> listOf(abi)
        }
        ZipFile(source).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            val actualAbis = names.asSequence().filter { it.startsWith("lib/") }.map { it.removePrefix("lib/").substringBefore('/') }.toSet()
            require(actualAbis == targetAbis.toSet()) { "Unexpected ABI set in ${source.name}: expected ${targetAbis.sorted()} but found ${actualAbis.sorted()}" }
            if (requiredNativePatterns.get().isEmpty()) return
            targetAbis.forEach { targetAbi ->
                requiredNativePatterns.get().forEach { pattern ->
                    val expected = "lib/$targetAbi/$pattern"
                    val found = names.any { name -> name.startsWith("lib/$targetAbi/") && wildcardMatch(pattern, name.substringAfterLast('/')) }
                    require(found) { "Missing required native $expected in ${source.name}" }
                }
            }
        }
    }
    private fun verifyBadging(source: File, aapt2: File) {
        val output = captureTool(aapt2, "dump", "badging", source.path)
        val packageLine = output.lineSequence().firstOrNull { it.startsWith("package:") }
            ?: error("aapt2 dump badging produced no package line for ${source.name}")
        val name = badgingValue(packageLine, "name")
        val code = badgingValue(packageLine, "versionCode")
        val version = badgingValue(packageLine, "versionName")
        require(name == applicationId.get() && code == versionCode.get() && version == versionName.get()) {
            "APK identity mismatch in ${source.name}: expected id=${applicationId.get()} versionCode=${versionCode.get()} versionName=${versionName.get()}, " +
                "found id=$name versionCode=$code versionName=$version"
        }
    }
    private fun verifySignature(source: File, apksigner: File, java: File, signing: ApkSigningIdentity?): Boolean {
        val verified = runTool(java, "-jar", apksigner.path, "verify", "--verbose", source.path, allowFailure = true)
        if (!verified) {
            require(signing == null) { "APK signature verification failed for ${source.name} although a signing identity is configured" }
            logger.warn("WARNING: ${source.name} is published unsigned (no signing.properties); sidecar records signed=false")
            return false
        }
        if (signing != null) {
            val output = captureTool(java, "-jar", apksigner.path, "verify", "--print-certs", source.path)
            val actual = Regex("certificate SHA-256 digest: ([a-fA-F0-9]+)").findAll(output).map { it.groupValues[1].lowercase() }.toSet()
            val keyStore = KeyStore.getInstance(signing.store, signing.storePassword.toCharArray())
            val expected = MessageDigest.getInstance("SHA-256").digest(keyStore.getCertificate(signing.alias).encoded).joinToString("") { "%02x".format(it) }
            require(actual == setOf(expected)) { "Final APK certificate does not match the requested signing identity for ${source.name}" }
        }
        return true
    }
    private fun signingIdentity(): ApkSigningIdentity? {
        if (!signingStore.isPresent || !signingAlias.isPresent) return null
        val store = signingStore.get().asFile
        require(store.isFile) { "Signing keystore not found: $store" }
        return ApkSigningIdentity(
            store = store,
            alias = signingAlias.get(),
            storePassword = signingStorePassword.getOrElse(""),
            keyPassword = signingKeyPassword.getOrElse(""),
        )
    }
    private fun runTool(tool: File, vararg arguments: String, allowFailure: Boolean = false): Boolean {
        val command = toolCommand(tool) + arguments
        val process = ProcessBuilder(command).inheritIO().start()
        val exit = process.waitFor()
        if (allowFailure) return exit == 0
        check(exit == 0) { "Android tool failed (exit $exit): ${command.joinToString(" ")}" }
        return true
    }
    private fun captureTool(tool: File, vararg arguments: String): String = captureToolCommand(toolCommand(tool) + arguments)
    private fun captureToolCommand(command: List<String>): String {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        check(exit == 0) { "Android tool failed (exit $exit): ${command.joinToString(" ")}" }
        return output
    }
    private fun toolCommand(tool: File): List<String> = if (tool.extension == "jar") listOf(tool.path) else listOf(tool.path)
    private fun tool(buildTools: File, name: String): File = buildTools.resolve(if (isWindows()) "$name.exe" else name)
    private fun badgingValue(line: String, key: String): String? = Regex("""$key='([^']*)'""").find(line)?.groupValues?.get(1)
    private fun wildcardMatch(pattern: String, name: String): Boolean = if ('*' in pattern) {
        // 按 * 分段转义后再以 .* 连接；Regex.escape 整体引用会吞掉后续 replace 的通配替换。
        Regex(pattern.split("*").joinToString(".*") { Regex.escape(it) }).matches(name)
    } else {
        name == pattern
    }
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")
}

/** 签名身份（signing.properties + keystore），与 android.signingConfigs 同源。 */
data class ApkSigningIdentity(
    val store: File,
    val alias: String,
    val storePassword: String,
    val keyPassword: String,
)

fun resolveAndroidSdk(project: Project): File {
    val localProperties = project.rootProject.file("local.properties")
    if (localProperties.isFile) {
        val properties = Properties().apply { localProperties.inputStream().use(::load) }
        properties.getProperty("sdk.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
    }
    sequenceOf("ANDROID_SDK_ROOT", "ANDROID_HOME").forEach { variable ->
        System.getenv(variable)?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
    }
    error("Android SDK not found; set sdk.dir in local.properties or ANDROID_SDK_ROOT")
}

/** build-tools 定位：优先 android.buildToolsVersion（与 AGP 同一钉版），否则取 SDK 中已安装的最高版本。 */
fun resolveBuildToolsDir(project: Project): File {
    val sdk = resolveAndroidSdk(project)
    val root = sdk.resolve("build-tools")
    val installed = root.listFiles()?.filter { it.isDirectory }.orEmpty()
    require(installed.isNotEmpty()) { "No build-tools installed under $root" }
    val pinned = project.providers.gradleProperty("android.buildToolsVersion").orNull?.trim()?.takeIf { it.isNotEmpty() }
    val selected = pinned?.let { version ->
        installed.firstOrNull { it.name == version }
            ?: error("Pinned build-tools $version not installed under $root")
    } ?: installed.maxByOrNull { directory ->
        directory.name.split('.').fold(0L) { acc, part -> acc * 1000 + (part.toIntOrNull() ?: 0) }
    }!!
    return selected
}

fun resolveSigningIdentity(project: Project): ApkSigningIdentity? {
    val file = project.rootProject.file("signing.properties")
    if (!file.isFile) return null
    val properties = Properties().apply { file.inputStream().use(::load) }
    fun required(key: String) = requireNotNull(properties.getProperty(key)) { "Missing signing property $key" }
    val store = project.rootProject.file(properties.getProperty("keystore.path", "release.keystore"))
    require(store.isFile) { "Signing keystore not found: $store" }
    return ApkSigningIdentity(
        store = store,
        alias = required("key.alias"),
        storePassword = required("keystore.password"),
        keyPassword = required("key.password"),
    )
}
