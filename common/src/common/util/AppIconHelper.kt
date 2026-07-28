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

package com.github.yumelira.yumebox.common.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

object AppIconHelper {
    private const val DEFAULT_ACTIVITY_ALIAS = "com.github.yumelira.yumebox.MainActivityAlias"
    private const val CLASSIC_ACTIVITY_ALIAS =
        "com.github.yumelira.yumebox.MainActivityAliasClassic"
    private const val MAIN_ACTIVITY = "com.github.yumelira.yumebox.MainActivity"

    fun hideIcon(context: Context) {
        applyLauncherState(context, classic = false, hide = true)
    }

    fun showIcon(context: Context, classic: Boolean = false) {
        applyLauncherState(context, classic = classic, hide = false)
    }

    fun toggleIcon(context: Context, hide: Boolean, classic: Boolean = false) {
        applyLauncherState(context, classic = classic, hide = hide)
    }

    fun applyStyle(context: Context, classic: Boolean, hide: Boolean) {
        applyLauncherState(context, classic = classic, hide = hide)
    }

    private fun applyLauncherState(context: Context, classic: Boolean, hide: Boolean) {
        val pm = context.packageManager
        val defaultAlias = ComponentName(context.packageName, DEFAULT_ACTIVITY_ALIAS)
        val classicAlias = ComponentName(context.packageName, CLASSIC_ACTIVITY_ALIAS)
        val defaultTarget =
            when {
                hide -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                classic -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
        val classicTarget =
            when {
                hide -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                classic -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

        runCatching {
            val defaultUnchanged = isComponentAt(pm, defaultAlias, defaultTarget, enabledByDefault = true)
            val classicUnchanged =
                isComponentAt(pm, classicAlias, classicTarget, enabledByDefault = false)
            if (defaultUnchanged && classicUnchanged) return

            val main = ComponentName(context.packageName, MAIN_ACTIVITY)
            pm.setComponentEnabledSetting(
                main,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(defaultAlias, defaultTarget, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(classicAlias, classicTarget, PackageManager.DONT_KILL_APP)
        }
            .onFailure { error ->
                Timber.w(
                    error,
                    "Failed to apply launcher icon state hide=%s classic=%s",
                    hide,
                    classic,
                )
            }
    }

    private fun isComponentAt(
        pm: PackageManager,
        component: ComponentName,
        target: Int,
        enabledByDefault: Boolean,
    ): Boolean {
        val current = pm.getComponentEnabledSetting(component)
        if (current == target) return true
        return current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
            ((enabledByDefault && target == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) ||
                (!enabledByDefault && target == PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
    }
}
