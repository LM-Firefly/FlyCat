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

/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumeyucca.yumebox.runtime.service.session

import com.github.yumeyucca.yumebox.core.model.OverrideSpec
import com.github.yumeyucca.yumebox.core.util.YamlCodec
import java.io.File

/**
 * The eBPF bridge talks to a normal mihomo Mixed listener. It must be deterministic: the native
 * process is deliberately independent from mihomo's internal listener implementation and cannot
 * discover a profile-selected random port after startup.
 *
 * This fragment is appended after user overrides and is therefore authoritative for the local
 * bridge entry point. It also disables any profile-provided Tun entry point; eBPF is a socket
 * address mode, not a second Tun device.
 */
object EbpfOverride {
    const val FILE_NAME = "__ebpf_bridge_override__.yaml"
    const val MIXED_PORT = 7890

    fun buildYaml(): String =
        YamlCodec.dumpMap(
            linkedMapOf<String, Any?>(
                "mixed-port" to MIXED_PORT,
                "allow-lan" to false,
                "bind-address" to "127.0.0.1",
                "tun" to
                        linkedMapOf<String, Any?>(
                            "enable" to false,
                            "auto-route" to false,
                            "auto-redirect" to false,
                            "auto-detect-interface" to false,
                        ),
            )
        )

    fun materialize(dir: File): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml())
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}
