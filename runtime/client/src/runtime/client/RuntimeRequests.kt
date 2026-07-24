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

package com.github.yumelira.yumebox.runtime.client

import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner

/** Start request carried as one object instead of long argument lists. */
data class RuntimeStartRequest(
    val owner: RuntimeOwner,
    val mode: RunMode,
    val profile: Profile? = null,
)

/** Stop request carried as one object instead of long argument lists. */
data class RuntimeStopRequest(
    val owner: RuntimeOwner = RuntimeOwner.None,
    val targetMode: RunMode,
    val completeImmediately: Boolean = false,
    val reason: String? = null,
)
