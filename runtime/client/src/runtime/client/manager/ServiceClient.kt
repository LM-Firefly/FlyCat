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

package com.github.yumelira.yumebox.runtime.client.manager

import android.content.Context
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.api.IProfileManager
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.api.initializeServiceGlobal
import com.github.yumelira.yumebox.runtime.service.profile.ProfileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

object ServiceClient {
    private val mutex = Mutex()
    private var initialized = false
    private var clashManager: IClashManager? = null
    private var profileManager: IProfileManager? = null

    suspend fun connect(ctx: Context) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val appContext = ctx.appContextOrSelf
                if (initialized && clashManager != null && profileManager != null) {
                    return@withLock
                }

                val startedAt = System.currentTimeMillis()

                try {
                    initializeServiceGlobal(appContext)
                    val remoteStore =
                        RemoteControllerStore(MMKVProvider().getMMKV("remote_controller"))
                    val httpManager =
                        HttpClashManager(backendProvider = { remoteStore.activeBackend() })
                    clashManager =
                        ClashGateway(
                            appContext,
                            remote = httpManager,
                            isRemoteControllerActive = {
                                remoteStore.controllerEnabled.value &&
                                    remoteStore.activeBackend() != null
                            },
                        )
                    profileManager = ProfileManager(appContext)
                    initialized = true
                    Timber.d(
                        "ServiceClient gateway initialized in pid=${android.os.Process.myPid()}, process=${android.app.Application.getProcessName()}, cost=${System.currentTimeMillis() - startedAt}ms"
                    )
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    initialized = false
                    clashManager = null
                    profileManager = null
                    Timber.e(error, "Failed to initialize local service gateway")
                    throw error
                }
            }
        }
    }

    suspend fun disconnect() {
        clashManager = null
        profileManager = null
        initialized = false
    }

    suspend fun clash(): IClashManager =
        clashManager ?: throw IllegalStateException("ServiceClient not connected")

    suspend fun profile(): IProfileManager =
        profileManager ?: throw IllegalStateException("ServiceClient not connected")

    fun isConnected(): Boolean = initialized && clashManager != null && profileManager != null
}
