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

package com.github.yumelira.yumebox.runtime.client

import android.content.Context
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toRuntimeTargetMode
import com.github.yumelira.yumebox.runtime.client.root.RootTunController

internal class ProxyRuntimeControl(
    context: Context,
    private val clashRequestStopAction: () -> String,
) {
    private val appContext = context.appContextOrSelf

    suspend fun start(owner: RuntimeOwner, mode: ProxyMode) {
        when (owner) {
            RuntimeOwner.RootTun -> {
                RuntimeContractResolver.rootAccessSupport.requireRootTunAccess(appContext)
                val result = RootTunController.start(appContext)
                if (!result.success) {
                    error(result.error ?: "RootTun start failed")
                }
            }

            RuntimeOwner.LocalTun,
            RuntimeOwner.LocalHttp ->
                RuntimeContractResolver.localRuntimeService.start(
                    context = appContext,
                    mode = mode.toRuntimeTargetMode(),
                    source = ProxyServiceContracts.SOURCE_UI,
                )

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }

    suspend fun stop(owner: RuntimeOwner) {
        when (owner) {
            RuntimeOwner.RootTun -> {
                val result = RootTunController.stop(appContext)
                if (!result.success) {
                    error(result.error ?: "RootTun stop failed")
                }
            }

            RuntimeOwner.LocalTun,
            RuntimeOwner.LocalHttp -> RuntimeContractResolver.localRuntimeService.stop(
                context = appContext,
                clashRequestStopAction = clashRequestStopAction(),
            )

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }
}
