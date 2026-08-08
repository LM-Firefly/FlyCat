/*
 * This file is part of YumeBox.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumeyucca.yumebox.runtime.service.preview

import android.content.Context
import com.github.yumeyucca.yumebox.core.model.ProxyGroup
import com.github.yumeyucca.yumebox.core.model.ProxySort
import com.github.yumeyucca.yumebox.runtime.service.core.PreviewCoreProcess
import com.github.yumeyucca.yumebox.runtime.service.config.ServiceStore
import com.github.yumeyucca.yumebox.runtime.service.profile.ImportedDao
import com.github.yumeyucca.yumebox.runtime.service.session.CompiledConfigPipeline
import com.github.yumeyucca.yumebox.runtime.service.session.SessionRuntimeSpecFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** Snapshot published by the inspect-only process. Empty groups are a valid, but non-displayable, config. */
data class PreviewNodeState(
    val fingerprint: String = "",
    val groups: List<ProxyGroup> = emptyList(),
    val ready: Boolean = false,
)

/**
 * Foreground-only owner for the preview shell. Callers explicitly suspend it before a real core
 * launch and resume it after an idle transition; this keeps preview independent from VPN/Root
 * ownership and makes the handoff observable instead of racing two controllers.
 */
class PreviewRuntimeManager(context: Context) {
    private val context = context.applicationContext
    private val factory = SessionRuntimeSpecFactory(this.context)
    private val pipeline = CompiledConfigPipeline(this.context)
    private val process = PreviewCoreProcess(this.context)
    private val serviceStore = ServiceStore()
    private val mutex = Mutex()
    private val generation = AtomicLong(0L)
    private val _state = MutableStateFlow(PreviewNodeState())
    val state: StateFlow<PreviewNodeState> = _state.asStateFlow()

    /** Local storage only: preview must not depend on the service/controller backend being alive. */
    fun hasActiveProfile(): Boolean =
        serviceStore.activeProfile?.let(ImportedDao::queryByUUID) != null

    suspend fun ensureRunning() {
        val requestGeneration = generation.get()
        mutex.withLock {
            if (generation.get() != requestGeneration) return@withLock
            // Startup, the home page, and the node page can request the first snapshot together.
            // Compile under the same transaction as launch/readiness so they reuse one result.
            val compiled = pipeline.compileDetailed(factory.createPreviewSpec())
            if (generation.get() != requestGeneration) return@withLock
            // Several UI surfaces ask for the initial node snapshot at once. Keep the launch and
            // first controller read in one transaction: otherwise a second caller can replace a
            // still-booting PID before it has created preview.sock.
            if (process.isAlive() && _state.value.ready && _state.value.fingerprint == compiled.fingerprint) {
                return@withLock
            }
            if (!process.isAlive() || _state.value.fingerprint != compiled.fingerprint) {
                process.start(compiled.finalYaml)
            }
            val groups = awaitGroups(requestGeneration)
            if (generation.get() == requestGeneration && process.isAlive()) {
                _state.value =
                    PreviewNodeState(fingerprint = compiled.fingerprint, groups = groups, ready = true)
            }
        }
    }

    /** Never wait for config compilation or a controller readiness retry on the real-core handoff. */
    fun stop() {
        generation.incrementAndGet()
        process.stop()
    }

    fun reset() {
        generation.incrementAndGet()
        process.stop()
        _state.value = PreviewNodeState()
    }

    /** Preview is read-only for selection, but mihomo's delay probes are safe and useful here. */
    suspend fun healthCheck(group: String) = mutex.withLock {
        process.controller().healthCheck(group)
        refreshGroups()
    }

    suspend fun healthCheckAll() = mutex.withLock {
        val controller = process.controller()
        _state.value.groups.forEach { group -> controller.healthCheck(group.name) }
        refreshGroups()
    }

    suspend fun healthCheckProxy(group: String, proxyName: String): Int = mutex.withLock {
        val delay = process.controller().healthCheckProxy(group, proxyName)
        refreshGroups()
        delay
    }

    suspend fun refreshGroup(name: String, sort: ProxySort) = mutex.withLock {
        val refreshed = process.controller().queryProxyGroupAsync(name, sort)
        val previous = _state.value
        val merged =
            previous.groups.let { groups ->
                if (groups.none { it.name == name }) groups + refreshed
                else groups.map { group -> if (group.name == name) refreshed else group }
            }
        _state.value = previous.copy(groups = merged, ready = true)
    }

    private suspend fun refreshGroups() {
        val previous = _state.value
        _state.value = previous.copy(groups = process.controller().queryAllProxyGroupsAsync(false), ready = true)
    }

    private suspend fun awaitGroups(requestGeneration: Long): List<ProxyGroup> =
        withTimeout(CONTROLLER_READY_TIMEOUT_MS) {
            while (true) {
                if (generation.get() != requestGeneration || !process.isAlive()) {
                    throw kotlinx.coroutines.CancellationException("preview handoff requested")
                }
                try {
                    return@withTimeout process.controller().queryAllProxyGroupsAsync(false)
                } catch (_: Throwable) {
                    if (generation.get() != requestGeneration || !process.isAlive()) {
                        throw kotlinx.coroutines.CancellationException("preview handoff requested")
                    }
                    delay(CONTROLLER_RETRY_MS)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("preview controller retry loop ended unexpectedly")
        }

    private companion object {
        const val CONTROLLER_READY_TIMEOUT_MS = 8_000L
        const val CONTROLLER_RETRY_MS = 120L
    }
}
