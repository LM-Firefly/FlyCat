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

package com.github.lmfirefly.flycat.feature.about.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/**
 * FlyCat 品牌轮廓（`icons/FlyCat_Light.svg`，Affinity 导出）：圆角猫脸方框 + evenodd 镂空猫爪（四趾 + 掌垫）。
 * 路径为 SVG 原始坐标空间（视口 [LogoViewportWidth]×[LogoViewportHeight]），
 * 组变换 `matrix(1,0,0,1,tx,ty)` 烘焙为整体平移 [LogoGroupTranslateX]/[LogoGroupTranslateY]。
 */
private const val LogoViewportWidth = 383f
private const val LogoViewportHeight = 481f
private const val LogoGroupTranslateX = -58.545f
private const val LogoGroupTranslateY = -9.527945f

/** 轮廓宽高比（383:481），供调用方约束尺寸时保持形状不被拉伸。 */
const val FlyCatLogoAspectRatio: Float = LogoViewportWidth / LogoViewportHeight

private val FlyCatLogoPathData = "M69.992,490.28C58.545,490.28 58.545,490.28 58.545,477.988C58.546,378.569 58.545,283.561 58.545,181.463C58.545,141.666 89.224,107.08 125.994,89.029C118.555,38.99 188.43,-20.637 250,27.372C321.117,-23.333 379.038,45.744 373.999,89.044C411,106.735 441.455,139.442 441.455,180.324L441.455,477.945C441.455,490.28 441.455,490.28 430.118,490.28C310.076,490.283 172.295,490.282 69.992,490.28ZM241.753,85.736C241.753,63.644 223.845,45.736 201.754,45.736C179.662,45.736 161.754,63.644 161.754,85.736C161.754,107.827 179.662,125.735 201.754,125.735C223.845,125.735 241.753,107.827 241.753,85.736ZM337.752,85.736C337.752,63.644 319.844,45.736 297.753,45.736C275.662,45.736 257.753,63.644 257.753,85.736C257.753,107.827 275.662,125.735 297.753,125.735C319.844,125.735 337.752,107.827 337.752,85.736ZM168.753,165.751C168.753,143.668 150.845,125.767 128.754,125.767C106.662,125.767 88.754,143.668 88.754,165.751C88.754,187.833 106.662,205.734 128.754,205.734C150.845,205.734 168.753,187.833 168.753,165.751ZM297.753,205.734C297.753,179.224 276.263,157.735 249.753,157.735C223.243,157.735 201.754,179.224 201.754,205.734C201.754,215.352 204.582,224.308 209.452,231.818L176.753,231.818C150.243,231.818 128.754,253.307 128.754,279.817L128.754,283.818C128.754,310.328 150.243,331.818 176.753,331.818L322.754,331.818C349.264,331.818 370.754,310.328 370.754,283.818L370.754,279.817C370.754,253.307 349.264,231.818 322.754,231.818L290.055,231.818C294.925,224.308 297.753,215.352 297.753,205.734ZM410.754,165.767C410.754,143.675 392.845,125.767 370.754,125.767C348.663,125.767 330.754,143.675 330.754,165.767C330.754,187.858 348.663,205.766 370.754,205.766C392.845,205.766 410.754,187.858 410.754,165.767Z"

/**
 * 以 [tint]（通常是页面的 [com.github.lmfirefly.flycat.feature.about.presentation.screen.AppNameGradient] 渐变刷）
 * 着色的 FlyCat 轮廓图标。形状按视口等比缩放并居中于 [modifier] 约束内，用 `Modifier.aspectRatio(FlyCatLogoAspectRatio)` 约束可避免留白。
 */
@Composable
fun FlyCatLogoIcon(tint: Brush, modifier: Modifier = Modifier) {
    val logoPath = remember {
        PathParser().parsePathString(FlyCatLogoPathData).toPath(Path()).apply {
            fillType = PathFillType.EvenOdd
        }
    }
    Canvas(modifier = modifier) {
        val scale = minOf(size.width / LogoViewportWidth, size.height / LogoViewportHeight)
        val offsetX = (size.width - LogoViewportWidth * scale) / 2f
        val offsetY = (size.height - LogoViewportHeight * scale) / 2f
        withTransform({
            translate(offsetX + LogoGroupTranslateX * scale, offsetY + LogoGroupTranslateY * scale)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(path = logoPath, brush = tint)
        }
    }
}
