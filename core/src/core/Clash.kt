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

package com.github.yumelira.yumebox.core

import com.github.yumelira.yumebox.core.bridge.Bridge
import com.github.yumelira.yumebox.core.bridge.ClashException
import com.github.yumelira.yumebox.core.bridge.FetchCallback
import com.github.yumelira.yumebox.core.bridge.LogcatInterface
import com.github.yumelira.yumebox.core.bridge.TunInterface
import com.github.yumelira.yumebox.core.model.AgeKeyPair
import com.github.yumelira.yumebox.core.model.CompileRawSummary
import com.github.yumelira.yumebox.core.model.CompileRequest
import com.github.yumelira.yumebox.core.model.CompileResult
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.FetchStatus
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.NativeInspectResult
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.RootTunConfig
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.core.util.parseInetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress

object Clash {
    private val CompilerJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Native may emit an explicit `null` for fields that have a non-null default (e.g.
        // `warnings` in an error summary). Coerce such nulls to the default so the real error
        // surfaces instead of a deserialization crash.
        coerceInputValues = true
    }

    private val ConnectionJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun encode(request: CompileRequest): String =
        CompilerJson.encodeToString(CompileRequest.serializer(), request)

    fun compilePreview(request: CompileRequest): CompileResult =
        CompilerJson.decodeFromString(
            CompileResult.serializer(),
            Bridge.nativeCompilePreview(encode(request)),
        )

    fun compileAndLoadConfigSummary(
        request: CompileRequest,
        completable: CompletableDeferred<Unit>,
    ): CompileRawSummary =
        CompilerJson.decodeFromString(
            CompileRawSummary.serializer(),
            Bridge.nativeCompileAndLoadConfigSummary(completable, encode(request)),
        )

    fun compileAndInspectGroups(
        request: CompileRequest,
        profileDir: File,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup> {
        val payload =
            Bridge.nativeCompileAndInspectGroups(
                encode(request),
                profileDir.absolutePath,
                excludeNotSelectable,
            ) ?: error("native compile-and-inspect groups failed")
        val result = CompilerJson.decodeFromString(NativeInspectResult.serializer(), payload)
        check(result.success) { result.error ?: "native compile-and-inspect groups failed" }
        return YamlCodec.decode(ListSerializer(ProxyGroup.serializer()), result.payload)
    }

    fun reset() {
        Bridge.nativeReset()
    }

    fun forceGc() {
        Bridge.nativeForceGc()
    }

    fun suspendCore(suspended: Boolean) {
        Bridge.nativeSuspend(suspended)
    }

    fun queryTunnelState(): TunnelState =
        Json.decodeFromString(TunnelState.serializer(), Bridge.nativeQueryTunnelState())

    fun queryTrafficNow(): Traffic = Bridge.nativeQueryTrafficNow()

    fun queryTrafficTotal(): Traffic = Bridge.nativeQueryTrafficTotal()

    fun queryConnections(): ConnectionSnapshot =
        ConnectionJson.decodeFromString(
            ConnectionSnapshot.serializer(),
            Bridge.nativeQueryConnections(),
        )

    fun closeConnection(id: String): Boolean = Bridge.nativeCloseConnection(id)

    fun closeAllConnections() {
        Bridge.nativeCloseAllConnections()
    }

    fun notifyDnsChanged(dns: List<String>) {
        Bridge.nativeNotifyDnsChanged(dns.toSet().joinToString(separator = ","))
    }

    fun notifyTimeZoneChanged(name: String, offset: Int) {
        Bridge.nativeNotifyTimeZoneChanged(name, offset)
    }

    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketOwner:
            (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> String,
    ) {
        Bridge.nativeStartTun(
            fd,
            stack,
            gateway,
            portal,
            dns,
            object : TunInterface {
                override fun markSocket(fd: Int) {
                    markSocket(fd)
                }

                override fun querySocketOwner(
                    protocol: Int,
                    source: String,
                    target: String,
                ): String =
                    querySocketOwner(
                        protocol,
                        parseInetSocketAddress(source),
                        parseInetSocketAddress(target),
                    )
            },
        )
    }

    fun stopTun() {
        Bridge.nativeStopTun()
    }

    fun startRootTun(config: RootTunConfig): String? =
        Bridge.nativeStartRootTun(YamlCodec.encode(RootTunConfig.serializer(), config))

    fun stopRootTun() {
        Bridge.nativeStopRootTun()
    }

    fun startHttp(listenAt: String): String? = Bridge.nativeStartHttp(listenAt)

    fun stopHttp() {
        Bridge.nativeStopHttp()
    }

    fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
        val names =
            Json.decodeFromString(
                JsonArray.serializer(),
                Bridge.nativeQueryGroupNames(excludeNotSelectable),
            )

        return names.map {
            require(it.jsonPrimitive.isString)
            it.jsonPrimitive.content
        }
    }

    fun queryGroup(name: String, sort: ProxySort): ProxyGroup =
        Bridge.nativeQueryGroup(name, sort.name)?.let {
            Json.decodeFromString(ProxyGroup.serializer(), it)
        } ?: ProxyGroup(name = name, type = Proxy.Type.Unknown, proxies = emptyList(), now = "")

    fun healthCheck(name: String): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().apply { Bridge.nativeHealthCheck(this, name) }

    fun healthCheckProxy(proxyName: String): CompletableDeferred<String> =
        CompletableDeferred<String>().apply { Bridge.nativeHealthCheckProxy(this, proxyName) }

    fun healthCheckAll() {
        Bridge.nativeHealthCheckAll()
    }

    fun patchSelector(selector: String, name: String): Boolean =
        Bridge.nativePatchSelector(selector, name)

    fun fetchAndValid(
        path: File,
        url: String,
        force: Boolean,
        reportStatus: (FetchStatus) -> Unit,
    ): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().apply {
            Bridge.nativeFetchAndValid(
                object : FetchCallback {
                    override fun report(statusJson: String) {
                        reportStatus(Json.decodeFromString(FetchStatus.serializer(), statusJson))
                    }

                    override fun complete(error: String?) {
                        if (error != null) {
                            completeExceptionally(ClashException(error))
                        } else {
                            complete(Unit)
                        }
                    }
                },
                path.absolutePath,
                url,
                force,
            )
        }

    fun queryProviders(): List<Provider> {
        val providers = Json.decodeFromString(JsonArray.serializer(), Bridge.nativeQueryProviders())
        return List(providers.size) {
            Json.decodeFromJsonElement(Provider.serializer(), providers[it])
        }
    }

    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().apply {
            Bridge.nativeUpdateProvider(this, type.toString(), name)
        }

    fun queryConfiguration(): UiConfiguration =
        Json.decodeFromString(UiConfiguration.serializer(), Bridge.nativeQueryConfiguration())

    fun subscribeLogcat(): ReceiveChannel<LogMessage> =
        Channel<LogMessage>(32).apply {
            Bridge.nativeSubscribeLogcat(
                object : LogcatInterface {
                    override fun received(jsonPayload: String) {
                        trySend(Json.decodeFromString(LogMessage.serializer(), jsonPayload))
                    }
                }
            )
        }

    fun setCustomUserAgent(userAgent: String) {
        Bridge.nativeSetCustomUserAgent(userAgent)
    }

    fun setAgeSecretKey(key: String?) {
        Bridge.nativeSetAgeSecretKey(key?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun genX25519KeyPair(): AgeKeyPair? =
        Bridge.nativeGenX25519KeyPair()?.let { Json.decodeFromString(AgeKeyPair.serializer(), it) }

    fun genHybridKeyPair(): AgeKeyPair? =
        Bridge.nativeGenHybridKeyPair()?.let { Json.decodeFromString(AgeKeyPair.serializer(), it) }

    fun verifySecretKeys(secretKeys: String): Boolean =
        Bridge.nativeVerifySecretKeys(secretKeys.trim())

    fun toPublicKeys(secretKeys: String): List<String>? =
        Bridge.nativeToPublicKeys(secretKeys.trim())?.let {
            Json.decodeFromString(ListSerializer(String.serializer()), it)
        }

    fun verifyPublicKeys(publicKeys: String): Boolean =
        Bridge.nativeVerifyPublicKeys(publicKeys.trim())
}
