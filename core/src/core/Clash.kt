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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress

object Clash : ClashEngine {
    private val ClashJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun compilePreview(request: CompileRequest): CompileResult {
        val payload =
            Bridge.nativeCompilePreview(
                ClashJson.encodeToString(CompileRequest.serializer(), request),
            )
        return ClashJson.decodeFromString(CompileResult.serializer(), payload)
    }

    override fun compileAndLoadConfigSummary(
        request: CompileRequest,
        completable: CompletableDeferred<Unit>,
    ): CompileRawSummary {
        val payload =
            Bridge.nativeCompileAndLoadConfigSummary(
                completable,
                ClashJson.encodeToString(CompileRequest.serializer(), request),
            )
        return ClashJson.decodeFromString(CompileRawSummary.serializer(), payload)
    }

    override fun compileAndInspectGroups(
        request: CompileRequest,
        profileDir: File,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup> {
        val payload =
            Bridge.nativeCompileAndInspectGroups(
                ClashJson.encodeToString(CompileRequest.serializer(), request),
                profileDir.absolutePath,
                excludeNotSelectable,
            ) ?: error("native compile-and-inspect groups failed")
        val result = ClashJson.decodeFromString(NativeInspectResult.serializer(), payload)
        check(result.success) { result.error ?: "native compile-and-inspect groups failed" }
        return YamlCodec.decode(ListSerializer(ProxyGroup.serializer()), result.payload)
    }

    override fun compileAndInspectTunRouteExcludeAddress(request: CompileRequest): List<String> {
        val payload =
            Bridge.nativeCompileAndInspectTunRouteExcludeAddress(
                ClashJson.encodeToString(CompileRequest.serializer(), request),
            ) ?: error("native compile-and-inspect tun route-exclude-address failed")
        val result = ClashJson.decodeFromString(NativeInspectResult.serializer(), payload)
        check(result.success) {
            result.error ?: "native compile-and-inspect tun route-exclude-address failed"
        }
        return Json.decodeFromString(ListSerializer(String.serializer()), result.payload)
    }

    override fun reset() {
        Bridge.nativeReset()
    }

    override fun forceGc() {
        Bridge.nativeForceGc()
    }

    override fun suspendCore(suspended: Boolean) {
        Bridge.nativeSuspend(suspended)
    }

    override fun queryTunnelState(): TunnelState {
        val json = Bridge.nativeQueryTunnelState()
        return Json.decodeFromString(TunnelState.serializer(), json)
    }

    override fun queryTrafficNow(): Traffic {
        return Bridge.nativeQueryTrafficNow()
    }

    override fun queryTrafficTotal(): Traffic {
        return Bridge.nativeQueryTrafficTotal()
    }

    override fun queryConnections(): ConnectionSnapshot {
        val rawJson = Bridge.nativeQueryConnections()
        val element = ClashJson.parseToJsonElement(rawJson)
        val normalized = if (element is JsonObject && element["connections"] == JsonNull) {
            JsonObject(
                element.toMutableMap().apply {
                    put("connections", JsonArray(emptyList()))
                },
            )
        } else {
            element
        }
        return ClashJson.decodeFromJsonElement(
            ConnectionSnapshot.serializer(),
            normalized,
        )
    }

    override fun closeConnection(id: String): Boolean {
        return Bridge.nativeCloseConnection(id)
    }

    override fun closeAllConnections() {
        Bridge.nativeCloseAllConnections()
    }

    override fun notifyDnsChanged(dns: List<String>) {
        Bridge.nativeNotifyDnsChanged(dns.toSet().joinToString(separator = ","))
    }

    override fun notifyTimeZoneChanged(name: String, offset: Int) {
        Bridge.nativeNotifyTimeZoneChanged(name, offset)
    }

    override fun startTun(
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

    override fun stopTun() {
        Bridge.nativeStopTun()
    }

    override fun startRootTun(config: RootTunConfig): String? {
        return Bridge.nativeStartRootTun(YamlCodec.encode(RootTunConfig.serializer(), config))
    }

    override fun stopRootTun() {
        Bridge.nativeStopRootTun()
    }

    override fun startHttp(listenAt: String): String? {
        return Bridge.nativeStartHttp(listenAt)
    }

    override fun stopHttp() {
        Bridge.nativeStopHttp()
    }

    override fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
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

    override fun inspectCompiledGroups(
        yamlText: String,
        profileDir: File,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup> {
        val groupsYaml =
            Bridge.nativeInspectCompiledGroups(
                yamlText,
                profileDir.absolutePath,
                excludeNotSelectable,
            ) ?: return emptyList()
        return runCatching { YamlCodec.decode(ListSerializer(ProxyGroup.serializer()), groupsYaml) }
            .getOrElse { emptyList() }
    }

    override fun inspectCompiledGroupNames(yamlText: String, excludeNotSelectable: Boolean): List<String> {
        val namesJson = Bridge.nativeInspectCompiledGroupNames(yamlText, excludeNotSelectable)
            ?: return emptyList()
        return runCatching {
            val array = Json.decodeFromString(JsonArray.serializer(), namesJson)
            array.map {
                require(it.jsonPrimitive.isString)
                it.jsonPrimitive.content
            }
        }.getOrElse { emptyList() }
    }

    override fun queryGroup(name: String, sort: ProxySort): ProxyGroup {
        return Bridge.nativeQueryGroup(name, sort.name)?.let {
            ClashJson.decodeFromString(ProxyGroup.serializer(), it)
        } ?: ProxyGroup(name = name, type = Proxy.Type.Unknown, proxies = emptyList(), now = "")
    }

    override fun healthCheck(name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply { Bridge.nativeHealthCheck(this, name) }
    }

    override fun healthCheckProxy(proxyName: String): CompletableDeferred<String> {
        return CompletableDeferred<String>().apply {
            Bridge.nativeHealthCheckProxy(this, proxyName)
        }
    }

    override fun healthCheckAll() {
        Bridge.nativeHealthCheckAll()
    }

    override fun patchTunnelMode(mode: TunnelState.Mode): Boolean {
        val rawMode = when (mode) {
            TunnelState.Mode.Direct -> "direct"
            TunnelState.Mode.Global -> "global"
            TunnelState.Mode.Rule -> "rule"
            TunnelState.Mode.Script -> return false
        }
        return Bridge.nativePatchTunnelMode(rawMode)
    }

    override fun patchSelector(selector: String, name: String): Boolean {
        return Bridge.nativePatchSelector(selector, name)
    }

    override fun patchForceSelector(selector: String, name: String): Boolean {
        return Bridge.nativeForcePatchSelector(selector, name)
    }

    override fun fetchAndValid(
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

    override fun queryProviders(): List<Provider> {
        val providers = Json.decodeFromString(JsonArray.serializer(), Bridge.nativeQueryProviders())
        return List(providers.size) {
            Json.decodeFromJsonElement(Provider.serializer(), providers[it])
        }
    }

    override fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeUpdateProvider(this, type.toString(), name)
        }
    }

    override fun queryConfiguration(): UiConfiguration {
        return Json.decodeFromString(
            UiConfiguration.serializer(),
            Bridge.nativeQueryConfiguration(),
        )
    }

    override fun subscribeLogcat(): ReceiveChannel<LogMessage> {
        return Channel<LogMessage>(32).apply {
            Bridge.nativeSubscribeLogcat(
                object : LogcatInterface {
                    override fun received(jsonPayload: String) {
                        trySend(Json.decodeFromString(LogMessage.serializer(), jsonPayload))
                    }
                }
            )
        }
    }

    override fun setCustomUserAgent(userAgent: String) {
        Bridge.nativeSetCustomUserAgent(userAgent)
    }

    override fun setAgeSecretKey(key: String) {
        Bridge.nativeSetAgeSecretKey(key)
    }

    override fun genX25519KeyPair(): Pair<String, String>? {
        val json = Bridge.nativeGenX25519KeyPair() ?: return null
        return runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(json)
                as kotlinx.serialization.json.JsonObject
            val secretKey = obj["secretKey"]?.toString()?.removeSurrounding("\"") ?: return null
            val publicKey = obj["publicKey"]?.toString()?.removeSurrounding("\"") ?: return null
            secretKey to publicKey
        }.getOrNull()
    }

    override fun toPublicKeys(secretKeys: List<String>): List<String>? {
        val json = Bridge.nativeToPublicKeys(secretKeys.joinToString("\n")) ?: return null
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
        }.getOrNull()
    }

    fun genHybridKeyPair(): AgeKeyPair? =
        Bridge.nativeGenHybridKeyPair()?.let { Json.decodeFromString(AgeKeyPair.serializer(), it) }

    fun verifySecretKeys(secretKeys: String): Boolean =
        Bridge.nativeVerifySecretKeys(secretKeys.trim())

    override fun verifySecretKeys(secretKeys: List<String>): Boolean {
        return Bridge.nativeVerifySecretKeys(secretKeys.joinToString("\n"))
    }

    override fun verifyPublicKeys(publicKeys: List<String>): Boolean {
        return Bridge.nativeVerifyPublicKeys(publicKeys.joinToString("\n"))
    }
}
