package dev.yume.packer

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

@DisableCachingByDefault(because = "The output is signed with secret material")
abstract class PackApkTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputApkDirectory: DirectoryProperty

    @get:OutputDirectory abstract val outputApkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val loaderDex: RegularFileProperty

    @get:Internal abstract val sdkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keyStoreFile: RegularFileProperty

    @get:Input abstract val originalApplication: Property<String>

    @get:Input abstract val originalComponentFactory: Property<String>

    @get:Input abstract val keyAlias: Property<String>

    @get:Internal abstract val keyStorePassword: Property<String>

    @get:Internal abstract val keyPassword: Property<String>

    protected fun packApk(input: File, output: File) {
        val work = temporaryDir.resolve(input.nameWithoutExtension)
        work.deleteRecursively()
        work.mkdirs()
        val unaligned = work.resolve("unaligned.apk")
        val aligned = work.resolve("aligned.apk")

        val dexPayload =
            ZipFile(input).use { zip ->
                zip.entries()
                    .asSequence()
                    .filter { !it.isDirectory && DEX_NAME.matches(it.name) }
                    .sortedBy(ZipEntry::getName)
                    .map { entry ->
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        PayloadEntry(
                            assetName = "${PayloadFormat.ROOT}/dex/${entry.name}.xz",
                            outputName = entry.name,
                            originalSize = bytes.size.toLong(),
                            sha256 = sha256(bytes),
                            compressed = compress(bytes),
                        )
                    }
                    .toList()
            }
        check(dexPayload.isNotEmpty()) { "No classes*.dex found in $input" }
        val nativePayload =
            ZipFile(input).use { zip ->
                zip.entries()
                    .asSequence()
                    .filter { !it.isDirectory && NATIVE_LIBRARY.matches(it.name) }
                    .filterNot { BOOTSTRAP_LIBRARY.matches(it.name) }
                    .sortedBy(ZipEntry::getName)
                    .map { entry -> nativePayload(zip, entry) }
                    .toList()
            }
        check(nativePayload.distinctBy { it.abi to it.outputName }.size == nativePayload.size) {
            "Duplicate native library payload entries found in $input"
        }
        val payloadId = payloadId(dexPayload, nativePayload)
        val metadata = metadata(payloadId, dexPayload, nativePayload)

        ZipFile(input).use { source ->
            ZipOutputStream(unaligned.outputStream().buffered()).use { target ->
                source
                    .entries()
                    .asSequence()
                    .filterNot { shouldDrop(it.name) }
                    .forEach { entry ->
                        if (entry.isDirectory) return@forEach
                        val bytes = source.getInputStream(entry).use { it.readBytes() }
                        target.put(bytes, entry.name, entry.method == ZipEntry.STORED)
                    }
                target.put(loaderDex.get().asFile.readBytes(), "classes.dex", stored = false)
                dexPayload.forEach { target.put(it.compressed, it.assetName, stored = true) }
                nativePayload.forEach { target.put(it.compressed, it.assetName, stored = true) }
                target.put(metadata, PayloadFormat.METADATA, stored = true)
            }
        }

        val tools = latestBuildTools()
        runCommand(
            listOf(
                tools.resolve(executable("zipalign")).absolutePath,
                "-P",
                "16",
                "-f",
                "4",
                unaligned.absolutePath,
                aligned.absolutePath,
            ),
            "zipalign failed for ${input.name}",
        )
        sign(tools, aligned)
        aligned.copyTo(output, overwrite = true)
    }

    private fun metadata(
        payloadId: String,
        dexEntries: List<PayloadEntry>,
        nativeEntries: List<NativePayloadEntry>,
    ): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PayloadFormat.MAGIC)
                output.writeInt(PayloadFormat.VERSION)
                output.writeUTF(payloadId)
                output.writeUTF(originalApplication.get())
                output.writeUTF(originalComponentFactory.get())
                output.writeInt(dexEntries.size)
                dexEntries.forEach { entry ->
                    output.writeUTF(entry.assetName)
                    output.writeUTF(entry.outputName)
                    output.writeLong(entry.originalSize)
                    output.write(entry.sha256)
                }
                output.writeInt(nativeEntries.size)
                nativeEntries.forEach { entry ->
                    output.writeUTF(entry.abi)
                    output.writeUTF(entry.assetName)
                    output.writeUTF(entry.outputName)
                    output.writeLong(entry.originalSize)
                    output.write(entry.sha256)
                }
            }
            bytes.toByteArray()
        }

    private fun payloadId(
        dexEntries: List<PayloadEntry>,
        nativeEntries: List<NativePayloadEntry>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        dexEntries.forEach { entry ->
            digest.update(entry.outputName.toByteArray(Charsets.UTF_8))
            digest.update(entry.sha256)
        }
        nativeEntries.forEach { entry ->
            digest.update(entry.abi.toByteArray(Charsets.UTF_8))
            digest.update(entry.outputName.toByteArray(Charsets.UTF_8))
            digest.update(entry.sha256)
        }
        return digest.digest().toHex()
    }

    private fun nativePayload(zip: ZipFile, entry: ZipEntry): NativePayloadEntry {
        val match =
            NATIVE_LIBRARY.matchEntire(entry.name) ?: error("Invalid native library: ${entry.name}")
        val abi = match.groupValues[1]
        val packagedName = match.groupValues[2]
        val packagedBytes = zip.getInputStream(entry).use { it.readBytes() }
        val alreadyCompressed = packagedName.endsWith(".xz.so")
        val originalBytes =
            if (alreadyCompressed) {
                org.tukaani.xz.XZInputStream(packagedBytes.inputStream()).use { it.readBytes() }
            } else {
                packagedBytes
            }
        val outputName =
            if (alreadyCompressed) {
                packagedName.removeSuffix(".xz.so") + ".so"
            } else {
                packagedName
            }
        return NativePayloadEntry(
            abi = abi,
            assetName = "${PayloadFormat.ROOT}/lib/$abi/$outputName.xz",
            outputName = outputName,
            originalSize = originalBytes.size.toLong(),
            sha256 = sha256(originalBytes),
            compressed = if (alreadyCompressed) packagedBytes else compress(originalBytes),
        )
    }

    private fun compress(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            XZOutputStream(output, LZMA2Options(6)).use { it.write(bytes) }
            output.toByteArray()
        }

    private fun shouldDrop(name: String): Boolean =
        DEX_NAME.matches(name) ||
            (NATIVE_LIBRARY.matches(name) && !BOOTSTRAP_LIBRARY.matches(name)) ||
            name.startsWith("${PayloadFormat.ROOT}/") ||
            SIGNATURE_ENTRY.matches(name)

    private fun sign(buildTools: File, apk: File) {
        val signer = buildTools.resolve("lib/apksigner.jar")
        check(signer.isFile) { "apksigner.jar not found: $signer" }
        val command =
            listOf(
                javaExecutable(),
                "-jar",
                signer.absolutePath,
                "sign",
                "--ks",
                keyStoreFile.get().asFile.absolutePath,
                "--ks-key-alias",
                keyAlias.get(),
                "--ks-pass",
                "env:YUME_PACKER_KS_PASS",
                "--key-pass",
                "env:YUME_PACKER_KEY_PASS",
                apk.absolutePath,
            )
        val environment =
            mapOf(
                "YUME_PACKER_KS_PASS" to keyStorePassword.get(),
                "YUME_PACKER_KEY_PASS" to keyPassword.get(),
            )
        runCommand(command, "apksigner failed for ${apk.name}", environment)
    }

    private fun latestBuildTools(): File {
        val root = sdkDirectory.get().asFile.resolve("build-tools")
        return root.listFiles()?.filter(File::isDirectory)?.maxWithOrNull { left, right ->
            compareVersions(left.name, right.name)
        } ?: error("No Android build-tools installation found under $root")
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun executable(name: String): String =
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "$name.exe"
        else name

    private fun javaExecutable(): String =
        File(System.getProperty("java.home"), "bin/java").absolutePath

    private fun runCommand(
        command: List<String>,
        failure: String,
        environment: Map<String, String> = emptyMap(),
    ) {
        val process =
            ProcessBuilder(command).apply { environment().putAll(environment) }.inheritIO().start()
        check(process.waitFor() == 0) { failure }
    }

    private fun ZipOutputStream.put(bytes: ByteArray, name: String, stored: Boolean) {
        val entry =
            ZipEntry(name).apply {
                time = FIXED_TIMESTAMP
                if (stored) {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                }
            }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class PayloadEntry(
        val assetName: String,
        val outputName: String,
        val originalSize: Long,
        val sha256: ByteArray,
        val compressed: ByteArray,
    )

    private data class NativePayloadEntry(
        val abi: String,
        val assetName: String,
        val outputName: String,
        val originalSize: Long,
        val sha256: ByteArray,
        val compressed: ByteArray,
    )

    private companion object {
        val DEX_NAME = Regex("classes(?:[2-9][0-9]*)?\\.dex")
        val NATIVE_LIBRARY = Regex("lib/([^/]+)/([^/]+\\.so)")

        // Kept raw (not moved into the payload) so they stay under nativeLibraryDir:
        //  - libloader.so is the bootstrap loader itself;
        //  - libclash.so is the mihomo PIE core — it is fork+exec'd out of process, and a non-root
        // app
        //    can only execve() a file living in nativeLibraryDir (SELinux forbids exec from the
        //    extracted code_cache payload), so it must not be packed.
        val BOOTSTRAP_LIBRARY = Regex("lib/[^/]+/(?:libloader|libclash)\\.so")
        val SIGNATURE_ENTRY =
            Regex("META-INF/[^/]+\\.(?:MF|SF|RSA|DSA|EC)", RegexOption.IGNORE_CASE)
        const val FIXED_TIMESTAMP = 315_532_800_000L
    }
}
