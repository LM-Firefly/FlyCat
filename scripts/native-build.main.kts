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

@file:DependsOn("org.tukaani:xz:1.12")

import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class ProjectConfig {
    private val properties = Properties()
    private val kernelFile = File("kernel.properties")
    private val localFile = File("local.properties")
    private val gradleFile = File("gradle.properties")

    init {
        if (kernelFile.exists()) {
            kernelFile.inputStream().use { properties.load(it) }
        }
        if (localFile.exists()) {
            localFile.inputStream().use { properties.load(it) }
        }
        if (gradleFile.exists()) {
            gradleFile.inputStream().use { properties.load(it) }
        }
    }

    fun getString(key: String, default: String = ""): String {
        return System.getProperty(key)
            ?: System.getenv(key.replace('.', '_').uppercase())
            ?: properties.getProperty(key)
            ?: default
    }

    fun getInt(key: String, default: Int): Int {
        return getString(key, default.toString()).toIntOrNull() ?: default
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return getString(key, default.toString()).toBooleanStrictOrNull() ?: default
    }

    fun getCsv(key: String, default: String = ""): List<String> {
        return getString(key, default).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

object SystemDetector {
    val os: String by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "darwin"
            osName.contains("linux") -> "linux"
            else -> "unknown"
        }
    }

    val hostTag: String by lazy {
        val arch = System.getProperty("os.arch")
        when (os) {
            "windows" -> "windows-x86_64"
            "darwin" -> if (arch.contains("aarch64")) "darwin-arm64" else "darwin-x86_64"
            "linux" -> "linux-x86_64"
            else -> "linux-x86_64"
        }
    }

    fun checkCommandExists(cmd: String): Boolean {
        return try {
            val process =
                if (os == "windows") {
                    ProcessBuilder("cmd", "/c", "where", cmd).start()
                } else {
                    ProcessBuilder("which", cmd).start()
                }
            process.waitFor() == 0
        } catch (_: IOException) {
            false
        } catch (_: InterruptedException) {
            false
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val output: String = "",
    val error: String = "",
)

// Deliberate fault barrier: any process launch failure must become CommandResult(success=false).
@Suppress("TooGenericExceptionCaught")
fun executeCommand(
    command: List<String>,
    workingDir: File? = null,
    environment: Map<String, String> = emptyMap(),
    printStdout: Boolean = true,
    printStderr: Boolean = true,
    stderrIsError: Boolean = true,
    stdoutPrefix: String? = "[cmd]",
    stderrPrefix: String? = if (stderrIsError) "[err]" else "[cmd]",
): CommandResult {
    return try {
        val processBuilder = ProcessBuilder(command)
        workingDir?.let { processBuilder.directory(it) }
        processBuilder.environment().putAll(environment)

        val process = processBuilder.start()
        val output = StringBuilder()
        val error = StringBuilder()
        val stdoutThread =
            thread(start = true, name = "stdout-reader") {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line)
                        if (printStdout) {
                            if (stdoutPrefix != null) {
                                println("$stdoutPrefix $line")
                            } else {
                                println(line)
                            }
                        }
                    }
                }
            }
        val stderrThread =
            thread(start = true, name = "stderr-reader") {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        error.appendLine(line)
                        if (printStderr) {
                            if (stderrPrefix != null) {
                                println("$stderrPrefix $line")
                            } else {
                                println(line)
                            }
                        }
                    }
                }
            }

        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()
        CommandResult(
            success = exitCode == 0,
            output = output.toString(),
            error = error.toString(),
        )
    } catch (e: Exception) {
        CommandResult(success = false, error = e.message ?: "Unknown error")
    }
}

class NdkTools(private val config: ProjectConfig) {
    private val sdkDir: File by lazy {
        val path =
            config.getString("sdk.dir", "").takeIf { it.isNotEmpty() }
                ?: System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: error("Android SDK not found. Please configure sdk.dir or ANDROID_HOME.")
        File(path).also {
            require(it.isDirectory) { "Android SDK not found: ${it.absolutePath}" }
        }
    }

    val ndkDir: File by lazy {
        val explicitNdk = config.getString("ndk.dir", "")
        val ndkVersion = config.getString("android.ndkVersion", "")
        val ndkPath =
            explicitNdk.takeIf { it.isNotEmpty() } ?: File(sdkDir, "ndk/$ndkVersion").absolutePath

        File(ndkPath).also {
            require(it.isDirectory) { "NDK not found: ${it.absolutePath}" }
        }
    }

    fun getClangPath(abi: String): String {
        val triple =
            when (abi) {
                "arm64-v8a" -> "aarch64-linux-android"
                else -> throw IllegalArgumentException("Unsupported ABI: $abi")
            }
        val ext = if (SystemDetector.os == "windows") ".cmd" else ""
        return File(
                ndkDir,
                "toolchains/llvm/prebuilt/${SystemDetector.hostTag}/bin/${triple}${getMinAndroidApi()}-clang${ext}",
            )
            .absolutePath
    }

    fun getStripPath(): String {
        val ext = if (SystemDetector.os == "windows") ".exe" else ""
        return File(
                ndkDir,
                "toolchains/llvm/prebuilt/${SystemDetector.hostTag}/bin/llvm-strip${ext}",
            )
            .absolutePath
    }

    fun getMinAndroidApi(): Int = maxOf(config.getInt("android.minSdk", 24), 24)

    fun getCmakePath(): String {
        val ext = if (SystemDetector.os == "windows") ".exe" else ""
        val cmakeRoot = File(sdkDir, "cmake")
        require(cmakeRoot.isDirectory) {
            "CMake not found under Android SDK: ${cmakeRoot.absolutePath}"
        }

        val preferred =
            listOf("3.22.1").map { File(cmakeRoot, "$it/bin/cmake$ext") }.firstOrNull { it.isFile }
        if (preferred != null) {
            return preferred.absolutePath
        }

        return cmakeRoot
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "bin/cmake$ext") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath ?: error("CMake executable not found under ${cmakeRoot.absolutePath}")
    }

    fun getNinjaPath(): String {
        val ext = if (SystemDetector.os == "windows") ".exe" else ""
        val cmakeRoot = File(sdkDir, "cmake")
        require(cmakeRoot.isDirectory) {
            "CMake not found under Android SDK: ${cmakeRoot.absolutePath}"
        }

        val preferred =
            listOf("3.22.1").map { File(cmakeRoot, "$it/bin/ninja$ext") }.firstOrNull { it.isFile }
        if (preferred != null) {
            return preferred.absolutePath
        }

        return cmakeRoot
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "bin/ninja$ext") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath ?: error("Ninja executable not found under ${cmakeRoot.absolutePath}")
    }
}

/**
 * Mihomo core identity stamped next to the native libs so the APK job (which never has the mihomo
 * git tree) can still embed branch + short hash into BuildConfig.
 */
data class CoreVersionStamp(
    val branch: String,
    val commit: String,
    val displayVersion: String,
    val gitVersionArg: String,
    val buildTime: String,
) {
    fun toPropertiesText(): String = buildString {
        appendLine("# Generated by scripts/native-build.main.kts — do not edit.")
        appendLine("core.branch=$branch")
        appendLine("core.commit=$commit")
        appendLine("core.displayVersion=$displayVersion")
        appendLine("core.gitVersion=$gitVersionArg")
        appendLine("core.buildTime=$buildTime")
    }
}

val SUPPORTED_ANDROID_ABI = "arm64-v8a"

fun configuredAbis(config: ProjectConfig): List<String> {
    val abis = config.getCsv("abi.app.list", SUPPORTED_ANDROID_ABI)
    require(abis == listOf(SUPPORTED_ANDROID_ABI)) {
        "Only $SUPPORTED_ANDROID_ABI is supported; configured ABIs: ${abis.joinToString()}"
    }
    return abis
}

// Resolve branch/hash while the mihomo checkout is present (native job / local --go). Prefer `git`
// itself (handles worktrees, packed-refs, remotes) and fall back to kernel.properties labels.
fun resolveCoreVersionStamp(
    config: ProjectConfig,
    mihomoDir: File = File("lib/mihomo/mihomo"),
): CoreVersionStamp {
    val configuredBranch = config.getString("external.mihomo.branch", "Alpha").ifEmpty { "Alpha" }
    val suffix = config.getString("external.mihomo.suffix", "")
    val includeTimestamp = config.getBoolean("external.mihomo.includeTimestamp", false)
    val mihomoRel = config.getString("external.mihomo.dir", "lib/mihomo/mihomo")
    val repoDir = if (mihomoDir.isDirectory) mihomoDir else File(mihomoRel)

    val commit =
        runGit(repoDir, listOf("rev-parse", "--short=8", "HEAD"))?.takeIf {
            it.matches(Regex("[0-9a-fA-F]{4,40}"))
        } ?: "unknown"
    val gitBranch =
        runGit(repoDir, listOf("rev-parse", "--abbrev-ref", "HEAD"))?.takeIf {
            it.isNotEmpty() && it != "HEAD"
        }

    val branchBase = configuredBranch.ifBlank { gitBranch ?: "mihomo" }
    val branchLabel = branchBase + suffix
    val buildTime =
        if (includeTimestamp) {
            SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())
        } else {
            "local"
        }
    val displayVersion = "$branchLabel-$commit"
    val gitVersionArg = "${branchLabel.replace('_', '-')}_${commit}_$buildTime"
    return CoreVersionStamp(
        branch = branchLabel,
        commit = commit,
        displayVersion = displayVersion,
        gitVersionArg = gitVersionArg,
        buildTime = buildTime,
    )
}

fun runGit(repoDir: File, args: List<String>): String? {
    if (!repoDir.isDirectory || !File(repoDir, ".git").exists()) return null
    val result =
        executeCommand(
            command = listOf("git", "-C", repoDir.absolutePath) + args,
            printStdout = false,
            printStderr = false,
            stderrIsError = false,
        )
    if (!result.success) return null
    return result.output.trim().lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/** Write the stamp where Gradle + the native artifact pipeline both look. */
fun writeCoreVersionStamp(stamp: CoreVersionStamp, abis: List<String>) {
    val generated = File("build/generated/core-version.properties")
    generated.parentFile.mkdirs()
    generated.writeText(stamp.toPropertiesText())
    println("[GoCore] Stamped core version: ${stamp.displayVersion} -> ${generated.path}")

    abis.forEach { abi ->
        val perAbi = File("jniLibs/$abi/core-version.properties")
        perAbi.parentFile.mkdirs()
        perAbi.writeText(stamp.toPropertiesText())
    }
}

// Builds the heavyweight mihomo Go core as a shared library. The release packer XZ-compresses this
// payload; a tiny raw PIE shell loads it in the child process.
class GoCoreBuilder(private val config: ProjectConfig, private val ndkTools: NdkTools) {
    private val sourceDir = File("lib/native/go")
    private val outputDir = File("build/native/go-core")
    private val appJniRoot = File("jniLibs")

    // The mihomo source at lib/mihomo/mihomo is kept PRISTINE. The Android-root Tun changes
    // (tun uid rules and per-app owner rules) live as git
    // patches under .github/patches/mihomo and are applied here right before the Go build — so an
    // upstream mihomo sync never has to be hand-merged.
    private val mihomoDir = File("lib/mihomo/mihomo")
    private val kernelPatchDir = File(".github/patches/mihomo")

    private val abiToGoArch =
        mapOf(
            "arm64-v8a" to "arm64",
        )

    private val buildTags = config.getCsv("golang.buildTags", "cmfa")
    private val buildFlags = config.getCsv("golang.buildFlags", "-trimpath")
    private val packageName = config.getString("golang.packageName", "cfa/native")
    private val outputLibraryName = "libmihomocore.so"
    private var coreVersionStamp: CoreVersionStamp? = null

    fun buildAll() {
        if (!sourceDir.exists()) {
            error("[GoCore] Source directory not found: ${sourceDir.absolutePath}")
        }
        applyKernelPatches()
        val stamp = resolveCoreVersionStamp(config, mihomoDir)
        coreVersionStamp = stamp
        val abis = configuredAbis(config)
        writeCoreVersionStamp(stamp, abis)
        println(
            "[GoCore] Building shared core ($outputLibraryName) for ABIs: ${abis.joinToString()}"
        )
        abis.forEach(::buildForAbi)
    }

    // Apply every .github/patches/mihomo/*.patch to the mihomo tree before building. Idempotent: a
    // patch that is already applied (reverse-check succeeds) is skipped, so re-runs are safe.
    private fun applyKernelPatches() {
        if (!kernelPatchDir.isDirectory || !File(mihomoDir, ".git").exists()) return
        val patches =
            kernelPatchDir
                .listFiles { file -> file.extension == "patch" }
                ?.sortedBy { it.name }
                .orEmpty()
        if (patches.isEmpty()) return
        println("[GoCore] Applying ${patches.size} mihomo kernel patch(es)")
        patches.forEach { patch ->
            val alreadyApplied =
                executeCommand(
                        command =
                            listOf("git", "apply", "--reverse", "--check", patch.absolutePath),
                        workingDir = mihomoDir,
                        printStdout = false,
                        printStderr = false,
                        stderrIsError = false,
                    )
                    .success
            if (alreadyApplied) {
                println("[GoCore]   already applied: ${patch.name}")
                return@forEach
            }
            val result =
                executeCommand(
                    command = listOf("git", "apply", patch.absolutePath),
                    workingDir = mihomoDir,
                    stdoutPrefix = "[patch]",
                    stderrPrefix = "[patch]",
                    stderrIsError = false,
                )
            check(result.success) { "Failed to apply kernel patch ${patch.name}: ${result.error}" }
            println("[GoCore]   applied: ${patch.name}")
        }
    }

    private fun buildForAbi(abi: String) {
        val arch =
            abiToGoArch[abi]
                ?: error("[GoCore] Unsupported ABI: $abi")
        println("[building] Building for $abi (Go shared core, arch: $arch)...")
        val outputLibDir = File(outputDir, abi)
        outputLibDir.mkdirs()
        val outputFile = File(outputLibDir, outputLibraryName)

        val flags = mergeCoreLdflags(buildFlags, coreVersionStamp)
        val command = buildList {
            add("go")
            add("build")
            add("-buildmode")
            add("c-shared")
            addAll(flags)
            if (buildTags.isNotEmpty()) {
                add("-tags")
                add(buildTags.joinToString(","))
            }
            add("-o")
            add(outputFile.absolutePath)
            add(packageName)
        }

        val result =
            executeCommand(
                command = command,
                workingDir = sourceDir,
                environment = buildGoEnv(abi),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (result.success && outputFile.isFile) {
            val destDir = File(appJniRoot, abi)
            destDir.mkdirs()
            outputFile.copyTo(File(destDir, outputLibraryName), overwrite = true)
            println("[GoCore] Copied to ${File(destDir, outputLibraryName).absolutePath}")
        } else {
            val reason = result.error.ifBlank { result.output }.trim()
            error("[GoCore] Failed to build $abi: $reason")
        }
    }

    private fun buildGoEnv(abi: String): Map<String, String> {
        val arch = abiToGoArch.getValue(abi)
        // c-shared requires cgo and routes the final link through the ABI-specific NDK clang.
        return mapOf(
            "CGO_ENABLED" to "1",
            "GOOS" to "android",
            "GOARCH" to arch,
            "CC" to ndkTools.getClangPath(abi),
            "GOCACHE" to File("build/go-cache").absolutePath,
        )
    }

    // Fold the core identity into -ldflags so /version works even when the launcher omits
    // --git-version (CoreProcess currently only passes --home/--controller/…).
    private fun mergeCoreLdflags(
        baseFlags: List<String>,
        stamp: CoreVersionStamp?,
    ): List<String> {
        // ProcessBuilder passes each list element as one argv; no shell quoting.
        // kernel.properties stores: -trimpath,-ldflags,-s -w  so the token after -ldflags
        // is often "-s -w" (starts with '-', but is still the ldflags VALUE, not a go flag).
        val additions = mutableListOf(ANDROID_PACKED_RELOCATIONS_LDFLAG)
        if (stamp != null && stamp.commit != "unknown") {
            val versionValue = stamp.displayVersion.lowercase(Locale.US)
            additions += "-X github.com/metacubex/mihomo/constant.Version=$versionValue"
            additions += "-X github.com/metacubex/mihomo/constant.BuildTime=${stamp.buildTime}"
        }
        val inject = additions.joinToString(" ")
        val flags = baseFlags.toMutableList()
        val ldflagsIndex = flags.indexOf("-ldflags")
        if (ldflagsIndex >= 0) {
            if (ldflagsIndex + 1 < flags.size) {
                flags[ldflagsIndex + 1] = flags[ldflagsIndex + 1].trim() + " " + inject
            } else {
                flags.add(ldflagsIndex + 1, "-s -w $inject")
            }
        } else {
            flags.add("-ldflags")
            flags.add("-s -w $inject")
        }
        return flags
    }

    private companion object {
        const val ANDROID_PACKED_RELOCATIONS_LDFLAG =
            "-extldflags=-Wl,--pack-dyn-relocs=android"
    }
}

class RustBuilder(private val config: ProjectConfig) {
    private val sourceDir = File("lib/native/rust")
    private val outputDir = File("build/native/rust")
    private val appJniRoot = File("jniLibs")
    private val outputLibraryName = "liboverride.so"

    fun buildAll() {
        if (!File(sourceDir, "Cargo.toml").isFile) {
            error(
                "[Rust] Source directory not ready: missing ${File(sourceDir, "Cargo.toml").absolutePath}"
            )
        }

        val abis = configuredAbis(config)
        println("[Rust] Building Android shared library from ${sourceDir.absolutePath}")
        println("[Rust] Host CLI/ELF is not built by this script")
        println("[Rust] Building for ABIs: ${abis.joinToString()}")

        abis.forEach { abi -> buildForAbi(abi) }
    }

    private fun buildForAbi(abi: String) {
        println("[building] Building for $abi (Rust)...")

        val command =
            listOf(
                "cargo",
                "ndk",
                "-t",
                abi,
                "-o",
                outputDir.absolutePath,
                "build",
                "--release",
                "--lib",
                // Rebuild std from source so the runtime-performance profile (opt-level=3, LTO)
                // applies to it too. Requires the nightly
                // toolchain pinned below plus the rust-src component.
                "-Z",
                "build-std=std,panic_abort",
            )

        val result =
            executeCommand(
                command = command,
                workingDir = sourceDir,
                environment =
                    mapOf(
                        // -Z flags and -Cpanic=immediate-abort are nightly-only; pin via
                        // rustup so the build does not depend on the host default toolchain.
                        "RUSTUP_TOOLCHAIN" to "nightly",
                        // Panic handling is irrelevant to normal compilation throughput. Keep
                        // immediate
                        // abort for the native failure boundary, but avoid size-only linker passes
                        // that
                        // can trade code layout for a smaller output.
                        "RUSTFLAGS" to
                            listOf(
                                    "-Zunstable-options",
                                    "-Cpanic=immediate-abort",
                                    // liboverride is loaded via System.loadLibrary("override");
                                    // give it a stable
                                    // soname so it resolves whether packed (code_cache) or raw
                                    // (nativeLibraryDir).
                                    "-C",
                                    "link-arg=-Wl,-soname,liboverride.so",
                                )
                                .joinToString(" "),
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (result.success) {
            val sourceLib = File(outputDir, "$abi/$outputLibraryName")
            if (sourceLib.exists()) {
                copyToAppJni(abi, sourceLib)
                println("[building] Successfully built $abi (Rust)")
            } else {
                error("[building] Output library not found: ${sourceLib.absolutePath}")
            }
        } else {
            val reason = result.error.ifBlank { result.output }.trim()
            error("[building] Failed to build $abi (Rust): $reason")
        }
    }

    private fun copyToAppJni(abi: String, sourceLib: File) {
        val destDir = File(appJniRoot, abi)
        destDir.mkdirs()
        val destLib = File(destDir, "liboverride.so")
        sourceLib.copyTo(destLib, overwrite = true)
        println("[Rust] Copied to ${destLib.absolutePath}")
    }
}

class CoreShellBuilder(private val config: ProjectConfig, private val ndkTools: NdkTools) {
    private val sourceDir = File("lib/native/shell")
    private val outputDir = File("build/native/core-shell")
    private val appJniRoot = File("jniLibs")
    private val outputFileName = "libmihomo.so"

    fun buildAll() {
        require(File(sourceDir, "CMakeLists.txt").isFile) {
            "[CoreShell] Source directory not ready: missing ${File(sourceDir, "CMakeLists.txt").absolutePath}"
        }

        val abis = configuredAbis(config)
        println("[CoreShell] Building PIE launcher for ABIs: ${abis.joinToString()}")
        abis.forEach(::buildForAbi)
    }

    private fun buildForAbi(abi: String) {
        println("[building] Building for $abi (mihomo PIE shell)...")
        val objDir = File(outputDir, "obj/$abi")
        val binDir = File(outputDir, abi)
        objDir.mkdirs()
        binDir.mkdirs()
        val toolchain = File(ndkTools.ndkDir, "build/cmake/android.toolchain.cmake")
        val configure =
            executeCommand(
                command =
                    listOf(
                        ndkTools.getCmakePath(),
                        "-S",
                        sourceDir.absolutePath,
                        "-B",
                        objDir.absolutePath,
                        "-G",
                        "Ninja",
                        "-DCMAKE_MAKE_PROGRAM=${ndkTools.getNinjaPath()}",
                        "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                        "-DANDROID_ABI=$abi",
                        "-DANDROID_PLATFORM=android-${ndkTools.getMinAndroidApi()}",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${binDir.absolutePath}",
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (!configure.success) {
            val reason = configure.error.ifBlank { configure.output }.trim()
            error("[CoreShell] Failed to configure $abi: $reason")
        }
        val build =
            executeCommand(
                command =
                    listOf(
                        ndkTools.getCmakePath(),
                        "--build",
                        objDir.absolutePath,
                        "--target",
                        "mihomo-shell",
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (!build.success) {
            val reason = build.error.ifBlank { build.output }.trim()
            error("[CoreShell] Failed to build $abi: $reason")
        }

        val source = File(binDir, outputFileName)
        require(source.isFile) { "[CoreShell] Output not found: ${source.absolutePath}" }
        val destination = File(appJniRoot, "$abi/$outputFileName")
        destination.parentFile.mkdirs()
        source.copyTo(destination, overwrite = true)
        println("[CoreShell] Copied to ${destination.absolutePath}")
    }
}

class LoaderCBuilder(private val config: ProjectConfig, private val ndkTools: NdkTools) {
    private val sourceDir = File("pack/native")
    private val outputDir = File("build/native/loader")
    private val appJniRoot = File("jniLibs")
    private val outputLibraryName = "libloader.so"

    fun buildAll() {
        require(File(sourceDir, "CMakeLists.txt").isFile) {
            "[Loader] Source directory not ready: missing ${File(sourceDir, "CMakeLists.txt").absolutePath}"
        }

        val abis = configuredAbis(config)
        println("[Loader] Building native payload extractor for ABIs: ${abis.joinToString()}")
        abis.forEach(::buildForAbi)
    }

    private fun buildForAbi(abi: String) {
        println("[building] Building for $abi (Loader C/liblzma)...")
        val objDir = File(outputDir, "obj/$abi")
        val libDir = File(outputDir, abi)
        objDir.mkdirs()
        libDir.mkdirs()
        val toolchain = File(ndkTools.ndkDir, "build/cmake/android.toolchain.cmake")
        val configure =
            executeCommand(
                command =
                    listOf(
                        ndkTools.getCmakePath(),
                        "-S",
                        sourceDir.absolutePath,
                        "-B",
                        objDir.absolutePath,
                        "-G",
                        "Ninja",
                        "-DCMAKE_MAKE_PROGRAM=${ndkTools.getNinjaPath()}",
                        "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                        "-DANDROID_ABI=$abi",
                        "-DANDROID_PLATFORM=android-${ndkTools.getMinAndroidApi()}",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${libDir.absolutePath}",
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (!configure.success) {
            val reason = configure.error.ifBlank { configure.output }.trim()
            error("[building] Failed to configure $abi (Loader C): $reason")
        }
        val build =
            executeCommand(
                command =
                    listOf(
                        ndkTools.getCmakePath(),
                        "--build",
                        objDir.absolutePath,
                        "--target",
                        "loader",
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (!build.success) {
            val reason = build.error.ifBlank { build.output }.trim()
            error("[building] Failed to build $abi (Loader C): $reason")
        }

        val sourceLib = File(outputDir, "$abi/$outputLibraryName")
        require(sourceLib.isFile) {
            "[building] Output library not found: ${sourceLib.absolutePath}"
        }
        val destination = File(appJniRoot, "$abi/$outputLibraryName")
        destination.parentFile.mkdirs()
        sourceLib.copyTo(destination, overwrite = true)
        println("[Loader] Copied to ${destination.absolutePath}")
    }
}

class CompatBuilder(private val config: ProjectConfig, private val ndkTools: NdkTools) {
    private val sourceDir = File("lib/native/compat")
    private val outputDir = File("build/native/compat")
    private val appJniRoot = File("jniLibs")
    private val outputLibraryName = "libcompat.so"

    fun buildAll() {
        require(File(sourceDir, "CMakeLists.txt").isFile) {
            "[Compat] Source directory not ready: missing ${File(sourceDir, "CMakeLists.txt").absolutePath}"
        }

        val abis = configuredAbis(config)
        println(
            "[Compat] Building out-of-process core bridge (libcompat.so) for ABIs: ${abis.joinToString()}"
        )
        abis.forEach(::buildForAbi)
    }

    private fun buildForAbi(abi: String) {
        println("[building] Building for $abi (Compat C)...")
        val objDir = File(outputDir, "obj/$abi")
        val libDir = File(outputDir, abi)
        objDir.mkdirs()
        libDir.mkdirs()
        val toolchain = File(ndkTools.ndkDir, "build/cmake/android.toolchain.cmake")
        val configure =
            executeCommand(
                command =
                    listOf(
                        ndkTools.getCmakePath(),
                        "-S",
                        sourceDir.absolutePath,
                        "-B",
                        objDir.absolutePath,
                        "-G",
                        "Ninja",
                        "-DCMAKE_MAKE_PROGRAM=${ndkTools.getNinjaPath()}",
                        "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                        "-DANDROID_ABI=$abi",
                        "-DANDROID_PLATFORM=android-${ndkTools.getMinAndroidApi()}",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${libDir.absolutePath}",
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (!configure.success) {
            val reason = configure.error.ifBlank { configure.output }.trim()
            error("[building] Failed to configure $abi (Compat C): $reason")
        }
        val build =
            executeCommand(
                command =
                    listOf(
                        ndkTools.getCmakePath(),
                        "--build",
                        objDir.absolutePath,
                        "--target",
                        "compat",
                    ),
                stdoutPrefix = "[building][$abi]",
                stderrPrefix = "[building][$abi]",
                stderrIsError = false,
            )
        if (!build.success) {
            val reason = build.error.ifBlank { build.output }.trim()
            error("[building] Failed to build $abi (Compat C): $reason")
        }

        val sourceLib = File(outputDir, "$abi/$outputLibraryName")
        require(sourceLib.isFile) {
            "[building] Output library not found: ${sourceLib.absolutePath}"
        }
        val destination = File(appJniRoot, "$abi/$outputLibraryName")
        destination.parentFile.mkdirs()
        sourceLib.copyTo(destination, overwrite = true)
        println("[Compat] Copied to ${destination.absolutePath}")
    }
}

class ResourceDownloader(private val config: ProjectConfig) {
    private val outputDir = File("build/generated/assets/geo")

    fun downloadGeoFiles() {
        outputDir.mkdirs()

        val assets =
            listOf(
                AssetInfo(
                    "geoip.metadb",
                    config.getString(
                        "asset.geoip.url",
                        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb",
                    ),
                    compress = true,
                ),
                AssetInfo(
                    "geosite.dat",
                    config.getString(
                        "asset.geosite.url",
                        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat",
                    ),
                    compress = true,
                ),
                AssetInfo(
                    "ASN.mmdb",
                    config.getString(
                        "asset.asn.url",
                        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb",
                    ),
                    compress = true,
                ),
                AssetInfo(
                    "BundleMRS.7z",
                    config.getString(
                        "asset.bundleMRS.url",
                        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z",
                    ),
                    compress = false,
                ),
            )

        assets.forEach { asset ->
            if (asset.url.isNotEmpty() && asset.url.startsWith("https://")) {
                downloadFile(asset.name, asset.url, compress = asset.compress)
            }
        }
    }

    private data class AssetInfo(val name: String, val url: String, val compress: Boolean)

    private fun downloadFile(name: String, url: String, compress: Boolean = false) {
        try {
            println("[Geo] Downloading $name from $url...")

            val tempFile = File.createTempFile("geo-", "-${name}")
            tempFile.deleteOnExit()

            val connection = URL(url).openConnection()
            connection.connect()
            connection.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (compress) {
                val outputFile = File(outputDir, "${name}.xz")
                compressToXz(tempFile, outputFile)
                println("[Geo] Downloaded and compressed $name -> ${outputFile.absolutePath}")
            } else {
                val outputFile = File(outputDir, name)
                tempFile.copyTo(outputFile, overwrite = true)
                println("[Geo] Downloaded $name to ${outputFile.absolutePath}")
            }
        } catch (e: IOException) {
            println("[Geo] Failed to download $name: ${e.message}")
        }
    }

    private fun compressToXz(sourceFile: File, outputFile: File) {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        sourceFile.inputStream().buffered().use { input ->
            outputFile.outputStream().buffered().use { fileOutput ->
                XZOutputStream(fileOutput, LZMA2Options()).use { xzOutput ->
                    input.copyTo(xzOutput)
                }
            }
        }
    }
}

fun printUsage() {
    println(
        """
        YumeBox Native Build Tool

        Usage: kotlin scripts/native-build.main.kts [options]

        Options:
          --go       Build the mihomo shared core and PIE shell
          --coreexe  Compatibility alias of --go
          --shell    Build only the mihomo PIE shell (libmihomo.so)
          --rust     Build Rust config compiler
          --loader   Build the C/liblzma native payload extractor
          --compat   Build the out-of-process core bridge (libcompat.so)
          --geo      Download Geo databases and BundleMRS.7z into generated assets
          --clean    Clean build outputs
          --all      Build everything (default)
          --help     Show this help
        """
            .trimIndent()
    )
}

fun cleanBuildOutputs() {
    println("[Clean] Removing build outputs...")
    File("build/native").deleteRecursively()
    File("build/generated").deleteRecursively()

    listOf("geoip.metadb.xz", "geosite.dat.xz", "ASN.mmdb.xz").forEach { name ->
        File("app/assets/$name").delete()
    }

    val abis = listOf(SUPPORTED_ANDROID_ABI)
    abis.forEach { abi ->
        File("jniLibs/$abi/libmihomo.so").delete()
        File("jniLibs/$abi/libmihomocore.so").delete()
        File("jniLibs/$abi/liboverride.so").delete()
        File("jniLibs/$abi/libloader.so").delete()
        File("jniLibs/$abi/libcompat.so").delete()
        File("jniLibs/$abi/core-version.properties").delete()
    }
    File("build/generated/core-version.properties").delete()
    println("[Clean] Done")
}

val message =
    """
    __   __                             ____                 
    \ \ / /  _   _   _ __ ___     ___  | __ )    ___   __  __
     \ V /  | | | | | '_ ` _ \   / _ \ |  _ \   / _ \  \ \/ /
      | |   | |_| | | | | | | | |  __/ | |_) | | (_) |  >  < 
      |_|    \__,_| |_| |_| |_|  \___| |____/   \___/  /_/\_\
                                                             
    """
        .trimIndent()

fun main(args: Array<String>) {
    if (args.contains("--help")) {
        printUsage()
        return
    }

    println(message)
    println("=== YumeBox Native Build Tool ===")
    println("OS: ${SystemDetector.os}, Host: ${SystemDetector.hostTag}")

    if (args.contains("--clean")) {
        cleanBuildOutputs()
        return
    }

    val config = ProjectConfig()

    val buildGo =
        args.isEmpty() ||
            args.contains("--all") ||
            args.contains("--go") ||
            args.contains("--coreexe")
    val buildShell = buildGo || args.contains("--shell")
    val buildRust = args.isEmpty() || args.contains("--all") || args.contains("--rust")
    val buildLoader = args.isEmpty() || args.contains("--all") || args.contains("--loader")
    val buildCompat = args.isEmpty() || args.contains("--all") || args.contains("--compat")
    val downloadGeo = args.isEmpty() || args.contains("--all") || args.contains("--geo")

    // The c-shared core and every C component link through the ABI-specific NDK toolchain.
    val needsNdk = buildGo || buildShell || buildLoader || buildCompat
    val ndkTools by lazy { NdkTools(config) }

    if (needsNdk) {
        println("NDK: ${ndkTools.ndkDir.absolutePath}")
    }
    println("Go: ${if (SystemDetector.checkCommandExists("go")) "OK" else "NOT FOUND"}")
    println("Rust: ${if (SystemDetector.checkCommandExists("cargo")) "OK" else "NOT FOUND"}")
    println("XZ library: org.tukaani:xz:1.12")

    if (buildGo) {
        GoCoreBuilder(config, ndkTools).buildAll()
    }

    if (buildShell) {
        CoreShellBuilder(config, ndkTools).buildAll()
    }

    if (buildRust) {
        RustBuilder(config).buildAll()
    }

    if (buildLoader) {
        LoaderCBuilder(config, ndkTools).buildAll()
    }

    if (buildCompat) {
        CompatBuilder(config, ndkTools).buildAll()
    }

    if (downloadGeo) {
        ResourceDownloader(config).downloadGeoFiles()
    }

    println("=== Build Complete ===")
}

main(args)
