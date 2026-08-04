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

package com.github.yumeyucca.yumebox.runtime.client.session

import com.github.yumeyucca.yumebox.core.model.Proxy
import com.github.yumeyucca.yumebox.core.model.ProxyGroup
import com.github.yumeyucca.yumebox.core.model.isProxyGroup
import com.github.yumeyucca.yumebox.domain.model.ProxyGroupInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Proxy-group view state extracted from `ProxyFacade`: caches the last published group list,
 * dedupes republishes by content summary, and resolves the primary end node by walking the
 * selection chain. Publishing also reports readiness back so the runtime snapshot can track it.
 */
internal class ProxyGroupStore(
    private val isRuntimeRunning: () -> Boolean,
    private val onGroupsReady: (Boolean) -> Unit,
) {
    private val _groups = MutableStateFlow<List<ProxyGroupInfo>>(emptyList())
    val groups: StateFlow<List<ProxyGroupInfo>> = _groups.asStateFlow()

    private val _resolvedPrimaryNode = MutableStateFlow<Proxy?>(null)
    val resolvedPrimaryNode: StateFlow<Proxy?> = _resolvedPrimaryNode.asStateFlow()

    private var lastSummary: String? = null

    fun toInfo(group: ProxyGroup): ProxyGroupInfo =
        ProxyGroupInfo(
            name = group.name,
            type = group.type,
            proxies = group.proxies,
            now = group.now.trim(),
            icon = group.icon,
            hidden = group.hidden,
        )

    fun publish(groups: List<ProxyGroupInfo>) {
        val summary = summarize(groups)
        if (summary != lastSummary) {
            _groups.value = groups
            lastSummary = summary
        }
        onGroupsReady(groups.isNotEmpty())
        updateResolvedPrimaryNode(groups)
    }

    fun upsert(updated: ProxyGroupInfo): List<ProxyGroupInfo> {
        val currentGroups = _groups.value
        if (currentGroups.isEmpty()) return listOf(updated)
        if (currentGroups.none { it.name == updated.name }) {
            return currentGroups + updated
        }
        return currentGroups.map { group -> if (group.name == updated.name) updated else group }
    }

    fun clear(resetGroups: Boolean) {
        if (resetGroups) {
            _groups.value = emptyList()
            lastSummary = null
        }
        _resolvedPrimaryNode.value = null
    }

    private fun summarize(groups: List<ProxyGroupInfo>): String =
        groups.joinToString(separator = "\n") { group ->
            buildString {
                append(group.name)
                append('|')
                append(group.type)
                append('|')
                append(group.now)
                append('|')
                append(group.hidden)
                append('|')
                append(group.proxies.size)
                group.proxies.forEach { proxy ->
                    append('|')
                    append(proxy.name)
                    append(':')
                    append(proxy.type)
                    append(':')
                    append(proxy.isGroup)
                    append(':')
                    append(proxy.delay)
                }
            }
        }

    private fun updateResolvedPrimaryNode(groups: List<ProxyGroupInfo>) {
        if (!isRuntimeRunning() || groups.isEmpty()) {
            _resolvedPrimaryNode.value = null
            return
        }
        val mainGroup =
            groups.find { it.name.equals("Proxy", ignoreCase = true) } ?: groups.firstOrNull()
        val targetNode = mainGroup?.now?.trim().orEmpty()
        _resolvedPrimaryNode.value =
            targetNode.takeIf(String::isNotEmpty)?.let { resolveProxyNode(it, groups) }
    }

    private fun resolveProxyNode(
        nodeName: String,
        groups: List<ProxyGroupInfo>,
        visited: MutableSet<String> = linkedSetOf(),
    ): Proxy? {
        if (!visited.add(nodeName)) {
            return null
        }

        val group = groups.firstOrNull { it.name == nodeName }
        if (group != null) {
            val groupNow = group.now.trim()
            return groupNow
                .takeIf { it.isNotEmpty() }
                ?.let { resolveProxyNode(it, groups, visited) }
        }

        groups.forEach { proxyGroup ->
            val proxy = proxyGroup.proxies.firstOrNull { it.name == nodeName } ?: return@forEach
            if (proxy.isProxyGroup) {
                val nextGroup = groups.firstOrNull { it.name == proxy.name } ?: return null
                val nextNode = nextGroup.now.trim()
                return nextNode
                    .takeIf { it.isNotEmpty() }
                    ?.let { resolveProxyNode(it, groups, visited) }
            }
            return proxy
        }
        return null
    }
}
