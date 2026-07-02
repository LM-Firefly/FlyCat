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

package com.github.yumelira.yumebox.runtime.service.root

import com.github.yumelira.yumebox.runtime.service.session.RuntimeLogChunk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object RootTunJson {
    val Default = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** Reified shorthand for [RootTunJson] marshalling — reuses the shared `Default` config. */
inline fun <reified T> rootTunEncode(value: T): String = RootTunJson.Default.encodeToString(value)

inline fun <reified T> rootTunDecode(json: String): T = RootTunJson.Default.decodeFromString(json)

@Serializable data class RootTunStartRequest(val source: String = "")

@Serializable
data class RootTunOperationResult(
    val success: Boolean,
    val error: String? = null,
)

@Serializable
data class RootTunLogChunk(
    val nextSeq: Long = 0L,
    val items: List<String> = emptyList(),
) {
    companion object {
        fun from(value: RuntimeLogChunk): RootTunLogChunk =
            RootTunLogChunk(nextSeq = value.nextSeq, items = value.items)
    }
}
