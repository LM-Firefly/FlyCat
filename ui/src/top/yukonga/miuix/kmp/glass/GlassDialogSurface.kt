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
import top.yukonga.miuix.kmp.glass.internal.ScrimBackdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** [GlassDialog] 与 [glassDialogSurface] 的默认值。 */
object GlassDialogDefaults {

    /** 应用可见性与返回手势进度之前的遮罩（scrim）颜色。 */
    val ScrimColor: Color = Color(0x33000000)

    /** 当前主题的对话框材质。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.dialogGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 当前主题的对话框泛光描边。 */
    @Composable
    fun stroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassEdgeStrokes.SmallLight,
        dark = GlassEdgeStrokes.SmallDark,
    )

    /** 没有背景或运行时着色器支持时使用的不透明填充。 */
    @Composable
    fun containerColor(): Color = MiuixTheme.colorScheme.background
}

/**
 * 用于 OverlayDialog `surface` 槽位的玻璃表面。
 *
 * 背景在模糊之前被调暗，并在对话框缩放时保持与屏幕对齐。对话框内容不产生折射。
 *
 * FORK（FlyCat）：miuix 0.9.4 的 OverlayDialog 没有 `surface` 槽位与 DialogSurfaceScope，
 * 故此函数直接返回 [Modifier]；圆角改由 [cornerRadius] 参数传入，scrim/scale 以静态近似提供
 * （不随对话框缩放联动）。
 *
 * @param backdrop 页面录制内容，包含栏位但不包含对话框宿主及其遮罩。为 `null` 时使用
 *   [containerColor]。运行时着色器不受支持时也使用该不透明回退。
 * @param cornerRadius FORK（FlyCat）：表面圆角（上游由 DialogSurfaceScope 提供）。
 * @param material 对话框的模糊输入与有序颜色层。
 * @param stroke 可选的全分辨率泛光描边。
 * @param containerColor 不透明回退填充。
 */
@Composable
fun glassDialogSurface(
    backdrop: Backdrop?,
    cornerRadius: Dp,
    material: GlassMaterial = GlassDialogDefaults.material(),
    stroke: EdgeStroke? = GlassDialogDefaults.stroke(),
    containerColor: Color = GlassDialogDefaults.containerColor(),
): Modifier {
    // FORK（FlyCat）：0.9.4 overlay 无 surface 槽/scope：scrim 用默认常量、scale 固定 1f。
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
