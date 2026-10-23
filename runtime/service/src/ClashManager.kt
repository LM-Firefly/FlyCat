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

package com.github.lmfirefly.flycat.runtime.service

import android.content.Context
import android.content.Intent
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.runtime.api.constants.Intents
import com.github.lmfirefly.flycat.runtime.service.android.TunService
import com.github.lmfirefly.flycat.runtime.service.util.sendBroadcastSelf

/**
 * Stops all local proxy services (TUN and HTTP) and resets the Clash core.
 * Extracted from the former `ClashManager.requestStop()`.
 */
fun Context.requestClashStop() {
    runCatching { sendBroadcastSelf(Intent(Intents.actionClashRequestStop(packageName))) }
    runCatching {
        stopService(Intent(this, TunService::class.java))
    }
    runCatching {
        Clash.stopHttp()
        Clash.stopTun()
        Clash.reset()
    }
}
