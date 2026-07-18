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

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.api.service.remote.ILogObserver
import com.github.yumelira.yumebox.runtime.service.common.log.Log
import com.github.yumelira.yumebox.runtime.service.runtime.util.sendBroadcastSelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stops all local proxy services (TUN and HTTP) and resets the Clash core.
 * Extracted from the former `ClashManager.requestStop()`.
 */
fun Context.requestClashStop() {
    runCatching { sendBroadcastSelf(Intent(Intents.actionClashRequestStop(packageName))) }
    runCatching {
        stopService(Intent(this, TunService::class.java))
        stopService(Intent(this, ClashService::class.java))
    }
    runCatching {
        Clash.stopHttp()
        Clash.stopTun()
        Clash.reset()
    }
}

/**
 * Manages a logcat subscription from the Clash core. Attach an [ILogObserver] to receive
 * log messages; detach to cancel the subscription and release resources.
 * Extracted from the former `ClashManager.setLogObserver()`.
 */
class ClashLogcatSubscription(private val scope: CoroutineScope) {
    private var logReceiver: ReceiveChannel<LogMessage>? = null
    fun attach(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()
            }

            if (observer != null) {
                logReceiver =
                    Clash.subscribeLogcat().also { receiver ->
                        scope.launch {
                            try {
                                while (isActive) {
                                    observer.newItem(receiver.receive())
                                }
                            } catch (_: CancellationException) {} catch (error: Exception) {
                                Log.w("UI crashed", error)
                            } finally {
                                withContext(NonCancellable) {
                                    receiver.cancel()
                                }
                            }
                        }
                    }
            }
        }
    }

    fun detach() {
        attach(null)
    }
}
