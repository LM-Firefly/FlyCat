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

/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumeyucca.yumebox.runtime.service.core

import android.content.Context
import android.os.SystemClock
import com.github.yumeyucca.yumebox.core.model.RunMode
import com.github.yumeyucca.yumebox.core.util.runtimeHomeDir
import com.github.yumeyucca.yumebox.runtime.service.root.EbpfCgroupSupport
import com.github.yumeyucca.yumebox.runtime.service.session.EbpfOverride
import com.github.yumeyucca.yumebox.runtime.service.session.SessionRuntimeSpecFactory
import com.topjohnwu.superuser.Shell
import java.io.File

/** Owns the standalone root C++ eBPF socket-address bridge. */
object EbpfBridgeProcess {
    private const val BRIDGE_LOG = "ebpf-bridge.log"
    private const val STOP_GRACE_MS = 1_500L
    private const val STOP_POLL_MS = 50L
    private const val WATCHDOG_RETRY_COUNT = 1
    private const val PROC_STAT_START_TIME_INDEX_AFTER_COMM = 19

    /** Capability-only probe used by the settings gate; it creates no persistent map/program. */
    fun isCapabilityAvailable(context: Context, cgroupPath: String?): Boolean {
        val executable = CoreArtifacts.bridge(context)
        if (!executable.isFile || cgroupPath.isNullOrBlank()) return false
        val command = "${quote(executable.absolutePath)} --probe --cgroup ${quote(cgroupPath)}"
        return runCatching { Shell.cmd(command).exec().isSuccess }.getOrDefault(false)
    }

    fun start(
        context: Context,
        mihomoPid: Int,
        cgroupPath: String? = null,
        uidPolicy: SessionRuntimeSpecFactory.EbpfUidPolicy = SessionRuntimeSpecFactory.EbpfUidPolicy(0, emptyList()),
        dnsMode: Int = 0,
        enableIpv6: Boolean = true,
        bypassCidrs: List<String> = emptyList(),
    ) {
        require(mihomoPid > 0) { "mihomo PID is unavailable for eBPF bridge" }
        val executable = CoreArtifacts.bridge(context)
        check(executable.isFile) { "eBPF bridge executable is unavailable: ${executable.absolutePath}" }
        val targetCgroup = cgroupPath ?: EbpfCgroupSupport.rootCgroupPath()
        check(!targetCgroup.isNullOrBlank()) { "cgroup v2 mount is unavailable" }

        stop()
        val logFile = context.runtimeHomeDir.resolve(BRIDGE_LOG).absolutePath
        val logSession = "===== eBPF bridge start epochMillis=${System.currentTimeMillis()} ====="
        val command =
            "printf '%s\\n' ${quote(logSession)} >>${quote(logFile)}; " +
                    "( exec ${quote(executable.absolutePath)} --run " +
                    "--cgroup ${quote(targetCgroup)} " +
                    "--socks-host 127.0.0.1 " +
                    "--socks-port ${EbpfOverride.MIXED_PORT} " +
                    "--mihomo-pid $mihomoPid " +
                    "--uid-policy ${uidPolicy.mode} " +
                    (if (uidPolicy.uids.isEmpty()) "" else "--uids ${uidPolicy.uids.joinToString(",")} ") +
                    "--dns-mode $dnsMode " +
                    "--ipv6 ${if (enableIpv6) "on" else "off"} " +
                    (if (bypassCidrs.isEmpty()) "" else "--bypass-cidrs ${quote(bypassCidrs.joinToString(","))} ") +
                    "</dev/null >>${quote(logFile)} 2>&1 ) & bridge_pid=\$!; " +
                    "( while kill -0 \$bridge_pid 2>/dev/null; do sleep 1; done; " +
                    "i=0; while [ \$i -lt $WATCHDOG_RETRY_COUNT ]; do " +
                    "${quote(executable.absolutePath)} --cleanup --cgroup ${quote(targetCgroup)} " +
                    "2>&1 | tee -a ${quote(logFile)}; i=\$((i + 1)); sleep 1; done ) >/dev/null 2>&1 & " +
                    "echo \$bridge_pid"
        val result = Shell.cmd(command).exec()
        val pid = result.out.asSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()
        if (!(result.isSuccess && pid != null && pid > 0)) {
            error("eBPF bridge launch failed (success=${result.isSuccess} out=${result.out})")
        }

        val startTime = processStartTimeTicks(pid) ?: 0L
        RootDaemonState.attachBridge(pid, startTime, targetCgroup)
        if (!isAlive()) {
            RootDaemonState.detachBridge()
            error("eBPF bridge exited during startup: ${diagnosticLog(context)}")
        }
    }

    fun isAlive(): Boolean {
        val record = RootDaemonState.load() ?: return false
        if (record.mode != RunMode.Ebpf.coreArg || record.bridgePid <= 0) return false
        val alive = runCatching { Shell.cmd("kill -0 ${record.bridgePid}").exec().isSuccess }
            .getOrDefault(false)
        if (!alive) return false
        val executable =
            runCatching {
                Shell.cmd("readlink /proc/${record.bridgePid}/exe")
                    .exec()
                    .out
                    .firstOrNull()
                    ?.substringBefore(" (deleted)")
                    ?.let(::File)
                    ?.name
            }
                .getOrNull()
        if (executable != CoreArtifacts.BRIDGE_NAME) return false
        return record.bridgeStartTimeTicks <= 0L ||
                processStartTimeTicks(record.bridgePid) == record.bridgeStartTimeTicks
    }

    /** SIGTERM lets the bridge detach its cgroup programs and close BPF maps before mihomo stops. */
    fun stop() {
        val record = RootDaemonState.load() ?: return
        val pid = record.bridgePid
        RootDaemonState.detachBridge()
        if (pid <= 0) return
        runCatching { Shell.cmd("kill $pid").exec() }
        val deadline = SystemClock.elapsedRealtime() + STOP_GRACE_MS
        while (SystemClock.elapsedRealtime() < deadline && isPidAlive(pid)) {
            Thread.sleep(STOP_POLL_MS)
        }
        if (isPidAlive(pid)) {
            runCatching { Shell.cmd("kill -9 $pid").exec() }
        }
    }

    fun diagnosticLog(context: Context): String =
        runCatching {
            val file = context.runtimeHomeDir.resolve(BRIDGE_LOG)
            if (!file.isFile) return@runCatching ""
            file.readLines().lastOrNull { it.isNotBlank() }?.trim()?.take(500).orEmpty()
        }
            .getOrDefault("")

    private fun isPidAlive(pid: Int): Boolean =
        runCatching { Shell.cmd("kill -0 $pid").exec().isSuccess }.getOrDefault(false)

    private fun processStartTimeTicks(pid: Int): Long? =
        runCatching {
            val stat = Shell.cmd("cat /proc/$pid/stat").exec().out.joinToString(" ")
            stat.substringAfterLast(") ", missingDelimiterValue = "")
                .split(Regex("\\s+"))
                .getOrNull(PROC_STAT_START_TIME_INDEX_AFTER_COMM)
                ?.toLongOrNull()
        }
            .getOrNull()

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
