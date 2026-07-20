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
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.RootForegroundService
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess

/**
 * Launches the root [RunMode.Tun] / [RunMode.Tproxy] daemon. A foreground notification host tracks the
 * detached `su` daemon, which remains independently driven over the REST socket
 * ([CoreProcess.reconnectRoot]). Only an explicit [CoreProcess.stopRoot] kills the core.
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

        RootForegroundService.start(appContext)
        try {
            StatusProvider.markRuntimeStarting(mode)
            val spec = SessionRuntimeSpecFactory(appContext).createRootSpec(mode)
            val config = CompiledConfigPipeline(appContext).compile(spec)
            CoreProcess(appContext).startRoot(mode.coreArg, config)

            // The fork succeeding proves nothing: a rejected config kills the core moments later (only trace
            // is core.log). Re-probe after a grace so a dead-on-arrival daemon surfaces as Failed, not idle.
            PollingTimers.awaitTick(
                PollingTimerSpecs.dynamic(
                    name = "root_core_startup_probe",
                    intervalMillis = STARTUP_PROBE_DELAY_MS,
                    initialDelayMillis = STARTUP_PROBE_DELAY_MS,
                )
            )
            if (!CoreProcess.isRootDaemonAlive()) {
                val reason =
                    CoreProcess.rootCoreLogTail(appContext) ?: "root core exited during startup"
                CoreProcess.stopRoot()
                error(reason)
            }

            StatusProvider.markRuntimeRunning(mode)
            broadcast(appContext, Intents.actionClashStarted(appContext.packageName))
            log.append("${logScope.tag} root launcher: done")
        } catch (error: Throwable) {
            runCatching { CoreProcess.stopRoot() }
            StatusProvider.markRuntimeFailed(mode, error.message)
            RootForegroundService.stop(appContext)
            log.append("${logScope.tag} root launcher: failed=${error.message}")
            throw error
        }
    }

    /** Explicitly stop the daemon and release its status slot. */
    fun stop(context: Context) {
        val appContext = context.appContextOrSelf
        val mode = CoreProcess.rootDaemonMode()
        runCatching { CoreProcess.stopRoot() }
        mode?.let { StatusProvider.markRuntimeIdle(it) }
        RootForegroundService.stop(appContext)
        broadcast(appContext, Intents.actionClashStopped(appContext.packageName))
    }

    private fun broadcast(context: Context, action: String) {
        runCatching {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        }
    }

    private const val STARTUP_PROBE_DELAY_MS = 800L
}
