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

package com.github.yumelira.yumebox.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class IpInfo(
    val ip: String,
    @SerialName("country_code") val countryCode: String? = null,
)

sealed class IpMonitoringState {
    data class Success(
        val localIp: String?,
        val externalIp: IpInfo?,
        val isProxyActive: Boolean = false,
    ) : IpMonitoringState()

    data class Error(val message: String) : IpMonitoringState()

    object Loading : IpMonitoringState()
}
