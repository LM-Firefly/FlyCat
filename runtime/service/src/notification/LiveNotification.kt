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

package com.github.lmfirefly.flycat.runtime.service.notification

import android.os.Build
import androidx.core.app.NotificationCompat
import com.tencent.mmkv.MMKV

/**
 * Android 16 实时通知（promoted ongoing / Live Update）：把常驻服务通知提升为置顶实时通知，并以实时速率作为状态栏 chip 文案。
 * 与 HyperOS 超级岛互斥展示——岛接管状态栏时不请求提升，避免胶囊与 chip 重复占位；岛不展示的路径（如 root 模式）自动回退到实时通知。
 */
internal object LiveNotification {
    /** 用户开关（默认开启）；前端设置与前台服务经同一个多进程 "settings" store 读取该键。 */
    private const val KEY_ENABLED = "liveNotificationEnabled"
    /** Android 16（API 36，BAKLAVA）起支持提升；更低版本仅静默忽略提升请求。 */
    fun isEnabled(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && MMKV.mmkvWithID("settings", MMKV.MULTI_PROCESS_MODE).decodeBool(KEY_ENABLED, true)
    /** 仅内容持续刷新的流量帧适合提升为实时通知，状态帧保持普通常驻通知。 */
    fun shouldPromote(presentation: NotificationPresentation): Boolean = presentation is NotificationPresentation.Running && isEnabled()
    /** 请求系统提升为实时通知，并把实时速率写入状态栏 chip（无速率文本时仅显示图标）。 */
    fun promote(builder: NotificationCompat.Builder, presentation: NotificationPresentation) {
        builder.setRequestPromotedOngoing(true)
        (presentation as? NotificationPresentation.Running)?.compactTraffic?.takeIf { it.isNotBlank() }?.let { builder.setShortCriticalText(it) }
    }
}
