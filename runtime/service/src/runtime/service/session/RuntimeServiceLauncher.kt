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

package com.github.yumeyucca.yumebox.runtime.service.session

import android.content.Context
import android.content.Intent
import com.github.yumeyucca.yumebox.data.model.RunMode
import com.github.yumeyucca.yumebox.data.store.RemoteControllerStore
import com.github.yumeyucca.yumebox.runtime.api.Intents
import com.github.yumeyucca.yumebox.runtime.api.RuntimePhase
import com.github.yumeyucca.yumebox.runtime.api.appContextOrSelf
import com.github.yumeyucca.yumebox.runtime.service.StatusProvider
import com.github.yumeyucca.yumebox.runtime.service.TunService
import com.github.yumeyucca.yumebox.runtime.service.log.RuntimeLog

object RuntimeServiceLauncher {
    const val EXTRA_REQUEST_SOURCE = "runtime_request_source"

    const val SOURCE_UI = "ui"
    const val SOURCE_TILE = "tile"
    const val SOURCE_AUTO_RESTART = "auto_restart"
    const val SOURCE_AUTO_RESTART_BOOT = "auto_restart_boot"
    const val SOURCE_AUTO_RESTART_REPLACED = "auto_restart_replaced"
    const val SOURCE_UNKNOWN = "unknown"

    // Only [RunMode.VpnService] is service-hosted; the root Tun daemon launches via
    // CoreProcess.startRoot (through RuntimeLauncher), not this launcher.
    @Synchronized
    fun start(
        context: Context,
        mode: RunMode = RunMode.VpnService,
        source: String = SOURCE_UNKNOWN,
    ) {
        val appContext = context.appContextOrSelf
        val log = RuntimeLog.writer(appContext, mode)

        if (RemoteControllerStore.isActive()) {
            log.i(RuntimeLog.Type.Launcher, "skipped: remote controller active")
            return
        }

        // A redundant start against an already-running service would re-mark the persisted
        // phase as Starting with nothing to flip it back to Running afterwards.
        val currentPhase = StatusProvider.queryRuntimePhase(mode)
        if (currentPhase == RuntimePhase.Running) {
            log.i(RuntimeLog.Type.Launcher, "skipped: already running")
            return
        }

        if (StatusProvider.isRuntimeStartingWithinGrace(mode)) {
            log.i(RuntimeLog.Type.Launcher, "skipped: already starting")
            return
        }

        // A Starting phase past the grace window with a live-but-stuck instance would swallow a new
        // command
        // (onCreate never re-runs). Ask it to recreate itself after releasing its old session,
        // don't re-token.
        if (
            StatusProvider.queryRuntimePhase(mode) == RuntimePhase.Starting &&
                StatusProvider.isLocalRuntimeServiceAlive(mode)
        ) {
            log.w(RuntimeLog.Type.Launcher, "stopping stale ${mode.name} runtime before restart")
            appContext.sendBroadcast(
                Intent(Intents.ACTION_RUNTIME_REQUEST_STOP)
                    .setPackage(appContext.packageName)
                    .putExtra(Intents.EXTRA_RESTART, true)
                    .putExtra(Intents.EXTRA_RUNTIME_MODE, mode.name)
            )
            return
        }

        log.beginSession(
            RuntimeLog.Type.Launcher,
            "request start source=$source mode=${mode.name}",
        )
        val sessionToken = StatusProvider.beginRuntimeSession(mode)

        val intent =
            Intent(appContext, TunService::class.java).putExtra(EXTRA_REQUEST_SOURCE, source)

        runCatching { appContext.startForegroundService(intent) }
            .onSuccess { log.i(RuntimeLog.Type.Launcher, "service start requested") }
            .onFailure { error ->
                StatusProvider.markRuntimeIdle(mode, sessionToken)
                log.e(RuntimeLog.Type.Launcher, "service start request failed", error)
                throw error
            }
    }

    @Synchronized
    fun stop(context: Context, mode: RunMode = RunMode.VpnService) {
        val appContext = context.appContextOrSelf
        runCatching { appContext.stopService(Intent(appContext, TunService::class.java)) }
        StatusProvider.markRuntimeIdle(mode)
    }
}
