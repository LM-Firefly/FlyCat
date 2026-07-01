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

package com.github.yumelira.yumebox.runtime.api.service.root

import android.content.Context
import android.content.Intent
import android.os.IInterface
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.UiConfiguration
import com.github.yumelira.yumebox.runtime.api.service.RuntimeServiceContractRegistry
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RootTunJson {
    val Default = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** Reified shorthand for [RootTunJson] marshalling — reuses the shared `Default` config. */
inline fun <reified T> rootTunEncode(value: T): String = RootTunJson.Default.encodeToString(value)

inline fun <reified T> rootTunDecode(json: String): T = RootTunJson.Default.decodeFromString(json)

@Serializable data class RootTunStartRequest(val source: String = "")

@Serializable data class RootTunOperationResult(val success: Boolean, val error: String? = null)

@Serializable
data class RootTunStatus(
    val state: RuntimePhase = RuntimePhase.Idle,
    val running: Boolean = state.isActiveOrStopping,
    val lastError: String? = null,
    val profileUuid: String? = null,
    val profileName: String? = null,
    val runtimeReady: Boolean = false,
    val controllerReady: Boolean = false,
    val startedAt: Long? = null,
    val staticPlanFingerprint: String? = null,
    val transportFingerprint: String? = null,
    val overrideFingerprint: String? = null,
    val profileFingerprint: String? = null,
)

@Serializable
data class RootTunRuntimeSnapshot(
    val status: RootTunStatus = RootTunStatus(),
    val profileUuid: String? = null,
    val profileName: String? = null,
    val proxyGroups: List<ProxyGroup> = emptyList(),
    val configuration: UiConfiguration = UiConfiguration(),
    val providers: List<Provider> = emptyList(),
    val trafficNow: Long = 0L,
    val trafficTotal: Long = 0L,
    val transportFingerprint: String = "",
    val overrideFingerprint: String = "",
    val profileFingerprint: String = "",
)

data class RootAccessStatus(
    val rootAccessGranted: Boolean,
    val blockedMessage: String = "Root access required",
) {
    val canStartRootTun: Boolean
        get() = rootAccessGranted
    fun rootTunBlockedMessage(): String = blockedMessage
}

interface RootAccessSupportContract {
    fun evaluate(context: Context): RootAccessStatus
    suspend fun evaluateAsync(context: Context): RootAccessStatus
    suspend fun requireRootTunAccess(context: Context): RootAccessStatus
}

interface RootTunRuntimeRecoveryContract {
    fun isBinderAlive(service: IInterface?): Boolean
    fun isBinderConnectionFailure(error: Throwable): Boolean
    fun binderFailureReason(error: Throwable): String
    fun handleBinderGone(context: Context, reason: String?)
}

interface RootTunForegroundServiceContract {
    fun start(context: Context)
    fun stop(context: Context)
}

@Serializable
data class RootTunLogChunk(val nextSeq: Long = 0L, val items: List<String> = emptyList())

interface RootTunStateStoreContract {
    fun snapshot(): RootTunStatus
    fun isRunning(): Boolean
    fun updateStatus(status: RootTunStatus)
    fun markIdle(error: String? = null)
    fun clear()
}

interface RootTunStateStoreFactoryContract {
    fun create(context: Context): RootTunStateStoreContract
}

interface RootPackageQueryContract {
    fun hasRootAccess(): Boolean
    fun queryInstalledPackageNames(): Set<String>?
}

// ── RootTunBindingContract ──────────────────────────────────────────────

/**
 * Contract for managing the libsu RootTun IPC binding lifecycle.
 * Implementations live in runtime:service; consumed by RootTunController in runtime:client.
 */
interface RootTunBindingContract {
    suspend fun <T> remoteCall(
        context: Context,
        onBinderFailure: (() -> T)? = null,
        block: (Any) -> T,
    ): T
    suspend fun bind(context: Context): Any
    fun cachedBinder(context: Context): Any?
    fun createIntent(context: Context): Intent
    suspend fun disconnect()
    fun afterBind(callback: ((Any) -> Unit)?)
    fun beforeUnbind(callback: ((Any) -> Unit)?)
}

// ── RootTunStatusFlow ───────────────────────────────────────────────────

/**
 * Process-global main-process view of the RootTun runtime status.
 *
 * After the root process became the single authoritative writer of the `root_tun_state` MMKV
 * (via RootTunStatePublisher), the main process no longer writes that store. Instead it keeps
 * this in-process flow fed by:
 *  - a one-time read-only seed from the durable root-written store ([ensureSeeded]),
 *  - the push observer (`IRootTunStateObserver`) registered by RootTunController, and
 *  - its own optimistic/pull updates ([update]/[markIdle]).
 */
object RootTunStatusFlow {
    private val _flow = MutableStateFlow(RootTunStatus())
    val flow: StateFlow<RootTunStatus> = _flow.asStateFlow()

    @Volatile private var seeded = false

    fun ensureSeeded(context: Context) {
        if (seeded) return
        synchronized(this) {
            if (seeded) return
            val factory = RuntimeServiceContractRegistry.rootTunStateStoreFactory
            if (factory != null) {
                _flow.value = factory.create(context.appContextOrSelf).snapshot()
            }
            seeded = true
        }
    }

    fun current(context: Context): RootTunStatus {
        ensureSeeded(context)
        return _flow.value
    }

    fun update(status: RootTunStatus) {
        _flow.value = status.copy(running = status.state.isActiveOrStopping)
    }

    fun markIdle(error: String? = null) =
        update(
            RootTunStatus(
                state = RuntimePhase.Idle,
                lastError = error,
                runtimeReady = false,
                controllerReady = true,
            )
        )
}
