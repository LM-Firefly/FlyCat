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

@file:Suppress("ConvertLongToDuration")

package com.github.yumelira.yumebox.runtime.service.controller

import com.github.yumelira.yumebox.core.bridge.UnixSocketFactory
import com.github.yumelira.yumebox.core.model.*
import com.github.yumelira.yumebox.core.util.encodeTrafficValue
import com.github.yumelira.yumebox.data.model.RemoteBackend
import com.github.yumelira.yumebox.runtime.api.CoreApi
import com.github.yumelira.yumebox.runtime.api.CoreAsyncQueries
import com.github.yumelira.yumebox.runtime.api.LogObserver
import com.github.yumelira.yumebox.runtime.api.LogSubscription
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/** [CoreApi] over mihomo REST: local unix socket and/or remote TCP backend. */
class CoreController(
    private val local: Local? = null,
    private val backendProvider: () -> RemoteBackend? = { null },
) : CoreApi, CoreAsyncQueries {

    /** Local controller endpoint: fixed socket path, secret read per request. */
    class Local(val socketPath: String, val secret: () -> String)

    private val json = Json { ignoreUnknownKeys = true }

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logSink = AtomicReference<LogSink?>(null)
    @Volatile private var logJob: Job? = null
    @Volatile private var trafficSampleCache: TimedTrafficSample? = null

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            // Bound requests so a dead backend fails fast instead of saturating Dispatchers.IO.
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
            // Local mode routes all traffic over the fixed UNIX controller socket.
            local?.let { target ->
                engine { config { socketFactory(UnixSocketFactory(target.socketPath)) } }
            }
        }
    }

    /** Active target secret (local token or remote backend). */
    private fun activeSecret(): String =
        local?.secret?.invoke() ?: (backendProvider()?.secret.orEmpty())

    private fun ensureEndpointReady() {
        // Local controller: socket path is fixed; secret may be blank when the profile does not
        // set one (Bearer is optional). Do NOT treat empty secret as "core not ready" — that
        // false-failed root/VPN startup probes right after the core published clash.sock.
        if (local != null) return
        if (backendProvider() == null) {
            error("No active remote controller backend")
        }
    }

    private fun requireBackend(): RemoteBackend =
        backendProvider() ?: error("No active remote controller backend")

    /** Absolute URL under the active endpoint base. */
    private fun buildUrl(
        vararg pathSegments: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val base = if (local != null) LOCAL_BASE_URL else requireBackend().normalizedBaseUrl
        return URLBuilder(base)
            .apply {
                appendPathSegments(*pathSegments)
                query.forEach { (key, value) -> parameters.append(key, value) }
            }
            .buildString()
    }

    private suspend fun request(
        method: HttpMethod,
        vararg pathSegments: String,
        query: Map<String, String> = emptyMap(),
        body: Any? = null,
    ): HttpResponse {
        ensureEndpointReady()
        val url = buildUrl(*pathSegments, query = query)
        return when (method) {
            HttpMethod.Get -> client.get(url) { applyAuth() }

            HttpMethod.Delete -> client.delete(url) { applyAuth() }

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

    /** Encode free-form JSON body without relying on erased Map serializers. */
    private inline fun <reified T> jsonBody(value: T): TextContent =
        TextContent(json.encodeToString(value), ContentType.Application.Json)

    // ---- Tunnel / traffic ------------------------------------------------
    // Async implementations are the source of truth; CoreApi sync methods are legacy bridges.

    override suspend fun queryTunnelStateAsync(): TunnelState {
        val raw = request(HttpMethod.Get, "configs").bodyAsText()
        val configs = json.decodeFromString<RawConfigs>(raw)
        return TunnelState(configs.mode)
    }

    override fun queryTunnelState(): TunnelState =
        runBlocking(Dispatchers.IO) { queryTunnelStateAsync() }

    override suspend fun queryTrafficNowAsync(): Long {
        val sample = readTrafficSample() ?: return 0L
        return (encodeTrafficValue(sample.up) shl 32) or encodeTrafficValue(sample.down)
    }

    override fun queryTrafficNow(): Long = runBlocking(Dispatchers.IO) { queryTrafficNowAsync() }

    override suspend fun queryTrafficTotalAsync(): Long {
        val sample = readTrafficSample() ?: return 0L
        return (encodeTrafficValue(sample.upTotal) shl 32) or encodeTrafficValue(sample.downTotal)
    }

    override fun queryTrafficTotal(): Long =
        runBlocking(Dispatchers.IO) { queryTrafficTotalAsync() }

    /** Reuse one stream sample for the adjacent now/total reads performed by the UI. */
    private suspend fun readTrafficSample(): RawTraffic? {
        val now = System.nanoTime()
        trafficSampleCache?.takeIf { now - it.capturedAtNanos <= TRAFFIC_SAMPLE_CACHE_NS }?.let {
            return it.sample
        }
        return runCatching {
                client
                    .prepareGet(buildUrl("traffic")) { applyAuth() }
                    .execute { response ->
                        val line = response.bodyAsChannel().readLine()
                        line?.let { json.decodeFromString<RawTraffic>(it) }
                    }
            }
            .getOrNull()
            ?.also { sample ->
                trafficSampleCache = TimedTrafficSample(sample, System.nanoTime())
            }
    }

    override suspend fun queryConnectionsAsync(): ConnectionSnapshot = fetchConnections()

    override fun queryConnections(): ConnectionSnapshot =
        runBlocking(Dispatchers.IO) { queryConnectionsAsync() }

    private suspend fun fetchConnections(): ConnectionSnapshot =
        client
            .prepareGet(buildUrl("connections", query = CONNECTIONS_QUERY)) {
                applyAuth()
            }
            .execute { response ->
                val line =
                    response.bodyAsChannel().readLine()
                        ?: error("connections stream ended before the first snapshot")
                json.decodeFromString<ConnectionSnapshot>(line)
            }

    // Profile-local preview is not available over pure REST.

    override suspend fun queryProfileProxyGroupsAsync(
        excludeNotSelectable: Boolean
    ): List<ProxyGroup> = emptyList()

    override fun queryProfileProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        emptyList()

    // ---- Proxy groups ----------------------------------------------------

    override suspend fun queryAllProxyGroupsAsync(excludeNotSelectable: Boolean): List<ProxyGroup> =
        withGroupQueryTimeout {
            val nodes = fetchProxies()
            val groups = orderGroups(fetchGroups(), nodes)
            groups
                .filter { !excludeNotSelectable || it.type in Proxy.Type.manuallySelectable }
                .map { buildGroup(it, nodes, ProxySort.Default) }
        }

    override fun queryAllProxyGroups(excludeNotSelectable: Boolean): List<ProxyGroup> =
        runBlocking(Dispatchers.IO) { queryAllProxyGroupsAsync(excludeNotSelectable) }

    override suspend fun queryProxyGroupNamesAsync(excludeNotSelectable: Boolean): List<String> =
        withGroupQueryTimeout {
            val nodes = fetchProxies()
            orderGroups(fetchGroups(), nodes)
                .filter { !excludeNotSelectable || it.type in Proxy.Type.manuallySelectable }
                .map { it.name }
        }

    private suspend fun <T> withGroupQueryTimeout(block: suspend () -> T): T =
        if (local != null) {
            withTimeout(LOCAL_GROUP_QUERY_TIMEOUT_MS) { block() }
        } else {
            block()
        }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> =
        runBlocking(Dispatchers.IO) { queryProxyGroupNamesAsync(excludeNotSelectable) }

    /** Prefer GLOBAL.all order, then name. */
    private fun orderGroups(groups: List<RawProxy>, nodes: Map<String, RawProxy>): List<RawProxy> {
        val canonical = nodes["GLOBAL"]?.all ?: emptyList()
        val indexOf = canonical.withIndex().associate { (i, name) -> name to i }
        return groups.sortedWith(compareBy({ indexOf[it.name] ?: Int.MAX_VALUE }, { it.name }))
    }

    override suspend fun queryProxyGroupAsync(name: String, proxySort: ProxySort): ProxyGroup {
        val nodes = fetchProxies()
        val group =
            nodes[name]
                ?: return ProxyGroup(
                    name = name,
                    type = Proxy.Type.Unknown,
                    proxies = emptyList(),
                    now = "",
                )
        return buildGroup(group, nodes, proxySort)
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup =
        runBlocking(Dispatchers.IO) { queryProxyGroupAsync(name, proxySort) }

    private suspend fun fetchProxies(): Map<String, RawProxy> {
        // `/proxies` is the canonical, immediately available runtime snapshot. Waiting for
        // `/providers/proxies` here made every group query depend on provider initialization and
        // regressed startup by up to the full request timeout.
        val raw = request(HttpMethod.Get, "proxies").bodyAsText()
        return json.decodeFromString<RawProxiesResponse>(raw).proxies
    }

    private suspend fun fetchGroups(): List<RawProxy> {
        val raw = request(HttpMethod.Get, "group").bodyAsText()
        return json.decodeFromString<RawGroupResponse>(raw).proxies
    }

    // ---- Selection / connections mutation --------------------------------

    @Suppress("TooGenericExceptionCaught")
    override suspend fun patchSelectorAsync(group: String, name: String): Boolean =
        try {
            val response =
                request(
                    HttpMethod.Put,
                    "proxies",
                    group,
                    body = SelectBody(name),
                )
            response.status.isSuccess()
        } catch (_: Throwable) { // fault barrier: remote REST call must degrade to "not selected"
            false
        }

    override fun patchSelector(group: String, name: String): Boolean =
        runBlocking(Dispatchers.IO) { patchSelectorAsync(group, name) }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun closeConnectionAsync(id: String): Boolean =
        try {
            request(HttpMethod.Delete, "connections", id).status.isSuccess()
        } catch (_: Throwable) { // fault barrier: remote REST call must degrade to "not closed"
            false
        }

    override fun closeConnection(id: String): Boolean =
        runBlocking(Dispatchers.IO) { closeConnectionAsync(id) }

    override suspend fun closeAllConnectionsAsync() {
        runCatching { request(HttpMethod.Delete, "connections") }
    }

    override fun closeAllConnections() {
        runBlocking(Dispatchers.IO) { closeAllConnectionsAsync() }
    }

    // ---- Health checks ---------------------------------------------------

    override suspend fun healthCheck(group: String) {
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
            val response =
                request(
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
        } catch (
            error:
                Throwable) { // fault barrier: remote REST delay test must degrade to timeout (-1)
            -1
        }

    // ---- Providers -------------------------------------------------------

    /** Proxy + rule providers; built-in Compatible entries are omitted. */
    override suspend fun queryProvidersAsync(): ProviderList {
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
        return ProviderList(proxies.getOrDefault(emptyList()) + rules.getOrDefault(emptyList()))
    }

    override fun queryProviders(): ProviderList =
        runBlocking(Dispatchers.IO) { queryProvidersAsync() }

    private suspend fun fetchProviders(
        category: String,
        type: Provider.Type,
    ): List<Provider> {
        val raw = request(HttpMethod.Get, "providers", category).bodyAsText()
        val response = json.decodeFromString<RawProvidersResponse>(raw)
        return response.providers.mapNotNull { (key, entry) ->
            val vehicle = parseVehicleType(entry.vehicleType) ?: return@mapNotNull null
            if (vehicle == Provider.VehicleType.Compatible) return@mapNotNull null
            val name =
                entry.name
                    .ifBlank { key }
                    .ifBlank {
                        return@mapNotNull null
                    }
            Provider(
                name = name,
                type = type,
                vehicleType = vehicle,
                updatedAt = parseUpdatedAtMillis(entry.updatedAt),
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

    override suspend fun queryConfigurationAsync(): UiConfiguration = UiConfiguration()

    override fun queryConfiguration(): UiConfiguration = UiConfiguration()

    // ---- Rules -----------------------------------------------------------

    override suspend fun queryRulesAsync(): List<RuntimeRule> = fetchRules()

    override fun queryRules(): List<RuntimeRule> = runBlocking(Dispatchers.IO) { queryRulesAsync() }

    /** Toggle a rule, then re-fetch `/rules`. */
    override suspend fun setRuleDisabled(
        rule: RuntimeRule,
        disabled: Boolean,
    ): List<RuntimeRule> {
        request(
            HttpMethod.Patch,
            "rules",
            "disable",
            body = jsonBody(mapOf(rule.index.toString() to disabled)),
        )
        return fetchRules()
    }

    private suspend fun fetchRules(): List<RuntimeRule> {
        val raw = request(HttpMethod.Get, "rules").bodyAsText()
        return json.decodeFromString<RawRulesResponse>(raw).rules.map { it.toRuntimeRule() }
    }

    override fun requestStop() {
        // No-op: we don't own the remote core, so there is nothing to stop.
    }

    @Synchronized
    override fun subscribeLogs(observer: LogObserver): LogSubscription {
        val sink = LogSink(observer = observer)
        logSink.set(sink)
        logJob?.cancel()
        logJob = logScope.launch {
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
        return LogSubscription {
            synchronized(this@CoreController) {
                if (logSink.compareAndSet(sink, null)) {
                    logJob?.cancel()
                    logJob = null
                }
            }
        }
    }

    /** Stream `GET /logs` until closed or the observer is cleared. */
    private suspend fun streamLogsOnce(sink: LogSink) {
        client
            .prepareGet(buildUrl("logs", query = LOG_QUERY)) {
                applyAuth()
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            }
            .execute { response ->
                if (logSink.get() !== sink) return@execute
                sink.observer.onConnected()
                val channel = response.bodyAsChannel()
                while (logSink.get() === sink && !channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
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
            "warning",
            "warn" -> LogMessage.Level.Warning
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

    private fun buildGroup(
        group: RawProxy,
        nodes: Map<String, RawProxy>,
        sort: ProxySort,
    ): ProxyGroup {
        val members =
            group.all.map { memberName ->
                nodes[memberName]?.toProxy()
                    ?: Proxy(
                        name = memberName,
                        title = memberName,
                        subtitle = "",
                        type = Proxy.Type.Unknown,
                        delay = 0,
                    )
            }
        val sorted =
            when (sort) {
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

    @Serializable
    private data class RawProvider(
        val name: String = "",
        val vehicleType: String = "",
        val updatedAt: String? = null,
        val proxies: List<RawProxy> = emptyList(),
    )

    @Serializable
    private data class RawProxiesResponse(val proxies: Map<String, RawProxy> = emptyMap())

    @Serializable private data class RawGroupResponse(val proxies: List<RawProxy> = emptyList())

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

    @Serializable private data class RawDelay(val delay: Int = 0)

    @Serializable private data class RawDelayResult(val delay: Int = 0)

    @Serializable private data class RawConfigs(val mode: TunnelState.Mode = TunnelState.Mode.Rule)

    @Serializable
    private data class RawTraffic(
        val up: Long = 0,
        val down: Long = 0,
        val upTotal: Long = 0,
        val downTotal: Long = 0,
    )

    @Serializable private data class SelectBody(val name: String)

    @Serializable private data class RawRulesResponse(val rules: List<RawRule> = emptyList())

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

    private data class LogSink(val observer: LogObserver)

    private data class TimedTrafficSample(
        val sample: RawTraffic,
        val capturedAtNanos: Long,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val LOCAL_GROUP_QUERY_TIMEOUT_MS = 1_000L
        const val TRAFFIC_SAMPLE_CACHE_NS = 500_000_000L
        const val LOG_STREAM_RETRY_MS = 1_500L

        // Dummy host; UnixSocketFactory ignores host/port.
        const val LOCAL_BASE_URL = "http://localhost"

        val delayQuery =
            mapOf(
                "url" to "https://www.gstatic.com/generate_204",
                "timeout" to "5000",
            )
        val LOG_QUERY = mapOf("level" to "debug")
        val CONNECTIONS_QUERY = mapOf("interval" to "1000")
    }
}
