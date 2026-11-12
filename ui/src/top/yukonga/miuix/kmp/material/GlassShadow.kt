// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * 玻璃表面投下的阴影。
 *
 * @property color 阴影的颜色。其 alpha 是完全不透明时的强度。
 * @property offsetX 水平偏移。
 * @property offsetY 垂直偏移。较小的正值表示光来自上方。
 * @property radius 阴影超出轮廓的扩散距离。
 * @property dispersion 衰减的形状。小于 0.5 时阴影紧贴边缘；大于 0.5 时变暗区域扩散得更远更淡。
 */
@Immutable
data class GlassShadow(
    val color: Color,
    val offsetX: Float,
    val offsetY: Float,
    val radius: Float,
    val dispersion: Float,
)

/** 内置阴影。 */
object GlassShadows {

    private val Black = Color(0f, 0f, 0f, 0.05f)

    /** 贴在页面上的表面。 */
    @Stable
    val Low: GlassShadow = GlassShadow(Black, 0f, 0f, 44f, 0.5f)

    /** 日常使用的面板。 */
    @Stable
    val Regular: GlassShadow = GlassShadow(Black, 0f, 2f, 64f, 0.5f)

    /** 悬浮离开页面的表面。 */
    @Stable
    val High: GlassShadow = GlassShadow(Black, 0f, 4f, 80f, 0.5f)

    /** 压在变暗页面上的对话框。 */
    @Stable
    val ExtraHigh: GlassShadow = GlassShadow(Black, 0f, 70f, 96f, 0.5f)

    /** 浮动控件。 */
    @Stable
    val Float: GlassShadow = GlassShadow(Black, 0f, 0f, 24f, 0.5f)
}
