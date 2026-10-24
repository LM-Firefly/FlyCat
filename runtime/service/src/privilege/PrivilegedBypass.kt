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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 超级岛 XMSF 防火墙绕过的会话式持有。HyperOS 只在网络持续被阻断时保留非官方超级岛，故防火墙在首个持有者到达时阻断、最后一个持有者离开时恢复。
 * 阻断经 priv-kit 特权服务（[PrivilegeManager]）执行。
 */
object PrivilegedBypass {
    private const val TAG = "FlyCatBypass"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private val mutex = Mutex()
    /** 持有 bypass 的活跃会话数。防火墙仅在 0↔1 翻转，避免每帧改写系统状态。 */
    private var holders = 0
    /** 计数代表一条尚未恢复的防火墙规则，而非活跃会话。 */
    private var restorePending = false
    /** 取得一份 XMSF bypass 持有。返回 false 表示防火墙未能改动，稍后调用会重试。 */
    suspend fun acquireXmsfBypass(context: Context): Boolean = mutex.withLock {
        if (restorePending) {
            restorePending = false
            return@withLock true
        }
        if (holders > 0) {
            holders += 1
            return@withLock true
        }
        if (!PrivilegeManager.isRunning()) {
            Log.i(TAG, "xmsf bypass skipped: privileged server not connected")
            return@withLock false
        }
        val uid = xmsfUid(context) ?: return@withLock false
        if (!PrivilegeManager.setPackageNetworkingBlocked(uid, blocked = true)) return@withLock false
        holders = 1
        true
    }
    /** 释放一份持有。若本持有仍是最后一份且防火墙恢复失败，计数保持为一以便下次释放重试。 */
    suspend fun releaseXmsfBypass(context: Context): Boolean = mutex.withLock {
        when {
            holders > 1 -> {
                holders -= 1
                true
            }
            holders == 1 -> {
                val uid = xmsfUid(context) ?: return@withLock false
                if (PrivilegeManager.setPackageNetworkingBlocked(uid, blocked = false)) {
                    holders = 0
                    restorePending = false
                    true
                } else {
                    restorePending = true
                    false
                }
            }
            else -> true
        }
    }
    private fun xmsfUid(context: Context): Int? =
        runCatching { context.packageManager.getPackageUid(XMSF_PACKAGE, 0) }
            .onFailure { error -> Log.w(TAG, "$XMSF_PACKAGE is not installed", error) }
            .getOrNull()
}
