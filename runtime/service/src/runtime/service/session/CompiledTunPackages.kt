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

package com.github.yumelira.yumebox.runtime.service.session

/**
 * Service-process holder for the compiled config's `tun.include-package` / `tun.exclude-package`
 * lists.
 *
 * Single writer: [CompiledConfigPipeline] publishes via [update] after a successful native
 * compile-and-load, and [SessionRuntime] clears via [clear] on core teardown so a stale profile's
 * lists never leak into the next session. Single reader: [VpnTunTransport] reads the lists at
 * `VpnService.Builder.establish()` time to apply per-app routing.
 *
 * This state is deliberately kept in-process (service process only) and NOT in the multi-process
 * ServiceStore MMKV: the compiled config is loaded and consumed in the same process, and the lists
 * must always mirror the currently loaded config, not a persisted snapshot.
 */
object CompiledTunPackages {
    @Volatile
    var includePackages: List<String> = emptyList()
        private set

    @Volatile
    var excludePackages: List<String> = emptyList()
        private set

    /** Publishes the compiled lists; returns true when either list changed. */
    @Synchronized
    fun update(include: List<String>, exclude: List<String>): Boolean {
        val changed = include != includePackages || exclude != excludePackages
        includePackages = include
        excludePackages = exclude
        return changed
    }

    @Synchronized
    fun clear() {
        includePackages = emptyList()
        excludePackages = emptyList()
    }
}
