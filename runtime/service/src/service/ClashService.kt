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
import android.os.Binder
import android.os.IBinder
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.runtime.service.notification.ServiceNotificationManager
import com.github.yumelira.yumebox.runtime.service.runtime.session.LocalHttpTransport
import com.github.yumelira.yumebox.runtime.service.runtime.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.runtime.session.SessionRuntimeSpecFactory

class ClashService : BaseService() {
    private val controller by lazy {
        ProxySessionController(
            service = this,
            scope = this,
            mode = ProxyMode.Http,
            logScope = RuntimeStartupLogStore.Scope.LOCAL_HTTP,
            notificationConfig = ServiceNotificationManager.HTTP_CONFIG,
            transportFactory = { LocalHttpTransport(this) },
            specFactory = { ctx -> SessionRuntimeSpecFactory(ctx).createHttpSpec() },
            logTag = "LOCAL_HTTP",
            onClashRequestStopReceived = { reason, runtime ->
                runtime?.requestStop(reason)
                stopSelf()
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        controller.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.onStartCommand()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        controller.onTrimMemory()
    }
}
