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

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.TunService
import com.github.yumelira.yumebox.runtime.service.session.RootSessionLauncher
import com.github.yumelira.yumebox.runtime.service.session.RuntimeServiceLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class ProxyRuntimeControl(
    context: Context,
    private val clashRequestStopAction: () -> String,
) {
    private val appContext = context.appContextOrSelf

    suspend fun start(owner: RuntimeOwner, mode: RunMode) {
        when (owner) {
            // VpnService is the in-process foreground service; the root daemon launches out-of-process.
            RuntimeOwner.VpnService ->
                RuntimeServiceLauncher.start(
                    context = appContext,
                    mode = mode,
                    source = RuntimeServiceLauncher.SOURCE_UI,
                )

            RuntimeOwner.RootDaemon -> RootSessionLauncher.start(appContext, mode)

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }

    suspend fun stop(owner: RuntimeOwner) {
        when (owner) {
            RuntimeOwner.VpnService -> stopVpnRuntime()

            // Explicit stop of the decoupled daemon: `su kill` + release its status slot.
            RuntimeOwner.RootDaemon -> withContext(Dispatchers.IO) { RootSessionLauncher.stop(appContext) }

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }

    private suspend fun stopVpnRuntime() {
        withContext(Dispatchers.IO) {
            // Broadcast the graceful-teardown request unconditionally (like the QS tile): the handler
            // kills the core child before stopSelf. REST requestStop() is a no-op for the core we own.
            appContext.sendBroadcast(
                Intent(clashRequestStopAction()).setPackage(appContext.packageName)
            )
            appContext.stopService(Intent(appContext, TunService::class.java))
            // stopService is async: a start issued while the old instance is still dying is swallowed
            // (and its late stopSelf can kill the replacement). Serialize by waiting until it's gone.
            awaitVpnServiceStopped()
        }
    }

    private suspend fun awaitVpnServiceStopped() {
        val deadline = SystemClock.elapsedRealtime() + STOP_HANDOVER_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!StatusProvider.isLocalRuntimeServiceAlive(RunMode.VpnService)) return
            delay(STOP_HANDOVER_POLL_MS)
        }
    }

    private companion object {
        const val STOP_HANDOVER_TIMEOUT_MS = 5_000L
        const val STOP_HANDOVER_POLL_MS = 100L
    }
}
