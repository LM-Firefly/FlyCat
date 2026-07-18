/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
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
 *
 */

package com.github.yumelira.yumebox.feature.home.presentation.screen.moe

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeProxyControlState
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Repeat
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import kotlinx.coroutines.delay
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MoeSidebarDecoration(
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    blurProgress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val spacing = AppTheme.spacing
    val surface = MiuixTheme.colorScheme.surface
    val isDarkSurface = surface.luminance() < 0.5f
    val glassBase =
        if (isDarkSurface) {
            Color.Black.copy(alpha = 0.24f)
        } else {
            surface.copy(alpha = 0.13f)
        }
    val glassTint = Color.Black.copy(alpha = 0.10f)
    val glassGradientStart =
        if (isDarkSurface) {
            Color.Black.copy(alpha = 0.36f)
        } else {
            surface.copy(alpha = 0.23f)
        }
    val glassGradientEnd =
        if (isDarkSurface) {
            surface.copy(alpha = 0.18f)
        } else {
            surface.copy(alpha = 0.16f)
        }
    val clampedBlurProgress = blurProgress.coerceIn(0f, 1f)
    val blurRadiusPx = lerpFloat(30f, 52f, clampedBlurProgress)
    val blurColors =
        BlurDefaults.blurColors(
            blendColors =
                listOf(
                    BlendColorEntry(color = glassBase, mode = BlurBlendMode.SrcOver),
                    BlendColorEntry(color = glassTint, mode = BlurBlendMode.SrcOver),
                ),
            saturation = if (isDarkSurface) 1.06f else 1.02f,
            contrast = if (isDarkSurface) 1.08f else 1.10f,
            brightness = if (isDarkSurface) 0.00f else -0.05f,
        )
    val blurModifier =
        if (blurEnabled) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = blurRadiusPx,
                noiseCoefficient = 0f,
                colors = blurColors,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .then(blurModifier)
                .background(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(glassGradientStart, glassGradientEnd)
                        ),
                    shape = RectangleShape,
                )
                .padding(
                    horizontal = 0.dp,
                    vertical = spacing.space24,
                ),
        content = content,
    )
}

@Composable
internal fun MoeSidebarContent(
    topValue: String,
    bottomValue: String,
    batteryPercent: Int?,
    icons: List<MoeSidebarIconItem>,
    visibleWidth: Dp,
) {
    // Keep the rail inside the visible sidebar width; the content panel starts immediately after
    // this width, so adding horizontal decoration padding would push digits under the panel.
    val laneWidth = visibleWidth.coerceAtLeast(0.dp)
    Box(
        modifier = Modifier.fillMaxHeight().width(laneWidth),
        contentAlignment = Alignment.TopCenter,
    ) {
        MoeSidebarRail(
            topValue = topValue,
            bottomValue = bottomValue,
            batteryPercent = batteryPercent,
            icons = icons,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val MOE_LAUNCH_TEXT_SLIDE_DURATION = 450
private const val MOE_LAUNCH_TEXT_TRANSIENT_DELAY = 220L

private fun HomeProxyControlState.isMoeLaunchTransientState(): Boolean =
    this == HomeProxyControlState.Connecting || this == HomeProxyControlState.Disconnecting

@Composable
internal fun MoeLaunchControls(
    controlState: HomeProxyControlState,
    enabled: Boolean,
    isRemoteController: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    onLaunchClick: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MoeUi.Button.controlGap),
    ) {
        MoeLaunchConfigButton(surfaceColor = surfaceColor, onClick = onSettingsClick)
        MoeLaunchButton(
            controlState = controlState,
            enabled = enabled,
            isRemoteController = isRemoteController,
            surfaceColor = surfaceColor,
            modifier = Modifier.weight(1f),
            onClick = onLaunchClick,
        )
    }
}

@Composable
private fun moeLaunchButtonBorderColor(): Color {
    val onBackground = MiuixTheme.colorScheme.onBackground
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return onBackground.copy(alpha = if (isDark) 0.22f else 0.08f)
}

@Composable
private fun MoeLaunchConfigButton(surfaceColor: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed) MoeUi.Button.pressedScale else 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
            label = "moe_launch_config_button_press_scale",
        )
    val contentColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.62f)
    val borderColor = moeLaunchButtonBorderColor()

    Box(
        modifier =
            Modifier.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .size(MoeUi.Button.circleSize)
                .shadow(
                    elevation = MoeUi.Button.shadowElevation,
                    shape = MoeUi.Shape.launchButton,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .clip(MoeUi.Shape.launchButton)
                .background(surfaceColor, MoeUi.Shape.launchButton)
                .border(
                    width = MoeUi.Button.borderWidth,
                    color = borderColor,
                    shape = MoeUi.Shape.launchButton,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Yume.Repeat,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(MoeUi.Button.iconSize),
        )
    }
}

@Composable
internal fun MoeLaunchButton(
    controlState: HomeProxyControlState,
    enabled: Boolean,
    isRemoteController: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isRunning = controlState == HomeProxyControlState.Running
    val contentColor =
        MiuixTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.72f else 0.34f)
    val borderColor = moeLaunchButtonBorderColor()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed && enabled) MoeUi.Button.pressedScale else 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
            label = "moe_launch_button_press_scale",
        )
    val targetLabel =
        when {
            isRemoteController && isRunning -> FlyTxt.Home.Status.Running
            !enabled && controlState == HomeProxyControlState.Idle -> FlyTxt.Home.Traffic.NoProfile
            else ->
                when (controlState) {
                    HomeProxyControlState.Idle -> FlyTxt.Home.Control.Start
                    HomeProxyControlState.Connecting -> FlyTxt.Home.Status.Connecting
                    HomeProxyControlState.Running ->
                        if (isRemoteController) FlyTxt.Home.Status.Running else FlyTxt.Home.Control.Stop
                    HomeProxyControlState.Lost -> FlyTxt.Home.Status.Lost
                    HomeProxyControlState.Disconnecting -> FlyTxt.Home.Status.Disconnecting
                }
        }
    var displayedLabel by remember { mutableStateOf(targetLabel) }

    LaunchedEffect(targetLabel, controlState) {
        if (targetLabel == displayedLabel) return@LaunchedEffect
        if (controlState.isMoeLaunchTransientState()) {
            delay(MOE_LAUNCH_TEXT_TRANSIENT_DELAY)
        }
        displayedLabel = targetLabel
    }

    Box(
        modifier =
            modifier.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .fillMaxWidth()
                .shadow(
                    elevation = MoeUi.Button.shadowElevation,
                    shape = MoeUi.Shape.launchButton,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .height(MoeUi.Button.height)
                .clip(MoeUi.Shape.launchButton)
                .background(surfaceColor, MoeUi.Shape.launchButton)
                .border(
                    width = MoeUi.Button.borderWidth,
                    color = borderColor,
                    shape = MoeUi.Shape.launchButton,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    horizontal = MoeUi.Button.horizontalPadding,
                    vertical = MoeUi.Button.verticalPadding,
                )
    ) {
        Box(
            modifier = Modifier.align(Alignment.Center).height(22.dp).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = displayedLabel,
                transitionSpec = {
                    // 同一时长同一缓动、无延迟：新旧文本锁成一列同步上移，呈整体平移感
                    val slideSpec =
                        tween<IntOffset>(
                            durationMillis = MOE_LAUNCH_TEXT_SLIDE_DURATION,
                            easing = AnimationSpecs.StandardEasing,
                        )
                    slideInVertically(initialOffsetY = { it }, animationSpec = slideSpec)
                        .togetherWith(
                            slideOutVertically(targetOffsetY = { -it }, animationSpec = slideSpec)
                        )
                },
                label = "moe_launch_button_text",
            ) { text ->
                Text(
                    text = text,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
