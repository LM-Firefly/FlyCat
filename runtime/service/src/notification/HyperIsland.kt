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

import android.app.Notification
import android.app.Service
import android.graphics.drawable.Icon
import android.os.Bundle

/** 支持小米澎湃OS超级岛。澎湃OS从持续通知的额外数据中获取超级岛信息，而非通过通知API获取。 */
object HyperIsland {
    /** HyperOS 通过该系统属性为使用超级岛渲染器的设备添加标识。 */
    private const val FEATURE_PROPERTY = "persist.sys.feature.island"
    private const val EXTRA_PICS = "miui.focus.pics"
    private const val EXTRA_PARAM = "miui.focus.param"
    private const val PIC_APP_ICON = "miui.focus.pic_app_icon"
    private const val PIC_APP_ICON_DARK = "miui.focus.pic_app_icon_dark"
    private const val PIC_SMALL = "miui.focus.pic_small"
    private const val PIC_SMALL_DARK = "miui.focus.pic_small_dark"
    /** 只需读取一次：该属性在进程的整个生命周期内是固定的。 */
    private val supported: Boolean by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, FEATURE_PROPERTY, false) as Boolean
        }.getOrDefault(false)
    }
    fun isSupported(): Boolean = supported
    /**
     * @param appIconRes 展开卡片（亮/暗）的应用图标槽位：系统只取图标 alpha 单色着色绘制，实心轮廓会把爪印内部细节抹平，故传爪印镂空的负形图标，着色后爪印以圆底色透出。
     * @param smallIconRes 小岛（状态栏胶囊）槽位：按原图全彩绘制，保留双色矢量图标。
     */
    internal fun applyExtras(
        service: Service,
        notification: Notification,
        presentation: NotificationPresentation.Running,
        appIconRes: Int,
        smallIconRes: Int,
        promote: Boolean,
    ) {
        val appIcon = Icon.createWithResource(service, appIconRes)
        val smallIcon = Icon.createWithResource(service, smallIconRes)
        notification.extras.putBundle(
            EXTRA_PICS,
            Bundle().apply {
                putParcelable(PIC_APP_ICON, appIcon)
                putParcelable(PIC_APP_ICON_DARK, appIcon)
                putParcelable(PIC_SMALL, smallIcon)
                putParcelable(PIC_SMALL_DARK, smallIcon)
            },
        )
        notification.extras.putString(
            EXTRA_PARAM,
            HyperIslandParams.build(
                profileName = presentation.title,
                usageText = islandSubtitle(presentation),
                compactText = presentation.compactTraffic,
                currentNode = presentation.currentNode,
                promote = promote,
            ),
        )
    }

    /** 超级岛标题下的第二行：订阅用量（若有）+ 总流量。实时速率已由 hint 区展示，此处不再重复上下行。 */
    private fun islandSubtitle(presentation: NotificationPresentation.Running): String = listOf(presentation.usageLine, presentation.subText)
        .filter { !it.isNullOrBlank() }.joinToString(" · ")
}
