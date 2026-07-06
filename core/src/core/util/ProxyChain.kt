package com.github.yumelira.yumebox.core.util

import com.github.yumelira.yumebox.core.model.ProxyGroupInfo

class ProxyChainResolver {
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
}

fun resolveProxyChainOrder(
    chain: List<String>,
    reverse: Boolean = true,
): List<String> {
    val normalized = chain.filter { it.isNotBlank() }
    return if (reverse) normalized.asReversed() else normalized
}

fun buildRuleChain(
    rule: String,
    chain: List<String>,
    reverse: Boolean = true,
): List<String> {
    val orderedChain = resolveProxyChainOrder(chain, reverse)
    return buildList {
        if (rule.isNotBlank()) add(rule)
        addAll(orderedChain)
    }
}

fun formatProxyChain(
    parts: List<String>,
    separator: String = " -> ",
): String {
    return parts.filter { it.isNotBlank() }.joinToString(separator)
}
