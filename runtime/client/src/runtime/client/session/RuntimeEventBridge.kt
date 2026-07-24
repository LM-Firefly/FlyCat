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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import timber.log.Timber

/**
 * Registers runtime service broadcasts and dispatches them to facade callbacks.
 * Keeps IntentFilter / BroadcastReceiver out of the main facade body.
 */
internal class RuntimeEventBridge(
    context: Context,
    private val isConfigReloading: () -> Boolean,
    private val onRuntimeStarted: () -> Unit,
    private val onRuntimeStopped: (reason: String?) -> Unit,
    private val onConfigChanged: () -> Unit,
    private val onReconcile: () -> Unit,
    private val onRootFailed: (error: String?) -> Unit,
) {
    private val appContext = context.appContextOrSelf
    private val packageName = appContext.packageName

    private val actionRuntimeStarted = Intents.actionRuntimeStarted(packageName)
    private val actionRuntimeStopped = Intents.actionRuntimeStopped(packageName)
    private val actionProfileChanged = Intents.actionProfileChanged(packageName)
    private val actionProfileLoaded = Intents.actionProfileLoaded(packageName)
    private val actionOverrideChanged = Intents.actionOverrideChanged(packageName)
    private val actionServiceRecreated = Intents.actionServiceRecreated(packageName)
    private val actionRootRuntimeFailed = Intents.actionRootRuntimeFailed(packageName)

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action ?: return) {
                    actionRuntimeStarted -> onRuntimeStarted()
                    actionRuntimeStopped -> {
                        if (isConfigReloading()) {
                            Timber.d("Ignoring stale runtime-stopped event during config reload")
                        } else {
                            onRuntimeStopped(intent.getStringExtra(Intents.EXTRA_STOP_REASON))
                        }
                    }
                    actionProfileChanged -> {
                        if (intent.getBooleanExtra(Intents.EXTRA_AFFECTS_RUNTIME, true)) {
                            onConfigChanged()
                        }
                    }
                    actionOverrideChanged -> onConfigChanged()
                    actionProfileLoaded,
                    actionServiceRecreated -> onReconcile()
                    actionRootRuntimeFailed -> {
                        val error = intent.getStringExtra("error")
                        Timber.w("Root runtime failed: $error")
                        onRootFailed(error)
                    }
                }
            }
        }

    fun register() {
        val filter =
            IntentFilter().apply {
                addAction(actionRuntimeStarted)
                addAction(actionRuntimeStopped)
                addAction(actionProfileChanged)
                addAction(actionProfileLoaded)
                addAction(actionOverrideChanged)
                addAction(actionServiceRecreated)
                addAction(actionRootRuntimeFailed)
            }
        runCatching {
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
            .onFailure { error -> Timber.w(error, "Failed to register service event receiver") }
    }
}
