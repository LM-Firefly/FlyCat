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

package com.github.yumelira.yumebox.platform.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

object AppIconHelper {
    private const val MAIN_ACTIVITY_ALIAS = "com.github.yumelira.yumebox.MainActivityAlias"

    fun hideIcon(context: Context) {
        setIconState(context, hide = true)
    }

    fun showIcon(context: Context) {
        setIconState(context, hide = false)
    }

    private fun setIconState(context: Context, hide: Boolean) {
        val pm = context.packageManager
        val alias = ComponentName(context.packageName, MAIN_ACTIVITY_ALIAS)
        val target =
            if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

        runCatching {
                val current = pm.getComponentEnabledSetting(alias)
                val unchanged =
                    current == target ||
                        (!hide && current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                if (unchanged) return

                val main =
                    ComponentName(context.packageName, "com.github.yumelira.yumebox.MainActivity")
                pm.setComponentEnabledSetting(
                    main,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                pm.setComponentEnabledSetting(alias, target, PackageManager.DONT_KILL_APP)
            }
            .onFailure { error ->
                Timber.w(error, "Failed to ${if (hide) "hide" else "show"} app icon")
            }
    }

    fun toggleIcon(context: Context, hide: Boolean) {
        if (hide) {
            hideIcon(context)
        } else {
            showIcon(context)
        }
    }
}
