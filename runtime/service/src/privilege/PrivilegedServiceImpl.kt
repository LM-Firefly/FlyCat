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

package com.github.lmfirefly.flycat.runtime.service.privilege

import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shizuku UserService：以 shell 身份执行设备端命令，用于外部拉起 priv-kit 服务端。 */
class PrivilegedServiceImpl : IPrivilegedService.Stub() {
    companion object {
        private const val TAG = "FlyCatPrivilegedService"
        private const val EXEC_TIMEOUT_SECONDS = 8L
    }
    override fun exec(commandLine: String): Boolean {
        if (commandLine.isBlank()) return false
        // 线程 + 超时防护，避免挂起的命令阻塞调用方的 binder 事务。
        val latch = CountDownLatch(1)
        var result = false
        Thread {
            try {
                val process = ProcessBuilder("sh", "-c", commandLine).start()
                result = process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0
            } catch (error: Throwable) {
                Log.e(TAG, "Command execution failed", error)
            } finally {
                latch.countDown()
            }
        }.start()
        if (!latch.await(EXEC_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS)) {
            Log.w(TAG, "Command execution timed out")
            return false
        }
        return result
    }
}
