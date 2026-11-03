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

package com.github.lmfirefly.flycat.core.model.tunnel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 在 UI、运行时、启动器、所有者和编译器之间共享的单一代理运行模式密钥。
 * [VpnService]为非root Android VpnService 路径； [Tun] 和 [Ebpf] 是 root libsu-daemon 路径。
 * 序列化名称与内核 `--mode` 参数、编译器的 `run_mode` 保持同一约定。
 */
@Serializable
enum class RunMode {
    @SerialName("vpn") VpnService,
    @SerialName("tun") Tun,
    @SerialName("ebpf") Ebpf;
    /** 此模式映射到原生内核的 `--mode` 标志和持久守护进程记录的令牌。 */
    val coreArg: String get() = when (this) {
        VpnService -> "vpn"
        Tun -> "tun"
        Ebpf -> "ebpf"
    }
    companion object {
        fun fromCoreArg(value: String?): RunMode? = when (value) {
            "vpn" -> VpnService
            "tun" -> Tun
            "ebpf" -> Ebpf
            else -> null
        }
    }
}
