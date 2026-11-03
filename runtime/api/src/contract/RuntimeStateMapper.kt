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

package com.github.lmfirefly.flycat.runtime.api.contract

import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.api.contract.toRuntimeTargetMode

/**
 * 用于把运行时状态映射为 UI 关注信息的纯工具函数集。
 * 从 `runtime:client` 移入 `runtime:api`，使功能模块无需依赖 `runtime:client` 即可使用。
 */
object RuntimeStateMapper {
    fun isRunningOrStarting(snapshot: RuntimeSnapshot): Boolean = snapshot.phase == RuntimePhase.Starting || snapshot.phase == RuntimePhase.Running

    fun isActuallyRunning(snapshot: RuntimeSnapshot): Boolean = snapshot.phase == RuntimePhase.Running

    fun modeForOwner(owner: RuntimeOwner): RunMode? = when (owner) {
        RuntimeOwner.LocalTun -> RunMode.VpnService
        RuntimeOwner.RootTun -> null // RootTun 始终运行 Tun；使用配置模式
        RuntimeOwner.RemoteController -> null
        RuntimeOwner.None -> null
    }

    fun resolveDisplayMode(snapshot: RuntimeSnapshot, configuredMode: RunMode): RunMode = if (isRunningOrStarting(snapshot)) {
        modeForOwner(snapshot.owner) ?: configuredMode
    } else {
        configuredMode
    }

    fun idleSnapshot(
        configuredMode: RunMode,
        generation: Long = 0L,
        lastError: String? = null,
    ): RuntimeSnapshot = RuntimeSnapshot(
        owner = RuntimeOwner.None,
        phase = if (lastError.isNullOrBlank()) RuntimePhase.Idle else RuntimePhase.Failed,
        targetMode = configuredMode.toRuntimeTargetMode(),
        lastError = lastError,
        generation = generation,
    )
}
