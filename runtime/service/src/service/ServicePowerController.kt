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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServicePowerController(private val context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val _screenOn = MutableStateFlow(isScreenOn())
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val _deviceIdle = MutableStateFlow(isDeviceIdle())
    val deviceIdle: StateFlow<Boolean> = _deviceIdle.asStateFlow()

    private val _appForeground = MutableStateFlow(false)
    val appForeground: StateFlow<Boolean> = _appForeground.asStateFlow()

    private var started = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intents.actionAppForeground(context.packageName) ->
                        _appForeground.value = intent.getBooleanExtra(Intents.EXTRA_APP_FOREGROUND, false)
                    else -> updateState()
                }
            }
        }

    fun start() {
        if (started) return
        started = true
        register()
        updateState()
    }

    fun stop() {
        if (!started) return
        started = false
        unregister()
    }

    private fun updateState() {
        _screenOn.value = isScreenOn()
        _deviceIdle.value = isDeviceIdle()
    }

    private fun isScreenOn(): Boolean = powerManager?.isInteractive != false

    private fun isDeviceIdle(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isDeviceIdleMode == true
        } else {
            false
        }

    private fun register() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                }
                addAction(Intents.actionAppForeground(context.packageName))
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        }
    }

    private fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
