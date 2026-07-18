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

import android.content.Intent
import android.os.IBinder
import com.github.yumelira.yumebox.core.Global
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.contract.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunJson
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunLogChunk
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunOperationResult
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStartRequest
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.service.runtime.session.RootTunTransport
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeOperationResult
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.runtime.session.SessionRuntime
import com.github.yumelira.yumebox.runtime.service.runtime.session.SessionRuntimeSpecFactory
import com.github.yumelira.yumebox.service.root.IRootTunService
import com.github.yumelira.yumebox.service.root.IRootTunStateObserver
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class RootTunRootService : RootService() {
    private companion object {
        const val BINDER_CALL_TIMEOUT_MS = 5_000L
        const val MAX_CONCURRENT_BINDER_CALLS = 4
    }
    private lateinit var runtime: SessionRuntime
    private lateinit var stateStore: RootTunStateStore
    private lateinit var startupLogStore: RuntimeStartupLogStore
    private lateinit var runtimeSpecFactory: SessionRuntimeSpecFactory
    private lateinit var runtimeHost: RootTunRuntimeHost

    private val binderSemaphore = Semaphore(MAX_CONCURRENT_BINDER_CALLS)

    private val binder =
        object : IRootTunService.Stub() {
            override fun startRootTun(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=start source=${request.source} mode=${request.mode}"
                )
                val spec = createSpec("start", request.mode)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=start transport=${spec.transportFingerprint}"
                )
                stateStore.updateStatus(
                    stateStore
                        .snapshot()
                        .copy(
                            state = RuntimePhase.Starting,
                            running = true,
                            profileUuid = spec.profileUuid,
                            profileName = spec.profileName,
                            runtimeReady = false,
                            controllerReady = true,
                            startedAt = System.currentTimeMillis(),
                            staticPlanFingerprint = spec.staticPlanFingerprint,
                            transportFingerprint = spec.transportFingerprint,
                            overrideFingerprint = spec.effectiveFingerprint,
                            profileFingerprint = spec.profileFingerprint,
                            lastError = null,
                        )
                )
                return binderTimed { runtime.start(spec) }?.let { encodeResult(it) } ?: encodeTimeoutResult()
            }

            override fun restartRootTun(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=restart source=${request.source} mode=${request.mode}"
                )
                return binderTimed { runtime.restart(createSpec("restart", request.mode)) }?.let { encodeResult(it) } ?: encodeTimeoutResult()
            }

            override fun reloadActiveProfile(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=reload source=${request.source} mode=${request.mode}"
                )
                val spec = createSpec("reload", request.mode)
                val currentTransport = stateStore.snapshot().transportFingerprint
                return if (
                    currentTransport != null && currentTransport != spec.transportFingerprint
                ) {
                    startupLogStore.append(
                        "ROOT_TUN root-service: binder branch=reload action=restart currentTransport=$currentTransport nextTransport=${spec.transportFingerprint}"
                    )
                    encodeResult(binderTimed { runtime.restart(spec) } ?: return encodeTimeoutResult())
                } else {
                    startupLogStore.append(
                        "ROOT_TUN root-service: binder branch=reload action=reload currentTransport=$currentTransport nextTransport=${spec.transportFingerprint}"
                    )
                    encodeResult(binderTimed { runtime.reload(spec) } ?: return encodeTimeoutResult())
                }
            }

            override fun stopRootTun(): String {
                val result = binderTimed { runtime.stop() } ?: return encodeTimeoutResult()
                if (result.success) {
                    stopSelf()
                }
                return encodeResult(result)
            }

            override fun queryStatus(): String =
                RootTunJson.Default.encodeToString(
                    RootTunStatus.serializer(),
                    stateStore.snapshot(),
                )

            override fun queryTunnelStateJson(): String =
                RootTunJson.Default.encodeToString(
                    com.github.yumelira.yumebox.core.model.TunnelState.serializer(),
                    runtime.queryTunnelState(),
                )

            override fun queryTrafficNow(): Long = runtime.queryTrafficNow()

            override fun queryTrafficTotal(): Long = runtime.queryTrafficTotal()

            override fun queryConnectionsJson(): String =
                RootTunJson.Default.encodeToString(
                    com.github.yumelira.yumebox.core.model.ConnectionSnapshot.serializer(),
                    runtime.queryConnections(),
                )

            override fun queryAllProxyGroupsJson(excludeNotSelectable: Boolean): String =
                binderTimed {
                    RootTunJson.Default.encodeToString(
                        ListSerializer(com.github.yumelira.yumebox.core.model.ProxyGroup.serializer()),
                        runtime.queryAllProxyGroups(excludeNotSelectable),
                    )
                } ?: "[]"

            override fun queryProxyGroupNamesJson(excludeNotSelectable: Boolean): String =
                binderTimed {
                    RootTunJson.Default.encodeToString(
                        ListSerializer(String.serializer()),
                        runtime.queryProxyGroupNames(excludeNotSelectable),
                    )
                } ?: "[]"

            override fun queryProxyGroupJson(name: String, sort: String): String =
                RootTunJson.Default.encodeToString(
                    com.github.yumelira.yumebox.core.model.ProxyGroup.serializer(),
                    runtime.queryProxyGroup(
                        name,
                        com.github.yumelira.yumebox.core.model.ProxySort.valueOf(sort),
                    ),
                )

            override fun queryConfigurationJson(): String =
                binderTimed {
                    RootTunJson.Default.encodeToString(
                        com.github.yumelira.yumebox.core.model.UiConfiguration.serializer(),
                        runtime.queryConfiguration(),
                    )
                } ?: "{}"

            override fun queryProvidersJson(): String =
                RootTunJson.Default.encodeToString(
                    ListSerializer(com.github.yumelira.yumebox.core.model.Provider.serializer()),
                    runtime.queryProviders(),
                )

            override fun patchSelector(group: String, name: String): Boolean =
                runtime.patchSelector(group, name)

            override fun patchForceSelector(group: String, name: String): Boolean = runtime.patchForceSelector(group, name)

            override fun closeConnection(id: String): Boolean = runtime.closeConnection(id)

            override fun closeAllConnections() = runtime.closeAllConnections()

            override fun healthCheck(group: String): String? = binderTimed { runtime.healthCheck(group) }

            override fun healthCheckProxy(group: String, proxyName: String): String = binderTimed { runtime.healthCheckProxy(group, proxyName) } ?: "{\"delay\":-1}"

            override fun updateProvider(type: String, name: String): String? = binderTimed { runtime.updateProvider(type, name) }

            override fun requestStop() {
                runtime.requestStop()
                binderTimed { runtime.stop() }
                stopSelf()
            }

            override fun queryRecentLogsJson(sinceSeq: Long): String = binderTimed {
                val chunk = runtime.queryRecentLogsJson(sinceSeq)
                RootTunJson.Default.encodeToString(
                    RootTunLogChunk.serializer(),
                    RootTunLogChunk(nextSeq = chunk.nextSeq, items = chunk.items),
                )
            } ?: RootTunJson.Default.encodeToString(
                RootTunLogChunk.serializer(),
                RootTunLogChunk(nextSeq = sinceSeq, items = emptyList()),
            )

            override fun appendStartupLog(text: String) {
                startupLogStore.append(text)
            }

            override fun registerStateObserver(observer: IRootTunStateObserver) {
                runtimeHost.statePublisher.register(observer)
            }

            override fun unregisterStateObserver(observer: IRootTunStateObserver) {
                runtimeHost.statePublisher.unregister(observer)
            }
        }

    override fun onCreate() {
        super.onCreate()
        Global.init(this)
        MMKV.disableProcessModeChecker()
        MMKV.initialize(this)
        stateStore = RootTunStateStore(this)
        startupLogStore = RuntimeStartupLogStore(this, RuntimeStartupLogStore.Scope.ROOT_TUN)
        runtimeSpecFactory = SessionRuntimeSpecFactory(this)
        runtimeHost = RootTunRuntimeHost(this, RootTunStatePublisher(stateStore))
        startupLogStore.append("ROOT_TUN root-service: onCreate")
        runtime = SessionRuntime(host = runtimeHost, transport = RootTunTransport())
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean = false

    override fun onDestroy() {
        if (this::runtime.isInitialized && runtime.snapshot().phase.running) {
            runtime.requestStop("runtime destroyed")
            binderTimed { runtime.destroy() }
        }
        super.onDestroy()
    }

    private fun decodeRequest(requestJson: String): RootTunStartRequest =
        RootTunJson.Default.decodeFromString(RootTunStartRequest.serializer(), requestJson)

    private fun createSpec(action: String, mode: RunMode = RunMode.Tun): RuntimeSpec {
        startupLogStore.append("ROOT_TUN root-service: spec create begin action=$action mode=$mode")
        val spec = when (mode) {
            RunMode.Tun -> runtimeSpecFactory.createRootTunSpec()
            RunMode.Tproxy -> runtimeSpecFactory.createRootTproxySpec()
            else -> error("Root service does not support mode $mode")
        }
        startupLogStore.append(
            "ROOT_TUN root-service: spec create done action=$action mode=$mode profile=${spec.profileUuid} transport=${spec.transportFingerprint}"
        )
        return spec
    }

    private fun encodeResult(result: RuntimeOperationResult): String {
        return RootTunJson.Default.encodeToString(
            RootTunOperationResult.serializer(),
            RootTunOperationResult(success = result.success, error = result.error),
        )
    }

    private fun encodeTimeoutResult(): String = encodeResult(RuntimeOperationResult(success = false, error = "Binder call timed out after ${BINDER_CALL_TIMEOUT_MS}ms"))

    /**
     * Wraps a suspend call with [BINDER_CALL_TIMEOUT_MS] timeout and limits concurrent binder calls to [MAX_CONCURRENT_BINDER_CALLS] to prevent binder thread pool exhaustion.
     */
    private fun <T> binderTimed(block: suspend () -> T): T? = runBlocking {
        withTimeoutOrNull(BINDER_CALL_TIMEOUT_MS) {
            binderSemaphore.withPermit { block() }
        }
    }
}
