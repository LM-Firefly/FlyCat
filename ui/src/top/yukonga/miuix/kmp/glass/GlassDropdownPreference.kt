// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout

/**
 * 标准 Miuix 偏好设置行，搭配由 Scaffold 承载的 [GlassDropdownPopup]。
 *
 * 组件负责展开、锚点测量与关闭；调用方负责选择。
 * [backdrop] 必须排除弹出层宿主，以免材质采样到自身。
 *
 * @param onSelectedIndexChange 以选中项的索引回调。
 * @param items 可选项列表。空列表会禁用该偏好设置。
 * @param icons 每个选项的可选前置图标，按索引与 [items] 对齐。
 * @param itemEnabled 每个选项的可选可用状态，按索引与 [items] 对齐。禁用的选项置灰并忽略
 *   点击，而行本身仍可使用。
 * @param itemIconSize 选项前置图标的尺寸。
 * @param itemIconGap 选项前置图标与其标签之间的间距。
 * @param selectedIndex 当前选中项的索引。
 * @param title 偏好设置标题，使用标准 [BasicComponent] 排版。
 * @param backdrop 干净的页面录制；null 时使用弹出层的不透明回退色。
 * @param modifier 应用于偏好设置行的修饰符。
 * @param enabled 该行是否能打开弹出层。
 * @param showValue 是否在箭头旁显示选中的选项。
 * @param renderInRootScaffold 是否由最外层 Scaffold 承载弹出层。
 * @param titleColor 启用与禁用状态的标题颜色。
 * @param summary 标题下方的可选辅助文本。
 * @param summaryColor 启用与禁用状态的辅助文本颜色。
 * @param startAction 可选的前置内容。
 * @param bottomAction 行下方的可选内容。
 * @param insideMargin 行内边距，与标准偏好设置一致。
 * @param sizing 弹出层尺寸限制与窗口边距。
 * @param visuals 弹出层的材质、描边、阴影与回退色。
 * @param onExpandedChange 弹出层打开或关闭时调用。
 */
@Composable
fun GlassDropdownPreference(
    onSelectedIndexChange: (Int) -> Unit,
    items: List<String>,
    selectedIndex: Int,
    title: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    itemEnabled: List<Boolean>? = null,
    itemIconSize: Dp = GlassPopupDefaults.ItemIconSize,
    itemIconGap: Dp = GlassPopupDefaults.ItemIconGap,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val mounted = remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val actualEnabled = enabled && items.isNotEmpty()
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { next ->
            if (expanded != next) {
                expanded = next
                if (next) mounted.value = true
                currentOnExpandedChange?.invoke(next)
            }
        }
    }
    LaunchedEffect(actualEnabled) {
        if (!actualEnabled) setExpanded(false)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInRoot() },
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(
                modifier = Modifier.glassPopupAnchorValue(anchor),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val value = items.getOrNull(selectedIndex)
                if (showValue && !value.isNullOrEmpty()) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                    )
                }
                DropdownArrowEndAction(actionColor = actionColor)
            }
        },
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        onClick = {
            setExpanded(!expanded)
            if (expanded) hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        role = Role.DropdownList,
        holdDownState = mounted.value,
        enabled = actualEnabled,
    )

    if (!mounted.value) return

    PopupLayout(
        visible = mounted,
        enableWindowDim = false,
        enableBackHandler = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        var hostOrigin by remember { mutableStateOf(Offset.Zero) }
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInRoot() }) {
            GlassDropdownPopupContent(
                show = expanded && actualEnabled,
                onDismissRequest = { setExpanded(false) },
                anchorBounds = rowBounds,
                backdrop = backdrop,
                anchor = anchor,
                sizing = sizing,
                visuals = visuals,
                coordinateOrigin = hostOrigin,
                onDismissFinished = { mounted.value = false },
            ) {
                items.forEachIndexed { index, item ->
                    GlassPopupItem(
                        text = item,
                        icon = icons?.getOrNull(index),
                        iconSize = itemIconSize,
                        iconGap = itemIconGap,
                        selected = selectedIndex == index,
                        enabled = itemEnabled?.getOrNull(index) ?: true,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            setExpanded(false)
                            onSelectedIndexChange(index)
                        },
                    )
                }
            }
        }
    }
}
