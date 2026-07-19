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

package com.github.yumelira.yumebox.runtime.service.core

import com.github.yumelira.yumebox.data.store.MMKVProvider

/**
 * Persisted handle to the decoupled root core daemon.
 *
 * The VPN/HTTP cores are tracked children (an in-memory pid killed when the service stops). The root
 * daemon is different: it is launched via `su`, runs in the root SELinux domain, and **outlives the
 * app process**, so a fresh app start must be able to detect it and reattach over the REST socket.
 * That requires its `{pid, secret, mode}` to survive process death — persisted here in the
 * multi-process `root_tun_state` MMKV (pre-created by StatusProvider). The app is the single writer;
 * the daemon never touches this store.
 */
object RootDaemonState {
    private const val ID = "root_tun_state"
    private const val KEY_PID = "root_daemon_pid"
    private const val KEY_SECRET = "root_daemon_secret"
    private const val KEY_MODE = "root_daemon_mode"

    /** [mode] is the launch `--mode` value: "tun" or "tproxy". */
    data class Record(val pid: Int, val secret: String, val mode: String)

    private fun store() = MMKVProvider().getMMKV(ID)

    fun save(record: Record) {
        store().apply {
            encode(KEY_PID, record.pid)
            encode(KEY_SECRET, record.secret)
            encode(KEY_MODE, record.mode)
        }
    }

    fun load(): Record? {
        val mmkv = store()
        val pid = mmkv.decodeInt(KEY_PID, 0).takeIf { it > 0 } ?: return null
        val mode = mmkv.decodeString(KEY_MODE).orEmpty().ifEmpty { return null }
        return Record(pid = pid, secret = mmkv.decodeString(KEY_SECRET).orEmpty(), mode = mode)
    }

    fun clear() {
        store().apply {
            removeValueForKey(KEY_PID)
            removeValueForKey(KEY_SECRET)
            removeValueForKey(KEY_MODE)
        }
    }
}
