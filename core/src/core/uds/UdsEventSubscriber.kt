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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Dedicated UDS event subscriber connection.
 *
 * Opens a second connection to the Go server, sends `event.subscribe`,
 * then continuously reads pushed [UdsEvent] messages and dispatches them
 * to typed [SharedFlow]s and [Channel]s.
 *
 * Usage:
 * ```kotlin
 * val subscriber = UdsEventSubscriber(socketPath)
 * subscriber.connect()
 * subscriber.trafficEvents.collect { ... }
 * subscriber.logEvents.collect { ... }
 * ```
 */
class UdsEventSubscriber(
    private val socketPath: String,
) : Closeable {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null

    @Volatile
    private var closed = false

    // ─── Typed event flows ────────────────────────────────────────────────────

    private val _trafficEvents = MutableSharedFlow<UdsTrafficEvent>(replay = 1, extraBufferCapacity = 64)
    /** Hot flow of traffic events pushed by the Go server. */
    val trafficEvents: SharedFlow<UdsTrafficEvent> = _trafficEvents.asSharedFlow()

    private val _tunnelStateEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    /** Hot flow of tunnel state (mode) change events. Emits the new mode string. */
    val tunnelStateEvents: SharedFlow<String> = _tunnelStateEvents.asSharedFlow()

    private val _logChannel = Channel<UdsLogEvent>(Channel.BUFFERED)
    /** Channel of log events pushed by the Go server. */
    val logEvents: ReceiveChannel<UdsLogEvent> = _logChannel

    private val _rawEvents = MutableSharedFlow<UdsEvent>(extraBufferCapacity = 64)
    /** Hot flow of all raw events (for advanced consumers). */
    val rawEvents: SharedFlow<UdsEvent> = _rawEvents.asSharedFlow()

    // ─── Connection lifecycle ─────────────────────────────────────────────────

    /**
     * Connects to the Go server and sends the event subscription request.
     * Starts a background reader loop.
     */
    fun connect() {
        check(!closed) { "Subscriber is closed" }

        val sock = LocalSocket()
        try {
            val addr = LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM)
            sock.connect(addr)
            socket = sock
            input = DataInputStream(sock.inputStream)

            Timber.tag(TAG).i("Event subscriber connected to %s", socketPath)

            // Send event.subscribe request.
            val outputStream = sock.outputStream
            val subscribeReq = """{"id":"event-sub","method":"event.subscribe"}""".toByteArray(Charsets.UTF_8)
            val header = ByteArray(4)
            header[0] = (subscribeReq.size shr 24).toByte()
            header[1] = (subscribeReq.size shr 16).toByte()
            header[2] = (subscribeReq.size shr 8).toByte()
            header[3] = subscribeReq.size.toByte()
            outputStream.write(header)
            outputStream.write(subscribeReq)
            outputStream.flush()

            // Read acknowledgement.
            val ackLen = DataInputStream(sock.inputStream).readInt()
            val ackBuf = ByteArray(ackLen)
            sock.inputStream.read(ackBuf)
            Timber.tag(TAG).d("Event subscribe ack: %s", String(ackBuf))

            // Start background reader loop.
            startReaderLoop()
        } catch (e: IOException) {
            sock.close()
            throw IOException("Failed to connect event subscriber: ${e.message}", e)
        }
    }

    override fun close() {
        closed = true
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        _logChannel.close()
        Timber.tag(TAG).i("Event subscriber closed")
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun startReaderLoop() {
        Thread({
            try {
                while (!closed) {
                    val raw = readRawMessage() ?: break
                    dispatchEvent(raw)
                }
            } catch (e: IOException) {
                if (!closed) {
                    Timber.tag(TAG).w(e, "Event reader loop ended")
                }
            } finally {
                close()
            }
        }, "uds-event-reader").apply { isDaemon = true; start() }
    }

    private fun readRawMessage(): String? {
        val inp = input ?: return null
        val length = inp.readInt()
        if (length <= 0 || length > 16 * 1024 * 1024) {
            throw IOException("Invalid event message length: $length")
        }
        val buf = ByteArray(length)
        inp.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun dispatchEvent(raw: String) {
        try {
            val event = json.decodeFromString(UdsEvent.serializer(), raw)

            // Emit to raw flow.
            _rawEvents.tryEmit(event)

            // Dispatch to typed flows.
            when (event.event) {
                "traffic" -> {
                    val data = json.decodeFromString(UdsTrafficEvent.serializer(), event.data.toString())
                    _trafficEvents.tryEmit(data)
                }
                "state" -> {
                    // State event: {"mode": "rule"}
                    val obj = json.parseToJsonElement(event.data.toString()) as JsonObject
                    val mode = obj["mode"]?.jsonPrimitive?.content ?: return
                    _tunnelStateEvents.tryEmit(mode)
                }
                "log" -> {
                    val data = json.decodeFromString(UdsLogEvent.serializer(), event.data.toString())
                    _logChannel.trySend(data)
                }
                else -> {
                    Timber.tag(TAG).d("Unknown event type: %s", event.event)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to dispatch event: %s", raw.take(200))
        }
    }

    companion object {
        private const val TAG = "UdsEventSubscriber"
    }
}
