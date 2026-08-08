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

package com.github.yumelira.yumebox.runtime.service.runtime.session

import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.service.runtime.session.RuntimeSpec

class RootTunTransport : RuntimeTransport {
    private val startupLogStore =
        RuntimeStartupLogStore(
            com.github.yumelira.yumebox.core.Global.application,
            RuntimeStartupLogStore.Scope.ROOT_TUN,
        )

    override fun start(spec: RuntimeSpec) {
        when (spec.runMode) {
            RunMode.Tun -> {
                val config = spec.rootTunConfig ?: error("root tun config missing")
                startupLogStore.append("ROOT_TUN transport start: begin (TUN mode)")
                Clash.startRootTun(config)?.let { error(it) }
                startupLogStore.append("ROOT_TUN transport start: done (TUN mode)")
            }
            else -> error("RootTunTransport does not support ${spec.runMode}")
        }
    }

    override fun stop() {
        Clash.stopRootTun()
        Clash.stopHttp()
        Clash.stopTun()
    }
}
