// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/**
 *
 * 一个由 Scaffold 承载的、附着式底部弹出面板，使用原生模糊/混合材质和动画遮罩层。
 * 宿主负责处理把手、滑动/拖拽/返回手势以及嵌套滚动。材质还会填充
 * 向上过度滚动区域。提供的 [backdrop] 必须记录弹出面板宿主之外的页面和各系统栏，
 * 排除面板本身及其遮罩层。若 [backdrop] 为空或运行时着色器不可用，则使用 [containerColor]。
 * 内容布局与 OverlayBottomSheet 一致。
 * 当可滚动内容的底部间距应随内容一起滚动时，在可滚动内容内部应用导航栏内边距；玻璃效果延伸到屏幕边缘。
 *
 * @param onDismissRequest 在允许的外部点击、拖拽关闭或返回手势时调用。
 * @param show 面板是否显示。
 * @param backdrop 干净的页面记录；null 选择不透明的后备方案。
 * @param modifier 应用于面板的 Modifier。
 * @param enableWindowDim 是否绘制和采样动画的 [dimColor]。
 * @param defaultWindowInsetsPadding 是否应用宿主的默认窗口内边距，包括输入法内边距。
 * @param allowDismiss 外部点击、拖拽和返回手势是否可以关闭面板。
 * @param enableNestedScroll 子滚动是否可以将动作传递给面板。
 * @param renderInRootScaffold 最外层 Scaffold 是否承载面板。
 * @param title 可选的标题，位于内容上方。
 * @param startAction 可选的前导头部操作。
 * @param endAction 可选的尾随头部操作。
 * @param cornerRadius 顶部圆角半径；底部圆角保持直角。
 * @param sheetMaxWidth 面板最大宽度。
 * @param outsideMargin 面板外部边距。
 * @param insideMargin 面板内容内边距。
 * @param dragHandleColor 应用按压透明度之前的把手颜色。
 * @param material 模糊输入和有序的颜色层。
 * @param stroke 可选的辉光近似效果，在过度滚动扩展区域上省略。
 * @param containerColor 不透明的后备填充。
 * @param dimColor 宿主应用动画进度之前的遮罩颜色。
 * @param onDismissFinished 隐藏动画完成后调用。
 * @param content 面板内容。根据需要添加滚动、导航栏内边距和额外间距。
 */
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    show: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enableWindowDim: Boolean = true,
    defaultWindowInsetsPadding: Boolean = true,
    allowDismiss: Boolean = true,
    enableNestedScroll: Boolean = true,
    renderInRootScaffold: Boolean = true,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    cornerRadius: Dp = BottomSheetDefaults.cornerRadius,
    sheetMaxWidth: Dp = BottomSheetDefaults.maxWidth,
    outsideMargin: DpSize = BottomSheetDefaults.outsideMargin,
    insideMargin: DpSize = BottomSheetDefaults.insideMargin,
    dragHandleColor: Color = BottomSheetDefaults.dragHandleColor(),
    material: GlassMaterial = GlassBottomSheetDefaults.material(),
    stroke: GlassStroke? = GlassBottomSheetDefaults.stroke(),
    containerColor: Color = GlassBottomSheetDefaults.containerColor(),
    dimColor: Color = GlassBottomSheetDefaults.ScrimColor,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        // 0.9.4 overlay 无 surface 槽：玻璃挂 modifier，覆盖整个面板（含标题/操作头部区）。
        modifier = modifier.then(glassBottomSheetSurface(backdrop, cornerRadius, material, stroke, containerColor)),
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = Color.Transparent,
        enableWindowDim = enableWindowDim,
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        dragHandleColor = dragHandleColor,
        allowDismiss = allowDismiss,
        enableNestedScroll = enableNestedScroll,
        renderInRootScaffold = renderInRootScaffold,
        content = content,
    )
}
