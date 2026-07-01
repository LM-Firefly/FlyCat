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

package com.github.yumelira.yumebox.runtime.service

import android.content.Intent
import android.net.VpnService
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.service.notification.ServiceNotificationManager
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.runtime.session.SessionRuntimeSpecFactory
import com.github.yumelira.yumebox.runtime.service.runtime.session.VpnTunTransport
import com.github.yumelira.yumebox.service.RuntimeForegroundController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val controller =
        RuntimeForegroundController(
            service = this,
            scope = this,
            mode = ProxyMode.Tun,
            label = "Tun",
            notificationConfig = ServiceNotificationManager.VPN_CONFIG,
            logScope = RuntimeStartupLogStore.Scope.LOCAL_TUN,
            createTransport = { VpnTunTransport(this) },
            createSpec = { SessionRuntimeSpecFactory(appContextOrSelf).createTunSpec() },
        )

    override fun onCreate() {
        super.onCreate()
        controller.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        controller.onStartCommand()

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        controller.onTrimMemory()
    }
}
