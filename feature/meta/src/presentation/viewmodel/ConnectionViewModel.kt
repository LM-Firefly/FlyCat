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

package com.github.lmfirefly.flycat.feature.meta.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.bridge.Bridge
import com.github.lmfirefly.flycat.core.contract.AppIdentityReader
import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
import com.github.lmfirefly.flycat.core.model.AppIdentity
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.model.ConnectionOverviewSnapshot
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.util.ConnectionHistoryManager
import com.github.lmfirefly.flycat.core.util.ProxyChainResolver
import com.github.lmfirefly.flycat.core.util.format.formatBytes
import com.github.lmfirefly.flycat.core.util.format.formatSpeed
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

enum class ConnectionSort {
    Time,
    Upload,
    Download,
    Host,
}

enum class ConnectionTab {
    ACTIVE,
    CLOSED,
}

data class ConnectionState(
    val snapshot: ConnectionSnapshot? = null,
    val connectionSpeeds: Map<String, ConnectionSpeed> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val sortBy: ConnectionSort = ConnectionSort.Time,
    val selectedTab: ConnectionTab = ConnectionTab.ACTIVE,
    val error: String? = null,
) {
    val totalConnections: Int
        get() = snapshot?.connections?.size ?: 0
}

data class ConnectionSpeed(
    val uploadPerSecond: Long = 0L,
    val downloadPerSecond: Long = 0L,
)

data class ConnectionCardItem(
    val connectionInfo: ConnectionInfo,
    val displayHost: String,
    val relativeTime: String,
    val network: String,
    val protocolAndNetwork: String,
    val processName: String,
    val ruleChain: String,
    val downloadSpeedText: String,
    val downloadText: String,
    val uploadSpeedText: String,
    val uploadText: String,
)

class ConnectionViewModel(
    private val connectionRepository: ConnectionRepository,
    private val appIdentityResolver: AppIdentityReader,
) : BaseViewModel<ConnectionState>(ConnectionState()) {

    private var lastConnectionGeneration = 0L
    private val connectionDetailsById = LinkedHashMap<String, ConnectionInfo>()
    private var needsFullSnapshot = true
    private var pollingJob: Job? = null

    val state: StateFlow<ConnectionState> get() = uiState

    fun resolveIdentity(metadata: JsonObject): AppIdentity = appIdentityResolver.resolve(metadata)

    companion object {
        const val UNKNOWN_APP_NAME: String = AppIdentityReader.UNKNOWN_APP_NAME
        private const val POLL_INTERVAL_FAST_MS = 1_000L
        private const val POLL_INTERVAL_MEDIUM_MS = 2_000L
        private const val POLL_INTERVAL_SLOW_MS = 5_000L
        private const val POLL_MEDIUM_THRESHOLD = 40
        private const val POLL_SLOW_THRESHOLD = 200
    }

    val filteredConnections: StateFlow<List<ConnectionCardItem>> = state
        .map(::buildFilteredConnections)
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun resetHistory() {
        ConnectionHistoryManager.clear()
        connectionDetailsById.clear()
        needsFullSnapshot = true
    }

    @Suppress("TooGenericExceptionCaught")
    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                var nextDelayMs = POLL_INTERVAL_FAST_MS
                runCatching {
                    val snapshot = queryPollingSnapshot()
                    nextDelayMs = computePollIntervalMs(snapshot.connections.size)
                    val previousConnections = currentState.snapshot?.connections.orEmpty().associateBy { it.id }
                    val connectionSpeeds = snapshot.connections.associate { connection ->
                        val previous = previousConnections[connection.id]
                        connection.id to ConnectionSpeed(
                            uploadPerSecond = (connection.upload - (previous?.upload ?: connection.upload)).coerceAtLeast(0L),
                            downloadPerSecond = (connection.download - (previous?.download ?: connection.download)).coerceAtLeast(0L),
                        )
                    }
                    ConnectionHistoryManager.updateConnections(snapshot.connections)
                    updateState {
                        it.copy(
                            snapshot = snapshot,
                            connectionSpeeds = connectionSpeeds,
                            error = null,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Timber.w(error, "Failed to poll connections")
                    nextDelayMs = POLL_INTERVAL_SLOW_MS
                    updateState { it.copy(error = error.message, isRefreshing = false) }
                }
                delay(nextDelayMs)
            }
        }
    }

    fun pausePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun resumePolling() {
        if (pollingJob?.isActive == true) return
        startPolling()
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        connectionDetailsById.clear()
        needsFullSnapshot = true
    }

    fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun setSortBy(sort: ConnectionSort) {
        updateState { it.copy(sortBy = sort) }
    }

    fun setTab(tab: ConnectionTab) {
        updateState { it.copy(selectedTab = tab) }
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    suspend fun closeConnection(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching { connectionRepository.closeConnection(id) }
                .onFailure { error ->
                    Timber.w(error, "Failed to close connection: %s", id)
                    updateState { it.copy(error = error.message) }
                }
                .getOrDefault(false)
        }
    }

    suspend fun closeAllConnections(): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                connectionRepository.closeAllConnections()
                true
            }.onFailure { error ->
                Timber.w(error, "Failed to close all connections")
                updateState { it.copy(error = error.message) }
            }.getOrDefault(false)
        }
    }

    private fun buildFilteredConnections(currentState: ConnectionState): List<ConnectionCardItem> {
        val connections =
            when (currentState.selectedTab) {
                ConnectionTab.ACTIVE -> currentState.snapshot?.connections ?: emptyList()
                ConnectionTab.CLOSED -> ConnectionHistoryManager.getClosedConnections()
            }

        val filtered =
            if (currentState.searchQuery.isEmpty()) {
                connections
            } else {
                val query = currentState.searchQuery.lowercase()
                connections.filter { conn ->
                    val host = connectionDisplayTarget(conn).lowercase()
                    val process =
                        conn.metadata["process"]?.jsonPrimitive?.content?.lowercase() ?: ""
                    val chains = conn.chains.joinToString(" ").lowercase()
                    val rule = conn.rule.lowercase()

                    host.contains(query) ||
                        process.contains(query) ||
                        chains.contains(query) ||
                        rule.contains(query)
                }
            }

        val sorted =
            if (currentState.selectedTab == ConnectionTab.ACTIVE) {
                when (currentState.sortBy) {
                    ConnectionSort.Time -> filtered.sortedByDescending { it.start }
                    ConnectionSort.Upload -> filtered.sortedByDescending { it.upload }
                    ConnectionSort.Download -> filtered.sortedByDescending { it.download }
                    ConnectionSort.Host -> filtered.sortedBy { connectionDisplayTarget(it) }
                }
            } else {
                filtered
            }
        return sorted.map { connection ->
            val metadata = connection.metadata
            val type = metadata["type"]?.jsonPrimitive?.content.orEmpty()
            val network = metadata["network"]?.jsonPrimitive?.content.orEmpty().ifEmpty { "TCP" }
            val processName = metadata["process"]?.jsonPrimitive?.content.orEmpty()
            val speeds = if (currentState.selectedTab == ConnectionTab.ACTIVE) {
                currentState.connectionSpeeds[connection.id] ?: ConnectionSpeed()
            } else {
                ConnectionSpeed()
            }
            val chainParts = ProxyChainResolver.buildRuleChain(
                rule = connection.rule,
                chain = connection.chains,
            )
            ConnectionCardItem(
                connectionInfo = connection,
                displayHost = connectionDisplayTarget(connection),
                relativeTime = formatRelativeTime(connection.start),
                network = network,
                protocolAndNetwork = buildProtocolAndNetwork(type, network),
                processName = processName,
                ruleChain = ProxyChainResolver.formatProxyChain(chainParts),
                downloadSpeedText = formatSpeed(speeds.downloadPerSecond),
                downloadText = formatBytes(connection.download),
                uploadSpeedText = formatSpeed(speeds.uploadPerSecond),
                uploadText = formatBytes(connection.upload),
            )
        }
    }

    private suspend fun queryPollingSnapshot(): ConnectionSnapshot {
        if (needsFullSnapshot) {
            return queryAndCacheFullSnapshot()
        }

        // Version gate: skip the expensive overview query if no connections joined or left.
        val generation = runCatching { Bridge.nativeQueryConnectionGeneration() }.getOrDefault(0L)
        if (generation == lastConnectionGeneration) {
            // No structural change — return current cached snapshot (speeds/totals still update via push).
            return currentState.snapshot ?: return queryAndCacheFullSnapshot()
        }
        lastConnectionGeneration = generation

        val overview = connectionRepository.queryConnectionsOverview()
        return mergeOverviewSnapshot(overview) ?: queryAndCacheFullSnapshot()
    }

    private suspend fun queryAndCacheFullSnapshot(): ConnectionSnapshot {
        val snapshot = connectionRepository.queryConnections()
        cacheConnectionDetails(snapshot.connections)
        needsFullSnapshot = false
        return snapshot
    }

    private fun mergeOverviewSnapshot(overview: ConnectionOverviewSnapshot): ConnectionSnapshot? {
        val activeIds = overview.connections.mapTo(linkedSetOf()) { it.id }
        val mergedConnections = ArrayList<ConnectionInfo>(overview.connections.size)
        for (connection in overview.connections) {
            val detail = connectionDetailsById[connection.id] ?: return null
            mergedConnections += detail.copy(
                upload = connection.upload,
                download = connection.download,
            )
        }
        connectionDetailsById.keys.retainAll(activeIds)
        return ConnectionSnapshot(
            downloadTotal = overview.downloadTotal,
            uploadTotal = overview.uploadTotal,
            connections = mergedConnections,
            memory = overview.memory,
        )
    }

    private fun cacheConnectionDetails(connections: List<ConnectionInfo>) {
        val activeIds = connections.mapTo(linkedSetOf()) { it.id }
        connections.forEach { connection -> connectionDetailsById[connection.id] = connection }
        connectionDetailsById.keys.retainAll(activeIds)
    }

    private fun computePollIntervalMs(activeConnectionCount: Int): Long =
        when {
            activeConnectionCount <= 0 -> POLL_INTERVAL_SLOW_MS
            activeConnectionCount <= POLL_MEDIUM_THRESHOLD -> POLL_INTERVAL_FAST_MS
            activeConnectionCount <= POLL_SLOW_THRESHOLD -> POLL_INTERVAL_MEDIUM_MS
            else -> POLL_INTERVAL_SLOW_MS
        }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

private fun buildProtocolAndNetwork(type: String, network: String): String {
    val displayType = type.trim().uppercase()
    val displayNetwork = network.trim().uppercase()
    return when {
        displayType.isNotEmpty() && displayNetwork.isNotEmpty() -> "$displayType | $displayNetwork"
        displayType.isNotEmpty() -> displayType
        else -> displayNetwork
    }
}

private fun formatRelativeTime(start: String): String {
    if (start.isEmpty()) return ""
    return try {
        val startTime = java.time.OffsetDateTime.parse(start).toInstant()
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(startTime, now)
        val seconds = duration.seconds
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        when {
            seconds < 60 -> FlyTxt.Connection.RelativeTime.JustNow
            minutes < 60 -> FlyTxt.Connection.RelativeTime.MinutesAgo.format(minutes)
            hours < 24 -> FlyTxt.Connection.RelativeTime.HoursAgo.format(hours)
            days < 7 -> FlyTxt.Connection.RelativeTime.DaysAgo.format(days)
            else -> {
                val date = java.time.LocalDateTime.ofInstant(startTime, java.time.ZoneId.systemDefault())
                FlyTxt.Connection.RelativeTime.Date.format(date.monthValue, date.dayOfMonth)
            }
        }
    } catch (_: Exception) {
        ""
    }
}

private fun connectionDisplayTarget(connection: ConnectionInfo): String {
    val metadata = connection.metadata
    val host = metadata["host"]?.jsonPrimitive?.content.orEmpty()
    val destinationIp = metadata["destinationIP"]?.jsonPrimitive?.content.orEmpty()
    val destinationPort = metadata["destinationPort"]?.jsonPrimitive?.content.orEmpty()
    val sourceIp = metadata["sourceIP"]?.jsonPrimitive?.content.orEmpty()
    val sourcePort = metadata["sourcePort"]?.jsonPrimitive?.content.orEmpty()
    return when {
        host.isNotBlank() && destinationPort.isNotBlank() -> "$host:$destinationPort"
        host.isNotBlank() -> host
        destinationIp.isNotBlank() && destinationPort.isNotBlank() ->
            "$destinationIp:$destinationPort"
        destinationIp.isNotBlank() -> destinationIp
        sourceIp.isNotBlank() && sourcePort.isNotBlank() -> "$sourceIp:$sourcePort"
        else -> sourceIp
    }
}
