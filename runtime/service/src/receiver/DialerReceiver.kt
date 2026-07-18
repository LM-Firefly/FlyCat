/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.runtime.service.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.runtime.api.constants.Components
import com.github.lmfirefly.flycat.runtime.service.R
import timber.log.Timber

class DialerReceiver : BroadcastReceiver() {
    companion object {
        private const val NOTIFICATION_ID = 1102
        private const val CHANNEL_ID = "secret_code"
        private const val SECRET_CODE = "*#*#0721#*#*"
        private const val ACTION_NEW_OUTGOING_CALL = "android.intent.action.NEW_OUTGOING_CALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.provider.Telephony.SECRET_CODE" -> {
                // Background activity starts are blocked since Android 10, so the secret code
                // surfaces a notification whose tap opens the app instead of launching directly.
                postOpenNotification(context)
            }

            ACTION_NEW_OUTGOING_CALL -> {
                val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                if (SECRET_CODE == phoneNumber) {
                    resultData = null
                    postOpenNotification(context)
                }
            }
        }
    }

    private fun postOpenNotification(context: Context) {
        runCatching {
                val manager = NotificationManagerCompat.from(context)
                if (!manager.areNotificationsEnabled()) return

                manager.createNotificationChannel(
                    NotificationChannelCompat.Builder(
                            CHANNEL_ID,
                            NotificationManagerCompat.IMPORTANCE_HIGH,
                        )
                        .setName(FlyTxt.Service.Tile.ClickToOpen)
                        .build()
                )

                val launchIntent =
                    Intent(Intent.ACTION_MAIN).apply {
                        component = Components.MAIN_ACTIVITY
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                val pendingIntent =
                    PendingIntent.getActivity(
                        context,
                        0,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val notification =
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_logo_service)
                        .setContentTitle(FlyTxt.Service.Tile.ClickToOpen)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()
                manager.notify(NOTIFICATION_ID, notification)
            }
            .onFailure { error -> Timber.e(error, "Secret code notification failed") }
    }
}
