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

import android.content.Context
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import kotlinx.coroutines.runBlocking

/** HTTP-proxy mode: launch the out-of-process core with the compiled config (listeners declared in
 *  it), no TUN fd. */
class LocalHttpTransport(context: Context) : RuntimeTransport {
    private val appContext = context.appContextOrSelf
    private val startupLogStore =
        RuntimeStartupLogStore(appContext, RuntimeStartupLogStore.Scope.LOCAL_HTTP)
    private val pipeline = CompiledConfigPipeline(appContext)
    private val core = CoreProcess(appContext)

    override fun start(spec: RuntimeSpec) {
        startupLogStore.append("LOCAL_HTTP transport start: begin")
        val config = runBlocking { pipeline.compile(spec) }
        core.startHttp(config)
        startupLogStore.append("LOCAL_HTTP transport start: done")
    }

    override fun stop() {
        core.stop()
    }
}
