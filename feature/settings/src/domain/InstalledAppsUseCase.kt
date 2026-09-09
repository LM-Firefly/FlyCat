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

package com.github.lmfirefly.flycat.feature.settings.domain

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.github.lmfirefly.flycat.runtime.api.contract.ProxyControlContract

/**
 * Encapsulates the installed-apps loading pipeline: permission check,
 * root shell fallback, and metadata extraction.
 */
class InstalledAppsUseCase(
    private val application: Application,
    private val proxyFacade: ProxyControlContract,
) {
    /**
     * Load all installed applications with metadata.
     * Falls back to root shell if SecurityException is thrown.
     *
     * @return List of [AppEntry] with package name, label, system flag, and timestamps.
     * @throws SecurityException if both normal and root queries fail.
     */
    fun loadInstalledApps(): List<AppEntry> {
        val pm = application.packageManager
        val selfPackageName = application.packageName

        val packages = runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }
            .getOrElse { error ->
                if (error is SecurityException) {
                    loadInstalledAppsFromRoot(pm, selfPackageName)
                } else {
                    throw error
                }
            }

        return packages
            .filter { it.packageName != selfPackageName }
            .map { appInfo ->
                val pkgInfo = runCatching { pm.getPackageInfo(appInfo.packageName, 0) }.getOrNull()
                AppEntry(
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(pm).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installTime = pkgInfo?.firstInstallTime ?: 0L,
                    updateTime = pkgInfo?.lastUpdateTime ?: 0L,
                )
            }
    }

    /**
     * Check if the current device is MIUI and requires special permission
     * for accessing installed apps list.
     */
    fun checkMiuiPermission(): Boolean {
        val permission = "com.android.permission.GET_INSTALLED_APPS"
        if (proxyFacade.hasRootPackageAccess()) return false
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            application, permission,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) return false
        return runCatching {
            val permissionInfo = application.packageManager.getPermissionInfo(permission, 0)
            permissionInfo.packageName == "com.lbe.security.miui"
        }.getOrDefault(false)
    }

    fun hasRootPackageAccess(): Boolean = proxyFacade.hasRootPackageAccess()

    private fun loadInstalledAppsFromRoot(
        pm: PackageManager,
        selfPackageName: String,
    ): List<ApplicationInfo> {
        val packageNames = proxyFacade.queryInstalledRootPackageNames()
            ?: throw SecurityException("Unable to query installed packages from root shell")
        return packageNames
            .asSequence()
            .filterNot { it == selfPackageName }
            .mapNotNull { packageName ->
                runCatching { pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA) }.getOrNull()
            }
            .toList()
    }

    data class AppEntry(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
        val installTime: Long = 0L,
        val updateTime: Long = 0L,
    )
}
