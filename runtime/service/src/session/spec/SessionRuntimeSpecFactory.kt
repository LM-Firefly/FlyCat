/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.runtime.service.session.spec

import android.content.Context
import com.github.lmfirefly.flycat.core.appContextOrSelf
import com.github.lmfirefly.flycat.core.importedDir
import com.github.lmfirefly.flycat.core.model.OverrideSpec
import com.github.lmfirefly.flycat.core.model.profile.Imported
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunConfig
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeOwner
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec
import com.github.lmfirefly.flycat.runtime.service.config.AccessControlMode
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.records.ImportedDao
import com.github.lmfirefly.flycat.runtime.service.root.EbpfOverride
import com.github.lmfirefly.flycat.runtime.service.root.RootTunConfigFactory
import com.github.lmfirefly.flycat.runtime.service.session.GlobalUaOverride
import com.github.lmfirefly.flycat.runtime.service.session.transport.TunOverride
import com.github.lmfirefly.flycat.runtime.service.util.directoryLastModified
import java.io.File
import java.security.MessageDigest

class SessionRuntimeSpecFactory(
    context: Context,
    private val store: ServiceStore = ServiceStore(),
) {
    private val context: Context = context.appContextOrSelf
    private val compiledConfigPipeline = CompiledConfigPipeline(this.context)

    fun createTunSpec(): RuntimeSpec = createLocalSpec()

    private fun createLocalSpec(): RuntimeSpec {
        val profile = requireActiveProfile()
        val profileDir = context.importedDir.resolve(profile.uuid.toString())
        val disableAllUserOverrides = store.disableAllOverride
        val runMode = RunMode.VpnService
        val userOverrides =
            if (disableAllUserOverrides) {
                emptyList()
            } else {
                compiledConfigPipeline.resolveOverrideSpecs(profile.uuid.toString())
            }
        val overrideSpecs = userOverrides + GlobalUaOverride.materialize(profileDir)
        val ageSecretKey = normalizeAgeSecretKey(profile.ageSecretKey)
        return RuntimeSpec(
            owner = RuntimeOwner.LocalTun,
            profileUuid = profile.uuid.toString(),
            profileName = profile.name,
            profileDir = profileDir.absolutePath,
            runtimeConfigPath = profileDir.resolve("runtime.yaml").absolutePath,
            ageSecretKey = ageSecretKey,
            overrideSpecs = overrideSpecs,
            runMode = runMode,
            skipRuntimePatches = disableAllUserOverrides,
            effectiveFingerprint =
                buildEffectiveFingerprint(profile.uuid.toString(), overrideSpecs, ageSecretKey, disableAllUserOverrides),
            profileFingerprint = buildProfileFingerprint(profile.uuid.toString()),
        )
    }

    fun createRootTunSpec(log: (String) -> Unit = {}): RuntimeSpec {
        val rootResult = RootTunConfigFactory(context).create(log)
        val profile =
            ImportedDao.queryByUUID(rootResult.profileUuid)
                ?: error("Root tun profile metadata not found: ${rootResult.profileUuid}")
        val profileDir = context.importedDir.resolve(rootResult.profileUuid.toString())
        val disableAllUserOverrides = store.disableAllOverride
        val skipModePatches = disableAllUserOverrides
        val userOverrides =
            if (disableAllUserOverrides) {
                emptyList()
            } else {
                compiledConfigPipeline.resolveOverrideSpecs(rootResult.profileUuid.toString())
            }
        val modeOverrides =
            if (!skipModePatches) {
                userOverrides + TunOverride.materialize(rootResult.config, profileDir)
            } else {
                userOverrides
            }
        val overrideSpecs = modeOverrides + GlobalUaOverride.materialize(profileDir)
        val ageSecretKey = normalizeAgeSecretKey(profile.ageSecretKey)
        return RuntimeSpec(
            owner = RuntimeOwner.RootTun,
            profileUuid = rootResult.profileUuid.toString(),
            profileName = rootResult.profileName,
            profileDir = rootResult.profileDir.absolutePath,
            runtimeConfigPath = rootResult.profileDir.resolve("runtime.yaml").absolutePath,
            ageSecretKey = ageSecretKey,
            overrideSpecs = overrideSpecs,
            runMode = RunMode.Tun,
            skipRuntimePatches = skipModePatches,
            rootTunConfig = rootResult.config,
            staticPlanFingerprint = rootResult.staticPlan.fingerprint,
            transportFingerprint = rootResult.dynamicOverrides.transportFingerprint,
            effectiveFingerprint =
                buildEffectiveFingerprint(
                    rootResult.profileUuid.toString(),
                    overrideSpecs,
                    ageSecretKey,
                    skipModePatches,
                ),
            profileFingerprint = rootResult.dynamicOverrides.profileFingerprint,
        )
    }

    /** Creates a spec for eBPF mode. Uses the active profile with eBPF override (tun disabled, mixed-port 7890). */
    fun createEbpfSpec(log: (String) -> Unit = {}): RuntimeSpec {
        val rootResult = RootTunConfigFactory(context).create(log)
        val profile =
            ImportedDao.queryByUUID(rootResult.profileUuid)
                ?: error("eBPF profile metadata not found: ${rootResult.profileUuid}")
        val profileDir = context.importedDir.resolve(rootResult.profileUuid.toString())
        val disableAllUserOverrides = store.disableAllOverride
        val userOverrides =
            if (disableAllUserOverrides) {
                emptyList()
            } else {
                compiledConfigPipeline.resolveOverrideSpecs(rootResult.profileUuid.toString())
            }
        // eBPF模式：可选中国规则绕行覆盖（eBPF监听器+中国规则提供者）
        val ebpfOverride = EbpfOverride.materialize(EbpfOverride.Config(bypassCn = store.ebpfBypassCn), profileDir)
        val modeOverrides = userOverrides + listOfNotNull(ebpfOverride)
        // eBPF keeps the profile authoritative — skip GlobalUaOverride.
        val overrideSpecs = modeOverrides
        val ageSecretKey = normalizeAgeSecretKey(profile.ageSecretKey)
        return RuntimeSpec(
            owner = RuntimeOwner.RootTun,
            profileUuid = rootResult.profileUuid.toString(),
            profileName = rootResult.profileName,
            profileDir = rootResult.profileDir.absolutePath,
            runtimeConfigPath = rootResult.profileDir.resolve("runtime.yaml").absolutePath,
            ageSecretKey = ageSecretKey,
            overrideSpecs = overrideSpecs,
            runMode = RunMode.Ebpf,
            skipRuntimePatches = false, // eBPF keeps DNS/path injection
            rootTunConfig = rootResult.config, // eBPF reuses the profile config; transport applies EbpfOverride on top
            staticPlanFingerprint = rootResult.staticPlan.fingerprint,
            transportFingerprint = rootResult.dynamicOverrides.transportFingerprint,
            effectiveFingerprint =
                buildEffectiveFingerprint(
                    rootResult.profileUuid.toString(),
                    overrideSpecs,
                    ageSecretKey,
                    skipRuntimePatches = false,
                ),
            profileFingerprint = rootResult.dynamicOverrides.profileFingerprint,
        )
    }

    /** Native eBPF policy uses the same package-to-UID resolution as Root Tun. */
    fun resolveEbpfUidPolicy(): EbpfUidPolicy {
        val access = resolveTunAccessControl()
        return when (store.accessControlMode) {
            AccessControlMode.AcceptAll -> EbpfUidPolicy(mode = 0, uids = emptyList())
            AccessControlMode.AcceptSelected -> EbpfUidPolicy(mode = 1, uids = access.includeUid)
            AccessControlMode.RejectSelected -> EbpfUidPolicy(mode = 2, uids = access.excludeUid)
            AccessControlMode.RejectAll -> EbpfUidPolicy(mode = 1, uids = listOf(context.applicationInfo.uid))
        }
    }

    data class EbpfUidPolicy(
        val mode: Int,
        val uids: List<Int>,
    )

    private fun buildTunConfig(): TunConfig {
        val access = resolveTunAccessControl()
        return TunConfig(
            ifName = store.rootTunIfName,
            mtu = store.rootTunMtu,
            stack = store.tunStackMode,
            autoRoute = store.rootTunAutoRoute,
            strictRoute = store.rootTunStrictRoute,
            autoRedirect = store.rootTunAutoRedirect,
            includeUid = access.includeUid,
            excludeUid = access.excludeUid,
            includeAndroidUser = access.includeAndroidUser,
            routeExcludeAddress = store.rootTunRouteExcludeAddress,
            dnsMode = store.tunDnsMode,
            fakeIpRange = store.rootTunFakeIpRange,
            fakeIpRange6 = store.rootTunFakeIpRange6,
            allowIpv6 = store.allowIpv6,
        )
    }

    private fun resolveTunAccessControl(): TunAccessControl {
        val self = context.applicationInfo.uid
        val selectedPackages =
            store.accessControlPackages.map(String::trim).filter(String::isNotEmpty).toSet()
        val selectedUid =
            selectedPackages
                .mapNotNull { pkg -> resolvePackageUid(pkg) }
                .filter { it != self }
                .distinct()
                .sorted()
        val allUsers = resolveIncludeAndroidUsers()
        return when (store.accessControlMode) {
            AccessControlMode.AcceptAll -> TunAccessControl(includeAndroidUser = allUsers)
            AccessControlMode.AcceptSelected -> TunAccessControl(includeUid = selectedUid)
            AccessControlMode.RejectSelected -> TunAccessControl(excludeUid = selectedUid, includeAndroidUser = allUsers)
            // Whitelist only ourselves ⇒ no other app is ever routed into the tun.
            AccessControlMode.RejectAll -> TunAccessControl(includeUid = listOf(self))
        }
    }

    /**
     * Empty list means "all Android users": TunOverride omits include-android-user and sing-tun does not install per-user ExcludeUID ranges.
     * The old hard-coded default [0, 10] only kept owner + work-profile traffic and silently dropped every other multi-user profile.
     */
    private fun resolveIncludeAndroidUsers(): List<Int> {
        val users = store.rootTunIncludeAndroidUser
        if (users == LEGACY_INCLUDE_ANDROID_USERS) {
            store.rootTunIncludeAndroidUser = emptyList()
            return emptyList()
        }
        return users
    }

    private fun resolvePackageUid(pkg: String): Int? = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).applicationInfo?.uid
    }.getOrNull()

    private data class TunAccessControl(
        val includeUid: List<Int> = emptyList(),
        val excludeUid: List<Int> = emptyList(),
        val includeAndroidUser: List<Int> = emptyList(),
    )

    private companion object {
        private val LEGACY_INCLUDE_ANDROID_USERS = listOf(0, 10)
    }

    private fun requireActiveProfile(): Imported {
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
        // Stream path + size + mtime + content hash without loading the whole file into a byte[].
        update(file.absolutePath.toByteArray())
        update(file.length().toString().toByteArray())
        update(file.lastModified().toString().toByteArray())
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                update(buffer, 0, read)
            }
        }
    }

    private fun normalizeAgeSecretKey(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun sha256String(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
