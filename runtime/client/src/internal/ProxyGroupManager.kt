package com.github.lmfirefly.flycat.runtime.client.internal

import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.bridge.Bridge
import com.github.lmfirefly.flycat.core.contract.ProxySelectTimeoutException
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroup
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.core.model.proxy.ProxySort
import com.github.lmfirefly.flycat.core.util.PollingTimerSpecs
import com.github.lmfirefly.flycat.core.util.ProxyChainResolver
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimePhase
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.client.RuntimeBackendRouter
import com.github.lmfirefly.flycat.runtime.client.remote.ServiceClient
import com.github.lmfirefly.flycat.runtime.client.root.RootTunController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal class ProxyGroupManager(
    private val onProxyGroupsPublished: (List<ProxyGroupInfo>) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val router: RuntimeBackendRouter? = null,
    private val appContext: android.content.Context? = null,
    private val snapshotProvider: () -> RuntimeSnapshot = { RuntimeSnapshot() },
    private val isRootSessionActive: () -> Boolean = { false },
    private val connectCurrentBackend: suspend () -> Unit = {},
    private val onScheduleFullRefresh: (Long) -> Unit = { _ -> },
) {
    private data class DelayCacheEntry(
        val delay: Int,
        val updatedAt: Long,
    )
    private data class PreviewCacheKey(
        val profileId: java.util.UUID,
        val profileUpdatedAt: Long,
        val excludeNotSelectable: Boolean,
        val overrideSignature: String,
    )
    private data class PreviewCacheEntry(val key: PreviewCacheKey, val groups: List<ProxyGroupInfo>)
    private companion object {
        const val PROXY_DELAY_CACHE_TTL_MS = 5 * 60 * 1000L
        const val PROXY_SELECT_FULL_REFRESH_DELAY_MS = 400L
        const val PROXY_GROUP_QUERY_TIMEOUT_MS = 8_000L
        /** 单次 patchSelector 超时。release 非阻塞 + closeConnByGroup 异步 + OnLeave 限流之后，选择路径只剩「改 Selector 内存状态 + cachefile 落盘 + 起后台关连接」，正常毫秒级返回；3s 只兜 cachefile 磁盘抖动 / 残留的原生阻塞。 */
        const val SELECT_TIMEOUT_MS = 3_000L
        /** 含恢复重试在内的总尝试次数：第 1 次阻塞 → 恢复控制链 → 再试 1 次。 */
        const val SELECT_MAX_ATTEMPTS = 2
    }
    private var previewCacheEntry: PreviewCacheEntry? = null
    private var previewProfile: Profile? = null

    fun setPreviewProfile(profile: Profile?) {
        previewProfile = profile
    }
    private val refreshProxyGroupsMutex = Mutex()
    //** 防止并发JNI查询；与互斥锁不同，绝不会阻塞协程。 */
    private val queryLock = AtomicBoolean(false)
    private val proxyDelayCache = ConcurrentHashMap<String, DelayCacheEntry>()
    private var pendingGroupsRefreshJob: Job? = null
    private val pendingGroupRefreshJobs = ConcurrentHashMap<String, Job>()
    private var lastProxyGroupsHash: Int = 0
    private var lastRawGroupsHash: Int = 0
    private var lastProxyGroupVersion = 0L
    private val _proxyGroups = MutableStateFlow<List<ProxyGroupInfo>>(emptyList())
    /** 延迟测试进行中标记——测试期间轮询刷新应跳过，避免覆盖测试结果。 */
    @Volatile
    var isDelayTestActive: Boolean = false
        private set

    fun markDelayTestActive(active: Boolean) {
        isDelayTestActive = active
    }
    val proxyGroups: StateFlow<List<ProxyGroupInfo>> = _proxyGroups.asStateFlow()
    private val _resolvedPrimaryNode = MutableStateFlow<Proxy?>(null)
    val resolvedPrimaryNode: StateFlow<Proxy?> = _resolvedPrimaryNode.asStateFlow()
    suspend fun refreshProxyGroups(
        appContext: android.content.Context,
        snapshot: RuntimeSnapshot,
        isRootSessionActive: () -> Boolean,
        connectCurrentBackend: suspend () -> Unit,
        force: Boolean = false,
    ) {
        refreshProxyGroupsInner(appContext, snapshot, isRootSessionActive, connectCurrentBackend, force)
    }
    private suspend fun refreshProxyGroupsInner(
        appContext: android.content.Context,
        snapshot: RuntimeSnapshot,
        isRootSessionActive: () -> Boolean,
        connectCurrentBackend: suspend () -> Unit,
        force: Boolean,
    ) {
            // 版本门控：若代理分组结构未变更，则跳过昂贵的分组查询。
            // 当强制模式启用时（健康检查、选择器变更、启动时），此机制将被绕过。
            if (!force && snapshot.running && _proxyGroups.value.isNotEmpty()) {
                val version = runCatching { Bridge.nativeQueryProxyGroupVersion() }.getOrDefault(0L)
                if (version == lastProxyGroupVersion) return
                lastProxyGroupVersion = version
            }
            // 获取轻量级查询锁（非阻塞）以防止并发JNI调用。
            // 重量级JNI查询在refreshProxyGroupsMutex外部运行，以确保被阻塞的Go运行时不会饿死由UI触发的刷新或后台定时调度。
            if (!queryLock.compareAndSet(false, true)) {
                Timber.d("refreshProxyGroupsInner: query already in flight, skipping")
                return
            }
            var missingLocalRuntime = false
            val groups = try {
                withAbandonableTimeout(PROXY_GROUP_QUERY_TIMEOUT_MS, "refreshProxyGroupsInner") {
                    withContext(Dispatchers.IO) {
                        try {
                            if (!snapshot.running) {
                                return@withContext queryPreviewProxyGroups(appContext, connectCurrentBackend)
                            }
                            if (snapshot.owner == RuntimeOwner.RootTun && !isRootSessionActive()) {
                                error("RootTun runtime not ready")
                            }
                            if (snapshot.owner == RuntimeOwner.RootTun) {
                                RootTunController.queryAllProxyGroups(
                                        context = appContext,
                                        excludeNotSelectable = false,
                                    )
                                    .let(::toProxyGroupInfos)
                            } else {
                                connectCurrentBackend()
                                ServiceClient.clash()
                                    .queryAllProxyGroups(excludeNotSelectable = false)
                                    .let(::toProxyGroupInfos)
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (error: Exception) {
                            Timber.e(error, "Failed to refresh proxy groups")
                            missingLocalRuntime = snapshot.owner != RuntimeOwner.RootTun &&
                                snapshot.owner != RuntimeOwner.None &&
                                snapshot.owner != RuntimeOwner.RemoteController
                            null
                        }
                    }
                }
            } finally {
                // 必须在外层 finally 释放：withAbandonableTimeout 超时会遗弃内层 job，若把释放放在 job 内，阻塞的 JNI 不返回则 queryLock 永久卡死（日志表现为 "query already in flight, skipping" 循环）。
                queryLock.set(false)
            }
        // 状态更新 —— 仅在快速发布路径中持有互斥锁。
        refreshProxyGroupsMutex.withLock {
            if (groups != null && (groups.isNotEmpty() || snapshot.running)) {
                publishProxyGroups(groups, cacheForPreview = true)
            } else if (!snapshot.running) {
                val cached = previewCacheFallback(
                    phase = snapshot.phase,
                    profile = previewProfile,
                    excludeNotSelectable = false,
                    overrideSignature = "",
                )
                if (!cached.isNullOrEmpty()) {
                    publishProxyGroups(cached, cacheForPreview = false)
                }
            }
        }
        // 不再因 missingLocalRuntime 清空代理组缓存——保留旧数据优于显示空白
    }
    /**
     * 用于UI触发刷新的非阻塞变体。
     * 在后台同步持有锁时，使用短暂的超时来获取互斥锁，以避免阻塞UI线程。
     */
    suspend fun refreshProxyGroupsNonBlocking(
        appContext: android.content.Context,
        snapshot: RuntimeSnapshot,
        isRootSessionActive: () -> Boolean,
        connectCurrentBackend: suspend () -> Unit,
    ) {
        val acquired = withTimeoutOrNull(500L) {
            refreshProxyGroupsInner(appContext, snapshot, isRootSessionActive, connectCurrentBackend, force = true)
        }
        if (acquired == null) {
            Timber.d("refreshProxyGroupsNonBlocking: timeout, skipping to avoid UI block")
        }
    }
    /**
     * 用于后台同步的尝试变体。queryLock (AtomicBoolean) 已在 refreshProxyGroupsInner 内部。
     * 防止并发 JNI 调用，mutex 仅保护快速状态更新，无需外部 tryLock 包装。
     */
    suspend fun refreshProxyGroupsTryLock(
        appContext: android.content.Context,
        snapshot: RuntimeSnapshot,
        isRootSessionActive: () -> Boolean,
        connectCurrentBackend: suspend () -> Unit,
    ) {
        refreshProxyGroupsInner(appContext, snapshot, isRootSessionActive, connectCurrentBackend, force = false)
    }
    suspend fun refreshProxyGroup(
        appContext: android.content.Context,
        name: String,
        sort: ProxySort = ProxySort.Default,
        snapshot: RuntimeSnapshot,
        isRootSessionActive: () -> Boolean,
        connectCurrentBackend: suspend () -> Unit,
    ) {
        if (!snapshot.running) {
            // 即使未运行，若组不在缓存中，也应尝试刷新。
            // 这样可以防止从长时间后台恢复后，过时数据阻塞界面。
            if (_proxyGroups.value.isEmpty() || _proxyGroups.value.none { it.name == name }) {
                refreshProxyGroups(appContext, snapshot, isRootSessionActive, connectCurrentBackend)
            }
            return
        }
        if (!queryLock.compareAndSet(false, true)) {
            Timber.d("refreshProxyGroup: query already in flight, skipping group=%s", name)
            return
        }
        val updatedGroup =
            try {
                withAbandonableTimeout(PROXY_GROUP_QUERY_TIMEOUT_MS, "refreshProxyGroup") {
                    withContext(Dispatchers.IO) {
                        try {
                            if (snapshot.owner == RuntimeOwner.RootTun && !isRootSessionActive()) {
                                error("RootTun runtime not ready")
                            }
                            if (snapshot.owner == RuntimeOwner.RootTun) {
                                toProxyGroupInfo(
                                    RootTunController.queryProxyGroup(appContext, name, sort)
                                )
                            } else {
                                connectCurrentBackend()
                                toProxyGroupInfo(ServiceClient.clash().queryProxyGroup(name, sort))
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (error: Exception) {
                            Timber.e(error, "Failed to refresh proxy group: %s", name)
                            null
                        }
                    }
                }
            } finally {
                // 同 refreshProxyGroupsInner：必须在外层释放，避免阻塞 JNI 导致锁永久持有。
                queryLock.set(false)
            } ?: return
        refreshProxyGroupsMutex.withLock {
            val updatedGroups = attachChainPaths(updateCachedProxyGroup(updatedGroup))
            publishProxyGroups(updatedGroups, cacheForPreview = true)
        }
    }
    /**
     * [refreshProxyGroup] 的非阻塞变体，用于 UI 触发的单组刷新。
     * 如果互斥锁已被持有则跳过，防止 UI 线程阻塞。
     */
    suspend fun refreshProxyGroupNonBlocking(
        appContext: android.content.Context,
        name: String,
        sort: ProxySort = ProxySort.Default,
        snapshot: RuntimeSnapshot,
        isRootSessionActive: () -> Boolean,
        connectCurrentBackend: suspend () -> Unit,
    ) {
        val acquired = withTimeoutOrNull(300L) {
            refreshProxyGroup(appContext, name, sort, snapshot, isRootSessionActive, connectCurrentBackend)
        }
        if (acquired == null) {
            Timber.d("refreshProxyGroupNonBlocking: mutex busy, skipping group=%s", name)
        }
    }
    fun publishProxyGroups(groups: List<ProxyGroupInfo>, cacheForPreview: Boolean) {
        // Quick structural check: skip expensive enrich if raw groups haven't changed.
        val rawHash = hashProxyGroups(groups)
        val normalizedGroups = if (rawHash == lastRawGroupsHash && _proxyGroups.value.isNotEmpty()) {
            _proxyGroups.value
        } else {
            enrichProxyGroupDelays(groups)
        }
        lastRawGroupsHash = rawHash
        val hash = hashProxyGroups(normalizedGroups)
        if (hash != lastProxyGroupsHash) {
            _proxyGroups.value = normalizedGroups
            lastProxyGroupsHash = hash
        }
        if (cacheForPreview && normalizedGroups.isNotEmpty()) {
            previewProfile?.let { profile ->
                previewCacheStore(
                    profile = profile,
                    excludeNotSelectable = false,
                    overrideSignature = "",
                    groups = normalizedGroups,
                )
            }
        }
        onProxyGroupsPublished(normalizedGroups)
    }
    fun updateResolvedPrimaryNode(snapshot: RuntimeSnapshot, groups: List<ProxyGroupInfo>) {
        if (snapshot.phase != RuntimePhase.Running || groups.isEmpty()) {
            _resolvedPrimaryNode.value = null
            return
        }
        val mainGroup = groups.find { it.name.equals("Proxy", ignoreCase = true) } ?: groups.firstOrNull()
        val targetNode = mainGroup?.now?.trim().orEmpty()
        _resolvedPrimaryNode.value = targetNode.takeIf(String::isNotEmpty)?.let { resolveProxyNode(it, groups) }
    }
    fun clearGroups() {
        _proxyGroups.value = emptyList()
        lastProxyGroupsHash = 0
        _resolvedPrimaryNode.value = null
    }
    fun scheduleRuntimeGroupRefresh(
        scope: CoroutineScope,
        groupName: String,
        delayMillis: Long = 0L,
        refreshAction: suspend (String) -> Unit,
    ) {
        if (groupName.isBlank()) return
        pendingGroupRefreshJobs[groupName]?.cancel()
        pendingGroupRefreshJobs[groupName] = scope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            runCatching { refreshAction(groupName) }
                .onFailure { error ->
                    Timber.d(error, "Deferred proxy group refresh skipped: %s", groupName)
                }
            pendingGroupRefreshJobs.remove(groupName)
        }
    }
    fun scheduleRuntimeProxyGroupsRefresh(
        scope: CoroutineScope,
        delayMillis: Long = 0L,
        refreshAction: suspend () -> Unit,
    ) {
        pendingGroupsRefreshJob?.cancel()
        pendingGroupsRefreshJob = scope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            refreshAction()
        }
    }
    private suspend fun queryPreviewProxyGroups(
        appContext: android.content.Context,
        connectCurrentBackend: suspend () -> Unit,
    ): List<ProxyGroupInfo> {
        connectCurrentBackend()
        val groups = ServiceClient.clash()
            .queryProfileProxyGroups(excludeNotSelectable = false)
            .let(::toProxyGroupInfos)
        return groups
    }
    private fun enrichProxyGroupDelays(groups: List<ProxyGroupInfo>): List<ProxyGroupInfo> {
        if (groups.isEmpty()) {
            proxyDelayCache.clear()
            return groups
        }
        val now = System.currentTimeMillis()
        groups.asSequence()
            .flatMap { group -> group.proxies.asSequence() }
            .forEach { proxy ->
                if (proxy.delay != 0) {
                    proxyDelayCache[proxy.name] = DelayCacheEntry(delay = proxy.delay, updatedAt = now)
                }
            }
        val validDelayMap = proxyDelayCache.entries
            .filter { (_, entry) -> now - entry.updatedAt <= PROXY_DELAY_CACHE_TTL_MS }
            .associate { (name, entry) -> name to entry.delay }
        if (validDelayMap.isEmpty()) {
            proxyDelayCache.clear()
            return groups
        }
        proxyDelayCache.keys.removeAll { name -> name !in validDelayMap }
        val groupNowMap = groups.associate { group -> group.name to group.now.trim() }
        return groups.map { group ->
            val enrichedProxies = group.proxies.map { proxy ->
                val effectiveDelay = resolveEffectiveDelay(
                    name = proxy.name,
                    delayMap = validDelayMap,
                    groupNowMap = groupNowMap,
                    visited = mutableSetOf(),
                )
                if (effectiveDelay != null && effectiveDelay != proxy.delay) {
                    proxy.copy(delay = effectiveDelay)
                } else {
                    proxy
                }
            }
            group.copy(proxies = enrichedProxies)
        }
    }
    private fun resolveEffectiveDelay(
        name: String,
        delayMap: Map<String, Int>,
        groupNowMap: Map<String, String>,
        visited: MutableSet<String>,
    ): Int? {
        if (!visited.add(name)) return null
        val selectedChild = groupNowMap[name].orEmpty()
        if (selectedChild.isNotEmpty()) {
            val childDelay = resolveEffectiveDelay(
                name = selectedChild,
                delayMap = delayMap,
                groupNowMap = groupNowMap,
                visited = visited,
            )
            if (childDelay != null && childDelay != 0) {
                return childDelay
            }
        }
        return delayMap[name]?.takeIf { it != 0 }
    }
    private fun toProxyGroupInfo(group: ProxyGroup): ProxyGroupInfo {
        return ProxyGroupInfo(
            name = group.name,
            type = group.type,
            proxies = group.proxies,
            now = group.now.trim(),
            icon = group.icon,
            hidden = group.hidden,
            fixed = group.fixed.trim(),
            chainPath = emptyList(),
        )
    }
    private fun toProxyGroupInfos(groups: List<ProxyGroup>): List<ProxyGroupInfo> {
        return attachChainPaths(groups.map(::toProxyGroupInfo))
    }
    private fun attachChainPaths(groups: List<ProxyGroupInfo>): List<ProxyGroupInfo> {
        if (groups.isEmpty()) return groups
        return groups.map { group ->
            if (group.type !in Proxy.Type.groupTypes || group.now.isBlank()) {
                group.copy(chainPath = emptyList())
            } else {
                group.copy(
                    chainPath = ProxyChainResolver.buildChainPath(group.name, groups),
                )
            }
        }
    }
    private fun updateCachedProxyGroup(updated: ProxyGroupInfo): List<ProxyGroupInfo> {
        val currentGroups = _proxyGroups.value
        if (currentGroups.isEmpty()) return listOf(updated)
        if (currentGroups.none { it.name == updated.name }) {
            return currentGroups + updated
        }
        return currentGroups.map { group -> if (group.name == updated.name) updated else group }
    }
    private fun hashProxyGroups(groups: List<ProxyGroupInfo>): Int {
        var hash = groups.size
        for (group in groups) {
            hash = hash * 31 + group.name.hashCode()
            hash = hash * 31 + group.type.hashCode()
            hash = hash * 31 + group.now.hashCode()
            hash = hash * 31 + group.hidden.hashCode()
            hash = hash * 31 + group.proxies.size
            for (proxy in group.proxies) {
                hash = hash * 31 + proxy.name.hashCode()
                hash = hash * 31 + proxy.type.hashCode()
                hash = hash * 31 + proxy.delay
            }
        }
        return hash
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
            if (proxy.type in Proxy.Type.groupTypes) {
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

    // ── Inlined ProxyGroupPreviewCache ──────────────────────────────────

    private fun previewCacheStore(
        profile: Profile,
        excludeNotSelectable: Boolean,
        overrideSignature: String,
        groups: List<ProxyGroupInfo>,
    ) {
        previewCacheEntry = PreviewCacheEntry(
            key = previewCacheKey(profile, excludeNotSelectable, overrideSignature),
            groups = groups,
        )
    }

    private fun previewCacheFallback(
        phase: RuntimePhase,
        profile: Profile?,
        excludeNotSelectable: Boolean,
        overrideSignature: String,
    ): List<ProxyGroupInfo>? {
        if (phase == RuntimePhase.Running) return null
        val cached = previewCacheEntry ?: return null
        if (profile == null) return cached.groups
        return cached
            .takeIf { it.key == previewCacheKey(profile, excludeNotSelectable, overrideSignature) }
            ?.groups
    }

    private fun previewCacheKey(
        profile: Profile,
        excludeNotSelectable: Boolean,
        overrideSignature: String,
    ): PreviewCacheKey {
        return PreviewCacheKey(
            profileId = profile.uuid,
            profileUpdatedAt = profile.updatedAt,
            excludeNotSelectable = excludeNotSelectable,
            overrideSignature = overrideSignature,
        )
    }

    // ── Inlined ProxyGroupInteraction ───────────────────────────────────

    suspend fun queryProxyGroupNames(excludeNotSelectable: Boolean = false): List<String> {
        return router!!.dispatch(
            onRoot = { RootTunController.queryProxyGroupNames(it, excludeNotSelectable) },
            onLocal = { ServiceClient.clash().queryProxyGroupNames(excludeNotSelectable) },
        )
    }

    suspend fun queryProfileProxyGroups(excludeNotSelectable: Boolean = false): List<ProxyGroup> {
        router!!.ensureLocalConnected()
        return ServiceClient.clash().queryProfileProxyGroups(excludeNotSelectable)
    }

    suspend fun queryProxyGroup(name: String, sort: ProxySort = ProxySort.Default): ProxyGroup {
        return router!!.dispatch(
            onRoot = { RootTunController.queryProxyGroup(it, name, sort) },
            onLocal = { ServiceClient.clash().queryProxyGroup(name, sort) },
        )
    }

    suspend fun selectProxy(group: String, proxyName: String): Boolean {
        Timber.d("Select proxy: group=$group proxy=$proxyName")
        val ok = dispatchSelectWithRecovery("selectProxy") {
            router!!.dispatch(
                requireRunning = true,
                defaultIfNotRunning = { false },
                onRoot = { RootTunController.patchSelector(it, group, proxyName) },
                onLocal = { ServiceClient.clash().patchSelector(group, proxyName) },
            )
        }
        if (ok) {
            // 乐观局部更新以使界面立即反映更改，即使延迟刷新被过时的互斥锁阻塞。
            applyLocalForceSelection(group = group, proxyName = proxyName)
        }
        // 无论成功失败都调度刷新——配置文件切换后节点名可能已变，失败时也需要刷新以同步 UI 到实际后端状态。
        scope.launch {
            runCatching {
                delay(if (ok) 200L else 0L)
                refreshGroupDirect(group, ProxySort.Default)
                onScheduleFullRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
            }
        }
        return ok
    }

    suspend fun forceSelectProxy(group: String, proxyName: String): Boolean {
        Timber.d("Force select proxy: group=$group proxy=$proxyName")
        val ok = dispatchSelectWithRecovery("forceSelectProxy") {
            router!!.dispatch(
                requireRunning = true,
                defaultIfNotRunning = { false },
                onRoot = { RootTunController.patchForceSelector(it, group, proxyName) },
                onLocal = { ServiceClient.clash().patchForceSelector(group, proxyName) },
            )
        }
        if (ok) {
            applyLocalForceSelection(group = group, proxyName = proxyName)
            scope.launch {
                runCatching {
                    // 与 selectProxy 相同的宽限期，避免刷新过快覆盖乐观选择导致 UI 回跳
                    delay(200L)
                    refreshGroupDirect(group, ProxySort.Default)
                    onScheduleFullRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
                }
            }
        }
        return ok
    }

    /**
     * 带超时与控制链恢复的选择调用。
     * 返回值是内核的真实结果：`true` 切换成功；`false` 表示内核拒绝（如节点不存在），不重试。
     * 本次尝试超时或抛错时，先恢复控制链再试；全部尝试仍被阻塞则抛 [ProxySelectTimeoutException]，供 UI 区分「切换超时」与「切换失败」。
     *
     * 关于「100% 可达」：已经进入原生层的 CGO 调用无法从 Kotlin 取消。超时只是遗弃该调用（见 [withAbandonableTimeout]），新发起的调用会在另一个 IO 线程独立完成，因此「超时 → 恢复 → 重试」在实践中可做到几乎必达。真正把原生层打死只剩进程级死锁，那需要重启内核进程，不宜由单次切节点触发。
     */
    private suspend fun dispatchSelectWithRecovery(label: String, block: suspend () -> Boolean): Boolean {
        repeat(SELECT_MAX_ATTEMPTS) { attempt ->
            val result = withAbandonableTimeout(SELECT_TIMEOUT_MS, label, block)
            when (result) {
                true -> return true
                false -> return false
                null -> {
                    Timber.w("%s attempt %d blocked or failed, recovering JNI control chain", label, attempt + 1)
                    recoverControlChain()
                }
            }
        }
        Timber.e("%s blocked on all %d attempts", label, SELECT_MAX_ATTEMPTS)
        throw ProxySelectTimeoutException()
    }

    /**
     * 对阻塞型 JNI/CGO 调用真正有效的超时。
     * `withTimeoutOrNull { block() }` 对阻塞 JNI 是无效的：超时只能取消协程，取消不了已经进入原生层的阻塞调用，block() 挂住不返回则超时永不触发，调用方既收不到结果也收不到异常（表现为「点了没反应、没有 toast」，以及 queryLock 永不释放）。
     * 这里把 block 丢进独立 [async]，用 `await()` 接超时：`await()` 可被取消，超时立即返回 `null` 并遗弃该 job（底层阻塞调用会继续跑完，但结果被丢弃；占用一个 IO 线程直到返回）。
     * @return 内核返回值；超时或抛错返回 `null`。
     */
    private suspend fun <T> withAbandonableTimeout(timeoutMs: Long, label: String, block: suspend () -> T): T? {
        val job = scope.async(Dispatchers.IO) { block() }
        return try {
            withTimeoutOrNull(timeoutMs) { job.await() }
        } catch (e: CancellationException) {
            job.cancel()
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "%s abandonable call threw", label)
            null
        }.also { result ->
            if (result == null) {
                Timber.w("%s abandonable call timed out after %dms, abandoning job (native call may still be running)", label, timeoutMs)
                job.cancel()
            }
        }
    }

    /**
     * 重置 UI↔core 控制链的软件层状态，让下一次 patchSelector 走全新 gateway。
     * 原生库与 Go 运行时保持不动（webdashboard 证明 core 健康）；只丢弃 Kotlin 侧可能持有陈旧句柄的 gateway 包装。
     * release 非阻塞 + closeConnByGroup 异步 + OnLeave 限流保证了新调用不会再被事件风暴拖住。
     */
    private suspend fun recoverControlChain() {
        val ctx = appContext ?: return
        runCatching {
            ServiceClient.disconnect()
            ServiceClient.connect(ctx)
        }.onFailure { error -> Timber.e(error, "Control chain recovery failed") }
    }

    suspend fun healthCheck(group: String) {
        Timber.d("Health check request: group=%s", group)
        router!!.dispatch(
            onRoot = { RootTunController.healthCheck(it, group) },
            onLocal = { ServiceClient.clash().healthCheck(group) },
        )
        Timber.d("Health check dispatched: group=%s", group)
        // 刷新由调用方（ProxyHealthCheckUseCase）统一调度，此处不再重复安排。
    }

    suspend fun healthCheckAll() {
        Timber.d("Health check all request")
        router!!.dispatch(
            onRoot = { ctx ->
                RootTunController.queryAllProxyGroups(ctx, excludeNotSelectable = false)
                    .map { it.name }
                    .forEach { groupName ->
                        RootTunController.healthCheck(ctx, groupName)
                    }
            },
            onRemote = {
                proxyGroups.value
                    .map { it.name }
                    .forEach { groupName ->
                        ServiceClient.clash().healthCheck(groupName)
                    }
            },
            onLocal = { Clash.healthCheckAll() },
        )
        // 刷新由调用方（ProxyHealthCheckUseCase）统一调度，此处不再重复安排。
    }

    suspend fun healthCheckProxy(group: String, proxyName: String): Int {
        Timber.d("Health check proxy request: group=%s proxy=%s", group, proxyName)
        val delay = router!!.dispatch(
            onRoot = { RootTunController.healthCheckProxy(it, group, proxyName).toIntOrNull() ?: 0 },
            onLocal = { ServiceClient.clash().healthCheckProxy(group, proxyName) },
        )
        Timber.d("Health check proxy done: group=%s proxy=%s delay=%s", group, proxyName, delay)
        // 刷新操作采用“即发即忘”方式，以便调用方能立即返回。
        // 若 refreshProxyGroupsMutex 正被一个卡住的后台同步任务持有，此操作也不会被阻塞。
        scope.launch {
            runCatching {
                refreshGroupDirect(group, ProxySort.Default)
                onScheduleFullRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
            }
        }
        return delay
    }

    private suspend fun refreshGroupDirect(name: String, sort: ProxySort) {
        val ctx = appContext ?: return
        refreshProxyGroup(
            appContext = ctx,
            name = name,
            sort = sort,
            snapshot = snapshotProvider(),
            isRootSessionActive = isRootSessionActive,
            connectCurrentBackend = connectCurrentBackend,
        )
    }

    private suspend fun applyLocalForceSelection(group: String, proxyName: String) {
        val desired = proxyName.trim()
        val currentGroups = _proxyGroups.value
        if (currentGroups.isEmpty()) return
        val updatedGroups = currentGroups.map { info ->
            if (info.name != group) return@map info
            val nextNow = if (desired.isNotEmpty()) desired else info.now.trim()
            info.copy(now = nextNow, fixed = desired)
        }
        publishProxyGroups(attachChainPaths(updatedGroups), cacheForPreview = true)
    }
}
