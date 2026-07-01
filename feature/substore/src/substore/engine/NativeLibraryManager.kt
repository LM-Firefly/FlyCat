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

package com.github.yumelira.yumebox.feature.substore.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

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
        try {
            val packageInfo = appContext!!.packageManager.getPackageInfo(appContext!!.packageName, 0)
            val lastUpdateTime = packageInfo.lastUpdateTime
            val prefs = appContext!!.getSharedPreferences("native_lib_config", Context.MODE_PRIVATE)
            val savedTime = prefs.getLong("last_update_time", 0)
            if (lastUpdateTime != savedTime) {
                Timber.i("App updated (time=$lastUpdateTime), clearing native libs cache")
                libsBaseDir?.deleteRecursively()
                prefs.edit().putLong("last_update_time", lastUpdateTime).apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check app update time")
        }
        libsBaseDir?.mkdirs()
        registerDefaultLibraries()
        isInitialized = true
        extractAllLibraries()
    }

    @SuppressLint("StaticFieldLeak")
    private fun registerDefaultLibraries() {
        registerLibrary(
            LibraryInfo(
                name = "libjavet-node-android",
                type = LibraryType.JNI_LOAD,
                source = LibrarySource.EXTENSION_APK,
                packageName = "com.github.yumelira.yumebox.extension",
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
            appContext?.applicationInfo?.sourceDir ?: throw RuntimeException("Context not initialized")

        val abi = getSupportedAbi()
        ZipFile(apkPath).use { zip ->
            var libEntry = zip.getEntry("lib/$abi/${info.name}")
            if (libEntry == null) {
                val supportedAbis = Build.SUPPORTED_ABIS
                for (tryAbi in supportedAbis) {
                    libEntry = zip.getEntry("lib/$tryAbi/${info.name}")
                    if (libEntry != null) break
                }
            }
            if (libEntry == null) {
                val pattern = Regex("lib/($abi|${Build.SUPPORTED_ABIS.joinToString("|")})/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so")
                libEntry = zip.entries().asSequence().firstOrNull { e ->
                    pattern.matches(e.name)
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
            val targetFileName = if (actualFileName.startsWith("libjavet-node-android")) {
                "libjavet-node-android.so"
            } else {
                actualFileName
            }
            val actualTargetFile = File(targetFile.parentFile, targetFileName)
            if (targetFileName != info.name) {
                actualLibraryNames[info.name] = targetFileName
            }

            zip.getInputStream(libEntry).use { input ->
                FileOutputStream(actualTargetFile).use { output -> input.copyTo(output) }
            }

            actualTargetFile.setReadable(true, false)
            if (info.type == LibraryType.PROCESS_EXEC) {
                actualTargetFile.setExecutable(true, false)
            }

            return true
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFromExtensionApk(info: LibraryInfo, targetFile: File): Boolean {
        if (info.packageName == null) {
            throw RuntimeException("Package name required for extension APK source")
        }

        val extensionApk = getExtensionApk(info.packageName)
        if (extensionApk == null) {
            Timber.w("Extension APK missing: ${info.packageName}, trying Main APK for ${info.name}")
            return extractFromMainApk(info, targetFile)
        }

        val abi = getSupportedAbi()

        ZipFile(extensionApk).use { zip ->
            val libEntries = zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/") }
                .joinToString(", ")
            val pattern =
                Regex(
                    "lib/($abi|${Build.SUPPORTED_ABIS.joinToString("|")})/${info.name}\\.v\\.\\d+\\.\\d+\\.\\d+\\.so"
                )
            val entry =
                zip.entries().asSequence().firstOrNull { error -> pattern.matches(error.name) }

            if (entry == null) {
                Timber.w("Library ${info.name} not found in extension APK, available: $libEntries")
                return false
            }

            val actualFileName = entry.name.substringAfterLast("/")
            val actualTargetFile = File(targetFile.parentFile, actualFileName)

            actualLibraryNames[info.name] = actualFileName

            zip.getInputStream(entry).use { input ->
                FileOutputStream(actualTargetFile).use { output -> input.copyTo(output) }
            }

            actualTargetFile.setReadable(true, false)
            if (info.type == LibraryType.PROCESS_EXEC) {
                actualTargetFile.setExecutable(true, false)
            }
            Timber.d("Successfully extracted $actualFileName to ${actualTargetFile.absolutePath}")

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
        val supportedABIs = Build.SUPPORTED_ABIS
        return when {
            supportedABIs.contains("arm64-v8a") -> "arm64-v8a"
            supportedABIs.contains("x86_64") -> "x86_64"
            supportedABIs.contains("armeabi-v7a") -> "armeabi-v7a"
            supportedABIs.contains("x86") -> "x86"
            else -> supportedABIs.firstOrNull() ?: "arm64-v8a"
        }
    }
}
