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

package com.github.lmfirefly.flycat.data.datasource

import android.app.Application
import com.github.lmfirefly.flycat.core.contract.LogRecordGateway
import java.io.File

/** No-op fallback when no real implementation is registered. */
object NoOpLogRecordGateway : LogRecordGateway {
    override val isRecording: Boolean get() = false
    override val currentLogFileName: String? get() = null
    override val logPrefix: String get() = ""
    override val logSuffix: String get() = ".log"
    override val stopWaitMillis: Long get() = 300L
    override fun start(application: Application) = Unit
    override fun stop(application: Application) = Unit
    override fun getLogDir(application: Application): File { return File(application.filesDir, "logs").apply { mkdirs() } }
}
