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

package com.github.lmfirefly.flycat.runtime.service.root

import android.content.Context
import com.github.lmfirefly.flycat.core.importedDir
import com.github.lmfirefly.flycat.core.model.tunnel.TunConfig
import com.github.lmfirefly.flycat.core.model.tunnel.TunDnsMode
import com.github.lmfirefly.flycat.runtime.api.root.rootTunEncode
import com.github.lmfirefly.flycat.runtime.service.config.ServiceStore
import com.github.lmfirefly.flycat.runtime.service.records.ImportedDao
import com.github.lmfirefly.flycat.runtime.service.records.ProfileStore
import com.github.lmfirefly.flycat.runtime.service.session.telemetry.RuntimeStartupLogStore
import com.github.lmfirefly.flycat.runtime.service.util.directoryLastModified
import java.io.File
import java.util.UUID

class RootTunConfigFactory(
    private val context: Context,
    private val store: ServiceStore = ServiceStore(),
) {
    private val packageResolver = RootTunPackageResolver(context, store)
    private val startupLogStore =
        RuntimeStartupLogStore(context, RuntimeStartupLogStore.Scope.ROOT_TUN)

    data class StaticRootTunPlan(
        val fingerprint: String,
        val ifName: String,
        val mtu: Int,
        val dnsHijack: List<String>,
        val autoRoute: Boolean,
        val strictRoute: Boolean,
        val autoRedirect: Boolean,
        val includeUid: List<Int>,
        val excludeUid: List<Int>,
        val includeAndroidUser: List<Int>,
        val routeAddress: List<String>,
        val routeExcludeAddress: List<String>,
        val dnsMode: TunDnsMode,
        val fakeIpRange: String?,
        val fakeIpRange6: String?,
        val allowIpv6: Boolean,
        val missingPackages: Set<String>,
    )

    data class DynamicRootTunOverrides(
        val transportFingerprint: String,
        val profileFingerprint: String,
    )

    data class Result(
        val profileUuid: UUID,
        val profileName: String,
        val profileDir: File,
        val staticPlan: StaticRootTunPlan,
        val dynamicOverrides: DynamicRootTunOverrides,
        val config: TunConfig,
    )

    /**
     * Builds the RootTun spec. [log] receives the startup trace; callers in the root process pass
     * the ROOT_TUN store's append (single writer), while main-process query paths pass a no-op so
     * they never write to / truncate `root_tun_startup.log`.
     */
    fun create(log: (String) -> Unit = {}): Result {
        val startedAt = System.currentTimeMillis()
        log(formatProfilesStoreLine())

        log("ROOT_TUN factory: resolve active profile")
        val activeProfile = store.activeProfile ?: error("No active profile selected")
        val imported =
            ImportedDao.queryByUUID(activeProfile)
                ?: error("Active profile metadata not found: $activeProfile")
        val profileDir = context.importedDir.resolve(imported.uuid.toString())
        log("ROOT_TUN factory: activeProfile=${imported.uuid} name=${imported.name}")

        val staticPlanResolveAt = System.currentTimeMillis()
        log("ROOT_TUN factory: resolve static transport plan")
        val staticPlan = resolveStaticPlan()
        val staticPlanResolveCost = System.currentTimeMillis() - staticPlanResolveAt
        log("ROOT_TUN factory: static transport plan done ${staticPlanResolveCost}ms")

        val stack = firstNonBlank(store.tunStackMode) ?: "system"
        val allowIpv6 = staticPlan.allowIpv6
        val config =
            TunConfig(
                ifName = staticPlan.ifName,
                mtu = staticPlan.mtu,
                stack = stack,
                inet4Address = listOf(INET4),
                inet6Address = if (allowIpv6) listOf(INET6) else emptyList(),
                dnsHijack = staticPlan.dnsHijack,
                autoRoute = staticPlan.autoRoute,
                strictRoute = staticPlan.strictRoute,
                autoRedirect = staticPlan.autoRedirect,
                includeUid = staticPlan.includeUid,
                excludeUid = staticPlan.excludeUid,
                includeAndroidUser = staticPlan.includeAndroidUser,
                routeAddress = staticPlan.routeAddress,
                routeExcludeAddress = staticPlan.routeExcludeAddress,
                dnsMode = staticPlan.dnsMode,
                fakeIpRange = staticPlan.fakeIpRange,
                fakeIpRange6 = staticPlan.fakeIpRange6,
                allowIpv6 = allowIpv6,
                debugLogPath = startupLogStore.path(),
            )
        val transportFingerprint = buildTransportFingerprint(config)
        val dynamicOverrides =
            DynamicRootTunOverrides(
                transportFingerprint = transportFingerprint,
                profileFingerprint =
                    buildProfileFingerprint(imported.uuid, profileDir.directoryLastModified ?: -1L),
            )
        log("ROOT_TUN factory: derived TunConfig transportFingerprint=$transportFingerprint")

        log(
            "ROOT_TUN factory: timings staticPlan=${staticPlanResolveCost}ms total=${System.currentTimeMillis() - startedAt}ms"
        )

        val summary = buildString {
            append("ROOT_TUN factory: includeUid=")
            append(config.includeUid.size)
            append(", excludeUid=")
            append(config.excludeUid)
            append(", dnsHijack=")
            append(config.dnsHijack)
            append(", routeAddress=")
            append(config.routeAddress.size)
            if (staticPlan.missingPackages.isNotEmpty()) {
                append(", missingPackages=")
                append(staticPlan.missingPackages)
            }
        }
        log(summary)
        log("ROOT_TUN factory: config=" + rootTunEncode(config))

        return Result(
            profileUuid = imported.uuid,
            profileName = imported.name,
            profileDir = profileDir,
            staticPlan = staticPlan,
            dynamicOverrides = dynamicOverrides,
            config = config,
        )
    }

    private fun resolveStaticPlan(): StaticRootTunPlan {
        val fingerprint = buildStaticPlanFingerprint()
        val cached = cachedStaticPlan
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached
        }

        val uidPlan = packageResolver.resolve()
        val dnsMode = store.tunDnsMode
        val plan =
            StaticRootTunPlan(
                fingerprint = fingerprint,
                ifName = firstNonBlank(store.rootTunIfName) ?: IF_NAME,
                mtu = store.rootTunMtu.coerceAtLeast(1),
                dnsHijack = resolveDnsHijack(),
                autoRoute = store.rootTunAutoRoute,
                strictRoute = store.rootTunStrictRoute,
                autoRedirect = store.rootTunAutoRedirect,
                includeUid = uidPlan.includeUid,
                excludeUid = uidPlan.excludeUid,
                includeAndroidUser = resolveIncludeAndroidUser(store.rootTunIncludeAndroidUser),
                routeAddress = resolveRouteAddress(store.allowIpv6),
                routeExcludeAddress =
                    store.rootTunRouteExcludeAddress.map(String::trim).filter(String::isNotEmpty),
                dnsMode = dnsMode,
                fakeIpRange =
                    resolveFakeIpRange(
                        dnsMode,
                        store.rootTunFakeIpRange,
                        FAKE_IP_RANGE,
                    ),
                fakeIpRange6 =
                    resolveFakeIpRange(
                        dnsMode,
                        store.rootTunFakeIpRange6,
                        FAKE_IP_RANGE6,
                    ),
                allowIpv6 = store.allowIpv6,
                missingPackages = uidPlan.missingPackages,
            )
        cachedStaticPlan = plan
        return plan
    }

    private fun buildStaticPlanFingerprint(): String =
        buildString {
                append(store.accessControlMode.name)
                append('|')
                append(store.accessControlPackages.sorted().joinToString(","))
                append('|')
                append(store.allowIpv6)
                append('|')
                append(store.bypassPrivateNetwork)
                append('|')
                append(store.dnsHijacking)
                append('|')
                append(store.tunStackMode)
                append('|')
                append(store.rootTunIfName.trim())
                append('|')
                append(store.rootTunMtu)
                append('|')
                append(store.rootTunAutoRoute)
                append('|')
                append(store.rootTunStrictRoute)
                append('|')
                append(store.rootTunAutoRedirect)
                append('|')
                append(store.rootTunIncludeAndroidUser.joinToString(","))
                append('|')
                append(store.rootTunRouteExcludeAddress.joinToString(","))
                append('|')
                append(store.tunDnsMode.name)
                append('|')
                append(store.rootTunFakeIpRange.trim())
                append('|')
                append(store.rootTunFakeIpRange6.trim())
            }
            .hashCode()
            .toString()

    private fun buildTransportFingerprint(config: TunConfig): String =
        listOf(
                config.ifName,
                config.mtu.toString(),
                config.stack,
                config.dnsMode.name,
                config.fakeIpRange.orEmpty(),
                config.fakeIpRange6.orEmpty(),
                config.allowIpv6.toString(),
                config.dnsHijack.joinToString(","),
                config.routeAddress.joinToString(","),
                config.routeExcludeAddress.joinToString(","),
                config.includeUid.joinToString(","),
                config.excludeUid.joinToString(","),
                config.includeAndroidUser.joinToString(","),
                config.autoRoute.toString(),
                config.strictRoute.toString(),
                config.autoRedirect.toString(),
            )
            .joinToString("|")
            .hashCode()
            .toString()

    private fun buildProfileFingerprint(profileUuid: UUID, updatedAt: Long): String =
        "$profileUuid|$updatedAt"

    private fun formatProfilesStoreLine(): String {
        val keyCount = runCatching { ProfileStore.countStoredKeys() }.getOrDefault(0)
        return "<MMKV_IO.cpp:133::loadFromFile> loaded [profiles] with $keyCount key-values"
    }

    /**
     * Maps the stored include-android-user list to the TunConfig field.
     * Legacy value [0, 10] is migrated to empty list (= all Android users), matching YumeBox's semantics where empty means "all users".
     */
    private fun resolveIncludeAndroidUser(stored: List<Int>): List<Int> {
        val LEGACY_INCLUDE_ANDROID_USERS = listOf(0, 10)
        val filtered = stored.filter { it >= 0 }.distinct().sorted()
        if (filtered == LEGACY_INCLUDE_ANDROID_USERS) {
            // Legacy migration: [0, 10] → empty (= all Android users)
            return emptyList()
        }
        return filtered // empty = all users; non-empty = specific users
    }

    private fun resolveDnsHijack(): List<String> {
        if (!store.dnsHijacking) return emptyList()
        return defaultDnsHijack
    }

    private fun resolveRouteAddress(allowIpv6: Boolean): List<String> {
        if (!store.bypassPrivateNetwork) return emptyList()

        val values = buildList {
            addAll(
                context.resources.getStringArray(
                    com.github.lmfirefly.flycat.runtime.service.R.array.bypass_private_route
                )
            )
            if (allowIpv6) {
                addAll(
                    context.resources.getStringArray(
                        com.github.lmfirefly.flycat.runtime.service.R.array.bypass_private_route6
                    )
                )
            }
        }

        return values.map(String::trim).filter(String::isNotEmpty)
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun resolveFakeIpRange(
        dnsMode: TunDnsMode,
        value: String,
        fallback: String,
    ): String? {
        if (dnsMode != TunDnsMode.FakeIp) return null
        return firstNonBlank(value, fallback)
    }

    companion object {
        private const val IF_NAME = "Yume"
        private const val MTU = 1500
        private const val INET4 = "172.19.0.1/30"
        private const val INET6 = "fdfe:dcba:9876::1/126"
        private const val FAKE_IP_RANGE = "198.18.0.1/16"
        private const val FAKE_IP_RANGE6 = "fc00::/18"
        private val defaultDnsHijack = listOf("any:53", "tcp://any:53")

        @Volatile private var cachedStaticPlan: StaticRootTunPlan? = null
    }
}
