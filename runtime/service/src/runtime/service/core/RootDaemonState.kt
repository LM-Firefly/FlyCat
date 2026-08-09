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

package com.github.yumeyucca.yumebox.runtime.service.core

import com.github.yumeyucca.yumebox.data.store.MMKVProvider

/**
 * Persisted handle to the decoupled root core daemon. Unlike the tracked VPN child core, the root
 * daemon outlives the app process, so its `{pid, secret, mode}` must survive process death for a
 * fresh start to detect and reattach over the REST socket — held in the multi-process
 * `root_tun_state` MMKV (pre-created by StatusProvider). The app is the single writer; the daemon
 * never touches it.
 */
object RootDaemonState {
    private const val ID = "root_tun_state"
    private const val KEY_PID = "root_daemon_pid"
    private const val KEY_SECRET = "root_daemon_secret"
    private const val KEY_MODE = "root_daemon_mode"
    private const val KEY_START_TIME_TICKS = "root_daemon_start_time_ticks"
    private const val KEY_BRIDGE_PID = "root_bridge_pid"
    private const val KEY_BRIDGE_START_TIME_TICKS = "root_bridge_start_time_ticks"
    private const val KEY_CGROUP_PATH = "root_ebpf_cgroup_path"

    /** [mode] is the launch `--mode` value: "tun" or "ebpf". */
    data class Record(
        val pid: Int,
        val secret: String,
        val mode: String,
        val startTimeTicks: Long = 0L,
        val bridgePid: Int = 0,
        val bridgeStartTimeTicks: Long = 0L,
        val cgroupPath: String = "",
    )

    private fun store() = MMKVProvider().getMMKV(ID)

    fun save(record: Record) {
        store().apply {
            encode(KEY_PID, record.pid)
            encode(KEY_SECRET, record.secret)
            encode(KEY_MODE, record.mode)
            encode(KEY_START_TIME_TICKS, record.startTimeTicks)
            encode(KEY_BRIDGE_PID, record.bridgePid)
            encode(KEY_BRIDGE_START_TIME_TICKS, record.bridgeStartTimeTicks)
            encode(KEY_CGROUP_PATH, record.cgroupPath)
        }
    }

    fun load(): Record? {
        val mmkv = store()
        val pid = mmkv.decodeInt(KEY_PID, 0).takeIf { it > 0 } ?: return null
        val mode =
            mmkv.decodeString(KEY_MODE).orEmpty().ifEmpty {
                return null
            }
        return Record(
            pid = pid,
            secret = mmkv.decodeString(KEY_SECRET).orEmpty(),
            mode = mode,
            startTimeTicks = mmkv.decodeLong(KEY_START_TIME_TICKS, 0L),
            bridgePid = mmkv.decodeInt(KEY_BRIDGE_PID, 0),
            bridgeStartTimeTicks = mmkv.decodeLong(KEY_BRIDGE_START_TIME_TICKS, 0L),
            cgroupPath = mmkv.decodeString(KEY_CGROUP_PATH).orEmpty(),
        )
    }

    fun attachBridge(pid: Int, startTimeTicks: Long, cgroupPath: String) {
        val record = load() ?: return
        save(
            record.copy(
                bridgePid = pid,
                bridgeStartTimeTicks = startTimeTicks,
                cgroupPath = cgroupPath,
            )
        )
    }

    fun detachBridge() {
        val record = load() ?: return
        save(record.copy(bridgePid = 0, bridgeStartTimeTicks = 0L, cgroupPath = ""))
    }

    fun clear() {
        store().apply {
            removeValueForKey(KEY_PID)
            removeValueForKey(KEY_SECRET)
            removeValueForKey(KEY_MODE)
            removeValueForKey(KEY_START_TIME_TICKS)
            removeValueForKey(KEY_BRIDGE_PID)
            removeValueForKey(KEY_BRIDGE_START_TIME_TICKS)
            removeValueForKey(KEY_CGROUP_PATH)
        }
    }
}
