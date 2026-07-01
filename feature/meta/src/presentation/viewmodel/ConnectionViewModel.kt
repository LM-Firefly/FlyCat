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

package com.github.yumelira.yumebox.feature.meta.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.AppIdentityReader
import com.github.yumelira.yumebox.core.domain.ConnectionHistoryManager
import com.github.yumelira.yumebox.core.model.AppIdentity
import com.github.yumelira.yumebox.core.model.ConnectionInfo
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.util.buildRuleChain
import com.github.yumelira.yumebox.core.util.formatProxyChain
import com.github.yumelira.yumebox.platform.util.formatBytes
import com.github.yumelira.yumebox.platform.util.formatSpeed
import com.github.yumelira.yumebox.presentation.viewmodel.BaseViewModel
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val proxyFacade: ProxyFacade,
    private val appIdentityResolver: AppIdentityReader,
) : BaseViewModel<ConnectionState>(ConnectionState()) {

    val state: StateFlow<ConnectionState> get() = uiState

    fun resolveIdentity(metadata: JsonObject): AppIdentity = appIdentityResolver.resolve(metadata)

    companion object {
        const val UNKNOWN_APP_NAME: String = AppIdentityReader.UNKNOWN_APP_NAME
    }

    val filteredConnections: StateFlow<List<ConnectionCardItem>> = state
        .map(::buildFilteredConnections)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private var pollingJob: Job? = null

    fun resetHistory() {
        ConnectionHistoryManager.clear()
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            proxyFacade.connectionSnapshot.collect { snapshot ->
                try {
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
                } catch (error: Exception) {
                    Timber.w(error, "Failed to process connection snapshot")
                    updateState { it.copy(error = error.message, isRefreshing = false) }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
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
            runCatching { proxyFacade.closeConnection(id) }
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
                proxyFacade.closeAllConnections()
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
            val chainParts = buildRuleChain(
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
                ruleChain = formatProxyChain(chainParts),
                downloadSpeedText = formatSpeed(speeds.downloadPerSecond),
                downloadText = formatBytes(connection.download),
                uploadSpeedText = formatSpeed(speeds.uploadPerSecond),
                uploadText = formatBytes(connection.upload),
            )
        }
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
            seconds < 60 -> MLang.Connection.RelativeTime.JustNow
            minutes < 60 -> MLang.Connection.RelativeTime.MinutesAgo.format(minutes)
            hours < 24 -> MLang.Connection.RelativeTime.HoursAgo.format(hours)
            days < 7 -> MLang.Connection.RelativeTime.DaysAgo.format(days)
            else -> {
                val date = java.time.LocalDateTime.ofInstant(startTime, java.time.ZoneId.systemDefault())
                MLang.Connection.RelativeTime.Date.format(date.monthValue, date.dayOfMonth)
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
