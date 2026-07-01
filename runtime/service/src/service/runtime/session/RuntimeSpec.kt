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

package com.github.yumelira.yumebox.runtime.service.runtime.session

import kotlinx.serialization.Serializable

// RuntimeSpec has been moved to runtime:api (com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec)

@Serializable
data class RuntimeOperationResult(
    val success: Boolean,
    val error: String? = null,
)

@Serializable
data class RuntimeLogChunk(
    val nextSeq: Long = 0L,
    val items: List<String> = emptyList(),
)
