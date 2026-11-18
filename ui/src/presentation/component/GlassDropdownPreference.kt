/* Copyright (c) YumeYucca 2026 - Present. SPDX-License-Identifier: AGPL-3.0-or-later */

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.glassSurfaceBackdrop
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference as MiuixGlassDropdownPreference
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme


/** [GlassDropdownPreference] 中的一个选项。
 * 该选项携带其代表的 [value]，因此行会选择一个模型而不是索引，调用方无需先通过 `indexOf`/`getOrNull` 将其自身的枚举转换为索引。
 */
data class GlassDropdownOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

/**
 * * 渲染为上游玻璃下拉（backdrop 为空时由组件内部实色回退）。
 * @param options 按显示顺序排列的选项。被禁用的选项会显示为灰色，且不响应点击，但该行本身仍可使用。
 * @param selected 与之值匹配的选项显示为已选中；若某个值没有任何选项携带，则所有行都保持未选中状态，而不会错误地指向某个选项。
 */
@Composable
fun <T> GlassDropdownPreference(
    options: List<GlassDropdownOption<T>>,
    selected: T?,
    title: String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    maxHeight: Dp? = null,
    glassSurface: Boolean = false,
) {
    val backdrop = glassSurfaceBackdrop()
    val rowModifier = if (glassSurface) modifier.glassDropdownRowSurface() else modifier
    val selectedIndex = options.indexOfFirst { it.value == selected }
    /** 空的下拉框没有任何可提供的内容，因此视为不可用。 */
    val rowEnabled = enabled && options.isNotEmpty()
    MiuixGlassDropdownPreference(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        title = title,
        summary = summary,
        backdrop = backdrop,
        modifier = rowModifier,
        enabled = rowEnabled,
        sizing = maxHeight?.let { GlassPopupMenuSizing.copy(maxHeight = it) } ?: GlassPopupMenuSizing,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { onSelected(it.value) }
        },
    )
}

/** 表单场景的下拉行玻璃胶囊（与 [GlassTextField] 同款：玻璃 + 边缘光），设置页卡片内的裸行不启用。 */
@Composable
private fun Modifier.glassDropdownRowSurface(): Modifier {
    val shape = RoundedCornerShape(UiDp.dp18)
    return clip(shape)
        .glassCardSurface(shape, fallbackColor = MiuixTheme.colorScheme.secondaryContainer)
        .edgeStroke(
            shape = shape,
            stroke = GlassEdgeStrokes.forTheme(isDark = isDarkTheme(), light = GlassEdgeStrokes.SmallLight, dark = GlassEdgeStrokes.SmallDark),
        )
}
