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

import kotlinx.serialization.Serializable

@Serializable
enum class WifiAutomationAction {
    Start,
    Stop,
}

/** Action to take when the connected Wi-Fi does not match an SSID rule, or is disconnected. */
@Serializable
enum class WifiAutomationFallbackAction {
    Keep,
    Start,
    Stop,
}

/** A case-sensitive SSID rule. SSIDs are intentionally stored exactly as supplied. */
@Serializable
data class WifiAutomationRule(
    val ssid: String,
    val action: WifiAutomationAction,
)
