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

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.*

/** /** UNIX-domain connection to the local core controller (`@` path => abstract namespace). */ */
@Keep
class UnixSocket private constructor(private val descriptor: ParcelFileDescriptor) : Closeable {

    val fileDescriptor: FileDescriptor
        get() = descriptor.fileDescriptor

    /** Streams over the shared fd; they do not own the descriptor — [close] does. */
    fun inputStream(): InputStream = FileInputStream(descriptor.fileDescriptor)

    fun outputStream(): OutputStream = FileOutputStream(descriptor.fileDescriptor)

    /** SO_RCVTIMEO/SO_SNDTIMEO in milliseconds (0 = block indefinitely). */
    fun setSoTimeout(timeoutMs: Int) = nativeSetSoTimeout(descriptor.fd, timeoutMs)

    fun getSoTimeout(): Int = nativeGetSoTimeout(descriptor.fd)

    override fun close() = descriptor.close()

    companion object {
        init {
            CompatNative.ensureLoaded()
        }

        const val SOCK_STREAM = 1
        const val SOCK_DGRAM = 2

        /**
         * Connect to a UNIX socket at [path]. A leading `@` selects the abstract namespace.
         *
         * @throws java.io.IOException on socket/connect failure.
         */
        fun connect(path: String, type: Int = SOCK_STREAM): UnixSocket {
            val fd = nativeConnectUnixSocket(path, type)
            return UnixSocket(ParcelFileDescriptor.adoptFd(fd))
        }

        @JvmStatic private external fun nativeConnectUnixSocket(path: String, type: Int): Int

        @JvmStatic private external fun nativeSetSoTimeout(fd: Int, timeoutMs: Int)

        @JvmStatic private external fun nativeGetSoTimeout(fd: Int): Int
    }
}
