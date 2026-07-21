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

package com.github.yumelira.yumebox.core.uds

import android.content.Context
import android.content.SharedPreferences

/**
 * Feature flag controlling the UDS transport mode.
 *
 * When enabled, the native mihomo engine runs as a separate Go process
 * and communicates via Unix Domain Socket instead of JNI.
 *
 * The flag is persisted in SharedPreferences and can be toggled at runtime.
 * A restart is required for the change to take effect.
 */
object UdsFeatureFlag {

    private const val KEY_UDS_ENABLED = "uds_transport_enabled"
    private const val PREFS_NAME = "uds_feature_flags"

    private var _override: Boolean? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Override the flag value for testing. Set to null to use persisted value.
     */
    fun setOverride(enabled: Boolean?) {
        _override = enabled
    }

    /**
     * Returns true if the UDS transport mode is enabled.
     */
    fun isEnabled(context: Context): Boolean {
        _override?.let { return it }
        return prefs(context).getBoolean(KEY_UDS_ENABLED, false)
    }

    /**
     * Enables or disables the UDS transport mode.
     * Requires app restart to take effect.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UDS_ENABLED, enabled).apply()
    }

    /**
     * Returns a human-readable summary of the current flag state.
     */
    fun describe(context: Context): String {
        val enabled = isEnabled(context)
        val source = if (_override != null) "override" else "persisted"
        return "UDS transport: ${if (enabled) "ENABLED" else "DISABLED"} ($source)"
    }
}
