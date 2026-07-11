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

package com.github.yumelira.yumebox.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Global application-level [CoroutineScope].
 *
 * Uses [Dispatchers.Main.immediate] so that coroutines launched here execute on the main
 * thread without an extra dispatch when already on the main thread. This is intentional
 * for UI-related initialization work (e.g. applying user settings, starting update checks).
 *
 * **Do not** launch long-running or blocking I/O operations on this scope — use
 * [Dispatchers.IO] or [Dispatchers.Default] explicitly for those workloads.
 */
object Global : CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    val application: Context
        get() = _application ?: throw IllegalStateException(
            "Global.init() must be called before accessing application context"
        )

    @Volatile
    private var _application: Context? = null

    fun init(application: Context) {
        if (_application != null) return
        _application = application.applicationContext ?: application
    }

    fun destroy() {
        cancel()
    }
}

interface FirstRunInitializer {
    fun initialize()
}

val Context.appContextOrSelf: Context
    get() = applicationContext ?: this

val Context.importedDir: File
    get() = filesDir.resolve("imported")
