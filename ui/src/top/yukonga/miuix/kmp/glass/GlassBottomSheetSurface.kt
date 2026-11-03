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
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.internal.ScrimBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 启动器附带的底部弹出面板材质的默认值。 */
object GlassBottomSheetDefaults {
    val ScrimColor: Color = GlassDialogDefaults.ScrimColor
    /**原生面板和对话框引用相同的60dp模糊和三个颜色层。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.dialogGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )
    /** 初始近似值；原生面板的泛光使用与对话框不同的配置。 */
    @Composable
    fun stroke(): GlassStroke = GlassStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassStrokes.MiddleLight,
        dark = GlassStrokes.MiddleDark,
    )
    /** 当没有场景或运行时着色器支持时，使用不透明的回退方案。 */
    @Composable
    fun containerColor(): Color = MiuixTheme.colorScheme.background
}

/**
 * 用于 OverlayBottomSheet 的 `surface` 插槽的模糊/混合材质，带有圆角顶部。
 *
 * 宿主保留对尺寸、内边距、手柄和手势的所有权。提供的干净 [backdrop] 在模糊之前会局部变暗，采样遵循面板的平移屏幕坐标。
 * 在向上过滚动时，暴露的底部边缘会获得相同材质但不带描边。
 * 不会有折射或额外的投影。原生模糊内核转换、遮罩采样顺序和辉光效果仍需视觉校准。
 *
 * @param backdrop 页面记录，包含各系统栏，排除面板宿主及其遮罩。为 null 或运行时着色器不可用时，使用不透明的 [containerColor] 作为回退。
 * @param material 共享的对话框/面板模糊及有序混合层。
 * @param stroke 可选的辉光近似；过滚动扩展部分省略。
 * @param containerColor 不透明回退填充色。
 */
@Composable
fun glassBottomSheetSurface(
    backdrop: Backdrop?,
    cornerRadius: Dp,
    material: GlassMaterial = GlassBottomSheetDefaults.material(),
    stroke: GlassStroke? = GlassBottomSheetDefaults.stroke(),
    containerColor: Color = GlassBottomSheetDefaults.containerColor(),
): Modifier {
    // 0.9.4 overlay 无 surface 槽/scope：scrim 用默认常量、不随过滚动联动（静态近似）。
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
        stroke = stroke,
        shadow = null,
        shading = false,
        fallback = Modifier.clip(shape).background(containerColor),
    )
}
