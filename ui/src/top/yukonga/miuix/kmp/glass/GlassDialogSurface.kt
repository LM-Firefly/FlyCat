// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.internal.ScrimBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** OverlayDialog 托管的模糊/混合表面的默认设置。 */
object GlassDialogDefaults {
    /** 静止状态的路由遮罩颜色。由宿主端应用打开/关闭及返回手势的进度。 */
    val ScrimColor: Color = Color(0x33000000)
    /** 对话框的三个颜色层和模糊输入，从当前主题中选择。*/
    @Composable
    fun material(): GlassMaterial = GlassMaterials.dialogGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )
    /** 初始辉光近似，待校准原生对话框的辉光字段。 */
    @Composable
    fun stroke(): GlassStroke = GlassStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassStrokes.SmallLight,
        dark = GlassStrokes.SmallDark,
    )
    /** 当没有背景或运行时着色器支持时，使用不透明填充。*/
    @Composable
    fun containerColor(): Color = MiuixTheme.colorScheme.background
}

/**
 * 用于 OverlayDialog 的 `surface` 插槽的对话框专用模糊/混合表面。
 *
 * 宿主提供圆角以及动画化的遮罩（scrim）/缩放。干净的 [backdrop] 在模糊之前会被局部调暗，并在对话框缩放时保持与屏幕对齐。内容单独绘制，不产生折射。
 * 原生遮罩采样顺序和模糊核宽度仍需进行视觉校准；[stroke] 默认为初始泛光（bloom）近似。
 * @param backdrop 页面录制内容，包含栏位但不包含对话框宿主及其遮罩。为 Null 时使用 [containerColor]。不支持的运行时着色器也使用此不透明回退。
 * @param material 对话框的模糊输入及有序颜色层。
 * @param stroke 可选的全分辨率泛光描边。
 * @param containerColor 不透明回退填充。
 * */

@Composable
fun glassDialogSurface(
    backdrop: Backdrop?,
    cornerRadius: Dp,
    material: GlassMaterial = GlassDialogDefaults.material(),
    stroke: GlassStroke? = GlassDialogDefaults.stroke(),
    containerColor: Color = GlassDialogDefaults.containerColor(),
): Modifier {
    // 0.9.4 overlay 无 surface 槽/scope：scrim 用默认常量、scale 固定 1f（不随对话框缩放联动）。
    val scrimColorState = rememberUpdatedState(GlassDialogDefaults.ScrimColor)
    val scaleState = rememberUpdatedState(1f)
    val effectiveBackdrop = if (backdrop != null && isRuntimeShaderSupported()) {
        remember(backdrop, scrimColorState, scaleState) { ScrimBackdrop(backdrop, scrimColorState, scaleState) }
    } else {
        null
    }
    val shape = remember(cornerRadius) { GlassShape(cornerRadius) }
    return Modifier.glassPanel(
        backdrop = effectiveBackdrop,
        shape = shape,
        material = material,
        stroke = stroke,
        shadow = null,
        shading = false,
        fallback = Modifier.clip(shape).background(containerColor),
    )
}
