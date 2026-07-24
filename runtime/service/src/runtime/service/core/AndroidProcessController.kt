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

package com.github.yumelira.yumebox.runtime.service.core

import android.content.Context
import com.github.yumelira.yumebox.runtime.api.CoreEndpointRef
import com.github.yumelira.yumebox.runtime.api.ProcessController
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf

/** Android binding of [ProcessController] over [CoreProcess] static ownership. */
class AndroidProcessController(context: Context) : ProcessController {
    private val appContext = context.appContextOrSelf

    override fun currentEndpoint(): CoreEndpointRef? =
        CoreProcess.current?.let { CoreEndpointRef(sock = it.sock, secret = it.secret) }

    override fun stop() {
        CoreProcess.killRunning()
    }

    override fun stopRoot() {
        CoreProcess.stopRoot()
    }

    override fun isRootDaemonAlive(): Boolean = CoreProcess.isRootDaemonAlive()

    override fun reconnectRoot(): String? = CoreProcess.reconnectRoot(appContext)
}
