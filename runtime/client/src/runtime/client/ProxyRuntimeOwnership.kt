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

import com.github.yumelira.yumebox.data.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.RuntimeSnapshot

internal object ProxyRuntimeOwnership {
    fun detectOwner(isLocalSessionActive: (ProxyMode) -> Boolean): RuntimeOwner =
        when {
            isLocalSessionActive(ProxyMode.Tun) -> RuntimeOwner.LocalTun
            isLocalSessionActive(ProxyMode.Http) -> RuntimeOwner.LocalHttp
            else -> RuntimeOwner.None
        }

    fun startingSnapshot(
        owner: RuntimeOwner,
        targetMode: ProxyMode,
        profile: Profile,
        generation: Long,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = owner,
            phase = RuntimePhase.Starting,
            targetMode = targetMode,
            profileReady = true,
            profileUuid = profile.uuid.toString(),
            profileName = profile.name,
            startedAt = System.currentTimeMillis(),
            generation = generation,
        )

    fun activeSnapshot(
        owner: RuntimeOwner,
        configuredMode: ProxyMode,
        localPhase: RuntimePhase = RuntimePhase.Idle,
        localStartedAt: Long? = null,
    ): RuntimeSnapshot =
        RuntimeSnapshot(
            owner = owner,
            phase =
                when (owner) {
                    RuntimeOwner.LocalTun,
                    RuntimeOwner.LocalHttp -> localPhase
                    RuntimeOwner.RemoteController -> RuntimePhase.Running
                    RuntimeOwner.None -> RuntimePhase.Idle
                },
            targetMode = modeForOwner(owner, configuredMode),
            startedAt =
                when (owner) {
                    RuntimeOwner.LocalTun,
                    RuntimeOwner.LocalHttp -> localStartedAt
                    RuntimeOwner.RemoteController -> null
                    RuntimeOwner.None -> null
                },
        )

    fun startedSnapshot(
        current: RuntimeSnapshot,
        owner: RuntimeOwner,
        configuredMode: ProxyMode,
    ): RuntimeSnapshot =
        current.copy(
            owner = owner,
            phase = RuntimePhase.Running,
            targetMode = modeForOwner(owner, configuredMode),
            lastError = null,
        )

    fun ownerForMode(mode: ProxyMode): RuntimeOwner =
        when (mode) {
            ProxyMode.Tun -> RuntimeOwner.LocalTun
            ProxyMode.Http -> RuntimeOwner.LocalHttp
        }

    fun modeForOwner(owner: RuntimeOwner, configuredMode: ProxyMode): ProxyMode =
        RuntimeStateMapper.modeForOwner(owner) ?: configuredMode

}
