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

package com.github.yumelira.yumebox.data.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class OverrideService(
    context: Context,
    private val resolver: OverrideResolver,
    private val isRuntimeRunning: () -> Boolean = { false },
) {
    private companion object {
        const val APPLY_TIMEOUT_MILLIS = 15_000L
    }

    private val appContext = context.appContextOrSelf

    @Suppress("TooGenericExceptionCaught")
    suspend fun applyOverride(profileId: String): Boolean {
        return try {
            val overrideIds = resolver.resolveIds(profileId)
            val resolvedSpecs = resolver.resolveSpecs(overrideIds)
            val missingOverrideCount = overrideIds.size - resolvedSpecs.size

            Timber.i(
                "Apply override chain: profile=%s ids=%s specs=%s resolved=%d missing=%d",
                profileId,
                overrideIds.joinToString(","),
                resolvedSpecs.joinToString(",") { spec -> "${spec.ext}:${spec.path}" },
                resolvedSpecs.size,
                missingOverrideCount,
            )

            if (missingOverrideCount > 0) {
                Timber.w(
                    "Override chain contains missing configs: profile=%s ids=%s",
                    profileId,
                    overrideIds.joinToString(","),
                )
                return false
            }

            if (isRuntimeRunning()) {
                notifyRuntimeOverrideChangedAndAwait()
            } else {
                notifyRuntimeOverrideChanged()
                true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Failed to apply override for profile: %s", profileId)
            false
        }
    }

    private fun notifyRuntimeOverrideChanged(requestId: String? = null) {
        appContext.sendBroadcast(
            Intent(Intents.actionOverrideChanged(appContext.packageName))
                .putExtra(Intents.EXTRA_OVERRIDE_REQUEST_ID, requestId)
                .setPackage(appContext.packageName)
        )
    }

    private suspend fun notifyRuntimeOverrideChangedAndAwait(): Boolean {
        val requestId = UUID.randomUUID().toString()
        val result = CompletableDeferred<Boolean>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (
                        intent?.getStringExtra(Intents.EXTRA_OVERRIDE_REQUEST_ID) == requestId
                    ) {
                        result.complete(
                            intent.getBooleanExtra(Intents.EXTRA_OVERRIDE_APPLY_SUCCESS, false)
                        )
                    }
                }
            }
        val filter = IntentFilter(Intents.actionOverrideApplied(appContext.packageName))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") appContext.registerReceiver(receiver, filter)
        }
        return try {
            notifyRuntimeOverrideChanged(requestId)
            withTimeout(APPLY_TIMEOUT_MILLIS) { result.await() }
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
}
