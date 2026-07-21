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

import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a single UDS connection to the Go server.
 *
 * Wire format: 4-byte big-endian length prefix + JSON payload.
 *
 * Thread-safe: writes are serialised through a [Mutex]; reads happen on a
 * dedicated background thread. Multiple coroutines may call [call] concurrently.
 */
class UdsConnection(
    private val socketPath: String,
) : Closeable {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val writeMutex = Mutex()
    private val pending = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<UdsResponse>>()

    @Volatile
    private var closed = false

    /**
     * Connects to the UDS server. Throws if the connection cannot be established.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        check(!closed) { "Connection is closed" }

        val sock = LocalSocket()
        try {
            val addr = LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM)
            sock.connect(addr)
            socket = sock
            input = DataInputStream(sock.inputStream)
            output = DataOutputStream(sock.outputStream)

            Timber.tag(TAG).i("Connected to UDS server at %s", socketPath)

            // Start the reader loop in the background.
            startReaderLoop()
        } catch (e: IOException) {
            sock.close()
            throw IOException("Failed to connect to UDS server at $socketPath: ${e.message}", e)
        }
    }

    /**
     * Sends a request and waits for the response.
     */
    suspend fun call(method: String, params: kotlinx.serialization.json.JsonElement? = null): UdsResponse {
        val id = UUID.randomUUID().toString()
        val request = UdsRequest(id = id, method = method, params = params)

        val deferred = kotlinx.coroutines.CompletableDeferred<UdsResponse>()
        pending[id] = deferred

        try {
            writeRequest(request)
        } catch (e: Exception) {
            pending.remove(id)
            throw e
        }

        return deferred.await()
    }

    /**
     * Sends a request and expects an ok response. Throws on error.
     */
    suspend fun callOk(method: String, params: kotlinx.serialization.json.JsonElement? = null) {
        val resp = call(method, params)
        if (resp.error != null) {
            throw UdsException(resp.error.code, resp.error.message)
        }
    }

    /**
     * Sends a request and deserialises the result into [T].
     */
    suspend fun <T> callData(
        method: String,
        params: kotlinx.serialization.json.JsonElement? = null,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val resp = call(method, params)
        if (resp.error != null) {
            throw UdsException(resp.error.code, resp.error.message)
        }
        val result = resp.result ?: throw UdsException(-1, "null result")
        return json.decodeFromString(deserializer, result.toString())
    }

    /**
     * Serialises [params] to a [JsonElement] and calls [call].
     */
    suspend fun callWithObject(method: String, params: Any): UdsResponse {
        val element = json.encodeToJsonElement(
            kotlinx.serialization.json.JsonObject.serializer(),
            params as? kotlinx.serialization.json.JsonObject
                ?: throw IllegalArgumentException("params must be a JsonObject"),
        )
        return call(method, element)
    }

    override fun close() {
        closed = true
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        pending.values.forEach { it.completeExceptionally(IOException("Connection closed")) }
        pending.clear()
    }

    // ─── Wire codec ──────────────────────────────────────────────────────────

    private suspend fun writeRequest(request: UdsRequest) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val out = output ?: throw IOException("Not connected")
            val payload = json.encodeToString(UdsRequest.serializer(), request).toByteArray(Charsets.UTF_8)
            out.writeInt(payload.size)
            out.write(payload)
            out.flush()
        }
    }

    private fun startReaderLoop() {
        // Runs on IO dispatcher, reading messages from the socket.
        Thread({
            try {
                while (!closed) {
                    val msg = readRawMessage() ?: break
                    handleMessage(msg)
                }
            } catch (e: IOException) {
                if (!closed) {
                    Timber.tag(TAG).w(e, "Reader loop ended")
                }
            } finally {
                close()
            }
        }, "uds-reader").apply { isDaemon = true; start() }
    }

    private fun readRawMessage(): String? {
        val inp = input ?: return null
        val length = inp.readInt()
        if (length <= 0 || length > MAX_MESSAGE_SIZE) {
            throw IOException("Invalid message length: $length")
        }
        val buf = ByteArray(length)
        inp.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun handleMessage(raw: String) {
        try {
            // Try parsing as response first.
            val response = json.decodeFromString(UdsResponse.serializer(), raw)
            val deferred = pending.remove(response.id)
            if (deferred != null) {
                deferred.complete(response)
                return
            }

            // If no pending request matched, it might be an event.
            val event = json.decodeFromString(UdsEvent.serializer(), raw)
            handleEvent(event)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to handle message: %s", raw.take(200))
        }
    }

    private fun handleEvent(event: UdsEvent) {
        // Events are dispatched to registered handlers.
        eventHandlers.forEach { handler ->
            try {
                handler(event)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Event handler error for %s", event.event)
            }
        }
    }

    companion object {
        private const val TAG = "UdsConnection"
        private const val MAX_MESSAGE_SIZE = 16 * 1024 * 1024 // 16 MiB

        /** Global event handlers registered for all connections. */
        private val eventHandlers = mutableListOf<(UdsEvent) -> Unit>()

        fun addEventHandler(handler: (UdsEvent) -> Unit) {
            eventHandlers.add(handler)
        }

        fun removeEventHandler(handler: (UdsEvent) -> Unit) {
            eventHandlers.remove(handler)
        }
    }
}

/** Exception thrown when a UDS request fails. */
class UdsException(val code: Int, message: String) : IOException(message)
