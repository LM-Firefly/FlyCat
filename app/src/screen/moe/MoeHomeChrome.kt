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

package com.github.yumelira.yumebox.screen.moe

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.platform.util.formatBytesForDisplay
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Waiting
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.util.extractFlaggedName
import com.github.yumelira.yumebox.screen.home.HomeProxyControlState
import dev.oom_wg.purejoy.mlang.MLang
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
                    horizontal = MoeUi.Sidebar.innerHorizontalPadding,
                    vertical = spacing.space24,
                ),
        content = content,
    )
}

@Composable
internal fun MoeSidebarContent(
    topValue: String,
    bottomValue: String,
    icons: List<MoeSidebarIconItem>,
    visibleWidth: Dp,
) {
    // 在「可见条」内居中：扣除 decoration 的左右内边距后定宽，居中即对齐可见区域中线。
    val laneWidth =
        (visibleWidth - MoeUi.Sidebar.innerHorizontalPadding * 2).coerceAtLeast(0.dp)
    Box(
        modifier = Modifier.fillMaxHeight().width(laneWidth),
        contentAlignment = Alignment.TopCenter,
    ) {
        MoeSidebarRail(
            topValue = topValue,
            bottomValue = bottomValue,
            icons = icons,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun MoeQuoteText(quote: MoeQuote, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MoeUi.Quote.contentGap),
    ) {
        Text(
            text = quote.text,
            color = color,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Medium,
            fontSize = MoeUi.Quote.textSize,
            lineHeight = MoeUi.Quote.lineHeight,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "— ${quote.author}",
            modifier = Modifier.align(Alignment.End).padding(top = MoeUi.Quote.authorTopGap),
            color = color.copy(alpha = MoeUi.Quote.authorAlpha),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            fontSize = MoeUi.Quote.authorSize,
            softWrap = false,
        )
    }
}

@Composable
internal fun MoeLaunchButton(
    controlState: HomeProxyControlState,
    enabled: Boolean,
    isRemoteController: Boolean,
    onClick: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isRunning = controlState == HomeProxyControlState.Running
    val background =
        if (isRunning) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onBackground
        }
    val contentColor =
        if (isRunning) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.background
        }
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed && enabled) MoeUi.Button.pressedScale else 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
            label = "moe_launch_button_press_scale",
        )

    Box(
        modifier =
            Modifier.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .width(MoeUi.Button.fixedWidth)
                .clip(MoeUi.Shape.launchButton)
                .background(background, MoeUi.Shape.launchButton)
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
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space10),
        ) {
            Icon(
                imageVector =
                    when (controlState) {
                        HomeProxyControlState.Idle -> ShellIcons.StartProxy
                        HomeProxyControlState.Running -> ShellIcons.StopProxy
                        HomeProxyControlState.Connecting,
                        HomeProxyControlState.Lost,
                        HomeProxyControlState.Disconnecting -> Yume.Waiting
                    },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(spacing.space18),
            )
            Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = controlState,
                    transitionSpec = {
                        (slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec =
                                    tween(
                                        durationMillis = AnimationSpecs.DURATION_FAST,
                                        easing = AnimationSpecs.EmphasizedDecelerate,
                                    ),
                            ) +
                                fadeIn(
                                    animationSpec =
                                        tween(
                                            durationMillis = AnimationSpecs.DURATION_FAST,
                                            easing = AnimationSpecs.EnterEasing,
                                        )
                                ))
                            .togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { -it / 2 },
                                    animationSpec =
                                        tween(
                                            durationMillis = AnimationSpecs.DURATION_INSTANT,
                                            easing = AnimationSpecs.EmphasizedAccelerate,
                                        ),
                                ) +
                                    fadeOut(
                                        animationSpec =
                                            tween(
                                                durationMillis = AnimationSpecs.DURATION_INSTANT,
                                                easing = AnimationSpecs.ExitEasing,
                                            )
                                    )
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "moe_launch_button_text",
                ) { state ->
                    Text(
                        text =
                            when (state) {
                                HomeProxyControlState.Idle -> MLang.Home.Control.Start
                                HomeProxyControlState.Connecting -> MLang.Home.Status.Connecting
                                HomeProxyControlState.Running ->
                                    if (isRemoteController) "运行中" else MLang.Home.Control.Stop
                                HomeProxyControlState.Lost -> "失联"
                                HomeProxyControlState.Disconnecting ->
                                    MLang.Home.Status.Disconnecting
                            },
                        color = contentColor,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MoeTrafficStrip(
    downloadSpeed: Long,
    uploadSpeed: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MoeUi.Hero.trafficRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            MoeTrafficItem(label = MLang.Home.Traffic.UpShort, speed = uploadSpeed)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            MoeTrafficItem(label = MLang.Home.Traffic.DownShort, speed = downloadSpeed)
        }
    }
}

@Composable
private fun MoeTrafficItem(label: String, speed: Long) {
    val (value, unit) = formatBytesForDisplay(speed)
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(MoeUi.Traffic.itemGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label,
            color = onSurface.copy(alpha = 0.62f),
            style = MiuixTheme.textStyles.footnote1,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = MoeUi.Traffic.labelBottomPadding),
        )
        Text(
            text = value,
            color = onSurface,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
        )
        Text(
            text = unit,
            color = onSurface.copy(alpha = 0.55f),
            style = MiuixTheme.textStyles.footnote1,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = MoeUi.Traffic.labelBottomPadding),
        )
    }
}

@Composable
internal fun MoeHomeInfoPanel(
    serverName: String?,
    serverPing: Int?,
    modifier: Modifier = Modifier,
) {
    val flaggedNode = remember(serverName) { serverName?.let(::extractFlaggedName) }
    val resolvedNodeName = flaggedNode?.displayName ?: serverName.orEmpty().ifBlank { "" }
    val resolvedPing =
        serverPing
            ?.takeIf { it in 1..1000 }
            ?.let { ping ->
                MLang.Home.NodeInfo.DelayValue.format(ping)
            }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MoeUi.Hero.infoRowMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resolvedNodeName.isNotBlank()) {
            MoeInfoBlock(
                value = resolvedNodeName,
                modifier = Modifier.weight(1f).padding(end = MoeUi.Info.trailingPadding),
                leading = {
                    flaggedNode?.countryCode?.let { countryCode ->
                        CountryFlagCircle(
                            countryCode = countryCode,
                            size = AppTheme.spacing.space16,
                        )
                    }
                },
            )
        } else {
            Spacer(modifier = Modifier.weight(1f).padding(end = MoeUi.Info.trailingPadding))
        }

        if (resolvedPing != null) {
            MoeInfoBlock(
                value = resolvedPing,
                modifier = Modifier.width(MoeUi.Hero.delayWidth),
                valueColor =
                    when {
                        serverPing < 500 -> AppTheme.colors.moe.pingExcellent
                        else -> AppTheme.colors.moe.pingWarning
                    },
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun MoeInfoBlock(
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onBackground,
    valueFontFamily: FontFamily? = null,
    alignEnd: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                space = MoeUi.Info.blockGap,
                alignment = if (alignEnd) Alignment.End else Alignment.Start,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text = value,
            color = valueColor,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            fontFamily = valueFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = if (alignEnd) Modifier else Modifier.weight(1f),
        )
    }
}
