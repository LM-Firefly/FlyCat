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
 *
 */

package com.github.lmfirefly.flycat.runtime.api.wifi

sealed interface WifiSsidScanResult {
    data class Success(val networks: List<WifiSsidNetwork>) : WifiSsidScanResult

    /** Wi-Fi is disabled, scan access is unavailable, or Android rejected the request. */
    data object Unavailable : WifiSsidScanResult
}

/** One visible SSID together with the strongest scanned radio's public capability metadata. */
data class WifiSsidNetwork(
    val ssid: String,
    val band: String?,
    val wifiGeneration: String?,
) {
    val label: String?
        get() {
            val parts = listOfNotNull(band, wifiGeneration)
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

/** Observation of the currently connected Wi-Fi network. */
sealed interface WifiSsidObservation {
    data class Connected(val ssid: String) : WifiSsidObservation
    /** No physical Wi-Fi transport is connected. This can mean cellular, Ethernet, or offline. */
    data object NoWifi : WifiSsidObservation
    /** Permission loss, disabled location services, or SSID redaction. */
    data object Unavailable : WifiSsidObservation
}

/** Contract for Wi-Fi scanning and SSID observation. Implemented by [runtime:service]. */
interface WifiSsidProvider {
    /** Normalize a raw SSID string, returning null if blank/invalid. */
    fun normalizeSsid(raw: String): String?

    /** Perform one explicit, user-initiated Wi-Fi scan. */
    suspend fun scanOnce(): WifiSsidScanResult

    /** Read the currently connected Wi-Fi SSID without triggering a scan. */
    suspend fun readOnce(): WifiSsidObservation
}
