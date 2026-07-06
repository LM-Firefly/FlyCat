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

package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.core.data.RemoteControllerStoreReader
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.tencent.mmkv.MMKV

/**
 * Persists external-controller mode state: whether the app should act as a pure
 * remote controller, the list of saved backends, and which backend is active.
 *
 * Stored in its own MMKV file (`remote_controller`) using [MMKV.MULTI_PROCESS_MODE]
 * so both the UI and service processes observe the same configuration.
 */
class RemoteControllerStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), RemoteControllerStoreReader {
    /** Master switch — when on (and an active backend exists) the app runs in remote-controller mode. */
    override val controllerEnabled by boolFlow(false)

    /** All saved backends. */
    val backends by jsonListFlow(
        default = emptyList<RemoteBackend>(),
        decode = { str -> decodeFromString<List<RemoteBackend>>(str) },
        encode = { value -> encodeToString(value) },
    )

    /** Id of the currently active backend, or blank if none selected. */
    val activeBackendId by strFlow("")

    /** Convenience: resolve the active [RemoteBackend], or null if unset / missing. */
    override fun activeBackend(): RemoteBackend? {
        val id = activeBackendId.value
        if (id.isBlank()) return null
        return backends.value.firstOrNull { it.id == id }
    }

    companion object {
        private const val MMKV_ID = "remote_controller"

        private val gate by lazy { RemoteControllerStore(MMKVProvider().getMMKV(MMKV_ID)) }

        /** Live, cross-process check of whether remote-controller mode is active. */
        fun isActive(): Boolean = gate.controllerEnabled.value && gate.activeBackend() != null
    }
}
