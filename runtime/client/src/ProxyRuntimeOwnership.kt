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
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.detectRuntimeOwner
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toRuntimeTargetMode

internal object ProxyRuntimeOwnership {
    fun detectOwner(
        rootStatus: RootTunStatus,
        isLocalSessionActive: (ProxyMode) -> Boolean,
    ): RuntimeOwner {
        return rootStatus.detectRuntimeOwner(isLocalSessionActive)
    }

    fun startingSnapshot(
        owner: RuntimeOwner,
        targetMode: ProxyMode,
        profile: Profile,
        generation: Long,
    ): RuntimeSnapshot {
        return RuntimeSnapshot(
            owner = owner,
            phase = RuntimePhase.Starting,
            targetMode = targetMode.toRuntimeTargetMode(),
            profileReady = true,
            profileUuid = profile.uuid.toString(),
            profileName = profile.name,
            startedAt = System.currentTimeMillis(),
            generation = generation,
        )
    }

    fun activeSnapshot(
        owner: RuntimeOwner,
        configuredMode: ProxyMode,
        rootStatus: RootTunStatus,
        localPhase: LocalRuntimePhase = LocalRuntimePhase.Idle,
        localStartedAt: Long? = null,
    ): RuntimeSnapshot {
        return RuntimeSnapshot(
            owner = owner,
            phase =
                when (owner) {
                    RuntimeOwner.RootTun -> rootStatus.state
                    RuntimeOwner.LocalTun,
                    RuntimeOwner.LocalHttp -> localPhase.toRuntimePhase()
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
                    RuntimeOwner.RootTun -> rootStatus.startedAt
                    RuntimeOwner.LocalTun,
                    RuntimeOwner.LocalHttp -> localStartedAt
                    RuntimeOwner.RemoteController -> null
                    RuntimeOwner.None -> null
                },
        )
    }

    fun startedSnapshot(
        current: RuntimeSnapshot,
        owner: RuntimeOwner,
        configuredMode: ProxyMode,
    ): RuntimeSnapshot {
        return current.copy(
            owner = owner,
            phase = RuntimePhase.Running,
            targetMode = modeForOwner(owner, configuredMode).toRuntimeTargetMode(),
            lastError = null,
        )
    }

    fun ownerForMode(mode: ProxyMode): RuntimeOwner {
        return when (mode) {
            ProxyMode.Tun -> RuntimeOwner.LocalTun
            ProxyMode.Http -> RuntimeOwner.LocalHttp
            ProxyMode.RootTun -> RuntimeOwner.RootTun
        }
    }

    fun modeForOwner(owner: RuntimeOwner, configuredMode: ProxyMode): ProxyMode {
        return when (owner) {
            RuntimeOwner.LocalTun -> ProxyMode.Tun
            RuntimeOwner.LocalHttp -> ProxyMode.Http
            RuntimeOwner.RootTun -> ProxyMode.RootTun
            RuntimeOwner.RemoteController -> configuredMode
            RuntimeOwner.None -> configuredMode
        }
    }
}
