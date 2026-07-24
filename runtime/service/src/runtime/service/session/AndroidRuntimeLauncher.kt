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

@file:Suppress("ConvertLongToDuration")

package com.github.yumelira.yumebox.runtime.service.session

import kotlin.time.Duration.Companion.milliseconds

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.RuntimeLauncher
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.TunService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Android [RuntimeLauncher]: VpnService foreground host + root daemon host. Desktop can ship a
 * different [RuntimeLauncher] without Android Service APIs.
 */
class AndroidRuntimeLauncher(
    context: Context,
    private val stopAction: () -> String,
) : RuntimeLauncher {
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
            appContext.sendBroadcast(Intent(stopAction()).setPackage(appContext.packageName))
            appContext.stopService(Intent(appContext, TunService::class.java))
            awaitVpnServiceStopped()
        }
    }

    private suspend fun awaitVpnServiceStopped() {
        val deadline = SystemClock.elapsedRealtime() + STOP_HANDOVER_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!StatusProvider.isLocalRuntimeServiceAlive(RunMode.VpnService)) return
            delay(STOP_HANDOVER_POLL_MS.milliseconds)
        }
    }

    private companion object {
        const val STOP_HANDOVER_TIMEOUT_MS = 5_000L
        const val STOP_HANDOVER_POLL_MS = 100L
    }
}
