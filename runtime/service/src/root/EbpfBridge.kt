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

package com.github.lmfirefly.flycat.runtime.service.root

import com.github.lmfirefly.flycat.core.model.OverrideSpec
import com.github.lmfirefly.flycat.core.util.YamlCodec
import java.io.File

/**
 * 添加了用于CN绕过的可选CN提供者和原生eBPF监听器。
 * 原生eBPF监听器是mihomo进程自身的一部分——无需单独的桥接器。
 */
object EbpfOverride {
    const val FILE_NAME = "__ebpf_cn_override__.yaml"
    private const val LISTENER_NAME = "ebpf-in"
    private const val CN_PROVIDER_NAME = "CN-IP"
    private const val CN_PROVIDER_URL =
        "https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/cn.mrs"

    data class Config(val bypassCn: Boolean)

    fun buildYaml(config: Config, providerPath: String): String {
        if (!config.bypassCn) return ""
        val override =
            linkedMapOf<String, Any?>(
                "listeners+" to
                    listOf(
                        linkedMapOf<String, Any?>(
                            "name" to LISTENER_NAME,
                            "type" to "ebpf",
                            "mode" to "hybrid",
                            "dns-mode" to "hijack",
                            "bypass-rule-set" to listOf(CN_PROVIDER_NAME),
                            "shared" to linkedMapOf("interface" to listOf("br0")),
                        )
                    ),
                "rule-providers-merge" to
                    linkedMapOf(
                        CN_PROVIDER_NAME to
                            linkedMapOf<String, Any?>(
                                "type" to "http",
                                "behavior" to "ipcidr",
                                "format" to "mrs",
                                "interval" to 86400,
                                "path" to providerPath,
                                "url" to CN_PROVIDER_URL,
                            ),
                    ),
            )
        return YamlCodec.dumpMap(override)
    }

    fun materialize(config: Config, dir: File): OverrideSpec? {
        if (!config.bypassCn) return null
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        val providerFile = File(dir, "rule_provider/cn-ip.mrs")
        providerFile.parentFile?.mkdirs()
        file.writeText(buildYaml(config, providerFile.absolutePath))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}

//** 用于升级迁移的遗留网桥状态清理。 */
object EbpfBridgeMigration {
    private const val STORE_ID = "ebpf_bridge_state"

    //** 移除由启动独立 eBPF 桥接的版本写入的状态 */
    fun clearLegacyBridgeState() {
        runCatching {
            val store = com.tencent.mmkv.MMKV.mmkvWithID(STORE_ID, com.tencent.mmkv.MMKV.MULTI_PROCESS_MODE)
            store.removeValueForKey("bridge_pid")
            store.removeValueForKey("bridge_start_time_ticks")
            store.removeValueForKey("bridge_cgroup_path")
        }
    }
}
