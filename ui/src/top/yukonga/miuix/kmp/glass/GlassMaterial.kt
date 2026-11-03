// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/** [GlassMaterial] 的一个颜色层与其下方背景的混合方式。 */
enum class GlassColorBlendMode {
    /** 图层按自身 alpha 的比例替换背景。 */
    SrcOver,
    /** 减法变暗：`backdrop - layerAlpha * (1 - layer)`，向下截断到黑。 */
    PlusDarker,
    /** 加法变亮：`backdrop + layerAlpha * layer`，向上截断到白。 */
    PlusLighter,
    /** 温和的加深减淡。永远不会达到纯黑或纯白。 */
    SoftLight,
    /** 图层与背景互换的 [Overlay]。由图层决定对比度。 */
    HardLight,
    /** 对暗背景做正片叠底，对亮背景做滤色。由背景决定对比度。 */
    Overlay,
    /** 保留背景的色相与饱和度，取图层的亮度。 */
    Luminosity,
    /** 由图层驱动，把背景向白色提亮。 */
    ColorDodge,
    /** 由图层驱动，把背景向黑色压暗。 */
    ColorBurn,
}

/**
 * 铺在模糊背景之上的一层颜色。
 *
 * @property color 图层的颜色。其 alpha 决定混合的作用程度。
 * @property mode 它与下方已有内容的混合方式。
 */
@Immutable
data class GlassColorLayer(
    val color: Color,
    val mode: GlassColorBlendMode,
)

/**
 * 一个模糊半径及其之上的色彩处理——面板自身的主体。
 *
 * @property blurRadius 图层叠加之前背景的模糊程度。它取代 [GlassStyle] 原本请求的模糊。
 * @property first 最靠近背景的图层。始终存在。
 * @property second 覆盖在 [first] 之上的图层。`null` 表示层叠到此为止。
 * @property third 覆盖在 [second] 之上的图层。`null` 表示层叠到此为止。
 */
@Immutable
data class GlassMaterial(
    val blurRadius: Dp,
    val first: GlassColorLayer,
    val second: GlassColorLayer? = null,
    val third: GlassColorLayer? = null,
) {
    init {
        require(second != null || third == null) {
            "A GlassMaterial cannot carry a third colour layer without a second"
        }
    }
}

/** 把子级与衬底的模糊方差合并，供共享的单趟背景滤镜使用。 */
internal fun GlassMaterial.withUnderlayBlur(underlay: GlassMaterial?): GlassMaterial = if (underlay == null) {
    this
} else {
    copy(blurRadius = sqrt(blurRadius.value * blurRadius.value + underlay.blurRadius.value * underlay.blurRadius.value).dp)
}
