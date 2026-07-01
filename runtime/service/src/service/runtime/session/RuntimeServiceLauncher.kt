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
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimeServiceContract
import com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toProxyMode
import com.github.yumelira.yumebox.runtime.service.ClashService
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.TunService

object RuntimeServiceLauncher : LocalRuntimeServiceContract {
    const val EXTRA_REQUEST_SOURCE = "runtime_request_source"

    fun start(context: Context, mode: ProxyMode, source: String = ProxyServiceContracts.SOURCE_UNKNOWN) {
        require(mode != ProxyMode.RootTun) { "RuntimeServiceLauncher does not start RootTun" }

        val appContext = context.appContextOrSelf
        val store = RuntimeStartupLogStore(appContext, RuntimeStartupLogStore.scopeForMode(mode))
        store.clear()
        store.append(
            "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: request start source=$source mode=${mode.name}"
        )

        if (RemoteControllerStore.isActive()) {
            store.append(
                "${RuntimeStartupLogStore.scopeForMode(mode).tag} launcher: skipped, remote controller active"
            )
            return
        }

        if (mode == ProxyMode.Tun && StatusProvider.isTunStarting()) {
            store.append("LOCAL_TUN launcher: skipped, already starting")
            return
        }

        if (mode == ProxyMode.Tun) {
            StatusProvider.markTunStarting()
        }
        StatusProvider.markRuntimeStarting(mode)

        val intent =
            Intent(
                    appContext,
                    when (mode) {
                        ProxyMode.Tun -> TunService::class.java
                        ProxyMode.Http -> ClashService::class.java
                        ProxyMode.RootTun -> error("unsupported mode")
                    },
                )
                .putExtra(EXTRA_REQUEST_SOURCE, source)

        runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }
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
