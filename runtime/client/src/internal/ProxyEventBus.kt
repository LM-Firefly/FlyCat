package com.github.yumelira.yumebox.runtime.client.internal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.github.yumelira.yumebox.core.util.AppScreenState
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

internal sealed interface ProxyServiceEvent {
    data object ClashStarted : ProxyServiceEvent
    data class ClashStopped(val reason: String?) : ProxyServiceEvent
    data object ProfileChanged : ProxyServiceEvent
    data object ProfileLoaded : ProxyServiceEvent
    data object OverrideChanged : ProxyServiceEvent
    data object ServiceRecreated : ProxyServiceEvent
    data class RootRuntimeFailed(val error: String?) : ProxyServiceEvent
}

internal class ProxyEventBus(private val appContext: Context) {
    private val _events = MutableSharedFlow<ProxyServiceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ProxyServiceEvent> = _events.asSharedFlow()
    val screenOn: StateFlow<Boolean> get() = AppScreenState.screenOn
    val actionClashRequestStop: String get() = Intents.actionClashRequestStop(appContext.packageName)
    private val actionServiceRecreated: String get() = Intents.actionServiceRecreated(appContext.packageName)
    private val actionClashStarted: String get() = Intents.actionClashStarted(appContext.packageName)
    private val actionClashStopped: String get() = Intents.actionClashStopped(appContext.packageName)
    private val actionProfileChanged: String get() = Intents.actionProfileChanged(appContext.packageName)
    private val actionProfileLoaded: String get() = Intents.actionProfileLoaded(appContext.packageName)
    private val actionOverrideChanged: String get() = Intents.actionOverrideChanged(appContext.packageName)
    private val actionRootRuntimeFailed: String get() = Intents.actionRootRuntimeFailed(appContext.packageName)
    private val serviceEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = when (intent?.action ?: return) {
                actionClashStarted -> ProxyServiceEvent.ClashStarted
                actionClashStopped -> ProxyServiceEvent.ClashStopped(
                    intent.getStringExtra(Intents.EXTRA_STOP_REASON),
                )
                actionProfileLoaded -> ProxyServiceEvent.ProfileLoaded
                actionProfileChanged -> ProxyServiceEvent.ProfileChanged
                actionOverrideChanged -> ProxyServiceEvent.OverrideChanged
                actionServiceRecreated -> ProxyServiceEvent.ServiceRecreated
                actionRootRuntimeFailed -> ProxyServiceEvent.RootRuntimeFailed(
                    intent.getStringExtra("error"),
                )
                else -> return
            }
            _events.tryEmit(event)
        }
    }
    private val serviceEventsRegistered = AtomicBoolean(false)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register() {
        registerServiceEventReceiver()
        AppScreenState.init(appContext)
    }
    fun unregister() {
        if (serviceEventsRegistered.compareAndSet(true, false)) {
            runCatching { appContext.unregisterReceiver(serviceEventsReceiver) }
                .onFailure { Timber.w(it, "Failed to unregister service event receiver") }
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerServiceEventReceiver() {
        if (!serviceEventsRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(actionClashStarted)
            addAction(actionClashStopped)
            addAction(actionProfileChanged)
            addAction(actionProfileLoaded)
            addAction(actionOverrideChanged)
            addAction(actionServiceRecreated)
            addAction(actionRootRuntimeFailed)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(serviceEventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(serviceEventsReceiver, filter)
            }
        }.onFailure { error ->
            serviceEventsRegistered.set(false)
            Timber.w(error, "Failed to register service event receiver")
        }
    }
}
