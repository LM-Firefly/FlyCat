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

package com.github.yumelira.yumebox.data.gateway

import android.app.Application
import java.io.File

interface LogRecordGateway {
    val isRecording: Boolean
    val currentLogFileName: String?
    val logPrefix: String
    val logSuffix: String
    val stopWaitMillis: Long

    fun start(application: Application)

    fun stop(application: Application)

    fun getLogDir(application: Application): File
}

fun interface RuntimeLogWriter {
    fun writeLog(line: String)
}

/** No-op fallback when no real implementation is registered. */
object NoOpLogRecordGateway : LogRecordGateway {
    override val isRecording: Boolean get() = false
    override val currentLogFileName: String? get() = null
    override val logPrefix: String get() = ""
    override val logSuffix: String get() = ".log"
    override val stopWaitMillis: Long get() = 300L
    override fun start(application: Application) = Unit
    override fun stop(application: Application) = Unit
    override fun getLogDir(application: Application): File {
        return File(application.filesDir, "logs").apply { mkdirs() }
    }
}
