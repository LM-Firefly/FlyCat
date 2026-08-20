package com.github.lmfirefly.flycat.runtime.client.internal

import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.bridge.Bridge
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    }
    private var previewCacheEntry: PreviewCacheEntry? = null
    private var previewProfile: Profile? = null

    fun setPreviewProfile(profile: Profile?) {
        previewProfile = profile
    }
    private val refreshProxyGroupsMutex = Mutex()
    private val proxyDelayCache = java.util.concurrent.ConcurrentHashMap<String, DelayCacheEntry>()
    private var pendingGroupsRefreshJob: Job? = null
    private val pendingGroupRefreshJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var lastProxyGroupsHash: Int = 0
    private var lastRawGroupsHash: Int = 0
    private var lastProxyGroupVersion = 0L
    private val _proxyGroups = MutableStateFlow<List<ProxyGroupInfo>>(emptyList())
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
        refreshProxyGroupsMutex.withLock {
            // Version gate: skip expensive group queries if proxy group structure hasn't changed.
            // Bypassed when force=true (health checks, selector changes, startup).
            if (!force && snapshot.running && _proxyGroups.value.isNotEmpty()) {
                val version = runCatching { Bridge.nativeQueryProxyGroupVersion() }.getOrDefault(0L)
                if (version == lastProxyGroupVersion) return
                lastProxyGroupVersion = version
            }
            var missingLocalRuntime = false
            val groups =
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
                        }
                        catch (e: CancellationException) { throw e }
                        catch (error: Exception) {
                            Timber.e(error, "Failed to refresh proxy groups")
                            missingLocalRuntime = snapshot.owner != RuntimeOwner.RootTun &&
                                snapshot.owner != RuntimeOwner.None &&
                                snapshot.owner != RuntimeOwner.RemoteController
                            null
                        }
                }
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
            } else if (missingLocalRuntime) {
                _proxyGroups.value = emptyList()
            }
        }
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
            if (_proxyGroups.value.isEmpty()) {
                refreshProxyGroups(appContext, snapshot, isRootSessionActive, connectCurrentBackend)
            }
            return
        }
        refreshProxyGroupsMutex.withLock {
            val updatedGroup =
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
                        }
                        catch (e: CancellationException) { throw e }
                        catch (error: Exception) {
                            Timber.e(error, "Failed to refresh proxy group: %s", name)
                            null
                        }
                } ?: return
            val updatedGroups = attachChainPaths(updateCachedProxyGroup(updatedGroup))
            publishProxyGroups(updatedGroups, cacheForPreview = true)
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
        val mainGroup =
            groups.find { it.name.equals("Proxy", ignoreCase = true) } ?: groups.firstOrNull()
        val targetNode = mainGroup?.now?.trim().orEmpty()
        _resolvedPrimaryNode.value =
            targetNode.takeIf(String::isNotEmpty)?.let { resolveProxyNode(it, groups) }
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
        val groups =
            ServiceClient.clash()
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
        val ok = router!!.dispatch(
            onRoot = { RootTunController.patchSelector(it, group, proxyName) },
            onLocal = { ServiceClient.clash().patchSelector(group, proxyName) },
        )
        if (ok) {
            delay(200L)
            refreshGroupDirect(group, ProxySort.Default)
            onScheduleFullRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
        }
        return ok
    }

    suspend fun forceSelectProxy(group: String, proxyName: String): Boolean {
        Timber.d("Force select proxy: group=$group proxy=$proxyName")
        val ok = router!!.dispatch(
            onRoot = { RootTunController.patchForceSelector(it, group, proxyName) },
            onLocal = { ServiceClient.clash().patchForceSelector(group, proxyName) },
        )
        if (ok) {
            applyLocalForceSelection(group = group, proxyName = proxyName)
            scope.launch {
                runCatching {
                    refreshGroupDirect(group, ProxySort.Default)
                    onScheduleFullRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
                }
            }
        }
        return ok
    }

    suspend fun healthCheck(group: String) {
        Timber.d("Health check request: group=%s", group)
        router!!.dispatch(
            onRoot = { RootTunController.healthCheck(it, group) },
            onLocal = { ServiceClient.clash().healthCheck(group) },
        )
        Timber.d("Health check dispatched: group=%s", group)
        scheduleRuntimeGroupRefresh(
            scope, group, PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis,
        ) { refreshGroupDirect(it, ProxySort.Default) }
        onScheduleFullRefresh(PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis)
    }

    suspend fun healthCheckAll() {
        Timber.d("Health check all request")
        router!!.dispatch(
            onRoot = { ctx ->
                RootTunController.queryAllProxyGroups(ctx, excludeNotSelectable = false)
                    .map { it.name }
                    .forEach { groupName ->
                        RootTunController.healthCheck(ctx, groupName)
                        scheduleRuntimeGroupRefresh(
                            scope, groupName, PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis,
                        ) { refreshGroupDirect(it, ProxySort.Default) }
                    }
            },
            onRemote = {
                proxyGroups.value
                    .map { it.name }
                    .forEach { groupName ->
                        ServiceClient.clash().healthCheck(groupName)
                        scheduleRuntimeGroupRefresh(
                            scope, groupName, PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis,
                        ) { refreshGroupDirect(it, ProxySort.Default) }
                    }
            },
            onLocal = { Clash.healthCheckAll() },
        )
        onScheduleFullRefresh(PollingTimerSpecs.ProxyHealthcheckRefresh.intervalMillis)
    }

    suspend fun healthCheckProxy(group: String, proxyName: String): Int {
        Timber.d("Health check proxy request: group=%s proxy=%s", group, proxyName)
        val delay = router!!.dispatch(
            onRoot = { RootTunController.healthCheckProxy(it, group, proxyName).toIntOrNull() ?: 0 },
            onLocal = { ServiceClient.clash().healthCheckProxy(group, proxyName) },
        )
        Timber.d("Health check proxy done: group=%s proxy=%s delay=%s", group, proxyName, delay)
        refreshGroupDirect(group, ProxySort.Default)
        onScheduleFullRefresh(PROXY_SELECT_FULL_REFRESH_DELAY_MS)
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
        publishProxyGroups(updatedGroups, cacheForPreview = true)
    }
}
