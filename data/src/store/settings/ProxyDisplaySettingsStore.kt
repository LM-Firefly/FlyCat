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

package com.github.lmfirefly.flycat.data.store

import com.github.lmfirefly.flycat.core.contract.ProxyDisplaySettingsReader
import com.github.lmfirefly.flycat.core.model.proxy.PROXY_SHEET_HEIGHT_FRACTION_DEFAULT
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxySortMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState
import com.tencent.mmkv.MMKV

class ProxyDisplaySettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), ProxyDisplaySettingsReader {
    override val sortMode by enumFlow(ProxySortMode.DEFAULT)
    override val displayMode by enumFlow(ProxyDisplayMode.DOUBLE_DETAILED)
    override val proxyMode by enumFlow(TunnelState.Mode.Rule)
    override val sheetHeightFraction by floatFlow(PROXY_SHEET_HEIGHT_FRACTION_DEFAULT)
}
