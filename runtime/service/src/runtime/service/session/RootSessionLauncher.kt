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
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.StartupTaskCoordinator
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.RootForegroundService
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import com.github.yumelira.yumebox.runtime.service.log.RuntimeLog
import kotlinx.coroutines.withTimeout

/**
 * Launches the root [RunMode.Tun] daemon. A foreground notification host tracks
 * the detached `su` daemon, which remains independently driven over the REST socket
 * ([CoreProcess.reconnectRoot]). Only an explicit [CoreProcess.stopRoot] kills the core.
 */
object RootSessionLauncher {
    /** Compile the active profile for [mode] and launch the detached root daemon. */
    suspend fun start(context: Context, mode: RunMode) {
        require(mode == RunMode.Tun) {
            "RootSessionLauncher handles root modes only, got $mode"
        }
        val appContext = context.appContextOrSelf
        val log = RuntimeLog.writer(appContext, mode)
        log.beginSession(RuntimeLog.Type.Launcher, "root start mode=${mode.name}")

        RootForegroundService.start(appContext)
        try {
            StatusProvider.markRuntimeStarting(mode)
            StartupTaskCoordinator.awaitWarmup()
            val spec = SessionRuntimeSpecFactory(appContext).createRootSpec(mode)
            log.i(
                RuntimeLog.Type.Launcher,
                "profile=${spec.profileName} overrides=${spec.overrideSpecs.size}",
            )
            val compiled = CompiledConfigPipeline(appContext).compileDetailed(spec)
            log.i(RuntimeLog.Type.Launcher, "compiled groups=${compiled.proxyGroupNames.size}")
            CoreProcess(appContext).startRoot(mode.coreArg, compiled.finalYaml)
            awaitControllerReady(appContext)
            log.i(RuntimeLog.Type.Launcher, "controller ready")

            StatusProvider.markRuntimeRunning(mode)
            broadcast(appContext, Intents.actionRuntimeStarted(appContext.packageName))
            log.i(RuntimeLog.Type.Launcher, "success: root daemon running mode=${mode.name}")
        } catch (error: Throwable) {
            runCatching { CoreProcess.stopRoot() }
            StatusProvider.markRuntimeFailed(mode, error.message)
            RootForegroundService.stop(appContext)
            log.e(RuntimeLog.Type.Launcher, "root start failed", error)
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
        broadcast(appContext, Intents.actionRuntimeStopped(appContext.packageName))
    }

    private fun broadcast(context: Context, action: String) {
        runCatching {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        }
    }

    private suspend fun awaitControllerReady(context: Context) {
        val deadline = SystemClock.elapsedRealtime() + STARTUP_PROBE_TIMEOUT_MS
        var lastError: Throwable? = null
        while (true) {
            if (!CoreProcess.isRootDaemonAlive()) {
                val reason = CoreProcess.coreLogTail(context) ?: "root core exited during startup"
                error(reason)
            }

            val remainingMillis = deadline - SystemClock.elapsedRealtime()
            if (remainingMillis <= 0L) break
            val result =
                runCatching {
                    withTimeout(remainingMillis) {
                        CoreProcess.probeController(context)
                    }
                }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()

            val retryDelay =
                minOf(
                    STARTUP_PROBE_INTERVAL_MS,
                    deadline - SystemClock.elapsedRealtime(),
                )
            if (retryDelay <= 0L) break
            PollingTimers.awaitTick(
                PollingTimerSpecs.dynamic(
                    name = "root_core_startup_probe",
                    intervalMillis = retryDelay,
                    initialDelayMillis = retryDelay,
                )
            )
        }

        val tail = CoreProcess.coreLogTail(context)
        throw IllegalStateException(
            "root core controller unavailable: " +
                (lastError?.message ?: lastError?.let { it::class.simpleName } ?: "startup timed out") +
                (tail?.let { " ($it)" } ?: ""),
            lastError,
        )
    }

    private const val STARTUP_PROBE_INTERVAL_MS = 75L
    private const val STARTUP_PROBE_TIMEOUT_MS = 2_000L
}
