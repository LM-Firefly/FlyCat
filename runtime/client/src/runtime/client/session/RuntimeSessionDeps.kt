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

package com.github.yumelira.yumebox.runtime.client.session

import android.content.Context
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.api.*
import com.github.yumelira.yumebox.runtime.service.AndroidRuntimeStatusStore
import com.github.yumelira.yumebox.runtime.service.core.AndroidProcessController
import com.github.yumelira.yumebox.runtime.service.session.AndroidRuntimeLauncher
import kotlinx.coroutines.CoroutineScope

/** Construction bag for [RuntimeSession]. Keeps the session entry free of long argument lists. */
internal data class RuntimeSessionDeps(
    val context: Context,
    val scope: CoroutineScope,
    val networkSettingsStorage: NetworkSettingsStore,
    val remoteControllerStore: RemoteControllerStore,
    val statusStore: RuntimeStatusStore = AndroidRuntimeStatusStore,
    val processController: ProcessController = AndroidProcessController(context),
    val launcher: RuntimeLauncher =
        AndroidRuntimeLauncher(
            context = context,
            stopAction = { Intents.actionRuntimeRequestStop(context.appContextOrSelf.packageName) },
        ),
    val queryTrafficNowAction: suspend () -> Long = { 0L },
    val queryTrafficTotalAction: suspend () -> Long = { 0L },
    val onAfterRunning: suspend () -> Unit = {},
    val onAfterIdle: suspend () -> Unit = {},
    val onGroupTick: suspend () -> Unit = {},
    val onTrafficTickExtra: suspend (tick: Int) -> Unit = {},
    val onClearGroups: (Boolean) -> Unit = {},
)
