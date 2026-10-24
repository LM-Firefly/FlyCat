package dev.flycat.build

import java.io.File
import java.security.MessageDigest

class ToolchainBackend(
    private val root: File,
    graph: RuntimeGraph,
    private val resourceAars: List<ResourceAar>,
    private val flavor: String,
) {
    private val excludedNames = graph.excluded.flatMap { coordinate ->
        val classifier = coordinate.classifier.takeIf(String::isNotEmpty)?.let { "-$it" }.orEmpty()
        listOf("${coordinate.artifact}-${coordinate.version}$classifier.jar", "${coordinate.artifact}-${coordinate.version}$classifier.aar")
    }.sorted()
    // 0.13.0 没有脱糖/排除 DSL。这是一个内部后端垫片，而非项目构建：编排、清单、原生负载和签名仍使用 Kotlin。
    // 版本下限必须与 gradle/libs.versions.toml（@keep 安全下限）保持同步，以及 DependencyGraph.kt（绑定强制执行）。
    // 注意：依赖项由 Kotlin 工具链自身预先解析，因此下面的 useVersion 下限仅作为纵深防御；权威版本为 gradle/libs.versions.toml 中的目录固定版本。
    private val initScript = """
        import org.gradle.api.artifacts.FileCollectionDependency
        import java.security.MessageDigest
        import java.util.zip.ZipFile
        import java.util.zip.ZipOutputStream
        import java.util.zip.ZipEntry
        def excludedNames = [${excludedNames.joinToString(",") { "'" + it.replace("'", "\\'") + "'" }}] as Set
        def resourceAars = [${resourceAars.joinToString(",") { "'" + it.module + "':'" + it.file.invariantSeparatorsPath.replace("'", "\\'") + "'" }.ifEmpty { ":" }}]
        def overlayAar = { File original, File resource, File directory ->
            directory.mkdirs()
            synchronized (directory.absolutePath.intern()) {
                def digest = MessageDigest.getInstance('SHA-256')
                digest.update(original.bytes)
                digest.update(resource.bytes)
                def output = new File(directory, original.name.substring(0, original.name.length() - 4) + '-' + digest.digest().encodeHex().toString() + '.aar')
                if (output.isFile()) return output
                def entries = [:]
                new ZipFile(original).withCloseable { zip -> zip.entries().each { entry -> if (!entry.directory) entries[entry.name] = zip.getInputStream(entry).bytes } }
                new ZipFile(resource).withCloseable { zip -> zip.entries().each { entry -> if (!entry.directory && entry.name != 'classes.jar') entries[entry.name] = zip.getInputStream(entry).bytes } }
                def temporary = File.createTempFile('overlay-', '.aar', directory)
                new ZipOutputStream(new FileOutputStream(temporary)).withCloseable { zip -> entries.each { name, bytes -> def entry = new ZipEntry(name); entry.time = 315532800000L; zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry() } }
                java.nio.file.Files.move(temporary.toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return output
            }
        }
        allprojects { p ->
            p.plugins.withId('com.android.application') {
                // 之前只有主应用启用了核心库脱糖；扩展边车从未启用过（且其空程序会导致 L8 脱糖步骤缺失）。
                if (p.name == 'app') {
                    p.extensions.getByName('android').compileOptions.coreLibraryDesugaringEnabled = true
                    p.dependencies.add('coreLibraryDesugaring', 'com.android.tools:desugar_jdk_libs:2.1.5')
                }
                if (p.rootDir.path.contains('_prepareAndroid')) resourceAars.values().each { p.dependencies.add('implementation', p.files(it)) }
            }
            p.configurations.configureEach { configuration ->
                configuration.withDependencies { dependencies ->
                    def changes = []
                    dependencies.findAll { it instanceof FileCollectionDependency }.each { dependency ->
                        def original = dependency.files.files
                        def kept = original.findAll { !excludedNames.contains(it.name) }.collect { file ->
                            def module = file.name.endsWith('-jvm.aar') ? file.name.substring(0, file.name.length() - '-jvm.aar'.length()) : null
                            if (!p.rootDir.path.contains('_prepareAndroid') && module != null && resourceAars.containsKey(module) && file.toURI().path.contains('/tasks/_' + module + '_aarAndroid')) {
                                return overlayAar(file, new File(resourceAars[module]), new File(p.layout.buildDirectory.get().asFile, 'flycat-aars'))
                            }
                            return file
                        }
                        if (kept.toSet() != original.toSet()) changes.add([dependency, kept])
                    }
                    changes.each { change ->
                        dependencies.remove(change[0])
                        if (!change[1].isEmpty()) dependencies.add(p.dependencies.create(p.files(change[1])))
                    }
                }
                configuration.resolutionStrategy.eachDependency { details ->
                    def group = details.requested.group
                    def name = details.requested.name
                    if (group == 'com.google.protobuf') details.useVersion('3.25.9')
                    if (group == 'org.bouncycastle') details.useVersion('1.86')
                    if (group == 'io.netty') details.useVersion('4.2.17.Final')
                    if (group == 'com.google.guava' && name == 'guava') details.useVersion('32.0.0-android')
                    if (group == 'org.apache.httpcomponents' && name == 'httpclient') details.useVersion('4.5.14')
                    if (group == 'org.apache.commons' && name == 'commons-lang3') details.useVersion('3.20.0')
                    if (group == 'com.fasterxml.jackson.core' && name == 'jackson-core') details.useVersion('2.22.0')
                    if (group == 'org.jdom' && name == 'jdom2') details.useVersion('2.0.6.1')
                    if (group == 'org.bitbucket.b_c' && name == 'jose4j') details.useVersion('0.9.6')
                }
            }
        }
    """.trimIndent()
    // flavor 标记将每个 MMKV 产品线隔离到各自独立的构建根目录中，从而确保两种 ABI 变体永远不会共享（并因此冲突）增量任务状态。
    private val fingerprint = MessageDigest.getInstance("SHA-256").digest(("0.13.0/resource-symbols-v1/nontransitive-true/nonfinal-true/mmkv-$flavor\n" + initScript).toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    val buildRoot: File = root.resolve("build/toolchain-$fingerprint")
    private val gradleHome = root.resolve(".kotlin/flycat/gradle-home")
    fun build(module: String, variant: String) {
        require(root.resolve("kotlin.bat").readText().contains("set kotlin_cli_version=0.13.0")) { "Update the Android output adapter before changing Toolchain version" }
        gradleHome.resolve("init.d/flycat.gradle").apply { parentFile.mkdirs(); writeText(initScript) }
        gradleHome.resolve("gradle.properties").writeText(listOf("org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g", "org.gradle.caching=true", "android.nonTransitiveRClass=true", "android.nonFinalResIds=true", "").joinToString(System.lineSeparator()))
        println("Toolchain backend: $fingerprint (mmkv flavor $flavor); excluded runtime artifacts: ${excludedNames.joinToString()}")
        // 当 ANDROID_HOME 未设置时，后端会自动配置其自带的 SDK；建议优先使用本机 local.properties 中的路径，以避免本地运行时下载未授权的重复副本。
        val environment = mutableMapOf("GRADLE_USER_HOME" to gradleHome.path)
        if (System.getenv("ANDROID_HOME").isNullOrBlank()) {
            loadProperties(root.resolve("local.properties")).getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let { environment["ANDROID_HOME"] = it }
        }
        toolchain(root, listOf("build", "--module=$module", "--variant=$variant", "--build-dir=${buildRoot.path}"), environment)
    }
    fun apk(module: String, variant: String): File {
        val suffix = variant.replaceFirstChar(Char::uppercaseChar)
        val directory = buildRoot.resolve("tasks/_${module}_buildAndroid$suffix")
        val candidates = directory.listFiles().orEmpty().filter { it.isFile && it.extension == "apk" }
        require(candidates.size == 1) { "Expected one exported APK for $module/$variant in $directory, found ${candidates.size}; update the pinned adapter" }
        return candidates.single()
    }
    fun sdk(module: String, variant: String): File {
        val suffix = variant.replaceFirstChar(Char::uppercaseChar)
        val local = loadProperties(buildRoot.resolve("tasks/_${module}_prepareAndroid$suffix/gradle-project/local.properties"))
        return File(requireNotNull(local.getProperty("sdk.dir")) { "Toolchain Android SDK handoff unavailable" })
    }
}

fun toolchain(root: File, arguments: List<String>, environment: Map<String, String> = emptyMap(), capture: Boolean = false): String {
    val command = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd.exe", "/d", "/c", root.resolve("kotlin.bat").path) else listOf("sh", root.resolve("kotlin").path)
    val builder = ProcessBuilder(command + arguments).directory(root).redirectError(ProcessBuilder.Redirect.INHERIT)
    builder.environment().putAll(environment)
    if (!capture) builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    val process = builder.start()
    val output = if (capture) process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else ""
    check(process.waitFor() == 0) { "Kotlin Toolchain command failed: ${arguments.first()}" }
    return output
}
