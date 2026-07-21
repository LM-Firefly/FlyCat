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
import timber.log.Timber
import java.io.FileDescriptor

/**
 * Sends a file descriptor over a Unix domain socket using SCM_RIGHTS.
 *
 * This is required for passing the TUN fd from the Kotlin/Android side
 * to the Go UDS server process. Android's [LocalSocket] doesn't expose
 * raw ancillary data APIs, so we use a native JNI helper.
 */
object UdsFdSender {

    init {
        try {
            System.loadLibrary("uds_fd_sender")
        } catch (e: UnsatisfiedLinkError) {
            Timber.tag(TAG).w(e, "Failed to load uds_fd_sender native library")
        }
    }

    /**
     * Sends [fdToSend] over [socketFd] using SCM_RIGHTS.
     *
     * @param socketFd The file descriptor of the Unix domain socket.
     * @param fdToSend The file descriptor to send (e.g., the TUN fd).
     * @return true on success, false on failure.
     */
    external fun nativeSendFd(socketFd: Int, fdToSend: Int): Boolean

    /**
     * Sends [fdToSend] over [socket] using SCM_RIGHTS.
     *
     * This extracts the socket's file descriptor via reflection and
     * then calls the native sendmsg with SCM_RIGHTS.
     */
    fun sendFd(socket: LocalSocket, fdToSend: FileDescriptor): Boolean {
        return try {
            val socketFd = extractSocketFd(socket)
            val fdInt = extractFd(fdToSend)
            if (socketFd < 0 || fdInt < 0) {
                Timber.tag(TAG).e("Invalid fds: socket=%d fdToSend=%d", socketFd, fdInt)
                return false
            }
            nativeSendFd(socketFd, fdInt)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send fd via SCM_RIGHTS")
            false
        }
    }

    /**
     * Sends a raw integer fd over [socket] using SCM_RIGHTS.
     */
    fun sendRawFd(socket: LocalSocket, fdToSend: Int): Boolean {
        return try {
            val socketFd = extractSocketFd(socket)
            if (socketFd < 0) {
                Timber.tag(TAG).e("Invalid socket fd: %d", socketFd)
                return false
            }
            nativeSendFd(socketFd, fdToSend)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send raw fd via SCM_RIGHTS")
            false
        }
    }

    private fun extractSocketFd(socket: LocalSocket): Int {
        return try {
            val implField = LocalSocket::class.java.getDeclaredField("impl")
            implField.isAccessible = true
            val impl = implField.get(socket)

            val fdField = impl.javaClass.getDeclaredField("fd")
            fdField.isAccessible = true
            val fd = fdField.get(impl) as FileDescriptor
            extractFd(fd)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract socket fd via reflection")
            -1
        }
    }

    private fun extractFd(fd: FileDescriptor): Int {
        return try {
            val getInt = FileDescriptor::class.java.getDeclaredMethod("getInt$")
            getInt.invoke(fd) as Int
        } catch (e: Exception) {
            // Fallback for older API.
            try {
                val field = FileDescriptor::class.java.getDeclaredField("descriptor")
                field.isAccessible = true
                field.getInt(fd)
            } catch (e2: Exception) {
                -1
            }
        }
    }

    private const val TAG = "UdsFdSender"
}
