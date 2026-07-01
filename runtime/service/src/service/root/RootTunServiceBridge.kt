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

package com.github.yumelira.yumebox.runtime.service.root

import android.content.Context
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.service.root.IRootTunService
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunJson
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunOperationResult
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStartRequest
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunDecode
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunEncode
import com.github.yumelira.yumebox.runtime.service.RootTunService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RootTunServiceBridge {
    private val binding = RootTunBinding()

    private suspend fun <T> remoteCall(
        context: Context,
        onBinderFailure: (() -> T)? = null,
        block: (IRootTunService) -> T,
    ): T = binding.remoteCall(context, onBinderFailure, block)

    private suspend fun bind(context: Context): IRootTunService = binding.bind(context)

    private suspend fun disconnect() = binding.disconnect()

    suspend fun start(context: Context): RootTunOperationResult {
        val appContext = context.appContextOrSelf
        val request = RootTunStartRequest(source = "service.bridge.start")
        val result =
            withContext(Dispatchers.IO) {
                val service = bind(context)
                val resultJson = service.startRootTun(rootTunEncode(request))
                rootTunDecode<RootTunOperationResult>(resultJson)
            }
        if (result.success) {
            RootTunService.start(appContext)
        }
        return result
    }

    suspend fun stop(context: Context): RootTunOperationResult {
        val result =
            remoteCall(
                context = context,
                onBinderFailure = { RootTunOperationResult(success = true) },
            ) { service ->
                val resultJson = service.stopRootTun()
                rootTunDecode<RootTunOperationResult>(resultJson)
            }
        disconnect()
        return result
    }

    suspend fun queryStatus(context: Context): RootTunStatus =
        remoteCall(context) { service ->
            val statusJson = service.queryStatus()
            rootTunDecode<RootTunStatus>(statusJson)
        }

    suspend fun queryTrafficNow(context: Context): Long =
        remoteCall(context) { service -> service.queryTrafficNow() }

    suspend fun queryTrafficTotal(context: Context): Long =
        remoteCall(context) { service -> service.queryTrafficTotal() }

    suspend fun queryProxyGroupNames(
        context: Context,
        excludeNotSelectable: Boolean = false,
    ): List<String> =
        remoteCall(context) { service ->
            rootTunDecode<List<String>>(service.queryProxyGroupNamesJson(excludeNotSelectable))
        }

    suspend fun queryProxyGroup(
        context: Context,
        name: String,
        sort: ProxySort = ProxySort.Default,
    ): ProxyGroup? =
        remoteCall(context) { service ->
            service.queryProxyGroupJson(name, sort.name)?.let {
                rootTunDecode<ProxyGroup>(it)
            }
        }

    suspend fun queryConnections(context: Context): ConnectionSnapshot =
        remoteCall(context) { service ->
            rootTunDecode<ConnectionSnapshot>(service.queryConnectionsJson())
        }

    suspend fun closeConnection(context: Context, id: String): Boolean =
        remoteCall(context) { service -> service.closeConnection(id) }

    suspend fun closeAllConnections(context: Context) {
        remoteCall(context) { service -> service.closeAllConnections() }
    }
}
