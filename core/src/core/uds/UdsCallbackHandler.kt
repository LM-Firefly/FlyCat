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
import android.content.pm.PackageManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Handles reverse callback requests from the Go server.
 *
 * The Go mihomo core needs to query which Android app owns a given socket
 * (for per-app routing / access control). In JNI mode this was done via
 * a callback function pointer. In UDS mode, the Go server sends a request
 * on a dedicated callback connection, and this class reads it, resolves
 * the socket owner, and sends the response back.
 *
 * Protocol:
 * 1. Kotlin opens a third connection to the Go server.
 * 2. Kotlin sends `{"method": "callback.register"}`.
 * 3. Go sends requests: `{"id": "...", "method": "tun.querySocketOwner", "params": {...}}`
 * 4. Kotlin reads the request, resolves the owner, sends back a response.
 */
class UdsCallbackHandler(
    private val socketPath: String,
    private val context: Context,
) : Closeable {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    @Volatile
    private var closed = false

    /**
     * Connects to the Go server and registers as a callback handler.
     * Starts a background loop that processes incoming requests.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        check(!closed) { "Callback handler is closed" }

        val sock = LocalSocket()
        try {
            val addr = LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM)
            sock.connect(addr)
            socket = sock
            input = DataInputStream(sock.inputStream)
            output = DataOutputStream(sock.outputStream)

            // Register as callback handler.
            val registerReq = """{"id":"cb-reg","method":"callback.register"}""".toByteArray(Charsets.UTF_8)
            val header = ByteArray(4)
            header[0] = (registerReq.size shr 24).toByte()
            header[1] = (registerReq.size shr 16).toByte()
            header[2] = (registerReq.size shr 8).toByte()
            header[3] = registerReq.size.toByte()
            sock.outputStream.write(header)
            sock.outputStream.write(registerReq)
            sock.outputStream.flush()

            // Read ack.
            val ackLen = DataInputStream(sock.inputStream).readInt()
            val ackBuf = ByteArray(ackLen)
            sock.inputStream.read(ackBuf)

            Timber.tag(TAG).i("Callback handler connected and registered")

            // Start request processing loop.
            startCallbackLoop()
        } catch (e: IOException) {
            sock.close()
            throw IOException("Failed to connect callback handler: ${e.message}", e)
        }
    }

    override fun close() {
        closed = true
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        Timber.tag(TAG).i("Callback handler closed")
    }

    private fun startCallbackLoop() {
        Thread({
            try {
                while (!closed) {
                    val raw = readRawMessage() ?: break
                    handleCallbackRequest(raw)
                }
            } catch (e: IOException) {
                if (!closed) {
                    Timber.tag(TAG).w(e, "Callback loop ended")
                }
            } finally {
                close()
            }
        }, "uds-callback").apply { isDaemon = true; start() }
    }

    private fun readRawMessage(): String? {
        val inp = input ?: return null
        val length = inp.readInt()
        if (length <= 0 || length > 16 * 1024 * 1024) {
            throw IOException("Invalid callback message length: $length")
        }
        val buf = ByteArray(length)
        inp.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun handleCallbackRequest(raw: String) {
        try {
            val request = json.decodeFromString(UdsRequest.serializer(), raw)

            when (request.method) {
                "tun.querySocketOwner" -> {
                    handleQuerySocketOwner(request)
                }
                else -> {
                    Timber.tag(TAG).w("Unknown callback method: %s", request.method)
                    writeResponse(UdsResponse(
                        id = request.id,
                        error = UdsResponseError(404, "unknown callback method: ${request.method}")
                    ))
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to handle callback request: %s", raw.take(200))
        }
    }

    private fun handleQuerySocketOwner(request: UdsRequest) {
        try {
            val params = request.params?.let {
                json.decodeFromString(JsonObject.serializer(), it.toString())
            }

            val protocol = params?.get("protocol")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val source = params?.get("source")?.jsonPrimitive?.content ?: ""
            val target = params?.get("target")?.jsonPrimitive?.content ?: ""

            val result = resolveSocketOwner(protocol, source, target)

            writeResponse(UdsResponse(
                id = request.id,
                result = kotlinx.serialization.json.buildJsonObject {
                    put("uid", kotlinx.serialization.json.JsonPrimitive(result.uid))
                    put("package", kotlinx.serialization.json.JsonPrimitive(result.packageName))
                }
            ))
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to resolve socket owner")
            writeResponse(UdsResponse(
                id = request.id,
                error = UdsResponseError(500, "resolve failed: ${e.message}")
            ))
        }
    }

    /**
     * Resolves which Android app owns a socket by querying the UID
     * from TrafficStats or /proc/net, then mapping to a package name.
     */
    private fun resolveSocketOwner(protocol: Int, source: String, target: String): SocketOwner {
        // Try to extract UID from /proc/net/tcp6 or TrafficStats.
        val uid = lookupSocketUid(protocol, source, target)
        if (uid < 0) {
            return SocketOwner(uid = -1, packageName = "")
        }

        val packageName = try {
            val packages = context.packageManager.getPackagesForUid(uid)
            packages?.firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }

        return SocketOwner(uid = uid, packageName = packageName)
    }

    private fun lookupSocketUid(protocol: Int, source: String, target: String): Int {
        // Parse source address to extract local port.
        // Format: "192.168.1.1:12345" or "[::1]:12345"
        val localPort = source.substringAfterLast(":").substringBefore("]").toIntOrNull() ?: return -1

        // Use TrafficStats to get UID by tag, or read from /proc/net/tcp6.
        // This is a simplified version; the actual implementation uses
        // mihomo's process resolver which reads /proc/net/tcp6.
        return try {
            // Read /proc/net/tcp6 and find the matching local port.
            val procNet = if (protocol == 6) "/proc/net/tcp6" else "/proc/net/tcp"
            val lines = java.io.File(procNet).readLines()
            for (line in lines.drop(1)) { // skip header
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 8) continue
                val localAddr = parts[1]
                val hexPort = localAddr.substringAfterLast(":")
                val port = hexPort.toLongOrNull(16)?.toInt() ?: continue
                if (port == localPort) {
                    val uid = parts[7].toIntOrNull() ?: continue
                    return uid
                }
            }
            -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun writeResponse(response: UdsResponse) {
        try {
            val out = output ?: return
            val payload = json.encodeToString(UdsResponse.serializer(), response).toByteArray(Charsets.UTF_8)
            synchronized(out) {
                out.writeInt(payload.size)
                out.write(payload)
                out.flush()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to write callback response")
        }
    }

    data class SocketOwner(val uid: Int, val packageName: String)

    companion object {
        private const val TAG = "UdsCallbackHandler"
    }
}
