/* Copyright (c) YumeYucca 2026 - Present. SPDX-License-Identifier: AGPL-3.0-or-later */

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference as MiuixGlassDropdownPreference
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
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
 * 只有在有背景采样层且支持 RuntimeShader 时才使用上游玻璃弹窗；否则回退旧窗口弹层。
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
    // 空的下拉框没有任何可提供的内容，因此在两个分支中都被视为不可用。
    val rowEnabled = enabled && options.isNotEmpty()
    if (backdrop != null && isRuntimeShaderSupported()) {
        MiuixGlassDropdownPreference(
            items = options.map { it.label },
            icons = options.map { it.icon },
            itemEnabled = options.map { it.enabled },
            itemIconSize = GlassPopupMenuDefaults.ItemIconSize,
            itemIconGap = GlassPopupMenuDefaults.ItemIconGap,
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
    } else {
        val entry = DropdownEntry(
            items = options.mapIndexed { index, option ->
                val optionIcon: (@Composable (Modifier) -> Unit)? = option.icon?.let { icon ->
                    // 窗口弹出层传递的是一个自带尾部内边距的26dp图标单元，这会导致间距与玻璃列表不一致；应改为在此处绘制该图标字形。
                    { _: Modifier ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.padding(end = GlassPopupMenuDefaults.ItemIconGap).size(GlassPopupMenuDefaults.ItemIconSize),
                        )
                    }
                }
                DropdownItem(
                    text = option.label,
                    enabled = option.enabled,
                    selected = index == selectedIndex,
                    icon = optionIcon,
                    onClick = { onSelected(option.value) },
                )
            },
        )
        WindowDropdownPreference(
            entry = entry,
            title = title,
            summary = summary,
            modifier = rowModifier,
            enabled = rowEnabled,
            maxHeight = maxHeight,
        )
    }
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
