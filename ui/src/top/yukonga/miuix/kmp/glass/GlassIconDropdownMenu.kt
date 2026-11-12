// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.CompositeBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.layout.CascadingPopupDefaults

/**
 * 从既有的 [GlassIconButton] 锚点打开的玻璃菜单。
 *
 * 把本组件放在与锚点同一个根 [BoxScope] 中。带子项的条目会展开出带返回头部与分隔线的子菜单。
 * 选中态由调用方控制；条目更新同样会应用到已打开的子菜单。
 * 次级背景把 [secondaryBackdrop] 与主弹出层的录制内容合并，但排除次级自身。
 * 请把提供的场景背景放在两个弹出图层之外。
 */
// 调用方的修饰符属于主面板，而不是其全页录制图层。
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
fun BoxScope.GlassIconDropdownMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchor: GlassPopupAnchor,
    anchorContent: @Composable () -> Unit,
    entries: List<DropdownEntry>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    secondaryBackdrop: Backdrop? = backdrop,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    collapseOnSelection: Boolean = entries.size <= 1,
) {
    val density = LocalDensity.current
    var submenu by remember { mutableStateOf(false) }
    var submenuPresent by remember { mutableStateOf(false) }
    var submenuPath by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var submenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var primaryWidth by remember { mutableStateOf(sizing.minWidth) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    val groups = remember(entries) { entries.filter { it.items.isNotEmpty() } }
    val submenuEntry = submenuPath?.let { groups.getOrNull(it.first) }
    val submenuItem = submenuPath?.let { submenuEntry?.items?.getOrNull(it.second) }
    val submenuShown = show && submenu && !submenuItem?.children.isNullOrEmpty()
    // FORK（FlyCat）：箭头旋转动画改用共享 rememberSubmenuArrowRotation（原内联，与 GlassPopupHost 同构）。
    val submenuArrow = rememberSubmenuArrowRotation(submenuShown)
    val menuVisibility = animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = GlassMotion.transformBounds(show),
        label = "glassCascadeVisibility",
    )
    LaunchedEffect(show) {
        if (!show) submenu = false
    }

    val primaryBackdrop = rememberLayerBackdrop()
    // FORK（FlyCat）：上游的内联合成改用共享 CompositeBackdrop（拟上游化进 miuix-blur），行为一致（先画场景、后画主弹出层）。
    val layeredBackdrop = remember(secondaryBackdrop, primaryBackdrop) {
        secondaryBackdrop?.let { scene -> CompositeBackdrop(under = scene, over = primaryBackdrop) }
    }
    // 只录制主弹出层。在这里录制次级会让它采样到自身。
    Box(Modifier.matchParentSize().layerBackdrop(primaryBackdrop)) {
        GlassTransformPopup(
            show = show,
            onDismissRequest = {
                submenu = false
                onDismissRequest()
            },
            anchor = anchor,
            backdrop = backdrop,
            anchorContent = anchorContent,
            modifier = modifier,
            stacked = submenuShown,
            sizing = sizing,
            visuals = visuals,
            onMeasured = { primaryWidth = with(density) { it.width.toDp() } },
        ) {
            groups.forEachIndexed { groupIndex, entry ->
                entry.items.forEachIndexed { itemIndex, item ->
                    val children = item.children.orEmpty()
                    var rowBounds by remember(groupIndex, itemIndex) { mutableStateOf(Rect.Zero) }
                    GlassPopupItem(
                        text = item.text,
                        summary = item.summary,
                        enabled = entry.enabled && item.enabled,
                        selected = item.selected,
                        showArrow = children.isNotEmpty(),
                        arrowRotation = {
                            if (submenuPath?.first == groupIndex && submenuPath?.second == itemIndex) {
                                submenuArrow.value * (1f - anchor.secondaryBackProgress)
                            } else {
                                0f
                            }
                        },
                        modifier = if (children.isNotEmpty()) {
                            Modifier.onGloballyPositioned { coordinates ->
                                if (!submenuPresent) rowBounds = coordinates.boundsInWindow()
                            }
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (children.isNotEmpty()) {
                                if (show && !submenuPresent) {
                                    submenuAnchor = rowBounds
                                    submenuPath = groupIndex to itemIndex
                                    submenuPresent = true
                                    submenu = true
                                }
                            } else {
                                item.onClick?.invoke()
                                if (collapseOnSelection) {
                                    submenu = false
                                    onDismissRequest()
                                }
                            }
                        },
                    )
                }
                if (groupIndex < groups.lastIndex) {
                    GlassMenuDivider()
                }
            }
        }
    }
    // 次级菜单收起时缩回触发行；完全收起时还会向工具栏锚点移动。
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { hostOrigin = it.positionInWindow() }
            .graphicsLayer {
                val progress = menuVisibility.value.coerceIn(0f, 1f)
                val trigger = anchor.containerBounds
                val closedScale = (trigger.width / primaryWidth.toPx()).coerceIn(0f, 1f)
                val scale = closedScale + (1f - closedScale) * progress
                // 锚点以窗口坐标上报；本图层位于宿主局部坐标系。
                val centerX = trigger.center.x - hostOrigin.x
                val centerY = trigger.center.y - hostOrigin.y
                transformOrigin = TransformOrigin(
                    if (size.width > 0f) centerX / size.width else 0.5f,
                    if (size.height > 0f) centerY / size.height else 0.5f,
                )
                scaleX = scale
                scaleY = scale
                alpha = progress
            },
    ) {
        GlassSecondaryPopup(
            show = submenuShown,
            onDismissRequest = { submenu = false },
            anchorBounds = submenuAnchor,
            backdrop = layeredBackdrop,
            materialAnchor = anchor,
            sizing = sizing.copy(minWidth = primaryWidth),
            visuals = visuals,
            onDismissFinished = {
                submenuPresent = false
                submenuPath = null
            },
        ) {
            submenuItem?.let { parent ->
                GlassPopupItem(
                    text = parent.text,
                    summary = parent.summary,
                    selected = parent.selected,
                    showArrow = true,
                    arrowRotation = { submenuArrow.value * (1f - anchor.secondaryBackProgress) },
                    onClick = { submenu = false },
                )
                GlassMenuDivider()
                parent.children.orEmpty().forEach { item ->
                    GlassPopupItem(
                        text = item.text,
                        summary = item.summary,
                        enabled = submenuEntry?.enabled == true && parent.enabled && item.enabled,
                        selected = item.selected,
                        onClick = {
                            item.onClick?.invoke()
                            if (collapseOnSelection) {
                                submenu = false
                                onDismissRequest()
                            }
                        },
                    )
                }
            }
        }
    }
}

// FORK（FlyCat）：改为 internal，供应用侧 GlassPopupHost 复用（上游为 private）。
@Composable
internal fun GlassMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(
            horizontal = GlassPopupDefaults.DividerPaddingHorizontal,
            vertical = GlassPopupDefaults.DividerPaddingVertical,
        ),
        thickness = GlassPopupDefaults.DividerThickness,
    )
}
