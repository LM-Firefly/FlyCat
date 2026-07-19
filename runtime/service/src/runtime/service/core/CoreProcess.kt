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

import android.content.Context
import android.os.ParcelFileDescriptor
import com.github.yumelira.yumebox.core.bridge.Channel
import com.github.yumelira.yumebox.core.bridge.NativeProcess
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.runtime.api.IClashManager
import com.github.yumelira.yumebox.runtime.service.manager.HttpClashManager
import com.topjohnwu.superuser.Shell
import java.io.File
import java.security.SecureRandom
import timber.log.Timber

/** The running core's UNIX REST controller: socket path + bearer secret. */
data class CoreEndpoint(val sock: String, val secret: String)

/**
 * Launches and owns the out-of-process mihomo core (the `native` package built as a PIE, packaged as
 * `libclash.so`). Minimal by design:
 *
 *  1. fork+exec the PIE with [runtimeHomeDir] as its working directory. It ships in nativeLibraryDir
 *     (executable there, which is the non-root exec path); if that exec is refused we fall back to a
 *     copy extracted into app storage and marked executable (the path a root launch uses);
 *  2. stream the compiled config to it over the socketpair (in memory — never written to disk in
 *     plaintext), then hand over the VpnService TUN fd via SCM_RIGHTS;
 *  3. expose the controller endpoint via [current] for the client's REST manager.
 *
 * Egress: the tun always uses the userspace gVisor stack, so the VpnService excluding the app's own
 * uid from the tunnel keeps the core's egress off it — no per-socket protect needed.
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
        val sock = File(home, SOCK).absolutePath
        // Reuse the config's own bearer secret so a web dashboard on external-controller shares it;
        // fall back to a random one when the profile sets none (the unix socket is uid-protected too).
        val secret = secretFromConfig(config) ?: randomSecret()

        val args = arrayOf(
            "--home", home.absolutePath,
            "--controller", sock,
            "--secret", secret,
            "--gateway", gateway,
            "--portal", portal,
            "--dns", dns,
        )
        Timber.tag(TAG).i("launch core, tunFd=%d", tunFd)
        val proc = spawn(home, args)
        process = proc
        running = proc

        // Stream config over the socketpair (in memory), then the TUN fd as a terminating SCM_RIGHTS
        // message. The core dups the fd; the app closes its own copy.
        runCatching {
            val channel = Channel(proc.channelFd)
            val bytes = config.toByteArray(Charsets.UTF_8)
            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(CHUNK, bytes.size - offset)
                channel.writeMessage(bytes, offset, len)
                offset += len
            }
            channel.writeMessage(END, 0, END.size, attachFd = tunFd)
            channel.close()
        }.onFailure { Timber.tag(TAG).w(it, "config/fd handoff failed") }
        runCatching { ParcelFileDescriptor.adoptFd(tunFd).close() }

        return CoreEndpoint(sock, secret).also { current = it }
    }

    /**
     * Launch the core as a detached ROOT daemon (tun / tproxy) via `su`, so it runs in the root
     * SELinux domain — free to open a kernel TUN, program routes, and set up iptables. Unlike the
     * VPN/HTTP child cores this is NOT a tracked child: it outlives the app process and is reattached
     * over the REST socket (see [reconnectRoot]). [mode] is "tun" or "tproxy".
     */
    fun startRoot(mode: String, config: String): CoreEndpoint {
        val home = context.runtimeHomeDir.apply { mkdirs() }
        val sock = File(home, SOCK).absolutePath
        val secret = secretFromConfig(config) ?: randomSecret()

        // A detached `su` process can't inherit the app's socketpair, so hand the compiled config to
        // the daemon via a file it reads with --config. It stays in the app-private home dir; on a
        // rooted device root can already read process memory, so this is no additional exposure.
        val configFile = File(home, ROOT_CONFIG).apply { writeText(config) }
        val lib = File(context.applicationInfo.nativeLibraryDir, LIB).absolutePath
        val logFile = File(home, ROOT_LOG).absolutePath
        val command =
            "exec ${quote(lib)} --mode $mode --home ${quote(home.absolutePath)} " +
                "--controller ${quote(sock)} --secret ${quote(secret)} " +
                "--config ${quote(configFile.absolutePath)} " +
                "</dev/null >${quote(logFile)} 2>&1 & echo \$!"

        Timber.tag(TAG).i("launch root core, mode=%s", mode)
        val result = Shell.cmd(command).exec()
        val pid = result.out.asSequence().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()
        check(result.isSuccess && pid != null && pid > 0) {
            "root core launch failed (success=${result.isSuccess} out=${result.out})"
        }

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

    /** The top-level `secret:` from the compiled config, or null if the profile sets none. */
    private fun secretFromConfig(config: String): String? {
        val raw = config.lineSequence()
            .firstOrNull { it.startsWith("secret:") }
            ?.substringAfter("secret:")
            ?.trim()
            ?: return null
        return raw.trim('"', '\'').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Fork the core with [home] as its working directory. Tries the copy in nativeLibraryDir first
     * (executable there for a non-root app); if the kernel refuses that exec, retries from a copy
     * extracted into app storage and marked executable. Exec failures now propagate (the child
     * reports them back), so the fallback triggers on a real failure rather than silently.
     */
    private fun spawn(home: File, args: Array<String>): NativeProcess {
        val wd = home.absolutePath
        // libclash.so must stay raw in nativeLibraryDir (executable there for a non-root app); that is
        // the exec path. If the kernel refuses it, retry from an extracted, chmod'd copy (the path a
        // root launch uses).
        val bundled = File(context.applicationInfo.nativeLibraryDir, LIB).absolutePath
        return try {
            NativeProcess.start(bundled, args, workdir = wd)
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "exec from nativeLibraryDir failed; retrying from extracted copy")
            NativeProcess.start(extractBin().absolutePath, args, workdir = wd)
        }
    }

    /** Copy the core out of nativeLibraryDir into app storage and chmod it executable. */
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

    private fun randomSecret(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** The endpoint of the core currently running (null when stopped). Read by the REST client. */
        @Volatile
        var current: CoreEndpoint? = null
            private set

        /** The running core child, tracked statically so it can be killed lock-free (see [killRunning]). */
        @Volatile
        private var running: NativeProcess? = null

        /**
         * Lock-free SIGKILL of the running core child. Killing it closes the tun fd it holds, which
         * brings the VpnService interface down — so `TunService.onDestroy` calls this to guarantee
         * teardown even if the session-scoped stop is blocked (e.g. on the runtime lock).
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

        /** The run mode of the persisted root daemon ("tun"/"tproxy" → [RunMode]), or null when none. */
        fun rootDaemonMode(): RunMode? = RunMode.fromCoreArg(RootDaemonState.load()?.mode)

        /**
         * Last non-blank line of the root core's log — the dead-on-arrival diagnostic (a rejected
         * config makes the core print one fatal line and exit). Root-created but world-readable.
         */
        fun rootCoreLogTail(context: Context): String? = runCatching {
            context.runtimeHomeDir.resolve(ROOT_LOG).takeIf { it.exists() }
                ?.readLines()?.lastOrNull { it.isNotBlank() }?.trim()?.take(300)
        }.getOrNull()

        /**
         * Reattach to a still-running root daemon after an app restart: probe liveness and, if alive,
         * republish [current] from the persisted secret WITHOUT relaunching. Returns the running mode
         * ("tun"/"tproxy"), or null when no live daemon exists (stale state is cleared).
         */
        fun reconnectRoot(context: Context): String? {
            val record = RootDaemonState.load() ?: return null
            val alive =
                runCatching { Shell.cmd("kill -0 ${record.pid}").exec().isSuccess }
                    .getOrDefault(false)
            if (!alive) {
                RootDaemonState.clear()
                return null
            }
            current = CoreEndpoint(context.runtimeHomeDir.resolve(SOCK).absolutePath, record.secret)
            return record.mode
        }

        /** Explicitly stop the root daemon (`su kill`, SIGTERM so mihomo tears down tun/iptables). */
        fun stopRoot() {
            RootDaemonState.load()?.let { record ->
                runCatching { Shell.cmd("kill ${record.pid}").exec() }
            }
            RootDaemonState.clear()
            current = null
        }

        private const val ROOT_CONFIG = "run.yaml"
        private const val ROOT_LOG = "core.log"

        private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        /** Controller socket filename under the runtime home dir. Read by the client's REST manager. */
        const val SOCK = "clash.sock"

        @Volatile
        private var rest: IClashManager? = null

        /**
         * Shared [IClashManager] over the running local core (mihomo REST-over-unix). The socket path
         * is fixed per install; the secret is read fresh from [current] per request, so one instance
         * serves every session. Used by both the service (telemetry) and the client's gateway.
         */
        fun rest(context: Context): IClashManager = rest ?: HttpClashManager(
            local = HttpClashManager.Local(
                socketPath = context.runtimeHomeDir.resolve(SOCK).absolutePath,
                secret = { current?.secret.orEmpty() },
            ),
        ).also { rest = it }

        private const val TAG = "CoreProcess"
        private const val LIB = "libclash.so"
        private const val CHUNK = 32 * 1024
        private val END = byteArrayOf(1)
    }
}
