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
import android.content.Intent
import android.os.DeadObjectException
import android.os.IInterface
import android.os.RemoteException
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunRuntimeRecoveryContract
import com.github.yumelira.yumebox.runtime.service.RootTunService
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.topjohnwu.superuser.ipc.RootService

object RootTunRuntimeRecovery : RootTunRuntimeRecoveryContract {
    private val bindingFailureMarkers =
        listOf("root tun binder is null", "root tun service returned null binding", "binding died")

    override fun isBinderAlive(service: IInterface?): Boolean {
        val remote = service?.asBinder() ?: return false
        return remote.isBinderAlive && remote.pingBinder()
    }

    override fun isBinderConnectionFailure(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }
            .any { cause ->
                cause is DeadObjectException ||
                    cause is RemoteException ||
                    (cause is IllegalStateException &&
                        bindingFailureMarkers.any { marker ->
                            cause.message?.contains(marker, ignoreCase = true) == true
                        })
            }
    }

    override fun binderFailureReason(error: Throwable): String {
        return generateSequence(error) { it.cause }
            .mapNotNull { cause -> cause.message?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull() ?: "RootTun IPC disconnected"
    }

    override fun handleBinderGone(context: Context, reason: String?) {
        val appContext = context.appContextOrSelf
        val previous = RootTunStatusFlow.current(appContext)
        val hadRuntime = previous.state.isActiveOrStopping || previous.runtimeReady
        val message = reason?.takeIf { it.isNotBlank() } ?: previous.lastError

        if (hadRuntime || !message.isNullOrBlank()) {
            RootTunStatusFlow.markIdle(message)
        }

        StatusProvider.markRuntimeIdle(ProxyMode.RootTun)
        runCatching { RootTunService.stop(appContext) }
        runCatching { RootService.stop(Intent(appContext, RootTunRootService::class.java)) }

        if (!hadRuntime) return

        runCatching {
            appContext.sendBroadcast(
                Intent(Intents.actionClashStopped(appContext.packageName))
                    .setPackage(appContext.packageName)
                    .putExtra(Intents.EXTRA_STOP_REASON, message)
            )
        }
    }
}
