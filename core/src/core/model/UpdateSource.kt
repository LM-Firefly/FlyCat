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

package com.github.yumelira.yumebox.core.model

enum class UpdateSource(val key: String, val tag: String?) {
    Latest("latest", null),
    Prerelease("prerelease", "Pre-release"),
    Smart("smart", "Smart"),
    ;
    companion object {
        fun fromKey(raw: String?): UpdateSource {
            val key = raw?.trim().orEmpty()
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: Latest
        }
    }
}
