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

package com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.lmfirefly.flycat.core.contract.AppIdentityReader
import com.github.lmfirefly.flycat.core.contract.ConnectionRepository
import com.github.lmfirefly.flycat.core.model.AppIdentity
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.model.ConnectionSnapshot
import com.github.lmfirefly.flycat.core.util.ConnectionHistoryManager
import com.github.lmfirefly.flycat.core.util.format.formatBytes
import com.github.lmfirefly.flycat.core.util.format.formatSpeed
import com.github.lmfirefly.flycat.feature.dashboard.domain.ConnectionCardData
import com.github.lmfirefly.flycat.feature.dashboard.domain.ConnectionPollingUseCase
import com.github.lmfirefly.flycat.feature.dashboard.domain.ConnectionSortMode
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

// Re-export domain types for backward compatibility with Screen files
typealias ConnectionSort = ConnectionSortMode
typealias ConnectionCardItem = ConnectionCardData

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
    val sortBy: ConnectionSortMode = ConnectionSortMode.Time,
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

class ConnectionViewModel(
    private val connectionRepository: ConnectionRepository,
    private val appIdentityResolver: AppIdentityReader,
    private val pollingUseCase: ConnectionPollingUseCase,
) : BaseViewModel<ConnectionState>(ConnectionState()) {

    private var pollingJob: Job? = null

    val state: StateFlow<ConnectionState> get() = uiState

    fun resolveIdentity(metadata: JsonObject): AppIdentity = appIdentityResolver.resolve(metadata)

    companion object {
        const val UNKNOWN_APP_NAME: String = AppIdentityReader.UNKNOWN_APP_NAME
    }

    val filteredConnections: StateFlow<List<ConnectionCardItem>> = state
        .map { currentState ->
            val connections = when (currentState.selectedTab) {
                ConnectionTab.ACTIVE -> currentState.snapshot?.connections ?: emptyList()
                ConnectionTab.CLOSED -> ConnectionHistoryManager.getClosedConnections()
            }
            pollingUseCase.buildFilteredConnections(
                connections = connections,
                searchQuery = currentState.searchQuery,
                sortBy = currentState.sortBy,
                connectionSpeeds = currentState.connectionSpeeds.mapValues { Pair(it.value.uploadPerSecond, it.value.downloadPerSecond) },
                showSpeeds = currentState.selectedTab == ConnectionTab.ACTIVE,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun resetHistory() {
        ConnectionHistoryManager.clear()
        pollingUseCase.resetCache()
    }

    @Suppress("TooGenericExceptionCaught")
    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                var nextDelayMs = ConnectionPollingUseCase.POLL_INTERVAL_FAST_MS
                runCatching {
                    val previousConnections = currentState.snapshot?.connections.orEmpty()
                    val snapshot = pollingUseCase.queryPollingSnapshot(currentState.snapshot)
                    nextDelayMs = pollingUseCase.computePollIntervalMs(snapshot.connections.size)
                    val speedDeltas = pollingUseCase.computeSpeedDeltas(snapshot.connections, previousConnections)
                    val connectionSpeeds = speedDeltas.mapValues { (_, v) ->
                        ConnectionSpeed(uploadPerSecond = v.first, downloadPerSecond = v.second)
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
                    nextDelayMs = ConnectionPollingUseCase.POLL_INTERVAL_SLOW_MS
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
        pollingUseCase.resetCache()
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

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
