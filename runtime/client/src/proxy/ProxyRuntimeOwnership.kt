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

package com.github.lmfirefly.flycat.runtime.client

import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.runtime.api.contract.LocalRuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.api.contract.detectRuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.toRuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.toRuntimeTargetMode
import com.github.lmfirefly.flycat.runtime.api.root.RootTunStatus

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
            RunMode.VpnService -> RuntimeOwner.LocalTun
            RunMode.Tun -> RuntimeOwner.RootTun
            RunMode.Ebpf -> RuntimeOwner.RootTun // eBPF shares root daemon path
        }

    fun modeForOwner(owner: RuntimeOwner, configuredMode: RunMode): RunMode =
        when (owner) {
            RuntimeOwner.LocalTun -> RunMode.VpnService
            RuntimeOwner.RootTun -> configuredMode
            RuntimeOwner.RemoteController -> configuredMode
            RuntimeOwner.None -> configuredMode
        }
}
