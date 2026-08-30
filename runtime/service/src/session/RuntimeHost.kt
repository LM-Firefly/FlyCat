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

package com.github.lmfirefly.flycat.runtime.service.session

import android.content.Context
import com.github.lmfirefly.flycat.core.model.LogMessage
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.runtime.api.contract.RuntimeSnapshot
import com.github.lmfirefly.flycat.runtime.api.session.RuntimeSpec

interface RuntimeHost {
    val context: Context
    val mode: RunMode

    fun onStarting(spec: RuntimeSpec)

    fun onStarted(spec: RuntimeSpec)

    fun onStopped(reason: String?)

    fun onProfileLoaded(profileUuid: String)

    fun onSnapshotChanged(snapshot: RuntimeSnapshot)

    fun onLogReady(ready: Boolean)

    fun onLogItem(log: LogMessage) {}

    fun reportFailure(error: String)
}
