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

package com.github.yumelira.yumebox.core.uds

import com.github.yumelira.yumebox.core.ClashEngine
import com.github.yumelira.yumebox.core.model.AgeKeyPair
import com.github.yumelira.yumebox.core.model.CompileRawSummary
import com.github.yumelira.yumebox.core.model.CompileRequest
import com.github.yumelira.yumebox.core.model.CompileResult
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.FetchStatus
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.NativeInspectResult
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.RootTunConfig
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.YamlCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.File
import java.net.InetSocketAddress

/**
 * [ClashEngine] implementation that communicates with the Go mihomo core
 * via a Unix Domain Socket instead of JNI.
 *
 * This replaces the [Clash] singleton for the UDS transport mode.
 * The Go binary runs as a separate process; all RPC calls go through
 * the length-prefixed JSON protocol over the UDS.
 */
class UdsClashEngine(
    private val connection: UdsConnection,
    /** Optional accessor for the event subscriber. Provided by [UdsProcessManager]. */
    private val eventSubscriberRef: (() -> UdsEventSubscriber?)? = null,
) : ClashEngine {

    private val udsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─── Compilation ──────────────────────────────────────────────────────────

    /**
     * Sends a pre-compiled raw config JSON to the Go server for loading.
     *
     * In UDS mode, the Kotlin side handles the Rust override compilation
     * (via [Bridge.nativeCompilePreview]), then calls this method to send
     * the compiled result to the Go server which applies it via `hub.ApplyConfig`.
     *
     * @param configRawJson The compiled raw config JSON from the Rust engine.
     * @return The fingerprint of the loaded config.
     */
    fun loadCompiledRaw(configRawJson: String): String {
        val resp = blockingCall("config.loadCompiledRaw", buildJsonObject {
            put("configRawJson", configRawJson)
        })
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        val success = obj["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
        if (!success) {
            val error = obj["error"]?.jsonPrimitive?.content ?: "load failed"
            throw UdsException(-1, error)
        }
        return obj["fingerprint"]?.jsonPrimitive?.content ?: ""
    }

    override fun compilePreview(request: CompileRequest): CompileResult {
        throw UnsupportedOperationException(
            "compilePreview is handled by the Rust override engine on the Kotlin side; " +
                "use loadCompiledRaw after Kotlin-side Rust compilation"
        )
    }

    override fun compileAndLoadConfigSummary(
        request: CompileRequest,
        completable: CompletableDeferred<Unit>,
    ): CompileRawSummary {
        throw UnsupportedOperationException(
            "compileAndLoadConfigSummary is handled by the Rust override engine on the Kotlin side"
        )
    }

    override fun compileAndInspectGroups(
        request: CompileRequest,
        profileDir: File,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup> {
        throw UnsupportedOperationException(
            "compileAndInspectGroups is handled by the Rust override engine on the Kotlin side"
        )
    }

    override fun compileAndInspectTunRouteExcludeAddress(request: CompileRequest): List<String> {
        throw UnsupportedOperationException(
            "compileAndInspectTunRouteExcludeAddress is handled by the Rust override engine on the Kotlin side"
        )
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun reset() {
        blockingCall("core.reset")
    }

    override fun forceGc() {
        blockingCall("core.forceGc")
    }

    override fun suspendCore(suspended: Boolean) {
        blockingCall("core.suspend", buildJsonObject { put("suspended", suspended) })
    }

    // ─── Tunnel state ─────────────────────────────────────────────────────────

    override fun queryTunnelState(): TunnelState {
        val resp = blockingCall("tunnel.state")
        return udsJson.decodeFromString(TunnelState.serializer(), resp)
    }

    override fun queryTrafficNow(): Traffic {
        val resp = blockingCall("tunnel.trafficNow")
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        val up = obj["upload"]!!.jsonPrimitive.content.toLong()
        val down = obj["download"]!!.jsonPrimitive.content.toLong()
        // Pack into the same Long format as the JNI version: (up << 32 | down)
        return (up shl 32) or (down and 0xFFFFFFFFL)
    }

    override fun queryTrafficTotal(): Traffic {
        val resp = blockingCall("tunnel.trafficTotal")
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        val up = obj["upload"]!!.jsonPrimitive.content.toLong()
        val down = obj["download"]!!.jsonPrimitive.content.toLong()
        return (up shl 32) or (down and 0xFFFFFFFFL)
    }

    // ─── Connections ──────────────────────────────────────────────────────────

    override fun queryConnections(): ConnectionSnapshot {
        val rawJson = blockingCall("tunnel.connections")
        val element = udsJson.parseToJsonElement(rawJson)
        val normalized = if (element is JsonObject && element["connections"] == JsonNull) {
            JsonObject(element.toMutableMap().apply { put("connections", JsonArray(emptyList())) })
        } else {
            element
        }
        return udsJson.decodeFromString(ConnectionSnapshot.serializer(), normalized.toString())
    }

    override fun closeConnection(id: String): Boolean {
        val resp = blockingCall("tunnel.closeConnection", buildJsonObject { put("id", id) })
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        return obj["ok"]!!.jsonPrimitive.content.toBoolean()
    }

    override fun closeAllConnections() {
        blockingCall("tunnel.closeAll")
    }

    // ─── System notifications ─────────────────────────────────────────────────

    override fun notifyDnsChanged(dns: List<String>) {
        blockingCall("system.notifyDnsChanged", buildJsonObject {
            put("dnsList", dns.joinToString(","))
        })
    }

    override fun notifyTimeZoneChanged(name: String, offset: Int) {
        blockingCall("system.notifyTimeZoneChanged", buildJsonObject {
            put("name", name)
            put("offset", offset)
        })
    }

    // ─── TUN management ──────────────────────────────────────────────────────

    override fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketOwner: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> String,
    ) {
        // TUN fd passing via SCM_RIGHTS — Phase 3.
        throw UnsupportedOperationException("tun.start via UDS requires SCM_RIGHTS (Phase 3)")
    }

    override fun stopTun() {
        blockingCall("tun.stop")
    }

    // ─── Root TUN management ──────────────────────────────────────────────────

    override fun startRootTun(config: RootTunConfig): String? {
        throw UnsupportedOperationException("Root TUN uses AIDL, not UDS")
    }

    override fun stopRootTun() {
        throw UnsupportedOperationException("Root TUN uses AIDL, not UDS")
    }

    // ─── HTTP proxy ───────────────────────────────────────────────────────────

    override fun startHttp(listenAt: String): String? {
        val resp = blockingCall("http.start", buildJsonObject { put("listenAt", listenAt) })
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        return obj["listen"]?.jsonPrimitive?.content
    }

    override fun stopHttp() {
        blockingCall("http.stop")
    }

    // ─── Proxy groups ─────────────────────────────────────────────────────────

    override fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
        val resp = blockingCall("proxy.queryGroupNames", buildJsonObject {
            put("excludeNotSelectable", excludeNotSelectable)
        })
        return udsJson.decodeFromString(ListSerializer(String.serializer()), resp)
    }

    override fun inspectCompiledGroups(yamlText: String, profileDir: File, excludeNotSelectable: Boolean): List<ProxyGroup> {
        val resp = blockingCall("config.inspectCompiledGroups", buildJsonObject {
            put("configRawJson", yamlText)
            put("profileDir", profileDir.absolutePath)
            put("excludeNotSelectable", excludeNotSelectable)
        })
        val result = udsJson.decodeFromString(NativeInspectResult.serializer(), resp)
        check(result.success) { result.error ?: "inspect groups failed" }
        return YamlCodec.decode(ListSerializer(ProxyGroup.serializer()), result.payload)
    }

    override fun inspectCompiledGroupNames(yamlText: String, excludeNotSelectable: Boolean): List<String> {
        val resp = blockingCall("config.inspectCompiledGroups", buildJsonObject {
            put("configRawJson", yamlText)
            put("profileDir", "")
            put("excludeNotSelectable", excludeNotSelectable)
        })
        val result = udsJson.decodeFromString(NativeInspectResult.serializer(), resp)
        check(result.success) { result.error ?: "inspect group names failed" }
        return YamlCodec.decode(ListSerializer(ProxyGroup.serializer()), result.payload).map { it.name }
    }

    override fun queryGroup(name: String, sort: ProxySort): ProxyGroup {
        val sortStr = when (sort) {
            ProxySort.Title -> "Title"
            ProxySort.Delay -> "Delay"
            else -> "Default"
        }
        val resp = blockingCall("proxy.queryGroup", buildJsonObject {
            put("name", name)
            put("sort", sortStr)
        })
        return udsJson.decodeFromString(ProxyGroup.serializer(), resp)
    }

    // ─── Health checks ────────────────────────────────────────────────────────

    override fun healthCheck(name: String): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                blockingCall("proxy.healthCheck", buildJsonObject { put("name", name) })
                deferred.complete(Unit)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    override fun healthCheckProxy(proxyName: String): CompletableDeferred<String> {
        val deferred = CompletableDeferred<String>()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val resp = blockingCall("proxy.healthCheckProxy", buildJsonObject { put("proxyName", proxyName) })
                deferred.complete(resp)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    override fun healthCheckAll() {
        blockingCall("proxy.healthCheckAll")
    }

    // ─── Configuration patching ───────────────────────────────────────────────

    override fun patchTunnelMode(mode: TunnelState.Mode): Boolean {
        val resp = blockingCall("tunnel.patchMode", buildJsonObject { put("mode", mode.name) })
        val obj = udsJson.parseToJsonElement(resp) as? JsonObject
        return obj?.get("ok")?.jsonPrimitive?.content?.toBoolean() ?: false
    }

    override fun patchSelector(selector: String, name: String): Boolean {
        val resp = blockingCall("proxy.patchSelector", buildJsonObject {
            put("selector", selector)
            put("name", name)
        })
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        return obj["ok"]!!.jsonPrimitive.content.toBoolean()
    }

    override fun patchForceSelector(selector: String, name: String): Boolean {
        val resp = blockingCall("proxy.patchForceSelector", buildJsonObject {
            put("selector", selector)
            put("name", name)
        })
        val obj = udsJson.parseToJsonElement(resp) as JsonObject
        return obj["ok"]!!.jsonPrimitive.content.toBoolean()
    }

    // ─── Profile management ───────────────────────────────────────────────────

    override fun fetchAndValid(
        path: File,
        url: String,
        force: Boolean,
        reportStatus: (FetchStatus) -> Unit,
    ): CompletableDeferred<Unit> {
        // fetchAndValid is async and uses callbacks — handled by the Kotlin-side fetcher.
        throw UnsupportedOperationException("fetchAndValid is handled by the Kotlin-side fetcher")
    }

    // ─── Providers ────────────────────────────────────────────────────────────

    override fun queryProviders(): List<Provider> {
        val resp = blockingCall("proxy.queryProviders")
        return udsJson.decodeFromString(ListSerializer(Provider.serializer()), resp)
    }

    override fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                blockingCall("proxy.updateProvider", buildJsonObject {
                    put("type", type.name)
                    put("name", name)
                })
                deferred.complete(Unit)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    override fun queryConfiguration(): UiConfiguration {
        val resp = blockingCall("system.queryConfiguration")
        return udsJson.decodeFromString(UiConfiguration.serializer(), resp)
    }

    // ─── Logging ──────────────────────────────────────────────────────────────

    override fun subscribeLogcat(): ReceiveChannel<LogMessage> {
        // If we have an event subscriber, use its log channel directly.
        val subscriber = eventSubscriberRef?.invoke()
        if (subscriber != null) {
            return subscriber.logEvents.let { srcChannel ->
                val out = Channel<LogMessage>(Channel.BUFFERED)
                GlobalScope.launch(Dispatchers.IO) {
                    for (evt in srcChannel) {
                        val level = when (evt.level.lowercase()) {
                            "debug" -> LogMessage.Level.Debug
                            "info" -> LogMessage.Level.Info
                            "warning" -> LogMessage.Level.Warning
                            "error" -> LogMessage.Level.Error
                            "silent" -> LogMessage.Level.Silent
                            else -> LogMessage.Level.Unknown
                        }
                        out.trySend(LogMessage(level = level, message = evt.message, time = java.util.Date(evt.time)))
                    }
                }
                out
            }
        }

        // Fallback: register an ad-hoc event handler on the connection.
        val channel = Channel<LogMessage>(Channel.BUFFERED)
        UdsConnection.addEventHandler { event ->
            if (event.event == "log") {
                try {
                    val logEvent = udsJson.decodeFromString(UdsLogEvent.serializer(), event.data.toString())
                    val level = when (logEvent.level.lowercase()) {
                        "debug" -> LogMessage.Level.Debug
                        "info" -> LogMessage.Level.Info
                        "warning" -> LogMessage.Level.Warning
                        "error" -> LogMessage.Level.Error
                        "silent" -> LogMessage.Level.Silent
                        else -> LogMessage.Level.Unknown
                    }
                    channel.trySend(
                        LogMessage(level = level, message = logEvent.message, time = java.util.Date(logEvent.time))
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to decode log event")
                }
            }
        }

        // Request log subscription from the server.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                blockingCall("log.subscribe", buildJsonObject { put("level", "info") })
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to subscribe to logs")
            }
        }

        return channel
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    override fun setCustomUserAgent(userAgent: String) {
        blockingCall("core.setUserAgent", buildJsonObject { put("userAgent", userAgent) })
    }

    override fun setAgeSecretKey(key: String) {
        blockingCall("core.setAgeSecretKey", buildJsonObject { put("key", key) })
    }

    override fun genX25519KeyPair(): Pair<String, String>? {
        // These crypto operations are not yet available via UDS.
        Timber.tag(TAG).w("genX25519KeyPair not yet available via UDS")
        return null
    }

    override fun genHybridKeyPair(): AgeKeyPair? {
        Timber.tag(TAG).w("genHybridKeyPair not yet available via UDS")
        return null
    }

    override fun toPublicKeys(secretKeys: List<String>): List<String>? {
        Timber.tag(TAG).w("toPublicKeys not yet available via UDS")
        return null
    }

    override fun verifySecretKeys(secretKeys: List<String>): Boolean {
        Timber.tag(TAG).w("verifySecretKeys not yet available via UDS")
        return false
    }

    override fun verifyPublicKeys(publicKeys: List<String>): Boolean {
        Timber.tag(TAG).w("verifyPublicKeys not yet available via UDS")
        return false
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun blockingCall(method: String, params: JsonObject? = null): String {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val resp = connection.call(method, params)
            if (resp.error != null) {
                throw UdsException(resp.error.code, resp.error.message)
            }
            resp.result?.toString() ?: "{}"
        }
    }

    companion object {
        private const val TAG = "UdsClashEngine"
    }
}
