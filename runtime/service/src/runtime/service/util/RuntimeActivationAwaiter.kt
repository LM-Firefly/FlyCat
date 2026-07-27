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

@file:Suppress("ConvertLongToDuration")

package com.github.yumelira.yumebox.runtime.service.util

import android.os.SystemClock
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

data class RuntimeActivationState(
    val phase: RuntimePhase,
    val error: String? = null,
)

sealed interface RuntimeActivationResult {
    val mode: RunMode

    data class Running(override val mode: RunMode) : RuntimeActivationResult

    data class Failed(
        override val mode: RunMode,
        val error: String?,
    ) : RuntimeActivationResult

    data class TimedOut(
        override val mode: RunMode,
        val lastState: RuntimeActivationState,
    ) : RuntimeActivationResult
}

class RuntimeActivationAwaiter(
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val delayMillis: suspend (Long) -> Unit = { delay(it.milliseconds) },
) {
    suspend fun await(
        mode: RunMode,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
        queryState: suspend () -> RuntimeActivationState,
    ): RuntimeActivationResult {
        require(timeoutMillis >= 0L) { "timeoutMillis must not be negative" }
        require(pollIntervalMillis > 0L) { "pollIntervalMillis must be positive" }

        val startedAt = elapsedRealtimeMillis()
        while (true) {
            val state = queryState()
            when (state.phase) {
                RuntimePhase.Running -> return RuntimeActivationResult.Running(mode)
                RuntimePhase.Failed -> return RuntimeActivationResult.Failed(mode, state.error)
                RuntimePhase.Idle,
                RuntimePhase.Starting,
                RuntimePhase.Stopping -> Unit
            }

            val elapsed = (elapsedRealtimeMillis() - startedAt).coerceAtLeast(0L)
            val remaining = timeoutMillis - elapsed
            if (remaining <= 0L) {
                return RuntimeActivationResult.TimedOut(mode, state)
            }
            delayMillis(min(pollIntervalMillis, remaining))
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_POLL_INTERVAL_MILLIS = 250L
    }
}
