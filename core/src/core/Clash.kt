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
import com.github.yumelira.yumebox.core.model.ConnectionOverviewSnapshot
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.FetchStatus
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.NativeInspectResult
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.RuntimeRule
import com.github.yumelira.yumebox.core.model.TunConfig
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.core.util.parseInetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress

// ─────────────────────────────────────────────────────────────────────────────
// ClashEngine (merged from ClashEngine.kt)
// ─────────────────────────────────────────────────────────────────────────────

interface ClashEngine {
    // Compilation
    fun compilePreview(request: CompileRequest): CompileResult
    fun compileAndLoadConfigSummary(request: CompileRequest, completable: CompletableDeferred<Unit>): CompileRawSummary
    fun compileAndInspectGroups(request: CompileRequest, profileDir: File, excludeNotSelectable: Boolean): List<ProxyGroup>
    fun compileAndInspectTunRouteExcludeAddress(request: CompileRequest): List<String>
    // Lifecycle
    fun reset()
    fun forceGc()
    // Tunnel state
    fun queryTunnelState(): TunnelState
    fun queryTrafficNow(): Traffic
    fun queryTrafficTotal(): Traffic
    // Connections
    fun queryConnections(): ConnectionSnapshot
    fun queryConnectionsOverview(): ConnectionOverviewSnapshot
    fun queryRules(): List<RuntimeRule>
    fun setRuleDisabled(index: Int, disabled: Boolean): Boolean
    fun closeConnection(id: String): Boolean
    fun closeAllConnections()
    // System notifications
    fun notifyDnsChanged(dns: List<String>)
    fun notifyTimeZoneChanged(name: String, offset: Int)
    // TUN management
    fun startTun(fd: Int, stack: String, gateway: String, portal: String, dns: String, markSocket: (Int) -> Boolean, querySocketOwner: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> String)
    fun stopTun()
    // Root TUN management
    fun startRootTun(config: TunConfig): String?
    fun stopRootTun()
    // HTTP proxy
    fun startHttp(listenAt: String): String?
    fun stopHttp()
    // Proxy groups
    fun queryGroupNames(excludeNotSelectable: Boolean): List<String>
    fun inspectCompiledGroups(yamlText: String, profileDir: File, excludeNotSelectable: Boolean): List<ProxyGroup>
    fun inspectCompiledGroupNames(yamlText: String, excludeNotSelectable: Boolean): List<String>
    fun queryGroup(name: String, sort: ProxySort): ProxyGroup
    // Health checks
    fun healthCheck(name: String): CompletableDeferred<Unit>
    fun healthCheckProxy(proxyName: String): CompletableDeferred<String>
    fun healthCheckAll()
    // Configuration patching
    fun patchTunnelMode(mode: TunnelState.Mode): Boolean
    fun patchSelector(selector: String, name: String): Boolean
    fun patchForceSelector(selector: String, name: String): Boolean
    // Profile management
    fun fetchAndValid(path: File, url: String, force: Boolean, reportStatus: (FetchStatus) -> Unit): CompletableDeferred<Unit>
    // Providers
    fun queryProviders(): List<Provider>
    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit>
    // Configuration
    fun queryConfiguration(): UiConfiguration
    // Logging
    fun subscribeLogcat(): ReceiveChannel<LogMessage>
    // Settings
    fun setCustomUserAgent(userAgent: String)
    fun setAgeSecretKey(key: String)
    fun genX25519KeyPair(): Pair<String, String>?
    fun genHybridKeyPair(): AgeKeyPair?
    fun genAgeKey(): AgeKeyPair?
    fun agePublicKey(secretKey: String): String?
    fun toPublicKeys(secretKeys: List<String>): List<String>?
    fun verifySecretKeys(secretKeys: List<String>): Boolean
    fun verifyPublicKeys(publicKeys: List<String>): Boolean
}

/**
 * Singleton gateway to the mihomo native engine via JNI.
 *
 * **Thread safety**: All methods in this object delegate to native code through [Bridge].
 * The native engine is responsible for its own synchronization. Kotlin callers may invoke these methods from any thread, but long-running operations (e.g. [compilePreview], [fetchAndValid]) should be called from a background dispatcher ([Dispatchers.IO] or [Dispatchers.Default]) to avoid blocking the main thread.
 */
object Clash : ClashEngine {
    private val ClashJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun compilePreview(request: CompileRequest): CompileResult {
        val payload = Bridge.nativeCompile(ClashJson.encodeToString(CompileRequest.serializer(), request))
        return ClashJson.decodeFromString(CompileResult.serializer(), payload)
    }

    override fun compileAndLoadConfigSummary(
        request: CompileRequest,
        completable: CompletableDeferred<Unit>,
    ): CompileRawSummary {
        val payload = Bridge.nativeCompileAndLoadConfigSummary(completable, ClashJson.encodeToString(CompileRequest.serializer(), request))
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
        val payload = Bridge.nativeCompileAndInspectTunRouteExcludeAddress(ClashJson.encodeToString(CompileRequest.serializer(), request)) ?: error("native compile-and-inspect tun route-exclude-address failed")
        val result = ClashJson.decodeFromString(NativeInspectResult.serializer(), payload)
        check(result.success) { result.error ?: "native compile-and-inspect tun route-exclude-address failed" }
        return Json.decodeFromString(ListSerializer(String.serializer()), result.payload)
    }

    override fun reset() {
        Bridge.nativeReset()
    }

    override fun forceGc() {
        Bridge.nativeForceGc()
    }

    override fun queryTunnelState(): TunnelState {
        val json = Bridge.nativeQueryTunnelState()
        return Json.decodeFromString(TunnelState.serializer(), json)
    }

    override fun queryTrafficNow(): Traffic = Bridge.nativeQueryTrafficNow()

    override fun queryTrafficTotal(): Traffic = Bridge.nativeQueryTrafficTotal()

    override fun queryConnections(): ConnectionSnapshot {
        val rawJson = Bridge.nativeQueryConnections()
        return try {
            // Hot path: avoid building a JsonElement tree for per-second polling.
            ClashJson.decodeFromString(ConnectionSnapshot.serializer(), rawJson)
        } catch (_: SerializationException) {
            val element = ClashJson.parseToJsonElement(rawJson)
            val normalized = if (element is JsonObject && element["connections"] == JsonNull) {
                JsonObject(element.toMutableMap().apply { put("connections", JsonArray(emptyList())) })
            } else {
                element
            }
            ClashJson.decodeFromJsonElement(
                ConnectionSnapshot.serializer(),
                normalized,
            )
        }
    }

    override fun queryConnectionsOverview(): ConnectionOverviewSnapshot {
        val rawJson = Bridge.nativeQueryConnectionsOverview()
        return ClashJson.decodeFromString(ConnectionOverviewSnapshot.serializer(), rawJson)
    }

    override fun queryRules(): List<RuntimeRule> {
        val payload = Bridge.nativeQueryRules()
        return Json.decodeFromString(ListSerializer(RuntimeRule.serializer()), payload)
    }

    override fun setRuleDisabled(index: Int, disabled: Boolean): Boolean =
        Bridge.nativeSetRuleDisabled(index, disabled)

    override fun closeConnection(id: String): Boolean = Bridge.nativeCloseConnection(id)

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

    override fun startRootTun(config: TunConfig): String? =
        Bridge.nativeStartRootTun(YamlCodec.encode(TunConfig.serializer(), config))

    override fun stopRootTun() {
        Bridge.nativeStopRootTun()
    }

    override fun startHttp(listenAt: String): String? = Bridge.nativeStartHttp(listenAt)

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

    override fun inspectCompiledGroups(yamlText: String, profileDir: File, excludeNotSelectable: Boolean): List<ProxyGroup> {
        val groupsYaml = Bridge.nativeInspectCompiledGroups(yamlText, profileDir.absolutePath, excludeNotSelectable) ?: return emptyList()
        return runCatching { YamlCodec.decode(ListSerializer(ProxyGroup.serializer()), groupsYaml) }.getOrElse { emptyList() }
    }

    override fun inspectCompiledGroupNames(yamlText: String, excludeNotSelectable: Boolean): List<String> {
        val namesJson = Bridge.nativeInspectCompiledGroupNames(yamlText, excludeNotSelectable)?: return emptyList()
        return runCatching {
            val array = Json.decodeFromString(JsonArray.serializer(), namesJson)
            array.map {
                require(it.jsonPrimitive.isString)
                it.jsonPrimitive.content
            }
        }.getOrElse { emptyList() }
    }

    override fun queryGroup(name: String, sort: ProxySort): ProxyGroup =
        Bridge.nativeQueryGroup(name, sort.name)?.let {
            ClashJson.decodeFromString(ProxyGroup.serializer(), it)
        } ?: ProxyGroup(name = name, type = Proxy.Type.Unknown, proxies = emptyList(), now = "")

    override fun healthCheck(name: String): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().apply { Bridge.nativeHealthCheck(this, name) }

    override fun healthCheckProxy(proxyName: String): CompletableDeferred<String> =
        CompletableDeferred<String>().apply { Bridge.nativeHealthCheckProxy(this, proxyName) }

    override fun healthCheckAll() {
        Bridge.nativeHealthCheckAll()
    }

    override fun patchSelector(selector: String, name: String): Boolean =
        Bridge.nativePatchSelector(selector, name)

    override fun patchForceSelector(selector: String, name: String): Boolean =
        Bridge.nativeForcePatchSelector(selector, name)

    override fun patchTunnelMode(mode: TunnelState.Mode): Boolean =
        run {
            val rawMode = when (mode) {
                TunnelState.Mode.Direct -> "direct"
                TunnelState.Mode.Global -> "global"
                TunnelState.Mode.Rule -> "rule"
                TunnelState.Mode.Script -> return false
            }
            Bridge.nativePatchTunnelMode(rawMode)
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

    override fun queryConfiguration(): UiConfiguration = UiConfiguration()

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

    override fun genHybridKeyPair(): AgeKeyPair? =
        Bridge.nativeGenHybridKeyPair()?.let { Json.decodeFromString(AgeKeyPair.serializer(), it) }

    override fun genAgeKey(): AgeKeyPair? =
        Bridge.nativeGenAgeKey()?.let { Json.decodeFromString(AgeKeyPair.serializer(), it) }

    override fun agePublicKey(secretKey: String): String? =
        Bridge.nativeAgePublicKey(secretKey)

    override fun verifySecretKeys(secretKeys: List<String>): Boolean =
        Bridge.nativeVerifySecretKeys(secretKeys.joinToString("\n"))

    override fun toPublicKeys(secretKeys: List<String>): List<String>? {
        val json = Bridge.nativeToPublicKeys(secretKeys.joinToString("\n")) ?: return null
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
        }.getOrNull()
    }

    override fun verifyPublicKeys(publicKeys: List<String>): Boolean =
        Bridge.nativeVerifyPublicKeys(publicKeys.joinToString("\n"))

    fun convertMrsToText(filePath: String): String? =
        Bridge.nativeConvertMrsToText(filePath)
}
