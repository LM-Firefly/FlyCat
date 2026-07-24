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
import android.os.ParcelFileDescriptor
import android.system.Os
import com.github.yumelira.yumebox.core.bridge.Channel
import com.github.yumelira.yumebox.core.bridge.NativeProcess
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.runtime.api.CoreApi
import com.github.yumelira.yumebox.runtime.service.controller.CoreController
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

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

    /** Fork the core for VpnService mode, deliver [config] and [tunFd], and publish [current]. */
    fun startVpn(
        tunFd: Int,
        gateway: String,
        portal: String,
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
                "--portal",
                portal,
                "--dns",
                dns,
            )
        Timber.tag(TAG).i("launch core, tunFd=%d", tunFd)
        val proc = spawn(home, args)
        process = proc
        running = proc

        // Stream config over the socketpair (in memory), then the TUN fd as a terminating
        // SCM_RIGHTS
        // message. The core dups the fd; the app closes its own copy.
        runCatching {
            val channel = Channel(proc.channelFd)
            val bytes = runtimeConfig.toByteArray(Charsets.UTF_8)
            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(CHUNK, bytes.size - offset)
                channel.writeMessage(bytes, offset, len)
                offset += len
            }
            channel.writeMessage(END, 0, END.size, attachFd = tunFd)
            channel.close()
        }
            .onFailure { Timber.tag(TAG).w(it, "config/fd handoff failed") }
        runCatching { ParcelFileDescriptor.adoptFd(tunFd).close() }

        return CoreEndpoint(sock, secret).also { current = it }
    }

    /**
     * Launch the core as a detached ROOT daemon (tun / tproxy) via `su`: it runs in the root
     * SELinux domain (free to open a kernel TUN, program routes, iptables) and, unlike the VPN
     * child core, outlives the app process — reattached over the REST socket ([reconnectRoot]).
     * [mode] = "tun"/"tproxy".
     */
    fun startRoot(mode: String, config: String): CoreEndpoint {
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

        RootDaemonState.save(RootDaemonState.Record(pid = pid, secret = secret, mode = mode))
        Timber.tag(TAG).i("root core launched, pid=%d mode=%s", pid, mode)
        return CoreEndpoint(sock, secret).also { current = it }
    }

    fun stop() {
        process?.let { runCatching { it.kill() } }
        if (running === process) running = null
        process = null
        current = null
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

        /** True if the persisted root daemon is still alive (`kill -0` via su). */
        fun isRootDaemonAlive(): Boolean {
            val record = RootDaemonState.load() ?: return false
            return runCatching { Shell.cmd("kill -0 ${record.pid}").exec().isSuccess }
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
            val alive = runCatching {
                Shell.cmd("kill -0 ${record.pid}").exec().isSuccess
            }.getOrDefault(false)
            if (!alive) {
                RootDaemonState.clear()
                return null
            }
            current = CoreEndpoint(context.runtimeHomeDir.resolve(SOCK).absolutePath, record.secret)
            return record.mode
        }

        // The su kill returns fast, but libsu's shell round-trip + mihomo's SIGTERM teardown
        // (tproxy's
        // dozens of iptables execs, tun route/rule cleanup) add latency the stop path must not
        // block on.
        private val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Explicitly stop the root daemon (`su kill`, SIGTERM so mihomo tears down tun/iptables).
         */
        fun stopRoot() {
            val record = RootDaemonState.load()
            // Clear state FIRST so isRootDaemonAlive() reports "stopped" immediately; the UI must
            // never
            // wait on the kill. mihomo tears its iptables/tun down on the SIGTERM sent off-thread
            // below.
            RootDaemonState.clear()
            current = null
            record ?: return
            stopScope.launch { runCatching { Shell.cmd("kill ${record.pid}").exec() } }
        }

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
        private const val CHUNK = 32 * 1024
        private val END = byteArrayOf(1)
    }
}
