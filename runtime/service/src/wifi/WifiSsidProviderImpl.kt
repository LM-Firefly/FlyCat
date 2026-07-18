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

package com.github.lmfirefly.flycat.runtime.service.wifi

import android.content.Context
import com.github.lmfirefly.flycat.runtime.api.wifi.WifiSsidObservation
import com.github.lmfirefly.flycat.runtime.api.wifi.WifiSsidProvider
import com.github.lmfirefly.flycat.runtime.api.wifi.WifiSsidScanResult

/**
 * Android-backed [WifiSsidProvider] that delegates to [WifiSsidScanner] and [WifiSsidObserver].
 */
class WifiSsidProviderImpl(private val context: Context) : WifiSsidProvider {

    override fun normalizeSsid(raw: String): String? = WifiSsidObserver.normalizeSsid(raw)

    override suspend fun scanOnce(): WifiSsidScanResult = WifiSsidScanner.scanOnce(context)

    override suspend fun readOnce(): WifiSsidObservation = WifiSsidObserver.readOnce(context)
}
