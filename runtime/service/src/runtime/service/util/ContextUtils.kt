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

package com.github.yumelira.yumebox.runtime.service.util

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.runtime.api.Intents
import java.io.File
import java.util.UUID

val Context.importedDir: File
    get() = filesDir.resolve("imported")

val File.directoryLastModified: Long?
    get() {
        return walk().map { it.lastModified() }.maxOrNull()
    }

fun Context.sendBroadcastSelf(intent: Intent) {
    sendBroadcast(intent.setPackage(this.packageName))
}

fun Context.sendProfileChanged(uuid: UUID) {
    val intent =
        Intent(Intents.ACTION_PROFILE_CHANGED).putExtra(Intents.EXTRA_UUID, uuid.toString())

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

fun Context.sendClashStarted() {
    sendBroadcastSelf(Intent(Intents.ACTION_CLASH_STARTED))
}

fun Context.sendClashStopped(reason: String?) {
    sendBroadcastSelf(
        Intent(Intents.ACTION_CLASH_STOPPED).putExtra(Intents.EXTRA_STOP_REASON, reason)
    )
}
