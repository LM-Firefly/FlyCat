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
 * 生成mihomo核心使用的eBPF监听器覆盖YAML。
 *
 * 当应用进入eBPF模式时，基础eBPF监听器（`模式：本地`，cgroup数据平面）**始终**被注入，这是拦截本地流量的机制。
 * 当启用 `bypassCn` 时，会附加一个可选的中国绕过规则提供者。
 *
 * 原生eBPF监听器是mihomo进程本身的一部分——无独立桥接。
 */
object EbpfOverride {
    const val FILE_NAME = "__ebpf_override__.yaml"
    private const val LISTENER_NAME = "ebpf-in"
    private const val CN_PROVIDER_NAME = "CN-IP"
    private const val CN_PROVIDER_URL =
        "https://github.com/MetaCubeX/meta-rules-dat/raw/meta/geo/geoip/cn.mrs"
    /**
     * @param bypassCn 是否要附加一个国内IP绕过规则提供者
     * @param includeUid 要专门拦截的UID列表（AcceptSelected）；留空表示未设置
     * @param excludeUid 要跳过的UID列表（RejectSelected）；留空表示未设置
    */
    data class Config(
        val bypassCn: Boolean,
        val includeUid: List<Int> = emptyList(),
        val excludeUid: List<Int> = emptyList(),
    )
    fun buildYaml(config: Config, providerPath: String?): String {
        val localSection = linkedMapOf<String, Any?>(
            "data-plane" to "cgroup",
            "dns-mode" to "hijack",
            "ipv6" to true,
            "bypass-private-address" to true,
        )
        if (config.includeUid.isNotEmpty()) {
            localSection["include-uid"] = config.includeUid
        } else if (config.excludeUid.isNotEmpty()) {
            localSection["exclude-uid"] = config.excludeUid
        }
        val listener = linkedMapOf<String, Any?>(
            "name" to LISTENER_NAME,
            "type" to "ebpf",
            "mode" to "local",
            "network" to listOf("tcp", "udp"),
            "local" to localSection,
        )
        if (config.bypassCn) {
            listener["bypass-rule-set"] = listOf(CN_PROVIDER_NAME)
        }
        val override = linkedMapOf<String, Any?>(
            "listeners+" to listOf(listener),
        )
        if (config.bypassCn && providerPath != null) {
            override["rule-providers-merge"] = linkedMapOf(
                CN_PROVIDER_NAME to linkedMapOf<String, Any?>(
                    "type" to "http",
                    "behavior" to "ipcidr",
                    "format" to "mrs",
                    "interval" to 86400,
                    "path" to providerPath,
                    "url" to CN_PROVIDER_URL,
                ),
            )
        }
        return YamlCodec.dumpMap(override)
    }
    /** 始终返回非空的 [OverrideSpec] —— 在eBPF模式下，eBPF监听器是必需的。 */
    fun materialize(config: Config, dir: File): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        val providerPath = if (config.bypassCn) {
            val providerFile = File(dir, "rule_provider/cn-ip.mrs")
            providerFile.parentFile?.mkdirs()
            providerFile.absolutePath
        } else {
            null
        }
        file.writeText(buildYaml(config, providerPath))
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
