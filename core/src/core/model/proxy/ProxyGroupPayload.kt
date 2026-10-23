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

package com.github.lmfirefly.flycat.core.model.proxy

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 代理组跨边界载荷编解码（JNI 原生 ↔ JVM、root 进程 ↔ 应用进程 binder 共用）。
 *
 * 原生端发出紧凑的分隔符格式（组 \u0003，代理 \u0002，字段 \u0001），避免对该最大载荷做
 * json.Marshal/Unmarshal 的反射与转义；JSON 保留作为旧版原生构建的回退方案。判别依据是
 * 裸 U+0001 是否存在——JSON 文本中的控制字符只会以 `\u0001` 转义序列出现，不会是裸字符。
 */
object ProxyGroupPayload {
    private const val GROUP_SEP = '\u0003'
    private const val PROXY_SEP = '\u0002'
    private const val FIELD_SEP = '\u0001'
    private val PayloadJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    /** 解码单组载荷：分隔符（新原生/root 透传）或单个 JSON 对象（旧原生回退）。 */
    fun decodeGroup(payload: String): ProxyGroup {
        if (payload.indexOf(FIELD_SEP) >= 0) {
            decodeRecord(payload)?.let { return it }
        }
        return PayloadJson.decodeFromString(ProxyGroup.serializer(), payload)
    }
    /** 解码批量载荷：分隔符（新原生/root 透传）或 JSON 数组（旧原生回退）。 */
    fun decodeGroups(payload: String): List<ProxyGroup> {
        if (payload.isEmpty()) return emptyList()
        if (payload.indexOf(FIELD_SEP) < 0) {
            return PayloadJson.decodeFromString(ListSerializer(ProxyGroup.serializer()), payload)
        }
        val result = ArrayList<ProxyGroup>()
        for (g in payload.split(GROUP_SEP)) {
            decodeRecord(g)?.let(result::add)
        }
        return result
    }
    /** 将单组编码为分隔符载荷（布局与原生端一致，供 root 进程 re-encode 透传）。 */
    fun encode(group: ProxyGroup): String = encode(listOf(group))
    /** 将代理组列表编码为分隔符载荷，替代 kotlinx JSON 重序列化（root binder 路径）。 */
    fun encode(groups: List<ProxyGroup>): String {
        val sb = StringBuilder()
        groups.forEachIndexed { gi, g ->
            if (gi > 0) sb.append(GROUP_SEP)
            sb.append(g.name).append(FIELD_SEP)
            sb.append(g.type).append(FIELD_SEP)
            sb.append(g.now).append(FIELD_SEP)
            sb.append(g.icon.orEmpty()).append(FIELD_SEP)
            sb.append(if (g.hidden) '1' else '0').append(FIELD_SEP)
            sb.append(g.fixed).append(FIELD_SEP)
            g.proxies.forEachIndexed { pi, p ->
                if (pi > 0) sb.append(PROXY_SEP)
                sb.append(p.name).append(FIELD_SEP)
                sb.append(p.title).append(FIELD_SEP)
                sb.append(p.subtitle).append(FIELD_SEP)
                sb.append(p.type).append(FIELD_SEP)
                sb.append(p.delay).append(FIELD_SEP)
                sb.append(if (p.isGroup) '1' else '0')
            }
        }
        return sb.toString()
    }
    /** 解码单条组记录；字段数不足（损坏载荷）返回 null。 */
    private fun decodeRecord(g: String): ProxyGroup? {
        val gf = g.split(FIELD_SEP, limit = 7)
        if (gf.size != 7) return null
        val proxies = ArrayList<Proxy>()
        if (gf[6].isNotEmpty()) {
            for (p in gf[6].split(PROXY_SEP)) {
                val pf = p.split(FIELD_SEP, limit = 6)
                if (pf.size != 6) continue
                proxies.add(
                    Proxy(
                        name = pf[0], title = pf[1], subtitle = pf[2],
                        type = pf[3], delay = pf[4].toIntOrNull() ?: 0, isGroup = pf[5] == "1",
                    )
                )
            }
        }
        return ProxyGroup(
            name = gf[0], type = gf[1], proxies = proxies, now = gf[2],
            icon = gf[3].ifEmpty { null }, hidden = gf[4] == "1", fixed = gf[5],
        )
    }
}
