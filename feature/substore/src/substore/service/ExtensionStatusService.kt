package com.github.yumelira.yumebox.feature.substore.service

import android.content.Context
import com.github.yumelira.yumebox.feature.substore.engine.NativeLibraryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class ExtensionStatusService(private val context: Context) {
    companion object {
        private const val EXTENSION_PACKAGE_NAME = "com.github.yumelira.yumebox.extension"
        private const val JAVET_LIB_NAME = "libjavet-node-android"
    }
    suspend fun isExtensionAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (isExtensionPackageInstalled()) {
            Timber.d("Extension available: package installed")
            return@withContext true
        }
        if (isAvailableViaManager()) {
            Timber.d("Extension available: extracted via NativeLibraryManager")
            return@withContext true
        }
        Timber.d("Extension not available: all checks exhausted")
        false
    }
    suspend fun isJavetLoaded(): Boolean = withContext(Dispatchers.IO) {
        NativeLibraryManager.initialize(context)
        NativeLibraryManager.isLibraryAvailable(JAVET_LIB_NAME) ||
            NativeLibraryManager.extractAllLibraries()[JAVET_LIB_NAME] == true
    }
    private fun isExtensionPackageInstalled(): Boolean = runCatching {
        context.packageManager.getApplicationInfo(EXTENSION_PACKAGE_NAME, 0)
        true
    }.getOrDefault(false)
    private fun isAvailableViaManager(): Boolean = runCatching {
        NativeLibraryManager.initialize(context)
        NativeLibraryManager.extractAllLibraries()[JAVET_LIB_NAME] == true
    }.getOrElse { e ->
        Timber.w(e, "NativeLibraryManager check failed")
        false
    }
}
