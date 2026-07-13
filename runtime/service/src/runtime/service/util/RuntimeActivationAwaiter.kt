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

package com.github.yumelira.yumebox.runtime.service.util

import com.github.yumelira.yumebox.data.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import kotlinx.coroutines.delay
import kotlin.math.min

data class RuntimeActivationState(
    val phase: RuntimePhase,
    val error: String? = null,
)

sealed interface RuntimeActivationResult {
    val mode: ProxyMode

    data class Running(override val mode: ProxyMode) : RuntimeActivationResult

    data class Failed(
        override val mode: ProxyMode,
        val error: String?,
    ) : RuntimeActivationResult

    data class TimedOut(
        override val mode: ProxyMode,
        val lastState: RuntimeActivationState,
    ) : RuntimeActivationResult
}

class RuntimeActivationAwaiter(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun await(
        mode: ProxyMode,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
        readState: suspend () -> RuntimeActivationState,
    ): RuntimeActivationResult {
        require(timeoutMillis >= 0L)
        require(pollIntervalMillis > 0L)

        val deadline = nowMillis() + timeoutMillis
        var state = readState()
        while (true) {
            when (state.phase) {
                RuntimePhase.Running -> return RuntimeActivationResult.Running(mode)
                RuntimePhase.Failed ->
                    return RuntimeActivationResult.Failed(mode, state.error)
                RuntimePhase.Idle,
                RuntimePhase.Starting,
                RuntimePhase.Stopping -> Unit
            }

            val remaining = deadline - nowMillis()
            if (remaining <= 0L) {
                return RuntimeActivationResult.TimedOut(mode, state)
            }
            pause(min(pollIntervalMillis, remaining))
            state = readState()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_POLL_INTERVAL_MILLIS = 250L
    }
}
