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

package com.github.yumelira.yumebox.runtime.service.session

import android.content.Context
import com.github.yumelira.yumebox.core.model.OverrideSpec
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.TproxyConfig
import com.github.yumelira.yumebox.core.model.TunConfig
import com.github.yumelira.yumebox.data.model.TunStack
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.config.AccessControlMode
import com.github.yumelira.yumebox.runtime.service.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.profile.ImportedDao
import com.github.yumelira.yumebox.runtime.service.util.directoryLastModified
import com.github.yumelira.yumebox.runtime.service.util.importedDir
import java.io.File
import java.security.MessageDigest

class SessionRuntimeSpecFactory(
    context: Context,
    private val store: ServiceStore = ServiceStore(),
) {
    private val context: Context = context.appContextOrSelf
    private val compiledConfigPipeline = CompiledConfigPipeline(this.context)
    private val networkSettings by lazy {
        NetworkSettingsStore(MMKVProvider().getMMKV("network_settings"))
    }

    fun createVpnSpec(): RuntimeSpec = createSpec(RuntimeOwner.VpnService, RunMode.VpnService)

    fun createRootSpec(runMode: RunMode): RuntimeSpec = createSpec(RuntimeOwner.RootDaemon, runMode)

    private fun createSpec(owner: RuntimeOwner, runMode: RunMode): RuntimeSpec {
        val profile = requireActiveProfile()
        val profileDir = context.importedDir.resolve(profile.uuid.toString())
        val skipRuntimePatches =
            networkSettings.disableAllOverride.value &&
                (runMode == RunMode.Tun || runMode == RunMode.Tproxy)
        // Root users can opt out of every visual/runtime patch and make their profile YAML fully
        // authoritative. VPN never takes this path because Android owns its TUN transport.
        val userOverrides =
            if (skipRuntimePatches) {
                emptyList()
            } else {
                compiledConfigPipeline.resolveOverrideSpecs(profile.uuid.toString())
            }
        // Each mode injects its own built-in override (subject to disable-all-overrides): Tun the tun
        // geometry, Tproxy tproxy-port + iptables; VpnService gets its fd path and needs neither.
        val tunConfig = if (!skipRuntimePatches && runMode == RunMode.Tun) buildTunConfig() else null
        val tproxyConfig =
            if (!skipRuntimePatches && runMode == RunMode.Tproxy) buildTproxyConfig() else null
        val overrideSpecs =
            when {
                skipRuntimePatches -> userOverrides
                tunConfig != null -> userOverrides + TunOverride.materialize(tunConfig, profileDir)
                tproxyConfig != null ->
                    userOverrides + TproxyOverride.materialize(tproxyConfig, profileDir)
                else -> userOverrides
            }
        val ageSecretKey = normalizeAgeSecretKey(profile.ageSecretKey)
        return RuntimeSpec(
            owner = owner,
            profileUuid = profile.uuid.toString(),
            profileName = profile.name,
            profileDir = profileDir.absolutePath,
            runtimeConfigPath = profileDir.resolve("runtime.yaml").absolutePath,
            ageSecretKey = ageSecretKey,
            overrideSpecs = overrideSpecs,
            runMode = runMode,
            skipRuntimePatches = skipRuntimePatches,
            tunConfig = tunConfig,
            tproxyConfig = tproxyConfig,
            effectiveFingerprint =
                buildEffectiveFingerprint(
                    profile.uuid.toString(),
                    overrideSpecs,
                    ageSecretKey,
                    skipRuntimePatches,
                ),
            profileFingerprint = buildProfileFingerprint(profile.uuid.toString()),
        )
    }

    private fun buildTunConfig(): TunConfig {
        val access = resolveTunAccessControl()
        return TunConfig(
            ifName = networkSettings.tunIfName.value,
            mtu = networkSettings.tunMtu.value,
            stack =
                when (networkSettings.tunStack.value) {
                    TunStack.System -> "system"
                    TunStack.GVisor -> "gvisor"
                    TunStack.Mixed -> "mixed"
                },
            autoRoute = networkSettings.tunAutoRoute.value,
            strictRoute = networkSettings.tunStrictRoute.value,
            autoRedirect = networkSettings.tunAutoRedirect.value,
            includeUid = access.includeUid,
            excludeUid = access.excludeUid,
            includeAndroidUser = access.includeAndroidUser,
            routeExcludeAddress = networkSettings.tunRouteExcludeAddress.value,
            dnsMode = networkSettings.tunDnsMode.value,
            fakeIpRange = networkSettings.tunFakeIpRange.value,
            fakeIpRange6 = networkSettings.tunFakeIpRange6.value,
            allowIpv6 = networkSettings.enableIPv6.value,
        )
    }

    private fun buildTproxyConfig(): TproxyConfig {
        val access = resolveTunAccessControl()
        return TproxyConfig(
            port = networkSettings.tproxyPort.value,
            dnsMode = networkSettings.tunDnsMode.value,
            includeUid = access.includeUid,
            excludeUid = access.excludeUid,
            fakeIpRange = networkSettings.tunFakeIpRange.value,
            fakeIpRange6 = networkSettings.tunFakeIpRange6.value,
            allowIpv6 = networkSettings.enableIPv6.value,
        )
    }

    /**
     * Maps the shared access-control setting onto Tun uid rules. The cmfa build stubs out mihomo's
     * include/exclude-package, so a selected PACKAGE must be resolved to a UID here (include-uid /
     * exclude-uid). All-apps modes fall back to include-android-user (+ the built-in system-uid
     * exclusion in [TunConfig]).
     */
    private fun resolveTunAccessControl(): TunAccessControl {
        val self = context.applicationInfo.uid
        val selectedUid =
            store.accessControlPackages
                .mapNotNull(::resolvePackageUid)
                .filter { it != self }
                .distinct()
                .sorted()
        val allUsers = networkSettings.tunIncludeAndroidUser.value
        return when (store.accessControlMode) {
            AccessControlMode.AcceptAll -> TunAccessControl(includeAndroidUser = allUsers)
            AccessControlMode.AcceptSelected -> TunAccessControl(includeUid = selectedUid)
            AccessControlMode.RejectSelected ->
                TunAccessControl(excludeUid = selectedUid, includeAndroidUser = allUsers)
            // Whitelist only ourselves ⇒ no other app is ever routed into the tun.
            AccessControlMode.RejectAll -> TunAccessControl(includeUid = listOf(self))
        }
    }

    private fun resolvePackageUid(pkg: String): Int? =
        runCatching { context.packageManager.getPackageInfo(pkg, 0).applicationInfo?.uid }.getOrNull()

    private data class TunAccessControl(
        val includeUid: List<Int> = emptyList(),
        val excludeUid: List<Int> = emptyList(),
        val includeAndroidUser: List<Int> = emptyList(),
    )

    private fun requireActiveProfile():
        com.github.yumelira.yumebox.runtime.service.profile.Imported {
        val profileId = store.activeProfile ?: error("No active profile selected")
        return ImportedDao.queryByUUID(profileId)
            ?: error("Active profile metadata not found: $profileId")
    }

    private fun buildProfileFingerprint(profileUuid: String): String {
        val dir = context.importedDir.resolve(profileUuid)
        return sha256 {
            update(profileUuid.toByteArray())
            updateFile(dir.resolve("config.yaml"))
            update((dir.directoryLastModified ?: -1L).toString().toByteArray())
        }
    }

    private fun buildEffectiveFingerprint(
        profileUuid: String,
        overrideSpecs: List<OverrideSpec>,
        ageSecretKey: String?,
        skipRuntimePatches: Boolean,
    ): String {
        val profileDir = context.importedDir.resolve(profileUuid)
        val metadataFile = context.filesDir.resolve("overrides/metadata.yaml")
        return sha256 {
            update(profileUuid.toByteArray())
            updateAgeSecretKeyDigest(ageSecretKey)
            update("skip-runtime-patches:$skipRuntimePatches".toByteArray())
            updateFile(profileDir.resolve("config.yaml"))
            updateFile(metadataFile)
            overrideSpecs.forEach { overrideSpec ->
                update(overrideSpec.path.toByteArray())
                update(overrideSpec.ext.toByteArray())
                updateFile(File(overrideSpec.path))
            }
        }
    }

    private fun MessageDigest.updateAgeSecretKeyDigest(ageSecretKey: String?) {
        update("age-secret-key:".toByteArray())
        update((ageSecretKey?.let(::sha256String) ?: "none").toByteArray())
    }

    private inline fun sha256(block: MessageDigest.() -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.block()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MessageDigest.updateFile(file: File) {
        if (!file.exists()) {
            update("missing:${file.absolutePath}".toByteArray())
            return
        }
        update(file.absolutePath.toByteArray())
        update(file.readBytes())
    }

    private fun normalizeAgeSecretKey(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun sha256String(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
