// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** [GlassPopup] 的默认值。 */
object GlassPopupDefaults {

    /** 弹出窗口允许的最小宽度：`miuix_popup_window_min_width`。 */
    val MinWidth: Dp = 200.dp

    /** 弹出窗口允许的最大宽度：`miuix_popup_window_max_width`。 */
    val MaxWidth: Dp = 288.dp

    /** 列表开始滚动前的最大高度：`miuix_popup_window_max_height`。 */
    val MaxHeight: Dp = 416.dp

    /** 弹出窗口与窗口边缘保持的间距：`miuix_popup_window_safe_margin`。 */
    val SafeMargin: Dp = 12.dp

    /** 第一个元素上方和最后一个元素下方的内边距：`miuix_popup_window_vertical_padding`。 */
    val ContentPaddingVertical: Dp = 8.dp

    /** 锚定菜单的圆角半径：`miuix_theme_radius_demi_big`。 */
    val CornerRadius: Dp = 24.dp

    /** 列表下拉菜单的圆角半径：`miuix_theme_radius_demi_big`。 */
    val DropdownCornerRadius: Dp = 24.dp

    /** 菜单项允许的最小高度：`miuix_appcompat_popup_menu_item_min_height`。 */
    val ItemMinHeight: Dp = 44.dp

    /** 菜单项内部的水平内边距：`miuix_appcompat_popup_menu_item_padding_left`。 */
    val ItemPaddingHorizontal: Dp = 20.dp

    /** 菜单项标签上下的内边距：`miuix_appcompat_popup_menu_item_padding_vertical`。 */
    val ItemPaddingVertical: Dp = 10.dp

    /** 行首图标的尺寸：`miuix_appcompat_menu_icon_width`。 */
    val ItemIconSize: Dp = 24.dp

    /** 行标签与其旁边元素之间的间距。 */
    val ItemIconGap: Dp = 8.dp

    /**
     * 两组行之间分隔线的水平内缩：`popup_menu_divider_line` 自身的内缩。
     * 该线位于一个 16dp 的区块内，正是这个区块把两组行分开。
     */
    val DividerPaddingHorizontal: Dp = 20.dp

    /** 分隔线上下的间距，即其 16dp 区块扣除内缩后的剩余部分。 */
    val DividerPaddingVertical: Dp = 7.75.dp

    /** 分隔线的粗细：区块扣除内缩后剩下的 0.5dp。 */
    val DividerThickness: Dp = 0.5.dp

    /** 当前行勾选标记的尺寸。 */
    val ItemTickSize: Dp = 24.dp

    /** 通向下一级菜单的行上箭头图标的尺寸。 */
    val ItemArrowSize: Dp = 16.dp

    /** 按压行背后色块相对面板自身边缘内缩的距离。 */
    val ItemPressInset: Dp = 7.dp

    /** 按压行背后色块的圆角半径。 */
    val ItemPressRadius: Dp = 15.dp

    /** 按压行背后色块的填充色。 */
    @Composable
    fun itemPressedColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }

    /** 第二个菜单挡在本菜单之前时蒙上的一层罩色：`hyperMenuMask`，浅色主题下为 0.4 的白色，深色主题下为 0.4 的黑色。它是向页面底色的轻度罩染（浅色主题提亮、深色主题压暗）而非模态压暗，因为后面的菜单只是失焦，并未离开。 */
    @Composable
    fun maskColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.Black.copy(alpha = 0.4f)
    } else {
        Color.White.copy(alpha = 0.4f)
    }

    /** 菜单表面的材质构成，使用预置 token 作为默认值。 */
    @Composable
    fun visuals(
        style: GlassStyle = GlassDefaults.Style,
        alpha: Float = 1f,
        stroke: GlassStroke? = stroke(),
        shadow: GlassShadow? = GlassShadows.Regular,
        material: GlassMaterial? = material(),
        containerColor: Color = containerColor(),
    ): GlassPopupVisuals = GlassPopupVisuals(style, alpha, stroke, shadow, material, containerColor)

    /** 弹出层的轮廓形状。 */
    @Composable
    fun shape(cornerRadius: Dp = CornerRadius): GlassShape = GlassShape(cornerRadius)

    /** 弹出层自身的主体：`popupview-glass`，带 60dp 的蒙版模糊。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.popupViewGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** `popupview-glass` 声明的小号泛光描边。 */
    @Composable
    fun stroke(): GlassStroke = GlassStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassStrokes.SmallLight,
        dark = GlassStrokes.SmallDark,
    )

    /** 弹出层不带材质时垫在它下面的填充色：`miuix_default_color_surface_highest`，浅色主题为白色，深色主题为 `#2C2C2C`。 */
    @Composable
    fun containerColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF2C2C2C)
    } else {
        Color.White
    }
}

/**
 * 在所属控件的角落展开的玻璃菜单。
 *
 * 三种开场动画中的第一种，普通控件获得的就是这一种。面板有两条边从不动，另外两条边做插值，因此面板先又宽又扁，随后才放出高度。
 * 把它放在页面根部的 [BoxScope] 中，而不是放在控件旁边——锚点所在的容器会裁剪掉面板。收起动画完成前请保持此调用处于组合状态。
 *
 * @param show 弹出菜单是否打开。
 * @param onDismissRequest 点击外部或按返回键请求关闭时调用。
 * @param anchorBounds 控件的边界，位于此组合函数自身的坐标空间中。当锚点属于被缩放的主菜单时请使用静止边界，否则本菜单会被拖离原位。
 * @param backdrop 玻璃背后的 [Backdrop]，采样时须排除弹窗自身。`null` 时使用不透明填充，同时保留已配置的泛光描边和 Compose 阴影。
 * @param modifier 应用于面板的修饰符。
 * @param sizing 面板的尺寸限制与窗口边距。
 * @param visuals 面板表面的材质构成。
 * @param cornerRadius 面板最终停留的圆角半径。
 * @param secondary 此菜单是否从另一个菜单的行中打开。委托给 [GlassSecondaryPopup] 的几何；直接使用该组件则可通过 `materialAnchor` 继承主弹出层的展开外观。
 * @param contentPadding 各项周围的内边距。
 * @param content 各项。
 */
@Composable
fun BoxScope.GlassPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    sizing: GlassPopupSizing = GlassPopupSizing(),
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    cornerRadius: Dp = GlassPopupDefaults.CornerRadius,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(vertical = GlassPopupDefaults.ContentPaddingVertical),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (secondary) {
        GlassSecondaryPopup(
            show = show,
            onDismissRequest = onDismissRequest,
            anchorBounds = anchorBounds,
            backdrop = backdrop,
            modifier = modifier,
            sizing = sizing,
            visuals = visuals,
            cornerRadius = cornerRadius,
            contentPadding = contentPadding,
            content = content,
        )
        return
    }
    val progress = animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = GlassMotion.popupMorph(),
        label = "glassPopupBounds",
    )
    val fade by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = GlassMotion.popupMorphFade(show),
        label = "glassPopupFade",
    )
    val sharpness by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = GlassMotion.popupMorphBlur(),
        label = "glassPopupBlur",
    )
    val active by remember(show, progress) {
        derivedStateOf { progress.value > 0.001f || show }
    }
    rememberGlassPopupBackProgress(
        show = show,
        active = active,
        enabled = show,
        retainWhenInactive = false,
        resetSpec = GlassMotion.popupMorph(),
        onDismissRequest = onDismissRequest,
    )
    if (!active) return

    val startRadius = GlassMotion.POPUP_START_CORNER_DP.dp
    val layoutDirection = LocalLayoutDirection.current
    GlassPopupSurface(
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        sizing = sizing,
        visuals = visuals,
        contentPadding = contentPadding,
        panelLayer = {},
        overlay = {},
        frame = { end, page ->
            val settled = placeGlassPopup(anchorBounds, end, sizing.safeMargin.toPx(), page, layoutDirection)
            directionFrame(settled, end, progress.value, startRadius, cornerRadius, layoutDirection)
        },
        contentLayer = { _, _ ->
            val t = progress.value.coerceAtLeast(0f)
            val scale = GlassMotion.POPUP_START_WIDTH + (1f - GlassMotion.POPUP_START_WIDTH) * t
            scaleX = scale
            scaleY = scale
            val blur = (1f - sharpness) * GlassMotion.POPUP_MORPH_BLUR_PX
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
            alpha = fade
        },
        scrollable = true,
        content = content,
    )
}

/** 开场过程中面板的矩形，其中两条边保持不动。 */
private fun directionFrame(
    placement: GlassPopupPlacement,
    end: Size,
    progress: Float,
    startRadius: Dp,
    endRadius: Dp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): GlassPopupFrame {
    // 保留弹簧的过冲，让轮廓与各行一起回弹。
    val t = progress.coerceAtLeast(0f)
    val width = end.width * (GlassMotion.POPUP_START_WIDTH + (1f - GlassMotion.POPUP_START_WIDTH) * t)
    val endRatio = if (end.width > 0f) end.height / end.width else 1f
    val ratio = GlassMotion.POPUP_START_RATIO + (endRatio - GlassMotion.POPUP_START_RATIO) * t
    val height = width * ratio
    val left = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
        placement.rect.right - width
    } else {
        placement.rect.left
    }
    val top = if (placement.alignTop) placement.rect.top else placement.rect.bottom - height
    return GlassPopupFrame(
        rect = Rect(left, top, left + width, top + height),
        cornerRadius = startRadius + (endRadius - startRadius) * t,
    )
}

/**
 * [GlassPopup] 的一行。
 *
 * 必填的只有 [text] 与 [onClick]：图标、副标题、勾选标记和箭头图标均可单独或组合出现。
 *
 * @param text 标签文本。
 * @param onClick 点击该行时调用。
 * @param modifier 应用于该行的修饰符。
 * @param icon 标签前的可选图标。
 * @param iconSize [icon] 的尺寸。默认为 MIUI 菜单图标宽度。
 * @param iconGap [icon] 与标签之间的间距。默认为 MIUI 菜单图标间距。
 * @param summary 标签下方的可选第二行文本。通常是该行当前的设置值。
 * @param enabled 该行是否响应点击。
 * @param selected 该行是否为当前选项，选中时在其末尾显示勾选标记。
 * @param showArrow 该行是否通向下一级菜单，是则在其末尾显示箭头图标。
 * @param arrowRotation 箭头图标的旋转角度（度）。菜单打开的行按级联弹出菜单的箭头旋转量旋转它。
 * @param contentColor 标签与图标的颜色。
 * @param summaryColor 第二行文本的颜色。
 * @param selectedContentColor 选中行的标签及其勾选标记的颜色。
 * @param pressedColor 手指按住该行时其背后色块的填充色。
 * @param textMaxLines 标签文本允许换行的最大行数，超出则以省略号截断。
 */
@Composable
fun GlassPopupItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSize: Dp = GlassPopupDefaults.ItemIconSize,
    iconGap: Dp = GlassPopupDefaults.ItemIconGap,
    summary: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    showArrow: Boolean = false,
    arrowRotation: () -> Float = { 0f },
    contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
    selectedContentColor: Color = MiuixTheme.colorScheme.primary,
    pressedColor: Color = GlassPopupDefaults.itemPressedColor(),
    textMaxLines: Int = 1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressFill = animateColorAsState(
        targetValue = if (pressed) pressedColor else Color.Transparent,
        animationSpec = if (pressed) snap() else GlassMotion.popupPressExit(),
        label = "glassPopupItemPress",
    )
    val color = when {
        !enabled -> contentColor.copy(alpha = 0.4f)
        selected -> selectedContentColor
        else -> contentColor
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GlassPopupDefaults.ItemMinHeight)
            .padding(horizontal = GlassPopupDefaults.ItemPressInset)
            .drawBehind {
                drawRoundRect(
                    color = pressFill.value,
                    cornerRadius = CornerRadius(GlassPopupDefaults.ItemPressRadius.toPx()),
                )
            }
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(
                horizontal = GlassPopupDefaults.ItemPaddingHorizontal - GlassPopupDefaults.ItemPressInset,
                vertical = GlassPopupDefaults.ItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = iconGap)
                    .size(iconSize),
                tint = color,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = textMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = summaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = GlassPopupDefaults.ItemIconGap)
                    .size(GlassPopupDefaults.ItemTickSize),
                tint = selectedContentColor,
            )
        }
        if (showArrow) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = GlassPopupDefaults.ItemIconGap)
                    .size(GlassPopupDefaults.ItemArrowSize)
                    .graphicsLayer { rotationZ = arrowRotation() },
                tint = summaryColor,
            )
        }
    }
}
