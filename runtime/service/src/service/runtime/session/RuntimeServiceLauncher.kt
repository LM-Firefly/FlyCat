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

package com.github.yumelira.yumebox.runtime.service.runtime.session

import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.data.ServiceBootstrapHolder
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimeServiceContract
import com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toProxyMode
import com.github.yumelira.yumebox.runtime.service.ClashService
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.TunService

object RuntimeServiceLauncher : LocalRuntimeServiceContract {
    const val EXTRA_REQUEST_SOURCE = "runtime_request_source"

    const val SOURCE_UI = "ui"
    const val SOURCE_TILE = "tile"
    const val SOURCE_AUTO_RESTART = "auto_restart"
    const val SOURCE_AUTO_RESTART_BOOT = "auto_restart_boot"
    const val SOURCE_AUTO_RESTART_REPLACED = "auto_restart_replaced"
    const val SOURCE_UNKNOWN = "unknown"

    fun start(context: Context, mode: ProxyMode, source: String = ProxyServiceContracts.SOURCE_UNKNOWN) {
        require(mode != ProxyMode.RootTun) { "RuntimeServiceLauncher does not start RootTun" }

        val appContext = context.appContextOrSelf
        val store = RuntimeStartupLogStore(appContext, RuntimeStartupLogStore.scopeForMode(mode))
        store.clear()
        store.append(
            "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: request start source=$source mode=${mode.name}"
        )

        if (ServiceBootstrapHolder.reader.isRemoteControllerActive()) {
            store.append(
                "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: skipped, remote controller active"
            )
            return
        }

        // A redundant start against an already-running service would re-mark the persisted
        // phase as Starting with nothing to flip it back to Running afterwards.
        if (StatusProvider.queryRuntimePhase(mode) == RuntimePhase.Running) {
            store.append(
                "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: skipped, already running"
            )
            return
        }

        if (mode == ProxyMode.Tun && StatusProvider.isTunStarting()) {
            store.append("LOCAL_TUN launcher: skipped, already starting")
            return
        }

        // Only one local runtime may own the process-wide core; stop a lingering service of
        // the other mode here so every caller (UI, tile, auto restart) gets the exclusion,
        // otherwise the new session tears the core down underneath the old foreground service.
        val otherMode = if (mode == ProxyMode.Tun) ProxyMode.Http else ProxyMode.Tun
        if (StatusProvider.queryRuntimePhase(otherMode).isNotIdle) {
            store.append(
                "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: stopping previous ${otherMode.name} runtime"
            )
            runCatching { appContext.stopService(Intent(appContext, serviceClassFor(otherMode))) }
        }

        if (mode == ProxyMode.Tun) {
            StatusProvider.markTunStarting()
        }
        StatusProvider.markRuntimeStarting(mode)

        val intent =
            Intent(appContext, serviceClassFor(mode)).putExtra(EXTRA_REQUEST_SOURCE, source)

        runCatching { appContext.startForegroundService(intent) }
            .onFailure { error ->
                if (mode == ProxyMode.Tun) {
                    StatusProvider.clearTunStarting()
                }
                StatusProvider.markRuntimeIdle(mode)
                store.append(
                    "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: failed=${error.message}"
                )
                throw error
            }
    }

    private fun serviceClassFor(mode: ProxyMode): Class<*> =
        when (mode) {
            ProxyMode.Tun -> TunService::class.java
            ProxyMode.Http -> ClashService::class.java
            ProxyMode.RootTun -> error("unsupported mode")
        }

    override fun start(
        context: Context,
        mode: RuntimeTargetMode,
        source: String,
    ) {
        start(context = context, mode = mode.toProxyMode(), source = source)
    }

    override fun stop(
        context: Context,
        clashRequestStopAction: String,
    ) {
        val appContext = context.appContextOrSelf
        appContext.sendBroadcast(Intent(clashRequestStopAction).setPackage(appContext.packageName))
        appContext.stopService(Intent(appContext, TunService::class.java))
        appContext.stopService(Intent(appContext, ClashService::class.java))
    }
}
