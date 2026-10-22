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

package com.github.lmfirefly.flycat.runtime.client.internal

import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 按代理名保存实时延迟值。节点行只订阅自己的 StateFlow：批量测速的结果仅更新受影响的延迟徽标，不再需要为每个结果替换整个代理组列表（避免全列表重组与 BY_LATENCY 重排）。 */
/** 语义与 [ProxyGroupManager] 的延迟缓存一致：0（未测/无效）永不覆盖已有测量值，防止测试期间到达的旧快照把测得的延迟清零。 */
internal class ProxyDelayStore {
    private val delays = ConcurrentHashMap<String, MutableStateFlow<Int?>>()
    fun delayFlow(name: String): StateFlow<Int?> = delays.computeIfAbsent(name) { MutableStateFlow(null) }
    /** 单点测量即时落库（测得即显示，不必等待组快照刷新）。 */
    fun applyMeasured(name: String, delay: Int) {
        if (name.isBlank() || delay == 0) return
        set(name, delay)
    }
    /** 组快照发布时对齐：以快照值为准，并清理已从组里消失的节点。 */
    fun publish(groups: List<ProxyGroupInfo>) {
        val seen = HashSet<String>()
        groups.asSequence()
            .flatMap { group -> group.proxies.asSequence() }
            .forEach { proxy ->
                if (proxy.name.isBlank()) return@forEach
                seen += proxy.name
                /** 0 不覆盖已有值（同延迟缓存语义）；负值（超时）是真实状态，正常更新。 */
                if (proxy.delay != 0) {
                    set(proxy.name, proxy.delay)
                } else {
                    delays.computeIfAbsent(proxy.name) { MutableStateFlow(null) }
                }
            }
        delays.keys.removeAll { name -> name !in seen }
    }
    fun clear() {
        delays.values.forEach { flow -> flow.value = null }
        delays.clear()
    }
    private fun set(name: String, delay: Int) {
        delays.computeIfAbsent(name) { MutableStateFlow(null) }.value = delay
    }
}
