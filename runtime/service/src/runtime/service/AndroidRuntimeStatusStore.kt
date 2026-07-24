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

package com.github.yumelira.yumebox.runtime.service

import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.enumByNameOrNull
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.RuntimeStatusStore

/** Android [RuntimeStatusStore] backed by [StatusProvider]. */
object AndroidRuntimeStatusStore : RuntimeStatusStore {
    override fun isRuntimeActive(runModeName: String): Boolean {
        val mode = enumByNameOrNull<RunMode>(runModeName) ?: return false
        return StatusProvider.isRuntimeActive(mode)
    }

    override fun queryRuntimePhase(runModeName: String): RuntimePhase {
        val mode = enumByNameOrNull<RunMode>(runModeName) ?: return RuntimePhase.Idle
        return StatusProvider.queryRuntimePhase(mode)
    }

    override fun queryRuntimeStartedAt(runModeName: String): Long? {
        val mode = enumByNameOrNull<RunMode>(runModeName) ?: return null
        return StatusProvider.queryRuntimeStartedAt(mode)
    }

    override fun queryRuntimeLastError(runModeName: String): String? {
        val mode = enumByNameOrNull<RunMode>(runModeName) ?: return null
        return StatusProvider.queryRuntimeLastError(mode)
    }

    override fun markRuntimeIdle(runModeName: String) {
        val mode = enumByNameOrNull<RunMode>(runModeName) ?: return
        StatusProvider.markRuntimeIdle(mode)
    }

    override fun reconcilePersistedRuntimeState() {
        StatusProvider.reconcilePersistedRuntimeState()
    }

    override fun isLocalRuntimeServiceAlive(runModeName: String): Boolean {
        val mode = enumByNameOrNull<RunMode>(runModeName) ?: return false
        return StatusProvider.isLocalRuntimeServiceAlive(mode)
    }

    override fun clearLegacyStateFiles() {
        StatusProvider.clearLegacyStateFiles()
    }
}
