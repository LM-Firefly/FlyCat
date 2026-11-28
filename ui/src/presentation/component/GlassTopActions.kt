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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.glassSurfaceBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** FlyCat 顶栏 chrome 尺寸：42dp 容器承载 22dp 图标。 */
object GlassTopActionDefaults {
    val ContainerSize = 42.dp
    val IconSize = 22.dp
    /** 药丸形状内每个图标周围的热区扩展边距。 */
    val PillIconHitInset = 8.dp
    val PillIconGap = 4.dp
    val PillHorizontalPadding = 10.dp
}

/** 磨砂玻璃容器（顶栏胶囊/圆钮、弹层标题带）内的一个可点击操作。 */
data class GlassTopAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val modifier: Modifier = Modifier,
    val iconModifier: Modifier = Modifier,
    /** 图标颜色覆盖；[Color.Unspecified] 表示保持 chrome 自身的 on-surface 颜色。 */
    val tint: Color = Color.Unspecified,
    /** 按钮填充色；[Color.Unspecified] 使用顶栏按钮默认填充色。 */
    val fallbackFill: Color = Color.Unspecified,
    /** 由此按钮托管的菜单。在此处声明可以将菜单保持在触发它的操作旁边，这样就不需要猜测哪个覆盖层插槽属于哪个图标。 */
    val popup: (@Composable () -> Unit)? = null,
    /** [popup] 锚定的位置。按钮会自动绑定到该锚点，因此调用方无需再手动在 [modifier] 上设置 `glassPopupAnchor` 和在 [iconModifier] 上设置 `glassPopupAnchorContent`。 */
    val anchor: GlassPopupAnchor? = null,
    /**
     * 抑制弹窗变换端点复用按钮表面（fork 特化修复的移植）。
     * 当按钮采样的是受限的兄弟纯色录制层而非全页背景（如嵌在 GlassScaffold 录制子树内、为规避自采样崩溃而改用按钮大小纯色层的列表头按钮）时，
     * 库 GlassButtonSurface 发布的表面会把这块小背景铺满生长中的面板，在按钮原位留下一块不透明方块；置位后按钮不发布表面，面板改以纯玻璃形态生长。
     */
    val suppressTransformSurface: Boolean = false,
    /** 是否可用。禁用态保留外观，仅去掉点击与图标对比度（与 [SheetHeaderIconButton] 同约定）。 */
    val enabled: Boolean = true,
)

/**
 * 锚定的顶部栏操作在弹出层折叠为按钮后持续显示的字形。
 * 共享此字形是为了确保锚点图标传递给 [GlassPopupHost] 的内容，在尺寸和色调上不会与其菜单自身头部重绘的字形产生偏差。
 */
@Composable
fun GlassTopActionAnchorIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(GlassTopActionDefaults.IconSize),
        tint = MiuixTheme.colorScheme.onSurface,
    )
}

/** 将此按钮的边界绑定到其自身的 [GlassTopAction.anchor]，如果它声明了的话。 */
private val GlassTopAction.anchorModifier: Modifier
    get() = anchor?.let {
        Modifier.glassPopupAnchor(anchor = it, cornerRadius = GlassTopActionDefaults.IconSize / 2, floating = true)
    } ?: Modifier

/** 标记此按钮拥有菜单时 [GlassPopupHost] 从中变形展开的折叠字形（内容锚点）。 */
private val GlassTopAction.anchorIconModifier: Modifier
    get() = anchor?.let { Modifier.glassPopupAnchorContent(it) } ?: Modifier

/**
 * FlyCat 风格的磨砂顶栏操作：两个或更多图标共享一个胶囊形底座，单个图标则置于独立圆圈中。
 * 容器在半透明表面色调下对 [backdrop] 进行磨砂处理，并带有加法边缘光轮廓——与浮动底部栏相同的材质配方——在没有实时 backdrop 或 RuntimeShader 时则回退为纯色胶囊。
 * 按压反馈为玻璃质感的轻微缩小，无水波纹效果。
 */
@Composable
fun GlassTopActionPill(actions: List<GlassTopAction>, backdrop: Backdrop?, modifier: Modifier = Modifier) {
    if (actions.size < 2) {
        Row(modifier) { actions.forEach { action -> GlassTopIconButton(action, backdrop) } }
        return
    }
    val fallbackColor = MiuixTheme.colorScheme.surfaceContainer
    /** body 录制层带不透明底色，空页面上会将胶囊染成纯白；壁纸层才是液态玻璃观感。 */
    val resolvedBackdrop = LocalGlassBackdrop.current ?: backdrop
    GlassFloatingToolbar(
        modifier = modifier.height(GlassTopActionDefaults.ContainerSize),
        backdrop = resolvedBackdrop,
        cornerRadius = GlassTopActionDefaults.ContainerSize / 2,
        outSidePadding = PaddingValues(0.dp),
        material = GlassTopAppBarDefaults.buttonMaterial(),
        stroke = GlassTopAppBarDefaults.buttonStroke(),
        shadow = null,
        fill = fallbackColor,
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = GlassTopActionDefaults.PillHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassTopActionDefaults.PillIconGap),
        ) { actions.forEach { action -> PillActionSlot(action) { PillActionIcon(action) } } }
    }
}

/** 药丸（pill）的一个按钮及其所承载的菜单。菜单绘制在其自身按钮所在的同一个插槽中，因此每个操作携带自己的菜单，而不是由药丸维护并列的索引列表。 */
@Composable
private fun PillActionSlot(action: GlassTopAction, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Box(modifier) {
        icon()
        action.popup?.let { popup -> Box(Modifier.matchParentSize()) { popup() } }
    }
}

/**
 * 单个磨砂圆形按钮，是 [GlassTopActionPill] 外观的一图标变体。
 * 使用常驻中性玻璃表面，并由库 FAB 提供按压反馈。
 * 弹窗锚点经 [GlassTopAction.anchor] 透传，由玻璃表面发布折叠端点材质。
 */
@Composable
fun GlassTopIconButton(action: GlassTopAction, backdrop: Backdrop?, modifier: Modifier = Modifier) {
    val anchorModifier = action.anchor?.let {
        Modifier.glassPopupAnchor(anchor = it, cornerRadius = GlassTopActionDefaults.ContainerSize / 2, floating = true)
    } ?: Modifier
    val suppressTransformSurface = action.suppressTransformSurface
    PillActionSlot(action = action, modifier = if (suppressTransformSurface) anchorModifier else Modifier) {
        GlassFloatingActionButton(
            onClick = { if (action.enabled) action.onClick() },
            modifier = action.modifier.then(modifier).then(if (suppressTransformSurface) Modifier else anchorModifier)
                .then(if (action.enabled) Modifier else Modifier.semantics { disabled() }),
            backdrop = backdrop,
            material = GlassTopAppBarDefaults.buttonMaterial(),
            size = GlassTopActionDefaults.ContainerSize,
            fill = action.fallbackFill.takeOr(GlassTopAppBarDefaults.buttonFill()),
            stroke = GlassTopAppBarDefaults.buttonStroke(),
        ) {
            GlassTopActionIcon(action, MiuixTheme.colorScheme.onSurface)
        }
    }
}

/** 弹层侧边动作：复用常驻中性玻璃圆钮与按压反馈，并优先采样页面壁纸。 */
@Composable
fun SheetHeaderIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = Color.Unspecified, iconModifier: Modifier = Modifier) {
    GlassTopIconButton(
        action = GlassTopAction(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            enabled = enabled,
            iconModifier = iconModifier,
            tint = tint,
        ),
        backdrop = LocalGlassBackdrop.current ?: glassSurfaceBackdrop(),
    )
}

@Composable
private fun GlassTopActionIcon(action: GlassTopAction, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = action.icon,
        contentDescription = action.contentDescription,
        modifier = Modifier.size(GlassTopActionDefaults.IconSize).then(action.iconModifier).then(action.anchorIconModifier)
            .let { if (action.enabled) it else it.alpha(AppTheme.opacity.disabled) }.then(modifier),
        tint = action.tint.takeOr(tint),
    )
}

@Composable
private fun PillActionIcon(action: GlassTopAction) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "glass_top_pill_icon_press_scale",
    )
    Box(
        modifier = action.modifier.then(action.anchorModifier).height(GlassTopActionDefaults.ContainerSize)
            .width(GlassTopActionDefaults.IconSize + GlassTopActionDefaults.PillIconHitInset).clickable(
                enabled = action.enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = action.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassTopActionIcon(
            action = action,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        )
    }
}

/** 颜色未指定时返回回退色。 */
private fun Color.takeOr(fallback: Color): Color = if (this == Color.Unspecified) fallback else this
