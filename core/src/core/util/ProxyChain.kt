package com.github.lmfirefly.flycat.core.util

import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo

object ProxyChainResolver {
    /** Walks the proxy-group hierarchy from [startNodeName] and returns the resolved chain path. */
    fun buildChainPath(startNodeName: String, groups: List<ProxyGroupInfo>): List<String> {
        val path = mutableListOf<String>()
        buildChainPathRecursive(startNodeName, groups, mutableSetOf(), path)
        return path
    }

    private fun buildChainPathRecursive(
        proxyName: String,
        groups: List<ProxyGroupInfo>,
        visited: MutableSet<String>,
        path: MutableList<String>,
    ) {
        if (proxyName in visited) return
        visited.add(proxyName)
        path.add(proxyName)
        val asGroup = groups.find { it.name == proxyName }
        if (asGroup != null && asGroup.now.isNotBlank()) {
            buildChainPathRecursive(asGroup.now.trim(), groups, visited, path)
        }
    }

    /** Reverses a resolved chain for display (leaf-first → root-first by default). */
    fun resolveProxyChainOrder(chain: List<String>, reverse: Boolean = true): List<String> {
        val normalized = chain.filter { it.isNotBlank() }
        return if (reverse) normalized.asReversed() else normalized
    }

    /** Prepends [rule] to the reversed chain for connection-detail display. */
    fun buildRuleChain(rule: String, chain: List<String>, reverse: Boolean = true): List<String> {
        val orderedChain = resolveProxyChainOrder(chain, reverse)
        return buildList {
            if (rule.isNotBlank()) add(rule)
            addAll(orderedChain)
        }
    }

    /** Joins chain parts into a human-readable string. */
    fun formatProxyChain(parts: List<String>, separator: String = " -> "): String {
        return parts.filter { it.isNotBlank() }.joinToString(separator)
    }
}
