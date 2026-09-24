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

import com.github.lmfirefly.flycat.core.model.profile.Imported
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroup
import com.github.lmfirefly.flycat.core.model.traffic.TrafficData
import com.github.lmfirefly.flycat.core.util.format.formatBytes
import com.github.lmfirefly.flycat.core.util.format.formatSpeed
import com.github.lmfirefly.flycat.locale.FlyTxt
import java.time.Instant
import java.time.ZoneId

/** 持续通知所渲染的内容。 */
internal sealed class NotificationPresentation {
    abstract val title: String
    abstract val content: String
    abstract val expandedText: String
    abstract val subText: String?
    //** 流量数据；同时为超级岛载荷提供数据。 */
    data class Running(
        override val title: String,
        override val content: String,
        override val expandedText: String,
        override val subText: String,
        val usageLine: String,
        val compactTraffic: String,
        val currentNode: String?,
    ) : NotificationPresentation()
    /** 无流量数据（流量通知已禁用）。 */
    data class Status(
        override val title: String,
        override val content: String,
        override val expandedText: String,
        override val subText: String? = null,
    ) : NotificationPresentation()
}

internal object NotificationPresentationFactory {
    fun createRunning(
        profileName: String,
        profile: Imported?,
        currentNode: String?,
        trafficNow: Long,
        trafficTotal: Long,
    ): NotificationPresentation.Running {
        val speedLine = buildSpeedLine(trafficNow)
        val totalLine = buildTotalLine(trafficTotal)
        return NotificationPresentation.Running(
            title = profileName,
            content = speedLine,
            expandedText = "$speedLine\n$totalLine",
            subText = totalLine,
            usageLine = buildUsageLine(profile),
            compactTraffic = buildCompactTrafficLine(trafficNow),
            currentNode = currentNode,
        )
    }

    fun createStatus(profileName: String, status: String): NotificationPresentation.Status =
        NotificationPresentation.Status(
            title = profileName,
            content = status,
            expandedText = status,
        )

    private fun buildSpeedLine(trafficNow: Long): String {
        val (upNow, downNow) = TrafficData.from(trafficNow)
        return FlyTxt.Service.Notification.SpeedLine.format(formatSpeed(downNow), formatSpeed(upNow))
    }

    private fun buildTotalLine(trafficTotal: Long): String {
        val data = TrafficData.from(trafficTotal)
        return FlyTxt.Service.Notification.TotalTraffic.format(formatBytes(data.upload + data.download))
    }

    /**
     * 仅包含数值，不含标签，以便超级岛可以渲染为：`1.2 GB / 100 GB | 2026-08-31`。配置文件不携带订阅信息时返回空串，避免与标题重复显示文件名。
     */
    private fun buildUsageLine(profile: Imported?): String {
        val used = profile?.let { (it.upload + it.download).coerceAtLeast(0L) } ?: 0L
        val total = profile?.total ?: 0L
        val usage = when {
            total > 0L -> "${formatBytes(used)} / ${formatBytes(total)}"
            used > 0L -> formatBytes(used)
            else -> ""
        }
        val expire = profile?.expire?.takeIf { it > 0L }?.let { expireDate(it) }
        return listOfNotNull(usage.takeIf { it.isNotEmpty() }, expire).joinToString(" | ")
    }

    private fun expireDate(expireAt: Long): String = Instant.ofEpochMilli(expireAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun buildCompactTrafficLine(trafficNow: Long): String {
        val (upNow, downNow) = TrafficData.from(trafficNow)
        return formatSpeed((upNow + downNow).coerceAtLeast(0L))
    }

    /** 按名称批量解析代理组：扫描一次批量取回，链路逐跳查询共用同一回调。 */
    fun resolveNodeName(
        queryGroups: (List<String>) -> List<ProxyGroup>,
        queryGroupNames: () -> List<String>,
    ): String? {
        fun findGroup(name: String): ProxyGroup? = queryGroups(listOf(name)).firstOrNull()
        val startGroup =
            findGroup("Proxy")?.takeIf(::isSelectableGroup)
                ?: queryGroups(queryGroupNames()).firstOrNull(::isSelectableGroup)
                ?: return null
        val seed = startGroup.now.ifBlank { startGroup.proxies.firstOrNull()?.name.orEmpty() }
        return resolveSelection(seed, ::findGroup, mutableSetOf())
    }

    private fun resolveSelection(
        selection: String,
        findGroup: (String) -> ProxyGroup?,
        visited: MutableSet<String>,
    ): String? {
        val normalized = selection.trim()
        if (normalized.isEmpty()) return null
        if (!visited.add(normalized)) return normalized
        val group = findGroup(normalized)
        if (group != null && isSelectableGroup(group)) {
            val next = group.now.ifBlank { group.proxies.firstOrNull()?.name.orEmpty() }
            return resolveSelection(next, findGroup, visited) ?: normalized
        }
        return normalized
    }

    private fun isSelectableGroup(group: ProxyGroup): Boolean {
        return group.type in Proxy.Type.groupTypes && (group.now.isNotBlank() || group.proxies.isNotEmpty())
    }
}
