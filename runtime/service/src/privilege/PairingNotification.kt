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

package com.github.lmfirefly.flycat.runtime.service.privilege

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.runtime.service.R
import com.github.lmfirefly.flycat.runtime.service.receiver.PairingCodeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 无线调试配对的常驻通知（对照 GKD/priv-ui）：发现状态实时更新，通知栏内嵌配对码输入（RemoteInput），用户无需离开系统配对窗口即可完成配对，消除「配对窗口必须保持打开」与「切到应用输码」之间的竞态。 */
object PairingNotification {
    private const val TAG = "FlyCatPairing"
    private const val CHANNEL_ID = "privilege_pairing"
    private const val NOTIFICATION_ID = 0x71B1
    const val ACTION_PAIR_CODE: String = "com.github.lmfirefly.flycat.privilege.PAIR_CODE"
    const val ACTION_STOP: String = "com.github.lmfirefly.flycat.privilege.PAIR_STOP"
    const val EXTRA_PORT: String = "pairing_port"
    const val KEY_PAIRING_CODE: String = "pairing_code"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 开启配对会话：显示通知并开始预发现配对端口，发现后切换为「已找到配对服务」。 */
    fun start(context: Context) {
        ensureChannel(context)
        show(context, port = null)
        scope.launch {
            val port = runCatching {
                PrivilegeManager.awaitPairingPort()
            }.getOrNull()
            Log.i(TAG, "Pairing session discovery result: port=$port")
            if (port != null) {
                show(context, port)
            }
        }
    }
    /** 结束配对会话并撤下通知。 */
    fun stop(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
    /** 通知入口的配对会话执行（进程级协程；广播接收器只投递不等待，规避 ANR 窗口截断）。 */
    fun launchPairing(context: Context, pairingCode: String, pairingPort: Int?) {
        val app = context.applicationContext
        scope.launch {
            val paired = runCatching { PrivilegeManager.pairOnly(pairingCode, pairingPort) }.getOrDefault(false)
            if (!paired) {
                showResult(app, success = false, text = FlyTxt.AppSettings.ServiceSection.PrivilegePairFailed)
                return@launch
            }
            // 配对已完成：撤下输入界面，反馈启动进度；运行状态流会同步收尾界面弹窗。
            showStarting(app)
            val started = runCatching { PrivilegeManager.startOnly() }.getOrDefault(false)
            if (started) {
                stop(app)
            } else {
                showResult(app, success = false, text = FlyTxt.AppSettings.ServiceSection.PrivilegeStartFailed)
            }
        }
    }
    /** 配对终态反馈：成功撤下通知，失败保留通知并显示原因。 */
    fun showResult(context: Context, success: Boolean, text: String? = null) {
        if (success) {
            stop(context)
            return
        }
        ensureChannel(context)
        show(
            context,
            port = null,
            text = text ?: FlyTxt.AppSettings.ServiceSection.PrivilegePairFailed,
            ongoing = false,
        )
    }
    /** 配对已完成、启动进行中的过渡状态。 */
    private fun showStarting(context: Context) {
        ensureChannel(context)
        show(
            context,
            port = null,
            text = FlyTxt.AppSettings.ServiceSection.PrivilegeLogStarting,
            ongoing = true,
            withInput = false,
        )
    }
    private fun show(context: Context, port: Int?, text: String? = null, ongoing: Boolean = true, withInput: Boolean = true) {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_service)
                .setContentTitle(FlyTxt.AppSettings.ServiceSection.PrivilegeLogPairing)
                .setContentText(
                    text ?: if (port != null) {
                        FlyTxt.AppSettings.ServiceSection.PrivilegePairFoundPrompt
                    } else {
                        FlyTxt.AppSettings.ServiceSection.PrivilegePairSearching
                    },
                )
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            if (withInput) {
                builder.addAction(pairCodeAction(context, port))
            }
            manager.notify(NOTIFICATION_ID, builder.addAction(stopAction(context)).build())
        }.onFailure { error -> Log.w(TAG, "Unable to post pairing notification", error) }
    }
    /** 通知内嵌配对码输入；PendingIntent 必须可变，RemoteInput 才能回填结果。 */
    private fun pairCodeAction(context: Context, port: Int?): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE).setLabel(FlyTxt.AppSettings.ServiceSection.PrivilegePairCodeHint).build()
        val intent = Intent(context, PairingCodeReceiver::class.java).setAction(ACTION_PAIR_CODE).putExtra(EXTRA_PORT, port ?: -1)
        val pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return NotificationCompat.Action.Builder(
            0,
            FlyTxt.AppSettings.ServiceSection.PrivilegePairInputAction,
            pendingIntent,
        ).addRemoteInput(remoteInput).build()
    }
    private fun stopAction(context: Context): NotificationCompat.Action {
        val intent = Intent(context, PairingCodeReceiver::class.java).setAction(ACTION_STOP)
        val pendingIntent = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(
            0,
            FlyTxt.AppSettings.ServiceSection.PrivilegeStop,
            pendingIntent,
        ).build()
    }
    private fun ensureChannel(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    FlyTxt.AppSettings.ServiceSection.PrivilegePairDialogTitle,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
