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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupAnchorElement
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.PublishGlassAnchorSurfaces
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassAnchorSurface
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** NexioSchedule 顶栏 chrome 尺寸：42dp 容器承载 22dp 图标。 */
object GlassTopActionDefaults {
    val ContainerSize = 42.dp
    val IconSize = 22.dp
    /** 药丸形状内每个图标周围的热区扩展边距。 */
    val PillIconHitInset = 8.dp
    val PillIconGap = 14.dp
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
    /** 由此按钮托管的菜单。在此处声明可以将菜单保持在触发它的操作旁边，这样就不需要猜测哪个覆盖层插槽属于哪个图标。 */
    val popup: (@Composable () -> Unit)? = null,
    /** [popup] 锚定的位置。按钮会自动绑定到该锚点，因此调用方无需再手动在 [modifier] 上设置 `glassPopupAnchor` 和在 [iconModifier] 上设置 `glassPopupAnchorContent`。 */
    val anchor: GlassPopupAnchor? = null,
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

/** 此操作通过自身修饰符公开的弹出锚点（如有）。 */
private val GlassTopAction.popupAnchor: GlassPopupAnchor?
    get() = anchor ?: modifier.foldIn<GlassPopupAnchor?>(null) { found, element ->
        if (element is GlassPopupAnchorElement) element.anchor else found
    }

/** 将此按钮的边界绑定到其自身的 [GlassTopAction.anchor]，如果它声明了的话。 */
private val GlassTopAction.anchorModifier: Modifier
    get() = anchor?.let {
        Modifier.glassPopupAnchor(
            anchor = it,
            cornerRadius = GlassTopActionDefaults.IconSize / 2,
            floating = true,
        )
    } ?: Modifier

/** 标记此按钮拥有菜单时 [GlassPopupHost] 从中变形展开的折叠字形（内容锚点）。 */
private val GlassTopAction.anchorIconModifier: Modifier
    get() = anchor?.let { Modifier.glassPopupAnchorContent(it) } ?: Modifier

/**
 * NexioSchedule 风格的磨砂顶栏操作：两个或更多图标共享一个胶囊形底座，单个图标则各自站立于独立圆圈中（与参考顶栏的胶囊+圆形装饰一致）。
 * 容器在半透明表面色调下对 [backdrop] 进行磨砂处理，并带有加法边缘光轮廓——与浮动底部栏相同的材质配方——在没有实时 backdrop 或 RuntimeShader 时则回退为纯色胶囊。
 * 按压反馈为玻璃质感的轻微缩小，无水波纹效果。
 */
@Composable
fun GlassTopActionPill(actions: List<GlassTopAction>, backdrop: LayerBackdrop?, modifier: Modifier = Modifier) {
    if (actions.size < 2) {
        Row(modifier) {
            actions.forEach { action ->
                // GlassTopIconButton 自身已渲染 action.popup，此处不能再包 PillActionSlot（否则弹窗双重组合）。
                GlassTopIconButton(action, backdrop)
            }
        }
        return
    }
    val glass = rememberGlassTopActionStyle()
    /** 每次重组时操作列表都会被重新构建（其修饰符和 Lambda 都是新的实例），因此锚点列表不能作为可用的效果键。 */
    val resolvedPopupAnchors = actions.mapNotNull { it.popupAnchor }.distinct()
    val containerMaterial = GlassTopAppBarDefaults.buttonMaterial()
    val containerStroke = GlassTopAppBarDefaults.buttonStroke()
    val popupSurface = rememberGlassAnchorSurface(backdrop, containerMaterial, containerStroke, glass.fallbackColor)
    val collapsedSurfaceProgress = remember { mutableFloatStateOf(0f) }
    PublishGlassAnchorSurfaces(resolvedPopupAnchors, popupSurface, surfaceProgress = collapsedSurfaceProgress, surfaceFloating = false)
    val pillShape = remember { Capsule() }
    Box(
        modifier = modifier.height(GlassTopActionDefaults.ContainerSize).glassPanel(
            backdrop = backdrop,
            shape = GlassShape(GlassTopActionDefaults.ContainerSize / 2),
            style = GlassDefaults.Style,
            material = containerMaterial,
            stroke = containerStroke,
            shadow = null,
            shading = false,
            fallback = Modifier.clip(pillShape).background(glass.fallbackColor),
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GlassTopActionDefaults.PillHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassTopActionDefaults.PillIconGap),
        ) {
            actions.forEach { action ->
                PillActionSlot(action) { PillActionIcon(action, glass) }
            }
        }
    }
}

/** 药丸（pill）的一个按钮及其所承载的菜单。菜单绘制在其自身按钮所在的同一个插槽中，因此每个操作携带自己的菜单，而不是由药丸维护并列的索引列表。 */
@Composable
private fun PillActionSlot(action: GlassTopAction, icon: @Composable () -> Unit) {
    Box {
        icon()
        action.popup?.let { popup -> Box(Modifier.matchParentSize()) { popup() } }
    }
}

/**
 * 单个磨砂圆形按钮，是 [GlassTopActionPill] 外观的一图标变体。
 * 委托库 [GlassIconButton]：在顶栏槽位内继承顶栏的背景与材质，描边随滚动渐显（与色带同步材质化）；
 * 在顶栏之外（如底部弹层标题）则回退到自身材质并常显。弹窗锚点经 [GlassTopAction.anchor] 透传，由库按钮发布折叠端点材质。
 */
@Composable
fun GlassTopIconButton(action: GlassTopAction, backdrop: LayerBackdrop?, modifier: Modifier = Modifier) {
    // 弹窗与按钮同槽渲染（同 PillActionSlot 语义）：独立圆钮路径此前漏渲染 action.popup，导致挂在单钮上的弹窗（代理排序/日志过滤）永不组合、点击无反应。
    PillActionSlot(action) {
        // 圆钮的锚点起始圆角须等于 42dp 圆形半径，否则弹窗折叠端点渲染成圆角方形而非圆；库 GlassButtonSurface 识别该修饰符并发布折叠端点材质。
        val anchorModifier = action.popupAnchor?.let {
            Modifier.glassPopupAnchor(anchor = it, cornerRadius = GlassTopActionDefaults.ContainerSize / 2, floating = true)
        } ?: Modifier
        GlassIconButton(
            onClick = action.onClick,
            // [GlassTopAction.modifier] 属于该 action，必须保留（anchor/inset 覆盖）。
            modifier = action.modifier.then(modifier).then(anchorModifier),
            backdrop = backdrop,
            size = GlassTopActionDefaults.ContainerSize,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
                modifier = Modifier.size(GlassTopActionDefaults.IconSize).then(action.iconModifier).then(action.anchorIconModifier),
                tint = action.tint.takeOr(MiuixTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
private fun PillActionIcon(action: GlassTopAction, glass: GlassTopActionStyle) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "glass_top_pill_icon_press_scale",
    )
    Box(
        modifier = action.modifier.then(action.anchorModifier).height(GlassTopActionDefaults.ContainerSize).width(GlassTopActionDefaults.IconSize + GlassTopActionDefaults.PillIconHitInset)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = action.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
            modifier = Modifier.size(GlassTopActionDefaults.IconSize).then(action.iconModifier).then(action.anchorIconModifier).graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
            tint = action.tint.takeOr(glass.iconTint),
        )
    }
}

/** 返回回退色，除非此颜色带有明确的色调。 */
private fun Color.takeOr(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

/** 针对药丸形和圆形容器的共享材质配方，按主题解析。 */
internal class GlassTopActionStyle(
    val fallbackColor: Color,
    val tint: Color,
    val iconTint: Color,
)

@Composable
internal fun rememberGlassTopActionStyle(): GlassTopActionStyle {
    val colorScheme = MiuixTheme.colorScheme
    val isLightTheme = !isDarkTheme()
    val opacity = AppTheme.opacity
    val density = LocalDensity.current
    val surfaceContainer = colorScheme.surfaceContainer
    return remember(isLightTheme, opacity, surfaceContainer, density) {
        GlassTopActionStyle(
            fallbackColor = surfaceContainer,
            // 顶部栏自带的渐进模糊效果已经让后面的页面变得模糊不清，因此其色调比底部栏略微深一些，以确保界面元素清晰可辨。
            tint = surfaceContainer.copy(alpha = if (isLightTheme) 0.62f else 0.72f),
            iconTint = colorScheme.onSurface,
        )
    }
}
