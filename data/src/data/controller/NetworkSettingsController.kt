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

import com.github.yumelira.yumebox.data.model.ProxyMode
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkSettingsController(
    private val store: NetworkSettingsStore,
    isRunning: () -> Boolean,
    private val restartProxy: suspend (ProxyMode) -> Unit,
    private val beforeRestart: suspend (ProxyMode) -> Unit = {},
) {
    private val restarter =
        DebouncedProxyRestarter(
            timerName = "network_settings_restart_debounce",
            debounceMillis = RESTART_DEBOUNCE_DELAY_MS,
            isRunning = isRunning,
        )

    fun setProxyMode(mode: ProxyMode) {
        store.proxyMode.set(mode)
    }

    fun <T> setAndRestartIfNeeded(preference: Preference<T>, value: T) {
        if (preference.value == value) return
        preference.set(value)
        scheduleRestart()
    }

    suspend fun startService(mode: ProxyMode): Result<Unit> = runCatching {
        store.proxyMode.set(mode)
        beforeRestart(mode)
        withContext(Dispatchers.IO) { restartProxy(mode) }
    }

    fun requestRestartIfRunning() {
        scheduleRestart()
    }

    private fun scheduleRestart() {
        restarter.schedule {
            val targetMode = store.proxyMode.value
            beforeRestart(targetMode)
            startService(targetMode)
        }
    }

    companion object {
        private const val RESTART_DEBOUNCE_DELAY_MS = 300L
    }
}
