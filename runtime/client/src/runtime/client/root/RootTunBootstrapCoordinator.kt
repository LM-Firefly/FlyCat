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
import com.github.yumelira.yumebox.runtime.api.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.RootTunService
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Attach-and-probe loop for a root session that outlives the app process (extracted from
 * [com.github.yumelira.yumebox.runtime.client.ProxyFacade]): keeps the RootTun foreground
 * service attached while a session is live and probes the root binder until the runtime
 * reports Running. Observed statuses are handed back via [onRootStatus], which returns true
 * once the runtime is running and the loop may stop.
 */
internal class RootTunBootstrapCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val onRootStatus: suspend (RootTunStatus) -> Boolean,
) {
    private companion object {
        const val BOOTSTRAP_ATTEMPTS = 20
        const val BOOTSTRAP_DELAY_MS = 300L
    }

    private val appContext = context.appContextOrSelf
    private var job: Job? = null

    fun schedule() {
        if (job?.isActive == true) return
        job = scope.launch {
            repeat(BOOTSTRAP_ATTEMPTS) { attempt ->
                val persistedStatus = RootTunStatusFlow.current(appContext)
                if (!persistedStatus.isSessionActive) {
                    return@launch
                }

                val status =
                    runCatching {
                            ensureServiceAttached(persistedStatus)
                            RootTunController.queryStatus(appContext)
                        }
                        .getOrNull()

                if (status != null) {
                    RootTunStatusFlow.update(status)
                    if (onRootStatus(status)) {
                        return@launch
                    }
                }

                if (attempt < BOOTSTRAP_ATTEMPTS - 1) {
                    delay(BOOTSTRAP_DELAY_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun reconcileSafely() {
        runCatching {
                val persistedStatus = RootTunStatusFlow.current(appContext)
                if (!persistedStatus.isSessionActive) {
                    return@runCatching
                }
                ensureServiceAttached(persistedStatus)
                val status = RootTunController.queryStatus(appContext)
                RootTunStatusFlow.update(status)
                onRootStatus(status)
            }
            .onFailure { error -> Timber.d(error, "RootTun bootstrap reconcile skipped") }
    }

    fun ensureServiceAttached(
        status: RootTunStatus = RootTunStatusFlow.current(appContext)
    ) {
        if (!status.isSessionActive) {
            return
        }
        runCatching { RootTunService.start(appContext) }
            .onFailure { error -> Timber.d(error, "Attach RootTun foreground service skipped") }
    }

    /** Mirrors the old facade guard: probing is only worthwhile while a session looks live. */
    fun shouldBootstrap(
        status: RootTunStatus = RootTunStatusFlow.current(appContext)
    ): Boolean = status.isSessionActive
}
