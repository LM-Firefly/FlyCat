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
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 一个由 Scaffold 承载的对话框，使用原生对话框的模糊/混合材质以及动画遮罩层。
 *
 * 宿主负责处理尺寸、标题/摘要、内边距、打开/关闭以及预测性返回手势。所提供的 [backdrop] 必须在弹窗宿主外部记录页面及其栏位，排除对话框本身及其遮罩层。
 * 若 [backdrop] 为空或运行时着色器不可用，则使用 [containerColor]。共享此材质的操作请使用 [GlassDialogButton]。
 *
 * @param onDismissRequest 在点击外部区域或完成返回手势时调用。
 * @param show 是否显示该对话框。
 * @param backdrop 清晰的页面记录；传入 null 则使用不透明回退方案。
 * @param modifier 应用于对话框面板的 Modifier。
 * @param enableWindowDim 是否绘制并采样动画的 [dimColor]。
 * @param defaultWindowInsetsPadding 宿主是否应用默认的系统/输入法内边距。
 * @param renderInRootScaffold 是否由最外层 Scaffold 承载该对话框。
 * @param largeScreen 居中缩放/淡入显示的覆盖设置；传入 null 则自动检测窗口大小。
 * @param title 可选的由宿主渲染的标题。
 * @param titleColor 标题颜色。
 * @param summary 可选的由宿主渲染的摘要。
 * @param summaryColor 摘要颜色。
 * @param cornerRadius 可选的圆角覆盖；传入 null 则使用宿主的默认显示设置。
 * @param maxWidth 面板最大宽度。
 * @param outsideMargin 面板外部边距。
 * @param insideMargin 面板内容内边距。
 * @param material 模糊输入及分层有序的颜色层。
 * @param stroke 可选的辉光近似效果。
 * @param containerColor 不透明回退填充色。
 * @param dimColor 宿主应用动画进度之前的遮罩层颜色。
 * @param onDismissFinished 在隐藏动画完成后调用。
 * @param content 对话框内容，以锐利方式绘制在材质层之上。
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
    stroke: GlassStroke? = GlassDialogDefaults.stroke(),
    containerColor: Color = GlassDialogDefaults.containerColor(),
    dimColor: Color = GlassDialogDefaults.ScrimColor,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = show,
        // 0.9.4 overlay 无 surface 槽：玻璃挂 modifier，覆盖整个面板（含标题/摘要头部区）。
        modifier = modifier.then(glassDialogSurface(backdrop, cornerRadius ?: DialogDefaults.CornerRadius, material, stroke, containerColor)),
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
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
        content = content,
    )
}
