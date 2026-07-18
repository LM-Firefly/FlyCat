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

package com.github.yumelira.yumebox.runtime.client.remote

import android.content.Context
import com.github.yumelira.yumebox.core.Global
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.contract.RemoteControllerStoreReader
import com.github.yumelira.yumebox.runtime.api.service.remote.IClashManager
import com.github.yumelira.yumebox.runtime.api.service.remote.IProfileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

object ServiceClient {
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private val mutex = Mutex()
    private var initialized = false
    private var clashManager: IClashManager? = null
    private var profileManager: IProfileManager? = null
    private var remoteStore: RemoteControllerStoreReader? = null

    fun configure(store: RemoteControllerStoreReader) {
        remoteStore = store
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun connect(ctx: Context) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val appContext = ctx.appContextOrSelf
                if (initialized && clashManager != null && profileManager != null) {
                    return@withLock
                }

                val startedAt = System.currentTimeMillis()

                try {
                    withTimeout(CONNECT_TIMEOUT_MS) {
                        Global.init(appContext)
                        val store = remoteStore
                            ?: error("ServiceClient not configured: call configure() before connect()")
                        val httpManager =
                            HttpClashManager(backendProvider = { store.activeBackend() })
                        clashManager =
                            ClashGateway(
                                appContext,
                                remote = httpManager,
                                isRemoteControllerActive = {
                                    store.controllerEnabled.value &&
                                        store.activeBackend() != null
                                },
                            )
                        profileManager = instantiateServiceObject<IProfileManager>(
                            className = "com.github.yumelira.yumebox.runtime.service.ProfileManager",
                            context = appContext,
                        )
                        initialized = true
                    }
                    Timber.d(
                        "ServiceClient gateway initialized in pid=${android.os.Process.myPid()}, process=${android.app.Application.getProcessName()}, cost=${System.currentTimeMillis() - startedAt}ms"
                    )
                } catch (error: Exception) {
                    // fault barrier: gateway init spans MMKV/native/service wiring; reset state,
                    // log, and rethrow so the caller sees the original failure.
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

    fun disconnect() {
        clashManager = null
        profileManager = null
        initialized = false
    }

    fun clash(): IClashManager =
        clashManager ?: throw IllegalStateException("ServiceClient not connected")

    fun profile(): IProfileManager =
        profileManager ?: throw IllegalStateException("ServiceClient not connected")

    fun isConnected(): Boolean = initialized && clashManager != null && profileManager != null

    private inline fun <reified T> instantiateServiceObject(className: String, context: Context): T {
        val clazz = Class.forName(className)
        val instance = clazz.getConstructor(Context::class.java).newInstance(context)
        return (instance as? T) ?: error("$className does not implement ${T::class.java.name}")
    }
}
