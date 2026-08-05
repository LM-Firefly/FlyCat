/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * YumeBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumeyucca.yumebox.substore.engine

import android.annotation.SuppressLint
import android.content.Context
import org.tukaani.xz.XZInputStream
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

@SuppressLint("StaticFieldLeak")
object NativeLibraryManager {
    const val JAVET_LIBRARY_NAME = "libjavet-node-android"
    const val JAVET_LIBRARY_FILE_NAME = "libjavet.so"
    const val JAVET_ARCHIVE_FILE_NAME = "libjavet.so.xz"
    const val JAVET_ARCHIVE_SHA256 =
        "31d7606fe3dd9135930a6a9519d90394abd219e535796cee3d5c8747812c6d35"
    const val JAVET_LIBRARY_SHA256 =
        "126d1569d60c2f20605188001f236d91ba4c9cf7d35a8f02b57d69e47fe4b39d"

    private const val LIBRARY_DIR_NAME = "lib"

    private var context: Context? = null

    fun initialize(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        libraryDir?.mkdirs()
    }

    fun getLibraryFile(name: String): File? {
        if (context == null) return null
        return when (name) {
            JAVET_LIBRARY_NAME -> File(requireNotNull(libraryDir), JAVET_LIBRARY_FILE_NAME)
            else -> null
        }
    }

    fun getDownloadTempFile(name: String): File? =
        getLibraryFile(name)?.let { library ->
            File(library.parentFile, "$JAVET_ARCHIVE_FILE_NAME.download")
        }

    fun installDownloadedArchive(name: String, downloadedArchive: File): Boolean {
        val targetFile = getLibraryFile(name) ?: return false
        if (!isArchiveFileValid(name, downloadedArchive)) {
            Timber.e("Native library archive hash mismatch: $name")
            return false
        }

        val expandedFile = File(targetFile.parentFile, "${targetFile.name}.expanded")
        val installed =
            runCatching {
                if (expandedFile.exists() && !expandedFile.delete()) {
                    error("Unable to clear expanded native library: ${expandedFile.absolutePath}")
                }
                XZInputStream(downloadedArchive.inputStream().buffered()).use { input ->
                    expandedFile.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                check(isLibraryFileValid(name, expandedFile)) {
                    "Expanded native library hash mismatch: $name"
                }
                replaceLibrary(targetFile, expandedFile)
            }.getOrElse { error ->
                Timber.e(error, "Native library installation failed: $name")
                false
            }
        downloadedArchive.delete()
        if (!installed) expandedFile.delete()
        return installed
    }

    private fun replaceLibrary(targetFile: File, replacementFile: File): Boolean =
        runCatching {
            targetFile.parentFile?.mkdirs()
            val backupFile = File(targetFile.parentFile, "${targetFile.name}.previous")
            if (backupFile.exists() && !backupFile.delete()) {
                error("Unable to clear native library backup: ${backupFile.absolutePath}")
            }
            val hasPreviousLibrary = targetFile.exists()
            if (hasPreviousLibrary && !targetFile.renameTo(backupFile)) {
                error("Unable to back up native library: ${targetFile.absolutePath}")
            }
            if (!replacementFile.renameTo(targetFile)) {
                if (hasPreviousLibrary) backupFile.renameTo(targetFile)
                error("Unable to install native library: ${targetFile.absolutePath}")
            }
            backupFile.delete()
            targetFile.setReadable(true, false)
            true
        }.getOrElse { error ->
            Timber.e(error, "Native library replacement failed: ${targetFile.name}")
            false
        }

    fun isLibraryAvailable(name: String): Boolean =
        getLibraryFile(name)?.let { isLibraryFileValid(name, it) } == true

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadJniLibrary(name: String): Boolean {
        val path = getLibraryFile(name)?.takeIf(File::isFile)?.absolutePath ?: return false

        return runCatching {
            System.load(path)
            true
        }.getOrElse { error ->
            Timber.e(error, "JNI load failed: $name")
            false
        }
    }

    fun getLibraryStatus(name: String): String {
        val libraryFile = getLibraryFile(name) ?: return "Library manager not initialized"
        return when {
            !libraryFile.exists() -> "Library file not found: ${libraryFile.absolutePath}"
            !libraryFile.canRead() -> "Library file is not readable: ${libraryFile.absolutePath}"
            !isLibraryFileValid(name, libraryFile) -> "Library hash mismatch: ${libraryFile.absolutePath}"
            else -> "Library ready: $name at ${libraryFile.absolutePath}"
        }
    }

    private val libraryDir: File?
        get() = context?.filesDir?.resolve(LIBRARY_DIR_NAME)

    private fun isLibraryFileValid(name: String, file: File): Boolean =
        file.isFile && file.canRead() && sha256(file) == expectedSha256(name)

    private fun isArchiveFileValid(name: String, file: File): Boolean =
        file.isFile && file.canRead() && sha256(file) == expectedArchiveSha256(name)

    private fun expectedSha256(name: String): String? =
        when (name) {
            JAVET_LIBRARY_NAME -> JAVET_LIBRARY_SHA256
            else -> null
        }

    private fun expectedArchiveSha256(name: String): String? =
        when (name) {
            JAVET_LIBRARY_NAME -> JAVET_ARCHIVE_SHA256
            else -> null
        }

    private fun sha256(file: File): String? =
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.getOrNull()
}
