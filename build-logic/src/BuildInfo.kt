package dev.flycat.build

import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import kotlinx.serialization.json.*
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction

fun loadProperties(file: File): Properties = Properties().apply {
    if (file.isFile) file.reader(Charsets.UTF_8).use(::load)
}

fun requestPlan(properties: Properties): BuildPlan = planBuild(BuildRequest(
    productName = properties.getProperty("project.name"),
    applicationId = properties.getProperty("project.applicationId"),
    baseVersionName = properties.getProperty("project.version.name"),
    uiBuildId = properties.getProperty("update.uiBuildId"),
    buildHash = properties.getProperty("build.hash"),
    buildBranch = properties.getProperty("build.branch"),
    withExtension = properties.getProperty("build.withExtension", "false").toBooleanStrict(),
    outputPrefix = properties.getProperty("apk.output.prefix"),
    outputChannel = properties.getProperty("apk.output.channel"),
))

fun resolveCoreIdentity(root: File, request: Properties): Map<String, String> {
    val config = loadProperties(root.resolve("kernel.properties"))
    val tree = root.resolve(config.getProperty("external.mihomo.dir", "lib/mihomo/mihomo"))
    val liveCommit = if (tree.isDirectory) runCatching {
        val process = ProcessBuilder("git", "-C", tree.path, "rev-parse", "--short=8", "HEAD")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.inputStream.bufferedReader().use { it.readText().trim() }
            .takeIf { process.waitFor() == 0 && it.isNotEmpty() }
    }.getOrNull() else null
    val stamp = sequenceOf(root.resolve("build/generated/core-version.properties"), root.resolve("jniLibs/arm64-v8a/core-version.properties"))
        .map(::loadProperties).firstOrNull { it.getProperty("core.commit", "unknown") != "unknown" }
        ?: Properties()
    val explicitCommit = request.getProperty("core.commit")?.takeIf(String::isNotBlank)
    val fromStamp = explicitCommit == null && liveCommit == null && stamp.getProperty("core.commit") != null
    val commit = explicitCommit ?: liveCommit ?: stamp.getProperty("core.commit", "unknown")
    val configuredBranch = config.getProperty("external.mihomo.branch", "Alpha") + config.getProperty("external.mihomo.suffix", "")
    val branch = request.getProperty("core.branch")?.takeIf(String::isNotBlank)
        ?: if (fromStamp) stamp.getProperty("core.branch", configuredBranch) else configuredBranch
    val time = if (config.getProperty("external.mihomo.includeTimestamp", "false").toBoolean()) SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date()) else "local"
    val gitVersion = if (fromStamp) stamp.getProperty("core.gitVersion", "${branch.replace('_', '-')}_${commit}_$time") else "${branch.replace('_', '-')}_${commit}_$time"
    return mapOf(
        "CORE_BRANCH" to branch,
        "CORE_COMMIT" to commit,
        "CORE_VERSION" to if (fromStamp) stamp.getProperty("core.displayVersion", "$branch-$commit") else "$branch-$commit",
        "KERNEL_GIT_VERSION" to gitVersion,
    )
}

@TaskAction
fun generateBuildInfo(@Input requestFile: Path, @Input catalogFile: Path, @Input resourceSymbols: Path, namespace: String?, @Output sourceDir: Path) {
    if (namespace == null) {
        val output = sourceDir.toFile().apply { mkdirs() }
        output.walkTopDown().filter(File::isFile).forEach { check(it.delete()) }
        Json.parseToJsonElement(resourceSymbols.toFile().readText()).jsonObject.forEach { (path, contents) ->
            require(path.endsWith(".java") && !path.startsWith('/') && path.split('/').none { it == ".." })
            output.resolve(path).apply { parentFile.mkdirs(); writeText(contents.jsonPrimitive.content) }
        }
        return
    }
    require(requestFile.toFile().isFile) { "Build request missing; use kotlin run -m packaging -- --build-type=release" }
    val request = loadProperties(requestFile.toFile())
    val plan = requestPlan(request)
    val catalog = catalogFile.toFile().readText()
    val javet = Regex("(?m)^javetNodeAndroid\\s*=\\s*\"([^\"]+)\"").find(catalog)?.groupValues?.get(1)
        ?: error("Missing Javet version in catalog")
    val output = sourceDir.toFile().apply { mkdirs() }.resolve("BuildInfo.kt")
    output.writeText(buildString {
        appendLine("package $namespace")
        appendLine("object BuildInfo {")
        appendLine("    const val DEBUG: Boolean = ${request.getProperty("build.type") == "debug"}")
        appendLine("    const val VERSION_CODE: Int = ${plan.versionCode}")
        appendLine("    const val VERSION_NAME: String = ${kotlinString(plan.versionName)}")
        appendLine("    const val BASE_VERSION: String = ${kotlinString(request.getProperty("project.version.name"))}")
        appendLine("    const val APPLICATION_ID: String = ${kotlinString(request.getProperty("project.applicationId"))}")
        appendLine("    const val UI_BUILD_ID: String = ${kotlinString(request.getProperty("update.uiBuildId"))}")
        appendLine("    const val JAVET_VERSION: String = ${kotlinString(javet)}")
        listOf(
            "UPDATE_REPOSITORY" to "update.repository",
            "UPDATE_SOURCE" to "update.source",
            "UPDATE_MIRROR_TEMPLATES" to "update.mirrorTemplates",
            "KERNEL_GIT_VERSION" to "KERNEL_GIT_VERSION",
            "CORE_BRANCH" to "CORE_BRANCH",
            "CORE_COMMIT" to "CORE_COMMIT",
            "CORE_VERSION" to "CORE_VERSION",
        ).forEach { (key, property) ->
            appendLine("    const val $key: String = ${kotlinString(request.getProperty(property, "unknown"))}")
        }
        appendLine("}")
    })
}

private fun kotlinString(value: String): String = buildString {
    append(34.toChar())
    value.forEach { character ->
        when (character.code) {
            34, 36, 92 -> append(92.toChar()).append(character)
            10 -> append(92.toChar()).append('n')
            13 -> append(92.toChar()).append('r')
            else -> append(character)
        }
    }
    append(34.toChar())
}
