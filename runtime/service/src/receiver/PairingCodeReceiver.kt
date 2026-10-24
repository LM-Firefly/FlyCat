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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.github.lmfirefly.flycat.runtime.service.privilege.PairingNotification

/** 处理配对通知的内嵌输入与停止动作（对照 GKD 的通知栏配对）。 */
class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PairingNotification.ACTION_STOP -> PairingNotification.stop(context)
            PairingNotification.ACTION_PAIR_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(PairingNotification.KEY_PAIRING_CODE)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (code.isEmpty()) return
                val port = intent.getIntExtra(PairingNotification.EXTRA_PORT, -1).takeIf { it > 0 }
                // 配对+启动远超广播 ANR 窗口：交进程级协程执行，接收器立即返回。
                PairingNotification.launchPairing(context, code, port)
            }
        }
    }
}
