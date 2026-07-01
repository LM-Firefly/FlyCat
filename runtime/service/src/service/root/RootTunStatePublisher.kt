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

package com.github.yumelira.yumebox.runtime.service.root

import android.os.RemoteCallbackList
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunEncode
import com.github.yumelira.yumebox.service.root.IRootTunStateObserver

/**
 * Single root-side write path for [RootTunStateStore]. Wraps every store mutation with a fan-out
 * broadcast to registered [IRootTunStateObserver] callbacks so the main process can observe state
 * over a binder channel instead of polling shared MMKV.
 *
 * The store remains the backing persistence; this class only adds the push channel. With zero
 * observers registered, [update]/[markIdle] are behavior-identical to calling the store directly.
 */
class RootTunStatePublisher(private val store: RootTunStateStore) {
    private val observers = RemoteCallbackList<IRootTunStateObserver>()

    fun snapshot(): RootTunStatus = store.snapshot()

    @Synchronized
    fun update(status: RootTunStatus) {
        store.updateStatus(status)
        broadcast(store.snapshot())
    }

    @Synchronized
    fun markIdle(error: String? = null) {
        store.markIdle(error)
        broadcast(store.snapshot())
    }

    fun register(observer: IRootTunStateObserver) {
        observers.register(observer)
        runCatching { observer.onStatusChanged(encode(store.snapshot())) }
    }

    fun unregister(observer: IRootTunStateObserver) {
        observers.unregister(observer)
    }

    private fun broadcast(status: RootTunStatus) {
        val json = encode(status)
        val count = observers.beginBroadcast()
        try {
            for (i in 0 until count) {
                runCatching { observers.getBroadcastItem(i).onStatusChanged(json) }
            }
        } finally {
            observers.finishBroadcast()
        }
    }

    private fun encode(status: RootTunStatus): String = rootTunEncode(status)
}
