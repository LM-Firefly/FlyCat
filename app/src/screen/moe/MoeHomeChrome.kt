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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.platform.util.formatBytesForDisplay
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.AppFormDialog
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Settings-2`
import com.github.yumelira.yumebox.presentation.icon.yume.Repeat
import com.github.yumelira.yumebox.presentation.icon.yume.Waiting
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.util.extractFlaggedName
import com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeProxyControlState
import dev.oom_wg.purejoy.mlang.MLang
import java.util.Calendar
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
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

@Composable
internal fun MoeHomeCopyBlock(
    nowMillis: Long,
    quote: MoeQuote,
    color: Color,
    modifier: Modifier = Modifier,
    launchContent: @Composable (() -> Unit)? = null,
) {
    val greeting = remember(nowMillis) { moeGreetingText(nowMillis) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MoeUi.Quote.contentGap),
    ) {
        Text(
            text = greeting,
            color = color.copy(alpha = MoeUi.Quote.eyebrowAlpha),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            fontSize = MoeUi.Quote.eyebrowSize,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = quote.text,
            color = color.copy(alpha = 0.88f),
            modifier = Modifier.align(Alignment.End).padding(top = MoeUi.Quote.authorTopGap),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Medium,
            fontSize = MoeUi.Quote.textSize,
            lineHeight = MoeUi.Quote.lineHeight,
            softWrap = true,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
        if (quote.author.isNotBlank()) {
            Text(
                text = "—— ${quote.author}",
                color = color.copy(alpha = MoeUi.Quote.authorAlpha),
                modifier = Modifier.align(Alignment.End),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Normal,
                fontSize = MoeUi.Quote.authorSize,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        launchContent?.let { content ->
            Box(modifier = Modifier.fillMaxWidth().padding(top = MoeUi.Hero.launchTopGap)) {
                content()
            }
        }
    }
}

private fun moeGreetingText(nowMillis: Long): String {
    val hour =
        Calendar.getInstance()
            .apply { timeInMillis = nowMillis }
            .get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..23 -> "Good evening"
        else -> "Good night"
    }
}

private const val MoeLaunchTextSlideDuration = 450
private const val MoeLaunchTextTransientDelay = 220L

private fun HomeProxyControlState.isMoeLaunchTransientState(): Boolean =
    this == HomeProxyControlState.Connecting || this == HomeProxyControlState.Disconnecting

@Composable
internal fun MoeHomeSettingsSheet(
    show: Boolean,
    quote: String,
    quoteAuthor: String,
    classicHomeEnabled: Boolean,
    sidebarExpanded: Boolean,
    onQuoteChange: (String) -> Unit,
    onQuoteAuthorChange: (String) -> Unit,
    onClassicHomeEnabledChange: (Boolean) -> Unit,
    onSidebarExpandedChange: (Boolean) -> Unit,
    onLaunchGalleryPicker: () -> Unit,
    onNavigateToWallpaperCrop: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    var draftQuote by remember(show, quote) { mutableStateOf(quote) }
    var draftQuoteAuthor by remember(show, quoteAuthor) { mutableStateOf(quoteAuthor) }
    var draftClassicHomeEnabled by remember(show, classicHomeEnabled) {
        mutableStateOf(classicHomeEnabled)
    }
    var draftSidebarExpanded by remember(show, sidebarExpanded) { mutableStateOf(sidebarExpanded) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    val saveSettings = {
        onQuoteChange(draftQuote)
        onQuoteAuthorChange(draftQuoteAuthor)
        onClassicHomeEnabledChange(draftClassicHomeEnabled)
        onSidebarExpandedChange(draftSidebarExpanded)
        onDismiss()
    }

    AppActionBottomSheet(
        show = show,
        title = MLang.AppSettings.Section.Home,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = { AppBottomSheetConfirmAction(onClick = saveSettings) },
        onDismissRequest = onDismiss,
        enableNestedScroll = true,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space16)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12),
                ) {
                    Column {
                        PreferenceSwitchItem(
                            title = MLang.AppSettings.Interface.ClassicHomeTitle,
                            checked = draftClassicHomeEnabled,
                            onCheckedChange = { draftClassicHomeEnabled = it },
                        )
                        PreferenceSwitchItem(
                            title = "展开侧边栏",
                            checked = draftSidebarExpanded,
                            onCheckedChange = { draftSidebarExpanded = it },
                        )
                    }
                }
            }
            if (!draftClassicHomeEnabled) {
                item {
                    TextField(
                        value = draftQuote,
                        onValueChange = { draftQuote = it },
                        label = MLang.AppSettings.Interface.HomeQuoteTitle,
                        useLabelAsPlaceholder = true,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12),
                    )
                }
                item {
                    TextField(
                        value = draftQuoteAuthor,
                        onValueChange = { draftQuoteAuthor = it },
                        label = MLang.AppSettings.Interface.HomeQuoteAuthorTitle,
                        useLabelAsPlaceholder = true,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12),
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12),
                    ) {
                        WindowDropdownPreference(
                            title = MLang.AppSettings.Interface.HomeWallpaperSourceTitle,
                            summary = MLang.AppSettings.Interface.HomeWallpaperSourceSummary,
                            items = listOf(
                                MLang.AppSettings.Interface.HomeWallpaperSourceGallery,
                                MLang.AppSettings.Interface.HomeWallpaperSourceUrl,
                            ),
                            selectedIndex = -1,
                            onSelectedIndexChange = { index ->
                                when (index) {
                                    0 -> onLaunchGalleryPicker()
                                    1 -> showUrlInputDialog = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showUrlInputDialog) {
        MoeHomeRemoteWallpaperUrlDialog(
            show = showUrlInputDialog,
            initialUrl = urlInput,
            onDismiss = { showUrlInputDialog = false },
            onConfirm = { url ->
                showUrlInputDialog = false
                urlInput = url
                onNavigateToWallpaperCrop(url)
            },
        )
    }
}

@Composable
private fun MoeHomeRemoteWallpaperUrlDialog(
    show: Boolean,
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    AppFormDialog(
        show = show,
        title = MLang.AppSettings.Interface.HomeWallpaperUrlDialogTitle,
        onDismissRequest = onDismiss,
        onConfirm = {
            val trimmed = url.trim()
            if (trimmed.isNotEmpty()) onConfirm(trimmed)
        },
        scrollable = false,
    ) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = "https://example.com/image.jpg",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

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
            isRemoteController && isRunning -> "运行中"
            !enabled && controlState == HomeProxyControlState.Idle -> MLang.Home.Traffic.NoProfile
            else ->
                when (controlState) {
                    HomeProxyControlState.Idle -> MLang.Home.Control.Start
                    HomeProxyControlState.Connecting -> MLang.Home.Status.Connecting
                    HomeProxyControlState.Running ->
                        if (isRemoteController) "运行中" else MLang.Home.Control.Stop
                    HomeProxyControlState.Lost -> "失联"
                    HomeProxyControlState.Disconnecting -> MLang.Home.Status.Disconnecting
                }
        }
    var displayedLabel by remember { mutableStateOf(targetLabel) }

    LaunchedEffect(targetLabel, controlState) {
        if (targetLabel == displayedLabel) return@LaunchedEffect
        if (controlState.isMoeLaunchTransientState()) {
            delay(MoeLaunchTextTransientDelay)
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
                            durationMillis = MoeLaunchTextSlideDuration,
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
