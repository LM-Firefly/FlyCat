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

package com.github.lmfirefly.flycat.runtime.client

import android.content.Context
import com.github.lmfirefly.flycat.core.appContextOrSelf
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyServiceContracts
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.toRuntimeTargetMode
import com.github.lmfirefly.flycat.runtime.client.root.RootTunController

internal class ProxyRuntimeControl(
    context: Context,
    private val clashRequestStopAction: () -> String,
) {
    private val appContext = context.appContextOrSelf

    suspend fun start(owner: RuntimeOwner, mode: RunMode) {
        when (owner) {
            RuntimeOwner.LocalTun ->
                RuntimeContractResolver.localRuntimeService.start(
                    context = appContext,
                    mode = mode.toRuntimeTargetMode(),
                    source = ProxyServiceContracts.SOURCE_UI,
                )

            RuntimeOwner.RootTun -> {
                RuntimeContractResolver.rootAccessSupport.requireRootTunAccess(appContext)
                val result = RootTunController.start(appContext, mode)
                if (!result.success) {
                    error(result.error ?: "RootTun start failed")
                }
            }

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }

    suspend fun stop(owner: RuntimeOwner) {
        when (owner) {
            RuntimeOwner.LocalTun -> RuntimeContractResolver.localRuntimeService.stop(
                context = appContext,
                clashRequestStopAction = clashRequestStopAction(),
            )

            RuntimeOwner.RootTun -> {
                val result = RootTunController.stop(appContext)
                if (!result.success) {
                    error(result.error ?: "RootTun stop failed")
                }
            }

            RuntimeOwner.RemoteController,
            RuntimeOwner.None -> Unit
        }
    }
}
