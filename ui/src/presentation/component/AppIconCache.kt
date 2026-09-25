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

package com.github.lmfirefly.flycat.presentation.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 进程级应用图标位图缓存。列表项离开组合后重新进入时同步命中缓存，避免每次重组/滚动都回落到首字母占位徽标（图标"概率丢失"）。 */
object AppIconCache {
    private const val MAX_ENTRIES = 128
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean = size > MAX_ENTRIES
    }
    private fun key(packageName: String, bitmapSize: Int): String = "$packageName@$bitmapSize"
    fun get(packageName: String, bitmapSize: Int): ImageBitmap? = synchronized(lock) { cache[key(packageName, bitmapSize)] }
    fun resolve(context: Context, packageName: String?, bitmapSize: Int): ImageBitmap? {
        val target = packageName?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: return null
        get(target, bitmapSize)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(target).toBitmap(width = bitmapSize, height = bitmapSize).asImageBitmap()
        }.getOrNull() ?: return null
        synchronized(lock) { cache[key(target, bitmapSize)] = bitmap }
        return bitmap
    }
}

/** 按 [packageName] 记忆应用图标；缓存命中时首帧即返回，未命中则异步加载并回填缓存。 */
@Composable
fun rememberAppIconBitmap(packageName: String?, bitmapSize: Int): ImageBitmap? {
    val context = LocalContext.current
    val initial = packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { AppIconCache.get(it, bitmapSize) }
    val iconBitmap by produceState<ImageBitmap?>(initialValue = initial, key1 = packageName, key2 = bitmapSize) {
        value = withContext(Dispatchers.IO) {
            AppIconCache.resolve(context, packageName, bitmapSize)
        }
    }
    return iconBitmap
}
