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

package com.github.yumelira.yumebox.runtime.service.runtime.session

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.contract.ServiceBootstrapHolder
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.toRunMode
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimeServiceContract
import com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
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
    const val SOURCE_WIFI_AUTOMATION = "wifi_automation"

    fun start(context: Context, mode: RunMode, source: String = ProxyServiceContracts.SOURCE_UNKNOWN) {
        require(mode == RunMode.VpnService) { "RuntimeServiceLauncher only supports VpnService mode; use RootTunServiceBridge for root modes" }

        val appContext = context.appContextOrSelf
        StatusProvider.markRuntimeRequestSource(source)
        val logScope = RuntimeStartupLogStore.scopeForMode(mode)
        val startupLogStore = RuntimeStartupLogStore(appContext, logScope)
        startupLogStore.clear()
        startupLogStore.append(
            "${logScope.tag} launcher: request start source=$source mode=${mode.name}"
        )

        if (ServiceBootstrapHolder.reader.isRemoteControllerActive()) {
            startupLogStore.append(
                "${logScope.tag} launcher: skipped, remote controller active"
            )
            return
        }

        // A redundant start against an already-running service would re-mark the persisted
        // phase as Starting with nothing to flip it back to Running afterwards.
        if (StatusProvider.queryRuntimePhase(mode) == RuntimePhase.Running) {
            startupLogStore.append("${logScope.tag} launcher: skipped, already running")
            return
        }

        if (StatusProvider.isTunStarting()) {
            startupLogStore.append("LOCAL_TUN launcher: skipped, already starting")
            return
        }

        StatusProvider.markTunStarting()
        StatusProvider.markRuntimeStarting(mode)

        val intent =
            Intent(appContext, TunService::class.java).putExtra(EXTRA_REQUEST_SOURCE, source)

        runCatching { appContext.startForegroundService(intent) }
            .onFailure { error ->
                StatusProvider.clearTunStarting()
                StatusProvider.markRuntimeIdle(mode)
                startupLogStore.append("${logScope.tag} launcher: failed=${error.message}")
                throw error
            }
    }

    fun stop(context: Context, mode: RunMode) {
        require(mode == RunMode.VpnService) { "RuntimeServiceLauncher only supports VpnService mode" }

        val appContext = context.appContextOrSelf
        runCatching { appContext.stopService(Intent(appContext, TunService::class.java)) }
        StatusProvider.clearTunStarting()
        StatusProvider.markRuntimeIdle(mode)
    }

    override fun start(
        context: Context,
        mode: RuntimeTargetMode,
        source: String,
    ) {
        start(context = context, mode = mode.toRunMode(), source = source)
    }

    override fun stop(
        context: Context,
        clashRequestStopAction: String,
    ) {
        val appContext = context.appContextOrSelf
        appContext.sendBroadcast(Intent(clashRequestStopAction).setPackage(appContext.packageName))
        appContext.stopService(Intent(appContext, TunService::class.java))
    }
}
