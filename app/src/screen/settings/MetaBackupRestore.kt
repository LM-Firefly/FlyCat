package com.github.yumelira.yumebox.screen.settings

import android.content.Context
import android.net.Uri
import com.tencent.mmkv.MMKV
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

internal object MetaBackupRestore {
    private val EXCLUDED_ROOT_NAMES = setOf("libs")
    private const val RESTORE_TEMP_DIR = "backup_restore_tmp"
    private const val MMKV_ZIP_PREFIX = "mmkv/"
    private val MMKV_DIR_NAME: String by lazy {
        File(MMKV.getRootDir()).name
    }
    fun defaultBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "flycat_backup_$timestamp.zip"
    }
    suspend fun backup(context: Context, targetUri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching { backupInternal(context, targetUri) }
        }
    }
    suspend fun backupToFile(context: Context, targetFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                targetFile.parentFile?.mkdirs()
                targetFile.outputStream().use { outputStream ->
                    backupInternal(context, outputStream)
                }
            }
        }
    }
    suspend fun restore(context: Context, sourceUri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching { restoreInternal(context, sourceUri) }
        }
    }
    suspend fun restoreFromFile(context: Context, sourceFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                FileInputStream(sourceFile).use { inputStream ->
                    restoreInternal(context, inputStream)
                }
            }
        }
    }
    private fun backupInternal(context: Context, targetUri: Uri) {
        val outputStream = requireNotNull(context.contentResolver.openOutputStream(targetUri)) {
            "unable to open backup output"
        }
        outputStream.use {
            backupInternal(context, it)
        }
    }
    private fun backupInternal(context: Context, outputStream: java.io.OutputStream) {
        val rootDir = context.filesDir
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
            rootDir.listFiles()
                ?.sortedBy { it.name }
                ?.forEach { child ->
                    addEntry(rootDir, child, zipOutputStream)
                }
            addMmkvEntries(context, zipOutputStream)
        }
    }

    /**
     * MMKV default root is <app-data>/mmkv/, outside of filesDir.
     * Pack every MMKV data file as mmkv/<filename> so restore can re-populate it.
     */
    private fun addMmkvEntries(context: Context, zipOutputStream: ZipOutputStream) {
        val mmkvRoot = File(MMKV.getRootDir())
        if (!mmkvRoot.isDirectory) return
        mmkvRoot.listFiles()
            ?.sortedBy { it.name }
            ?.forEach { file ->
                if (file.isFile) {
                    zipOutputStream.putNextEntry(ZipEntry("$MMKV_ZIP_PREFIX${file.name}"))
                    file.inputStream().use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                }
            }
    }
    private fun addEntry(rootDir: File, file: File, zipOutputStream: ZipOutputStream) {
        if (file.name in EXCLUDED_ROOT_NAMES || file.name == MMKV_DIR_NAME) {
            return
        }
        val relativePath = rootDir.toPath()
            .relativize(file.toPath())
            .toString()
            .replace(File.separatorChar, '/')
        if (file.isDirectory) {
            zipOutputStream.putNextEntry(ZipEntry("$relativePath/"))
            zipOutputStream.closeEntry()
            file.listFiles()
                ?.sortedBy { it.name }
                ?.forEach { child ->
                    addEntry(rootDir, child, zipOutputStream)
                }
            return
        }
        zipOutputStream.putNextEntry(ZipEntry(relativePath))
        file.inputStream().use { inputStream ->
            inputStream.copyTo(zipOutputStream)
        }
        zipOutputStream.closeEntry()
    }
    private fun restoreInternal(context: Context, sourceUri: Uri) {
        val inputStream = requireNotNull(context.contentResolver.openInputStream(sourceUri)) {
            "unable to open backup input"
        }
        inputStream.use {
            restoreInternal(context, it)
        }
    }
    private fun restoreInternal(context: Context, inputStream: java.io.InputStream) {
        val rootDir = context.filesDir
        val tempDir = File(context.cacheDir, RESTORE_TEMP_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val mmkvTempDir = File(context.cacheDir, "mmkv_restore_tmp").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            unzipToDirectory(inputStream, tempDir, mmkvTempDir)
            val restoredChildren = tempDir.listFiles()?.sortedBy { it.name }.orEmpty()
            val mmkvChildren = mmkvTempDir.listFiles().orEmpty()
            require(restoredChildren.isNotEmpty() || mmkvChildren.isNotEmpty()) {
                "backup archive is empty"
            }
            clearRootDirectory(rootDir)
            restoredChildren.forEach { child ->
                copyTree(child, File(rootDir, child.name))
            }
            restoreMmkvFiles(context, mmkvTempDir)
        } finally {
            tempDir.deleteRecursively()
            mmkvTempDir.deleteRecursively()
        }
    }

    private fun restoreMmkvFiles(context: Context, mmkvTempDir: File) {
        var mmkvFiles = mmkvTempDir.listFiles()
        // Fallback for old backups: mmkv/ entries were extracted into tempDir/mmkv/ instead
        if (mmkvFiles.isNullOrEmpty()) {
            val legacyMmkvDir = File(context.cacheDir, "$RESTORE_TEMP_DIR/$MMKV_DIR_NAME")
            if (legacyMmkvDir.isDirectory) {
                mmkvFiles = legacyMmkvDir.listFiles()
            }
        }
        if (mmkvFiles.isNullOrEmpty()) return
        val mmkvRoot = File(MMKV.getRootDir())
        mmkvRoot.mkdirs()
        mmkvFiles.forEach { file ->
            if (file.isFile) {
                val target = File(mmkvRoot, file.name)
                file.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        MMKV.onExit()
        MMKV.initialize(context)
    }
    private fun unzipToDirectory(inputStream: java.io.InputStream, targetDir: File, mmkvTargetDir: File? = null) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                val entryName = entry.name.replace('\\', '/')
                if (entryName.startsWith(MMKV_ZIP_PREFIX) && mmkvTargetDir != null) {
                    val fileName = entryName.removePrefix(MMKV_ZIP_PREFIX)
                    if (fileName.isNotBlank() && !entry.isDirectory) {
                        mmkvTargetDir.mkdirs()
                        FileOutputStream(File(mmkvTargetDir, fileName)).use { zipInputStream.copyTo(it) }
                    }
                } else {
                    val shouldSkip = entryName.trimEnd('/').split('/')
                        .any { it in EXCLUDED_ROOT_NAMES || it.isBlank() }
                    if (!shouldSkip) {
                        val targetFile = File(targetDir, entryName)
                        if (isSafeTarget(targetDir, targetFile)) {
                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { outputStream ->
                                    zipInputStream.copyTo(outputStream)
                                }
                            }
                        } else {
                            Timber.w("unzip → SKIPPED (unsafe): %s", entryName)
                        }
                    }
                }
                zipInputStream.closeEntry()
            }
        }
    }
    private fun clearRootDirectory(rootDir: File) {
        rootDir.listFiles()?.forEach { child ->
            if (child.name !in EXCLUDED_ROOT_NAMES && child.name != MMKV_DIR_NAME) {
                child.deleteRecursively()
            }
        }
    }
    private fun copyTree(source: File, target: File) {
        if (source.name in EXCLUDED_ROOT_NAMES || source.name == MMKV_DIR_NAME) {
            return
        }
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()
                ?.sortedBy { it.name }
                ?.forEach { child ->
                    copyTree(child, File(target, child.name))
                }
            return
        }
        target.parentFile?.mkdirs()
        source.inputStream().use { inputStream ->
            FileOutputStream(target).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
    private fun isSafeTarget(rootDir: File, targetFile: File): Boolean {
        val rootPath = rootDir.canonicalFile.toPath()
        val targetPath = targetFile.canonicalFile.toPath()
        return targetPath.startsWith(rootPath)
    }
}
