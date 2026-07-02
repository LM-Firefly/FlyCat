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
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.runtime.api.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.initializeServiceGlobal
import com.github.yumelira.yumebox.runtime.service.session.RootTunTransport
import com.github.yumelira.yumebox.runtime.service.session.RuntimeSpec
import com.github.yumelira.yumebox.runtime.service.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.session.SessionRuntime
import com.github.yumelira.yumebox.runtime.service.session.SessionRuntimeSpecFactory
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.runBlocking

class RootTunRootService : RootService() {
    private lateinit var runtime: SessionRuntime
    private lateinit var stateStore: RootTunStateStore
    private lateinit var statePublisher: RootTunStatePublisher
    private lateinit var startupLogStore: RuntimeStartupLogStore
    private lateinit var runtimeSpecFactory: SessionRuntimeSpecFactory
    private lateinit var runtimeHost: RootTunRuntimeHost

    private val binder =
        object : IRootTunService.Stub() {
            override fun startRootTun(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=start source=${request.source}"
                )
                val spec = createSpec("start")
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=start transport=${spec.transportFingerprint}"
                )
                statePublisher.update(
                    statePublisher
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
                return encodeResult(runtime.start(spec))
            }

            override fun restartRootTun(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=restart source=${request.source}"
                )
                return encodeResult(runtime.restart(createSpec("restart")))
            }

            override fun reloadActiveProfile(requestJson: String): String {
                val request = decodeRequest(requestJson)
                startupLogStore.append(
                    "ROOT_TUN root-service: binder branch=reload source=${request.source}"
                )
                val spec = createSpec("reload")
                val currentTransport = statePublisher.snapshot().transportFingerprint
                return if (
                    currentTransport != null && currentTransport != spec.transportFingerprint
                ) {
                    startupLogStore.append(
                        "ROOT_TUN root-service: binder branch=reload action=restart currentTransport=$currentTransport nextTransport=${spec.transportFingerprint}"
                    )
                    encodeResult(runtime.restart(spec))
                } else {
                    startupLogStore.append(
                        "ROOT_TUN root-service: binder branch=reload action=reload currentTransport=$currentTransport nextTransport=${spec.transportFingerprint}"
                    )
                    encodeResult(runtime.reload(spec))
                }
            }

            override fun stopRootTun(): String {
                val result = runtime.stop()
                if (result.success) {
                    stopSelf()
                }
                return encodeResult(result)
            }

            override fun queryStatus(): String = rootTunEncode(statePublisher.snapshot())

            override fun queryTunnelStateJson(): String =
                rootTunEncode(runtime.queryTunnelState())

            override fun queryTrafficNow(): Long = runtime.queryTrafficNow()

            override fun queryTrafficTotal(): Long = runtime.queryTrafficTotal()

            override fun queryConnectionsJson(): String =
                rootTunEncode(runtime.queryConnections())

            override fun queryAllProxyGroupsJson(excludeNotSelectable: Boolean): String =
                rootTunEncode(runtime.queryAllProxyGroups(excludeNotSelectable))

            override fun queryProxyGroupNamesJson(excludeNotSelectable: Boolean): String =
                rootTunEncode(runtime.queryProxyGroupNames(excludeNotSelectable))

            override fun queryProxyGroupJson(name: String, sort: String): String =
                rootTunEncode(runtime.queryProxyGroup(name, ProxySort.valueOf(sort)))

            override fun queryConfigurationJson(): String =
                rootTunEncode(runtime.queryConfiguration())

            override fun queryProvidersJson(): String =
                rootTunEncode(runtime.queryProviders())

            override fun patchSelector(group: String, name: String): Boolean =
                runtime.patchSelector(group, name)

            override fun closeConnection(id: String): Boolean = runtime.closeConnection(id)

            override fun closeAllConnections() {
                runtime.closeAllConnections()
            }

            override fun healthCheck(group: String): String? = runBlocking {
                runtime.healthCheck(group)
            }

            override fun healthCheckProxy(group: String, proxyName: String): String = runBlocking {
                runtime.healthCheckProxy(group, proxyName)
            }

            override fun updateProvider(type: String, name: String): String? = runBlocking {
                runtime.updateProvider(type, name)
            }

            override fun requestStop() {
                runtime.requestStop()
                runtime.stop()
                stopSelf()
            }

            override fun queryRecentLogsJson(sinceSeq: Long): String =
                rootTunEncode(RootTunLogChunk.from(runtime.queryRecentLogsJson(sinceSeq)))

            override fun appendStartupLog(text: String) {
                startupLogStore.append(text)
            }

            override fun registerStateObserver(observer: IRootTunStateObserver) {
                statePublisher.register(observer)
            }

            override fun unregisterStateObserver(observer: IRootTunStateObserver) {
                statePublisher.unregister(observer)
            }
        }

    override fun onCreate() {
        super.onCreate()
        initializeServiceGlobal(this)
        MMKV.initialize(this)
        stateStore = RootTunStateStore()
        statePublisher = RootTunStatePublisher(stateStore)
        startupLogStore = RuntimeStartupLogStore(this, RuntimeStartupLogStore.Scope.ROOT_TUN)
        runtimeSpecFactory = SessionRuntimeSpecFactory(this)
        runtimeHost = RootTunRuntimeHost(this, statePublisher)
        startupLogStore.append("ROOT_TUN root-service: onCreate")
        runtime = SessionRuntime(host = runtimeHost, transport = RootTunTransport())
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean = false

    override fun onDestroy() {
        if (this::runtime.isInitialized && runtime.snapshot().phase.running) {
            runtime.requestStop("runtime destroyed")
            runtime.destroy()
        }
        super.onDestroy()
    }

    private fun decodeRequest(requestJson: String): RootTunStartRequest =
        rootTunDecode(requestJson)

    private fun createSpec(action: String): RuntimeSpec {
        startupLogStore.clear()
        startupLogStore.append("ROOT_TUN root-service: spec create begin action=$action")
        val spec = runtimeSpecFactory.createRootTunSpec(log = startupLogStore::append)
        startupLogStore.append(
            "ROOT_TUN root-service: spec create done action=$action profile=${spec.profileUuid} transport=${spec.transportFingerprint}"
        )
        return spec
    }

    private fun encodeResult(
        result: com.github.yumelira.yumebox.runtime.service.session.RuntimeOperationResult
    ): String =
        rootTunEncode(RootTunOperationResult(success = result.success, error = result.error))
}
