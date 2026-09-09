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

package com.github.lmfirefly.flycat.feature.substore.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import org.tukaani.xz.XZInputStream
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@SuppressLint("StaticFieldLeak")
object NativeLibraryManager {
    private const val LIBS_DIR_NAME = "libs"
    private var libsBaseDir: File? = null
    private var appContext: Context? = null
    private var isInitialized = false

    enum class LibraryType {
        JNI_LOAD,
        PROCESS_EXEC,
    }

    enum class LibrarySource {
        MAIN_APK,
        EXTENSION_APK,
    }

    data class LibraryInfo(
        val name: String,
        val type: LibraryType,
        val source: LibrarySource,
        val packageName: String? = null,
        val version: String? = null,
    )

    private val managedLibraries = mutableMapOf<String, LibraryInfo>()
    private val loadedJniLibraries = mutableSetOf<String>()

    fun initialize(context: Context) {
        if (isInitialized) return

        this.appContext = context.applicationContext
        libsBaseDir = File(appContext!!.filesDir, LIBS_DIR_NAME)
        val prefs = appContext!!.getSharedPreferences("native_lib_config", Context.MODE_PRIVATE)
        try {
            val packageInfo = appContext!!.packageManager.getPackageInfo(appContext!!.packageName, 0)
            val lastUpdateTime = packageInfo.lastUpdateTime
            val savedTime = prefs.getLong("last_update_time", 0)
            if (lastUpdateTime != savedTime) {
                Timber.i("App updated (time=$lastUpdateTime), clearing native libs cache")
                libsBaseDir?.deleteRecursively()
                prefs.edit()
                    .putLong("last_update_time", lastUpdateTime)
                    .putBoolean("libs_extracted", false)
                    .apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check app update time")
        }
        libsBaseDir?.mkdirs()
        registerDefaultLibraries()
        isInitialized = true
        if (prefs.getBoolean("libs_extracted", false)) {
            if (isExtensionSourceUpToDate(prefs)) {
                Timber.d("Libraries already extracted, skipping")
                return
            }
            Timber.i("Extension source changed, re-extracting libraries")
            libsBaseDir?.deleteRecursively()
            libsBaseDir?.mkdirs()
        }
        val results = extractAllLibraries()
        if (results.isNotEmpty() && results.values.all { it }) {
            val editor = prefs.edit().putBoolean("libs_extracted", true)
            // Snapshot extension APK update times for future comparison
            managedLibraries.values
                .filter { it.source == LibrarySource.EXTENSION_APK && it.packageName != null }
                .mapNotNull { it.packageName }
                .distinct()
                .forEach { pkgName ->
                    runCatching {
                        val extTime = appContext!!.packageManager.getPackageInfo(pkgName, 0).lastUpdateTime
                        editor.putLong("ext_update_time_$pkgName", extTime)
                    }
                }
            editor.apply()
            Timber.d("All libraries extracted successfully, flag saved")
        }
    }

    private fun isExtensionSourceUpToDate(prefs: android.content.SharedPreferences): Boolean {
        val extensionPackages = managedLibraries.values
            .filter { it.source == LibrarySource.EXTENSION_APK && it.packageName != null }
            .mapNotNull { it.packageName }
            .distinct()
        if (extensionPackages.isEmpty()) return true
        for (pkgName in extensionPackages) {
            val savedExtTime = prefs.getLong("ext_update_time_$pkgName", 0)
            val currentExtTime = try {
                appContext!!.packageManager.getPackageInfo(pkgName, 0).lastUpdateTime
            } catch (_: Exception) {
                // Extension not installed — extraction source gone, re-extract would fail anyway.
                // Clear the stale record so a future reinstall is detected as "new".
                if (savedExtTime != 0L) {
                    prefs.edit().remove("ext_update_time_$pkgName").apply()
                }
                continue
            }
            if (currentExtTime != savedExtTime) {
                prefs.edit().putLong("ext_update_time_$pkgName", currentExtTime).apply()
                return false
            }
        }
        return true
    }

    @SuppressLint("StaticFieldLeak")
    private fun registerDefaultLibraries() {
        registerLibrary(
            LibraryInfo(
                name = "libjavet-node-android",
                type = LibraryType.JNI_LOAD,
                source = LibrarySource.EXTENSION_APK,
                packageName = "com.github.lmfirefly.flycat.extension",
            )
        )
    }

    fun registerLibrary(info: LibraryInfo) {
        managedLibraries[info.name] = info
    }

    fun extractAllLibraries(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        managedLibraries.forEach { (name, info) -> results[name] = extractLibrary(info) }
        return results
    }

    fun extractLibrary(info: LibraryInfo): Boolean {
        val targetDir =
            libsBaseDir
                ?: run {
                    Timber.w("Library manager not initialized")
                    return false
                }
        targetDir.mkdirs()
        val targetFile = File(targetDir, info.name)

        if (targetFile.exists() && targetFile.canRead()) {
            if (info.type == LibraryType.PROCESS_EXEC && !targetFile.canExecute()) {
                targetFile.setExecutable(true, false)
            }
            return true
        }

        return runCatching {
                when (info.source) {
                    LibrarySource.MAIN_APK -> extractFromMainApk(info, targetFile)
                    LibrarySource.EXTENSION_APK -> extractFromExtensionApk(info, targetFile)
                }
            }
            .getOrElse { error ->
                Timber.w(error, "Library extract failed: ${info.name}")
                false
            }
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFromMainApk(info: LibraryInfo, targetFile: File): Boolean {
        val apkPath =
            appContext?.applicationInfo?.sourceDir
                ?: throw RuntimeException("Context not initialized")

        ZipFile(apkPath).use { zip ->
            var libEntry = zip.getEntry("lib/${getSupportedAbi()}/${info.name}")
            if (libEntry == null) {
                val supportedAbis = Build.SUPPORTED_ABIS
                for (tryAbi in supportedAbis) {
                    libEntry = zip.getEntry("lib/$tryAbi/${info.name}")
                    if (libEntry != null) break
                }
            }
            if (libEntry == null) {
                val allAbis = Build.SUPPORTED_ABIS.joinToString("|")
                val abiGroup = "(${getSupportedAbi()}|$allAbis)"
                val patternPlain = Regex("lib/$abiGroup/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so")
                val patternXz = Regex("(lib|assets/loader/lib)/$abiGroup/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so\\.xz")
                libEntry = zip.entries().asSequence().firstOrNull { e ->
                    patternPlain.matches(e.name) || patternXz.matches(e.name)
                }
                if (libEntry != null) {
                    Timber.d("Found library via regex match in Main APK: ${libEntry.name}")
                    val actualFileName = libEntry.name.substringAfterLast("/")
                    actualLibraryNames[info.name] = actualFileName
                }
            }

            if (libEntry == null) {
                throw RuntimeException("Library not found in APK: ${info.name}")
            }
            val actualFileName = libEntry.name.substringAfterLast("/")
            val isXzCompressed = actualFileName.endsWith(".xz")
            val decompressedName = if (isXzCompressed) actualFileName.removeSuffix(".xz") else actualFileName
            val targetFileName = if (decompressedName.startsWith("libjavet-node-android")) {
                "libjavet-node-android.so"
            } else {
                decompressedName
            }
            val actualTargetFile = File(targetFile.parentFile, targetFileName)
            val canonicalParent = targetFile.parentFile!!.canonicalPath
            if (!actualTargetFile.canonicalPath.startsWith("$canonicalParent/")) {
                throw SecurityException("Path traversal detected in zip entry: ${libEntry.name}")
            }
            if (targetFileName != info.name) {
                actualLibraryNames[info.name] = targetFileName
            }

            zip.getInputStream(libEntry).use { input ->
                if (isXzCompressed) {
                    org.tukaani.xz.XZInputStream(input).use { xzInput ->
                        FileOutputStream(actualTargetFile).use { output -> xzInput.copyTo(output) }
                    }
                } else {
                    FileOutputStream(actualTargetFile).use { output -> input.copyTo(output) }
                }
            }

            actualTargetFile.setWritable(false, false)
            actualTargetFile.setReadable(true, false)
            if (info.type == LibraryType.PROCESS_EXEC) {
                actualTargetFile.setExecutable(true, false)
            }

            return true
        }
    }

    private fun findMainApkLibEntry(zip: ZipFile, libraryName: String): ZipEntry? {
        zip.getEntry("lib/${getSupportedAbi()}/$libraryName")?.let { return it }
        for (tryAbi in Build.SUPPORTED_ABIS) {
            zip.getEntry("lib/$tryAbi/$libraryName")?.let { return it }
        }
        return null
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFromExtensionApk(info: LibraryInfo, targetFile: File): Boolean {
        if (info.packageName == null) {
            throw IllegalArgumentException("Package name required for extension APK source")
        }

        val extensionApk = getExtensionApk(info.packageName)
        if (extensionApk == null) {
            Timber.w("Extension APK missing: ${info.packageName}, trying Main APK for ${info.name}")
            return extractFromMainApk(info, targetFile)
        }

        val abi = getSupportedAbi()
        val allAbis = Build.SUPPORTED_ABIS.joinToString("|")
        val abiGroup = "($abi|$allAbis)"

        ZipFile(extensionApk).use { zip ->
            // Search order: assets/loader/lib/{abi}/*.so.xz, lib/{abi}/*.so, lib/{abi}/*.so.xz
            val patternXzAssets = Regex("assets/loader/lib/$abiGroup/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so\\.xz")
            val patternPlain = Regex("lib/$abiGroup/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so")
            val patternXzLib = Regex("lib/$abiGroup/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so\\.xz")
            val entry = zip.entries().asSequence().firstOrNull { e -> patternXzAssets.matches(e.name) || patternPlain.matches(e.name) || patternXzLib.matches(e.name) }

            if (entry == null) {
                val candidates = zip.entries().asSequence().map { it.name }
                    .filter { it.contains(info.name) }
                    .joinToString(", ")
                Timber.w("Library ${info.name} not found in extension APK, candidates: $candidates")
                return false
            }

            val actualFileName = entry.name.substringAfterLast("/")
            val isXzCompressed = actualFileName.endsWith(".xz")
            val decompressedName = if (isXzCompressed) actualFileName.removeSuffix(".xz") else actualFileName
            val canonicalFileName = if (decompressedName.startsWith("libjavet-node-android")) {
                "libjavet-node-android.so"
            } else {
                decompressedName
            }
            val actualTargetFile = File(targetFile.parentFile, canonicalFileName)
            val canonicalParent = targetFile.parentFile!!.canonicalPath
            if (!actualTargetFile.canonicalPath.startsWith("$canonicalParent/")) {
                throw SecurityException("Path traversal detected in zip entry: ${entry.name}")
            }
            actualLibraryNames[info.name] = canonicalFileName

            zip.getInputStream(entry).use { input ->
                if (isXzCompressed) {
                    org.tukaani.xz.XZInputStream(input).use { xzInput ->
                        FileOutputStream(actualTargetFile).use { output -> xzInput.copyTo(output) }
                    }
                } else {
                    FileOutputStream(actualTargetFile).use { output -> input.copyTo(output) }
                }
            }

            actualTargetFile.setWritable(false, false)
            actualTargetFile.setReadable(true, false)
            if (info.type == LibraryType.PROCESS_EXEC) {
                actualTargetFile.setExecutable(true, false)
            }
            Timber.d("Successfully extracted ${entry.name} as $canonicalFileName to ${actualTargetFile.absolutePath}")
            return true
        }
    }

    fun getActualLibraryName(baseName: String): String? {
        return actualLibraryNames[baseName]
    }

    private val actualLibraryNames = mutableMapOf<String, String>()

    private fun getExtensionApk(packageName: String): File? =
        runCatching {
                val pm = appContext?.packageManager ?: return null
                val info = pm.getApplicationInfo(packageName, 0)
                File(info.sourceDir)
            }
            .getOrNull()

    fun getLibraryPath(name: String): String? {
        if (!isInitialized) return null

        val actualName = actualLibraryNames[name] ?: name
        val libraryFile = File(libsBaseDir, actualName)
        return if (libraryFile.exists()) libraryFile.absolutePath else null
    }

    fun isLibraryAvailable(name: String): Boolean {
        val path = getLibraryPath(name) ?: return false
        val file = File(path)
        val info = managedLibraries[name]
        return when (info?.type) {
            LibraryType.JNI_LOAD -> file.exists() && file.canRead()
            LibraryType.PROCESS_EXEC -> file.exists() && file.canRead() && file.canExecute()
            null -> false
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    @Synchronized
    fun loadJniLibrary(name: String): Boolean {
        val info = managedLibraries[name]
        if (info?.type != LibraryType.JNI_LOAD) {
            return false
        }
        if (loadedJniLibraries.contains(name)) return true

        val path = getLibraryPath(name) ?: return false

        return runCatching {
                System.load(path)
                loadedJniLibraries.add(name)
                true
            }
            .getOrElse { error ->
                Timber.e(error, "JNI load failed: $name")
                false
            }
    }

    fun getLibraryStatus(name: String): String {
        if (!isInitialized) return "Library manager not initialized"
        val info = managedLibraries[name] ?: return "Library not registered: $name"
        val path = getLibraryPath(name)

        return when {
            path == null -> "Library not extracted: $name"
            !File(path).exists() -> "Library file not found: $path"
            info.type == LibraryType.PROCESS_EXEC && !File(path).canExecute() ->
                "Library exists but not executable: $path"

            info.type == LibraryType.JNI_LOAD && !File(path).canRead() ->
                "Library exists but not readable: $path"

            else -> "Library ready: $name (${info.type}) at $path"
        }
    }

    private fun getSupportedAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    // --- Download-based javet installation (for non-Extension builds) ---

    const val JAVET_LIBRARY_NAME = "libjavet-node-android"
    const val JAVET_LIBRARY_FILE_NAME = "libjavet.so"
    const val JAVET_ARCHIVE_FILE_NAME = "libjavet.so.xz"

    fun getDownloadTempFile(name: String): File? {
        if (!isInitialized) return null
        val dir = libsBaseDir ?: return null
        return File(dir, "$JAVET_ARCHIVE_FILE_NAME.download")
    }

    fun installDownloadedArchive(name: String, downloadedArchive: File): Boolean {
        if (!isInitialized) return false
        val dir = libsBaseDir ?: return false
        val targetFile = File(dir, JAVET_LIBRARY_FILE_NAME)
        val expandedFile = File(dir, "${JAVET_LIBRARY_FILE_NAME}.expanded")
        val installed = runCatching {
            if (expandedFile.exists() && !expandedFile.delete()) {
                error("Unable to clear expanded native library: ${expandedFile.absolutePath}")
            }
            XZInputStream(downloadedArchive.inputStream().buffered()).use { input ->
                expandedFile.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            replaceLibrary(targetFile, expandedFile)
        }.getOrElse { error ->
            Timber.e(error, "Native library installation failed: $name")
            false
        }
        downloadedArchive.delete()
        if (!installed) expandedFile.delete()
        // Register path so loadJniLibrary can find it by name
        if (installed) actualLibraryNames[name] = JAVET_LIBRARY_FILE_NAME
        return installed
    }

    private fun replaceLibrary(targetFile: File, replacementFile: File): Boolean =
        runCatching {
            targetFile.parentFile?.mkdirs()
            val backupFile = File(targetFile.parentFile, "${targetFile.name}.previous")
            if (backupFile.exists() && !backupFile.delete()) {
                error("Unable to clear native library backup: ${backupFile.absolutePath}")
            }
            val hasPrevious = targetFile.exists()
            if (hasPrevious && !targetFile.renameTo(backupFile)) {
                error("Unable to back up native library: ${targetFile.absolutePath}")
            }
            if (!replacementFile.renameTo(targetFile)) {
                if (hasPrevious) backupFile.renameTo(targetFile)
                error("Unable to install native library: ${targetFile.absolutePath}")
            }
            backupFile.delete()
            targetFile.setWritable(false, false)
            targetFile.setReadable(true, false)
            true
        }.getOrElse { error ->
            Timber.e(error, "Native library replacement failed: ${targetFile.name}")
            false
        }
}
