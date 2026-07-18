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

package com.github.yumelira.yumebox.feature.substore

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.github.yumelira.yumebox.core.util.NetworkUtil
import com.github.yumelira.yumebox.feature.substore.engine.NativeLibraryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tf.gal.yumebox.locale.FlyTxt
import timber.log.Timber

class SubStoreService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var caseEngine: CaseEngine? = null
    @Volatile private var isStarting = false
    @Volatile private var isRunning = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning || isStarting) return START_STICKY

        val request = SubStoreServiceController.requestFrom(intent)
        Timber.w("Sub-Store onStartCommand: frontend=${request.frontendPort}, backend=${request.backendPort}, allowLan=${request.allowLan}")
        isStarting = true
        serviceScope.launch {
            runCatching {
                if (
                    NetworkUtil.isPortInUse(request.frontendPort) ||
                        NetworkUtil.isPortInUse(request.backendPort)
                ) {
                    error(FlyTxt.Feature.SubStore.PortOccupied.format(request.frontendPort, request.backendPort))
                }

                if (!ensureJavetLibraryLoaded()) {
                    error(FlyTxt.Feature.SubStore.JavetLoadFailed)
                }

                val engine =
                    CaseEngine(
                        backendPort = request.backendPort,
                        frontendPort = request.frontendPort,
                        allowLan = request.allowLan,
                    )
                caseEngine = engine

                if (!engine.isInitialized()) {
                    error(FlyTxt.Feature.SubStore.CaseEngineInitFailed)
                }

                caseEngine = engine
                engine.startServer()
                val frontendReady =
                    NetworkUtil.waitForPortReady(host = "127.0.0.1", port = request.frontendPort)
                val backendReady =
                    NetworkUtil.waitForPortReady(host = "127.0.0.1", port = request.backendPort)
                Timber.w("Sub-Store port readiness: frontend=${request.frontendPort} ready=$frontendReady, backend=${request.backendPort} ready=$backendReady")
                if (!frontendReady || !backendReady) {
                    throw Exception("Sub-Store 启动超时（frontend:${request.frontendPort} ready=$frontendReady, backend:${request.backendPort} ready=$backendReady）")
                }
                isRunning = true
                SubStoreServiceController.markRunning()
                Timber.i("Sub-Store started: frontend=${request.frontendPort}, backend=${request.backendPort}, allowLan=${request.allowLan}")
            }
            .getOrElse { error ->
                Timber.e(error, "Sub-Store service start failed")
                cleanupService()
                stopSelf(startId)
            }
            isStarting = false
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cleanupService()
    }

    private fun ensureJavetLibraryLoaded(): Boolean =
        runCatching {
                NativeLibraryManager.initialize(applicationContext)
                val javetLibBaseName = "libjavet-node-android"

                if (!NativeLibraryManager.isLibraryAvailable(javetLibBaseName)) {
                    val results = NativeLibraryManager.extractAllLibraries()
                    if (results[javetLibBaseName] != true) {
                        Timber.e("Javet extract failed")
                        return false
                    }
                }
                true
            }
            .getOrElse { error ->
                Timber.e(error, "Javet load error")
                false
            }

    private fun cleanupService() {
        runCatching { caseEngine?.stopServer() }
            .onFailure { error -> Timber.e(error, "Failed to stop Sub-Store service") }
        caseEngine = null
        isRunning = false
        isStarting = false
        SubStoreServiceController.markStopped()
    }
}
