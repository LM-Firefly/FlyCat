package com.github.yumelira.yumebox.core.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide screen on/off state.
 *
 * Holds a single registered [BroadcastReceiver] per process so that multiple consumers
 * (proxy event bus, runtime gateways, traffic pollers, …) can observe screen state
 * without each registering their own duplicate receiver.
 *
 * Must be initialized once via [init] during application startup.
 */
object AppScreenState {
    private val _screenOn = MutableStateFlow(true)
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val initialized = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> _screenOn.value = true
                Intent.ACTION_SCREEN_OFF -> _screenOn.value = false
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        // Seed with current value before registering to avoid an initial "true" while screen is off.
        runCatching {
            val pm = appContext.getSystemService(PowerManager::class.java)
            _screenOn.value = pm?.isInteractive != false
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        }.onFailure {
            // If registration failed, allow a future init() to retry.
            initialized.set(false)
        }
    }
}
