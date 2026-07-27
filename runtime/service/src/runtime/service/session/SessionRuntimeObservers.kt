/*
 * This file is part of YumeBox.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumelira.yumebox.runtime.service.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf

internal class SessionRuntimeObservers(
    context: Context,
    private val transport: RuntimeTransport,
) {
    private val appContext = context.appContextOrSelf
    private var networkObserver: ServiceNetworkObserver? = null
    private var timeZoneReceiver: BroadcastReceiver? = null

    fun start() {
        if (networkObserver == null) {
            networkObserver =
                ServiceNetworkObserver(appContext) { transport.onNetworkChanged() }.also { it.start() }
        }
        if (timeZoneReceiver == null) {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) = Unit
                }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            timeZoneReceiver = receiver
        }
    }

    fun stop() {
        runCatching { networkObserver?.stop() }
        networkObserver = null
        timeZoneReceiver?.let { receiver -> runCatching { appContext.unregisterReceiver(receiver) } }
        timeZoneReceiver = null
    }
}
