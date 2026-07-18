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

package com.github.yumelira.yumebox.runtime.service.root

import android.content.Context
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeSnapshot
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeHost
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStarted
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendClashStopped
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendProfileLoaded
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import java.util.UUID

/**
 * Runs in the root process. The runtime phase is published exclusively through
 * [RootTunStatePublisher] (`root_tun_state` single-writer rule); the main-process
 * `service_cache` phase mirror is written only by the main process (RootTunService),
 * never from here — two processes writing the same MMKV keys races the phase back.
 */
internal class RootTunRuntimeHost(
    private val service: RootTunRootService,
    internal val statePublisher: RootTunStatePublisher,
) : RuntimeHost {
    private var startedBroadcastSent = false
    private var lastRuntimeSpec: RuntimeSpec? = null

    override val context: Context = service
    override val mode: RunMode = RunMode.Tun

    override fun onStarting(spec: RuntimeSpec) {
        startedBroadcastSent = false
        lastRuntimeSpec = spec
        StatusProvider.clearLegacyStateFiles()
        StatusProvider.markRuntimeStarting(RunMode.Tun)
    }

    override fun onStarted(spec: RuntimeSpec) {
        lastRuntimeSpec = spec
        StatusProvider.markRuntimeRunning(RunMode.Tun)
        service.sendClashStarted()
        startedBroadcastSent = true
    }

    override fun onStopped(reason: String?) {
        StatusProvider.markRuntimeIdle(RunMode.Tun)
        service.sendClashStopped(reason)
    }

    override fun onProfileLoaded(profileUuid: String) {
        service.sendProfileLoaded(UUID.fromString(profileUuid))
    }

    override fun onSnapshotChanged(snapshot: RuntimeSnapshot) {
        statePublisher.update(snapshot.toRootTunStatus(lastRuntimeSpec))
        if (snapshot.phase == RuntimePhase.Running && !startedBroadcastSent) {
            service.sendClashStarted()
            startedBroadcastSent = true
        }
    }

    override fun onLogReady(ready: Boolean) {
        val current = statePublisher.snapshot()
        statePublisher.update(
            current.copy(controllerReady = true, runtimeReady = ready || current.runtimeReady)
        )
    }

    override fun onLogItem(log: LogMessage) = Unit

    override fun reportFailure(error: String) {
        StatusProvider.markRuntimeFailed(RunMode.Tun)
        statePublisher.update(
            statePublisher
                .snapshot()
                .copy(
                    state = RuntimePhase.Failed,
                    running = false,
                    lastError = error,
                    runtimeReady = false,
                )
        )
        service.sendClashStopped(error)
    }

    private fun RuntimeSnapshot.toRootTunStatus(spec: RuntimeSpec?): RootTunStatus {
        val state =
            when (phase) {
                RuntimePhase.Idle -> RuntimePhase.Idle
                RuntimePhase.Starting -> RuntimePhase.Starting
                RuntimePhase.Running -> RuntimePhase.Running
                RuntimePhase.Stopping -> RuntimePhase.Stopping
                RuntimePhase.Failed -> RuntimePhase.Failed
            }
        return RootTunStatus(
            state = state,
            running = state.isActiveOrStopping,
            lastError = lastError,
            profileUuid = profileUuid,
            profileName = profileName,
            runtimeReady = phase == RuntimePhase.Running,
            controllerReady = true,
            startedAt = startedAt,
            staticPlanFingerprint = spec?.staticPlanFingerprint,
            transportFingerprint = spec?.transportFingerprint,
            overrideFingerprint = effectiveFingerprint ?: spec?.effectiveFingerprint,
            profileFingerprint = spec?.profileFingerprint,
        )
    }
}
