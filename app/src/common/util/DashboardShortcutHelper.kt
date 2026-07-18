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

package com.github.yumelira.yumebox.common.util

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.yumelira.yumebox.R
import com.github.yumelira.yumebox.WebViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DashboardShortcutHelper {
    suspend fun createPanelShortcut(
        context: Context,
        url: String,
        label: String,
        iconUri: Uri? = null,
    ) {
        if (url.isBlank()) return
        val icon =
            withContext(Dispatchers.IO) {
                iconUri?.let { loadIconFromUri(context, it) } ?: fetchFavicon(context, url)
            }
        val shortcut =
            ShortcutInfoCompat.Builder(context, "panel_" + url.hashCode())
                .setShortLabel(label.ifBlank { "Dashboard" })
                .setLongLabel(label.ifBlank { "Dashboard" })
                .setIcon(icon)
                .setIntent(WebViewActivity.intent(context, url))
                .build()
        withContext(Dispatchers.Main) {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            }
        }
    }

    private fun loadIconFromUri(context: Context, uri: Uri): IconCompat? {
        return runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeStream(input, null, opts)
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    if (maxDim > 0) {
                        opts.inSampleSize = maxDim / 128
                        opts.inJustDecodeBounds = false
                    }
                    context.contentResolver.openInputStream(uri)?.use { input2 ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(input2, null, opts) ?: return null
                        IconCompat.createWithBitmap(bmp)
                    }
                }
            }
            .getOrNull()
    }

    private fun fetchFavicon(context: Context, url: String): IconCompat {
        return runCatching {
                val u = java.net.URL(url)
                val faviconUrl = java.net.URL("${u.protocol}://${u.host}/favicon.ico")
                val conn =
                    (faviconUrl.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                    }
                conn.inputStream.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > 512 * 1024) return@runCatching null // 512KB max for favicon
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
                    val bmp =
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            ?: return@runCatching null
                    IconCompat.createWithBitmap(bmp)
                }
            }
            .getOrNull() ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }
}
