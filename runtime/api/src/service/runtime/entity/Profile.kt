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

package com.github.yumelira.yumebox.runtime.api.service.runtime.entity

import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.api.service.LocalRuntimePhase
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeTargetMode {
    Tun,
    Http,
    RootTun,
}

fun RuntimeTargetMode.toProxyMode(): ProxyMode = when (this) {
    RuntimeTargetMode.Tun -> ProxyMode.Tun
    RuntimeTargetMode.Http -> ProxyMode.Http
    RuntimeTargetMode.RootTun -> ProxyMode.RootTun
}

fun ProxyMode.toRuntimeTargetMode(): RuntimeTargetMode = when (this) {
    ProxyMode.Tun -> RuntimeTargetMode.Tun
    ProxyMode.Http -> RuntimeTargetMode.Http
    ProxyMode.RootTun -> RuntimeTargetMode.RootTun
}

@Serializable
enum class RuntimeOwner {
    None,
    LocalTun,
    LocalHttp,
    RootTun,
    RemoteController,
}

@Serializable
enum class RuntimePhase {
    Idle,
    Starting,
    Running,
    Stopping,
    Failed;
    val running: Boolean
        get() = this == Running
    val isActive: Boolean
        get() = this == Starting || this == Running || this == Stopping
    val isNotIdle: Boolean
        get() = this != Idle
    val isActiveOrStopping: Boolean
        get() = this == Starting || this == Running || this == Stopping
    val isRecovering: Boolean
        get() = this == Starting || this == Stopping
}

@Serializable
data class RuntimeSnapshot(
    val owner: RuntimeOwner = RuntimeOwner.None,
    val phase: RuntimePhase = RuntimePhase.Idle,
    val targetMode: RuntimeTargetMode = RuntimeTargetMode.Tun,
    val profileReady: Boolean = false,
    val groupsReady: Boolean = false,
    val trafficReady: Boolean = false,
    val configReady: Boolean = false,
    val transportReady: Boolean = false,
    val logReady: Boolean = false,
    val profileUuid: String? = null,
    val profileName: String? = null,
    val lastError: String? = null,
    val startedAt: Long? = null,
    val effectiveFingerprint: String? = null,
    val generation: Long = 0L,
    val running: Boolean = phase.running,
) {
    val payloadReady: Boolean
        get() = profileReady && groupsReady && trafficReady
}

fun LocalRuntimePhase.toRuntimePhase(): RuntimePhase = when (this) {
    LocalRuntimePhase.Idle -> RuntimePhase.Idle
    LocalRuntimePhase.Starting -> RuntimePhase.Starting
    LocalRuntimePhase.Running -> RuntimePhase.Running
    LocalRuntimePhase.Stopping -> RuntimePhase.Stopping
    LocalRuntimePhase.Failed -> RuntimePhase.Failed
}

fun RuntimePhase.toLocalRuntimePhase(): LocalRuntimePhase = when (this) {
    RuntimePhase.Idle -> LocalRuntimePhase.Idle
    RuntimePhase.Starting -> LocalRuntimePhase.Starting
    RuntimePhase.Running -> LocalRuntimePhase.Running
    RuntimePhase.Stopping -> LocalRuntimePhase.Stopping
    RuntimePhase.Failed -> LocalRuntimePhase.Failed
}

fun RootTunStatus.detectRuntimeOwner(isLocalActive: (ProxyMode) -> Boolean): RuntimeOwner =
    when {
        state.isActiveOrStopping || runtimeReady -> RuntimeOwner.RootTun
        isLocalActive(ProxyMode.Tun) -> RuntimeOwner.LocalTun
        isLocalActive(ProxyMode.Http) -> RuntimeOwner.LocalHttp
        else -> RuntimeOwner.None
    }
