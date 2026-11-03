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

package com.github.lmfirefly.flycat.core.model

/** 通知两行内容的可选来源。设置界面与服务端渲染共用同一解析器 [resolve]，保证预览与真实通知一致。 */
enum class NotificationLineContent {
    ProfileName,
    Status,
    CurrentNode,
    LiveTraffic,
    UsedTraffic,
    SessionTraffic,
    TotalTraffic,
    RemainingTraffic,
    SubscriptionUsage,
    ExpiryDate,
    CustomText,
    Hidden,
}

/** 已格式化好的通知取值；由服务端通知工厂组装。 */
data class NotificationLineValues(
    val profileName: String,
    val currentNode: String,
    val liveTraffic: String,
    val usedTraffic: String,
    val sessionTraffic: String,
    val totalTraffic: String,
    val remainingTraffic: String,
    val subscriptionUsage: String,
    val expiryDate: String,
    val status: String,
)

/** 通知内容的展示配置（两行来源 + 各自的自定义文本）。 */
data class NotificationDisplaySettings(
    val firstLine: NotificationLineContent = NotificationLineContent.ProfileName,
    val secondLine: NotificationLineContent = NotificationLineContent.LiveTraffic,
    val firstLineText: String = "",
    val secondLineText: String = "",
)

/** 把一行内容解析为最终文本；自定义文本只在选中 [NotificationLineContent.CustomText] 时生效。 */
fun NotificationLineContent.resolve(values: NotificationLineValues, customText: String = ""): String = when (this) {
    NotificationLineContent.ProfileName -> values.profileName
    NotificationLineContent.Status -> values.status
    NotificationLineContent.CurrentNode -> values.currentNode
    NotificationLineContent.LiveTraffic -> values.liveTraffic
    NotificationLineContent.UsedTraffic -> values.usedTraffic
    NotificationLineContent.SessionTraffic -> values.sessionTraffic
    NotificationLineContent.TotalTraffic -> values.totalTraffic
    NotificationLineContent.RemainingTraffic -> values.remainingTraffic
    NotificationLineContent.SubscriptionUsage -> values.subscriptionUsage
    NotificationLineContent.ExpiryDate -> values.expiryDate
    NotificationLineContent.CustomText -> customText
    NotificationLineContent.Hidden -> ""
}
