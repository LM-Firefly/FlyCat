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

package com.github.lmfirefly.flycat.runtime.service.root

import android.content.Context
import android.os.SystemClock
import com.github.lmfirefly.flycat.core.model.OverrideSpec
import com.github.lmfirefly.flycat.core.util.YamlCodec
import com.github.lmfirefly.flycat.core.util.path.runtimeHomeDir
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * The eBPF bridge talks to a normal mihomo Mixed listener. It must be deterministic: the native
 * process is deliberately independent from mihomo's internal listener implementation and cannot
 * discover a profile-selected random port after startup.
 *
 * This fragment is appended after user overrides and is therefore authoritative for the local
 * bridge entry point. It also disables any profile-provided Tun entry point; eBPF is a socket
 * address mode, not a second Tun device.
 */
object EbpfOverride {
    const val FILE_NAME = "__ebpf_bridge_override__.yaml"
    const val MIXED_PORT = 7890
    const val DNS_PORT = 1053

    fun buildYaml(dnsHijacking: Boolean): String {
        val override =
            linkedMapOf<String, Any?>(
                "mixed-port" to MIXED_PORT,
                "allow-lan" to false,
                "bind-address" to "127.0.0.1",
                "tun" to
                    linkedMapOf<String, Any?>(
                        "enable" to false,
                        "auto-route" to false,
                        "auto-redirect" to false,
                        "auto-detect-interface" to false,
                    ),
            )
        if (dnsHijacking) {
            override["dns"] =
                linkedMapOf<String, Any?>(
                    "enable" to true,
                    "listen" to "127.0.0.1:$DNS_PORT",
                )
        }
        return YamlCodec.dumpMap(override)
    }

    fun materialize(dnsHijacking: Boolean, dir: File): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml(dnsHijacking))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}

/** Resolves the cgroup v2 mount used by the socket-address hook. */
object EbpfCgroupSupport {
    private const val CGROUP_MOUNT = "/sys/fs/cgroup"

    /**
     * Android's SELinux policy may permit BPF attach on the cgroup v2 mount while rejecting attach
     * on app leaf cgroups such as `apps/uid_<uid>/pid_<pid>`. The root hook is intentionally paired with the
     * native UID policy map, so it does not mean that every UID is proxied by default.
     */
    fun rootCgroupPath(): String? =
        CGROUP_MOUNT.takeIf { File(it).isDirectory }
}

/** MMKV-persisted eBPF bridge handle for cross-process liveness checks. */
object EbpfBridgeStateStore {
    private const val STORE_ID = "ebpf_bridge_state"
    private const val KEY_PID = "bridge_pid"
    private const val KEY_START_TIME = "bridge_start_time_ticks"
    private const val KEY_CGROUP = "bridge_cgroup_path"

    data class Record(
        val pid: Int,
        val startTimeTicks: Long,
        val cgroupPath: String,
    )

    private val store: MMKV by lazy {
        MMKV.mmkvWithID(STORE_ID, MMKV.MULTI_PROCESS_MODE)
    }

    fun save(pid: Int, startTimeTicks: Long, cgroupPath: String) {
        store.encode(KEY_PID, pid)
        store.encode(KEY_START_TIME, startTimeTicks)
        store.encode(KEY_CGROUP, cgroupPath)
    }

    fun load(): Record? {
        val pid = store.decodeInt(KEY_PID, 0)
        if (pid <= 0) return null
        return Record(
            pid = pid,
            startTimeTicks = store.decodeLong(KEY_START_TIME, 0L),
            cgroupPath = store.decodeString(KEY_CGROUP, "") ?: "",
        )
    }

    fun clear() {
        store.removeValueForKey(KEY_PID)
        store.removeValueForKey(KEY_START_TIME)
        store.removeValueForKey(KEY_CGROUP)
    }
}

/** Owns the standalone root C++ eBPF socket-address bridge. */
object EbpfBridgeProcess {
    private const val BRIDGE_NAME = "libebpfbridge.so"
    private const val BRIDGE_LOG = "ebpf-bridge.log"
    private const val STOP_GRACE_MS = 1_500L
    private const val STOP_POLL_MS = 50L
    private const val DIAGNOSTIC_LOG_LINES = 20
    private const val DIAGNOSTIC_LOG_LIMIT = 2_000
    private const val PROC_STAT_START_TIME_INDEX_AFTER_COMM = 19

    /** Capability-only probe used by the settings gate; it creates no persistent map/program. */
    fun isCapabilityAvailable(context: Context, cgroupPath: String?): Boolean {
        val executable = bridgeExecutable(context)
        if (!executable.isFile || cgroupPath.isNullOrBlank()) return false
        val command = "${quote(executable.absolutePath)} --probe --cgroup ${quote(cgroupPath)}"
        return runCatching { Shell.cmd(command).exec().isSuccess }.getOrDefault(false)
    }

    fun start(
        context: Context,
        mihomoPid: Int,
        cgroupPath: String? = null,
        uidPolicyMode: Int = 0,
        uidPolicyUids: List<Int> = emptyList(),
        dnsHijacking: Boolean = false,
        enableIpv6: Boolean = true,
        bypassCidrs: List<String> = emptyList(),
    ) {
        require(mihomoPid > 0) { "mihomo PID is unavailable for eBPF bridge" }
        val executable = bridgeExecutable(context)
        check(executable.isFile) { "eBPF bridge executable is unavailable: ${executable.absolutePath}" }
        val targetCgroup = cgroupPath ?: EbpfCgroupSupport.rootCgroupPath()
        check(!targetCgroup.isNullOrBlank()) { "cgroup v2 mount is unavailable" }

        stop(context)
        val logFile = context.runtimeHomeDir.resolve(BRIDGE_LOG).absolutePath
        val logSession = "===== eBPF bridge start epochMillis=${System.currentTimeMillis()} ====="
        val command =
            "printf '%s\\n' ${quote(logSession)} >>${quote(logFile)}; " +
                "( exec ${quote(executable.absolutePath)} --run " +
                "--cgroup ${quote(targetCgroup)} " +
                "--socks-host 127.0.0.1 " +
                "--socks ${EbpfOverride.MIXED_PORT} " +
                "--mihomo-pid $mihomoPid " +
                "--uid-policy ${when (uidPolicyMode) { 1 -> "include"; 2 -> "exclude"; else -> "all" }} " +
                (if (uidPolicyUids.isEmpty()) "" else "--uids ${uidPolicyUids.joinToString(",")} ") +
                "--dns-mode ${if (dnsHijacking) "hijack" else "bypass"} " +
                "--dns-port ${if (dnsHijacking) EbpfOverride.DNS_PORT else 0} " +
                "--ipv6 ${if (enableIpv6) "true" else "false"} " +
                (if (bypassCidrs.isEmpty()) "" else "--bypass-cidrs ${quote(bypassCidrs.joinToString(","))} ") +
                "</dev/null >>${quote(logFile)} 2>&1 ) & bridge_pid=\$!; " +
                "echo \$bridge_pid"
        val result = Shell.cmd(command).exec()
        val pid = result.out.asSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()
        if (!(result.isSuccess && pid != null && pid > 0)) {
            error("eBPF bridge launch failed (success=${result.isSuccess} out=${result.out})")
        }

        val startTime = processStartTimeTicks(pid) ?: 0L
        EbpfBridgeStateStore.save(pid, startTime, targetCgroup)
        if (!isAlive()) {
            EbpfBridgeStateStore.clear()
            error("eBPF bridge exited during startup: ${diagnosticLog(context)}")
        }
    }

    fun isAlive(): Boolean {
        val state = EbpfBridgeStateStore.load() ?: return false
        if (state.pid <= 0) return false
        return isBridgeAlive(state.pid, state.startTimeTicks)
    }

    /** SIGTERM lets the bridge detach its cgroup programs and close BPF maps before mihomo stops. */
    fun stop(context: Context) {
        val state = EbpfBridgeStateStore.load() ?: return
        val pid = state.pid
        if (pid <= 0) return
        if (!isBridgeAlive(pid, state.startTimeTicks)) {
            appendLog(context, "eBPF bridge: stop skipped for stale pid=$pid")
            EbpfBridgeStateStore.clear()
            return
        }
        appendLog(context, "eBPF bridge: stop requested pid=$pid")
        runCatching { Shell.cmd("kill -TERM $pid").exec() }
        val deadline = SystemClock.elapsedRealtime() + STOP_GRACE_MS
        while (SystemClock.elapsedRealtime() < deadline && isPidAlive(pid)) {
            Thread.sleep(STOP_POLL_MS)
        }
        if (isPidAlive(pid)) {
            appendLog(context, "eBPF bridge: SIGTERM timeout, sending SIGKILL pid=$pid")
            runCatching { Shell.cmd("kill -KILL $pid").exec() }
        }
        if (isPidAlive(pid)) {
            appendLog(context, "eBPF bridge: stop failed, pid still alive=$pid")
            return
        }
        EbpfBridgeStateStore.clear()
        appendLog(context, "eBPF bridge: stopped pid=$pid")
    }

    /**
     * Removes bridges left by builds that cleared [EbpfBridgeStateStore] before stopping the bridge.
     * This is intentionally an upgrade-only migration: normal shutdown always uses the persisted
     * PID and process start time above.
     */
    fun cleanupOrphanedBridges(context: Context) {
        val pids =
            runCatching {
                Shell.cmd(
                    "for proc in /proc/[0-9]*; do " +
                        "pid=\${proc##*/}; exe=\$(readlink \"\$proc/exe\" 2>/dev/null); " +
                        "case \"\${exe%% *}\" in */$BRIDGE_NAME) echo \$pid;; esac; " +
                        "done"
                )
                    .exec()
                    .out
                    .mapNotNull { it.trim().toIntOrNull() }
            }
                .getOrDefault(emptyList())
        pids.forEach { pid ->
            appendLog(context, "eBPF bridge: cleaning orphan pid=$pid after APK replacement")
            stopPid(context, pid)
        }
    }

    fun diagnosticLog(context: Context): String =
        runCatching {
            val file = context.runtimeHomeDir.resolve(BRIDGE_LOG)
            if (!file.isFile) return@runCatching ""
            file.readLines().takeLast(DIAGNOSTIC_LOG_LINES).joinToString("\n").trim().takeLast(DIAGNOSTIC_LOG_LIMIT)
        }
            .getOrDefault("")

    private fun bridgeExecutable(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BRIDGE_NAME)

    private fun isBridgeAlive(pid: Int, startTimeTicks: Long): Boolean {
        val alive = isPidAlive(pid)
        if (!alive) return false
        val executable =
            runCatching {
                Shell.cmd("readlink /proc/$pid/exe")
                    .exec()
                    .out
                    .firstOrNull()
                    ?.substringBefore(" (deleted)")
                    ?.let(::File)
                    ?.name
            }
                .getOrNull()
        if (executable != BRIDGE_NAME) return false
        return startTimeTicks <= 0L ||
            processStartTimeTicks(pid) == startTimeTicks
    }

    private fun appendLog(context: Context, message: String) {
        val file = context.runtimeHomeDir.resolve(BRIDGE_LOG).absolutePath
        runCatching { Shell.cmd("printf '%s\\n' ${quote(message)} >>${quote(file)}").exec() }
    }

    private fun isPidAlive(pid: Int): Boolean =
        runCatching { Shell.cmd("kill -0 $pid").exec().isSuccess }.getOrDefault(false)

    private fun stopPid(context: Context, pid: Int) {
        runCatching { Shell.cmd("kill -TERM $pid").exec() }
        val deadline = SystemClock.elapsedRealtime() + STOP_GRACE_MS
        while (SystemClock.elapsedRealtime() < deadline && isPidAlive(pid)) {
            Thread.sleep(STOP_POLL_MS)
        }
        if (isPidAlive(pid)) {
            appendLog(context, "eBPF bridge: orphan SIGTERM timeout, sending SIGKILL pid=$pid")
            runCatching { Shell.cmd("kill -KILL $pid").exec() }
        }
    }

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
