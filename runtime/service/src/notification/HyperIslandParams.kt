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

import com.github.lmfirefly.flycat.locale.FlyTxt
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** 构建 HyperOS 超级岛载荷，系统会从 `miui.focus.param` 通知额外参数中读取该载荷：基础信息（配置文件和订阅线路）、展开区域的提示区块（当前节点和精简流量文本）以及岛区域的定义。 */
object HyperIslandParams {
    private const val BUSINESS = "flycat_service"
    /** 岛存活时长。过短的值会让 HyperOS 回收条目，下一次投递又重新建岛。 */
    private const val ISLAND_TIMEOUT_SECONDS = 7 * 24 * 60 * 60
    /** 岛渲染器会丢弃序列号未增长的更新，因此从时钟初始化它，以避免重放前一进程的序列号。 */
    private val sequence = AtomicLong(System.currentTimeMillis() / 1000)
    /**
     * hint 区左列宽度取标签/内容较宽者，右列位置与宽度随之变化：左列过宽会把右列（实时流量）挤裁，过窄则右列左移且右缘留白。故把节点名补齐/截断为恒定宽度，使右列位置稳定并贴近卡片右缘。
     * 宽度按非 ASCII 记 2、ASCII 记 1 估算；补齐用 U+00A0（不被 String.trim() 裁剪）。卡片总宽约 44–46 半角，右列需约 14 半角，故上限约 32。
     */
    private const val NODE_DISPLAY_WIDTH = 52
    private fun formatNodeDisplay(name: String, targetWidth: Int = NODE_DISPLAY_WIDTH): String {
        val trimmed = name.trim()
        val builder = StringBuilder()
        var width = 0
        for (char in trimmed) {
            val charWidth = if (char.code < 0x2000) 1 else 2
            if (width + charWidth > targetWidth - 2) {
                builder.append('…')
                width += 2
                break
            }
            builder.append(char)
            width += charWidth
        }
        while (width < targetWidth) {
            builder.append('\u00a0')
            width += 1
        }
        return builder.toString()
    }
    /** @param promote 仅岛会话的首帧为 true。首帧可自动展开一次；更新帧必须保持折叠，重复首帧标志会重播出现动画。 */
    fun build(
        profileName: String,
        usageText: String,
        compactText: String,
        currentNode: String?,
        promote: Boolean,
    ): String {
        val now = System.currentTimeMillis()
        val node = currentNode?.let { formatNodeDisplay(it) } ?: FlyTxt.Service.Notification.NoNode
        val params =
            JSONObject().apply {
                put("business", BUSINESS)
                put("protocol", 1)
                put("orderId", BUSINESS)
                put("islandFirstFloat", promote)
                put("enableFloat", false)
                put("updatable", true)
                put("outEffectSrc", "")
                // "reopen" 会重新展示已取消的通知；存活岛的更新不能请求它，否则丢帧会以新岛形式回归。
                put("reopen", if (promote) "reopen" else "close")
                put("sequence", sequence.incrementAndGet())
                put("aodTitle", profileName)
                put(
                    "baseInfo",
                    JSONObject().apply {
                        put("type", 2)
                        put("title", profileName)
                        // 标题下方一行：订阅用量（若有）与总流量拼接，见 HyperIsland.islandSubtitle。
                        put("content", usageText)
                        put("subTitle", "")
                        put("extraTitle", "")
                        put("specialTitle", "")
                        put("subContent", "")
                        put("picFunction", "")
                        put("showDivider", true)
                        put("showContentDivider", false)
                        put("colorTitle", "#111111")
                        put("colorTitleDark", "#ffffff")
                        put("colorContent", "#333333")
                        put("colorContentDark", "#cccccc")
                    },
                )
                put(
                    "picInfo",
                    JSONObject().apply {
                        put("type", 1)
                        put("pic", "")
                    },
                )
                put(
                    "hintInfo",
                    JSONObject().apply {
                        put("type", 2)
                        put("content", FlyTxt.Service.Notification.CurrentNodeLabel)
                        put("title", node)
                        put(
                            "timerInfo",
                            JSONObject().apply {
                                put("timerType", 0)
                                put("timerWhen", 0L)
                                put("timerTotal", 0L)
                                put("timerSystemCurrent", now)
                            },
                        )
                        put("subContent", FlyTxt.Service.Notification.RealtimeTraffic)
                        put("subTitle", compactText)
                        put("colorContent", "#666666")
                        put("colorContentDark", "#aaaaaa")
                        put("colorTitle", "#222222")
                        put("colorTitleDark", "#eeeeee")
                        put("colorSubContent", "#666666")
                        put("colorSubContentDark", "#aaaaaa")
                        put("colorSubTitle", "#222222")
                        put("colorSubTitleDark", "#eeeeee")
                    },
                )
                put(
                    "param_island",
                    JSONObject().apply {
                        put("islandProperty", 1)
                        put("islandTimeout", ISLAND_TIMEOUT_SECONDS)
                        put(
                            "bigIslandArea",
                            JSONObject().apply {
                                put("templateNo", 2)
                                put(
                                    "imageTextInfoLeft",
                                    JSONObject().apply {
                                        put("type", 1)
                                        put(
                                            "textInfo",
                                            JSONObject().apply {
                                                put("title", profileName)
                                                put("content", "")
                                                put("showHighlightColor", false)
                                                put("narrowFont", false)
                                            },
                                        )
                                    },
                                )
                                put(
                                    "textInfo",
                                    JSONObject().apply {
                                        put("frontTitle", "")
                                        put("title", compactText)
                                        put("content", "")
                                        put("showHighlightColor", false)
                                        put("narrowFont", false)
                                    },
                                )
                            },
                        )
                        put(
                            "smallIslandArea",
                            JSONObject().apply {
                                put(
                                    "picInfo",
                                    JSONObject().apply {
                                        put("type", 1)
                                        put("pic", "miui.focus.pic_small")
                                        put("picDark", "miui.focus.pic_small_dark")
                                    },
                                )
                            },
                        )
                    },
                )
            }
        return JSONObject().put("param_v2", params).toString()
    }
}
