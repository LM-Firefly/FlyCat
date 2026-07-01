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

package com.github.yumelira.yumebox.runtime.client.root

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunOperationResult
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

private const val RELOAD_DEBOUNCE_MS = 100L
private const val MAX_RERUN_COUNT = 2  // Prevent infinite reload loops

object RootTunReloadScheduler {
    enum class Reason {
        PROFILE_CHANGED,
        PROFILE_OVERRIDE_CHANGED,
        SESSION_OVERRIDE_CHANGED,
        ROOT_TUN_CONFIG_CHANGED,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var debounceJob: Job? = null
    private var reloadJob: Job? = null
    private val pendingReasons = linkedSetOf<Reason>()
    private var dirtyWhileRunning = false
    private var rerunCount = 0  // Track consecutive reruns

    fun schedule(context: Context, reason: Reason) {
        val appContext = context.appContextOrSelf
        synchronized(lock) {
            pendingReasons += reason
            if (reloadJob?.isActive == true) {
                dirtyWhileRunning = true
                return
            }
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(RELOAD_DEBOUNCE_MS)
                runReload(appContext)
            }
        }
    }

    private suspend fun runReload(context: Context) {
        val reasons =
            synchronized(lock) {
                if (reloadJob?.isActive == true) {
                    dirtyWhileRunning = true
                    return
                }
                val copied = pendingReasons.toSet()
                pendingReasons.clear()
                copied
            }
        if (reasons.isEmpty()) {
            return
        }

        val state = RootTunStatusFlow.current(context)
        if (!state.state.isActiveOrStopping && !state.runtimeReady) {
            return
        }

        synchronized(lock) {
            reloadJob = scope.launch {
                val result = syncAndReload(context, reasons)
                if (!result.success) {
                    notifyFailure(context, result.error ?: "root runtime reload failed")
                }

                val shouldRunAgain =
                    synchronized(lock) {
                        val rerun = (dirtyWhileRunning || pendingReasons.isNotEmpty()) && rerunCount < MAX_RERUN_COUNT
                        if (rerun) {
                            rerunCount++
                        } else {
                            rerunCount = 0
                        }
                        dirtyWhileRunning = false
                        rerun
                    }
                if (shouldRunAgain) {
                    delay(RELOAD_DEBOUNCE_MS)
                    runReload(context)
                }
            }
        }
    }

    private suspend fun syncAndReload(
        context: Context,
        reasons: Set<Reason>,
    ): RootTunOperationResult {
        Timber.i("RootTun reload: reasons=%s", reasons.joinToString(","))
        return retryReload(context)
    }

    private suspend fun retryReload(
        context: Context
    ): RootTunOperationResult {
        val delays = longArrayOf(0L, 250L, 500L, 1000L)
        var lastResult =
            RootTunOperationResult(success = true)
        for (index in delays.indices) {
            if (delays[index] > 0L) {
                delay(delays[index])
            }
            lastResult = RootTunController.reload(context)
            if (lastResult.success) {
                return lastResult
            }
            Timber.w("RootTun reload failed attempt=${index + 1}: ${lastResult.error}")
        }
        return lastResult
    }

    private fun notifyFailure(context: Context, error: String) {
        runCatching {
            context.sendBroadcast(
                Intent(Intents.actionRootRuntimeFailed(context.packageName))
                    .setPackage(context.packageName)
                    .putExtra("error", error)
            )
        }
    }
}
