// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 玻璃材质样式的完整描述。
 *
 * @property blend 应用于折射背景的亮度曲线、饱和度、亮度与暗部压缩。
 * @property inner 玻璃主体的着色、内发光与最终 gamma。
 * @property edge 边缘几何——受光带的宽度，以及表面在其内部的弯曲方式。
 * @property reflect 反射光线混入折射光线的比例。
 * @property light 为边缘着色的两盏方向光。
 * @property refract 玻璃主体的折射率。
 * @property background 提供低频色彩的宽模糊背景的处理方式。
 * @property blur 材质采样的两个背景模糊半径。
 */
@Immutable
data class GlassStyle(
    val blend: GlassBlend,
    val inner: GlassInner,
    val edge: GlassEdge,
    val reflect: GlassReflect,
    val light: GlassLight,
    val refract: GlassRefract,
    val background: GlassBackground,
    val blur: GlassBlur,
)

/**
 * 折射背景的色彩处理。
 *
 * @property curveA 亮度曲线的三次项。
 * @property curveB 亮度曲线的二次项。
 * @property curveC 亮度曲线的一次项。
 * @property curveD 亮度曲线的常数项。
 * @property amount 曲线结果的混入比例。0 表示亮度不变。
 * @property saturation 饱和度乘数。1 表示饱和度不变。
 * @property brightness 加性亮度。0 表示亮度不变。
 * @property darker 暗部向近黑紫色压缩的强度。
 * @property darkerStart 压缩开始处的亮度。
 * @property darkerEnd 压缩达到 [darker] 处的亮度。
 */
@Immutable
data class GlassBlend(
    val curveA: Float,
    val curveB: Float,
    val curveC: Float,
    val curveD: Float,
    val amount: Float,
    val saturation: Float,
    val brightness: Float,
    val darker: Float,
    val darkerStart: Float,
    val darkerEnd: Float,
)

/**
 * 玻璃主体的色彩与不透明度处理。
 *
 * @property bottom 底边加性辉光的强度。
 * @property tint 玻璃的颜色。其自身的 alpha 被忽略——由 [tintStrength] 承载。
 * @property tintStrength 背景色向 [tint] 靠拢的程度。大于 1 的值合法。
 * @property colorWhite 宽模糊背景在为玻璃着色之前向白色靠拢的程度。
 * @property colorMix 成品玻璃色替换折射背景的程度。
 * @property colorPow 应用于材质的最终 gamma。大于 1 变暗，小于 1 提亮。
 * @property alpha 材质的基础不透明度。
 */
@Immutable
data class GlassInner(
    val bottom: Float,
    val tint: Color,
    val tintStrength: Float,
    val colorWhite: Float,
    val colorMix: Float,
    val colorPow: Float,
    val alpha: Float,
)

/**
 * 边缘几何，单位为像素。
 *
 * @property width 边缘带的宽度。
 * @property pow 边缘剖面的曲率指数。1 是柔和的圆拱；数值越大，表面平坦段越长，到轮廓处转折越急。
 * @property thickness 玻璃厚度。它决定折射光线偏移的距离。
 * @property reflectOffset 反射光线偏移的距离。大值会引入更远处的颜色。
 */
@Immutable
data class GlassEdge(
    val width: Float,
    val pow: Float,
    val thickness: Float,
    val reflectOffset: Float,
)

/**
 * 反射光线。
 *
 * @property lighten 叠加在折射之上的反射加性强度。
 * @property strength 在轮廓处反射混入折射的程度。
 */
@Immutable
data class GlassReflect(
    val lighten: Float,
    val strength: Float,
)

/**
 * 为边缘着色的两盏方向光。第二盏光在 X 和 Y 上指向相反方向，因此一组预设就能照亮形状的两侧。
 *
 * @property directionX 主光方向的 X 分量。
 * @property directionY 主光方向的 Y 分量。
 * @property directionZ 主光方向的 Z 分量。
 * @property intensity 主光的强度。
 * @property oppositeIntensity 反向光的强度。
 * @property angleRange 角度衰减范围，以 pi 的倍数表示。
 */
@Immutable
data class GlassLight(
    val directionX: Float,
    val directionY: Float,
    val directionZ: Float,
    val intensity: Float,
    val oppositeIntensity: Float,
    val angleRange: Float,
)

/**
 * 玻璃主体的折射率。
 * @property ior 折射率。1 不产生弯曲；1.5 是窗玻璃。
 */
@Immutable
data class GlassRefract(
    val ior: Float,
)

/**
 * 宽模糊背景的处理方式，它提供玻璃的低频色彩。
 *
 * @property saturation 宽模糊背景的饱和度乘数。0 使其变为灰色。
 * @property brightness 宽模糊背景的加性亮度。
 * @property burn 颜色加深（colour-burn）比率的指数。小于 0.5 的值会被提升到 0.5。
 * @property unShade 材质坍缩为平涂 [GlassInner.tint] 的程度。1 表示完全去掉材质，只剩一个纯着色的形状。
 */
@Immutable
data class GlassBackground(
    val saturation: Float,
    val brightness: Float,
    val burn: Float,
    val unShade: Float,
)

/**
 * 两个背景模糊半径，单位为 [GlassDefaults.SourceDensity] 下的像素。
 *
 * @property small 折射模糊的半径。它保留结构，因此边缘能看到可辨认的背景弯折影像。
 * @property big 色彩模糊的半径。它只提供低频着色。
 */
@Immutable
data class GlassBlur(
    val small: Float,
    val big: Float,
)
