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
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.internal.ScrimBackdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** [GlassBottomSheet] 与 [glassBottomSheetSurface] 的默认值。 */
object GlassBottomSheetDefaults {
    val ScrimColor: Color = GlassDialogDefaults.ScrimColor

    /** 当前主题的底部面板材质。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.dialogGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 当前主题的底部面板泛光描边。 */
    @Composable
    fun stroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassEdgeStrokes.MiddleLight,
        dark = GlassEdgeStrokes.MiddleDark,
    )

    /** 没有场景或运行时着色器支持时的不透明回退。 */
    @Composable
    fun containerColor(): Color = MiuixTheme.colorScheme.background
}

/**
 * 用于 OverlayBottomSheet `surface` 槽位的玻璃表面，顶部两角为圆角。
 *
 * 背景在模糊之前被调暗，并按面板平移后的屏幕坐标采样。向上过滚动时材质延伸且不带描边。
 * 内容不产生折射，也不绘制额外投影。
 *
 * FORK（FlyCat）：miuix 0.9.4 的 OverlayBottomSheet 没有 `surface` 槽位与 BottomSheetSurfaceScope，
 * 故此函数直接返回 [Modifier]；圆角改由 [cornerRadius] 参数传入，scrim 以静态近似提供
 * （不随过滚动联动）。
 *
 * @param backdrop 页面录制内容，包含栏位但不包含面板宿主及其遮罩。为 `null` 或运行时着色器
 *   不可用时使用不透明的 [containerColor] 回退。
 * @param cornerRadius FORK（FlyCat）：表面顶部圆角（上游由 BottomSheetSurfaceScope 提供）。
 * @param material 对话框/面板共享的模糊输入与有序混合层。
 * @param stroke 可选的泛光描边，过滚动延伸部分省略。
 * @param containerColor 不透明回退填充。
 */
@Composable
fun glassBottomSheetSurface(
    backdrop: Backdrop?,
    cornerRadius: Dp,
    material: GlassMaterial = GlassBottomSheetDefaults.material(),
    stroke: EdgeStroke? = GlassBottomSheetDefaults.stroke(),
    containerColor: Color = GlassBottomSheetDefaults.containerColor(),
): Modifier {
    // FORK（FlyCat）：0.9.4 overlay 无 surface 槽/scope：scrim 用默认常量（静态近似）。
    val scrimColorState = rememberUpdatedState(GlassBottomSheetDefaults.ScrimColor)
    val effectiveBackdrop = if (backdrop != null && isRuntimeShaderSupported()) {
        remember(backdrop, scrimColorState) { ScrimBackdrop(backdrop, scrimColorState) }
    } else {
        null
    }
    val shape = remember(cornerRadius) {
        GlassShape(topStart = cornerRadius, topEnd = cornerRadius, bottomEnd = 0.dp, bottomStart = 0.dp)
    }
    return Modifier.glassPanel(
        backdrop = effectiveBackdrop,
        shape = shape,
        material = material,
        // FORK（FlyCat）：无 BottomSheetSurfaceScope，感知不到 isOverscroll，故不过滚动时也保留描边。
        stroke = stroke,
        shadow = null,
        shading = false,
        fallback = Modifier.clip(shape).background(containerColor),
    )
}
