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
import android.os.Build
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import timber.log.Timber

/**
 * Bootstraps the UDS transport mode.
 *
 * Call [initialize] from [App.onCreate] or early in the app lifecycle.
 * If the UDS feature flag is enabled, this will:
 * 1. Start the Go UDS server process
 * 2. Create a [UdsClashEngine] connected to it
 * 3. Switch [Clash] to UDS mode
 *
 * If the flag is disabled or startup fails, the app falls back to JNI mode.
 */
object UdsBootstrap {

    private var processManager: UdsProcessManager? = null

    /**
     * Initializes the UDS transport if the feature flag is enabled.
     *
     * @return true if UDS mode was successfully initialized, false for JNI fallback.
     */
    fun initialize(context: Context): Boolean {
        if (!UdsFeatureFlag.isEnabled(context)) {
            Timber.tag(TAG).d("UDS feature flag is disabled, using JNI mode")
            return false
        }

        return try {
            val ctx = context.applicationContext
            val home = ctx.runtimeHomeDir.apply { mkdirs() }.absolutePath
            val versionName = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
            } catch (_: Exception) { "unknown" }
            val sdkVersion = Build.VERSION.SDK_INT

            Timber.tag(TAG).i("Initializing UDS transport: home=%s version=%s sdk=%d", home, versionName, sdkVersion)

            val pm = UdsProcessManager(ctx)
            kotlinx.coroutines.runBlocking {
                val conn = pm.start(home, versionName, sdkVersion = sdkVersion)
                val engine = UdsClashEngine(conn) { pm.getEventSubscriber() }
                Clash.switchToUds(engine)
            }

            processManager = pm
            Timber.tag(TAG).i("UDS transport initialized successfully")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize UDS transport, falling back to JNI")
            Clash.switchToJni()
            processManager?.stop()
            processManager = null
            false
        }
    }

    /**
     * Returns the active [UdsProcessManager], or null if not in UDS mode.
     */
    fun getProcessManager(): UdsProcessManager? = processManager

    /**
     * Returns true if UDS mode is active.
     */
    fun isActive(): Boolean = Clash.isUdsMode

    /**
     * Shuts down the UDS transport and switches back to JNI mode.
     */
    fun shutdown() {
        Timber.tag(TAG).i("Shutting down UDS transport")
        Clash.switchToJni()
        processManager?.stop()
        processManager = null
    }

    private const val TAG = "UdsBootstrap"
}
