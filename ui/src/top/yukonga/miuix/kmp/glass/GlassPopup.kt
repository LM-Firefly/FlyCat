// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import top.yukonga.miuix.kmp.layout.CascadingPopupDefaults
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** [GlassPopup] 的默认值。 */
object GlassPopupDefaults {

    /** 弹出窗口的最小宽度。 */
    val MinWidth: Dp = 200.dp

    /** 弹出窗口的最大宽度。 */
    val MaxWidth: Dp = 288.dp

    /** 内容开始滚动前的最大高度。 */
    val MaxHeight: Dp = 416.dp

    /** 距窗口安全区与弹出层宿主边缘的最小边距。 */
    val SafeMargin: Dp = 12.dp

    /** 内容的垂直内边距。 */
    val ContentPaddingVertical: Dp = 8.dp

    /** 锚定菜单的圆角半径。 */
    val CornerRadius: Dp = 24.dp

    /** 列表下拉菜单的圆角半径。 */
    val DropdownCornerRadius: Dp = 24.dp

    /** 菜单项的最小高度。 */
    val ItemMinHeight: Dp = 44.dp

    /** 菜单项的水平内边距。 */
    val ItemPaddingHorizontal: Dp = 20.dp

    /** 菜单项的垂直内边距。 */
    val ItemPaddingVertical: Dp = 10.dp

    /** 行首图标的尺寸。 */
    val ItemIconSize: Dp = 24.dp

    /** 菜单项标签与相邻图标之间的间距。 */
    val ItemIconGap: Dp = 8.dp

    /** 分隔线的水平内缩。 */
    val DividerPaddingHorizontal: Dp = 20.dp

    /** 分隔线上下的内边距。 */
    val DividerPaddingVertical: Dp = 7.75.dp

    /** 分隔线的粗细。 */
    val DividerThickness: Dp = 0.5.dp

    /** 选中项勾选标记的尺寸。 */
    val ItemTickSize: Dp = 24.dp

    /** 子菜单箭头图标的尺寸。 */
    val ItemArrowSize: Dp = 16.dp

    /** 按压项背景的水平内缩。 */
    val ItemPressInset: Dp = 7.dp

    /** 按压项背景的圆角半径。 */
    val ItemPressRadius: Dp = 15.dp

    /** 按压项背景的颜色。 */
    @Composable
    fun itemPressedColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }

    /** 主菜单被次级菜单挡在后面时蒙上的罩色。 */
    @Composable
    fun maskColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.Black.copy(alpha = 0.4f)
    } else {
        Color.White.copy(alpha = 0.4f)
    }

    /** 创建弹出层的表面外观。 */
    @Composable
    fun visuals(
        style: GlassStyle = GlassDefaults.Style,
        alpha: Float = 1f,
        stroke: EdgeStroke? = stroke(),
        shadow: GlassShadow? = GlassShadows.Regular,
        material: GlassMaterial? = material(),
        containerColor: Color = containerColor(),
    ): GlassPopupVisuals = GlassPopupVisuals(style, alpha, stroke, shadow, material, containerColor)

    /** 创建弹出层的形状。 */
    @Composable
    fun shape(cornerRadius: Dp = CornerRadius): GlassShape = GlassShape(cornerRadius)

    /** 当前主题的默认弹出层材质。 */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.popupViewGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** 当前主题的默认弹出层泛光描边。 */
    @Composable
    fun stroke(): EdgeStroke = GlassEdgeStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassEdgeStrokes.SmallLight,
        dark = GlassEdgeStrokes.SmallDark,
    )

    /** 当前主题的不透明回退色。 */
    @Composable
    fun containerColor(): Color = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF2C2C2C)
    } else {
        Color.White
    }
}

/**
 * 锚定在控件旁、带边缘展开过渡的玻璃菜单。
 *
 * 把它放在页面根部的 [BoxScope] 中，以免被锚点所在的容器裁剪。收起动画完成前请保持其处于组合状态。
 *
 * @param show 弹出菜单是否打开。
 * @param onDismissRequest 点击外部或按返回键请求关闭时调用。
 * @param anchorBounds 触发区域的边界，使用窗口坐标（`boundsInWindow()`）；当锚点属于被缩放的主菜单时请使用静止边界。
 * @param backdrop 面板采样的背景。须排除弹出层自身。`null` 时使用不透明填充，同时保留已配置的泛光描边与阴影。
 * @param modifier 应用于面板的修饰符。
 * @param sizing 面板的尺寸限制与窗口边距。
 * @param visuals 面板表面外观。
 * @param cornerRadius 面板展开后的圆角半径。
 * @param secondary 是否使用 [GlassSecondaryPopup] 的几何。直接使用该组件则可通过 `materialAnchor` 共享主弹出层的外观。
 * @param contentPadding 各项周围的内边距。
 * @param content 菜单各项。
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
        sizing = sizing,
        visuals = visuals,
        contentPadding = contentPadding,
        panelLayer = {},
        overlay = {},
        frame = { end, windowBounds ->
            val settled = placeGlassPopup(anchorBounds, end, sizing.safeMargin.toPx(), windowBounds, layoutDirection)
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
        modifier = modifier,
        scrollable = true,
        content = content,
    )
}

/** 插值面板边界，同时保持两条边不动。 */
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
 * 带可选图标、副标题、选中勾选标记与子菜单箭头的菜单项。
 *
 * @param text 标签文本。
 * @param onClick 点击该行时调用。
 * @param modifier 应用于该行的修饰符。
 * @param icon 标签前的可选图标。
 * @param iconSize FORK（FlyCat）：[icon] 的尺寸。默认为 MIUI 菜单图标宽度。
 * @param iconGap FORK（FlyCat）：[icon] 与标签之间的间距。默认为 MIUI 菜单图标间距。
 * @param summary 标签下方的可选第二行文本。通常是该行当前的设置值。
 * @param enabled 该行是否响应点击。
 * @param selected 是否显示选中勾选标记。
 * @param showArrow 是否显示子菜单箭头。
 * @param arrowRotation 箭头的旋转角度（度）。打开的子菜单使用 [CascadingPopupDefaults.arrowRotation]。
 * @param contentColor 标签与图标的颜色。
 * @param summaryColor 第二行文本的颜色。
 * @param selectedContentColor 选中行的标签及其勾选标记的颜色。
 * @param pressedColor 按压时的背景颜色。
 * @param textMaxLines FORK（FlyCat）：标签文本允许换行的最大行数，超出则以省略号截断。
 */
@Composable
fun GlassPopupItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    // FORK（FlyCat）：图标尺寸与图标/标签间距可由调用方指定（详见 KDoc）。
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
    // FORK（FlyCat）：标签文本允许换行的最大行数（详见 KDoc）。
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
                // FORK（FlyCat）：图标尺寸与图标/标签间距由参数指定（上游取 ItemIconSize 与图标固有尺寸的较大值）。
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
                // FORK（FlyCat）：标签行数由 textMaxLines 指定（上游写死 1）。
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
        Icon(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = null,
            modifier = Modifier
                .padding(start = GlassPopupDefaults.ItemIconGap)
                .size(GlassPopupDefaults.ItemTickSize),
            tint = if (selected) selectedContentColor else Color.Transparent,
        )
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
