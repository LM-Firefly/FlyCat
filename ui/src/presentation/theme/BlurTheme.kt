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

package com.github.lmfirefly.flycat.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.core.model.BlurThemeSettings
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownOption
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.EdgeStrokes
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.withTilt

/** 全局主题模糊样式（主题设置页写入，玻璃/模糊组件采样）。 */
val LocalBlurTheme = compositionLocalOf { BlurThemeSettings() }

private val blurThemeJson = Json { ignoreUnknownKeys = true }

fun parseBlurTheme(json: String): BlurThemeSettings = if (json.isBlank()) { BlurThemeSettings() } else {
    runCatching {
        blurThemeJson.decodeFromString<BlurThemeSettings>(json)
    }.getOrDefault(BlurThemeSettings()).let {
        settings -> settings.copy(
            texture = settings.texture.copy(
                cardCornerRadius = settings.texture.cardCornerRadius.coerceIn(
                    BlurThemeSettings.MIN_CARD_CORNER_RADIUS,
                    BlurThemeSettings.MAX_CARD_CORNER_RADIUS,
                ),
            ),
        )
    }
}

fun serializeBlurTheme(settings: BlurThemeSettings): String = blurThemeJson.encodeToString(BlurThemeSettings.serializer(), settings)

@Composable
fun themeCardCornerRadius(): Dp = LocalBlurTheme.current.texture.cardCornerRadius.coerceIn(BlurThemeSettings.MIN_CARD_CORNER_RADIUS, BlurThemeSettings.MAX_CARD_CORNER_RADIUS).dp

/** 单条混色令牌预设：显示名 + 明/暗两套色层（主题无关的预设两套共用）。 */
private class BlendPreset(val label: String, val light: List<BlendColorEntry>, val dark: List<BlendColorEntry>)

private fun themedPreset(label: String, light: List<BlendColorEntry>, dark: List<BlendColorEntry>) = BlendPreset(label, light, dark)

private fun sharedPreset(label: String, entries: List<BlendColorEntry>) = BlendPreset(label, entries, entries)

/** 纹理/渐进两组共用的混色令牌矩阵（None + Info 四档、Colored/Pured/Overlay 五档、Info Colored）。名称与明暗色层同表绑定，杜绝序号错位。 */
private val blendPresets: List<BlendPreset> = listOf(
    sharedPreset("None", emptyList()),
    themedPreset("Info Extra Thin", ColorBlendToken.Info_Extra_Thin_Light, ColorBlendToken.Info_Extra_Thin_Dark),
    themedPreset("Info Thin", ColorBlendToken.Info_Thin_Light, ColorBlendToken.Info_Thin_Dark),
    themedPreset("Info Regular", ColorBlendToken.Info_Regular_Light, ColorBlendToken.Info_Regular_Dark),
    themedPreset("Info Thick", ColorBlendToken.Info_Thick_Light, ColorBlendToken.Info_Thick_Dark),
    sharedPreset("Info Colored", ColorBlendToken.Info_Colored_Regular),
    themedPreset("Colored Extra Thin", ColorBlendToken.Colored_Extra_Thin_Light, ColorBlendToken.Colored_Extra_Thin_Dark),
    themedPreset("Colored Thin", ColorBlendToken.Colored_Thin_Light, ColorBlendToken.Colored_Thin_Dark),
    themedPreset("Colored Regular", ColorBlendToken.Colored_Regular_Light, ColorBlendToken.Colored_Regular_Dark),
    themedPreset("Colored Thick", ColorBlendToken.Colored_Thick_Light, ColorBlendToken.Colored_Thick_Dark),
    themedPreset("Colored Extra Thick", ColorBlendToken.Colored_Extra_Thick_Light, ColorBlendToken.Colored_Extra_Thick_Dark),
    themedPreset("Pured Extra Thin", ColorBlendToken.Pured_Extra_Thin_Light, ColorBlendToken.Pured_Extra_Thin_Dark),
    themedPreset("Pured Thin", ColorBlendToken.Pured_Thin_Light, ColorBlendToken.Pured_Thin_Dark),
    themedPreset("Pured Regular", ColorBlendToken.Pured_Regular_Light, ColorBlendToken.Pured_Regular_Dark),
    themedPreset("Pured Thick", ColorBlendToken.Pured_Thick_Light, ColorBlendToken.Pured_Thick_Dark),
    themedPreset("Pured Extra Thick", ColorBlendToken.Pured_Extra_Thick_Light, ColorBlendToken.Pured_Extra_Thick_Dark),
    themedPreset("Overlay Extra Thin", ColorBlendToken.Overlay_Extra_Thin_Light, ColorBlendToken.Overlay_Extra_Thin_Dark),
    themedPreset("Overlay Thin", ColorBlendToken.Overlay_Thin_Light, ColorBlendToken.Overlay_Thin_Dark),
    themedPreset("Overlay Regular", ColorBlendToken.Overlay_Regular_Light, ColorBlendToken.Overlay_Regular_Dark),
    themedPreset("Overlay Thick", ColorBlendToken.Overlay_Thick_Light, ColorBlendToken.Overlay_Thick_Dark),
    themedPreset("Overlay Extra Thick", ColorBlendToken.Overlay_Extra_Thick_Light, ColorBlendToken.Overlay_Extra_Thick_Dark),
)

/** 前景模糊的令牌表：共享矩阵 + 插在 None 之后的 Logo Blend（示例 ForegroundBlurDemo 默认项，序号 1）。 */
private val foregroundBlendPresets: List<BlendPreset> = listOf(blendPresets.first(), themedPreset("Logo Blend", logoBlend(dark = false), logoBlend(dark = true))) + blendPresets.drop(1)

private fun presets(foreground: Boolean): List<BlendPreset> = if (foreground) foregroundBlendPresets else blendPresets

/** 混色令牌显示名表（序号即 [blurBlendToken] 的令牌索引）。 */
fun blurTokenNames(foreground: Boolean): List<String> = presets(foreground).map { it.label }

/** 混色模式下拉选项（序号即 [blurBlendToken] 的令牌索引）。 */
fun blendDropdownOptions(foreground: Boolean): List<GlassDropdownOption<Int>> = blurTokenNames(foreground).mapIndexed { index, label -> GlassDropdownOption(value = index, label = label) }

/** 滑杆数值显示（最多两位小数，向零截断）。 */
fun sliderText(value: Float): String = "${(value * 100).toInt() / 100f}"

/** 噪点数值显示（最多四位小数，向零截断）。 */
fun noiseText(value: Float): String = "${(value * 10000).toInt() / 10000f}"

private fun logoBlend(dark: Boolean): List<BlendColorEntry> = if (dark) {
    listOf(
        BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
    )
} else {
    listOf(
        BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
    )
}

/** 按序号取混色令牌（明暗色层与显示名同表绑定，见 [blurTokenNames]）。 */
fun blurBlendToken(index: Int, dark: Boolean, foreground: Boolean): List<BlendColorEntry> {
    val preset = presets(foreground).getOrNull(index) ?: return emptyList()
    return if (dark) preset.dark else preset.light
}

/** 主题驱动的容器描边（0=关闭 1=大 2=中 3=小，同示例 EdgeStrokeConfig）。 */
@Composable
fun themeEdgeStroke(dark: Boolean): EdgeStroke? = when (LocalBlurTheme.current.texture.edgeStroke) {
    BlurThemeSettings.EDGE_LARGE -> if (!dark) EdgeStrokes.BigLight else EdgeStrokes.BigDark
    BlurThemeSettings.EDGE_MEDIUM -> if (!dark) EdgeStrokes.MiddleLight else EdgeStrokes.MiddleDark
    BlurThemeSettings.EDGE_SMALL -> if (!dark) EdgeStrokes.SmallLight else EdgeStrokes.SmallDark
    else -> null
}

/**
 * 主题驱动的容器描边修饰符：设置为 Disabled 时不描边。
 * 光源经 `withTilt` 跟随设备姿态（同示例 rememberContainerEdgeStrokeState，主/次光源灵敏度分别为 0.15/0.12）；裸预设的亮面朝向与带姿态管线的渲染不同，必须走这条链路才能与示例一致。
 * [tint] 非空时以该色重染描边基色与两侧光源（保留各层透明度与强度），供选中态等强调场景使用。
 * [emphasized] 用于焦点态或选中态：提高边缘光强度并以 SrcOver 保留主题色，避免亮色壁纸将染色冲淡。
 */
@Composable
fun Modifier.themeEdgeStroke(shape: Shape, tint: Color? = null, emphasized: Boolean = false): Modifier {
    val base = themeEdgeStroke(isDarkTheme()) ?: return this
    val styledBase = if (emphasized) {
        base.copy(
            color = base.color.copy(alpha = 0.35f),
            primary = base.primary.copy(
                color = base.primary.color.copy(alpha = 0.95f),
                intensity = base.primary.intensity.coerceAtLeast(0.9f),
            ),
            secondary = base.secondary.copy(
                color = base.secondary.color.copy(alpha = 0.8f),
                intensity = base.secondary.intensity.coerceAtLeast(0.75f),
            ),
            blendMode = BlendMode.SrcOver,
        )
    } else { base }
    val tilt = LocalDeviceTilt.current
    val stroke = remember(styledBase, tilt, tint) {
        derivedStateOf {
            styledBase.copy(
                primary = styledBase.primary.withTilt(tilt.value, 0.15f),
                secondary = styledBase.secondary.withTilt(tilt.value, 0.12f),
            ).tinted(tint)
        }
    }
    return this.drawWithContent {
        drawContent()
        drawEdgeStroke(shape = shape, stroke = stroke.value)
    }
}

/** 以 [tint] 重染描边的基色与两侧光源色（保留各层 alpha 与强度）。 */
private fun EdgeStroke.tinted(tint: Color?): EdgeStroke = if (tint == null) { this } else {
    fun recolor(original: Color): Color = Color(tint.red, tint.green, tint.blue, original.alpha)
    copy(
        color = recolor(color),
        primary = primary.copy(color = recolor(primary.color)),
        secondary = secondary.copy(color = recolor(secondary.color)),
    )
}

/** BlurBlendMode → GlassColorBlendMode：九值同名直译；LinearLight/Lab 无对应，降级到语义最接近的混合（HardLight / Luminosity）。 */
private fun glassBlendOf(mode: BlurBlendMode): GlassColorBlendMode = when (mode) {
    BlurBlendMode.SrcOver -> GlassColorBlendMode.SrcOver
    BlurBlendMode.PlusDarker -> GlassColorBlendMode.PlusDarker
    BlurBlendMode.PlusLighter -> GlassColorBlendMode.PlusLighter
    BlurBlendMode.SoftLight -> GlassColorBlendMode.SoftLight
    BlurBlendMode.HardLight -> GlassColorBlendMode.HardLight
    BlurBlendMode.Overlay -> GlassColorBlendMode.Overlay
    BlurBlendMode.Luminosity -> GlassColorBlendMode.Luminosity
    BlurBlendMode.ColorDodge -> GlassColorBlendMode.ColorDodge
    BlurBlendMode.ColorBurn -> GlassColorBlendMode.ColorBurn
    BlurBlendMode.LinearLight -> GlassColorBlendMode.HardLight
    BlurBlendMode.Lab -> GlassColorBlendMode.Luminosity
    else -> GlassColorBlendMode.SrcOver
}

/** 主题驱动的玻璃材质：采样半径取纹理组 Blur Radius，色层由纹理组 Blend Mode 令牌翻译（[BlurBlendMode] 与 [GlassColorBlendMode] 九值同名，LinearLight/Lab 降级）。令牌为空时退化为单层透明。 */
@Composable
fun themeGlassMaterial(dark: Boolean): GlassMaterial {
    val style = LocalBlurTheme.current.texture
    val layers = blurBlendToken(style.blendMode, dark, foreground = false).map { GlassColorLayer(it.color, glassBlendOf(it.mode)) }
    val first = layers.firstOrNull() ?: GlassColorLayer(Color.Transparent, GlassColorBlendMode.SrcOver)
    return GlassMaterial(
        blurRadius = style.blurRadius.dp,
        first = first,
        second = layers.getOrNull(1),
        third = layers.getOrNull(2),
    )
}

/** 主题驱动的弹层视觉（下拉/菜单玻璃面板）：材质与描边跟随 Texture Blur 组（混色令牌/采样半径经 [themeGlassMaterial]、容器描边经 [themeEdgeStroke]），其余沿用库默认。 */
@Composable
fun themePopupVisuals(): GlassPopupVisuals {
    val dark = isDarkTheme()
    return GlassPopupDefaults.visuals(stroke = themeEdgeStroke(dark), material = themeGlassMaterial(dark))
}
