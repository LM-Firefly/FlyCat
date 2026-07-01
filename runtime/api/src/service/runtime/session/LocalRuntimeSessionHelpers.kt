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
 * MERCHANTABILITY OR FITNESS FOR ANY PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumelira.yumebox.runtime.api.service.runtime.session

import com.github.yumelira.yumebox.core.model.OverrideSpec
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.core.model.RootTunConfig
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimeOwner
import kotlinx.serialization.Serializable

// ── RuntimeSpec ─────────────────────────────────────────────────────────

@Serializable
data class RuntimeSpec(
    val owner: RuntimeOwner,
    val profileUuid: String,
    val profileName: String,
    val profileDir: String,
    val runtimeConfigPath: String = "",
    val overrideSpecs: List<OverrideSpec> = emptyList(),
    val rootTunConfig: RootTunConfig? = null,
    val staticPlanFingerprint: String = "",
    val transportFingerprint: String = "",
    val effectiveFingerprint: String = "",
    val profileFingerprint: String = "",
)

// ── SpecMode ────────────────────────────────────────────────────────────

enum class SpecMode {
    Tun,
    Http,
    RootTun,
}

// ── LocalRuntimeSessionHelpers ──────────────────────────────────────────

/**
 * Delegates the local (non-remote, non-root) runtime session helpers that
 * ClashGateway uses for in-process proxy-group resolution and config preview.
 *
 * Implementations live in runtime:service; ClashGateway in runtime:client
 * consumes this interface via [com.github.yumelira.yumebox.runtime.api.service.RuntimeServiceContractRegistry].
 */
interface LocalRuntimeSessionHelpers {
    val serviceRunning: Boolean
    val activeProfileUuid: String?
    fun resolveOverrideSpecs(profileUuid: String): List<OverrideSpec>
    suspend fun previewTunRouteExcludeAddress(profileUuid: String): List<String>
    suspend fun previewGroups(spec: RuntimeSpec, excludeNotSelectable: Boolean): List<ProxyGroup>
    suspend fun resolvedGroups(spec: RuntimeSpec, excludeNotSelectable: Boolean, enrichLive: Boolean = true): List<ProxyGroup>
    suspend fun resolvedGroupNames(spec: RuntimeSpec, excludeNotSelectable: Boolean): List<String>
    fun createSpec(mode: SpecMode): RuntimeSpec?
    fun stopLocalServices(packageName: String)
    fun stopLocalHttpProxy()
}
