/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.yumelira.yumebox.data.controller

import com.github.yumelira.yumebox.core.contract.AccessControlControllerContract
import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessControlController(
    private val store: NetworkSettingsStore,
    private val isRunning: () -> Boolean,
    private val resolveActiveMode: () -> RunMode?,
    private val commandExecutor: AccessControlCommandExecutor,
) : AccessControlControllerContract, java.io.Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var applyJob: Job? = null
    override fun close() { scope.cancel() }

    override fun setAccessControlMode(mode: AccessControlMode) {
        if (store.accessControlMode.value == mode) return
        store.accessControlMode.set(mode)
        scheduleApply()
    }

    override fun applyPackages(packages: Set<String>) {
        if (store.accessControlPackages.value == packages) return
        store.accessControlPackages.set(packages)
        scheduleApply()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleApply() {
        applyJob?.cancel()
        applyJob = scope.launch {
            PollingTimers.awaitTick(
                PollingTimerSpecs.dynamic(
                    name = "access_control_apply_packages",
                    intervalMillis = 350L,
                    initialDelayMillis = 350L,
                )
            )

            if (!isRunning()) return@launch
            val activeMode = resolveActiveMode()
            val targetMode = activeMode ?: store.runMode.value

            try {
                commandExecutor.restartProxy(targetMode)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }
}

class AccessControlCommandExecutor(private val restartProxy: suspend (RunMode) -> Unit, private val beforeRestart: suspend (RunMode) -> Unit = {}) {
    suspend fun restartProxy(mode: RunMode) {
        withContext(NonCancellable) { beforeRestart(mode); withContext(Dispatchers.IO) { restartProxy.invoke(mode) } }
    }
}
