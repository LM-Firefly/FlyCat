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

package com.github.lmfirefly.flycat.core.util.coroutine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session-scoped gate for auto start behavior.
 *
 * Manual pause blocks auto start only within current app process lifetime. Foreground auto actions
 * also run at most once per process lifetime. After process restart, state resets naturally.
 */
object AutoStartSessionGate {
    private val paused = AtomicBoolean(false)
    private val autoInFlight = AtomicBoolean(false)
    private val autoHandled = AtomicBoolean(false)

    fun markManualPaused() {
        paused.set(true)
    }

    fun clearManualPaused() {
        paused.set(false)
    }

    fun shouldSkipAutoStart(): Boolean = paused.get()

    fun tryBeginAutoActions(): Boolean {
        if (autoHandled.get()) return false
        return autoInFlight.compareAndSet(false, true)
    }

    fun finishAutoActions(markHandled: Boolean) {
        autoInFlight.set(false)
        if (markHandled) autoHandled.set(true)
    }
}
