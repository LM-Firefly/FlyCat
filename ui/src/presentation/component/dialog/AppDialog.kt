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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.github.lmfirefly.flycat.presentation.component.rememberSurfaceBackdrop
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.glass.glassDialogSurface
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog


object AppDialogDefaults {
    @Composable fun titleColor(): Color = DialogDefaults.titleColor()

    @Composable fun summaryColor(): Color = DialogDefaults.summaryColor()

    @Composable fun backgroundColor(): Color = DialogDefaults.backgroundColor()

    val outsideMargin: DpSize
        get() = DialogDefaults.outsideMargin

    val insideMargin: DpSize
        get() = DialogDefaults.insideMargin
}

@Composable
fun AppDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    titleColor: Color = AppDialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = AppDialogDefaults.summaryColor(),
    backgroundColor: Color = AppDialogDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = AppDialogDefaults.outsideMargin,
    insideMargin: DpSize = AppDialogDefaults.insideMargin,
    cornerRadius: Dp = UiDp.dp18,
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 面板级玻璃表面：委托库 glassDialogSurface（材质单源 GlassDialogDefaults.material()，stroke=null 保持现观感；
    // 库默认带 ScrimBackdrop 压暗）。自带无 shader/无背景的实色回退。
    val pageWallpaperBackdrop = LocalGlassBackdrop.current
    OverlayDialog(
        show = show,
        modifier = modifier.then(
            glassDialogSurface(
                backdrop = pageWallpaperBackdrop,
                cornerRadius = cornerRadius,
                stroke = null,
                containerColor = backgroundColor,
            )
        ),
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
        cornerRadius = cornerRadius,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        renderInRootScaffold = renderInRootScaffold,
        content = {
            // 内容玻璃采样页面壁纸层（跨层安全，卡片同款）；不采样自身内容层（HyperOS 自采样递归崩）。
            CompositionLocalProvider(LocalGlassBackdrop provides pageWallpaperBackdrop) {
                content()
            }
        },
    )
}
