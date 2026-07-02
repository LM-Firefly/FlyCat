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

package com.github.yumelira.yumebox.runtime.api

import kotlinx.serialization.Serializable

@Serializable
data class RootTunStatus(
    val state: RuntimePhase = RuntimePhase.Idle,
    val running: Boolean = state.isActiveOrStopping,
    val lastError: String? = null,
    val profileUuid: String? = null,
    val profileName: String? = null,
    val runtimeReady: Boolean = false,
    val controllerReady: Boolean = false,
    val startedAt: Long? = null,
    val staticPlanFingerprint: String? = null,
    val transportFingerprint: String? = null,
    val overrideFingerprint: String? = null,
    val profileFingerprint: String? = null,
    /** Monotonic stamp assigned by the root-side publisher; 0 marks locally built statuses. */
    val seq: Long = 0L,
) {
    /** A root session counts as live while its phase is active/stopping or the runtime is ready. */
    val isSessionActive: Boolean
        get() = state.isActiveOrStopping || runtimeReady
}
