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

package com.github.yumelira.yumebox.runtime.api.contract

import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.contract.entity.toRuntimeTargetMode

/**
 * Pure utility for mapping runtime state to UI-relevant information.
 * Moved from runtime:client to runtime:api so feature modules can use it
 * without depending on runtime:client.
 */
object RuntimeStateMapper {
    fun isRunningOrStarting(snapshot: RuntimeSnapshot): Boolean =
        snapshot.phase == RuntimePhase.Starting || snapshot.phase == RuntimePhase.Running

    fun isActuallyRunning(snapshot: RuntimeSnapshot): Boolean =
        snapshot.phase == RuntimePhase.Running

    fun modeForOwner(owner: RuntimeOwner): RunMode? =
        when (owner) {
            RuntimeOwner.LocalTun -> RunMode.VpnService
            RuntimeOwner.RootTun -> null // RootTun always runs Tun; use configured mode
            RuntimeOwner.RemoteController -> null
            RuntimeOwner.None -> null
        }

    fun resolveDisplayMode(snapshot: RuntimeSnapshot, configuredMode: RunMode): RunMode =
        if (isRunningOrStarting(snapshot)) {
            modeForOwner(snapshot.owner) ?: configuredMode
        } else {
            configuredMode
        }

    fun idleSnapshot(
        configuredMode: RunMode,
        generation: Long = 0L,
        lastError: String? = null,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = RuntimeOwner.None,
            phase = if (lastError.isNullOrBlank()) RuntimePhase.Idle else RuntimePhase.Failed,
            targetMode = configuredMode.toRuntimeTargetMode(),
            lastError = lastError,
            generation = generation,
        )
}
