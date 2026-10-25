package dev.flycat.build

import dev.flycat.packer.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

//** 保持与之前的 BuildSoPayloadTask 一致：priv-kit starter 必须保持可被链接器加载。 */
private val BOOTSTRAP_LIBRARIES = setOf("libprivkitstarter.so")
private val GEO_ASSETS = listOf("geoip.metadb.xz", "geosite.dat.xz", "ASN.mmdb.xz", "BundleMRS.7z")
private val REQUIRED_NATIVES = listOf("libmihomo.so", "liboverride.so")

fun packageApplication(arguments: Array<String>) {
    if (arguments.any { it == "--help" }) {
        println("kotlin run -m packaging -- --module=app --build-type=release [-Pkey=value] [--with-extension] [--compile-only] [--allow-unsigned] [--javet-assets]")
        return
    }
    val options = linkedMapOf<String, String>()
    val flags = mutableSetOf<String>()
    arguments.forEach { argument ->
        when {
            argument.startsWith("-P") && '=' in argument -> options[argument.substring(2).substringBefore('=')] = argument.substringAfter('=')
            argument.startsWith("--") && '=' in argument -> options[argument.substring(2).substringBefore('=')] = argument.substringAfter('=')
            argument in setOf("--compile-only", "--allow-unsigned", "--with-extension", "--javet-assets") -> flags += argument
            else -> error("Unknown packaging argument: $argument")
        }
    }
    val accepted = setOf(
        "module", "build-type", "build.allAbis", "build.number", "build.hash", "build.branch",
        "update.uiBuildId", "update.repository", "update.source", "update.mirrorTemplates",
        "update.channel", "update.tag", "update.metaAssetName",
        "apk.output.prefix", "apk.output.channel",
        "core.branch", "core.commit", "android.injected.build.abi",
    )
    require(options.keys.all { it in accepted }) { "Unsupported packaging options: ${options.keys - accepted}" }
    val root = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
        .firstOrNull { it.resolve("product.properties").isFile && it.resolve("project.yaml").isFile }
        ?: error("Run packaging from the FlyCat checkout")
    val state = root.resolve(".kotlin/flycat").apply { mkdirs() }
    FileChannel.open(state.resolve("packaging.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        channel.lock().use { packageLocked(root, state, options, flags) }
    }
}

private fun packageLocked(root: File, state: File, options: Map<String, String>, flags: Set<String>) {
    val module = options["module"] ?: "app"
    val variant = options["build-type"] ?: "debug"
    require(module in setOf("app", "extension") && variant in setOf("debug", "release")) { "Unsupported module or build type" }
    val catalog = root.resolve("gradle/libs.versions.toml").readText()
    val maven = MavenFiles(root)
    if ("--javet-assets" in flags) {
        javetAssets(root, maven, catalog)
        return
    }
    val request = loadProperties(root.resolve("product.properties")).apply {
        options.filterKeys { it != "module" && it != "build-type" }.forEach { (key, value) -> setProperty(key, value) }
        setProperty("build.type", variant)
        setProperty("build.withExtension", ("--with-extension" in flags).toString())
        if (getProperty("update.uiBuildId").isNullOrBlank()) setProperty("update.uiBuildId", defaultUiBuildId(root))
    }
    resolveCoreIdentity(root, request).forEach { (key, value) -> request.setProperty(key, value) }
    val plan = requestPlan(request)
    val requestFile = state.resolve("request.properties")
    val serialized = StringWriter().also { request.store(it, null) }.toString().lineSequence()
        .filter { it.isNotEmpty() && !it.startsWith('#') }.sorted().joinToString(System.lineSeparator(), postfix = System.lineSeparator())
    if (!requestFile.isFile || requestFile.readText() != serialized) requestFile.writeText(serialized)
    println("Packaging $module $variant: ${plan.versionName} (${plan.versionCode})")
    val graphText = toolchain(root, listOf("--log-level=error", "show", "dependencies", "--module=$module", "--platform-group=android", "--scope=runtime", "--exclude-tests"), capture = true)
    val graph = runtimeGraph(graphText)
    val tools = AndroidTools(androidSdk(root))
    val resources = resourceAars(root, tools)
    val workspace = state.resolve("runs/${UUID.randomUUID()}").apply { mkdirs() }
    val manifest = ApkManifest(tools, workspace)
    val signing = signingIdentity(root, tools, variant, "--allow-unsigned" in flags)
    val strip = nativeStripper(root, request)
    if (module == "extension") {
        val backend = ToolchainBackend(root, graph, resources, "ext")
        backend.build(module, variant)
        if ("--compile-only" in flags) return
        val raw = backend.apk(module, variant)
        check(tools.sdk.canonicalFile == backend.sdk(module, variant).canonicalFile) { "Toolchain SDK and packaging SDK differ" }
        val abis = propertyList(request, "abi.extension.list")
        abis.forEach { abi ->
            val rewritten = manifest.rewrite(raw, plan.versionCode, plan.baseVersionName)
            val prepared = workspace.resolve("prepared-$abi.apk")
            packApk(rewritten, prepared, emptyMap(), { name -> name.startsWith("lib/") && !name.startsWith("lib/$abi/") }, compressNative = false)
            val ready = workspace.resolve("ready-$abi.apk")
            tools.finish(prepared, ready, signing)
            tools.verify(ready, signing)
            manifest.verify(ready, plan.copy(applicationId = plan.applicationId + ".extension", versionName = plan.baseVersionName))
            publish(root, variant, plan.extensionApkFileName(abi, variant), ready, module, abi = abi, mmkv = "none", signed = signing != null)
        }
        return
    }
    // MMKV 保持按 ABI 拆分的方式，与历史上按目标 ABI 构建的做法一致：mmkv32 系列 （1.3.14）是唯一提供 32 位原生库的版本，而 64 位产物则使用 mmkv64 系列 （2.4.3）。
    // 这两个系列具有不兼容的 JNI 注册签名，绝不能在同一个 APK 中混用，因此每个 flavor 都是一次完整的工具链构建（编译 + 打包），其依赖解析通过一个临时的根目录目录覆盖（withMmkvCatalog）来选择。
    val rawByFlavor = mutableMapOf<String, File>()
    listOf("mmkv32" to false, "mmkv64" to true).forEach { (flavor, use64BitLine) ->
        val backend = ToolchainBackend(root, graph, resources, flavor)
        withMmkvCatalog(root, use64BitLine) { backend.build(module, variant) }
        check(tools.sdk.canonicalFile == backend.sdk(module, variant).canonicalFile) { "Toolchain SDK and packaging SDK differ" }
        rawByFlavor[flavor] = backend.apk(module, variant)
    }
    if ("--compile-only" in flags) return
    val withExtension = "--with-extension" in flags
    val allAbis = propertyList(request, "abi.app.list")
    val packagingAbis = if (withExtension) propertyList(request, "abi.extension.list") else allAbis
    val universal = request.getProperty("build.allAbis", "false").toBoolean() || withExtension
    val geoAdditions = geoAdditions(root)
    val licenseBytes = collectLicenses(root, graph.included, maven)
    val rewrittenByFlavor = mutableMapOf<String, File>()
    val outputs = buildList {
        packagingAbis.forEach { abi ->
            val flavor = if (abi == "arm64-v8a" || abi == "x86_64") "mmkv64" else "mmkv32"
            add(Triple(abi, listOf(abi), flavor))
        }
        // 合包按位宽拆分为 universal32/universal64（4 ABI 总 universal 已弃用）：单 APK 的 dex 全 ABI 共享，
        // 而两线 JNI 注册签名不可混包，故合包只能各自携带本线 ABI 与本线 MMKV（universal32→mmkv32 线、universal64→mmkv64 线）。
        if (universal) {
            add(Triple("universal32", allAbis.filterNot { it == "arm64-v8a" || it == "x86_64" }, "mmkv32"))
            add(Triple("universal64", allAbis.filter { it == "arm64-v8a" || it == "x86_64" }, "mmkv64"))
        }
    }
    outputs.forEach { (abiName, targetAbis, flavor) ->
        val raw = rawByFlavor.getValue(flavor)
        val rewritten = rewrittenByFlavor.getOrPut(flavor) { manifest.rewrite(raw, plan.versionCode, plan.versionName) }
        val prepared = workspace.resolve("prepared-$abiName.apk")
        val additions = linkedMapOf<String, ByteArray>()
        additions.putAll(geoAdditions)
        additions["aboutlibraries.json"] = licenseBytes
        additions.putAll(injectedNativeAdditions(root, state, targetAbis, compress = variant == "release", strip = strip))
        packApk(rewritten, prepared, additions, { name ->
            when {
                name.startsWith("META-INF/") -> true
                name.startsWith("lib/") && name.substringAfterLast('/').startsWith("libjavet") && !withExtension -> true
                name.startsWith("lib/") && targetAbis.none { name.startsWith("lib/$it/") } -> true
                else -> false
            }
        }, compressNative = variant == "release")
        val ready = workspace.resolve("ready-$abiName.apk")
        tools.finish(prepared, ready, signing)
        tools.verify(ready, signing)
        manifest.verify(ready, plan)
        publish(root, variant, plan.apkFileName(abiName), ready, module, abi = abiName, mmkv = catalogVersion(catalog, flavor), signed = signing != null)
    }
}

 // 为下一次工具链构建选择 MMKV 产品线。Kotlin 工具链每次构建都会从项目目录中解析一个版本；根目录下的 `libs.versions.toml` 会覆盖 `gradle/libs.versions.toml`，
 // 因此 64 位变体通过一个临时的（已被 git 忽略的）根目录目录文件来切换 `mmkv` 条目，而不是修改受版本控制的文件。
 // 该覆盖会在 finally 块和关闭钩子中被再次移除，以确保崩溃的构建不会将其泄漏到后续运行中。
private fun <T> withMmkvCatalog(root: File, use64BitLine: Boolean, block: () -> T): T {
    val override = root.resolve("libs.versions.toml")
    if (!use64BitLine) {
        check(override.delete() || !override.exists()) { "Stale catalog override: $override" }
        return block()
    }
    val needle = "mmkv = { module = \"com.tencent:mmkv\", version.ref = \"mmkv32\" }"
    val catalog = root.resolve("gradle/libs.versions.toml").readText()
    require(needle in catalog) { "Unexpected mmkv catalog entry; update withMmkvCatalog" }
    override.writeText(catalog.replace(needle, needle.replace("mmkv32", "mmkv64")))
    val hook = Thread { override.delete() }
    Runtime.getRuntime().addShutdownHook(hook)
    try {
        return block()
    } finally {
        override.delete()
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }
}

private fun geoAdditions(root: File): Map<String, ByteArray> {
    val geoDir = root.resolve("build/generated/assets/geo")
    return GEO_ASSETS.associateTo(linkedMapOf()) { name ->
        val file = geoDir.resolve(name)
        require(file.isFile) { "Required builtin geo input missing: $file; run kotlin scripts/native-build.main.kts --geo first" }
        "assets/$name" to file.readBytes()
    }
}

private fun injectedNativeAdditions(root: File, state: File, abis: List<String>, compress: Boolean, strip: File?): Map<String, ByteArray> {
    val additions = linkedMapOf<String, ByteArray>()
    abis.forEach { abi ->
        val nativeRoot = root.resolve("jniLibs/$abi")
        REQUIRED_NATIVES.forEach { require(nativeRoot.resolve(it).isFile) { "Required native input missing: jniLibs/$abi/$it; run kotlin scripts/native-build.main.kts first" } }
        nativeRoot.listFiles().orEmpty().filter { it.isFile && it.extension == "so" }.forEach { file ->
            var bytes = file.readBytes()
            if (strip != null && file.name !in BOOTSTRAP_LIBRARIES) bytes = stripBytes(strip, state, bytes)
            if (compress && file.name !in BOOTSTRAP_LIBRARIES) bytes = xzBytes(bytes)
            additions["lib/$abi/${file.name}"] = bytes
        }
    }
    return additions
}

private fun packApk(
    input: File,
    output: File,
    additions: Map<String, ByteArray>,
    exclude: (String) -> Boolean,
    compressNative: Boolean,
) {
    output.parentFile.mkdirs()
    require(input.canonicalFile != output.canonicalFile) { "APK input and output must differ" }
    ZipFile(input).use { source ->
        ZipOutputStream(output.outputStream().buffered()).use { target ->
            source.entries().asSequence().filterNot { it.isDirectory || it.name in additions || exclude(it.name) }.forEach { entry ->
                var bytes = source.getInputStream(entry).use { it.readBytes() }
                val nativeEntry = entry.name.startsWith("lib/") && entry.name.endsWith(".so")
                if (nativeEntry) {
                    if (compressNative && entry.name.substringAfterLast('/') !in BOOTSTRAP_LIBRARIES) bytes = xzBytes(bytes)
                    target.writeEntry(entry.name, bytes, stored = true)
                } else {
                    target.writeEntry(entry.name, bytes, stored = entry.method == ZipEntry.STORED)
                }
            }
            additions.toSortedMap().forEach { (name, bytes) ->
                require(!name.startsWith('/') && name.split('/').none { it == ".." }) { "Unsafe APK entry: $name" }
                target.writeEntry(name, bytes, stored = true)
            }
        }
    }
}

private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray, stored: Boolean) {
    putNextEntry(ZipEntry(name).apply {
        time = 315_532_800_000L
        if (stored) {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = size
            crc = CRC32().apply { update(bytes) }.value
        }
    })
    write(bytes)
    closeEntry()
}

private fun xzBytes(bytes: ByteArray, level: Int = 6): ByteArray = ByteArrayOutputStream().also { out -> XZOutputStream(out, LZMA2Options(level)).use { it.write(bytes) } }.toByteArray()

private fun stripBytes(strip: File, state: File, bytes: ByteArray): ByteArray {
    val temporary = File.createTempFile("strip-", ".so", state)
    try {
        temporary.writeBytes(bytes)
        val process = ProcessBuilder(strip.path, "-s", temporary.path).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        check(process.waitFor() == 0) { "llvm-strip failed" }
        return temporary.readBytes()
    } finally {
        temporary.delete()
    }
}

private fun nativeStripper(root: File, request: Properties): File? {
    val ndkVersion = request.getProperty("android.ndkVersion", "").trim()
    val sdk = androidSdk(root)
    val ndk = listOfNotNull(
        loadProperties(root.resolve("local.properties")).getProperty("ndk.dir")?.takeIf(String::isNotBlank)?.let(::File),
        System.getenv("ANDROID_HOME")?.takeIf(String::isNotBlank)?.let { File(it, "ndk/$ndkVersion") },
        File(sdk, "ndk/$ndkVersion").takeIf(File::isDirectory),
    ).firstOrNull(File::isDirectory) ?: return null
    val host = if (System.getProperty("os.name").startsWith("Windows")) "windows-x86_64" else if (System.getProperty("os.name").startsWith("Mac")) "darwin-x86_64" else "linux-x86_64"
    val name = if (System.getProperty("os.name").startsWith("Windows")) "llvm-strip.exe" else "llvm-strip"
    return ndk.resolve("toolchains/llvm/prebuilt/$host/bin/$name").takeIf(File::isFile).also {
        if (it == null) println("WARNING: NDK llvm-strip not found; native libraries are packaged unstripped")
    }
}

private fun javetAssets(root: File, maven: MavenFiles, catalog: String) {
    val version = catalogVersion(catalog, "javetNodeAndroid")
    val aar = maven.artifact("com.caoccao.javet", "javet-node-android", version, "aar")
    val outDir = root.resolve("build/generated/libjavet").apply { mkdirs() }
    ZipFile(aar).use { zip ->
        listOf("arm64-v8a", "x86_64").forEach { abi ->
            val entry = zip.getEntry("jni/$abi/libjavet-node-android.v.$version.so")
                ?: error("Javet native input missing for $abi in $aar")
            outDir.resolve("libjavet-$abi.so.xz").writeBytes(xzBytes(zip.getInputStream(entry).use { it.readBytes() }, 9))
        }
    }
    outDir.listFiles().orEmpty().forEach { println("Javet asset: ${it.path}") }
}

private fun publish(root: File, variant: String, name: String, ready: File, module: String, abi: String, mmkv: String, signed: Boolean) {
    val output = root.resolve("output_apk/$variant/$name").apply { parentFile.mkdirs() }
    val temporary = File.createTempFile("verified-", ".apk", output.parentFile)
    ready.copyTo(temporary, overwrite = true)
    Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    output.resolveSibling(output.name + ".json").writeText(buildJsonObject {
        put("module", module); put("variant", variant)
        put("abi", abi); put("mmkv", mmkv); put("signed", signed)
        put("sha256", sha256(output)); put("toolchain", "0.13.0")
    }.toString())
    println("Verified APK: ${output.path}")
}

private fun signingIdentity(root: File, tools: AndroidTools, variant: String, allowUnsigned: Boolean): ApkSigning? {
    val file = root.resolve("signing.properties")
    if (file.isFile) {
        val properties = loadProperties(file)
        fun required(key: String) = requireNotNull(properties.getProperty(key)) { "Missing signing property $key" }
        val store = root.resolve(properties.getProperty("keystore.path", "release.keystore"))
        require(store.isFile) { "Signing keystore not found: $store" }
        return ApkSigning(store, required("key.alias"), required("keystore.password"), required("key.password"))
    }
    if (allowUnsigned) return null
    require(variant == "debug") { "Release signing is required; provide signing.properties or explicitly pass --allow-unsigned" }
    val debug = File(System.getProperty("user.home"), ".android/debug.keystore")
    if (!debug.isFile) {
        debug.parentFile.mkdirs()
        tools.run(listOf(File(System.getProperty("java.home"), "bin/keytool").path, "-genkeypair", "-noprompt", "-keystore", debug.path,
            "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US"))
    }
    return ApkSigning(debug, "androiddebugkey", "android", "android")
}

private fun defaultUiBuildId(root: File): String {
    val format = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }
    val buildStamp = format.format(Date())
    val commit = runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short=6", "HEAD").directory(root).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { process.waitFor() == 0 && it.isNotBlank() }
    }.getOrNull() ?: "000000"
    return "$buildStamp-$commit"
}

private fun catalogVersion(catalog: String, key: String): String = Regex("(?m)^${Regex.escape(key)}\\s*=\\s*\"([^\"]+)\"").find(catalog)?.groupValues?.get(1)
    ?: error("Missing $key in gradle/libs.versions.toml")

private fun propertyList(properties: Properties, key: String): List<String> = properties.getProperty(key).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
