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

package com.github.yumelira.yumebox.runtime.service.notification

import com.github.yumelira.yumebox.common.util.formatBytes
import com.github.yumelira.yumebox.common.util.formatSpeed
import tf.gal.yumebox.locale.YumeTxt

internal data class NotificationPresentation(
    val title: String,
    val content: String,
    val expandedText: String,
    val subText: String? = null,
)

internal object NotificationPresentationFactory {
    fun createRunning(
        profileName: String,
        trafficNow: Long,
        trafficTotal: Long,
    ): NotificationPresentation {
        val speedLine = buildSpeedLine(trafficNow)
        val totalLine = buildTotalLine(trafficTotal)
        return NotificationPresentation(
            title = profileName,
            content = speedLine,
            expandedText = "$speedLine\n$totalLine",
            subText = totalLine,
        )
    }

    fun createStatus(profileName: String, status: String): NotificationPresentation =
        NotificationPresentation(
            title = profileName,
            content = status,
            expandedText = status,
            subText = null,
        )

    private fun buildSpeedLine(trafficNow: Long): String {
        val upNow = decodeTrafficHalf(trafficNow ushr 32)
        val downNow = decodeTrafficHalf(trafficNow and 0xFFFFFFFFL)
        return YumeTxt.Service.Notification.SpeedLine.format(
            formatSpeed(downNow),
            formatSpeed(upNow),
        )
    }

    private fun buildTotalLine(trafficTotal: Long): String {
        val upTotal = decodeTrafficHalf(trafficTotal ushr 32)
        val downTotal = decodeTrafficHalf(trafficTotal and 0xFFFFFFFFL)
        return YumeTxt.Service.Notification.TotalTraffic.format(formatBytes(upTotal + downTotal))
    }

    private fun decodeTrafficHalf(encoded: Long): Long {
        val type = (encoded ushr 30) and 0x3L
        val payload = encoded and 0x3FFFFFFFL
        return when (type.toInt()) {
            0 -> payload
            1 -> (payload * 1024L) / 100L
            2 -> (payload * 1024L * 1024L) / 100L
            3 -> (payload * 1024L * 1024L * 1024L) / 100L
            else -> 0L
        }
    }
}
