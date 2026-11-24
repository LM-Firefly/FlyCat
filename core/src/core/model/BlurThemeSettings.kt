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

import kotlinx.serialization.Serializable

/** 全局主题样式；模糊参数默认值取自 miuix 示例，卡片圆角默认 18dp。 */
@Serializable
data class BlurThemeSettings(
    val texture: TextureBlurStyle = TextureBlurStyle(),
    val foreground: ForegroundBlurStyle = ForegroundBlurStyle(),
    val progressive: ProgressiveBlurStyle = ProgressiveBlurStyle(),
) {
    /** 纹理组样式：混色、描边、全局圆角、模糊与色彩参数。 */
    @Serializable
    data class TextureBlurStyle(
        val blendMode: Int = 9,
        val edgeStroke: Int = 3,
        val cardCornerRadius: Float = 18f,
        val blurRadius: Float = 40f,
        val noise: Float = 0.0045f,
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
    )
    /** 前景模糊样式：前景内容模糊参数（背景着色器变体由 AppBackgroundStyle 选择）。 */
    @Serializable
    data class ForegroundBlurStyle(
        val blendMode: Int = 1,
        val dynamicBackground: Boolean = true,
        val blurRadius: Float = 100f,
        val noise: Float = 0.0045f,
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
    )
    /** 渐进模糊样式：方向 + 混色 + 梯度起讫/曲率。 */
    @Serializable
    data class ProgressiveBlurStyle(
        val direction: Int = 0,
        val blendMode: Int = 0,
        val blurRadius: Float = 20f,
        val noise: Float = 0f,
        val start: Float = 0f,
        val end: Float = 1f,
        val curve: Float = 1f,
    )
    companion object {
        const val MIN_CARD_CORNER_RADIUS = 8f
        const val MAX_CARD_CORNER_RADIUS = 36f
        /** 容器描边序号：0=关闭 1=大 2=中 3=小（对应示例 EdgeStrokeConfig.Container）。 */
        const val EDGE_DISABLED = 0
        const val EDGE_LARGE = 1
        const val EDGE_MEDIUM = 2
        const val EDGE_SMALL = 3
        /** 渐进模糊方向序号：0=Top 1=Bottom 2=Left 3=Right。 */
        const val DIR_TOP = 0
        const val DIR_BOTTOM = 1
        const val DIR_LEFT = 2
        const val DIR_RIGHT = 3
    }
}
