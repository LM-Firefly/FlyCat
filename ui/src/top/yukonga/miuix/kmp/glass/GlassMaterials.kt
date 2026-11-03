// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 内置材质。 */
object GlassMaterials {

    /** `internal-pured-thin-glass`，亮色：浮动条的主体。 */
    @Stable
    val PuredThinGlassLight: GlassMaterial = GlassMaterial(
        blurRadius = 20.dp,
        first = GlassColorLayer(Color(0x05000000), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color(0x99FFFFFF), GlassColorBlendMode.SoftLight),
        third = GlassColorLayer(Color(0x66FFFFFF), GlassColorBlendMode.HardLight),
    )

    /** `internal-pured-thin-glass`，暗色。 */
    @Stable
    val PuredThinGlassDark: GlassMaterial = GlassMaterial(
        blurRadius = 20.dp,
        first = GlassColorLayer(Color(0x1A000000), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color(0x66565656), GlassColorBlendMode.Luminosity),
        third = GlassColorLayer(Color(0x993F3F3F), GlassColorBlendMode.Overlay),
    )

    /** `popupview-glass`，亮色：60dp 模糊，配自有三层颜色层。 */
    @Stable
    val PopupViewGlassLight: GlassMaterial = GlassMaterial(
        blurRadius = 60.dp,
        first = GlassColorLayer(Color(0x05000000), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color(0x99FFFFFF), GlassColorBlendMode.SoftLight),
        third = GlassColorLayer(Color(0x99FFFFFF), GlassColorBlendMode.HardLight),
    )

    /** `popupview-glass`，暗色：60dp 模糊，配自有三层颜色层。 */
    @Stable
    val PopupViewGlassDark: GlassMaterial = GlassMaterial(
        blurRadius = 60.dp,
        first = GlassColorLayer(Color(0x1A000000), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color(0x996C6C6C), GlassColorBlendMode.Luminosity),
        third = GlassColorLayer(Color(0x99060606), GlassColorBlendMode.SoftLight),
    )

    /** 对话框模糊/混合材质，亮色。三个颜色层按声明顺序叠加。 */
    @Stable
    val DialogGlassLight: GlassMaterial = GlassMaterial(
        blurRadius = 60.dp,
        first = GlassColorLayer(Color(0x05000000), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color(0x99FFFFFF), GlassColorBlendMode.SoftLight),
        third = GlassColorLayer(Color(0x99FFFFFF), GlassColorBlendMode.HardLight),
    )

    /** 对话框模糊/混合材质，暗色。 */
    @Stable
    val DialogGlassDark: GlassMaterial = GlassMaterial(
        blurRadius = 60.dp,
        first = GlassColorLayer(Color(0x1A000000), GlassColorBlendMode.PlusDarker),
        second = GlassColorLayer(Color(0x996C6C6C), GlassColorBlendMode.Luminosity),
        third = GlassColorLayer(Color(0x99060606), GlassColorBlendMode.SoftLight),
    )

    /** 操作栏的 `mask-pured-regular`，亮色。 */
    @Stable
    val ActionBarMaskLight: GlassMaterial = GlassMaterial(
        blurRadius = 40.dp,
        first = GlassColorLayer(Color(0x33F9F9F9), GlassColorBlendMode.Overlay),
        second = GlassColorLayer(Color(0xB3FFFFFF), GlassColorBlendMode.HardLight),
    )

    /** 操作栏的 `mask-pured-regular`，暗色。 */
    @Stable
    val ActionBarMaskDark: GlassMaterial = GlassMaterial(
        blurRadius = 60.dp,
        first = GlassColorLayer(Color(0x75000000), GlassColorBlendMode.ColorBurn),
        second = GlassColorLayer(Color(0x52000000), GlassColorBlendMode.SrcOver),
    )

    /** 从一对主题材质中选取属于当前主题的那一个。 */
    @Stable
    fun forTheme(isDark: Boolean, light: GlassMaterial, dark: GlassMaterial): GlassMaterial = if (isDark) dark else light

    /** 按当前主题返回 [PuredThinGlassLight] 或 [PuredThinGlassDark]。 */
    @Stable
    fun puredThinGlass(isDark: Boolean): GlassMaterial = forTheme(isDark, PuredThinGlassLight, PuredThinGlassDark)

    /** 按当前主题返回 [PopupViewGlassLight] 或 [PopupViewGlassDark]。 */
    @Stable
    fun popupViewGlass(isDark: Boolean): GlassMaterial = forTheme(isDark, PopupViewGlassLight, PopupViewGlassDark)

    /** 按当前主题返回 [DialogGlassLight] 或 [DialogGlassDark]。 */
    @Stable
    fun dialogGlass(isDark: Boolean): GlassMaterial = forTheme(isDark, DialogGlassLight, DialogGlassDark)

    /** 按当前主题返回 [ActionBarMaskLight] 或 [ActionBarMaskDark]。 */
    @Stable
    fun actionBarMask(isDark: Boolean): GlassMaterial = forTheme(isDark, ActionBarMaskLight, ActionBarMaskDark)
}
