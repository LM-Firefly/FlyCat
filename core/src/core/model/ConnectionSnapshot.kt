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

package com.github.lmfirefly.flycat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * 原生连接负载的共享容错解码器。本地与 Root 两条链路都用它直接解码原生 JSON，
 * 从而让 Root 服务端省去「解码成对象再重编码」的双重序列化；原生侧对空切片发 `null`，
 * [coerceInputValues] 会把 `connections: null` 归一回空列表。
 */
object ConnectionSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun decodeSnapshot(rawJson: String): ConnectionSnapshot = try { json.decodeFromString(ConnectionSnapshot.serializer(), rawJson) } catch (_: kotlinx.serialization.SerializationException) {
        val element = json.parseToJsonElement(rawJson)
        val normalized = if (element is JsonObject && element["connections"] == JsonNull) {
            JsonObject(element.toMutableMap().apply { put("connections", JsonArray(emptyList())) })
        } else {
            element
        }
        json.decodeFromJsonElement(ConnectionSnapshot.serializer(), normalized)
    }

    fun decodeOverview(rawJson: String): ConnectionOverviewSnapshot = json.decodeFromString(ConnectionOverviewSnapshot.serializer(), rawJson)

    /** 解码裸 `List<ConnectionInfo>`（如按 id 水合的负载）。原生侧对空集合发送 `null`。 */
    fun decodeConnectionList(rawJson: String): List<ConnectionInfo> = if (rawJson.isBlank() || rawJson == "null") {
        emptyList()
    } else {
        json.decodeFromString(ListSerializer(ConnectionInfo.serializer()), rawJson)
    }
}

@Serializable
data class ConnectionSnapshot(
    val downloadTotal: Long = 0L,
    val uploadTotal: Long = 0L,
    val connections: List<ConnectionInfo> = emptyList(),
    val memory: Long = 0L,
)

@Serializable
data class ConnectionOverviewSnapshot(
    val downloadTotal: Long = 0L,
    val uploadTotal: Long = 0L,
    val connections: List<ConnectionOverviewInfo> = emptyList(),
    val memory: Long = 0L,
)

@Serializable
data class ConnectionOverviewInfo(
    val id: String = "",
    @SerialName("upload") val upload: Long = 0L,
    @SerialName("download") val download: Long = 0L,
)

@Serializable
data class ConnectionInfo(
    val id: String = "",
    val metadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("upload") val upload: Long = 0L,
    @SerialName("download") val download: Long = 0L,
    val start: String = "",
    val chains: List<String> = emptyList(),
    val providerChains: List<String> = emptyList(),
    val rule: String = "",
    val rulePayload: String = "",
    val uploadDelta: Long = 0L,
    val downloadDelta: Long = 0L,
)
