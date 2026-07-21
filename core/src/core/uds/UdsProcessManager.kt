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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Manages the lifecycle of the Go UDS server process.
 *
 * Responsibilities:
 * - Start the Go binary as a child process
 * - Wait for the UDS socket to become ready
 * - Establish a [UdsConnection]
 * - Health-check and auto-restart on crash
 * - Graceful shutdown
 */
class UdsProcessManager(
    private val context: Context,
) {
    private var process: Process? = null
    private var connection: UdsConnection? = null
    private var eventSubscriber: UdsEventSubscriber? = null
    private var callbackHandler: UdsCallbackHandler? = null

    @Volatile
    private var running = false

    /** Path to the Go binary in the app's native library directory. */
    private val nativeBinaryPath: String
        get() = File(context.applicationInfo.nativeLibraryDir, "libclashd.so").absolutePath

    /** UDS socket path in the app's private files directory. */
    val socketPath: String
        get() = File(context.filesDir, "clash.sock").absolutePath

    /**
     * Starts the Go process and establishes a UDS connection.
     *
     * @param home mihomo home directory
     * @param versionName app version name
     * @param gitVersion git version string (branch_hash_time)
     * @param sdkVersion Android SDK version
     * @param connectTimeoutMs how long to wait for the socket to appear
     */
    suspend fun start(
        home: String,
        versionName: String,
        gitVersion: String = "",
        sdkVersion: Int = 0,
        connectTimeoutMs: Long = 10_000,
    ): UdsConnection = withContext(Dispatchers.IO) {
        check(!running) { "Go process is already running" }

        Timber.tag(TAG).i("Starting Go UDS server: binary=%s socket=%s", nativeBinaryPath, socketPath)

        // Remove stale socket.
        File(socketPath).delete()

        // Launch the Go binary.
        val pb = ProcessBuilder(
            nativeBinaryPath,
            "--socket", socketPath,
            "--home", home,
            "--version", versionName,
            "--git-version", gitVersion,
            "--sdk", sdkVersion.toString(),
        )
        pb.redirectErrorStream(true)
        pb.environment()["TMPDIR"] = context.cacheDir.absolutePath

        try {
            process = pb.start()
            running = true

            // Consume stdout/stderr in background.
            consumeProcessOutput(process!!)

            // Wait for socket to appear.
            waitForSocket(connectTimeoutMs)

            // Connect.
            val conn = UdsConnection(socketPath)
            conn.connect()
            connection = conn

            // Send core.init.
            conn.callOk("core.init", buildJsonObject {
                put("home", home)
                put("versionName", versionName)
                put("gitVersion", gitVersion)
                put("sdkVersion", sdkVersion)
            })

            Timber.tag(TAG).i("Go UDS server started and initialised")

            // Open a dedicated event subscriber connection.
            try {
                val subscriber = UdsEventSubscriber(socketPath)
                subscriber.connect()
                eventSubscriber = subscriber
                Timber.tag(TAG).i("Event subscriber connected")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to connect event subscriber (non-fatal)")
            }

            // Open a reverse callback connection for socket owner queries.
            try {
                val callback = UdsCallbackHandler(socketPath, context)
                callback.connect()
                callbackHandler = callback
                Timber.tag(TAG).i("Callback handler connected")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to connect callback handler (non-fatal)")
            }

            conn
        } catch (e: Exception) {
            stop()
            throw IOException("Failed to start Go UDS server: ${e.message}", e)
        }
    }

    /**
     * Returns the active connection, or null if not started.
     */
    fun getConnection(): UdsConnection? = connection

    /**
     * Returns the active event subscriber, or null if not started.
     */
    fun getEventSubscriber(): UdsEventSubscriber? = eventSubscriber

    /**
     * Returns the active callback handler, or null if not started.
     */
    fun getCallbackHandler(): UdsCallbackHandler? = callbackHandler

    /**
     * Returns true if the Go process is running and the connection is open.
     */
    fun isRunning(): Boolean = running && connection?.let { true } ?: false

    /**
     * Stops the Go process and closes the connection.
     */
    fun stop() {
        running = false
        try {
            callbackHandler?.close()
        } catch (_: Exception) {
        }
        callbackHandler = null
        try {
            eventSubscriber?.close()
        } catch (_: Exception) {
        }
        eventSubscriber = null
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null

        try {
            process?.destroy()
            process?.waitFor()
        } catch (_: Exception) {
        }
        process = null

        File(socketPath).delete()
        Timber.tag(TAG).i("Go UDS server stopped")
    }

    /**
     * Checks if the connection is alive by sending a ping.
     */
    suspend fun healthCheck(): Boolean {
        val conn = connection ?: return false
        return try {
            val resp = conn.call("core.ping")
            resp.error == null
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Health check failed")
            false
        }
    }

    /**
     * Restarts the Go process. Used for crash recovery.
     */
    suspend fun restart(
        home: String,
        versionName: String,
        gitVersion: String = "",
        sdkVersion: Int = 0,
    ): UdsConnection {
        stop()
        return start(home, versionName, gitVersion, sdkVersion)
    }

    private suspend fun waitForSocket(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val socketFile = File(socketPath)

        while (System.currentTimeMillis() < deadline) {
            if (socketFile.exists()) {
                Timber.tag(TAG).d("Socket file appeared at %s", socketPath)
                return
            }
            delay(50)
        }

        throw IOException("Timed out waiting for UDS socket at $socketPath after ${timeoutMs}ms")
    }

    private fun consumeProcessOutput(process: Process) {
        Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    Timber.tag(GO_TAG).d("%s", line)
                }
            } catch (_: Exception) {
            }
        }, "go-stdout").apply { isDaemon = true; start() }
    }

    companion object {
        private const val TAG = "UdsProcessManager"
        private const val GO_TAG = "clashd"
    }
}
