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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ─────────────────────────────────────────────────────────────────────────────
// UDS Protocol message types
//
// Wire format: 4-byte big-endian length prefix + JSON body
// ─────────────────────────────────────────────────────────────────────────────

/** A client-to-server RPC request. */
@Serializable
data class UdsRequest(
    val id: String,
    val method: String,
    val params: JsonElement? = null,
)

/** A server-to-client RPC response (sent on the same connection as the request). */
@Serializable
data class UdsResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: UdsResponseError? = null,
)

/** Error detail in a response. */
@Serializable
data class UdsResponseError(
    val code: Int,
    val message: String,
)

/** A server-to-client push event (on a dedicated event connection). */
@Serializable
data class UdsEvent(
    val event: String,
    val data: JsonElement,
)

// ─── Traffic event (pushed periodically) ─────────────────────────────────────

@Serializable
data class UdsTrafficEvent(
    @SerialName("uploadNow") val uploadNow: Long = 0,
    @SerialName("downloadNow") val downloadNow: Long = 0,
    @SerialName("uploadTotal") val uploadTotal: Long = 0,
    @SerialName("downloadTotal") val downloadTotal: Long = 0,
)

// ─── Log event (pushed on log subscription) ──────────────────────────────────

@Serializable
data class UdsLogEvent(
    val level: String,
    val message: String,
    val time: Long,
)

// ─── Parameter types ─────────────────────────────────────────────────────────

@Serializable
data class CoreInitParams(
    val home: String,
    val versionName: String,
    val gitVersion: String,
    val sdkVersion: Int,
)

@Serializable
data class CoreSuspendParams(
    val suspended: Boolean,
)

@Serializable
data class SetUserAgentParams(
    val userAgent: String,
)

@Serializable
data class SetAgeSecretKeyParams(
    val key: String,
)

@Serializable
data class QueryGroupNamesParams(
    val excludeNotSelectable: Boolean,
)

@Serializable
data class QueryGroupParams(
    val name: String,
    val sort: String,
)

@Serializable
data class PatchSelectorParams(
    val selector: String,
    val name: String,
)

@Serializable
data class HealthCheckParams(
    val name: String,
)

@Serializable
data class HealthCheckProxyParams(
    val proxyName: String,
)

@Serializable
data class CloseConnectionParams(
    val id: String,
)

@Serializable
data class UpdateProviderParams(
    val type: String,
    val name: String,
)

@Serializable
data class NotifyDnsParams(
    val dnsList: String,
)

@Serializable
data class NotifyTimezoneParams(
    val name: String,
    val offset: Int,
)

@Serializable
data class StartHttpParams(
    val listenAt: String,
)

@Serializable
data class LoadCompiledRawParams(
    val configRawJson: String,
)

@Serializable
data class InspectCompiledGroupsParams(
    val configRawJson: String,
    val profileDir: String,
    val excludeNotSelectable: Boolean,
)

@Serializable
data class InspectTunRouteExcludeAddressParams(
    val configRawJson: String,
)

@Serializable
data class ConvertMrsToTextParams(
    val filePath: String,
)

@Serializable
data class CompiledRawResultErrorParams(
    val resultJson: String,
)

@Serializable
data class CompiledRawResultSummaryParams(
    val resultJson: String,
)

@Serializable
data class LogSubscribeParams(
    val level: String = "info",
)
