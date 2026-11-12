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
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/**
 * 由 Scaffold 承载的玻璃底部面板（bottom sheet），带动画遮罩（scrim）、拖拽关闭与预测性返回（predictive Back）。
 *
 * [backdrop] 必须包含页面与各栏位，但不包含面板宿主及其遮罩。
 * [backdrop] 为 `null` 或运行时着色器不可用时使用 [containerColor]。玻璃延伸到屏幕边缘，
 * 并填充向上的过滚动（overscroll）。当该间距需要随内容一起滚动时，在可滚动内容内部应用导航栏内边距。
 *
 * @param onDismissRequest 允许的外部点击、拖拽关闭或返回手势时调用。
 * @param show 是否显示该面板。
 * @param backdrop 页面背景；`null` 使用不透明填充。
 * @param modifier 应用于面板的 Modifier。
 * @param enableWindowDim 是否绘制并采样动画的 [dimColor]。
 * @param defaultWindowInsetsPadding 是否应用宿主的默认窗口内边距，包括输入法内边距。
 * @param allowDismiss 外部点击、拖拽和返回手势是否可以关闭面板。
 * @param enableNestedScroll 子项滚动是否可以把运动传递给面板。
 * @param renderInRootScaffold 是否由最外层 Scaffold 承载该面板。
 * @param title 可选的标题，位于内容上方。
 * @param startAction 可选的头部前置操作。
 * @param endAction 可选的头部后置操作。
 * @param cornerRadius 顶部圆角半径；底部两角保持直角。
 * @param sheetMaxWidth 面板最大宽度。
 * @param outsideMargin 面板外部边距。
 * @param insideMargin 面板内容内边距。
 * @param dragHandleColor 应用按压透明度之前的拖拽把手颜色。
 * @param material 模糊输入与有序颜色层。
 * @param stroke 可选的泛光描边（bloom stroke），在过滚动延伸部分上省略。
 * @param containerColor 不透明回退填充。
 * @param dimColor 宿主应用动画进度之前的遮罩颜色。
 * @param onDismissFinished 隐藏动画完成后调用。
 * @param content 面板内容。按需添加滚动、导航栏内边距与额外间距。
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
    stroke: EdgeStroke? = GlassBottomSheetDefaults.stroke(),
    containerColor: Color = GlassBottomSheetDefaults.containerColor(),
    dimColor: Color = GlassBottomSheetDefaults.ScrimColor,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        // FORK（FlyCat）：0.9.4 OverlayBottomSheet 无 `surface` 槽位：玻璃挂在 modifier 上，覆盖整个面板
        //（含标题/操作头部区）；圆角改由 glassBottomSheetSurface 的 cornerRadius 参数传入（上游由 BottomSheetSurfaceScope 提供）。
        modifier = modifier.then(glassBottomSheetSurface(backdrop, cornerRadius, material, stroke, containerColor)),
        title = title,
        startAction = startAction,
        endAction = endAction,
        // FORK（FlyCat）：玻璃已带自身回退填充，宿主背景保持透明（上游为 containerColor）。
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
        // FORK（FlyCat）：0.9.4 OverlayBottomSheet 无 `dimColor` 参数（遮罩由宿主 enableWindowDim 提供），
        // 不透传 dimColor；形参保留上游形态。
        content = content,
    )
}
