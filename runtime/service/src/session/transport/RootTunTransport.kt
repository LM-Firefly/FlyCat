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

package com.github.lmfirefly.flycat.runtime.service.session.transport

import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec
import com.github.lmfirefly.flycat.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.topjohnwu.superuser.Shell

class RootTunTransport : RuntimeTransport {
    private val startupLogStore =
        RuntimeStartupLogStore(
            com.github.lmfirefly.flycat.core.Global.application,
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
            RunMode.Ebpf -> {
                startupLogStore.append("ROOT_TUN transport start: begin (eBPF mode)")
                val config = spec.rootTunConfig ?: error("eBPF profile config missing")
                Clash.startRootTun(config)?.let { error(it) }
                startupLogStore.append("ROOT_TUN transport start: done (eBPF mode)")
            }
            else -> error("RootTunTransport does not support ${spec.runMode}")
        }
    }

    override fun stop() {
        Clash.stopRootTun()
        Clash.stopHttp()
        Clash.stopTun()
    }

    companion object {
        /** Finds the mihomo root daemon PID by scanning /proc for the libmihomo.so executable. */
        fun findMihomoPid(): Int? =
            runCatching {
                Shell.cmd(
                    "for proc in /proc/[0-9]*; do " +
                        "pid=\${proc##*/}; exe=\$(readlink \"\$proc/exe\" 2>/dev/null); " +
                        "case \"\${exe%% *}\" in */libmihomo.so) echo \$pid;; esac; " +
                        "done"
                )
                    .exec()
                    .out
                    .mapNotNull { it.trim().toIntOrNull() }
                    .firstOrNull()
            }.getOrNull()
    }
}
