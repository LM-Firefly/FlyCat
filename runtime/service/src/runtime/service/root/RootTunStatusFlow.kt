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

package com.github.yumelira.yumebox.runtime.service.root

import android.content.Context
import com.github.yumelira.yumebox.runtime.api.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow.ensureSeeded
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow.markIdle
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-global main-process view of the RootTun runtime status.
 *
 * After the root process became the single authoritative writer of the `root_tun_state` MMKV
 * (via [RootTunStatePublisher]), the main process no longer writes that store. Instead it keeps
 * this in-process flow fed by:
 *  - a one-time read-only seed from the durable root-written store ([ensureSeeded]),
 *  - the push observer (`IRootTunStateObserver`) registered by `RootTunController`, and
 *  - its own optimistic/pull updates ([update]/[markIdle]).
 *
 * Normalization mirrors [RootTunStateStore.updateStatus]/[RootTunStateStore.markIdle] exactly so the
 * observable behavior matches the previous "read shared MMKV" path.
 */
object RootTunStatusFlow {
    private val _flow = MutableStateFlow(RootTunStatus())
    val flow: StateFlow<RootTunStatus> = _flow.asStateFlow()

    @Volatile private var seeded = false

    /** One-time read-only seed from the durable root-written store (cross-process READ is safe). */
    fun ensureSeeded(context: Context) {
        if (seeded) return
        synchronized(this) {
            if (seeded) return
            _flow.value = RootTunStateStore().snapshot()
            seeded = true
        }
    }

    fun current(context: Context): RootTunStatus {
        ensureSeeded(context)
        return _flow.value
    }

    /** Optimistic/push/pull update — normalizes `running` exactly like the store did. */
    fun update(status: RootTunStatus) {
        // Root-stamped statuses (seq > 0) apply in order only; a slow pull result must not
        // resurrect an older phase over a fresher push. Locally built statuses (seq == 0,
        // or copies of the current value) always apply.
        if (status.seq != 0L && status.seq < _flow.value.seq) return
        _flow.value = status.copy(running = status.state.isActiveOrStopping)
    }

    fun markIdle(error: String? = null) =
        update(
            RootTunStatus(
                state = RuntimePhase.Idle,
                lastError = error,
                runtimeReady = false,
                controllerReady = true,
            )
        )
}
