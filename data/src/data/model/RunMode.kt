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

package com.github.yumelira.yumebox.data.model

/**
 * The user-facing proxy run mode selected on the network settings page. [VpnService] is the only mode
 * available today (it maps to [ProxyMode.Tun]); [RootTun] and [Tproxy] are root-only and land with the
 * future libsu path. Distinct from [ProxyMode], which is the runtime transport (Tun/Http).
 */
enum class RunMode {
    VpnService,
    RootTun,
    Tproxy,
}
