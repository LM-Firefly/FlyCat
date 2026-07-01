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

package com.github.yumelira.yumebox.data.controller

import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessControlController(
    private val store: NetworkSettingsStore,
    private val isRunning: () -> Boolean,
    private val resolveActiveMode: () -> ProxyMode?,
    private val commandExecutor: AccessControlCommandExecutor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var applyJob: Job? = null

    fun setAccessControlMode(mode: AccessControlMode) {
        if (store.accessControlMode.value == mode) return
        store.accessControlMode.set(mode)
        scheduleApply()
    }

    fun applyPackages(packages: Set<String>) {
        if (store.accessControlPackages.value == packages) return
        store.accessControlPackages.set(packages)
        scheduleApply()
    }

    private fun scheduleApply() {
        applyJob?.cancel()
        applyJob = scope.launch {
            delay(350L)

            if (!isRunning()) return@launch
            val activeMode = resolveActiveMode()
            val targetMode = activeMode ?: store.proxyMode.value
            if (targetMode == ProxyMode.Http) return@launch
            if (store.accessControlMode.value == AccessControlMode.ALLOW_ALL) return@launch

            try {
                commandExecutor.restartProxy(targetMode)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }
}

class AccessControlCommandExecutor(
    private val restartProxy: suspend (ProxyMode) -> Unit,
    private val beforeRestart: suspend (ProxyMode) -> Unit = {},
) {
    suspend fun restartProxy(mode: ProxyMode) {
        withContext(NonCancellable) {
            beforeRestart(mode)
            withContext(Dispatchers.IO) {
                restartProxy.invoke(mode)
            }
        }
    }
}
