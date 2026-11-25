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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.SheetHeaderIconButton
import com.github.lmfirefly.flycat.presentation.component.card.glassCardMaterial
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Check
import com.github.lmfirefly.flycat.presentation.icon.flycat.Close
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.themeCardCornerRadius
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme

object AppBottomSheetDefaults {
    /** 官方悬浮标题带与正文共用的水平内缩；12dp 与各弹窗显式取值一致，勿再叠一层卡片 screenHorizontal。 */
    val insideMargin = DpSize(UiDp.dp12, UiDp.dp16)
    @Composable fun backgroundColor(): Color = MiuixTheme.colorScheme.surface
    @Composable fun material(): GlassMaterial = glassCardMaterial()
    @Composable fun stroke(): EdgeStroke? = GlassBottomSheetDefaults.stroke()
    @Composable fun dragHandleColor(): Color = MiuixTheme.colorScheme.onSurfaceVariantActions
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
fun AppBottomSheetCloseAction(onClick: () -> Unit, enabled: Boolean = true, contentDescription: String = FlyTxt.Component.Button.Cancel) {
    AppBottomSheetIconAction(action = AppBottomSheetAction(icon = FlyCat.Close, contentDescription = contentDescription, enabled = enabled, onClick = onClick))
}

@Composable
fun AppBottomSheetConfirmAction(onClick: () -> Unit, enabled: Boolean = true, contentDescription: String = FlyTxt.Component.Button.Confirm) {
    AppBottomSheetIconAction(action = AppBottomSheetAction(icon = FlyCat.Check, contentDescription = contentDescription, enabled = enabled, tint = MiuixTheme.colorScheme.primary, onClick = onClick))
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
    cornerRadius: androidx.compose.ui.unit.Dp = themeCardCornerRadius(),
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
    material: GlassMaterial = AppBottomSheetDefaults.material(),
    stroke: EdgeStroke? = AppBottomSheetDefaults.stroke(),
    content: @Composable () -> Unit,
) {
    val resolvedBackgroundColor = if (backgroundColor == Color.Unspecified) { AppBottomSheetDefaults.backgroundColor() } else { backgroundColor }
    val resolvedDragHandleColor = if (dragHandleColor == Color.Unspecified) { AppBottomSheetDefaults.dragHandleColor() } else { dragHandleColor }
    // 面板复用库 GlassBottomSheet 的玻璃管线（glassBottomSheetSurface：GlassMaterial + 泛光描边），采样页面壁纸层（非弹窗内容层）；无壁纸/无 shader 时由 containerColor 回退实色。
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
        progressiveBlur = true,
        title = title,
        startAction = startAction?.let { action -> { CompositionLocalProvider(LocalGlassBackdrop provides pageWallpaperBackdrop) { action() } } },
        endAction = endAction?.let { action -> { CompositionLocalProvider(LocalGlassBackdrop provides pageWallpaperBackdrop) { action() } } },
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        dragHandleColor = resolvedDragHandleColor,
        material = material,
        stroke = stroke,
        containerColor = resolvedBackgroundColor,
        onDismissFinished = onDismissFinished,
        content = { headerPadding ->
            val view = LocalView.current
            Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                // 弹层经 Dialog 窗口渲染，窗口坐标≠屏幕坐标；内容盒起点即卡片顶边，叠加窗口位置后供壁纸衬层对齐。
                val windowLocation = IntArray(2)
                view.getLocationOnScreen(windowLocation)
                onSheetTopPositioned?.invoke(windowLocation[1] + coordinates.positionInWindow().y)
            }) {
                CompositionLocalProvider(LocalGlassBackdrop provides pageWallpaperBackdrop) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = headerPadding.calculateTopPadding())) { content() }
                }
            }
        },
    )
}
