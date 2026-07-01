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
import java.io.File

object Global {
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

    @VisibleForTesting
    fun reset() {
        _application = null
    }

    val isInitialized: Boolean
        get() = _application != null
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
private annotation class VisibleForTesting

interface FirstRunInitializer {
    fun initialize()
}

val Context.appContextOrSelf: Context
    get() = applicationContext ?: this

val Context.importedDir: File
    get() = filesDir.resolve("imported")
