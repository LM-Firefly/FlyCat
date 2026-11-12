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

package com.github.lmfirefly.flycat.presentation.component.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.FloatingSheetHeader
import com.github.lmfirefly.flycat.presentation.component.FloatingSheetHeaderDefaults
import com.github.lmfirefly.flycat.presentation.component.SheetHeaderIconButton
import com.github.lmfirefly.flycat.presentation.component.rememberSurfaceBackdrop
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Check
import com.github.lmfirefly.flycat.presentation.icon.flycat.Close
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

object AppBottomSheetDefaults {
    /** 水平内缩只作用于内容列（标题带/按钮贴卡片边，见 [AppActionBottomSheet] 的实现注释）。 */
    val insideMargin = DpSize(UiDp.dp24, UiDp.dp16)

    @Composable fun backgroundColor(): Color = MiuixTheme.colorScheme.surface

    @Composable fun dragHandleColor(): Color = MiuixTheme.colorScheme.onSurfaceVariantActions

    @Composable
    fun actionIconTint(enabled: Boolean): Color = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = AppTheme.opacity.disabled)
    }
}

data class AppBottomSheetAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val tint: Color = Color.Unspecified,
    val onClick: () -> Unit,
)

@Composable
fun AppBottomSheetIconAction(action: AppBottomSheetAction) {
    // 与页面顶栏同款的 42dp 玻璃圆钮（无 RuntimeShader 时自动退化实色）。
    SheetHeaderIconButton(
        icon = action.icon,
        contentDescription = action.contentDescription,
        onClick = action.onClick,
        enabled = action.enabled,
        tint = action.tint,
    )
}

@Composable
fun AppBottomSheetCloseAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String = FlyTxt.Component.Button.Cancel,
) {
    AppBottomSheetIconAction(
        action = AppBottomSheetAction(
            icon = FlyCat.Close,
            contentDescription = contentDescription,
            enabled = enabled,
            onClick = onClick,
        )
    )
}

@Composable
fun AppBottomSheetConfirmAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String = FlyTxt.Component.Button.Confirm,
) {
    AppBottomSheetIconAction(
        action = AppBottomSheetAction(
            icon = FlyCat.Check,
            contentDescription = contentDescription,
            enabled = enabled,
            onClick = onClick,
        )
    )
}

@Composable
fun AppActionBottomSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    startAction: (@Composable (() -> Unit))? = null,
    endAction: (@Composable (() -> Unit))? = null,
    backgroundColor: Color = Color.Unspecified,
    enableWindowDim: Boolean = true,
    cornerRadius: androidx.compose.ui.unit.Dp = UiDp.dp18,
    sheetMaxWidth: androidx.compose.ui.unit.Dp = BottomSheetDefaults.maxWidth,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = BottomSheetDefaults.outsideMargin,
    insideMargin: DpSize = AppBottomSheetDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    dragHandleColor: Color = Color.Unspecified,
    allowDismiss: Boolean = true,
    enableNestedScroll: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onSheetTopPositioned: ((Float) -> Unit)? = null,
    material: GlassMaterial = GlassBottomSheetDefaults.material(),
    stroke: EdgeStroke? = GlassBottomSheetDefaults.stroke(),
    content: @Composable () -> Unit,
) {
    val resolvedBackgroundColor = if (backgroundColor == Color.Unspecified) { AppBottomSheetDefaults.backgroundColor() } else { backgroundColor }
    val resolvedDragHandleColor = if (dragHandleColor == Color.Unspecified) { AppBottomSheetDefaults.dragHandleColor() } else { dragHandleColor }
    // 弹层表面录制：在容器色基底上录下内容 Box，作为弹层表面的采样层。
    // 只有内容 Box 是录制子树；玻璃消费者都在其外（浮动标题带、独立弹窗窗口），满足 HyperOS 同子树约束。
    val sheetContentBackdrop = rememberSurfaceBackdrop(resolvedBackgroundColor)
    // 面板复用库 GlassBottomSheet 的玻璃管线（glassBottomSheetSurface：GlassMaterial + 泛光描边），
    // 采样页面壁纸层（非弹窗内容层）；无壁纸/无 shader 时由 containerColor 回退实色。
    val pageWallpaperBackdrop = LocalGlassBackdrop.current
    GlassBottomSheet(
        onDismissRequest = onDismissRequest,
        show = show,
        backdrop = pageWallpaperBackdrop,
        modifier = modifier,
        enableWindowDim = enableWindowDim,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        allowDismiss = allowDismiss,
        enableNestedScroll = enableNestedScroll,
        renderInRootScaffold = renderInRootScaffold,
        title = null,
        startAction = null,
        endAction = null,
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        outsideMargin = outsideMargin,
        // 库的水平内缩套住整列（拖拽条+标题行+内容），既把按钮推离卡边又让内容边距与标题带不同源（重复计算）；清零水平分量，只由下方内容列施加一次。
        insideMargin = DpSize(0.dp, insideMargin.height),
        dragHandleColor = resolvedDragHandleColor,
        material = material,
        stroke = stroke,
        containerColor = resolvedBackgroundColor,
        onDismissFinished = onDismissFinished,
        content = {
            val density = LocalDensity.current
            val view = LocalView.current
            Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                // 弹层经 Dialog 窗口渲染，窗口坐标≠屏幕坐标：须叠加窗口屏幕位置；标题带自卡片顶边(-ChromeAboveContent)起画，故卡片顶边 = 内容盒顶 - 42dp。
                val windowLocation = IntArray(2)
                view.getLocationOnScreen(windowLocation)
                val cardTopPx = windowLocation[1] + coordinates.positionInWindow().y - with(density) { FloatingSheetHeaderDefaults.ChromeAboveContent.toPx() }
                onSheetTopPositioned?.invoke(cardTopPx)
            }) {
                Box(modifier = Modifier.fillMaxWidth().layerBackdrop(sheetContentBackdrop)) {
                    // 内部控件显式续接页面壁纸层而非弹层自身录制层：防采样自身录制层（HyperOS 自引用递归崩）。
                    CompositionLocalProvider(LocalGlassBackdrop provides pageWallpaperBackdrop) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = insideMargin.width),
                        ) {
                            // 浮动标题带自卡片顶边（-ChromeAboveContent）起画并压入内容顶部，内容让位 ContentTopInset。
                            Spacer(modifier = Modifier.height(FloatingSheetHeaderDefaults.ContentTopInset))
                            content()
                        }
                    }
                }
                FloatingSheetHeader(
                    title = title,
                    sheetColor = resolvedBackgroundColor,
                    startAction = startAction,
                    endAction = endAction,
                )
            }
        },
    )
}
