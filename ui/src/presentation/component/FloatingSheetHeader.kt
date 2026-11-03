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

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.glassSurfaceBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 标题带自身的录制层（sheet 配色基底），供 [SheetHeaderIconButton] 的玻璃圆钮采样。 */
private val LocalSheetHeaderBackdrop = compositionLocalOf<LayerBackdrop?> { null }

object FloatingSheetHeaderDefaults {
    /** 固定标题带高度：有无侧边按钮时标题都垂直居中。 */
    val Height = 76.dp
    /** miuix 弹层在内容上方铺的 chrome：24dp 拖拽条 + 空标题行（6dp + 12dp）。标题带上移这么多后正好从卡片顶边开始画，拖拽条浮在带上部。 */
    val ChromeAboveContent = 42.dp
    /** 滚动内容在标题带下方的顶部留白。 */
    val ContentTopInset = Height - ChromeAboveContent
    /** 侧边按钮距卡片边缘的内缩（仅外侧）。 */
    val ActionInset = 18.dp
}

/**
 * 固定高度的弹层标题带，覆盖在弹层内容之上：顶部渐隐色带 + 76dp 标题行（居中标题、两端动作按钮、整体垂直居中）。
 * 作为弹层内容 Box 的最后一个子节点挂载；滚动内容顶部留出 [FloatingSheetHeaderDefaults.ContentTopInset]。
 * 标题带从卡片顶边开始画（`offset(-ChromeAboveContent)`），拖拽条浮在带上部。
 */
@Composable
fun BoxScope.FloatingSheetHeader(title: String, sheetColor: Color, startAction: (@Composable () -> Unit)? = null, endAction: (@Composable () -> Unit)? = null) {
    // 标题带把自身（覆盖整条 76dp 带，含上移到卡顶的部分）录进独立背景层供玻璃圆钮采样。
    // 不复用 sheet 内容录制层：后者从 chrome（42dp 拖拽条+空标题行）下方才起录，而圆钮上移跨入 chrome 区，
    // 采到的上半截落在录制层之外会渲染成空白平边——即按钮“截顶”。自录整条带则按钮全高都有材质可采。
    val bandBackdrop = key(sheetColor) { rememberLayerBackdrop { drawRect(sheetColor) } }
    CompositionLocalProvider(LocalSheetHeaderBackdrop provides bandBackdrop) {
        Box(modifier = Modifier.fillMaxWidth().height(FloatingSheetHeaderDefaults.Height).offset(y = -FloatingSheetHeaderDefaults.ChromeAboveContent).zIndex(1f).layerBackdrop(bandBackdrop).background(GlassTopAppBarDefaults.bandBrush(sheetColor)))
        Box(modifier = Modifier.fillMaxWidth().height(FloatingSheetHeaderDefaults.Height).offset(y = -FloatingSheetHeaderDefaults.ChromeAboveContent).zIndex(2f)) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = UiDp.dp56),
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (startAction != null) { Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = FloatingSheetHeaderDefaults.ActionInset)) { startAction() } }
            if (endAction != null) { Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = FloatingSheetHeaderDefaults.ActionInset)) { endAction() } }
        }
    }
}

/** 弹层侧边动作：与页面顶栏完全同款的 42dp 玻璃圆钮（边缘光 + 22dp 图标），通过 [LocalSheetHeaderBackdrop] 采样 sheet 表面，使 sheet 动作与页面顶栏读起来是同一种控件。 */
@Composable
fun SheetHeaderIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = Color.Unspecified, iconModifier: Modifier = Modifier) {
    // 禁用态保留外观，仅去掉点击与图标对比度。
    val performClick: () -> Unit = if (enabled) onClick else NoOpClick
    GlassTopIconButton(
        action = GlassTopAction(
            icon = icon,
            contentDescription = contentDescription,
            onClick = performClick,
            iconModifier = if (enabled) { iconModifier } else { iconModifier.alpha(AppTheme.opacity.disabled) },
            tint = tint,
        ),
        backdrop = LocalSheetHeaderBackdrop.current ?: glassSurfaceBackdrop(),
    )
}

/** 禁用态的点击空槽，跨重组保持零分配。 */
private val NoOpClick: () -> Unit = {}
