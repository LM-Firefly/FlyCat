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

package com.github.lmfirefly.flycat.runtime.service.session

import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroup
import com.github.lmfirefly.flycat.core.model.proxy.ProxySort
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec
import com.github.lmfirefly.flycat.runtime.service.session.spec.CompiledConfigPipeline
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 解析UI中显示的代理组列表。
 * 当会话正在运行时，运行中的核心本身是代理组**顺序 + 成员资格 + 实时状态**的唯一权威来源：mihomo分叉保留`proxy-groups:`声明的顺序 （`config/config.go`），原生的`QueryProxyGroupNames`严格按照该声明顺序遍历GLOBAL提供商。
 * 由于rust覆盖路径现在已正确加载rawConfig，运行中的核心已能给出正确的顺序并附带实时的`now` / `proxies`，因此在运行期间进行规范化重新编译是多余的。
 * [canonicalGroups]（已编译的rawConfig，`proxy-groups:`声明顺序）仅作为预览来源保留，并作为核心加载完成前的瞬态窗口的回退方案。
 */
class RuntimeProxyGroupResolver(private val compiledConfigPipeline: CompiledConfigPipeline) {
    private companion object {
        /** 空编译结果的负缓存时长：防止坏配置/启动瞬态下每次刷新都重跑全量编译。 */
        const val NEGATIVE_CACHE_TTL_MS = 5_000L
    }
    /** 编译是阻塞 JNI 且不可中断，单飞互斥避免并发编译堆积饿死 CPU。 */
    private val compileMutex = Mutex()
    private val expectedNameCacheLock = Any()
    private var expectedNameCache: ExpectedGroupCache? = null
    private var expectedNameNegative: ExpectedGroupNegative? = null
    private val canonicalCacheLock = Any()
    private var canonicalCache: CanonicalGroupCache? = null
    private var canonicalNegative: CanonicalGroupNegative? = null
    /**
     * 权威的有序代理组列表，直接来自编译后的 rawConfig（`proxy-groups:`）。
     * 始终通过 [CompiledConfigPipeline.previewGroups] 从新编译中重建，因此它永远不会读取磁盘上过期的 runtime.yaml；由 [CanonicalGroupKey] 缓存，因此重新编译仅在配置/覆盖集实际发生变化（指纹翻转）时才会触发，而不是每次刷新都触发。
     * 空结果不会被正向缓存：启动窗口期间的瞬时空编译不得污染会话其余部分的缓存。相反，它被保存在短命的负缓存中，这样重复刷新就不会堆积无法编译的重复项。
     */
    suspend fun canonicalGroups(spec: RuntimeSpec, excludeNotSelectable: Boolean): List<ProxyGroup> {
        val cacheKey = CanonicalGroupKey(
            profileUuid = spec.profileUuid,
            effectiveFingerprint = spec.effectiveFingerprint,
            excludeNotSelectable = excludeNotSelectable,
            ageSecretKeyFingerprint = sha256Short(spec.ageSecretKey),
        )
        synchronized(canonicalCacheLock) {
            canonicalCache
                ?.takeIf { it.key == cacheKey }
                ?.let {
                    return it.groups
                }
            canonicalNegative
                ?.takeIf { it.key == cacheKey && it.isFresh() }
                ?.let {
                    return emptyList()
                }
        }
        return compileMutex.withLock {
            // 双检：排队等锁期间可能已被并发调用者填充。
            synchronized(canonicalCacheLock) {
                canonicalCache?.takeIf { it.key == cacheKey }?.let { return@withLock it.groups }
                canonicalNegative
                    ?.takeIf { it.key == cacheKey && it.isFresh() }
                    ?.let { return@withLock emptyList() }
            }
            val groups = runCatching { compiledConfigPipeline.previewGroups(spec, excludeNotSelectable) }.getOrDefault(emptyList()).filter { it.name.isNotBlank() }
            synchronized(canonicalCacheLock) {
                if (groups.isNotEmpty()) {
                    canonicalCache = CanonicalGroupCache(cacheKey, groups)
                    canonicalNegative = null
                } else {
                    canonicalNegative = CanonicalGroupNegative(cacheKey, System.currentTimeMillis())
                }
            }
            groups
        }
    }
    suspend fun expectedGroupNames(spec: RuntimeSpec, excludeNotSelectable: Boolean): List<String> {
        val cacheKey = ExpectedGroupKey(
            profileUuid = spec.profileUuid,
            effectiveFingerprint = spec.effectiveFingerprint,
            excludeNotSelectable = excludeNotSelectable,
            ageSecretKeyFingerprint = sha256Short(spec.ageSecretKey),
        )
        synchronized(expectedNameCacheLock) {
            expectedNameCache
                ?.takeIf { it.key == cacheKey }
                ?.let {
                    return it.names
                }
            expectedNameNegative
                ?.takeIf { it.key == cacheKey && it.isFresh() }
                ?.let {
                    return emptyList()
                }
        }
        return compileMutex.withLock {
            // 双检：排队等锁期间可能已被并发调用者填充。
            synchronized(expectedNameCacheLock) {
                expectedNameCache?.takeIf { it.key == cacheKey }?.let { return@withLock it.names }
                expectedNameNegative
                    ?.takeIf { it.key == cacheKey && it.isFresh() }
                    ?.let { return@withLock emptyList() }
            }
            // 所有配置文件（加密和非加密的）都会经过原生内存编译，因此预期的名称从不依赖于磁盘上的 runtime.yaml。
            val names = compiledConfigPipeline.previewGroupNames(spec, excludeNotSelectable)
            // 永远不要将空结果缓存为有效结果：启动窗口期间的瞬时空编译不得将整个会话锁定为空的预期名称集。
            synchronized(expectedNameCacheLock) {
                if (names.isNotEmpty()) {
                    expectedNameCache = ExpectedGroupCache(cacheKey, names)
                    expectedNameNegative = null
                } else {
                    expectedNameNegative =
                        ExpectedGroupNegative(cacheKey, System.currentTimeMillis())
                }
            }
            names
        }
    }

    fun runtimeGroupNames(excludeNotSelectable: Boolean): List<String> =
        Clash.queryGroupNames(excludeNotSelectable)

    suspend fun resolvedGroupNames(
        spec: RuntimeSpec?,
        excludeNotSelectable: Boolean,
    ): List<String> {
        // Running core is the source of truth: GLOBAL provider order == proxy-groups declaration
        // order.
        val coreNames = runtimeGroupNames(excludeNotSelectable).filter(String::isNotBlank)
        if (coreNames.isNotEmpty()) {
            return coreNames
        }
        // Core not loaded yet (preview / transient start window): fall back to the compiled
        // rawConfig.
        return spec
            ?.let { canonicalGroups(it, excludeNotSelectable) }
            .orEmpty()
            .map(ProxyGroup::name)
            .filter(String::isNotBlank)
    }

    /**
     * The ordered, complete group list.
     *
     * @param enrichLive when true (running session) the list is taken straight from the live core
     *   ([coreGroups]): it already carries the `proxy-groups:` declaration order plus real-time
     *   `now` / `proxies`, so no canonical recompile is needed. When the core is not loaded yet
     *   (transient start window) or when [enrichLive] is false (preview / not running), the
     *   compiled rawConfig ([canonicalGroups]) is used instead.
     */
    suspend fun resolvedGroups(
        spec: RuntimeSpec?,
        excludeNotSelectable: Boolean,
        enrichLive: Boolean = true,
    ): List<ProxyGroup> {
        if (enrichLive) {
            val coreGroups = coreGroups(excludeNotSelectable)
            if (coreGroups.isNotEmpty()) {
                return coreGroups
            }
        }
        // Preview / core not loaded yet: use the compiled rawConfig (no running core to read from).
        return spec?.let { canonicalGroups(it, excludeNotSelectable) }.orEmpty()
    }

    /**
     * Groups straight from the live core, in GLOBAL-provider order (== `proxy-groups:` declaration
     * order in the mihomo fork). Each name is resolved to a usable live group; names that cannot be
     * resolved are skipped (they are not part of the live runtime). Returns empty when no core is
     * loaded, which signals the caller to fall back to [canonicalGroups].
     */
    private fun coreGroups(excludeNotSelectable: Boolean): List<ProxyGroup> {
        val names = runtimeGroupNames(excludeNotSelectable)
        if (names.isEmpty()) return emptyList()
        // Batch query: single JNI call replaces N × queryGroup() round-trips.
        val batchResult = Clash.queryGroupsBatch(names, ProxySort.Default)
        val batchByName = batchResult.associateBy { it.name }
        val coreNamesByTrimmed = buildCoreNamesByTrimmed(excludeNotSelectable)
        return names.mapNotNull { name ->
            // Try exact match from batch result first.
            batchByName[name]?.takeIf(::isUsable)
                // Retry with the core's actual (untrimmed) key for this trimmed name.
                ?: run {
                    val actualKey = coreNamesByTrimmed[name.trim()]
                    if (actualKey != null && actualKey != name) {
                        batchByName[actualKey]?.takeIf(::isUsable)
                    } else null
                }
        }
    }

    private fun buildCoreNamesByTrimmed(excludeNotSelectable: Boolean): Map<String, String> {
        val coreNames = runtimeGroupNames(excludeNotSelectable)
        val lookup = HashMap<String, String>(coreNames.size)
        coreNames.forEach { coreName ->
            val key = coreName.trim()
            if (key.isEmpty()) return@forEach
            // Prefer an exact (already-trimmed) registration; only fill from an untrimmed twin when
            // no exact key has been recorded for this trimmed name.
            val existing = lookup[key]
            if (existing == null || (existing != key && coreName == key)) {
                lookup[key] = coreName
            }
        }
        return lookup
    }

    fun isUsable(group: ProxyGroup): Boolean {
        if (group.name.isBlank()) {
            return false
        }
        return group.type != Proxy.Type.Unknown ||
            group.proxies.isNotEmpty() ||
            group.now.isNotBlank() ||
            !group.icon.isNullOrBlank()
    }

    /**
     * Short SHA-256 hash of the age secret key. The raw key is NEVER stored in the cache key; only
     * this hash is. Returns the literal "none" when the profile is not age-encrypted so a
     * null/non-null transition still invalidates the caches.
     */
    private fun sha256Short(value: String?): String {
        if (value == null) return "none"
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private data class ExpectedGroupKey(
        val profileUuid: String,
        val effectiveFingerprint: String,
        val excludeNotSelectable: Boolean,
        val ageSecretKeyFingerprint: String,
    )

    private data class ExpectedGroupCache(
        val key: ExpectedGroupKey,
        val names: List<String>,
    )

    private data class ExpectedGroupNegative(
        val key: ExpectedGroupKey,
        val at: Long,
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - at <= NEGATIVE_CACHE_TTL_MS
    }

    private data class CanonicalGroupKey(
        val profileUuid: String,
        val effectiveFingerprint: String,
        val excludeNotSelectable: Boolean,
        val ageSecretKeyFingerprint: String,
    )

    private data class CanonicalGroupCache(
        val key: CanonicalGroupKey,
        val groups: List<ProxyGroup>,
    )

    private data class CanonicalGroupNegative(
        val key: CanonicalGroupKey,
        val at: Long,
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - at <= NEGATIVE_CACHE_TTL_MS
    }
}
