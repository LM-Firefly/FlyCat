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

package com.github.yumelira.yumebox.runtime.client.root

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.runtime.api.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.RootTunService
import com.github.yumelira.yumebox.runtime.service.root.IRootTunService
import com.github.yumelira.yumebox.runtime.service.root.RootTunBinding
import com.github.yumelira.yumebox.runtime.service.root.RootTunLogChunk
import com.github.yumelira.yumebox.runtime.service.root.RootTunOperationResult
import com.github.yumelira.yumebox.runtime.service.root.RootTunStartRequest
import com.github.yumelira.yumebox.runtime.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.service.root.rootTunDecode
import com.github.yumelira.yumebox.runtime.service.root.rootTunEncode
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootTunController {
    // Status-push observation is armed by RootTunBinding.shared itself, so every caller of the
    // single process-wide connection feeds RootTunStatusFlow regardless of who bound first.
    private val binding = RootTunBinding.shared

    private suspend fun <T> remoteCall(
        context: Context,
        onBinderFailure: (() -> T)? = null,
        block: (IRootTunService) -> T,
    ): T = binding.remoteCall(context, onBinderFailure, block)

    private suspend fun bind(context: Context): IRootTunService = binding.bind(context)

    private suspend fun disconnect() = binding.disconnect()

    private fun cachedBinder(context: Context): IRootTunService? = binding.cachedBinder(context)

    private fun createIntent(context: Context): Intent = binding.createIntent(context)

    /** Forwards the main-process startup trace into the root process's single log file. */
    private suspend fun flushStartupTrace(lines: List<String>) {
        if (lines.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching { binding.currentBinder?.appendStartupLog(lines.joinToString("\n")) }
        }
    }

    suspend fun start(context: Context): RootTunOperationResult {
        val appContext = context.appContextOrSelf
        val trace = mutableListOf<String>()
        val startAt = System.currentTimeMillis()

        val request = RootTunStartRequest(source = "controller.start")
        trace += "ROOT_TUN controller: prepare=${System.currentTimeMillis() - startAt}ms"
        val foregroundStartAt = System.currentTimeMillis()
        val current = RootTunStatusFlow.current(appContext)
        RootTunStatusFlow.update(
            current.copy(
                state = RuntimePhase.Starting,
                running = true,
                runtimeReady = false,
                controllerReady = true,
                startedAt = startAt,
                lastError = null,
            )
        )
        RootTunService.start(appContext)
        trace += "ROOT_TUN controller: fgService=${System.currentTimeMillis() - foregroundStartAt}ms"
        return start(appContext, request, trace, startAt)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun start(
        context: Context,
        request: RootTunStartRequest,
        trace: MutableList<String>,
        startedAt: Long,
    ): RootTunOperationResult {
        val appContext = context.appContextOrSelf
        val remoteStartAt = System.currentTimeMillis()
        try {
            val result =
                runCatching {
                        remoteCall(context) { service ->
                            val resultJson = service.startRootTun(rootTunEncode(request))
                            rootTunDecode<RootTunOperationResult>(resultJson)
                        }
                    }
                    .getOrElse { error ->
                        rollbackFailedStart(
                            context = appContext,
                            error = error.message ?: "RootTun start failed",
                        )
                        trace +=
                            "ROOT_TUN controller: total=${System.currentTimeMillis() - startedAt}ms failed=${error.message}"
                        return RootTunOperationResult(
                            success = false,
                            error = error.message ?: "RootTun start failed",
                        )
                    }
            trace += "ROOT_TUN controller: remoteStart=${System.currentTimeMillis() - remoteStartAt}ms"
            if (!result.success) {
                rollbackFailedStart(
                    context = appContext,
                    error = result.error ?: "RootTun start failed",
                )
                trace += "ROOT_TUN controller: total=${System.currentTimeMillis() - startedAt}ms"
                return result
            }

            return try {
                runCatching { RootTunStatusFlow.update(queryStatus(context)) }
                trace += "ROOT_TUN controller: total=${System.currentTimeMillis() - startedAt}ms"
                result
            } catch (error: Throwable) {
                // fault barrier: post-start status sync spans binder/root process; any failure
                // must roll the session back instead of leaving a half-started RootTun.
                val rollbackResult =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                val resultJson = bind(context).stopRootTun()
                                rootTunDecode<RootTunOperationResult>(resultJson)
                            }
                            .getOrNull()
                    }
                runCatching { RootTunService.stop(appContext) }
                runCatching { RootService.stop(createIntent(appContext)) }
                disconnect()

                val message = buildString {
                    append(error.message ?: "failed to start RootTun foreground service")
                    rollbackResult
                        ?.error
                        ?.takeIf { it.isNotBlank() }
                        ?.let { append(" | rollback: ").append(it) }
                }
                trace +=
                    "ROOT_TUN controller: total=${System.currentTimeMillis() - startedAt}ms failed=$message"
                RootTunStatusFlow.markIdle(message)
                RootTunOperationResult(success = false, error = message)
            }
        } finally {
            flushStartupTrace(trace)
        }
    }

    suspend fun reload(context: Context): RootTunOperationResult {
        if (!isRuntimeActive(context)) {
            return RootTunOperationResult(success = true)
        }

        val appContext = context.appContextOrSelf
        val trace = mutableListOf<String>()
        val currentStatus = RootTunStatusFlow.current(appContext)
        val request = RootTunStartRequest(source = "controller.reload")
        trace +=
            "ROOT_TUN controller: reload request currentTransport=${currentStatus.transportFingerprint}"

        try {
            val result =
                runCatching {
                        remoteCall(appContext) { service ->
                            trace += "ROOT_TUN controller: reload branch=service"
                            val resultJson =
                                service.reloadActiveProfile(rootTunEncode(request))
                            rootTunDecode<RootTunOperationResult>(resultJson)
                        }
                    }
                    .getOrElse { error ->
                        return RootTunOperationResult(
                            success = false,
                            error = error.message ?: "RootTun reload failed",
                        )
                    }
            if (result.success) {
                runCatching { RootTunStatusFlow.update(queryStatus(appContext)) }
            }
            return result
        } finally {
            flushStartupTrace(trace)
        }
    }

    suspend fun stop(context: Context): RootTunOperationResult {
        if (!isRuntimeActive(context)) {
            return RootTunOperationResult(success = true)
        }

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

    suspend fun requestStop(context: Context) {
        if (!isRuntimeActive(context)) return
        remoteCall(context = context, onBinderFailure = {}) { service -> service.requestStop() }
        disconnect()
    }

    suspend fun queryStatus(context: Context): RootTunStatus =
        remoteCall(context) { service ->
            val statusJson = service.queryStatus()
            rootTunDecode<RootTunStatus>(statusJson)
        }

    suspend fun queryTunnelState(context: Context): TunnelState =
        remoteCall(context) { service ->
            rootTunDecode<TunnelState>(service.queryTunnelStateJson())
        }

    suspend fun queryTrafficNow(context: Context): Long =
        remoteCall(context) { service -> service.queryTrafficNow() }

    suspend fun queryTrafficTotal(context: Context): Long =
        remoteCall(context) { service -> service.queryTrafficTotal() }

    suspend fun queryConnections(context: Context): ConnectionSnapshot =
        remoteCall(context) { service ->
            rootTunDecode<ConnectionSnapshot>(service.queryConnectionsJson())
        }

    suspend fun queryProxyGroupNames(
        context: Context,
        excludeNotSelectable: Boolean,
    ): List<String> =
        remoteCall(context) { service ->
            rootTunDecode<List<String>>(service.queryProxyGroupNamesJson(excludeNotSelectable))
        }

    suspend fun queryAllProxyGroups(
        context: Context,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup> =
        remoteCall(context) { service ->
            rootTunDecode<List<ProxyGroup>>(service.queryAllProxyGroupsJson(excludeNotSelectable))
        }

    suspend fun queryProxyGroup(context: Context, name: String, sort: ProxySort): ProxyGroup =
        remoteCall(context) { service ->
            val raw =
                service.queryProxyGroupJson(name, sort.name)
                    ?: error("proxy group not found: $name")
            rootTunDecode<ProxyGroup>(raw)
        }

    suspend fun queryConfiguration(context: Context): UiConfiguration =
        remoteCall(context) { service ->
            rootTunDecode<UiConfiguration>(service.queryConfigurationJson())
        }

    suspend fun queryProviders(context: Context): List<Provider> =
        remoteCall(context) { service ->
            rootTunDecode<List<Provider>>(service.queryProvidersJson())
        }

    suspend fun patchSelector(context: Context, group: String, name: String): Boolean =
        remoteCall(context) { service -> service.patchSelector(group, name) }

    suspend fun closeConnection(context: Context, id: String): Boolean =
        remoteCall(context) { service -> service.closeConnection(id) }

    suspend fun closeAllConnections(context: Context) {
        remoteCall(context) { service -> service.closeAllConnections() }
    }

    suspend fun healthCheck(context: Context, group: String) {
        val error = remoteCall(context) { service -> service.healthCheck(group) }
        if (!error.isNullOrBlank()) {
            error(error)
        }
    }

    suspend fun healthCheckProxy(context: Context, group: String, proxyName: String): String =
        remoteCall(context) { service -> service.healthCheckProxy(group, proxyName) }

    suspend fun updateProvider(context: Context, type: Provider.Type, name: String) {
        val error = remoteCall(context) { service -> service.updateProvider(type.name, name) }
        if (!error.isNullOrBlank()) {
            error(error)
        }
    }

    suspend fun queryRecentLogs(context: Context, sinceSeq: Long): RootTunLogChunk =
        remoteCall(context) { service ->
            val raw = service.queryRecentLogsJson(sinceSeq)
            rootTunDecode<RootTunLogChunk>(raw)
        }

    private fun isRuntimeActive(context: Context): Boolean {
        cachedBinder(context)?.let {
            return true
        }
        val status = RootTunStatusFlow.current(context)
        return status.isSessionActive
    }

    private suspend fun rollbackFailedStart(
        context: Context,
        error: String,
    ) {
        RootTunStatusFlow.markIdle(error)
        runCatching { RootTunService.stop(context) }
        runCatching { RootService.stop(createIntent(context)) }
        disconnect()
    }
}
