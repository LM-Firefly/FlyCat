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

import com.github.yumelira.yumebox.core.data.Preference
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkSettingsController(
    private val store: NetworkSettingsStore,
    private val isRunning: () -> Boolean,
    private val commandExecutor: NetworkSettingsCommandExecutor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var restartJob: Job? = null

    fun setProxyMode(mode: ProxyMode) {
        store.proxyMode.set(mode)
    }

    fun <T> setAndRestartIfNeeded(preference: Preference<T>, value: T) {
        if (preference.value == value) return
        preference.set(value)
        scheduleRestart()
    }

    suspend fun startService(mode: ProxyMode): Result<Unit> = runCatching {
        commandExecutor.startService(mode).getOrThrow()
    }

    private fun scheduleRestart() {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(RESTART_DEBOUNCE_DELAY_MS)
            if (!isRunning()) return@launch
            commandExecutor.restartConfiguredService()
        }
    }

    companion object {
        private const val RESTART_DEBOUNCE_DELAY_MS = 300L
    }
}

class NetworkSettingsCommandExecutor(
    private val store: NetworkSettingsStore,
    private val restartProxy: suspend (ProxyMode) -> Unit,
    private val beforeRestart: suspend (ProxyMode) -> Unit = {},
) {
    suspend fun startService(mode: ProxyMode): Result<Unit> = runCatching {
        store.proxyMode.set(mode)
        beforeRestart(mode)
        withContext(Dispatchers.IO) {
            restartProxy(mode)
        }
    }
    suspend fun restartConfiguredService(): Result<Unit> {
        val targetMode = store.proxyMode.value
        return startService(targetMode)
    }
}
