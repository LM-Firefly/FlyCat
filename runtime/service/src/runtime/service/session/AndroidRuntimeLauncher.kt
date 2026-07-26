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

package com.github.yumelira.yumebox.runtime.service.session

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.RuntimeLauncher
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.TunService
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Android [RuntimeLauncher]: VpnService foreground host + root daemon host. Desktop can ship a
 * different [RuntimeLauncher] without Android Service APIs.
 */
class AndroidRuntimeLauncher(context: Context) : RuntimeLauncher {
    private val appContext = context.appContextOrSelf

    override suspend fun start(owner: RuntimeOwner, mode: RunMode) {
        when (owner) {
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

    override suspend fun stop(owner: RuntimeOwner) {
        when (owner) {
            RuntimeOwner.VpnService -> stopVpnRuntime()
            RuntimeOwner.RootDaemon ->
                withContext(Dispatchers.IO) { RootSessionLauncher.stop(appContext) }

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }

    private suspend fun stopVpnRuntime() {
        withContext(Dispatchers.IO) {
            appContext.stopService(Intent(appContext, TunService::class.java))
            val deadline = SystemClock.elapsedRealtime() + VPN_STOP_TIMEOUT_MS
            while (
                StatusProvider.isLocalRuntimeServiceAlive(RunMode.VpnService) &&
                    SystemClock.elapsedRealtime() < deadline
            ) {
                delay(VPN_STOP_POLL_MS)
            }
            if (StatusProvider.isLocalRuntimeServiceAlive(RunMode.VpnService)) {
                CoreProcess.killRunning()
            }
        }
    }

    private companion object {
        const val VPN_STOP_TIMEOUT_MS = 1_000L
        const val VPN_STOP_POLL_MS = 25L
    }
}
