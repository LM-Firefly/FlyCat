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

@file:Suppress("SimplifiableCallChain", "CanConvertToMultiDollarString", "CanUnescapeDollarLiteral")

package com.github.yumelira.yumebox.runtime.service.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import com.github.yumelira.yumebox.core.bridge.Channel
import com.github.yumelira.yumebox.core.bridge.NativeProcess
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.runtime.api.CoreApi
import com.github.yumelira.yumebox.runtime.service.controller.CoreController
import com.github.yumelira.yumebox.runtime.service.util.SocketOwnerResolver
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

/** The running core's UNIX REST controller: socket path + bearer secret. */
data class CoreEndpoint(val sock: String, val secret: String)

/**
 * Launches and owns the out-of-process mihomo core (the `native` PIE, packaged as `libclash.so`):
 * fork+exec it from nativeLibraryDir (the non-root exec path; falls back to an extracted, chmod'd
 * copy if refused), stream the compiled config over the socketpair (in memory, never on disk) plus
 * the VpnService TUN fd via SCM_RIGHTS, and publish the controller endpoint via [current].
 *
 * Egress: the tun uses the userspace gVisor stack, so excluding the app's own uid from the
 * VpnService tunnel keeps the core's egress off it — no per-socket protect needed.
 */
class CoreProcess(private val context: Context) {

    private var process: NativeProcess? = null
    private var ownerChannel: Channel? = null
    private var ownerQueryThread: Thread? = null

    /** Fork the core for VpnService mode, deliver [config] and [tunFd], and publish [current]. */
    fun startVpn(
        tunFd: Int,
        gateway: String,
        dns: String,
        config: String,
    ): CoreEndpoint {
        val home = context.runtimeHomeDir.apply { mkdirs() }
        // Fresh core.log for this launch (libcompat redirects stdout/stderr there after chdir).
        File(home, CORE_LOG).delete()
        // Drop any leftover controller node so readiness probes cannot hit a dead socket.
        File(home, SOCK).delete()
        val sock = File(home, SOCK).absolutePath
        // Keep the controller secret out of argv (visible to other processes / root shell).
        // Profiles often omit `secret:`; mint one so REST auth and readiness stay consistent.
        val (runtimeConfig, secret) = ensureControllerSecret(config)

        val args =
            arrayOf(
                "--home",
                home.absolutePath,
                "--controller",
                sock,
                "--gateway",
                gateway,
                "--dns",
                dns,
                "--mode",
                "vpn",
                "--sdk",
                Build.VERSION.SDK_INT.toString(),
            )
        Timber.tag(TAG).i("launch core, tunFd=%d", tunFd)
        val proc = spawn(home, args)
        process = proc
        running = proc

        // Stream config over the socketpair (in memory), then the TUN fd as a terminating
        // SCM_RIGHTS
        // message. The core dups the fd; the app closes its own copy.
        val handoff =
            runCatching {
                val channel = Channel(proc.channelFd)
                var ownershipRpcStarted = false
                try {
                    val bytes = runtimeConfig.toByteArray(Charsets.UTF_8)
                    var offset = 0
                    while (offset < bytes.size) {
                        val len = minOf(CHUNK, bytes.size - offset)
                        channel.writeMessage(bytes, offset, len)
                        offset += len
                    }
                    channel.writeMessage(END, 0, END.size, attachFd = tunFd)
                    startOwnerQueryLoop(channel)
                    ownershipRpcStarted = true
                } finally {
                    if (!ownershipRpcStarted) {
                        channel.close()
                    }
                }
            }
        runCatching { ParcelFileDescriptor.adoptFd(tunFd).close() }
        handoff.getOrElse { error ->
            runCatching { proc.kill() }
            stopOwnerQueryLoop()
            if (running === proc) running = null
            if (process === proc) process = null
            current = null
            throw IllegalStateException("config/fd handoff failed", error)
        }

        return CoreEndpoint(sock, secret).also { current = it }
    }

    private fun startOwnerQueryLoop(channel: Channel) {
        val resolver = SocketOwnerResolver(context)
        ownerChannel = channel
        ownerQueryThread =
            Thread {
                    val buffer = ByteArray(OWNER_QUERY_BUFFER_SIZE)
                    try {
                        while (true) {
                            val result = channel.readMessage(buffer, 0, buffer.size)
                            if (result.count <= 0) break
                            val request = buffer.decodeToString(0, result.count)
                            val response = resolveOwnerQuery(resolver, request)
                            val responseBytes = response.toByteArray(Charsets.UTF_8)
                            channel.writeMessage(responseBytes, 0, responseBytes.size)
                        }
                    } catch (error: Throwable) {
                        if (ownerChannel === channel && isLocalCoreAlive()) {
                            Timber.tag(TAG).w(error, "socket owner RPC stopped unexpectedly")
                        }
                    } finally {
                        if (ownerChannel === channel) {
                            ownerChannel = null
                            ownerQueryThread = null
                        }
                        runCatching { channel.close() }
                    }
                }
                .apply {
                    name = "Core-SocketOwner"
                    isDaemon = true
                    start()
                }
    }

    private fun resolveOwnerQuery(resolver: SocketOwnerResolver, request: String): String {
        val fields = request.split('\t', limit = 3)
        if (fields.size != 3) return UNKNOWN_SOCKET_OWNER
        val protocol = fields[0].toIntOrNull() ?: return UNKNOWN_SOCKET_OWNER
        val source = parseSocketAddress(fields[1]) ?: return UNKNOWN_SOCKET_OWNER
        val target = parseSocketAddress(fields[2]) ?: return UNKNOWN_SOCKET_OWNER
        return resolver.queryOwner(protocol, source, target)
    }

    private fun parseSocketAddress(value: String): InetSocketAddress? = runCatching {
        val (host, portText) =
            if (value.startsWith('[')) {
                val closingBracket = value.indexOf(']')
                require(closingBracket > 1 && value.getOrNull(closingBracket + 1) == ':')
                value.substring(1, closingBracket) to value.substring(closingBracket + 2)
            } else {
                val separator = value.lastIndexOf(':')
                require(separator > 0)
                value.substring(0, separator) to value.substring(separator + 1)
            }
        InetSocketAddress(InetAddress.getByName(host), portText.toInt())
    }.getOrNull()

    /**
     * Launch the core as a detached ROOT daemon (tun / tproxy) via `su`: it runs in the root
     * SELinux domain (free to open a kernel TUN, program routes, iptables) and, unlike the VPN
     * child core, outlives the app process — reattached over the REST socket ([reconnectRoot]).
     * [mode] = "tun"/"tproxy".
     */
    fun startRoot(mode: String, config: String): CoreEndpoint {
        awaitRootStopGrace()
        val home = context.runtimeHomeDir.apply { mkdirs() }
        File(home, SOCK).delete()
        val sock = File(home, SOCK).absolutePath
        val (runtimeConfig, secret) = ensureControllerSecret(config)

        // A detached `su` daemon can't inherit the config socketpair the VPN core streams over, so
        // hand the compiled config (proxy secrets) through a named pipe instead of a file: the core
        // reads it once via --config and nothing is ever written to disk — the same
        // no-plaintext-at-
        // rest posture as VPN. Drop any legacy plaintext run.yaml an older build left behind.
        File(home, LEGACY_ROOT_CONFIG).delete()
        val fifo = File(home, ROOT_CONFIG_PIPE).apply { delete() }
        Os.mkfifo(fifo.absolutePath, ROOT_PIPE_MODE)

        val lib = File(context.applicationInfo.nativeLibraryDir, LIB).absolutePath
        val logFile = File(home, CORE_LOG).absolutePath
        val command =
            "exec ${quote(lib)} --mode $mode --home ${quote(home.absolutePath)} " +
                "--sdk ${Build.VERSION.SDK_INT} " +
                "--controller ${quote(sock)} " +
                "--config ${quote(fifo.absolutePath)} " +
                "</dev/null >${quote(logFile)} 2>&1 & echo \$!"

        Timber.tag(TAG).i("launch root core, mode=%s", mode)
        val result = Shell.cmd(command).exec()
        val pid = result.out.asSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()
        if (!(result.isSuccess && pid != null && pid > 0)) {
            fifo.delete()
            error("root core launch failed (success=${result.isSuccess} out=${result.out})")
        }

        // Feed the config into the pipe; the core's ReadFile blocks until we open+write. Run it on
        // a
        // daemon thread with a timeout so a core that died on launch (no reader) can't block the
        // caller forever; then unlink the pipe node (it holds nothing at rest either way).
        val writer = Thread {
            runCatching {
                    FileOutputStream(fifo).use {
                        it.write(runtimeConfig.toByteArray(Charsets.UTF_8))
                    }
                }
                .onFailure { Timber.tag(TAG).w(it, "root config pipe write failed") }
        }
            .apply {
                isDaemon = true
                start()
            }
        writer.join(FIFO_WRITE_TIMEOUT_MS)
        if (writer.isAlive) {
            // No reader turned up: open one ourselves to release the blocked writer thread. The
            // dead
            // daemon then surfaces via the launcher's startup probe (core.log shows the read
            // failure).
            Timber.tag(TAG).w("root config handoff timed out; core likely died on launch")
            runCatching { FileInputStream(fifo).use { it.readBytes() } }
        }
        fifo.delete()

        RootDaemonState.save(
            RootDaemonState.Record(
                pid = pid,
                secret = secret,
                mode = mode,
                startTimeTicks = rootProcessStartTimeTicks(pid) ?: 0L,
            )
        )
        Timber.tag(TAG).i("root core launched, pid=%d mode=%s", pid, mode)
        return CoreEndpoint(sock, secret).also { current = it }
    }

    fun stop() {
        process?.let { runCatching { it.kill() } }
        stopOwnerQueryLoop()
        if (running === process) running = null
        process = null
        current = null
    }

    private fun stopOwnerQueryLoop() {
        val channel = ownerChannel
        ownerChannel = null
        runCatching { channel?.close() }
        ownerQueryThread = null
    }

    /**
     * Guarantee a non-empty controller bearer for local REST. Reuses the profile secret when
     * present; otherwise mints one and patches the runtime config.
     */
    private fun ensureControllerSecret(config: String): Pair<String, String> {
        secretFromConfig(config)?.let {
            return config to it
        }
        val secret = UUID.randomUUID().toString().replace("-", "")
        val line = "secret: \"$secret\""
        val lines = config.lineSequence().toMutableList()
        val idx = lines.indexOfFirst { it.trimStart().startsWith("secret:") }
        if (idx >= 0) {
            lines[idx] = line
        } else {
            lines.add(0, line)
        }
        return lines.joinToString("\n") to secret
    }

    /** The top-level `secret:` from the compiled config, or null if the profile sets none. */
    private fun secretFromConfig(config: String): String? {
        val raw =
            config
                .lineSequence()
                .firstOrNull { it.trimStart().startsWith("secret:") }
                ?.substringAfter("secret:")
                ?.trim() ?: return null
        return raw.trim('"', '\'').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Fork the core in [home]: the nativeLibraryDir copy first (non-root exec path), then an
     * extracted, chmod'd copy if the kernel refuses that exec (real exec failures now propagate).
     */
    private fun spawn(home: File, args: Array<String>): NativeProcess {
        val wd = home.absolutePath
        // libclash.so stays raw in nativeLibraryDir (the non-root exec path); fall back to an
        // extracted, chmod'd copy if that exec is refused.
        val bundled = File(context.applicationInfo.nativeLibraryDir, LIB).absolutePath
        return try {
            NativeProcess.start(bundled, args, workdir = wd)
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "exec from nativeLibraryDir failed; retrying from extracted copy")
            NativeProcess.start(extractBin().absolutePath, args, workdir = wd)
        }
    }

    /** Copy the core out of nativeLibraryDir into app storage and chmod it executable. */
    @SuppressLint("SetWorldReadable")
    private fun extractBin(): File {
        val src = File(context.applicationInfo.nativeLibraryDir, LIB)
        val dst = File(context.filesDir, "bin/clash")
        dst.parentFile?.mkdirs()
        if (!dst.exists() || dst.length() != src.length()) {
            src.copyTo(dst, overwrite = true)
        }
        dst.setReadable(true, false)
        dst.setExecutable(true, false)
        return dst
    }

    companion object {
        /**
         * The endpoint of the core currently running (null when stopped). Read by the controller
         * client.
         */
        @Volatile
        var current: CoreEndpoint? = null
            private set

        /**
         * The running core child, tracked statically so it can be killed lock-free (see
         * [killRunning]).
         */
        @Volatile private var running: NativeProcess? = null

        /**
         * Lock-free SIGKILL of the running core child; closing the tun fd it holds drops the
         * VpnService interface. Called from `TunService.onDestroy` so teardown happens even if the
         * scoped stop blocks.
         */
        fun killRunning() {
            running?.let { runCatching { it.kill() } }
            running = null
        }

        /** True if the persisted root daemon still has the recorded process identity. */
        fun isRootDaemonAlive(): Boolean {
            val record = RootDaemonState.load() ?: return false
            return isRootRecordAlive(record)
        }

        private fun isRootRecordAlive(record: RootDaemonState.Record): Boolean {
            val alive =
                runCatching { Shell.cmd("kill -0 ${record.pid}").exec().isSuccess }
                    .getOrDefault(false)
            if (!alive) return false

            val executable =
                runCatching {
                        Shell.cmd("readlink /proc/${record.pid}/exe")
                            .exec()
                            .out
                            .firstOrNull()
                            ?.substringBefore(" (deleted)")
                            ?.let(::File)
                            ?.name
                    }
                    .getOrNull()
            if (executable !in ROOT_CORE_EXECUTABLE_NAMES) return false

            val recordedStartTime = record.startTimeTicks
            return recordedStartTime <= 0L ||
                rootProcessStartTimeTicks(record.pid) == recordedStartTime
        }

        private fun rootProcessStartTimeTicks(pid: Int): Long? =
            runCatching {
                    val stat = Shell.cmd("cat /proc/$pid/stat").exec().out.joinToString(" ")
                    stat.substringAfterLast(") ", missingDelimiterValue = "")
                        .split(Regex("\\s+"))
                        .getOrNull(PROC_STAT_START_TIME_INDEX_AFTER_COMM)
                        ?.toLongOrNull()
                }
                .getOrNull()

        /**
         * True if the non-root VPN child core is still alive. Used by LOCAL_TUN startup verify so a
         * dead process fails immediately instead of spinning on a missing clash.sock.
         */
        fun isLocalCoreAlive(): Boolean {
            val pid = running?.pid ?: return false
            return runCatching {
                    Os.kill(pid, 0)
                    true
                }
                .getOrDefault(false)
        }

        /**
         * The run mode of the persisted root daemon ("tun"/"tproxy" → [RunMode]), or null when
         * none.
         */
        fun rootDaemonMode(): RunMode? = RunMode.fromCoreArg(RootDaemonState.load()?.mode)

        /** Last non-blank line of `<runtimeHome>/core.log`. */
        fun coreLogTail(context: Context): String? = runCatching {
            context.runtimeHomeDir
                .resolve(CORE_LOG)
                .takeIf { it.exists() }
                ?.readLines()
                ?.lastOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(300)
        }
            .getOrNull()

        /** Full `core.log` (Go already pins log-level=error + boot markers). */
        fun coreDiagnosticLog(context: Context): String = runCatching {
            val file = context.runtimeHomeDir.resolve(CORE_LOG)
            if (!file.exists()) return@runCatching ""
            file.readText().trimEnd()
        }
            .getOrDefault("")

        /**
         * Reattach to a live root daemon after an app restart: probe liveness and republish
         * [current] from the persisted secret without relaunching. Returns the mode, or null
         * (clearing stale state).
         */
        fun reconnectRoot(context: Context): String? {
            val record = RootDaemonState.load() ?: return null
            if (!isRootRecordAlive(record)) {
                RootDaemonState.clear()
                return null
            }
            current = CoreEndpoint(context.runtimeHomeDir.resolve(SOCK).absolutePath, record.secret)
            return record.mode
        }

        // The su kill returns fast, but libsu's shell round-trip + mihomo's SIGTERM teardown
        // (tproxy's iptables execs, tun route/rule cleanup) add latency the stop path must not
        // block on.
        private val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Pid of a daemon whose SIGTERM teardown is still running; see [awaitRootStopGrace]. */
        @Volatile private var dyingRootPid: Int? = null

        /**
         * Explicitly stop the root daemon: SIGTERM so mihomo tears down its tun/iptables state, a
         * bounded grace for that teardown, then SIGKILL to close the window for good.
         */
        fun stopRoot() {
            val record = RootDaemonState.load()
            // Clear state FIRST so isRootDaemonAlive() reports "stopped" immediately; the UI
            // must never wait on the kill.
            RootDaemonState.clear()
            current = null
            record ?: return
            dyingRootPid = record.pid
            stopScope.launch {
                try {
                    runCatching { Shell.cmd("kill ${record.pid}").exec() }
                    val deadline = SystemClock.elapsedRealtime() + ROOT_STOP_GRACE_MS
                    while (SystemClock.elapsedRealtime() < deadline && isRootPidAlive(record.pid)) {
                        delay(ROOT_STOP_POLL_MS)
                    }
                    if (isRootPidAlive(record.pid)) {
                        runCatching { Shell.cmd("kill -9 ${record.pid}").exec() }
                    }
                } finally {
                    dyingRootPid = null
                }
            }
        }

        private fun isRootPidAlive(pid: Int): Boolean =
            runCatching { Shell.cmd("kill -0 $pid").exec().isSuccess }.getOrDefault(false)

        /**
         * Block (bounded) until a dying predecessor has finished tearing down. The daemon's ip
         * rules, nftables table and iptables chains all carry fixed names, so a teardown that
         * outlives the stop can dismantle what a freshly launched successor just set up.
         */
        fun awaitRootStopGrace() {
            val deadline = SystemClock.elapsedRealtime() + ROOT_STOP_GRACE_MS + ROOT_STOP_POLL_MS
            while (dyingRootPid != null && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(ROOT_STOP_POLL_MS)
            }
        }

        private const val ROOT_STOP_GRACE_MS = 2_000L
        private const val ROOT_STOP_POLL_MS = 100L

        // Config is delivered over this named pipe (never persisted); LEGACY_ROOT_CONFIG is the old
        // plaintext file, deleted on launch. 0600 = owner-only (app creates it, root reads it).
        private const val ROOT_CONFIG_PIPE = "run.pipe"
        private const val LEGACY_ROOT_CONFIG = "run.yaml"
        private const val ROOT_PIPE_MODE = 384
        private const val FIFO_WRITE_TIMEOUT_MS = 5000L

        /** Core stdout/stderr log under [runtimeHomeDir]; launcher redirects both modes here. */
        const val CORE_LOG = "core.log"

        private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        /** Controller socket filename under the runtime home dir. Read by the client controller. */
        const val SOCK = "clash.sock"

        @Volatile private var controller: CoreApi? = null

        /** Shared local-core controller client (unix socket path fixed, secret from [current]). */
        fun controller(context: Context): CoreApi =
            controller
                ?: CoreController(
                        local =
                            CoreController.Local(
                                socketPath = context.runtimeHomeDir.resolve(SOCK).absolutePath,
                                secret = { current?.secret.orEmpty() },
                            )
                    )
                    .also { controller = it }

        private const val TAG = "CoreProcess"
        private const val LIB = "libclash.so"
        private val ROOT_CORE_EXECUTABLE_NAMES = setOf(LIB, "clash")
        // After stripping "pid (comm) ", index 0 is field 3 (state), so field 22 is index 19.
        private const val PROC_STAT_START_TIME_INDEX_AFTER_COMM = 19
        private const val CHUNK = 32 * 1024
        private const val OWNER_QUERY_BUFFER_SIZE = 4096
        private const val UNKNOWN_SOCKET_OWNER = "-1\t"
        private val END = byteArrayOf(1)
    }
}
