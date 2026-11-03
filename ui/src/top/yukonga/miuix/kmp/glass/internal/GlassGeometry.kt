// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass.internal

import kotlin.math.sqrt

/**
 * 角部图块尺寸相对角半径的倍数。轮廓线在距盒角这么远处离开直边，
 * 这正是角部连续过渡（而非纯圆弧）的原因。
 */
internal const val TILE_SCALE = 1.5286649465560913f

internal const val FIT_4 = -0.7391197269f
internal const val FIT_3 = 2.4034927648f
internal const val FIT_2 = -2.4907319173f
internal const val FIT_1 = 0.4768708960f
internal const val FIT_0 = 0.4747847594f

/** 低于该半径时角部退化为普通圆角矩形；否则图块运算会除以零。 */
internal const val MIN_SUPERCIRCLE_RADIUS = 0.5f

/**
 * 构建边缘轮廓所用的五次平滑核。它把 `[0, 1]` 映射到 `[0, 1]`，
 * 两端一阶、二阶导数均为零，因此边缘不会出现折痕。
 */
internal fun smooth5Map(t: Float): Float {
    val x = (0.5f + 0.5f * t).coerceIn(0f, 1f)
    val s = x * x * x * (x * (x * 6f - 15f) + 10f)
    return (s - 0.5f) * 2f
}

/**
 * 边缘深度阈值：轮廓内侧 [band] 像素处经五次平滑映射的深度值，
 * 从而使抗锯齿带无论边缘宽度如何都保持固定的屏幕像素宽度。
 *
 * @param band 抗锯齿带宽度（全分辨率像素）。
 * @param edgeFullPx 边缘宽度（全分辨率像素）。
 */
internal fun coverageThreshold(band: Float, edgeFullPx: Float): Float {
    val ratio = (band / if (edgeFullPx > 1f) edgeFullPx else 1f).coerceIn(0f, 1f)
    return smooth5Map(sqrt(ratio))
}
