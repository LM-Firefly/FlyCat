package com.github.yumelira.yumebox.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.github.yumelira.yumebox.core.model.GeoXItem
import com.github.yumelira.yumebox.core.util.AssetDownloader
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.data.store.AppStateManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetaFeatureViewModel(
    private val downloadClient: AssetDownloader,
    appStateManager: AppStateManager,
) : ViewModel() {
    private val appSettingsStore = appStateManager.appSettingsStore

    fun defaultBackupFileName(): String {
        return MetaBackupRestore.defaultBackupFileName()
    }

    suspend fun downloadGeoXFiles(
        context: Context,
        items: List<GeoXItem>,
    ): Int {
        var successCount = 0
        withContext(Dispatchers.IO) {
            val runtimeHome = context.runtimeHomeDir
            runtimeHome.mkdirs()
            items.forEach { item ->
                val targetFile = File(runtimeHome, item.fileName)
                if (downloadClient.download(item.url, targetFile)) {
                    successCount++
                }
            }
        }
        return successCount
    }

    suspend fun backupToUri(
        context: Context,
        targetUri: Uri,
    ): Result<Unit> {
        return MetaBackupRestore.backup(context, targetUri)
    }

    suspend fun restoreFromUri(
        context: Context,
        sourceUri: Uri,
    ): Result<Unit> {
        return MetaBackupRestore.restore(context, sourceUri)
    }

    fun getWebDavConfig(): MetaWebDavConfig {
        val webDav = appSettingsStore.webDav
        return MetaWebDavConfig(
            url = webDav.webDavUrl.value,
            account = webDav.webDavAccount.value,
            password = webDav.webDavPassword.value,
            directory = webDav.webDavDir.value,
        )
    }

    fun updateWebDavConfig(config: MetaWebDavConfig) {
        val webDav = appSettingsStore.webDav
        webDav.webDavUrl.set(config.url.trim())
        webDav.webDavAccount.set(config.account.trim())
        webDav.webDavPassword.set(config.password)
        webDav.webDavDir.set(config.directory.trim())
    }

    suspend fun testWebDavConfig(config: MetaWebDavConfig): Result<Unit> {
        return MetaWebDavBackup.test(config)
    }

    suspend fun backupToWebDav(context: Context): Result<String> {
        val config = getWebDavConfig()
        if (!config.isValid()) {
            return Result.failure(IllegalStateException("webdav is not configured"))
        }
        return MetaWebDavBackup.backup(context, config)
    }

    suspend fun restoreLatestFromWebDav(context: Context): Result<String> {
        val config = getWebDavConfig()
        if (!config.isValid()) {
            return Result.failure(IllegalStateException("webdav is not configured"))
        }
        return MetaWebDavBackup.restoreLatest(context, config)
    }
}
