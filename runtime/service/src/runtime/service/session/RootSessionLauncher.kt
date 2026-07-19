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
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess

/**
 * Launches the root [RunMode.Tun] / [RunMode.Tproxy] daemon. Unlike [RuntimeServiceLauncher] there is
 * no foreground service: the core runs as a decoupled `su` daemon (survives app death) and the client
 * drives it over the REST socket, reattaching via [CoreProcess.reconnectRoot]. Only an explicit stop
 * kills it ([CoreProcess.stopRoot]); a service teardown never does.
 */
object RootSessionLauncher {
    /** Compile the active profile for [mode] and launch the detached root daemon. */
    suspend fun start(context: Context, mode: RunMode) {
        require(mode == RunMode.Tun || mode == RunMode.Tproxy) {
            "RootSessionLauncher handles root modes only, got $mode"
        }
        val appContext = context.appContextOrSelf
        val logScope = RuntimeStartupLogStore.scopeForMode(mode)
        val log = RuntimeStartupLogStore(appContext, logScope)
        log.clear()
        log.append("${logScope.tag} root launcher: start mode=${mode.name}")

        StatusProvider.markRuntimeStarting(mode)
        val spec = SessionRuntimeSpecFactory(appContext).createRootSpec(mode)
        val config = CompiledConfigPipeline(appContext).compile(spec)
        runCatching { CoreProcess(appContext).startRoot(mode.coreArg, config) }
            .onFailure { error ->
                StatusProvider.markRuntimeFailed(mode, error.message)
                log.append("${logScope.tag} root launcher: failed=${error.message}")
                throw error
            }

        StatusProvider.markRuntimeRunning(mode)
        broadcast(appContext, Intents.actionClashStarted(appContext.packageName))
        log.append("${logScope.tag} root launcher: done")
    }

    /** Explicitly stop the daemon and release its status slot. */
    fun stop(context: Context) {
        val appContext = context.appContextOrSelf
        val mode = CoreProcess.rootDaemonMode()
        runCatching { CoreProcess.stopRoot() }
        mode?.let { StatusProvider.markRuntimeIdle(it) }
        broadcast(appContext, Intents.actionClashStopped(appContext.packageName))
    }

    private fun broadcast(context: Context, action: String) {
        runCatching {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        }
    }
}
