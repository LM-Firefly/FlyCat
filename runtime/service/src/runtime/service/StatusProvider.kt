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

@file:Suppress("RedundantNullableReturnType", "UnusedSymbol")

package com.github.yumeyucca.yumebox.runtime.service


import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.github.yumeyucca.yumebox.core.Global
import com.github.yumeyucca.yumebox.core.util.enumByNameOrNull
import com.github.yumeyucca.yumebox.data.model.RunMode
import com.github.yumeyucca.yumebox.runtime.api.RuntimePhase
import com.github.yumeyucca.yumebox.runtime.api.initializeServiceGlobal
import com.github.yumeyucca.yumebox.runtime.service.core.CoreProcess
import com.tencent.mmkv.MMKV
import java.util.*

class StatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
        when (method) {
            METHOD_CURRENT_PROFILE -> {
                syncCachedRuntimeState()
                if (serviceRunning) Bundle().apply { putString("name", currentProfile) } else null
            }

            else -> super.call(method, arg, extras)
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw IllegalArgumentException("Stub!")

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw IllegalArgumentException("Stub!")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw IllegalArgumentException("Stub!")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw IllegalArgumentException("Stub!")

    override fun getType(uri: Uri): String? = throw IllegalArgumentException("Stub!")

    override fun onCreate(): Boolean {
        runCatching {
            val app = context?.applicationContext as? android.app.Application ?: return@runCatching
            initializeServiceGlobal(app)
            // MMKV 必须在使用前初始化，ContentProvider 在 Application.onCreate 之前执行
            MMKV.initialize(app)
            // Pre-create every store the root process touches: a file first created by the
            // root uid would be root-owned and unreadable/unwritable for the app afterwards.
            listOf("service", "network_settings", "profiles", "root_tun_state").forEach { id ->
                runCatching { MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE) }
            }
            runCatching { serviceCache().removeValueForKey(KEY_TUN_STARTING_LEGACY) }
            syncCachedRuntimeState()
        }
        return true
    }

    companion object {
        const val METHOD_CURRENT_PROFILE = "currentProfile"

        private val legacyRuntimeFiles =
            listOf("service_running.lock", "service_autostart.lock", "service_running_mode.txt")
        private const val SERVICE_CACHE_ID = "service_cache"
        private const val STARTING_GRACE_MS = 60_000L
        private const val STARTING_DISPATCH_GRACE_MS = 2_000L
        private const val FAILED_RETENTION_MS = 10 * 60_000L
        private const val KEY_TUN_STARTING_LEGACY = "local_tun_starting"
        private const val KEY_RUNTIME_MODE = "local_runtime_mode"
        private const val KEY_RUNTIME_PHASE = "local_runtime_phase"
        private const val KEY_RUNTIME_STARTED_AT = "local_runtime_started_at"
        private const val KEY_RUNTIME_SESSION = "local_runtime_session"
        private const val KEY_RUNTIME_LAST_ERROR = "local_runtime_last_error"

        @Volatile
        var serviceRunning: Boolean = false
            private set

        @Volatile
        var runningMode: RunMode? = null
            private set

        @Volatile
        var localRuntimePhase: RuntimePhase = RuntimePhase.Idle
            private set

        @Volatile var currentProfile: String? = null

        // ---- session-token lifecycle (VpnService runtime) ----
        // The phase store has a single mode slot shared by every writer. VpnService writers carry a
        // token so a late write from an outgoing instance can't stomp its replacement (stale-token
        // writes are dropped); the root daemon uses the token-less force writes below (own single
        // writer).

        /** Claims the phase slot for a new local session and returns its write token. */
        @Synchronized
        fun beginRuntimeSession(mode: RunMode): String {
            val token = UUID.randomUUID().toString()
            persistRuntimeState(
                mode = mode,
                phase = RuntimePhase.Starting,
                startedAt = System.currentTimeMillis(),
                token = token,
            )
            updateInMemoryRuntimeState(mode, RuntimePhase.Starting)
            return token
        }

        /** Adopts the token the launcher created for this start, or claims a fresh session. */
        @Synchronized
        fun adoptOrBeginRuntimeSession(mode: RunMode): String {
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            val token = currentSessionToken()
            if (persistedMode == mode && persistedPhase == RuntimePhase.Starting && token != null) {
                updateInMemoryRuntimeState(mode, RuntimePhase.Starting)
                return token
            }
            return beginRuntimeSession(mode)
        }

        @Synchronized
        fun markRuntimeRunning(mode: RunMode, token: String) {
            markRuntimePhaseOwned(mode, RuntimePhase.Running, token)
        }

        @Synchronized
        fun markRuntimeStopping(mode: RunMode, token: String) {
            markRuntimePhaseOwned(mode, RuntimePhase.Stopping, token)
        }

        @Synchronized
        fun markRuntimeFailed(mode: RunMode, token: String, error: String?) {
            markRuntimePhaseOwned(mode, RuntimePhase.Failed, token, error)
        }

        @Synchronized
        fun markRuntimeIdle(mode: RunMode, token: String) {
            markRuntimePhaseOwned(mode, RuntimePhase.Idle, token)
        }

        fun queryRuntimeLastError(mode: RunMode): String? {
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            if (persistedMode != mode || persistedPhase != RuntimePhase.Failed) return null
            return serviceCache().decodeString(KEY_RUNTIME_LAST_ERROR)?.takeIf { it.isNotBlank() }
        }

        fun isRuntimeStartingWithinGrace(mode: RunMode): Boolean {
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            // The liveness check keeps a Starting record orphaned by a process death from
            // blocking the user's next start for the rest of the grace window.
            return persistedMode == mode &&
                persistedPhase == RuntimePhase.Starting &&
                isStartingWithinGrace() &&
                isLocalRuntimeServiceAlive(mode)
        }

        // ---- token-less force writes (root daemon, explicit user stops, cleanup) ----

        @Synchronized
        fun markRuntimeStarting(mode: RunMode) {
            markRuntimePhase(mode, RuntimePhase.Starting)
        }

        @Synchronized
        fun markRuntimeRunning(mode: RunMode) {
            markRuntimePhase(mode, RuntimePhase.Running)
        }

        @Synchronized
        fun markRuntimeStopping(mode: RunMode) {
            markRuntimePhase(mode, RuntimePhase.Stopping)
        }

        @Synchronized
        fun markRuntimeFailed(mode: RunMode, error: String? = null) {
            markRuntimePhase(mode, RuntimePhase.Failed, error)
        }

        @Synchronized
        fun markRuntimeIdle(mode: RunMode) {
            markRuntimePhase(mode, RuntimePhase.Idle)
        }

        // Failed is a terminal record kept for diagnosis, not an engaged runtime.
        fun isRuntimeActive(mode: RunMode): Boolean = queryRuntimePhase(mode).isActiveOrStopping

        fun queryRuntimePhase(mode: RunMode): RuntimePhase {
            reconcilePersistedRuntimeState()
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            updateInMemoryRuntimeState(persistedMode, persistedPhase)
            return if (persistedMode == mode) persistedPhase else RuntimePhase.Idle
        }

        @Synchronized
        fun queryRuntimeStartedAt(mode: RunMode): Long? {
            reconcilePersistedRuntimeState()
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            var startedAt = readPersistedRuntimeStartedAt()
            if (persistedMode == mode && persistedPhase.isNotIdle && startedAt == null) {
                startedAt = System.currentTimeMillis()
                // Read-repair only; keep the session token and error intact.
                persistRuntimeState(
                    mode = persistedMode,
                    phase = persistedPhase,
                    startedAt = startedAt,
                    token = currentSessionToken(),
                    error = serviceCache().decodeString(KEY_RUNTIME_LAST_ERROR),
                )
            }
            updateInMemoryRuntimeState(persistedMode, persistedPhase)
            return startedAt.takeIf { persistedMode == mode && persistedPhase.isNotIdle }
        }

        @Synchronized
        fun reconcilePersistedRuntimeState() {
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            if (persistedMode == null || !persistedPhase.isNotIdle) {
                updateInMemoryRuntimeState(persistedMode, persistedPhase)
                return
            }

            // Failed is a terminal record (service expected dead), so it must not be liveness-reset
            // —
            // that erased failures before they were read. It decays after retention or on the next
            // session.
            if (persistedPhase == RuntimePhase.Failed) {
                val failedAt = readPersistedRuntimeStartedAt()
                if (
                    failedAt == null ||
                        System.currentTimeMillis() - failedAt !in 0..FAILED_RETENTION_MS
                ) {
                    persistRuntimeState(mode = null, phase = RuntimePhase.Idle)
                    updateInMemoryRuntimeState(mode = null, phase = RuntimePhase.Idle)
                } else {
                    updateInMemoryRuntimeState(persistedMode, persistedPhase)
                }
                return
            }

            // Allow only the short startForegroundService dispatch window without a lifecycle
            // signal. Beyond it, Starting must prove service/core liveness like Running does.
            if (
                persistedPhase == RuntimePhase.Starting &&
                    startingElapsedMs()?.let { it <= STARTING_DISPATCH_GRACE_MS } == true
            ) {
                updateInMemoryRuntimeState(persistedMode, persistedPhase)
                return
            }

            if (isLocalRuntimeServiceAlive(persistedMode)) {
                updateInMemoryRuntimeState(persistedMode, persistedPhase)
                return
            }

            persistRuntimeState(mode = null, phase = RuntimePhase.Idle)
            updateInMemoryRuntimeState(mode = null, phase = RuntimePhase.Idle)
            currentProfile = null
        }

        // VpnService is in-process, so a flag driven from its lifecycle is an O(1) liveness signal
        // (it replaced an ActivityManager.getRunningServices binder scan that stalled the UI). The
        // root
        // daemon is out-of-process and survives app death, so its liveness is a pid probe, not a
        // flag.
        @Volatile private var vpnServiceAlive = false

        /** Set from [RuntimeForegroundController.onCreate]/onDestroy for the VpnService. */
        fun setServiceAlive(mode: RunMode, alive: Boolean) {
            if (mode == RunMode.VpnService) vpnServiceAlive = alive
        }

        fun isLocalRuntimeServiceAlive(mode: RunMode): Boolean =
            when (mode) {
                RunMode.VpnService -> vpnServiceAlive
                RunMode.Tun -> CoreProcess.isRootDaemonAlive()
            }

        fun clearLegacyStateFiles() {
            val filesDir = Global.application.filesDir
            legacyRuntimeFiles.forEach { name -> runCatching { filesDir.resolve(name).delete() } }
        }

        private fun serviceCache(): MMKV =
            MMKV.mmkvWithID(SERVICE_CACHE_ID, MMKV.MULTI_PROCESS_MODE)

        private fun markRuntimePhaseOwned(
            mode: RunMode,
            phase: RuntimePhase,
            token: String,
            error: String? = null,
        ) {
            if (token.isEmpty() || currentSessionToken() != token) return
            when (phase) {
                RuntimePhase.Idle -> {
                    persistRuntimeState(mode = null, phase = RuntimePhase.Idle)
                    updateInMemoryRuntimeState(mode = null, phase = RuntimePhase.Idle)
                }

                RuntimePhase.Failed -> {
                    // Terminal record: keep the failure readable (mode + error + timestamp),
                    // drop the token so any later stale write from this session is a no-op.
                    persistRuntimeState(
                        mode = mode,
                        phase = RuntimePhase.Failed,
                        startedAt = System.currentTimeMillis(),
                        error = error,
                    )
                    updateInMemoryRuntimeState(mode, RuntimePhase.Failed)
                }

                else -> {
                    persistRuntimeState(
                        mode = mode,
                        phase = phase,
                        startedAt = resolveRuntimeStartedAt(mode = mode, phase = phase),
                        token = token,
                    )
                    updateInMemoryRuntimeState(mode, phase)
                }
            }
        }

        private fun markRuntimePhase(mode: RunMode, phase: RuntimePhase, error: String? = null) {
            if (phase == RuntimePhase.Idle) {
                // Force-idle only releases the slot the caller owns; another mode's live
                // session (e.g. the root daemon racing a VpnService start) must not be wiped.
                val (persistedMode, _) = readPersistedRuntimeState()
                if (persistedMode != null && persistedMode != mode) return
            }
            val activeMode = mode.takeIf { phase.isNotIdle }
            val startedAt = resolveRuntimeStartedAt(mode = activeMode, phase = phase)
            persistRuntimeState(
                mode = activeMode,
                phase = phase,
                startedAt = startedAt,
                error = error.takeIf { phase == RuntimePhase.Failed },
            )
            updateInMemoryRuntimeState(mode = activeMode, phase = phase)
        }

        private fun currentSessionToken(): String? =
            serviceCache().decodeString(KEY_RUNTIME_SESSION)?.takeIf { it.isNotEmpty() }

        private fun persistRuntimeState(
            mode: RunMode?,
            phase: RuntimePhase,
            startedAt: Long? = null,
            token: String? = null,
            error: String? = null,
        ) {
            val cache = serviceCache()
            if (phase == RuntimePhase.Idle || mode == null) {
                cache.removeValueForKey(KEY_RUNTIME_MODE)
                cache.removeValueForKey(KEY_RUNTIME_PHASE)
                cache.removeValueForKey(KEY_RUNTIME_STARTED_AT)
                cache.removeValueForKey(KEY_RUNTIME_SESSION)
                cache.removeValueForKey(KEY_RUNTIME_LAST_ERROR)
                return
            }
            cache.encode(KEY_RUNTIME_MODE, mode.name)
            cache.encode(KEY_RUNTIME_PHASE, phase.name)
            if (startedAt != null) {
                cache.encode(KEY_RUNTIME_STARTED_AT, startedAt)
            } else {
                cache.removeValueForKey(KEY_RUNTIME_STARTED_AT)
            }
            if (token != null) {
                cache.encode(KEY_RUNTIME_SESSION, token)
            } else {
                cache.removeValueForKey(KEY_RUNTIME_SESSION)
            }
            if (error != null) {
                cache.encode(KEY_RUNTIME_LAST_ERROR, error)
            } else {
                cache.removeValueForKey(KEY_RUNTIME_LAST_ERROR)
            }
        }

        private fun readPersistedRuntimeState(): Pair<RunMode?, RuntimePhase> {
            val cache = serviceCache()
            val phase =
                enumByNameOrNull<RuntimePhase>(cache.decodeString(KEY_RUNTIME_PHASE))
                    ?: RuntimePhase.Idle
            val mode =
                enumByNameOrNull<RunMode>(cache.decodeString(KEY_RUNTIME_MODE))?.takeIf {
                    phase.isNotIdle
                }
            return mode to phase
        }

        private fun readPersistedRuntimeStartedAt(): Long? =
            serviceCache().decodeLong(KEY_RUNTIME_STARTED_AT, 0L).takeIf {
                it > 0L
            }

        private fun isStartingWithinGrace(): Boolean =
            startingElapsedMs()?.let { it in 0..STARTING_GRACE_MS } == true

        private fun startingElapsedMs(): Long? {
            val startedAt = readPersistedRuntimeStartedAt() ?: return null
            return (System.currentTimeMillis() - startedAt).takeIf { it >= 0L }
        }

        private fun resolveRuntimeStartedAt(mode: RunMode?, phase: RuntimePhase): Long? {
            if (!phase.isNotIdle || mode == null) {
                return null
            }
            if (phase == RuntimePhase.Starting) {
                return System.currentTimeMillis()
            }

            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            val persistedStartedAt = readPersistedRuntimeStartedAt()
            return persistedStartedAt.takeIf { persistedMode == mode && persistedPhase.isNotIdle }
                ?: System.currentTimeMillis()
        }

        private fun updateInMemoryRuntimeState(mode: RunMode?, phase: RuntimePhase) {
            runningMode = mode.takeIf { phase == RuntimePhase.Running }
            localRuntimePhase = phase
            serviceRunning = phase == RuntimePhase.Running
        }

        private fun syncCachedRuntimeState() {
            reconcilePersistedRuntimeState()
            val (mode, phase) = readPersistedRuntimeState()
            updateInMemoryRuntimeState(mode, phase)
        }
    }
}
