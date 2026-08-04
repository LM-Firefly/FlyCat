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

@file:Suppress("UnusedSymbol", "SimplifiableCallChain")

package com.github.yumeyucca.yumebox.runtime.service.util

import android.content.Context
import android.content.Intent
import com.github.yumeyucca.yumebox.runtime.api.Intents
import java.io.File
import java.util.*

val Context.importedDir: File
    get() = filesDir.resolve("imported")

val File.directoryLastModified: Long?
    get() {
        return walk().map { it.lastModified() }.maxOrNull()
    }

fun Context.sendBroadcastSelf(intent: Intent) {
    sendBroadcast(intent.setPackage(this.packageName))
}

fun Context.sendProfileChanged(uuid: UUID, affectsRuntime: Boolean) {
    val intent =
        Intent(Intents.ACTION_PROFILE_CHANGED)
            .putExtra(Intents.EXTRA_UUID, uuid.toString())
            .putExtra(Intents.EXTRA_AFFECTS_RUNTIME, affectsRuntime)

    sendBroadcastSelf(intent)
}

fun Context.sendProfileLoaded(uuid: UUID) {
    sendBroadcastSelf(
        Intent(Intents.ACTION_PROFILE_LOADED).putExtra(Intents.EXTRA_UUID, uuid.toString())
    )
}

fun Context.sendOverrideChanged() {
    sendBroadcastSelf(Intent(Intents.ACTION_OVERRIDE_CHANGED))
}

fun Context.sendServiceRecreated() {
    sendBroadcastSelf(Intent(Intents.ACTION_SERVICE_RECREATED))
}

fun Context.sendRuntimeStarted() {
    sendBroadcastSelf(Intent(Intents.ACTION_RUNTIME_STARTED))
    requestTileRefresh()
}

fun Context.sendRuntimeStopped(reason: String?) {
    sendBroadcastSelf(
        Intent(Intents.ACTION_RUNTIME_STOPPED).putExtra(Intents.EXTRA_STOP_REASON, reason)
    )
    requestTileRefresh()
}

/**
 * Nudge the QS tile to re-read the runtime state. The tile only self-refreshes while its panel is
 * open (onStartListening); without this, starting/stopping from the app UI leaves it stale. This
 * asks the system to call onStartListening even when the panel is closed.
 */
private fun Context.requestTileRefresh() {
    runCatching {
        android.service.quicksettings.TileService.requestListeningState(
            this,
            android.content.ComponentName(
                this,
                com.github.yumeyucca.yumebox.runtime.service.ProxyTileService::class.java,
            ),
        )
    }
}
