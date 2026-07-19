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

package com.github.yumelira.yumebox.core.bridge

import androidx.annotation.Keep

/**
 * The socketpair control channel to the core child process ([NativeProcess.channelFd]). Carries
 * length-delimited control messages and, via SCM_RIGHTS, a single file descriptor per message —
 * this is how the TUN fd is handed to the core in the non-root VpnService path.
 *
 * YumeBox's equivalent of CFA's `AndroidChannel`. This is a thin fd wrapper; message framing and
 * the command vocabulary live in the Kotlin layer above it.
 */
@Keep
class Channel(private val fd: Int) {
    private val fdHolder = IntArray(1)

    /**
     * Read one datagram into [buffer] at [offset]/[length]. Returns the number of bytes read
     * (0 means the peer closed the channel). If the peer attached a descriptor, it is returned;
     * otherwise the returned fd is -1.
     */
    fun readMessage(buffer: ByteArray, offset: Int, length: Int): ReadResult {
        fdHolder[0] = -1
        val count = nativeReadMessage(fd, buffer, offset, length, fdHolder)
        return ReadResult(count = count, fd = fdHolder[0])
    }

    /**
     * Write one datagram from [buffer] at [offset]/[length], optionally attaching [attachFd] via
     * SCM_RIGHTS (pass -1 for no descriptor). Returns the number of bytes written.
     */
    fun writeMessage(buffer: ByteArray, offset: Int, length: Int, attachFd: Int = -1): Int {
        fdHolder[0] = attachFd
        return nativeWriteMessage(fd, buffer, offset, length, fdHolder)
    }

    /** Close the channel fd. Signals EOF to the peer (the no-fd config-stream terminator). */
    fun close() {
        runCatching { android.os.ParcelFileDescriptor.adoptFd(fd).close() }
    }

    data class ReadResult(val count: Int, val fd: Int)

    companion object {
        init {
            CompatNative.ensureLoaded()
        }

        @JvmStatic
        private external fun nativeReadMessage(
            fd: Int,
            buffer: ByteArray,
            offset: Int,
            length: Int,
            fdHolder: IntArray,
        ): Int

        @JvmStatic
        private external fun nativeWriteMessage(
            fd: Int,
            buffer: ByteArray,
            offset: Int,
            length: Int,
            fdHolder: IntArray,
        ): Int
    }
}
