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
 * MERCHANTABILITY OR FITNESS FOR ANY PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumelira.yumebox.runtime.service.runtime.session

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.OverrideSpec
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.LocalRuntimeSessionHelpers
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.SpecMode
import com.github.yumelira.yumebox.runtime.service.ClashService
import com.github.yumelira.yumebox.runtime.service.StatusProvider
import com.github.yumelira.yumebox.runtime.service.TunService
import com.github.yumelira.yumebox.runtime.service.runtime.config.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * runtime:service-side implementation of [LocalRuntimeSessionHelpers].
 *
 * Delegates to the existing service-layer collaborators (ServiceStore,
 * SessionRuntimeSpecFactory, CompiledConfigPipeline, RuntimeProxyGroupResolver)
 * so that ClashGateway in runtime:client does not need to import any
 * runtime:service classes directly.
 */
class LocalRuntimeSessionHelpersImpl(context: Context) : LocalRuntimeSessionHelpers {
    private val store = ServiceStore()
    private val compiledConfigPipeline = CompiledConfigPipeline(context)
    private val proxyGroupResolver = RuntimeProxyGroupResolver(compiledConfigPipeline)
    private val runtimeSpecFactory = SessionRuntimeSpecFactory(context)

    override val serviceRunning: Boolean
        get() = StatusProvider.serviceRunning

    override val activeProfileUuid: String?
        get() = store.activeProfile?.toString()

    override fun resolveOverrideSpecs(profileUuid: String): List<OverrideSpec> {
        return compiledConfigPipeline.resolveOverrideSpecs(profileUuid)
    }

    override suspend fun previewTunRouteExcludeAddress(profileUuid: String): List<String> {
        val spec = runtimeSpecFactory.createTunSpec()
        return compiledConfigPipeline.previewTunRouteExcludeAddress(spec)
    }

    override suspend fun previewGroups(
        spec: RuntimeSpec,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup> {
        return compiledConfigPipeline.previewGroups(spec, excludeNotSelectable)
    }

    override suspend fun resolvedGroups(
        spec: RuntimeSpec,
        excludeNotSelectable: Boolean,
        enrichLive: Boolean,
    ): List<ProxyGroup> {
        return proxyGroupResolver.resolvedGroups(spec, excludeNotSelectable, enrichLive)
    }

    override suspend fun resolvedGroupNames(
        spec: RuntimeSpec,
        excludeNotSelectable: Boolean,
    ): List<String> {
        return proxyGroupResolver.resolvedGroupNames(spec, excludeNotSelectable)
    }

    override fun createSpec(mode: SpecMode): RuntimeSpec? {
        return runCatching {
            when (mode) {
                SpecMode.Tun -> runtimeSpecFactory.createTunSpec()
                SpecMode.Http -> runtimeSpecFactory.createHttpSpec()
                SpecMode.RootTun -> runtimeSpecFactory.createRootTunSpec()
            }
        }.getOrNull()
    }

    override fun stopLocalServices(packageName: String) {
        val intent = com.github.yumelira.yumebox.runtime.api.service.ProxyServiceContracts
            .intentSelf(
                action = com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
                    .actionClashRequestStop(packageName),
                packageName = packageName,
            )
        runCatching { ClashService::class.java.let { /* used via context below */ } }
        // sendBroadcastSelf equivalent
        runCatching {
            val ctx = Clash::class.java // need a context; use global
            com.github.yumelira.yumebox.core.Global.application?.let { app ->
                app.sendBroadcast(intent.setPackage(app.packageName))
            }
        }
        runCatching {
            com.github.yumelira.yumebox.core.Global.application?.let { app ->
                app.stopService(Intent(app, TunService::class.java))
                app.stopService(Intent(app, ClashService::class.java))
            }
        }
    }

    override fun stopLocalHttpProxy() {
        runCatching { Clash.stopHttp() }
        runCatching { Clash.stopTun() }
        runCatching { Clash.reset() }
    }
}
