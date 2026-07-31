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

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.core.model


import kotlinx.serialization.Serializable

@Serializable
data class OverrideSpec(
    val path: String,
    val ext: String,
)

@Serializable
data class CompileRequest(
    val schemaVersion: Int = 1,
    val profileUuid: String,
    val profileDir: String,
    val profilePath: String,
    val overrides: List<OverrideSpec> = emptyList(),
    val outputPath: String,
    val ageSecretKey: String? = null,
    // Forwarded to liboverride: in Tun mode it keeps the compiled `tun:` block authoritative (the
    // core opens its own kernel device) instead of force-disabling it as for VpnService.
    val runMode: RunMode = RunMode.VpnService,
    // Root Tun + disable-all only. Skips compiler runtime DNS/path patches so the raw
    // profile stays authoritative. VPN never sets this — user overrides are still cleared by the
    // factory, but system patches remain.
    val skipRuntimePatches: Boolean = false,
)

@Serializable
data class CompileResult(
    val success: Boolean,
    val fingerprint: String = "",
    val finalYaml: String = "",
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
data class NativeInspectResult(
    val success: Boolean,
    val payload: String = "",
    val error: String? = null,
)

@Serializable
data class CompileRawSummary(
    val success: Boolean,
    val fingerprint: String = "",
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val tunIncludePackage: List<String> = emptyList(),
    val tunExcludePackage: List<String> = emptyList(),
)
