package com.github.lmfirefly.flycat.runtime.api.contract

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var appContext: Context? = null
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
        val ctx = context.applicationContext ?: context
        appContext = ctx
        runCatching {
            val pm = ctx.getSystemService(PowerManager::class.java)
            _screenOn.value = pm?.isInteractive != false
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) } else { ctx.registerReceiver(receiver, filter) }
        }.onFailure {
            appContext = null
            initialized.set(false)
        }
    }

    fun destroy() {
        if (!initialized.compareAndSet(true, false)) return
        appContext?.let { appContext ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        appContext = null
        _screenOn.value = true
    }
}
