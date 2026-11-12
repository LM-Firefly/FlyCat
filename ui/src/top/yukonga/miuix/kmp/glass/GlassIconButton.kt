// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.drawGlassMask
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.withUnderlayBlur

/**
 * 圆形玻璃图标按钮，在 [GlassTopAppBar] 内继承材质与动效。
 *
 * @param onClick 按钮被点按时调用。
 * @param backdrop 可选的采样背景。在 [GlassTopAppBar] 内按钮继承顶栏背景；为 `null` 时使用 [fill]。
 * @param modifier 应用于按钮的修饰符。
 * @param inheritTopBarMaterial 是否继承顶栏的背景与垫底材质。为 false 时在 [backdrop] 上使用按钮自身材质，回退到顶栏背景。
 * @param surfaceAlpha 按钮颜色层与描边的不透明度乘数。
 * @param style 传递给渲染器的玻璃样式。
 * @param material 显式按钮材质。为 `null` 且 [inheritTopBarMaterial] 为 true 时继承顶栏材质，否则使用 [GlassTopAppBarDefaults.buttonMaterial]。继承的顶栏垫底材质会为显式材质贡献模糊。
 * @param size 按钮直径。
 * @param shape 按钮轮廓。
 * @param shadow 按钮下方的阴影；`null` 关闭阴影。
 * @param stroke 可选的泛光描边。
 * @param content 按钮内容。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inheritTopBarMaterial: Boolean = true,
    backdrop: Backdrop? = null,
    surfaceAlpha: Float = 1f,
    style: GlassStyle = LocalGlassTopAppBarContext.current?.style ?: GlassDefaults.Style,
    material: GlassMaterial? = null,
    size: Dp = LocalGlassTopAppBarContext.current?.size ?: GlassTopAppBarDefaults.ButtonSize,
    shape: GlassShape = LocalGlassTopAppBarContext.current?.shape ?: GlassShape(size / 2),
    fill: Color = LocalGlassTopAppBarContext.current?.fill ?: GlassTopAppBarDefaults.buttonFill(),
    stroke: EdgeStroke? = LocalGlassTopAppBarContext.current.let { if (it != null) it.stroke else GlassTopAppBarDefaults.buttonStroke() },
    shadow: GlassShadow? = LocalGlassTopAppBarContext.current.let { if (it != null) it.shadow else GlassShadows.Regular },
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val topBarContext = LocalGlassTopAppBarContext.current
    val materialContext = topBarContext.takeIf { inheritTopBarMaterial }
    val resolvedSurfaceAlpha = surfaceAlpha * (topBarContext?.alpha ?: 1f)
    val topBarRamp = topBarContext?.ramp
    GlassButtonSurface(
        backdrop = materialContext?.buttonBackdrop ?: backdrop ?: topBarContext?.buttonBackdrop,
        floating = topBarContext?.floating ?: true,
        surfaceAlpha = resolvedSurfaceAlpha,
        shadowAlpha = { resolvedSurfaceAlpha * (topBarRamp?.value ?: 1f) },
        style = style,
        material = material?.withUnderlayBlur(materialContext?.buttonUnderlayMaterial)
            ?: materialContext?.material ?: GlassTopAppBarDefaults.buttonMaterial(),
        underlayMaterial = materialContext?.buttonUnderlayMaterial,
        shape = shape,
        size = size,
        fill = fill,
        stroke = stroke,
        shadow = shadow,
        sharedProgress = topBarContext?.materialProgress,
        sharedKeepMaterial = topBarContext?.keepMaterial,
        pressed = pressed,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

/** 玻璃图标按钮共享的材质表面与按压反馈。 */
@Composable
// foldIn 只读取锚点标记；修饰符仍应用于且仅应用于一个根 Box。
@Suppress("ktlint:compose:modifier-reused-check", "ktlint:compose:state-param-check")
private fun GlassButtonSurface(
    backdrop: Backdrop?,
    floating: Boolean,
    surfaceAlpha: Float,
    shadowAlpha: () -> Float,
    style: GlassStyle,
    material: GlassMaterial,
    underlayMaterial: GlassMaterial?,
    fill: Color,
    shape: GlassShape,
    size: Dp,
    stroke: EdgeStroke?,
    shadow: GlassShadow?,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    // 保留 State 供绘制阶段读取；动画时钟由顶栏共享。
    sharedProgress: State<Float>? = null,
    sharedKeepMaterial: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val popupAnchor = modifier.foldIn<GlassPopupAnchor?>(null) { found, element ->
        if (element is GlassPopupAnchorElement) element.anchor else found
    }
    val progress = sharedProgress ?: run {
        val floatingTransition = updateTransition(targetState = floating, label = "glassTopBarButtonFloat")
        floatingTransition.animateFloat(
            transitionSpec = { GlassMotion.topBarButtonFloat() },
            label = "glassTopBarButtonMaterial",
        ) { active ->
            if (active) 1f else 0f
        }
    }
    val ownKeepMaterial by remember(floating, progress) {
        derivedStateOf { floating || progress.value > 0f }
    }
    val keepMaterial = sharedKeepMaterial ?: ownKeepMaterial
    val opacity = surfaceAlpha.coerceIn(0f, 1f)
    val anchorSurface = remember(backdrop, style, material, underlayMaterial, stroke, fill) {
        GlassAnchorSurface(backdrop, style, material, underlayMaterial, stroke, fill)
    }
    // FORK（FlyCat）：锚点发布抽为共享 PublishGlassAnchorSurface（原为内联，与消费侧逐字段同构），
    // suppressTransformSurface 策略统一收敛在发布器内。
    PublishGlassAnchorSurface(
        anchor = popupAnchor,
        surface = anchorSurface,
        surfaceProgress = progress,
        surfaceMaterialAlpha = opacity,
        surfaceFloating = floating,
    )
    val pressTransition = updateTransition(targetState = pressed, label = "glassTopBarButtonPress")
    val contentAlpha = pressTransition.animateFloat(
        transitionSpec = { if (targetState) GlassMotion.pressDown() else GlassMotion.pressUp() },
        label = "glassTopBarButtonContentAlpha",
    ) { down ->
        if (down) GlassTopAppBarDefaults.PressedContentAlpha else 1f
    }
    val overlayAlpha = pressTransition.animateFloat(
        transitionSpec = { if (targetState) GlassMotion.pressDown() else GlassMotion.pressUp() },
        label = "glassTopBarButtonOverlayAlpha",
    ) { down ->
        if (down) 1f else 0f
    }
    val pressedOverlay = GlassTopAppBarDefaults.buttonPressedOverlay()

    Box(
        modifier = modifier
            .size(size)
            .glassShadow(shape, shadow, shadowAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progress.value }
                .then(
                    if (backdrop != null) {
                        if (underlayMaterial != null) {
                            Modifier.glassOnActionBar(
                                backdrop = backdrop,
                                shape = shape,
                                style = style,
                                alpha = opacity,
                                material = material,
                                underlayMaterial = underlayMaterial,
                                stroke = stroke.takeIf { keepMaterial },
                                enabled = keepMaterial,
                            )
                        } else {
                            Modifier.glass(
                                backdrop = backdrop,
                                shape = shape,
                                style = style,
                                alpha = opacity,
                                material = material,
                                stroke = stroke.takeIf { keepMaterial },
                                enabled = keepMaterial,
                                shading = false,
                            )
                        }
                    } else {
                        Modifier
                            .then(
                                if (isRuntimeShaderSupported()) {
                                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                } else {
                                    Modifier.clip(shape)
                                },
                            )
                            .background(fill.copy(alpha = fill.alpha * opacity))
                            .drawWithContent {
                                drawContent()
                                if (stroke != null && keepMaterial) {
                                    drawEdgeStroke(shape, stroke, opacity)
                                }
                                drawGlassMask(shape)
                            }
                    },
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .drawBehind {
                    drawRect(pressedOverlay.copy(alpha = pressedOverlay.alpha * overlayAlpha.value.coerceIn(0f, 1f)))
                },
        )
        Box(
            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value.coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
