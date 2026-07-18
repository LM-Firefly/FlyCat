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

package com.github.yumelira.yumebox.core.util

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

object NetworkUtil {
    fun isPortInUse(port: Int): Boolean =
        try {
            ServerSocket(port).use { false }
        } catch (_: IOException) {
            true
        }

    suspend fun waitForPortReady(host: String, port: Int, timeoutMs: Long = 5000, probeIntervalMs: Long = 120): Boolean = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 300)
                    true
                }
            }.getOrElse { false }
            if (connected) return@withContext true
            delay(probeIntervalMs)
        }
        false
    }
}
