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

package com.github.yumelira.yumebox.runtime.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.github.yumelira.yumebox.core.Global
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.util.enumByNameOrNull
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimeStatusContract
import com.github.yumelira.yumebox.runtime.api.service.RuntimeServiceContractRegistry
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeTargetMode
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toLocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.toProxyMode
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow
import com.github.yumelira.yumebox.runtime.service.root.RootTunBinding
import com.github.yumelira.yumebox.runtime.service.root.RootTunBindingContractAdapter
import com.github.yumelira.yumebox.runtime.service.runtime.session.LocalRuntimeSessionHelpersImpl
import com.github.yumelira.yumebox.runtime.service.root.RootAccessSupport
import com.github.yumelira.yumebox.runtime.service.root.RootPackageShell
import com.github.yumelira.yumebox.runtime.service.root.RootTunRuntimeRecovery
import com.github.yumelira.yumebox.runtime.service.root.RootTunStateStoreFactory
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeServiceLauncher
import com.tencent.mmkv.MMKV
import kotlin.enums.enumEntries

@Suppress("DEPRECATION")
class StatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_CURRENT_PROFILE -> {
                syncCachedRuntimeState()
                if (serviceRunning) Bundle().apply { putString("name", currentProfile) } else null
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw IllegalArgumentException("Stub!")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        throw IllegalArgumentException("Stub!")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun getType(uri: Uri): String? {
        throw IllegalArgumentException("Stub!")
    }

    override fun onCreate(): Boolean {
        runCatching {
            val app = context?.applicationContext as? android.app.Application ?: return@runCatching
            Global.init(app)
            // MMKV 必须在使用前初始化，ContentProvider 在 Application.onCreate 之前执行
            MMKV.disableProcessModeChecker()
            MMKV.initialize(app)
            clearTunStarting()
            syncCachedRuntimeState()
            RuntimeServiceContractRegistry.localRuntimeService = RuntimeServiceLauncher
            RuntimeServiceContractRegistry.localRuntimeStatus = Companion
            RuntimeServiceContractRegistry.rootAccessSupport = RootAccessSupport
            RuntimeServiceContractRegistry.rootTunRuntimeRecovery = RootTunRuntimeRecovery
            RuntimeServiceContractRegistry.rootTunForegroundService = RootTunService
            RuntimeServiceContractRegistry.rootTunStateStoreFactory = RootTunStateStoreFactory
            RuntimeServiceContractRegistry.rootPackageQuery = RootPackageShell
            RuntimeServiceContractRegistry.rootTunBinding = RootTunBindingContractAdapter(RootTunBinding())
            RuntimeServiceContractRegistry.localRuntimeSessionHelpers = LocalRuntimeSessionHelpersImpl(app)
            RootTunStatusFlow.ensureSeeded(app)
        }
        return true
    }

    companion object : LocalRuntimeStatusContract {
        const val METHOD_CURRENT_PROFILE = "currentProfile"

        private val legacyRuntimeFiles =
            listOf("service_running.lock", "service_autostart.lock", "service_running_mode.txt")
        private const val SERVICE_CACHE_ID = "service_cache"
        private const val KEY_TUN_STARTING = "local_tun_starting"
        private const val KEY_RUNTIME_MODE = "local_runtime_mode"
        private const val KEY_RUNTIME_PHASE = "local_runtime_phase"
        private const val KEY_RUNTIME_STARTED_AT = "local_runtime_started_at"

        @Volatile
        override var serviceRunning: Boolean = false
            private set

        @Volatile
        var runningMode: ProxyMode? = null
            private set

        @Volatile
        var localRuntimePhase: RuntimePhase = RuntimePhase.Idle
            private set

        @Volatile var currentProfile: String? = null

        fun markRuntimeStarting(mode: ProxyMode) {
            markRuntimePhase(mode, RuntimePhase.Starting)
        }

        fun markRuntimeRunning(mode: ProxyMode) {
            markRuntimePhase(mode, RuntimePhase.Running)
        }

        fun markRuntimeStopping(mode: ProxyMode) {
            markRuntimePhase(mode, RuntimePhase.Stopping)
        }

        fun markRuntimeFailed(mode: ProxyMode) {
            markRuntimePhase(mode, RuntimePhase.Failed)
        }

        fun markRuntimeIdle(mode: ProxyMode) {
            markRuntimePhase(mode, RuntimePhase.Idle)
        }

        fun isRuntimeActive(mode: ProxyMode): Boolean = queryRuntimePhase(mode).isNotIdle

        fun queryRuntimePhase(mode: ProxyMode): RuntimePhase {
            reconcilePersistedRuntimeState()
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            updateInMemoryRuntimeState(persistedMode, persistedPhase)
            return if (persistedMode == mode) persistedPhase else RuntimePhase.Idle
        }

        fun queryRuntimeStartedAt(mode: ProxyMode): Long? {
            reconcilePersistedRuntimeState()
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            var startedAt = readPersistedRuntimeStartedAt()
            if (persistedMode == mode && persistedPhase.isNotIdle && startedAt == null) {
                startedAt = System.currentTimeMillis()
                persistRuntimeState(
                    mode = persistedMode,
                    phase = persistedPhase,
                    startedAt = startedAt,
                )
            }
            updateInMemoryRuntimeState(persistedMode, persistedPhase)
            return startedAt.takeIf { persistedMode == mode && persistedPhase.isNotIdle }
        }

        override fun reconcilePersistedRuntimeState() {
            val (persistedMode, persistedPhase) = readPersistedRuntimeState()
            if (persistedMode == null || !persistedPhase.isNotIdle) {
                updateInMemoryRuntimeState(persistedMode, persistedPhase)
                return
            }

            if (
                persistedMode == ProxyMode.RootTun || persistedPhase == RuntimePhase.Starting
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
        override fun isRuntimeActive(mode: RuntimeTargetMode): Boolean {
            return isRuntimeActive(mode.toProxyMode())
        }
        override fun queryRuntimePhase(mode: RuntimeTargetMode): LocalRuntimePhase {
            return queryRuntimePhase(mode.toProxyMode()).toLocalRuntimePhase()
        }
        override fun queryRuntimeStartedAt(mode: RuntimeTargetMode): Long? {
            return queryRuntimeStartedAt(mode.toProxyMode())
        }

        fun isLocalRuntimeServiceAlive(mode: ProxyMode): Boolean {
            if (mode == ProxyMode.RootTun) return false
            val application = runCatching { Global.application }.getOrNull() ?: return false
            val activityManager =
                application.getSystemService(ActivityManager::class.java) ?: return false
            val targetClassName =
                when (mode) {
                    ProxyMode.Tun -> TunService::class.java.name
                    ProxyMode.Http -> ClashService::class.java.name
                    ProxyMode.RootTun -> return false
                }

            return runCatching {
                    queryRunningServiceClassNames(activityManager).any { className ->
                        className == targetClassName
                    }
                }
                .getOrDefault(false)
        }
        override fun isLocalRuntimeServiceAlive(mode: RuntimeTargetMode): Boolean {
            return isLocalRuntimeServiceAlive(mode.toProxyMode())
        }

        fun markTunStarting() {
            serviceCache().encode(KEY_TUN_STARTING, true)
        }

        fun clearTunStarting() {
            serviceCache().removeValueForKey(KEY_TUN_STARTING)
        }

        fun isTunStarting(): Boolean {
            return serviceCache().decodeBool(KEY_TUN_STARTING, false)
        }

        override fun clearLegacyStateFiles() {
            val filesDir = Global.application.filesDir
            legacyRuntimeFiles.forEach { name -> runCatching { filesDir.resolve(name).delete() } }
        }
        override fun markRuntimeIdle(mode: RuntimeTargetMode) { markRuntimeIdle(mode.toProxyMode()) }

        private fun serviceCache(): MMKV {
            return MMKV.mmkvWithID(SERVICE_CACHE_ID, MMKV.MULTI_PROCESS_MODE)
        }

        private fun markRuntimePhase(mode: ProxyMode, phase: RuntimePhase) {
            if (mode == ProxyMode.Tun && phase != RuntimePhase.Starting) {
                clearTunStarting()
            }
            val activeMode = mode.takeIf { phase.isNotIdle }
            val startedAt = resolveRuntimeStartedAt(mode = activeMode, phase = phase)
            persistRuntimeState(mode = activeMode, phase = phase, startedAt = startedAt)
            updateInMemoryRuntimeState(mode = activeMode, phase = phase)
        }

        private fun persistRuntimeState(
            mode: ProxyMode?,
            phase: RuntimePhase,
            startedAt: Long? = null,
        ) {
            val cache = serviceCache()
            if (phase == RuntimePhase.Idle || mode == null) {
                cache.removeValueForKey(KEY_RUNTIME_MODE)
                cache.removeValueForKey(KEY_RUNTIME_PHASE)
                cache.removeValueForKey(KEY_RUNTIME_STARTED_AT)
                return
            }
            cache.encode(KEY_RUNTIME_MODE, mode.name)
            cache.encode(KEY_RUNTIME_PHASE, phase.name)
            if (startedAt != null) {
                cache.encode(KEY_RUNTIME_STARTED_AT, startedAt)
            } else {
                cache.removeValueForKey(KEY_RUNTIME_STARTED_AT)
            }
        }

        private fun readPersistedRuntimeState(): Pair<ProxyMode?, RuntimePhase> {
            val cache = serviceCache()
            val phase =
                enumByNameOrNull<RuntimePhase>(cache.decodeString(KEY_RUNTIME_PHASE))
                    ?: RuntimePhase.Idle
            val mode =
                enumByNameOrNull<ProxyMode>(cache.decodeString(KEY_RUNTIME_MODE))
                    ?.takeIf { phase.isNotIdle }
            return mode to phase
        }

        private fun readPersistedRuntimeStartedAt(): Long? {
            return serviceCache().decodeLong(KEY_RUNTIME_STARTED_AT, 0L).takeIf { it > 0L }
        }

        private fun resolveRuntimeStartedAt(mode: ProxyMode?, phase: RuntimePhase): Long? {
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

        private fun updateInMemoryRuntimeState(mode: ProxyMode?, phase: RuntimePhase) {
            runningMode = mode.takeIf { phase == RuntimePhase.Running }
            localRuntimePhase = phase
            serviceRunning = phase == RuntimePhase.Running
        }

        private fun syncCachedRuntimeState() {
            reconcilePersistedRuntimeState()
            val (mode, phase) = readPersistedRuntimeState()
            updateInMemoryRuntimeState(mode, phase)
        }

        @SuppressLint("Deprecated")
        @Suppress("DEPRECATION")
        private fun queryRunningServiceClassNames(activityManager: ActivityManager): List<String> {
            return activityManager.getRunningServices(Int.MAX_VALUE).mapNotNull { service ->
                service.service
                    ?.takeIf { it.packageName == Global.application.packageName }
                    ?.className
            }
        }
    }
}
