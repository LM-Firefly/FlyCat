/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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
 */

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.theme.glassSurfaceBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals
import top.yukonga.miuix.kmp.glass.GlassSecondaryPopup
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.rememberSubmenuArrowRotation
import top.yukonga.miuix.kmp.layout.CascadingPopupDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout

/** 固定玻璃菜单共享的面板尺寸 */
val GlassPopupMenuSizing: GlassPopupSizing = GlassPopupSizing(
    minWidth = 188.dp,
    maxWidth = 188.dp,
)

object GlassPopupMenuDefaults {
    val ItemIconSize: Dp = 18.dp
    /** 故意偏离 [UiDp] 刻度：以 MIUI 菜单为参照测量，而非间距步长。 */
    val ItemIconGap: Dp = 15.dp
}

/**
 * 托管一个锚定的玻璃弹出层，并在其折叠动画完成前保持挂载。
 * 新增的 entries 形态菜单请直接使用库 [top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu]（同根 BoxScope 挂载）；
 * 本组件用于自由内容（分组标签/任意行组合）或需要窗口级挂载（PopupLayout/返回手势托管）的菜单。
 * 拥有级联页面的菜单会将其作为 [submenu] 传递；宿主随后会提供页面的位置、面板的测量宽度、背景采样层以及堆叠状态，从而确保页面与面板不会产生偏移。
 * [submenuContent] 仅在页面处于打开状态时才进行组合渲染。
 */
@Composable
fun GlassPopupHost(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassPopupAnchor,
    anchorContent: @Composable () -> Unit,
    backdrop: Backdrop? = glassSurfaceBackdrop(),
    sizing: GlassPopupSizing = GlassPopupMenuSizing,
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    alignToPageEnd: Boolean = true,
    dismissOnBack: Boolean = false,
    onDismissFinished: () -> Unit = {},
    submenu: GlassSubmenuState? = null,
    submenuContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentShow by rememberUpdatedState(show)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val density = LocalDensity.current
    val popupVisible = remember { mutableStateOf(false) }
    var menuWidth by remember { mutableStateOf(0.dp) }
    LaunchedEffect(show) { if (show) popupVisible.value = true }
    PopupLayout(
        visible = popupVisible,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableWindowDim = false,
        enableBackHandler = dismissOnBack,
    ) {
        Box(Modifier.fillMaxSize()) {
            // 先以面板关闭状态进行布局，这样开场的变换动画才能真正生效。
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(show) { expanded = show }
            GlassTransformPopup(
                show = expanded,
                onDismissRequest = {
                    submenu?.close()
                    onDismissRequest()
                },
                anchor = anchor,
                backdrop = backdrop,
                anchorContent = anchorContent,
                anchorAlpha = 0f,
                sizing = sizing,
                visuals = visuals,
                stacked = submenu?.open == true,
                alignToPageEnd = alignToPageEnd,
                onMeasured = { size ->
                    menuWidth = with(density) { size.width.toDp() }
                },
                onDismissFinished = {
                    if (!currentShow) {
                        popupVisible.value = false
                        currentOnDismissFinished()
                    }
                },
            ) {
                content()
            }
            if (submenu != null) {
                GlassSecondaryPopup(
                    show = expanded && submenu.open,
                    onDismissRequest = submenu::close,
                    anchorBounds = submenu.bounds,
                    backdrop = backdrop,
                    materialAnchor = anchor,
                    sizing = GlassPopupMenuSizing.fixedWidth(menuWidth),
                    visuals = visuals,
                    content = submenuContent,
                )
            }
        }
    }
}

/**
 * 级联菜单页面的打开状态，以及页面展开所依据那一行的边界范围。
 * 每个页面一个实例：触发该页面的那一行会以自身边界调用 [openAt]，其余一切由宿主掌控。[open] 是可读的，这样触发行就可以对其箭头执行动画。
 */
@Stable
class GlassSubmenuState internal constructor() {
    var open: Boolean by mutableStateOf(false)
        private set
    internal var bounds: Rect by mutableStateOf(Rect.Zero)
        private set
    /** 打开页面，使其从报告 [rowBounds] 的行中展开。 */
    fun openAt(rowBounds: Rect) {
        bounds = rowBounds
        open = true
    }
    /** 返回父菜单而不关闭它。 */
    fun close() {
        open = false
    }
}

/** 记录一个 [GlassSubmenuState]，并在 [show] 变为 false 时折叠其页面。*/
@Composable
fun rememberGlassSubmenuState(show: Boolean): GlassSubmenuState {
    val state = remember { GlassSubmenuState() }
    LaunchedEffect(show) { if (!show) state.close() }
    return state
}

/**
 * 应用中某个锚定玻璃菜单里的一行。
 * 每个菜单都共享 [GlassPopupMenuDefaults] 的紧凑字形度量，因此本行提供这些度量，调用处只需声明不同之处。使用 [GlassPopupSubmenuItem] 表示会打开级联页面的行，使用 [GlassSubmenuPageHeader] 表示标识页面名称的行。
 */
@Composable
fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    showArrow: Boolean = false,
    arrowRotation: () -> Float = { 0f },
    textMaxLines: Int = 1,
) {
    GlassPopupItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        iconSize = GlassPopupMenuDefaults.ItemIconSize,
        iconGap = GlassPopupMenuDefaults.ItemIconGap,
        enabled = enabled,
        selected = selected,
        contentColor = contentColor,
        showArrow = showArrow,
        arrowRotation = arrowRotation,
        textMaxLines = textMaxLines,
    )
}

/** 打开级联页面的一行：[open] 为 true 时箭头旋转，并把本行的静止边界传给 [onOpen]，让页面从该行展开。 */
@Composable
fun GlassPopupSubmenuItem(
    text: String,
    anchor: GlassPopupAnchor,
    open: Boolean,
    onOpen: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    GlassMenuItem(
        text = text,
        icon = icon,
        enabled = enabled,
        showArrow = true,
        arrowRotation = submenuArrow(anchor, open),
        modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInRoot() },
        onClick = { onOpen(rowBounds) },
    )
}

/** 标识级联页面的行，绘制在页面自身各行的上方；它会返回到菜单。*/
@Composable
fun GlassSubmenuPageHeader(
    state: GlassSubmenuState,
    text: String,
    anchor: GlassPopupAnchor,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    GlassMenuItem(
        text = text,
        icon = icon,
        modifier = modifier,
        showArrow = true,
        arrowRotation = submenuArrow(anchor, state.open),
        onClick = state::close,
    )
    // 每个页面都将其命名行与其数据行分开；在此处绘制它，可以避免每个调用点都必须记住需要单独画一条分隔线。
    GlassMenuDivider()
}

/** 级联行箭头的旋转：页面展开时旋转，页面收起时转回，因为页面驱动了 [GlassPopupAnchor.secondaryBackProgress]。 */
@Composable
private fun submenuArrow(anchor: GlassPopupAnchor, open: Boolean): () -> Float {
    // 与库 GlassIconDropdownMenu 共享 rememberSubmenuArrowRotation（原两侧各一份同构实现）。
    val rotation by rememberSubmenuArrowRotation(open)
    return { rotation * (1f - anchor.secondaryBackProgress) }
}

/** 一个菜单中两个部分之间的分隔符。 */
@Composable
fun GlassMenuDivider() {
    top.yukonga.miuix.kmp.glass.GlassMenuDivider()
}

/** 一组 [GlassMenuItem] 行上方的静默标题，与 MIUI 菜单内边距保持一致。 */
@Composable
fun GlassMenuSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = GlassPopupDefaults.ItemPaddingHorizontal,
            end = GlassPopupDefaults.ItemPaddingHorizontal,
            top = 6.dp,
            bottom = 2.dp,
        ),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 共享菜单宽度，缩小为一个固定的[宽度]。 */
fun GlassPopupSizing.fixedWidth(width: Dp): GlassPopupSizing = copy(minWidth = width, maxWidth = width)
