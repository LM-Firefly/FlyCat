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

@file:Suppress("UnusedSymbol")

package com.github.yumeyucca.yumebox.domain.model


import com.github.yumeyucca.yumebox.core.model.Proxy
import com.github.yumeyucca.yumebox.core.model.isManuallySelectable
import com.github.yumeyucca.yumebox.core.model.isProxyGroup
import kotlinx.serialization.Serializable

@Serializable
data class ProxyGroupInfo(
    val name: String,
    val type: String,
    val proxies: List<Proxy>,
    val now: String,
    val icon: String? = null,
    val hidden: Boolean = false,
)

val ProxyGroupInfo.isSelectable: Boolean
    get() = type.isManuallySelectable

val ProxyGroupInfo.isProxyGroup: Boolean
    get() = type in Proxy.Type.groupTypes || now.isNotBlank() || proxies.isNotEmpty()

/** Resolves a selected proxy-group entry to the terminal (non-group) proxy. */
fun List<ProxyGroupInfo>.resolveTerminalProxy(entryName: String): Proxy? {
    fun findGroup(name: String): ProxyGroupInfo? =
        firstOrNull { it.name == name } ?: firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun findProxy(name: String): Proxy? =
        asSequence()
            .flatMap { it.proxies.asSequence() }
            .firstOrNull { it.name == name }
            ?: asSequence()
                .flatMap { it.proxies.asSequence() }
                .firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun resolve(name: String, visited: MutableSet<String>): Proxy? {
        val normalized = name.trim()
        if (normalized.isEmpty() || !visited.add(normalized.lowercase())) return null

        findGroup(normalized)?.let { group ->
            return resolve(group.now, visited)
        }

        val proxy = findProxy(normalized) ?: return null
        val nestedGroup = findGroup(proxy.name)
        if (proxy.isProxyGroup || nestedGroup != null) {
            nestedGroup?.let { group ->
                return resolve(group.now, visited) ?: proxy
            }
        }
        return proxy
    }

    return resolve(entryName, linkedSetOf())
}
