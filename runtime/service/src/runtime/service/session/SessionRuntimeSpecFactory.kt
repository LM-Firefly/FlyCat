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
import com.github.yumelira.yumebox.core.model.TunConfig
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
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
        val disableOverrides = networkSettings.disableAllOverride.value
        // "Disable all overrides" skips the whole override chain (incl. the built-in Tun override).
        val userOverrides =
            if (disableOverrides) {
                emptyList()
            } else {
                compiledConfigPipeline.resolveOverrideSpecs(profile.uuid.toString())
            }
        // Root modes inject the Tun geometry as a built-in override — but subject to the same
        // disable-all-overrides switch (user's choice), so append it only when overrides are on.
        val tunConfig = if (runMode != RunMode.VpnService) buildTunConfig() else null
        val overrideSpecs =
            if (tunConfig != null && !disableOverrides) {
                userOverrides + TunOverride.materialize(tunConfig, profileDir)
            } else {
                userOverrides
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
            tunConfig = tunConfig,
            effectiveFingerprint =
                buildEffectiveFingerprint(profile.uuid.toString(), overrideSpecs, ageSecretKey),
            profileFingerprint = buildProfileFingerprint(profile.uuid.toString()),
        )
    }

    private fun buildTunConfig(): TunConfig =
        TunConfig(
            ifName = networkSettings.tunIfName.value,
            mtu = networkSettings.tunMtu.value,
            autoRoute = networkSettings.tunAutoRoute.value,
            strictRoute = networkSettings.tunStrictRoute.value,
            autoRedirect = networkSettings.tunAutoRedirect.value,
            includeAndroidUser = networkSettings.tunIncludeAndroidUser.value,
            routeExcludeAddress = networkSettings.tunRouteExcludeAddress.value,
            dnsMode = networkSettings.tunDnsMode.value,
            fakeIpRange = networkSettings.tunFakeIpRange.value,
            fakeIpRange6 = networkSettings.tunFakeIpRange6.value,
            allowIpv6 = networkSettings.enableIPv6.value,
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
    ): String {
        val profileDir = context.importedDir.resolve(profileUuid)
        val metadataFile = context.filesDir.resolve("overrides/metadata.yaml")
        return sha256 {
            update(profileUuid.toByteArray())
            updateAgeSecretKeyDigest(ageSecretKey)
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
