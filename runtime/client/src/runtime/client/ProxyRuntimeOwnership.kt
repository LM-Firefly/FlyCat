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

package com.github.yumelira.yumebox.runtime.client

import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.RuntimeSnapshot

internal object ProxyRuntimeOwnership {
    /** VpnService (in-process service) vs the root daemon (out-of-process, survives app death). */
    fun detectOwner(isVpnActive: () -> Boolean, isRootActive: () -> Boolean): RuntimeOwner =
        when {
            isVpnActive() -> RuntimeOwner.VpnService
            isRootActive() -> RuntimeOwner.RootDaemon
            else -> RuntimeOwner.None
        }

    fun ownerForMode(mode: RunMode): RuntimeOwner =
        when (mode) {
            RunMode.VpnService -> RuntimeOwner.VpnService
            RunMode.Tun, RunMode.Tproxy -> RuntimeOwner.RootDaemon
        }

    fun startingSnapshot(
        owner: RuntimeOwner,
        runMode: RunMode,
        profile: Profile,
        generation: Long,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = owner,
            phase = RuntimePhase.Starting,
            runMode = runMode,
            profileReady = true,
            profileUuid = profile.uuid.toString(),
            profileName = profile.name,
            startedAt = System.currentTimeMillis(),
            generation = generation,
        )

    fun activeSnapshot(
        owner: RuntimeOwner,
        runMode: RunMode,
        localPhase: RuntimePhase = RuntimePhase.Idle,
        localStartedAt: Long? = null,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = owner,
            phase =
                when (owner) {
                    RuntimeOwner.VpnService,
                    RuntimeOwner.RootDaemon -> localPhase
                    RuntimeOwner.RemoteController -> RuntimePhase.Running
                    RuntimeOwner.None -> RuntimePhase.Idle
                },
            runMode = runMode,
            startedAt =
                when (owner) {
                    RuntimeOwner.VpnService,
                    RuntimeOwner.RootDaemon -> localStartedAt
                    RuntimeOwner.RemoteController -> null
                    RuntimeOwner.None -> null
                },
        )

    fun startedSnapshot(
        current: RuntimeSnapshot,
        owner: RuntimeOwner,
        runMode: RunMode,
    ): RuntimeSnapshot =
        current.copy(
            owner = owner,
            phase = RuntimePhase.Running,
            runMode = runMode,
            lastError = null,
        )
}
