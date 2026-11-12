// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 由 Scaffold 承载的玻璃对话框，带动画遮罩（scrim）与预测性返回（predictive back）支持。
 *
 * [backdrop] 必须包含页面与各栏位，但不包含对话框宿主及其遮罩。
 * [backdrop] 为 `null` 或运行时着色器不可用时使用 [containerColor]。对话框操作请使用 [GlassDialogButton]。
 *
 * @param onDismissRequest 点击外部区域或完成返回手势时调用。
 * @param show 是否显示该对话框。
 * @param backdrop 页面背景；`null` 使用不透明填充。
 * @param modifier 应用于对话框面板的 Modifier。
 * @param enableWindowDim 是否绘制并采样动画的 [dimColor]。
 * @param defaultWindowInsetsPadding 宿主是否应用默认的系统/输入法内边距。
 * @param renderInRootScaffold 是否由最外层 Scaffold 承载该对话框。
 * @param largeScreen 居中缩放/淡入呈现的覆盖开关；null 自动检测窗口大小。
 * @param title 可选的由宿主渲染的标题。
 * @param titleColor 标题颜色。
 * @param summary 可选的由宿主渲染的摘要。
 * @param summaryColor 摘要颜色。
 * @param cornerRadius 可选的圆角覆盖；null 使用宿主的呈现默认值。
 * @param maxWidth 面板最大宽度。
 * @param outsideMargin 面板外部边距。
 * @param insideMargin 面板内容内边距。
 * @param material 模糊输入与有序颜色层。
 * @param stroke 可选的泛光描边（bloom stroke）。
 * @param containerColor 不透明回退填充。
 * @param dimColor 宿主应用动画进度之前的遮罩颜色。
 * @param onDismissFinished 隐藏动画完成后调用。
 * @param content 对话框内容，清晰地绘制在材质之上。
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    show: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enableWindowDim: Boolean = true,
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = true,
    largeScreen: Boolean? = null,
    title: String? = null,
    titleColor: Color = DialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = DialogDefaults.summaryColor(),
    cornerRadius: Dp? = null,
    maxWidth: Dp = DialogDefaults.MaxWidth,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    material: GlassMaterial = GlassDialogDefaults.material(),
    stroke: EdgeStroke? = GlassDialogDefaults.stroke(),
    containerColor: Color = GlassDialogDefaults.containerColor(),
    dimColor: Color = GlassDialogDefaults.ScrimColor,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = show,
        // FORK（FlyCat）：0.9.4 OverlayDialog 无 `surface` 槽位：玻璃挂在 modifier 上，覆盖整个面板
        //（含标题/摘要头部区）；圆角改由 glassDialogSurface 的 cornerRadius 参数传入（上游由 DialogSurfaceScope 提供）。
        modifier = modifier.then(glassDialogSurface(backdrop, cornerRadius ?: DialogDefaults.CornerRadius, material, stroke, containerColor)),
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        // FORK（FlyCat）：玻璃已带自身回退填充，宿主背景保持透明（上游为 containerColor）。
        backgroundColor = Color.Transparent,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        renderInRootScaffold = renderInRootScaffold,
        maxWidth = maxWidth,
        largeScreen = largeScreen,
        cornerRadius = cornerRadius,
        // FORK（FlyCat）：0.9.4 OverlayDialog 无 `dimColor` 参数（遮罩由宿主 enableWindowDim 提供），
        // 不透传 dimColor；形参保留上游形态。
        content = content,
    )
}
