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

import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.contract.entity.detectRuntimeOwner
import com.github.yumelira.yumebox.runtime.api.contract.entity.toRuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.toRuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus

internal object ProxyRuntimeOwnership {
    fun detectOwner(
        rootStatus: RootTunStatus,
        isLocalSessionActive: (RunMode) -> Boolean,
    ): RuntimeOwner =
        rootStatus.detectRuntimeOwner(isLocalSessionActive)

    fun startingSnapshot(
        owner: RuntimeOwner,
        targetMode: RunMode,
        profile: Profile,
        generation: Long,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = owner,
            phase = RuntimePhase.Starting,
            targetMode = targetMode.toRuntimeTargetMode(),
            profileReady = true,
            profileUuid = profile.uuid.toString(),
            profileName = profile.name,
            startedAt = System.currentTimeMillis(),
            generation = generation,
        )

    fun activeSnapshot(
        owner: RuntimeOwner,
        configuredMode: RunMode,
        rootStatus: RootTunStatus,
        localPhase: LocalRuntimePhase = LocalRuntimePhase.Idle,
        localStartedAt: Long? = null,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = owner,
            phase =
                when (owner) {
                    RuntimeOwner.LocalTun -> localPhase.toRuntimePhase()
                    RuntimeOwner.RootTun -> rootStatus.state
                    RuntimeOwner.RemoteController -> RuntimePhase.Running
                    RuntimeOwner.None -> RuntimePhase.Idle
                },
            targetMode = modeForOwner(owner, configuredMode).toRuntimeTargetMode(),
            profileReady = owner == RuntimeOwner.RootTun && !rootStatus.profileUuid.isNullOrBlank(),
            profileUuid = rootStatus.profileUuid.takeIf { owner == RuntimeOwner.RootTun },
            profileName = rootStatus.profileName.takeIf { owner == RuntimeOwner.RootTun },
            lastError = if (owner == RuntimeOwner.RootTun) rootStatus.lastError else null,
            startedAt =
                when (owner) {
                    RuntimeOwner.LocalTun -> localStartedAt
                    RuntimeOwner.RootTun -> rootStatus.startedAt
                    RuntimeOwner.RemoteController -> null
                    RuntimeOwner.None -> null
                },
        )

    fun startedSnapshot(
        current: RuntimeSnapshot,
        owner: RuntimeOwner,
        configuredMode: RunMode,
    ): RuntimeSnapshot =
        current.copy(
            owner = owner,
            phase = RuntimePhase.Running,
            targetMode = modeForOwner(owner, configuredMode).toRuntimeTargetMode(),
            lastError = null,
        )

    fun ownerForMode(mode: RunMode): RuntimeOwner =
        when (mode) {
            RunMode.Vpn -> RuntimeOwner.LocalTun
            RunMode.Tun -> RuntimeOwner.RootTun
            RunMode.Tproxy -> RuntimeOwner.RootTun
        }

    fun modeForOwner(owner: RuntimeOwner, configuredMode: RunMode): RunMode =
        when (owner) {
            RuntimeOwner.LocalTun -> RunMode.Vpn
            RuntimeOwner.RootTun -> configuredMode
            RuntimeOwner.RemoteController -> configuredMode
            RuntimeOwner.None -> configuredMode
        }
}
