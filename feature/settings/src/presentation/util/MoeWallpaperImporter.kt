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

package com.github.lmfirefly.flycat.feature.settings.presentation.util

import android.content.Context
import android.net.Uri
import com.github.lmfirefly.flycat.core.util.path.moeWallpaperFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 将 Moe 壁纸从外部 [sourceUri] 复制到应用私有文件目录中，使渲染不因原始来源的删除/权限丢失而失效。
 * 支持三种来源类型：
 * - `http://` / `https://` 远程 URL（通过 HTTP 下载）
 * - `content://` 来自照片选择器的 URI
 * - `file://` 本地路径
 *
 * 复制先写入临时文件，再重命名覆盖到稳定槽位，确保半写入的副本不会替换完好副本（存储失败的边界情况）。
 * @return 成功时返回指向本地副本的 `file://` URI，若源无法读取则返回 `null`（此时调用方应回退到原始来源 URI）。
 *   本地副本是固定文件名覆盖写入的，返回的 URI 会附加 `#v=<时间戳>` 内容版本后缀：写盘后 URI 字符串必然变化，既让设置流在同路径重写后仍能发射更新，也让渲染侧的解码缓存键随内容失效（见 `wallpaperCacheKey`）。
 */
object MoeWallpaperImporter {
    suspend fun importToLocal(context: Context, sourceUri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = context.moeWallpaperFile()
            val tmp = File(target.parentFile, "wallpaper.tmp")
            if (sourceUri.startsWith("http://") || sourceUri.startsWith("https://")) {
                downloadToFile(sourceUri, tmp)
            } else {
                context.contentResolver.openInputStream(Uri.parse(sourceUri)).use { input ->
                    requireNotNull(input) {
                        "Unable to open Moe wallpaper source: $sourceUri"
                    }
                    tmp.outputStream().use { input.copyTo(it) }
                }
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Failed to swap Moe wallpaper temp file" }
            }
            "file://${target.absolutePath}#v=${System.currentTimeMillis()}"
        }.getOrNull()
    }
    private fun downloadToFile(remoteUrl: String, target: File) {
        val conn = URL(remoteUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()
            check(conn.responseCode in 200..299) {
                "HTTP ${conn.responseCode} downloading wallpaper: $remoteUrl"
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }
}
