/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.runtime.service.privilege

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/** Shizuku UserService 的 Binder 契约：以 shell 身份执行单条命令（exec）。仅用于 priv-kit 服务端尚未运行的「外部启动」阶段。 */
interface IPrivilegedService : IInterface {
    fun exec(commandLine: String): Boolean
    abstract class Stub : Binder(), IPrivilegedService {
        init { attachInterface(this, DESCRIPTOR) }
        override fun asBinder(): IBinder = this
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            when (code) {
                TRANSACTION_EXEC -> {
                    data.enforceInterface(DESCRIPTOR)
                    val commandLine = data.readString().orEmpty()
                    val result = exec(commandLine)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }
        companion object {
            private const val DESCRIPTOR = "com.github.lmfirefly.flycat.runtime.service.privilege.IPrivilegedService"
            private const val TRANSACTION_EXEC = FIRST_CALL_TRANSACTION + 1
            fun asInterface(binder: IBinder?): IPrivilegedService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IPrivilegedService) local else Proxy(binder)
            }
        }
        private class Proxy(private val remote: IBinder) : IPrivilegedService {
            override fun asBinder(): IBinder = remote
            override fun exec(commandLine: String): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(commandLine)
                    remote.transact(TRANSACTION_EXEC, data, reply, 0)
                    reply.readException()
                    reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }
    }
}
