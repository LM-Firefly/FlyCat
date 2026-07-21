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

package com.github.yumelira.yumebox.runtime.service.manager

import com.github.yumelira.yumebox.core.bridge.UnixSocketFactory
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProviderList
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.RuntimeRule
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.core.util.encodeTrafficValue
import com.github.yumelira.yumebox.data.model.RemoteBackend
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.ILogObserver
import com.github.yumelira.yumebox.runtime.api.ILogSubscription
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import java.time.Instant
import java.util.Date
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * [IClashManager] over a mihomo REST controller (https://wiki.metacubex.one/api/). Serves BOTH:
 *  - a **remote** backend over TCP (External Controller mode), keyed off [backendProvider];
 *  - the **local** out-of-process core over its `external-controller-unix` socket, when [local]
 *    is set (OkHttp with a [UnixSocketFactory]). This is the single data plane for every local run
 *    mode (VPN / root) — the mode only differs in how the core process was launched, not how it is
 *    queried.
 *
 * Blocking interface methods bridge to suspend Ktor calls via [runBlocking] on the IO dispatcher.
 */
class HttpClashManager(
    private val local: Local? = null,
    private val backendProvider: () -> RemoteBackend? = { null },
) : IClashManager {

    /**
     * The local core's UNIX controller: a fixed socket path plus a secret read fresh each request
     * (the path is stable per install; the bearer secret is minted per core start).
     */
    class Local(val socketPath: String, val secret: () -> String)

    private val json = Json { ignoreUnknownKeys = true }

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logSink = AtomicReference<LogSink?>(null)
    @Volatile private var logJob: Job? = null

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            // Without timeouts a runBlocking REST call against an unreachable backend blocks its
            // IO thread until the OS TCP timeout (tens of seconds). The traffic poller and proxy
            // group sync loop fire these continuously, so a dead backend saturates Dispatchers.IO
            // and starves the local start path — the home start button appears frozen. Bound every
            // call so a lost backend fails fast instead of hanging. socketTimeout intentionally
            // covers the streaming /traffic read (one JSON line per second) without killing it.
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
            // Local mode: route every connection over the core's UNIX controller socket regardless
            // of the (dummy) request host. Remote mode uses OkHttp's default TCP socket factory.
            local?.let { target ->
                engine { config { socketFactory(UnixSocketFactory(target.socketPath)) } }
            }
        }
    }

    /** Bearer secret for the active target (local core token, or remote backend secret). */
    private fun activeSecret(): String =
        local?.secret?.invoke() ?: (backendProvider()?.secret.orEmpty())

    private fun requireBackend(): RemoteBackend =
        backendProvider() ?: error("No active remote controller backend")

    /** Builds an absolute URL to [pathSegments] under the active endpoint's base URL. */
    private fun buildUrl(vararg pathSegments: String, query: Map<String, String> = emptyMap()): String {
        val base = if (local != null) LOCAL_BASE_URL else requireBackend().normalizedBaseUrl
        return URLBuilder(base).apply {
            appendPathSegments(*pathSegments)
            query.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()
    }

    private suspend fun request(
        method: HttpMethod,
        vararg pathSegments: String,
        query: Map<String, String> = emptyMap(),
        body: Any? = null,
    ): HttpResponse {
        val url = buildUrl(*pathSegments, query = query)
        return when (method) {
            HttpMethod.Get ->
                client.get(url) { applyAuth() }
            HttpMethod.Delete ->
                client.delete(url) { applyAuth() }
            HttpMethod.Put ->
                client.put(url) {
                    applyAuth()
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
            HttpMethod.Patch ->
                client.patch(url) {
                    applyAuth()
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
            else -> error("Unsupported HTTP method: $method")
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth() {
        val secret = activeSecret()
        if (secret.isNotBlank()) {
            header(HttpHeaders.Authorization, "Bearer $secret")
        }
    }

    // ---- Tunnel / traffic ------------------------------------------------

    override fun queryTunnelState(): TunnelState =
        runBlocking(Dispatchers.IO) {
            val raw = request(HttpMethod.Get, "configs").bodyAsText()
            val configs = json.decodeFromString<RawConfigs>(raw)
            TunnelState(configs.mode)
        }

    override fun queryTrafficNow(): Long =
        runBlocking(Dispatchers.IO) {
            val sample = readTrafficSample() ?: return@runBlocking 0L
            (encodeTrafficValue(sample.up) shl 32) or encodeTrafficValue(sample.down)
        }

    override fun queryTrafficTotal(): Long =
        runBlocking(Dispatchers.IO) {
            val sample = readTrafficSample() ?: return@runBlocking 0L
            (encodeTrafficValue(sample.upTotal) shl 32) or encodeTrafficValue(sample.downTotal)
        }

    /**
     * Reads the FIRST line of the streaming `/traffic` endpoint (one JSON line per second) and
     * closes the stream. `up`/`down` are realtime bytes/second; `upTotal`/`downTotal` cumulative.
     */
    private suspend fun readTrafficSample(): RawTraffic? = runCatching {
        client.prepareGet(buildUrl("traffic")) { applyAuth() }.execute { response ->
            val line = response.bodyAsChannel().readUTF8Line()
            line?.let { json.decodeFromString<RawTraffic>(it) }
        }
    }.getOrNull()

    override fun queryConnections(): ConnectionSnapshot =
        runBlocking(Dispatchers.IO) { fetchConnections() }

    private suspend fun fetchConnections(): ConnectionSnapshot =
        client.prepareGet(buildUrl("connections", query = CONNECTIONS_QUERY)) {
            applyAuth()
        }.execute { response ->
            val line = response.bodyAsChannel().readUTF8Line()
                ?: error("connections stream ended before the first snapshot")
            json.decodeFromString<ConnectionSnapshot>(line)
        }

    // ---- Local-profile-only (irrelevant in pure-remote mode) -------------

    override fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> = emptyList()

    // ---- Proxy groups ----------------------------------------------------

    override fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        runBlocking(Dispatchers.IO) {
            val nodes = fetchProxies()
            val groups = orderGroups(fetchGroups(), nodes)
            groups
                .filter { !excludeNotSelectable || it.type in Proxy.Type.manuallySelectable }
                .map { buildGroup(it, nodes, ProxySort.Default) }
        }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        runBlocking(Dispatchers.IO) {
            val nodes = fetchProxies()
            orderGroups(fetchGroups(), nodes)
                .filter { !excludeNotSelectable || it.type in Proxy.Type.manuallySelectable }
                .map { it.name }
        }

    /** Stable group order: index in GLOBAL.all (config order); groups absent from it sort last by name. */
    private fun orderGroups(groups: List<RawProxy>, nodes: Map<String, RawProxy>): List<RawProxy> {
        val canonical = nodes["GLOBAL"]?.all ?: emptyList()
        val indexOf = canonical.withIndex().associate { (i, name) -> name to i }
        return groups.sortedWith(
            compareBy({ indexOf[it.name] ?: Int.MAX_VALUE }, { it.name })
        )
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        runBlocking(Dispatchers.IO) {
            val nodes = fetchProxies()
            val group = nodes[name]
                ?: return@runBlocking ProxyGroup(
                    name = name,
                    type = Proxy.Type.Unknown,
                    proxies = emptyList(),
                    now = "",
                )
            buildGroup(group, nodes, proxySort)
        }

    private suspend fun fetchProxies(): Map<String, RawProxy> {
        val raw = request(HttpMethod.Get, "proxies").bodyAsText()
        return json.decodeFromString<RawProxiesResponse>(raw).proxies
    }

    private suspend fun fetchGroups(): List<RawProxy> {
        val raw = request(HttpMethod.Get, "group").bodyAsText()
        return json.decodeFromString<RawGroupResponse>(raw).proxies
    }

    // ---- Selection / connections mutation --------------------------------

    @Suppress("TooGenericExceptionCaught")
    override fun patchSelector(group: String, name: String): Boolean =
        runBlocking(Dispatchers.IO) {
            try {
                val response = request(
                    HttpMethod.Put,
                    "proxies",
                    group,
                    body = SelectBody(name),
                )
                response.status.isSuccess()
            } catch (_: Throwable) { // fault barrier: remote REST call must degrade to "not selected"
                false
            }
        }

    @Suppress("TooGenericExceptionCaught")
    override fun closeConnection(id: String): Boolean =
        runBlocking(Dispatchers.IO) {
            try {
                request(HttpMethod.Delete, "connections", id).status.isSuccess()
            } catch (error: Throwable) { // fault barrier: remote REST call must degrade to "not closed"
                false
            }
        }

    override fun closeAllConnections() {
        runBlocking(Dispatchers.IO) {
            runCatching { request(HttpMethod.Delete, "connections") }
        }
    }

    // ---- Health checks ---------------------------------------------------

    override suspend fun healthCheck(group: String) {
        // Triggers a group delay test on the backend; the result body is ignored.
        runCatching {
            request(
                HttpMethod.Get,
                "group",
                group,
                "delay",
                query = delayQuery,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun healthCheckProxy(group: String, proxyName: String): Int =
        try {
            val response = request(
                HttpMethod.Get,
                "proxies",
                proxyName,
                "delay",
                query = delayQuery,
            )
            if (response.status.isSuccess()) {
                json.decodeFromString<RawDelayResult>(response.bodyAsText()).delay
            } else {
                -1
            }
        } catch (error: Throwable) { // fault barrier: remote REST delay test must degrade to timeout (-1)
            -1
        }

    // ---- Providers -------------------------------------------------------

    /**
     * Lists proxy + rule providers from the mihomo REST controller
     * (`GET /providers/proxies` + `GET /providers/rules`, see website/api/openapi.json).
     * Built-in Compatible providers are omitted — they are not user external resources.
     */
    override fun queryProviders(): ProviderList =
        runBlocking(Dispatchers.IO) {
            val proxies = runCatching {
                fetchProviders(category = "proxies", type = Provider.Type.Proxy)
            }
            val rules = runCatching {
                fetchProviders(category = "rules", type = Provider.Type.Rule)
            }
            if (proxies.isFailure && rules.isFailure) {
                throw requireNotNull(proxies.exceptionOrNull()).also { proxyError ->
                    rules.exceptionOrNull()?.let(proxyError::addSuppressed)
                }
            }
            ProviderList(proxies.getOrDefault(emptyList()) + rules.getOrDefault(emptyList()))
        }

    private suspend fun fetchProviders(
        category: String,
        type: Provider.Type,
    ): List<Provider> {
        val raw = request(HttpMethod.Get, "providers", category).bodyAsText()
        val response = json.decodeFromString<RawProvidersResponse>(raw)
        return response.providers.mapNotNull { (key, entry) ->
            val vehicle = parseVehicleType(entry.vehicleType) ?: return@mapNotNull null
            // Compatible is mihomo's built-in "default" bucket, not a user external resource.
            if (vehicle == Provider.VehicleType.Compatible) return@mapNotNull null
            val name = entry.name.ifBlank { key }.ifBlank { return@mapNotNull null }
            Provider(
                name = name,
                type = type,
                vehicleType = vehicle,
                updatedAt = parseUpdatedAtMillis(entry.updatedAt),
                // REST payload has no path; file-upload UI stays gated on a non-blank path elsewhere.
                path = "",
            )
        }
    }

    private fun parseVehicleType(raw: String): Provider.VehicleType? =
        when (raw.trim().lowercase()) {
            "http" -> Provider.VehicleType.HTTP
            "file" -> Provider.VehicleType.File
            "inline" -> Provider.VehicleType.Inline
            "compatible" -> Provider.VehicleType.Compatible
            else -> null
        }

    private fun parseUpdatedAtMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        val category = if (type == Provider.Type.Proxy) "proxies" else "rules"
        request(HttpMethod.Put, "providers", category, name)
    }

    // ---- Configuration ---------------------------------------------------

    override fun queryConfiguration(): UiConfiguration = UiConfiguration()

    // ---- Rules -----------------------------------------------------------

    override fun queryRules(): List<RuntimeRule> = runBlocking(Dispatchers.IO) { fetchRules() }

    override suspend fun setRuleDisabled(
        rule: RuntimeRule,
        disabled: Boolean,
    ): List<RuntimeRule> {
        val current = fetchRules().firstOrNull { it.index == rule.index }
            ?: error("rule ${rule.index} no longer exists")
        check(current.hasSameIdentity(rule)) { "rule ${rule.index} changed before update" }

        request(
            HttpMethod.Patch,
            "rules",
            "disable",
            body = mapOf(rule.index.toString() to disabled),
        )

        val updatedRules = fetchRules()
        val updated = updatedRules.firstOrNull { it.index == rule.index }
            ?: error("rule ${rule.index} disappeared after update")
        check(updated.hasSameIdentity(rule)) { "rule ${rule.index} changed during update" }
        check(updated.disabled == disabled) { "rule ${rule.index} state was not applied" }
        return updatedRules
    }

    private suspend fun fetchRules(): List<RuntimeRule> {
        val raw = request(HttpMethod.Get, "rules").bodyAsText()
        return json.decodeFromString<RawRulesResponse>(raw).rules.map { it.toRuntimeRule() }
    }

    private fun RuntimeRule.hasSameIdentity(other: RuntimeRule): Boolean =
        index == other.index && type == other.type && payload == other.payload && proxy == other.proxy

    // ---- Lifecycle (no-ops in pure-remote mode) --------------------------

    override fun requestStop() {
        // No-op: we don't own the remote core, so there is nothing to stop.
    }

    @Synchronized
    override fun subscribeLogs(observer: ILogObserver): ILogSubscription {
        val sink = LogSink(observer = observer)
        logSink.set(sink)
        logJob?.cancel()
        logJob =
            logScope.launch {
                while (isActive && logSink.get() === sink) {
                    try {
                        streamLogsOnce(sink)
                        if (logSink.get() === sink) {
                            sink.observer.onError(IOException("log stream ended"))
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        if (logSink.get() === sink) sink.observer.onError(error)
                        Timber.w(error, "log stream failed; retrying")
                    }
                    delay(LOG_STREAM_RETRY_MS)
                }
            }
        return ILogSubscription {
            synchronized(this@HttpClashManager) {
                if (logSink.compareAndSet(sink, null)) {
                    logJob?.cancel()
                    logJob = null
                }
            }
        }
    }

    /**
     * Opens `GET /logs` as a line-delimited stream and forwards each JSON line to the active
     * [ILogObserver]. Returns when the stream ends or the observer is cleared.
     */
    private suspend fun streamLogsOnce(sink: LogSink) {
        client.prepareGet(buildUrl("logs", query = LOG_QUERY)) {
            applyAuth()
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.execute { response ->
            if (logSink.get() !== sink) return@execute
            sink.observer.onConnected()
            val channel = response.bodyAsChannel()
            while (logSink.get() === sink && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                val entry =
                    runCatching { json.decodeFromString<RawLogLine>(line) }.getOrNull()
                        ?: continue
                val message =
                    LogMessage(
                        level = parseLogLevel(entry.type),
                        message = entry.payload,
                        time = Date(),
                    )
                sink.observer.newItem(message)
            }
        }
    }

    private fun parseLogLevel(raw: String): LogMessage.Level =
        when (raw.trim().lowercase()) {
            "debug" -> LogMessage.Level.Debug
            "info" -> LogMessage.Level.Info
            "warning", "warn" -> LogMessage.Level.Warning
            "error" -> LogMessage.Level.Error
            "silent" -> LogMessage.Level.Silent
            else -> LogMessage.Level.Unknown
        }

    private fun RawRule.toRuntimeRule(): RuntimeRule =
        RuntimeRule(
            index = index,
            type = type,
            payload = payload,
            proxy = proxy,
            size = size,
            disabled = extra?.disabled ?: false,
            hitCount = extra?.hitCount ?: 0L,
            missCount = extra?.missCount ?: 0L,
        )

    // ---- Adapters --------------------------------------------------------

    private fun RawProxy.toProxy(): Proxy =
        Proxy(
            name = name,
            title = name,
            subtitle = "",
            type = type,
            delay = history.lastOrNull()?.delay ?: 0,
        )

    private fun buildGroup(group: RawProxy, nodes: Map<String, RawProxy>, sort: ProxySort): ProxyGroup {
        val members = group.all.map { memberName ->
            nodes[memberName]?.toProxy()
                ?: Proxy(
                    name = memberName,
                    title = memberName,
                    subtitle = "",
                    type = Proxy.Type.Unknown,
                    delay = 0,
                )
        }
        val sorted = when (sort) {
            ProxySort.Default -> members
            ProxySort.Title -> members.sortedBy { it.name }
            ProxySort.Delay ->
                members.sortedWith(
                    compareBy(
                        { if (it.delay > 0) 0 else 1 },
                        { if (it.delay > 0) it.delay else Int.MAX_VALUE },
                    )
                )
        }
        return ProxyGroup(
            name = group.name,
            type = group.type,
            proxies = sorted,
            now = group.now,
            icon = group.icon,
            hidden = group.hidden,
        )
    }

    // ---- DTOs ------------------------------------------------------------

    @Serializable
    private data class RawProvidersResponse(val providers: Map<String, RawProvider> = emptyMap())

    /**
     * Shared shape of entries under GET /providers/proxies and /providers/rules
     * (website/api/openapi.json). Extra fields (proxies, subscriptionInfo, behavior, …)
     * are ignored via [json]'s ignoreUnknownKeys.
     */
    @Serializable
    private data class RawProvider(
        val name: String = "",
        val vehicleType: String = "",
        val updatedAt: String? = null,
    )

    @Serializable
    private data class RawProxiesResponse(val proxies: Map<String, RawProxy> = emptyMap())

    @Serializable
    private data class RawGroupResponse(val proxies: List<RawProxy> = emptyList())

    @Serializable
    private data class RawProxy(
        val name: String,
        val type: String,
        val now: String = "",
        val all: List<String> = emptyList(),
        val history: List<RawDelay> = emptyList(),
        val hidden: Boolean = false,
        val icon: String? = null,
        val udp: Boolean = false,
    )

    @Serializable
    private data class RawDelay(val delay: Int = 0)

    @Serializable
    private data class RawDelayResult(val delay: Int = 0)

    @Serializable
    private data class RawConfigs(val mode: TunnelState.Mode = TunnelState.Mode.Rule)

    @Serializable
    private data class RawTraffic(
        val up: Long = 0,
        val down: Long = 0,
        val upTotal: Long = 0,
        val downTotal: Long = 0,
    )

    @Serializable
    private data class SelectBody(val name: String)

    @Serializable
    private data class RawRulesResponse(val rules: List<RawRule> = emptyList())

    @Serializable
    private data class RawRule(
        val index: Int = 0,
        val type: String = "",
        val payload: String = "",
        val proxy: String = "",
        val size: Int = -1,
        val extra: RawRuleExtra? = null,
    )

    @Serializable
    private data class RawRuleExtra(
        val disabled: Boolean = false,
        val hitCount: Long = 0L,
        val missCount: Long = 0L,
    )

    @Serializable
    private data class RawLogLine(
        val type: String = "info",
        val payload: String = "",
    )

    private data class LogSink(
        val observer: ILogObserver,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val LOG_STREAM_RETRY_MS = 1_500L

        // Dummy authority for local mode: the UnixSocketFactory ignores host/port and always
        // connects to the core's controller socket, so only the path/scheme matter here.
        const val LOCAL_BASE_URL = "http://localhost"

        val delayQuery = mapOf(
            "url" to "https://www.gstatic.com/generate_204",
            "timeout" to "5000",
        )
        val LOG_QUERY = mapOf("level" to "debug")
        val CONNECTIONS_QUERY = mapOf("interval" to "1000")
    }
}
