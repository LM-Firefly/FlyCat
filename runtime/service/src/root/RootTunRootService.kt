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

package com.github.lmfirefly.flycat.runtime.service.root

import android.content.Intent
import android.os.IBinder
import com.github.lmfirefly.flycat.core.Global
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.root.RootTunJson
import com.github.lmfirefly.flycat.runtime.api.root.RootTunLogChunk
import com.github.lmfirefly.flycat.runtime.api.root.RootTunOperationResult
import com.github.lmfirefly.flycat.runtime.api.root.RootTunStartRequest
import com.github.lmfirefly.flycat.runtime.api.root.RootTunStatus
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.session.SessionRuntime
import com.github.lmfirefly.flycat.runtime.service.session.spec.RuntimeOperationResult
import com.github.lmfirefly.flycat.runtime.service.session.spec.SessionRuntimeSpecFactory
import com.github.lmfirefly.flycat.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.github.lmfirefly.flycat.runtime.service.session.transport.RootTunTransport
import com.github.lmfirefly.flycat.service.root.IRootTunService
import com.github.lmfirefly.flycat.service.root.IRootTunStateObserver
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
                val startResult = binderTimed { runtime.start(spec) }
                if (startResult?.success == true && spec.runMode == RunMode.Ebpf) {
                    try { startEbpfBridge(spec) } catch (error: Throwable) {
                        // Rollback: bridge failed but mihomo is running — stop everything
                        startupLogStore.append("ROOT_TUN root-service: eBPF bridge failed, rolling back: ${error.message}")
                        runCatching { binderTimed { runtime.stop() } }
                        stateStore.updateStatus(
                            stateStore.snapshot().copy(
                                state = RuntimePhase.Failed,
                                running = false,
                                runtimeReady = false,
                                lastError = "eBPF bridge failed: ${error.message}",
                            )
                        )
                        return encodeResult(RuntimeOperationResult(success = false, error = "eBPF bridge failed: ${error.message}"))
                    }
                }
                return startResult?.let { encodeResult(it) } ?: encodeTimeoutResult()
            }

            override fun restartRootTun(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=restart source=${request.source} mode=${request.mode}"
                )
                val spec = createSpec("restart", request.mode)
                stopEbpfBridge()
                stateStore.updateStatus(
                    stateStore.snapshot().copy(
                        state = RuntimePhase.Starting,
                        runtimeReady = false,
                        lastError = null,
                    )
                )
                val restartResult = binderTimed { runtime.restart(spec) }
                if (restartResult?.success == true && spec.runMode == RunMode.Ebpf) {
                    try {
                        startEbpfBridge(spec)
                    } catch (error: Throwable) {
                        startupLogStore.append("ROOT_TUN root-service: restart eBPF bridge failed: ${error.message}")
                        runCatching { binderTimed { runtime.stop() } }
                        stateStore.updateStatus(
                            stateStore.snapshot().copy(
                                state = RuntimePhase.Failed,
                                running = false,
                                runtimeReady = false,
                                lastError = "eBPF bridge failed: ${error.message}",
                            )
                        )
                        return encodeResult(RuntimeOperationResult(success = false, error = "eBPF bridge failed: ${error.message}"))
                    }
                }
                return restartResult?.let { encodeResult(it) } ?: encodeTimeoutResult()
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
                    stopEbpfBridge()
                    val restartResult = binderTimed { runtime.restart(spec) }
                    if (restartResult?.success == true && spec.runMode == RunMode.Ebpf) {
                        startEbpfBridge(spec)
                    }
                    encodeResult(restartResult ?: return encodeTimeoutResult())
                } else {
                    startupLogStore.append(
                        "ROOT_TUN root-service: binder branch=reload action=reload currentTransport=$currentTransport nextTransport=${spec.transportFingerprint}"
                    )
                    encodeResult(binderTimed { runtime.reload(spec) } ?: return encodeTimeoutResult())
                }
            }

            override fun stopRootTun(): String {
                stopEbpfBridge()
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
                    com.github.lmfirefly.flycat.core.model.tunnel.TunnelState.serializer(),
                    runtime.queryTunnelState(),
                )

            override fun queryTrafficNow(): Long = runtime.queryTrafficNow()

            override fun queryTrafficTotal(): Long = runtime.queryTrafficTotal()

            override fun queryConnectionsJson(): String =
                RootTunJson.Default.encodeToString(
                    com.github.lmfirefly.flycat.core.model.ConnectionSnapshot.serializer(),
                    runtime.queryConnections(),
                )

            override fun queryConnectionsOverviewJson(): String =
                RootTunJson.Default.encodeToString(
                    com.github.lmfirefly.flycat.core.model.ConnectionOverviewSnapshot.serializer(),
                    runtime.queryConnectionsOverview(),
                )

            override fun queryAllProxyGroupsJson(excludeNotSelectable: Boolean): String =
                binderTimed {
                    RootTunJson.Default.encodeToString(
                        ListSerializer(com.github.lmfirefly.flycat.core.model.proxy.ProxyGroup.serializer()),
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
                    com.github.lmfirefly.flycat.core.model.proxy.ProxyGroup.serializer(),
                    runtime.queryProxyGroup(
                        name,
                        com.github.lmfirefly.flycat.core.model.proxy.ProxySort.valueOf(sort),
                    ),
                )

            override fun queryProvidersJson(): String =
                RootTunJson.Default.encodeToString(
                    ListSerializer(com.github.lmfirefly.flycat.core.model.Provider.serializer()),
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
                stopEbpfBridge()
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
        stopEbpfBridge()
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
            RunMode.Ebpf -> runtimeSpecFactory.createEbpfSpec()
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

    private fun startEbpfBridge(spec: RuntimeSpec) {
        runCatching {
            val cgroupPath = EbpfCgroupSupport.rootCgroupPath()
                ?: error("eBPF requires a cgroup v2 mount")
            val mihomoPid = RootTunTransport.findMihomoPid()
                ?: error("mihomo root PID is unavailable for eBPF bridge")
            val uidPolicy = runtimeSpecFactory.resolveEbpfUidPolicy()
            val settings = ServiceStore()
            val bypassCidrs = resolveEbpfBypassCidrs(settings)
            EbpfBridgeProcess.start(
                this,
                mihomoPid,
                cgroupPath,
                uidPolicy.mode,
                uidPolicy.uids,
                dnsHijacking = settings.dnsHijacking,
                enableIpv6 = settings.allowIpv6,
                bypassCidrs = bypassCidrs,
            )
            check(EbpfBridgeProcess.isAlive()) {
                "eBPF bridge exited during startup: ${EbpfBridgeProcess.diagnosticLog(this)}"
            }
            startupLogStore.append("ROOT_TUN root-service: eBPF bridge ready cgroupMount=$cgroupPath")
        }.onFailure { error ->
            startupLogStore.append("ROOT_TUN root-service: eBPF bridge start failed: ${error.message}")
            throw error
        }
    }

    private fun resolveEbpfBypassCidrs(settings: ServiceStore): List<String> {
        val explicit = settings.rootTunRouteExcludeAddress
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter { settings.allowIpv6 || ':' !in it }
        if (explicit.isNotEmpty() || !settings.bypassPrivateNetwork) return explicit
        return listOf(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.168.0.0/16",
            "224.0.0.0/4", "240.0.0.0/4", "255.255.255.255/32",
            "fc00::/7", "fe80::/10", "ff00::/8",
        )
    }

    private fun stopEbpfBridge() {
        runCatching {
            EbpfBridgeProcess.stop(this)
            startupLogStore.append("ROOT_TUN root-service: eBPF bridge stopped")
        }
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
