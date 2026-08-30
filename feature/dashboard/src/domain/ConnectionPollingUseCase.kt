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

package com.github.lmfirefly.flycat.feature.dashboard.domain

import com.github.lmfirefly.flycat.core.bridge.Bridge
import com.github.lmfirefly.flycat.core.contract.AppIdentityReader
import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.model.ConnectionOverviewSnapshot
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.util.ConnectionHistoryManager
import com.github.lmfirefly.flycat.core.util.ProxyChainResolver
import com.github.lmfirefly.flycat.core.util.format.formatBytes
import com.github.lmfirefly.flycat.core.util.format.formatSpeed
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encapsulates connection polling strategy, snapshot merging, speed computation,
 * and connection filtering/formatting logic extracted from ConnectionViewModel.
 */
class ConnectionPollingUseCase(
    private val connectionRepository: ConnectionRepository,
    private val appIdentityResolver: AppIdentityReader,
) {
    companion object {
        const val POLL_INTERVAL_FAST_MS = 1_000L
        const val POLL_INTERVAL_MEDIUM_MS = 2_000L
        const val POLL_INTERVAL_SLOW_MS = 5_000L
        private const val POLL_MEDIUM_THRESHOLD = 40
        private const val POLL_SLOW_THRESHOLD = 200
    }

    // ── Snapshot caching state ────────────────────────────────────────────────
    private var lastConnectionGeneration = 0L
    private val connectionDetailsById = LinkedHashMap<String, ConnectionInfo>()
    private var needsFullSnapshot = true

    /** Reset all cached state (call when stopping polling). */
    fun resetCache() {
        connectionDetailsById.clear()
        needsFullSnapshot = true
        lastConnectionGeneration = 0L
    }

    /** Mark that a full snapshot is needed on next poll. */
    fun markNeedsFullSnapshot() {
        needsFullSnapshot = true
    }

    // ── Polling strategy ──────────────────────────────────────────────────────

    /**
     * Query the next connection snapshot using a smart polling strategy:
     * - Full snapshot on first poll or after reset
     * - Incremental overview merge when connections joined/left (generation change)
     * - Reuse cached snapshot when no structural change
     */
    suspend fun queryPollingSnapshot(currentSnapshot: ConnectionSnapshot?): ConnectionSnapshot {
        if (needsFullSnapshot) {
            return queryAndCacheFullSnapshot()
        }

        val generation = runCatching { Bridge.nativeQueryConnectionGeneration() }.getOrDefault(0L)
        if (generation == lastConnectionGeneration) {
            return currentSnapshot ?: return queryAndCacheFullSnapshot()
        }
        lastConnectionGeneration = generation

        val overview = connectionRepository.queryConnectionsOverview()
        return mergeOverviewSnapshot(overview) ?: queryAndCacheFullSnapshot()
    }

    /** Compute the adaptive polling interval based on active connection count. */
    fun computePollIntervalMs(activeConnectionCount: Int): Long = when {
        activeConnectionCount <= 0 -> POLL_INTERVAL_SLOW_MS
        activeConnectionCount <= POLL_MEDIUM_THRESHOLD -> POLL_INTERVAL_FAST_MS
        activeConnectionCount <= POLL_SLOW_THRESHOLD -> POLL_INTERVAL_MEDIUM_MS
        else -> POLL_INTERVAL_SLOW_MS
    }

    /** Compute per-connection speed deltas between current and previous snapshots. */
    fun computeSpeedDeltas(
        currentConnections: List<ConnectionInfo>,
        previousConnections: List<ConnectionInfo>,
    ): Map<String, Pair<Long, Long>> {
        val prevMap = previousConnections.associateBy { it.id }
        return currentConnections.associate { conn ->
            val prev = prevMap[conn.id]
            conn.id to Pair(
                (conn.upload - (prev?.upload ?: conn.upload)).coerceAtLeast(0L),
                (conn.download - (prev?.download ?: conn.download)).coerceAtLeast(0L),
            )
        }
    }

    // ── Connection filtering & formatting ─────────────────────────────────────

    /**
     * Filter, sort, and format connections into UI-ready card items.
     */
    fun buildFilteredConnections(
        connections: List<ConnectionInfo>,
        searchQuery: String,
        sortBy: ConnectionSortMode,
        connectionSpeeds: Map<String, Pair<Long, Long>>,
        showSpeeds: Boolean,
    ): List<ConnectionCardData> {
        val filtered = if (searchQuery.isEmpty()) {
            connections
        } else {
            val query = searchQuery.lowercase()
            connections.filter { conn ->
                val host = connectionDisplayTarget(conn).lowercase()
                val process = conn.metadata["process"]?.jsonPrimitive?.content?.lowercase() ?: ""
                val chains = conn.chains.joinToString(" ").lowercase()
                val rule = conn.rule.lowercase()
                host.contains(query) || process.contains(query) || chains.contains(query) || rule.contains(query)
            }
        }

        val sorted = when (sortBy) {
            ConnectionSortMode.Time -> filtered.sortedByDescending { it.start }
            ConnectionSortMode.Upload -> filtered.sortedByDescending { it.upload }
            ConnectionSortMode.Download -> filtered.sortedByDescending { it.download }
            ConnectionSortMode.Host -> filtered.sortedBy { connectionDisplayTarget(it) }
        }

        return sorted.map { conn ->
            val metadata = conn.metadata
            val type = metadata["type"]?.jsonPrimitive?.content.orEmpty()
            val network = metadata["network"]?.jsonPrimitive?.content.orEmpty().ifEmpty { "TCP" }
            val processName = metadata["process"]?.jsonPrimitive?.content.orEmpty()
            val speeds = if (showSpeeds) connectionSpeeds[conn.id] else null
            val chainParts = ProxyChainResolver.buildRuleChain(rule = conn.rule, chain = conn.chains)
            ConnectionCardData(
                connectionInfo = conn,
                displayHost = connectionDisplayTarget(conn),
                relativeTime = formatRelativeTime(conn.start),
                network = network,
                protocolAndNetwork = buildProtocolAndNetwork(type, network),
                processName = processName,
                ruleChain = ProxyChainResolver.formatProxyChain(chainParts),
                downloadSpeedText = formatSpeed(speeds?.second ?: 0L),
                downloadText = formatBytes(conn.download),
                uploadSpeedText = formatSpeed(speeds?.first ?: 0L),
                uploadText = formatBytes(conn.upload),
            )
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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
            mergedConnections += detail.copy(upload = connection.upload, download = connection.download)
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
}

// ── Data classes for domain layer ────────────────────────────────────────────

enum class ConnectionSortMode { Time, Upload, Download, Host }

data class ConnectionCardData(
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

// ── Shared formatting utilities ──────────────────────────────────────────────

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
        destinationIp.isNotBlank() && destinationPort.isNotBlank() -> "$destinationIp:$destinationPort"
        destinationIp.isNotBlank() -> destinationIp
        sourceIp.isNotBlank() && sourcePort.isNotBlank() -> "$sourceIp:$sourcePort"
        else -> sourceIp
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
            seconds < 60 -> "${seconds}s"
            minutes < 60 -> "${minutes}m ${seconds % 60}s"
            hours < 24 -> "${hours}h ${minutes % 60}m"
            days < 7 -> "${days}d ${hours % 24}h"
            else -> "${days}d"
        }
    } catch (_: Exception) {
        ""
    }
}

private fun buildProtocolAndNetwork(type: String, network: String): String {
    val protocol = type.takeIf { it.isNotBlank() } ?: return network
    return "$protocol · $network"
}
