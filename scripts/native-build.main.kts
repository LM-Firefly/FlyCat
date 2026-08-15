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
 * Copyright (c)  YumeLira 2025 - Present
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
        return getString(key, default)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
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
            val process = if (os == "windows") {
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
    val error: String = ""
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
    stderrPrefix: String? = if (stderrIsError) "[err]" else "[cmd]"
): CommandResult {
    return try {
        val processBuilder = ProcessBuilder(command)
        workingDir?.let { processBuilder.directory(it) }
        processBuilder.environment().putAll(environment)
        val process = processBuilder.start()
        val output = StringBuilder()
        val error = StringBuilder()
        val stdoutThread = thread(start = true, name = "stdout-reader") {
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
        val stderrThread = thread(start = true, name = "stderr-reader") {
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
            error = error.toString()
        )
    } catch (e: Exception) {
        CommandResult(success = false, error = e.message ?: "Unknown error")
    }
}

class NdkTools(private val config: ProjectConfig) {
    private val sdkDir: File by lazy {
        val path = config.getString("sdk.dir", "")
            .takeIf { it.isNotEmpty() }
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
        val ndkPath = explicitNdk.takeIf { it.isNotEmpty() }
            ?: File(sdkDir, "ndk/$ndkVersion").absolutePath
        File(ndkPath).also {
            require(it.isDirectory) { "NDK not found: ${it.absolutePath}" }
        }
    }
    fun getClangPath(abi: String): String {
        val triple = when (abi) {
            "arm64-v8a" -> "aarch64-linux-android"
            "armeabi-v7a" -> "armv7a-linux-androideabi"
            "x86" -> "i686-linux-android"
            "x86_64" -> "x86_64-linux-android"
            else -> throw IllegalArgumentException("Unsupported ABI: $abi")
        }
        val ext = if (SystemDetector.os == "windows") ".cmd" else ""
        return File(ndkDir, "toolchains/llvm/prebuilt/${SystemDetector.hostTag}/bin/${triple}${getMinAndroidApi()}-clang${ext}").absolutePath
    }
    fun getStripPath(): String {
        val ext = if (SystemDetector.os == "windows") ".exe" else ""
        return File(ndkDir, "toolchains/llvm/prebuilt/${SystemDetector.hostTag}/bin/llvm-strip${ext}").absolutePath
    }
    fun getMinAndroidApi(): Int = maxOf(config.getInt("android.minSdk", 24), 24)
    fun getCmakePath(): String {
        val ext = if (SystemDetector.os == "windows") ".exe" else ""
        val cmakeRoot = File(sdkDir, "cmake")
        require(cmakeRoot.isDirectory) { "CMake not found under Android SDK: ${cmakeRoot.absolutePath}" }
        val preferred = listOf("3.22.1")
            .map { File(cmakeRoot, "$it/bin/cmake$ext") }
            .firstOrNull { it.isFile }
        if (preferred != null) {
            return preferred.absolutePath
        }
        return cmakeRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "bin/cmake$ext") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath
            ?: error("CMake executable not found under ${cmakeRoot.absolutePath}")
    }
    fun getNinjaPath(): String {
        val ext = if (SystemDetector.os == "windows") ".exe" else ""
        val cmakeRoot = File(sdkDir, "cmake")
        require(cmakeRoot.isDirectory) { "CMake not found under Android SDK: ${cmakeRoot.absolutePath}" }
        val preferred = listOf("3.22.1")
            .map { File(cmakeRoot, "$it/bin/ninja$ext") }
            .firstOrNull { it.isFile }
        if (preferred != null) {
            return preferred.absolutePath
        }
        return cmakeRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "bin/ninja$ext") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath
            ?: error("Ninja executable not found under ${cmakeRoot.absolutePath}")
    }
}

// Mihomo core identity stamped next to native libs for CI APK jobs (which have no mihomo git tree).
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

fun runGit(repoDir: File, args: List<String>): String? {
    if (!repoDir.isDirectory || !File(repoDir, ".git").exists()) return null
    val result = executeCommand(
        command = listOf("git", "-C", repoDir.absolutePath) + args,
        printStdout = false, printStderr = false, stderrIsError = false
    )
    if (!result.success) return null
    return result.output.trim().lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

fun resolveCoreVersionStamp(config: ProjectConfig, mihomoDir: File = File("lib/mihomo/mihomo")): CoreVersionStamp {
    val configuredBranch = config.getString("external.mihomo.branch", "Alpha").ifEmpty { "Alpha" }
    val suffix = config.getString("external.mihomo.suffix", "")
    val includeTimestamp = config.getBoolean("external.mihomo.includeTimestamp", false)
    val commit = runGit(mihomoDir, listOf("rev-parse", "--short=8", "HEAD"))
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{4,40}")) } ?: "unknown"
    val gitBranch = runGit(mihomoDir, listOf("rev-parse", "--abbrev-ref", "HEAD"))
        ?.takeIf { it.isNotEmpty() && it != "HEAD" }
    val branchBase = configuredBranch.ifBlank { gitBranch ?: "mihomo" }
    val branchLabel = branchBase + suffix
    val buildTime = if (includeTimestamp) SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date()) else "local"
    val displayVersion = "$branchLabel-$commit"
    val gitVersionArg = "${branchLabel.replace('_', '-')}_${commit}_$buildTime"
    return CoreVersionStamp(branchLabel, commit, displayVersion, gitVersionArg, buildTime)
}

fun writeCoreVersionStamp(stamp: CoreVersionStamp, abis: List<String>) {
    val generated = File("build/generated/core-version.properties")
    generated.parentFile.mkdirs()
    generated.writeText(stamp.toPropertiesText())
    println("[Go] Stamped core version: ${stamp.displayVersion} -> ${generated.path}")
    abis.forEach { abi ->
        val perAbi = File("jniLibs/$abi/core-version.properties")
        perAbi.parentFile.mkdirs()
        perAbi.writeText(stamp.toPropertiesText())
    }
}

class GoBuilder(private val config: ProjectConfig, private val ndkTools: NdkTools) {
    private val sourceDir = File("lib/native/go")
    private val outputDir = File("build/native/go")
    private val appJniRoot = File("jniLibs")
    private val goModuleDir = File("lib/native/go")
    private val mihomoDir = File("lib/mihomo/mihomo")
    private val kernelPatchDir = File(".github/patches/mihomo")
    private var coreVersionStamp: CoreVersionStamp? = null
    private val abiToGoArch = mapOf(
        "arm64-v8a" to "arm64",
        "armeabi-v7a" to "arm",
        "x86" to "386",
        "x86_64" to "amd64"
    )
    private val buildTags = config.getCsv("golang.buildTags", "cmfa")
    private val buildFlags = config.getCsv("golang.buildFlags", "-trimpath")
    private val packageName = config.getString("golang.packageName", "cfa/native")
    fun buildAll() {
        if (!sourceDir.exists()) {
            println("[Go] Source directory not found: ${sourceDir.absolutePath}")
            return
        }
        applyKernelPatches()
        val stamp = resolveCoreVersionStamp(config, mihomoDir)
        coreVersionStamp = stamp
        val abis = config.getCsv("abi.app.list", "armeabi-v7a,arm64-v8a,x86,x86_64")
        writeCoreVersionStamp(stamp, abis)
        println("[Go] Building for ABIs: ${abis.joinToString()}")
        abis.forEach { abi -> buildForAbi(abi) }
    }
    // Apply patches idempotently before Go build. Already-applied patches are skipped.
    // Mirrors sync-kernel.sh logic: uses external.mihomo.suffix to select variant subdirectory.
    private fun applyKernelPatches() {
        if (!kernelPatchDir.isDirectory || !File(mihomoDir, ".git").exists()) return
        val suffix = config.getString("external.mihomo.suffix", "").trim()
        val variant = when {
            suffix.contains("smart", ignoreCase = true) -> "smart"
            suffix.contains("meta", ignoreCase = true) -> "meta"
            else -> ""
        }
        val basePatches = kernelPatchDir.listFiles { file -> file.extension == "patch" }
            ?.sortedBy { it.name }
            .orEmpty()
        if (basePatches.isEmpty()) return
        // If a variant subdirectory has a same-named patch, it overrides the base patch.
        val variantDir = if (variant.isNotEmpty()) File(kernelPatchDir, variant) else null
        println("[Go] Applying ${basePatches.size} mihomo kernel patch(es)${if (variant.isNotEmpty()) " (variant=$variant)" else ""}")
        basePatches.forEach { patch ->
            val effectivePatch = if (variantDir != null && variantDir.isDirectory) {
                val override = File(variantDir, patch.name)
                if (override.isFile) override else patch
            } else patch
            val alreadyApplied = executeCommand(
                command = listOf("git", "apply", "--reverse", "--check", effectivePatch.absolutePath),
                workingDir = mihomoDir, printStdout = false, printStderr = false, stderrIsError = false
            ).success
            if (alreadyApplied) {
                println("[Go]   already applied: ${effectivePatch.name}")
                return@forEach
            }
            val result = executeCommand(
                command = listOf("git", "apply", effectivePatch.absolutePath),
                workingDir = mihomoDir, stdoutPrefix = "[patch]", stderrPrefix = "[patch]", stderrIsError = false
            )
            check(result.success) { "Failed to apply kernel patch ${effectivePatch.name}: ${result.error}" }
            println("[Go]   applied: ${effectivePatch.name}")
        }
    }
    private fun buildForAbi(abi: String) {
        val arch = abiToGoArch[abi] ?: run {
            println("[Go] Unsupported ABI: $abi")
            return
        }
        println("[building] Building for $abi (arch: $arch)...")
        val outputLibDir = File(outputDir, abi)
        outputLibDir.mkdirs()
        val outputFile = File(outputLibDir, "libmihomo.so")
        val env = buildGoEnv(abi)
        val command = buildList {
            add("go")
            add("build")
            add("-buildmode")
            add("c-shared")
            addAll(mergeCoreLdflags(buildFlags, coreVersionStamp))
            if (buildTags.isNotEmpty()) {
                add("-tags")
                add(buildTags.joinToString(","))
            }
            add("-o")
            add(outputFile.absolutePath)
            add(packageName.ifBlank { "." })
        }
        val result = executeCommand(
            command = command,
            workingDir = sourceDir,
            environment = env,
            stdoutPrefix = "[building][$abi]",
            stderrPrefix = "[building][$abi]",
            stderrIsError = false
        )
        if (result.success) {
            copyToAppJni(abi, outputFile)
            println("[building] Successfully built $abi")
        } else {
            val reason = result.error.ifBlank { result.output }.trim()
            println("[building] Failed to build $abi: $reason")
        }
    }
    // Merge soname, version identity, and packed-relocations into ldflags.
    private fun mergeCoreLdflags(baseFlags: List<String>, stamp: CoreVersionStamp?): List<String> {
        val additions = mutableListOf("-extldflags=-Wl,--pack-dyn-relocs=android,-soname,libmihomo.so")
        if (stamp != null && stamp.commit != "unknown") {
            val versionValue = stamp.displayVersion.lowercase(Locale.US)
            additions += "-X github.com/metacubex/mihomo/constant.Version=$versionValue"
            additions += "-X github.com/metacubex/mihomo/constant.BuildTime=${stamp.buildTime}"
        }
        val inject = additions.joinToString(" ")
        val flags = baseFlags.toMutableList()
        val idx = flags.indexOf("-ldflags")
        if (idx >= 0 && idx + 1 < flags.size) {
            flags[idx + 1] = (flags[idx + 1].trim() + " " + inject).trim()
        } else {
            flags.add("-ldflags")
            flags.add("-s -w $inject")
        }
        return flags
    }
    private fun buildGoEnv(abi: String): Map<String, String> {
        val arch = abiToGoArch.getValue(abi)
        return mapOf(
            "CGO_ENABLED" to "1",
            "GOOS" to "android",
            "GOARCH" to arch,
            "CC" to ndkTools.getClangPath(abi),
            "CFLAGS" to "-O3 -Werror"
        ) + if (abi == "armeabi-v7a") mapOf("GOARM" to "7") else emptyMap()
    }
    private fun copyToAppJni(abi: String, sourceLib: File) {
        val destDir = File(appJniRoot, abi)
        destDir.mkdirs()
        val destLib = File(destDir, "libmihomo.so")
        sourceLib.copyTo(destLib, overwrite = true)
        val generatedHeader = File(sourceLib.parentFile, "libmihomo.h")
        if (!generatedHeader.exists()) {
            val fallbackHeader = File(goModuleDir, "libmihomo.h")
            if (fallbackHeader.exists()) {
                fallbackHeader.copyTo(generatedHeader, overwrite = true)
            }
        }
        println("[Go] Copied to ${destLib.absolutePath}")
    }
}

class RustBuilder(private val config: ProjectConfig) {
    private val sourceDir = File("lib/native/rust")
    private val outputDir = File("build/native/rust")
    private val appJniRoot = File("jniLibs")
    private val outputLibraryName = "liboverride.so"
    fun buildAll() {
        if (!File(sourceDir, "Cargo.toml").isFile) {
            error("[Rust] Source directory not ready: missing ${File(sourceDir, "Cargo.toml").absolutePath}")
        }
        val abis = config.getCsv("abi.app.list", "armeabi-v7a,arm64-v8a,x86,x86_64")
        println("[Rust] Building Android shared library from ${sourceDir.absolutePath}")
        println("[Rust] Host CLI/ELF is not built by this script")
        println("[Rust] Building for ABIs: ${abis.joinToString()}")
        abis.forEach { abi -> buildForAbi(abi) }
    }
    private fun buildForAbi(abi: String) {
        println("[building] Building for $abi (Rust)...")
        val command = listOf(
            "cargo", "ndk",
            "-t", abi,
            "-o", outputDir.absolutePath,
            "build", "--release", "--lib",
            // Rebuild std from source so the size profile (opt-level=z, LTO,
            // immediate-abort) applies to it too. Requires the nightly
            // toolchain pinned below plus the rust-src component.
            "-Z", "build-std=std,panic_abort",
        )
        val result = executeCommand(
            command = command,
            workingDir = sourceDir,
            environment = mapOf(
                // -Z flags and -Cpanic=immediate-abort are nightly-only; pin via
                // rustup so the build does not depend on the host default toolchain.
                "RUSTUP_TOOLCHAIN" to "nightly",
                // immediate-abort drops all panic message/formatting machinery
                // (~18% smaller liboverride.so). Panics already aborted the
                // process (profile panic=abort); they just lose the logcat
                // message. Compile errors are reported via Result/JSON and are
                // unaffected. gc-sections + lld ICF fold what LTO leaves behind.
                "RUSTFLAGS" to listOf(
                    "-Zunstable-options",
                    "-Cpanic=immediate-abort",
                    "-C", "link-arg=-Wl,--gc-sections",
                    "-C", "link-arg=-Wl,--icf=all",
                    "-C", "link-arg=-Wl,-soname,liboverride.so",
                ).joinToString(" "),
            ),
            stdoutPrefix = "[building][$abi]",
            stderrPrefix = "[building][$abi]",
            stderrIsError = false
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

class LoaderRustBuilder(private val config: ProjectConfig) {
    private val sourceDir = File("pack/native")
    private val outputDir = File("build/native/loader")
    private val appJniRoot = File("jniLibs")
    private val outputLibraryName = "libloader.so"
    fun buildAll() {
        require(File(sourceDir, "Cargo.toml").isFile) {
            "[Loader] Source directory not ready: missing ${File(sourceDir, "Cargo.toml").absolutePath}"
        }
        val abis = config.getCsv("abi.app.list", "armeabi-v7a,arm64-v8a,x86,x86_64")
        println("[Loader] Building native payload extractor (Rust/xz2) for ABIs: ${abis.joinToString()}")
        abis.forEach(::buildForAbi)
    }
    private fun buildForAbi(abi: String) {
        println("[building] Building for $abi (Loader Rust/xz2)...")
        val command = listOf(
            "cargo", "ndk",
            "-t", abi,
            "-o", outputDir.absolutePath,
            "build", "--release", "--lib",
            "-Z", "build-std=std,panic_abort",
        )
        val result = executeCommand(
            command = command,
            workingDir = sourceDir,
            environment = mapOf(
                "RUSTUP_TOOLCHAIN" to "nightly",
                "RUSTFLAGS" to listOf(
                    "-Zunstable-options",
                    "-Cpanic=immediate-abort",
                    "-C", "link-arg=-Wl,--gc-sections",
                    "-C", "link-arg=-Wl,--icf=all",
                    "-C", "link-arg=-Wl,-soname,libloader.so",
                ).joinToString(" "),
            ),
            stdoutPrefix = "[building][$abi]",
            stderrPrefix = "[building][$abi]",
            stderrIsError = false,
        )
        if (!result.success) {
            val reason = result.error.ifBlank { result.output }.trim()
            error("[building] Failed to build $abi (Loader Rust): $reason")
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

class EbpfBridgeBuilder(private val config: ProjectConfig) {
    private val sourceDir = File("lib/native/ebpf-bridge")
    private val outputDir = File("build/native/ebpf-bridge")
    private val appJniRoot = File("jniLibs")
    private val outputBinaryName = "libebpfbridge.so"
    fun buildAll() {
        if (!File(sourceDir, "Cargo.toml").isFile) {
            println("[eBPF] Source directory not found, skipping: ${sourceDir.absolutePath}")
            return
        }
        val abis = config.getCsv("abi.app.list", "armeabi-v7a,arm64-v8a,x86,x86_64")
        println("[eBPF] Building Rust eBPF bridge from ${sourceDir.absolutePath}")
        println("[eBPF] Building for ABIs: ${abis.joinToString()}")
        abis.forEach { abi -> buildForAbi(abi) }
    }
    private val abiToTargetTriple = mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "armv7-linux-androideabi",
        "x86" to "i686-linux-android",
        "x86_64" to "x86_64-linux-android",
    )
    private fun buildForAbi(abi: String) {
        println("[eBPF] Building for $abi...")
        val command = listOf(
            "cargo", "ndk",
            "-t", abi,
            "build", "--release", "--bin", "libebpfbridge",
            "-Z", "build-std=std,panic_abort",
        )
        val result = executeCommand(
            command = command,
            workingDir = sourceDir,
            environment = mapOf(
                "RUSTUP_TOOLCHAIN" to "nightly",
                "RUSTFLAGS" to listOf(
                    "-C", "link-arg=-Wl,--gc-sections",
                    "-C", "link-arg=-Wl,--build-id=none",
                    "-C", "link-arg=-Wl,-z,relro",
                    "-C", "link-arg=-Wl,-z,now",
                    "-C", "link-arg=-Wl,-soname,$outputBinaryName",
                ).joinToString(" "),
            ),
            stdoutPrefix = "[eBPF][$abi]",
            stderrPrefix = "[eBPF][$abi]",
            stderrIsError = false
        )
        if (result.success) {
            // cargo ndk doesn't copy binaries via -o; find in target directory
            val triple = abiToTargetTriple[abi] ?: error("[eBPF] Unknown ABI: $abi")
            val targetBin = File(sourceDir, "target/$triple/release/libebpfbridge")
            val sourceBin = File(outputDir, "$abi/$outputBinaryName")
            if (targetBin.exists()) {
                sourceBin.parentFile.mkdirs()
                targetBin.copyTo(sourceBin, overwrite = true)
                copyToAppJni(abi, sourceBin)
                println("[eBPF] Successfully built $abi")
            } else {
                error("[eBPF] Output binary not found: ${targetBin.absolutePath}")
            }
        } else {
            val reason = result.error.ifBlank { result.output }.trim()
            error("[eBPF] Failed to build $abi: $reason")
        }
    }
    private fun copyToAppJni(abi: String, sourceBin: File) {
        val destDir = File(appJniRoot, abi)
        destDir.mkdirs()
        val destBin = File(destDir, outputBinaryName)
        sourceBin.copyTo(destBin, overwrite = true)
        println("[eBPF] Copied to ${destBin.absolutePath}")
    }
}

class ResourceDownloader(private val config: ProjectConfig) {
    private val outputDir = File("build/generated/assets/geo")
    fun downloadGeoFiles() {
        outputDir.mkdirs()
        val assets = listOf(
            AssetInfo("geoip.metadb", config.getString("asset.geoip.url", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb"), compress = true),
            AssetInfo("geosite.dat", config.getString("asset.geosite.url", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"), compress = true),
            AssetInfo("ASN.mmdb", config.getString("asset.asn.url", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb"), compress = true),
            AssetInfo("BundleMRS.7z", config.getString("asset.bundleMRS.url", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z"), compress = false)
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
    println("""
        FlyCat Native Build Tool
        Usage: kotlin scripts/native-build.main.kts [options]
        Options:
          --go       Build Go native libraries
          --rust     Build Rust config compiler
          --loader   Build the Rust/xz2 native payload extractor
          --ebpf     Build Rust eBPF bridge
          --geo      Download Geo databases and BundleMRS.7z into generated assets
          --clean    Clean build outputs
          --all      Build everything (default)
          --help     Show this help
    """.trimIndent())
}

fun cleanBuildOutputs() {
    println("[Clean] Removing build outputs...")
    File("build/native").deleteRecursively()
    File("build/generated").deleteRecursively()
    listOf("geoip.metadb.xz", "geosite.dat.xz", "ASN.mmdb.xz").forEach { name ->
        File("app/assets/$name").delete()
    }
    val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    abis.forEach { abi ->
        File("jniLibs/$abi/libmihomo.so").delete()
        File("jniLibs/$abi/liboverride.so").delete()
        File("jniLibs/$abi/libloader.so").delete()
        File("jniLibs/$abi/libebpfbridge.so").delete()
        File("jniLibs/$abi/core-version.properties").delete()
    }
    File("build/generated/core-version.properties").delete()
    println("[Clean] Done")
}

val message = """
  _____  _           ____       _
 |  ___)| | _   _   / ___| __ _| |_
 | |_   | || | | | | |    / _ `| __|
 |  _|  | || |_| | | |___| (_| | |_
 | |    | |\_\_/_|  \____|\__,_|\__|
 |_|    \_\\   | |
           |\__| |
           \_____/

""".trimIndent()


fun main(args: Array<String>) {
    if (args.contains("--help")) {
        printUsage()
        return
    }
    println(message)
    println("=== FlyCat Native Build Tool ===")
    println("OS: ${SystemDetector.os}, Host: ${SystemDetector.hostTag}")
    if (args.contains("--clean")) {
        cleanBuildOutputs()
        return
    }
    val config = ProjectConfig()
    val buildGo = args.isEmpty() || args.contains("--all") || args.contains("--go")
    val buildRust = args.isEmpty() || args.contains("--all") || args.contains("--rust")
    val buildEbpf = args.isEmpty() || args.contains("--all") || args.contains("--ebpf")
    val buildLoader = args.isEmpty() || args.contains("--all") || args.contains("--loader")
    val downloadGeo = args.isEmpty() || args.contains("--all") || args.contains("--geo")
    val needsNdk = buildGo || buildLoader
    val ndkTools by lazy { NdkTools(config) }
    if (needsNdk) {
        println("NDK: ${ndkTools.ndkDir.absolutePath}")
    }
    println("Go: ${if (SystemDetector.checkCommandExists("go")) "OK" else "NOT FOUND"}")
    println("Rust: ${if (SystemDetector.checkCommandExists("cargo")) "OK" else "NOT FOUND"}")
    println("XZ library: org.tukaani:xz:1.12")
    if (buildGo) {
        GoBuilder(config, ndkTools).buildAll()
    }
    if (buildRust) {
        RustBuilder(config).buildAll()
    }
    if (buildEbpf) {
        EbpfBridgeBuilder(config).buildAll()
    }
    if (buildLoader) {
        LoaderRustBuilder(config).buildAll()
    }
    if (downloadGeo) {
        ResourceDownloader(config).downloadGeoFiles()
    }
    println("=== Build Complete ===")
}

main(args)
