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

package com.github.yumelira.yumebox.runtime.api

import com.github.yumelira.yumebox.core.model.*

/**
 * Coroutine-native controller queries. Prefer this over [CoreApi] sync methods to avoid nested
 * runBlocking. [CoreApi] remains for legacy call sites and is implemented as a thin bridge.
 */
interface CoreAsyncQueries {
    suspend fun queryTunnelStateAsync(): TunnelState

    suspend fun queryTrafficNowAsync(): Long

    suspend fun queryTrafficTotalAsync(): Long

    suspend fun queryConnectionsAsync(): ConnectionSnapshot

    suspend fun queryProfileProxyGroupsAsync(excludeNotSelectable: Boolean): List<ProxyGroup>

    suspend fun queryAllProxyGroupsAsync(excludeNotSelectable: Boolean): List<ProxyGroup>

    suspend fun queryProxyGroupNamesAsync(excludeNotSelectable: Boolean): List<String>

    suspend fun queryProxyGroupAsync(name: String, proxySort: ProxySort): ProxyGroup

    suspend fun queryConfigurationAsync(): UiConfiguration

    suspend fun queryProvidersAsync(): ProviderList

    suspend fun queryRulesAsync(): List<RuntimeRule>

    suspend fun patchSelectorAsync(group: String, name: String): Boolean

    suspend fun closeConnectionAsync(id: String): Boolean

    suspend fun closeAllConnectionsAsync()
}
