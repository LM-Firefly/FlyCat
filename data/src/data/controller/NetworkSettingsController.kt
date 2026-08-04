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

@file:Suppress("UnusedSymbol")

package com.github.yumeyucca.yumebox.data.controller


import com.github.yumeyucca.yumebox.core.model.RunMode
import com.github.yumeyucca.yumebox.data.store.NetworkSettingsStore
import com.github.yumeyucca.yumebox.data.store.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkSettingsController(
    private val store: NetworkSettingsStore,
    isRunning: () -> Boolean,
    private val restartProxy: suspend (RunMode) -> Unit,
    private val beforeRestart: suspend (RunMode) -> Unit = {},
) {
    private val restarter =
        DebouncedProxyRestarter(
            timerName = "network_settings_restart_debounce",
            debounceMillis = RESTART_DEBOUNCE_DELAY_MS,
            isRunning = isRunning,
        )

    fun setRunMode(mode: RunMode) {
        store.runMode.set(mode)
    }

    fun <T> setAndRestartIfNeeded(preference: Preference<T>, value: T) {
        if (preference.value == value) return
        preference.set(value)
        scheduleRestart()
    }

    suspend fun startService(mode: RunMode): Result<Unit> = runCatching {
        store.runMode.set(mode)
        beforeRestart(mode)
        withContext(Dispatchers.IO) { restartProxy(mode) }
    }

    fun requestRestartIfRunning() {
        scheduleRestart()
    }

    private fun scheduleRestart() {
        restarter.schedule {
            val targetMode = store.runMode.value
            beforeRestart(targetMode)
            startService(targetMode)
        }
    }

    companion object {
        private const val RESTART_DEBOUNCE_DELAY_MS = 300L
    }
}
