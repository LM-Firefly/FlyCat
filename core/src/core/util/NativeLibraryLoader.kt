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

package com.github.lmfirefly.flycat.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import org.tukaani.xz.XZInputStream
import timber.log.Timber

/**
 * 加载两个核心原生库（liboverride.so、libmihomo.so）。
 * 发布版 APK 打包器（dev.yume.packer / PackApkTask）会将每个库压缩为 XZ 格式的 assets/loader/lib/<abi>/<name>.so.xz 载荷。
 * 这些二进制文件必须解压到应用私有存储，并通过绝对路径加载。
 *
 * 为何需要解压：SELinux 会拒绝 APK zip 内部文件的 mmap(PROT_EXEC) 请求（DexKit #24, NDK #979）。
 * 应用自身数据目录（app_data_file）下的文件允许可执行映射，因此 System.load 对解压后的副本可以成功，无需设置可执行权限位。
 *
 * liboverride 和 libmihomo 均带有 DT_SONAME 条目。
 * 在纯 Rust JNI 桥接模式下，无需额外的 libbridge 共享库。
 *
 * 当不存在压缩二进制文件（默认打包方式）时，tryLoadFromBundle 返回 false，通知调用方从 nativeLibraryDir 加载原始库。
 */
object NativeLibraryLoader {
    private const val ROOT_DIR = "nativelibs"
    private const val PACKER_METADATA = "loader/payload.bin"

    // Dependency order: liboverride first, then libmihomo.
    private val CORE_LIBS = listOf("liboverride.so", "libmihomo.so")

    // libmihomo.so -> libmihomo.xz.so ; naming convention for XZ-compressed payloads.
    private fun compressedName(soName: String) = soName.removeSuffix(".so") + ".xz.so"

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadCoreLibraries(context: Context) {
        if (hasPackerPayload(context)) {
            loadFromClassLoader()
            Timber.i("Loaded core native libraries from the payload class loader")
            return
        }
        // 检查KernelManager是否有活动已下载内核
        val downloadedKernel = tryLoadDownloadedKernel(context)
        if (downloadedKernel) {
            return
        }
        if (!tryLoadFromBundle(context)) {
            loadFromClassLoader()
        }
    }

    /**
     * 尝试加载KernelManager下载的内核而非内置内核。
     * 直接读取活动内核标记文件（无跨模块依赖）。
     * 若成功加载已下载的内核则返回true。
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun tryLoadDownloadedKernel(context: Context): Boolean {
        return try {
            val libDir = context.filesDir.resolve("lib")
            val activeFile = java.io.File(libDir, "active-kernel")
            if (!activeFile.isFile) return false
            val activeId = activeFile.readText().trim()
            if (activeId.isEmpty() || activeId == "bundled-alpha") return false
            val kernelFile = java.io.File(libDir, "libmihomo-$activeId.so")
            if (!kernelFile.isFile) return false
            // 确保文件不可写——Android 16+ 会拒绝可写的 .so 文件。
            kernelFile.setReadOnly()
            // 从ClassLoader加载liboverride，从下载的内核加载libmihomo
            System.loadLibrary("override")
            System.load(kernelFile.absolutePath)
            Timber.i("Loaded downloaded kernel: $activeId")
            true
        } catch (e: Throwable) {
            Timber.d(e, "No downloaded kernel available, falling back to bundled")
            false
        }
    }

    /**
     * Extracts and loads the complete compressed bundle. Returns false only when none of the compressed blobs are present, signalling the caller to use raw jniLibs. A partial bundle is invalid because compressed packaging excludes all three raw libraries.
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun tryLoadFromBundle(context: Context): Boolean {
        if (!hasCompressedBundle(context)) return false
        val libDir = ensureExtracted(context)
        CORE_LIBS.forEach { name -> System.load(File(libDir, name).absolutePath) }
        Timber.i("Loaded core native libraries from compressed payload")
        return true
    }

    private fun hasPackerPayload(context: Context): Boolean =
        try {
            context.assets.open(PACKER_METADATA).use { }
            true
        } catch (_: FileNotFoundException) {
            false
        }

    private fun loadFromClassLoader() {
        CORE_LIBS.forEach { name ->
            System.loadLibrary(name.removePrefix("lib").removeSuffix(".so"))
        }
    }

    private fun hasCompressedBundle(context: Context): Boolean {
        val available = CORE_LIBS.associateWith { name ->
            try {
                openCompressed(context, name).close()
                true
            } catch (_: FileNotFoundException) {
                false
            }
        }
        if (available.values.none { present -> present }) return false
        check(available.values.all { present -> present }) {
            "Incomplete compressed native library bundle: " +
                available.filterValues { present -> !present }.keys.joinToString()
        }
        return true
    }

    private fun ensureExtracted(context: Context): File {
        val root = File(context.filesDir, ROOT_DIR)
        // Include the install timestamp so replacing an APK with the same versionCode cannot reuse
        // libraries extracted from an older build. The installed lib/ ABI is already device-only.
        val libDir = File(root, installId(context))

        if (CORE_LIBS.all { name -> File(libDir, name).length() > 0L }) return libDir

        withExtractionLock(root) {
            libDir.mkdirs()
            CORE_LIBS.forEach { name ->
                val target = File(libDir, name)
                if (target.length() <= 0L) {
                    decompress(context, name, target)
                }
            }
            pruneStaleVersions(root, libDir)
        }
        return libDir
    }

    /** Decompress an XZ library blob to [target] atomically (temp file + rename) so a crash can't
     *  leave a truncated, unloadable library behind. */
    private fun decompress(context: Context, soName: String, target: File) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            openCompressed(context, soName).use { raw ->
                XZInputStream(raw.buffered()).use { xz ->
                    FileOutputStream(tmp).use { out ->
                        xz.copyTo(out)
                        out.fd.sync()
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                throw IOException("Failed to move extracted lib into place: $tmp -> $target")
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    /**
     * Opens a compressed blob from the legacy installer directory, the packed assets payload, or
     * the legacy APK lib directory. The returned stream owns and closes the backing ZipFile when
     * read from the APK.
     */
    private fun openCompressed(context: Context, soName: String): InputStream {
        val compressedName = compressedName(soName)
        val extracted = File(context.applicationInfo.nativeLibraryDir, compressedName)
        if (extracted.isFile) return extracted.inputStream()

        val apk = ZipFile(context.applicationInfo.sourceDir)
        try {
            for (abi in Build.SUPPORTED_ABIS) {
                val entry = apk.getEntry("assets/loader/lib/$abi/$soName.xz")
                    ?: apk.getEntry("lib/$abi/$compressedName")
                    ?: continue
                val input = apk.getInputStream(entry)
                return object : FilterInputStream(input) {
                    override fun close() {
                        super.close()
                        apk.close()
                    }
                }
            }
        } catch (t: Throwable) {
            apk.close()
            throw t
        }
        apk.close()
        throw FileNotFoundException("Compressed native lib not found: $soName")
    }

    /** Best-effort removal of libs extracted for previous app versions. */
    private fun pruneStaleVersions(root: File, currentVersionDir: File) {
        root.listFiles()?.forEach { child ->
            if (child.isDirectory && child.name != currentVersionDir.name) {
                runCatching { child.deleteRecursively() }
                    .onFailure { Timber.w(it, "Failed to prune stale native lib dir %s", child) }
            }
        }
    }

    /** Serialise extraction across the app's processes (main UI + runtime service). */
    private inline fun <T> withExtractionLock(root: File, block: () -> T): T {
        root.mkdirs()
        RandomAccessFile(File(root, ".lock"), "rw").use { raf ->
            raf.channel.lock().use { return block() }
        }
    }

    @Suppress("DEPRECATION")
    private fun installId(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return "$versionCode-${info.lastUpdateTime}"
    }
}
