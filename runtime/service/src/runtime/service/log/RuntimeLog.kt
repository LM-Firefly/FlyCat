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

package com.github.yumelira.yumebox.runtime.service.log

import android.content.Context
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * The single runtime log.
 *
 * Everything that explains why a session or an import behaved the way it did lands in one file: the
 * VPN service, the root daemon, the launchers, the session runtime, profile import, and the mihomo
 * core's own log. These used to be three disjoint, prefix-tagged, untimestamped files that had to
 * be read side by side to reconstruct a single startup.
 *
 * Lines are fixed-width columns so the eye can drop straight to the one that matters:
 * ```
 * 2026-07-26 09:12:33 [Info]  local/session      started elapsedMs=812
 * 2026-07-26 09:12:34 [Warn]  local/verify       actualGroups=0 controller=down attempt=1/10
 * 2026-07-26 09:12:35 [Error] profile/core-test  import rejected | ParseError: rules[3] invalid
 * 2026-07-26 09:12:35 [Error] core/local         msg="unsupported rule type"
 * ```
 *
 * `<date time> [<Level>] <source>/<stage>  <message>[ | <cause chain>]`. Seconds are enough to
 * order a startup; the bracketed level is the only capitalised token, which makes it the natural
 * anchor when scanning, and everything after the origin column is plain message text.
 *
 * Writes are appended off the caller's thread, so logging never sits on a startup path.
 */
object RuntimeLog {

    enum class Level(val label: String) {
        Debug("Debug"),
        Info("Info"),
        Warn("Warn"),
        Error("Error"),
    }

    /**
     * Where an entry came from. One short keyword each, so a reader can filter a whole subsystem
     * out by eye or by `grep`.
     */
    enum class Source(val tag: String) {
        /** [RunMode.VpnService] session: launcher, service, transport, session runtime. */
        LocalTun("local"),

        /** Root `tun` / `tproxy` daemon session, same span of stages as [LocalTun]. */
        RootTun("root"),

        /** Subscription import, update and config validation. */
        Profile("profile"),

        /** Lines lifted verbatim out of the mihomo core's own log. */
        Core("core");

        companion object {
            fun forMode(mode: RunMode): Source =
                when (mode) {
                    RunMode.VpnService -> LocalTun
                    RunMode.Tun,
                    RunMode.Tproxy -> RootTun
                }
        }
    }

    /** Bound writer, so call sites don't repeat the context and source on every line. */
    class Writer internal constructor(
        private val context: Context,
        val source: Source,
    ) {
        fun d(stage: String, message: String) =
            RuntimeLog.write(context, Level.Debug, source, stage, message)

        fun i(stage: String, message: String) =
            RuntimeLog.write(context, Level.Info, source, stage, message)

        fun w(stage: String, message: String, error: Throwable? = null) =
            RuntimeLog.write(context, Level.Warn, source, stage, message, error)

        fun e(stage: String, message: String, error: Throwable? = null) =
            RuntimeLog.write(context, Level.Error, source, stage, message, error)

        /**
         * Marks the start of a distinct attempt. Replaces the old "delete the file on every start"
         * behaviour: the previous attempt is usually exactly what a bug report needs, so it is kept
         * and the new one is made findable instead.
         */
        fun beginSession(stage: String, message: String) {
            RuntimeLog.append(context, SESSION_SEPARATOR)
            i(stage, message)
        }

        /**
         * Folds the core's own log into this stream, one `[CORE/…]` line per core line, so a config
         * the core rejected reads in place instead of in a separate export entry.
         */
        fun coreDiagnostics(diagnostics: String) {
            // Stage names the session the core ran under, e.g. `core/local`.
            val stage = source.tag
            if (diagnostics.isBlank()) {
                RuntimeLog.write(
                    context,
                    Level.Warn,
                    Source.Core,
                    stage,
                    "no core log — the core may have died before it could write one",
                )
                return
            }
            RuntimeLog.write(context, Level.Info, Source.Core, stage, "--- core log begin ---")
            diagnostics.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                RuntimeLog.write(context, coreLineLevel(line), Source.Core, stage, line.trim())
            }
            RuntimeLog.write(context, Level.Info, Source.Core, stage, "--- core log end ---")
        }
    }

    fun writer(context: Context, source: Source): Writer = Writer(context.appContextOrSelf, source)

    fun writer(context: Context, mode: RunMode): Writer = writer(context, Source.forMode(mode))

    fun write(
        context: Context,
        level: Level,
        source: Source,
        stage: String,
        message: String,
        error: Throwable? = null,
    ) {
        if (message.isBlank() && error == null) return
        append(context, format(level, source, stage, message, error))
    }

    /** The whole log, oldest first. Used by the About screen's export. */
    fun snapshot(context: Context): String =
        runCatching {
                ioExecutor
                    .submit<String> {
                        synchronized(lock) {
                            val file = file(context)
                            if (file.exists()) file.readText(StandardCharsets.UTF_8) else ""
                        }
                    }
                    .get()
            }
            .getOrDefault("")

    private fun file(context: Context): File = File(context.appContextOrSelf.filesDir, FILE_NAME)

    private fun format(
        level: Level,
        source: Source,
        stage: String,
        message: String,
        error: Throwable?,
    ): String {
        val timestamp = TIMESTAMP.format(Instant.now().atZone(ZoneId.systemDefault()))
        val origin = if (stage.isBlank()) source.tag else "${source.tag}/$stage"
        val body = message.trimEnd().ifBlank { "(no message)" }
        return buildString {
            append(timestamp)
            append(' ')
            // Pad level and origin so the message column lines up across every entry — scanning a
            // startup is column-reading, not sentence-reading.
            append("[${level.label}]".padEnd(LEVEL_COLUMN_WIDTH))
            append(origin.padEnd(ORIGIN_COLUMN_WIDTH))
            append(body)
            error?.let { append(" | ").append(describe(it)) }
        }
    }

    /**
     * Full cause chain, because the outermost message is routinely the least informative one — a
     * generic "runtime start failed" wrapping the actual parse error reported by the core.
     */
    private fun describe(error: Throwable): String {
        val chain = StringBuilder()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            val node = current
            if (depth > 0) chain.append(" <- ")
            chain.append(node::class.java.simpleName)
            node.message?.takeIf(String::isNotBlank)?.let { chain.append(": ").append(it) }
            val cause = node.cause
            current = if (cause === node) null else cause
            depth++
        }
        return chain.toString().take(MAX_ERROR_CHARS)
    }

    /** mihomo writes `level=error` / `level=warning` into its own log; keep that classification. */
    private fun coreLineLevel(line: String): Level =
        when {
            line.contains("level=error", ignoreCase = true) -> Level.Error
            line.contains("level=warning", ignoreCase = true) ||
                line.contains("level=warn", ignoreCase = true) -> Level.Warn

            line.contains("level=debug", ignoreCase = true) -> Level.Debug
            else -> Level.Info
        }

    private fun append(context: Context, line: String) {
        val appContext = context.appContextOrSelf
        val normalized = "${line.trimEnd()}\n"
        ioExecutor.execute {
            synchronized(lock) {
                runCatching {
                    val file = file(appContext)
                    file.parentFile?.mkdirs()
                    file.appendText(normalized, StandardCharsets.UTF_8)
                    trimIfOversized(file)
                    dropLegacyLogs(appContext)
                }
            }
        }
    }

    /**
     * The pre-unification split files are no longer written or exported, so an upgraded install
     * would otherwise keep them forever with no way to reach them. Runs on the single writer
     * thread, hence the plain flag.
     */
    private fun dropLegacyLogs(context: Context) {
        if (legacyLogsDropped) return
        legacyLogsDropped = true
        LEGACY_FILE_NAMES.forEach { name ->
            runCatching { File(context.filesDir, name).delete() }
        }
    }

    /**
     * Caller must hold [lock]. A chatty core must not grow the file without bound, but the *recent*
     * history is the part worth keeping, so the head is dropped rather than the tail.
     */
    private fun trimIfOversized(file: File) {
        if (file.length() <= MAX_BYTES) return
        val kept = file.readText(StandardCharsets.UTF_8).takeLast(KEEP_CHARS)
        val aligned = kept.substringAfter('\n', missingDelimiterValue = kept)
        file.writeText("$TRUNCATION_MARKER\n$aligned", StandardCharsets.UTF_8)
    }

    const val FILE_NAME = "runtime.log"

    private const val SESSION_SEPARATOR =
        "--------------------------------------------------------------------------------"
    private const val TRUNCATION_MARKER = "... earlier entries dropped (log size limit) ..."
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val KEEP_CHARS = 1024 * 1024
    private const val MAX_CAUSE_DEPTH = 5
    private const val MAX_ERROR_CHARS = 2_000

    // "[Error]" + one space of gutter.
    private const val LEVEL_COLUMN_WIDTH = 8

    // Fits the widest real origin ("profile/core-test") + two spaces; longer ones just overflow.
    private const val ORIGIN_COLUMN_WIDTH = 19

    private val LEGACY_FILE_NAMES = listOf("local_tun_startup.log", "root_tun_startup.log")

    @Volatile private var legacyLogsDropped = false

    // Local wall-clock, seconds precision: enough to order a startup, and what a person actually
    // reads. Sub-second churn and a zone suffix on every line were noise.
    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val lock = Any()
    private val ioExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "runtime-log").apply { isDaemon = true }
        }
}
