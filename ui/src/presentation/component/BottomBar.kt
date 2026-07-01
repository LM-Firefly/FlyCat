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

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Arrow-down-up`
import com.github.yumelira.yumebox.presentation.icon.yume.Bolt
import com.github.yumelira.yumebox.presentation.icon.yume.House
import com.github.yumelira.yumebox.presentation.icon.yume.`Package-check`
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.kyant.shapes.Capsule
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.roundToInt

class MainPagerState(val pagerState: PagerState, private val coroutineScope: CoroutineScope) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val currentDistanceInPages =
            targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = currentDistanceInPages * pageSize

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(
                    value = scrollPixels,
                    animationSpec = MainBottomBarDefaults.PagerAnimationSpec,
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState {
    return remember(pagerState, coroutineScope) { MainPagerState(pagerState, coroutineScope) }
}

val LocalPagerState = compositionLocalOf<PagerState> { error("LocalPagerState is not provided") }
val LocalMainPagerState =
    compositionLocalOf<MainPagerState> { error("LocalMainPagerState is not provided") }
val LocalHandlePageChange =
    compositionLocalOf<(Int) -> Unit> { error("LocalHandlePageChange is not provided") }
val LocalNavigator =
    compositionLocalOf<Navigator> { error("LocalNavigator is not provided") }
val LocalBottomBarHazeState = compositionLocalOf<HazeState?> { null }
val LocalBottomBarHazeStyle = compositionLocalOf<HazeBlurStyle?> { null }
val LocalBottomBarUseLegacyStyle = compositionLocalOf { false }

object MainBottomBarDefaults {
    val CornerRadius = UiDp.dp28
    val Shape: androidx.compose.ui.graphics.Shape
        @Composable get() = RoundedCornerShape(
            topStart = CornerRadius,
            topEnd = CornerRadius,
        )
    val BorderWidth = UiDp.dp0_26
    val OutlineHorizontalInset = UiDp.dp0
    val ItemHeight = UiDp.dp60
    val IconSize = UiDp.dp26
    val LabelFontSize = 11.5.sp
    val IconLabelSpacing = UiDp.dp3
    val HorizontalPadding = UiDp.dp48
    val TopPadding = UiDp.dp6
    val FloatingBottomPadding = UiDp.dp12
    val EnterOffset = UiDp.dp68
    val ExitOffset = UiDp.dp84
    val FloatingReservedHeight = UiDp.dp68
    val PagerAnimationSpec: AnimationSpec<Float> =
        spring(
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = Int.VisibilityThreshold.toFloat(),
        )
}

@Composable
fun rememberMainPagerFlingBehavior(pagerState: PagerState): TargetedFlingBehavior {
    return PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = MainBottomBarDefaults.PagerAnimationSpec,
    )
}

@Composable
fun rememberBottomBarReservedHeight(): Dp {
    val density = LocalDensity.current
    val systemBottomInset =
        with(density) {
            max(
                    WindowInsets.navigationBars.getBottom(this),
                    WindowInsets.systemGestures.getBottom(this),
                )
                .toDp()
        }
    return remember(systemBottomInset) {
        MainBottomBarDefaults.FloatingReservedHeight + systemBottomInset
    }
}

@OptIn(ExperimentalHazeApi::class)
private fun Modifier.bottomBarHazeEffect(state: HazeState?, style: HazeBlurStyle?): Modifier {
    if (state == null || style == null) return this

    return hazeEffect(state) {
        blurEffect {
            this.style = style
            blurRadius = UiDp.dp26
            noiseFactor = 0f
        }
        inputScale = HazeInputScale.Fixed(0.24f)
        forceInvalidateOnPreDraw = false
    }
}

@Composable
fun BottomBarContent(isVisible: Boolean = true) {
    FloatingBottomBarContent(isVisible = isVisible)
}

@Composable
private fun FloatingBottomBarContent(isVisible: Boolean = true) {
    val bottomBarScrollBehavior = LocalBottomBarScrollBehavior.current
    val mainPagerState = LocalMainPagerState.current
    val hazeState = LocalBottomBarHazeState.current
    val hazeStyle = LocalBottomBarHazeStyle.current
    val hazeEnabled = hazeState != null && hazeStyle != null
    val pagerState = mainPagerState.pagerState
    val page by remember(mainPagerState) { derivedStateOf { mainPagerState.selectedPage } }
    val indicatorProgress by
        remember(pagerState) {
            derivedStateOf {
                (pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction).coerceIn(
                    0f,
                    (BottomBarDestination.entries.size - 1).toFloat(),
                )
            }
        }
    val bottomBarVisible = isVisible && (bottomBarScrollBehavior?.isBottomBarVisible ?: true)
    val density = LocalDensity.current
    val opacity = AppTheme.opacity
    val enterOffsetPx =
        remember(density) { with(density) { MainBottomBarDefaults.EnterOffset.toPx() } }
    val exitOffsetPx =
        remember(density) { with(density) { MainBottomBarDefaults.ExitOffset.toPx() } }
    val animatedTranslationY = remember { Animatable(if (bottomBarVisible) 0f else exitOffsetPx) }
    val animatedScale by
        animateFloatAsState(
            targetValue = if (bottomBarVisible) 1f else 0.98f,
            animationSpec =
                tween(
                    durationMillis = 240,
                    easing =
                        if (bottomBarVisible) {
                            AnimationSpecs.EmphasizedDecelerate
                        } else {
                            AnimationSpecs.EmphasizedAccelerate
                        },
                ),
            label = "legacy_bottom_bar_scale",
        )
    val animatedAlpha by
        animateFloatAsState(
            targetValue = if (bottomBarVisible) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = 180,
                    easing =
                        if (bottomBarVisible) {
                            AnimationSpecs.EmphasizedDecelerate
                        } else {
                            AnimationSpecs.EmphasizedAccelerate
                        },
                ),
            label = "legacy_bottom_bar_alpha",
        )

    LaunchedEffect(bottomBarVisible, enterOffsetPx, exitOffsetPx) {
        if (bottomBarVisible) {
            animatedTranslationY.snapTo(enterOffsetPx)
            animatedTranslationY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
            )
        } else {
            animatedTranslationY.animateTo(
                targetValue = exitOffsetPx,
                animationSpec =
                    tween(durationMillis = 220, easing = AnimationSpecs.EmphasizedAccelerate),
            )
        }
    }

    val handlePageChange = LocalHandlePageChange.current
    val onItemClick: (Int) -> Unit = { index ->
        if (index != mainPagerState.selectedPage) {
            handlePageChange(index)
        }
    }

    val bottomSafeInset =
        with(density) {
            val navigationBottom = WindowInsets.navigationBars.getBottom(this)
            val gestureBottom = WindowInsets.systemGestures.getBottom(this)
            max(navigationBottom, gestureBottom).toDp()
        }
    val selectedColor = MiuixTheme.colorScheme.primary
    val unselectedColor = MiuixTheme.colorScheme.onSurface.copy(alpha = opacity.secondaryText)
    val containerColor = if (hazeEnabled) {
        MiuixTheme.colorScheme.background.copy(alpha = opacity.elevatedSurface)
    } else {
        MiuixTheme.colorScheme.background
    }
    val indicatorContainerColor = selectedColor.copy(alpha = opacity.subtle)

    LegacyBottomNavigationBar(
        selectedIndex = page,
        indicatorProgress = indicatorProgress,
        tabsCount = BottomBarDestination.entries.size,
        containerColor = containerColor,
        indicatorContainerColor = indicatorContainerColor,
        hazeEnabled = hazeEnabled,
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = MainBottomBarDefaults.HorizontalPadding,
                    end = MainBottomBarDefaults.HorizontalPadding,
                    top = MainBottomBarDefaults.TopPadding,
                    bottom = bottomSafeInset + MainBottomBarDefaults.FloatingBottomPadding,
                )
                .offset { IntOffset(0, animatedTranslationY.value.toInt()) }
                .graphicsLayer {
                    alpha = animatedAlpha
                    scaleX = animatedScale
                    scaleY = animatedScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
    ) {
        BottomBarDestination.entries.forEachIndexed { index, destination ->
            val itemColor = if (page == index) selectedColor else unselectedColor
            LegacyBottomNavigationTabItem(
                enabled = bottomBarVisible,
                onClick = { onItemClick(index) },
            ) {
                Box(modifier = Modifier.size(UiDp.dp20), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        tint = itemColor,
                    )
                }
                BasicText(
                    text = destination.label,
                    style =
                        TextStyle(
                            color = itemColor,
                            fontSize = 11.sp,
                            fontWeight =
                                if (page == index) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LegacyBottomNavigationBar(
    selectedIndex: Int,
    indicatorProgress: Float,
    tabsCount: Int,
    containerColor: Color,
    indicatorContainerColor: Color,
    hazeEnabled: Boolean = false,
    hazeState: HazeState? = null,
    hazeStyle: HazeBlurStyle? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val opacity = AppTheme.opacity
    val density = LocalDensity.current
    val isLightTheme = !isSystemInDarkTheme()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val surfaceWidthPx = remember { mutableIntStateOf(0) }
    val safeSelectedIndex = selectedIndex.coerceIn(0, tabsCount - 1)
    val safeIndicatorProgress = indicatorProgress.coerceIn(0f, (tabsCount - 1).toFloat())
    val contentInsetPx = with(density) { (UiDp.dp4 * 2).toPx() }
    val innerWidthPx = (surfaceWidthPx.intValue - contentInsetPx).coerceAtLeast(0f)
    val tabWidthPx = if (tabsCount > 0) innerWidthPx / tabsCount else 0f
    val indicatorOffsetPx =
        if (isLtr) {
            safeIndicatorProgress * tabWidthPx
        } else {
            innerWidthPx - (safeIndicatorProgress + 1f) * tabWidthPx
        }
    val indicatorScale = remember { Animatable(1f) }
    val borderShadowColor =
        if (isLightTheme) {
            // White capsule on the near-white page (surface #F7F7F7) has almost no tonal
            // contrast, so the drop shadow has to carry the lift — 0.10 was too faint.
            Black.copy(alpha = opacity.softOverlay)
        } else {
            Black.copy(alpha = opacity.surfaceSoft)
        }
    val outerBorderColor =
        if (isLightTheme) {
            // A white rim on a white capsule is invisible; use a dark hairline so the capsule
            // edge reads clearly against the light page (miuix separates with hairlines, not glow).
            Black.copy(alpha = opacity.subtle)
        } else {
            Black.copy(alpha = opacity.mediumOverlay)
        }
    val innerBorderColor =
        if (isLightTheme) {
            Black.copy(alpha = opacity.ultraSubtle)
        } else {
            White.copy(alpha = opacity.verySubtle)
        }

    LaunchedEffect(safeSelectedIndex) {
        launch {
            indicatorScale.animateTo(0.9f, tween(120, easing = FastOutSlowInEasing))
            indicatorScale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { surfaceWidthPx.intValue = it.width }
                .graphicsLayer {
                    shape = Capsule()
                    clip = false
                    shadowElevation = with(density) { UiDp.dp7.toPx() }
                    ambientShadowColor = borderShadowColor
                    spotShadowColor = borderShadowColor
                }
                .height(UiDp.dp56)
                .clip(Capsule())
                .then(
                    if (hazeEnabled) {
                        Modifier.bottomBarHazeEffect(hazeState, hazeStyle)
                    } else {
                        Modifier
                    },
                )
                .background(containerColor, Capsule()),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (tabWidthPx > 0f) {
            LegacyBottomNavigationIndicator(
                modifier = Modifier.padding(UiDp.dp4).align(Alignment.CenterStart),
                indicatorOffsetPx = indicatorOffsetPx,
                indicatorWidthPx = tabWidthPx,
                indicatorScale = indicatorScale.value,
                indicatorContainerColor = indicatorContainerColor,
            )
        }

        Row(
            modifier =
                Modifier.padding(UiDp.dp4)
                    .height(UiDp.dp48)
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        LegacyBottomNavigationBorders(
            outerBorderColor = outerBorderColor,
            innerBorderColor = innerBorderColor,
        )
    }
}

@Composable
private fun LegacyBottomNavigationIndicator(
    modifier: Modifier = Modifier,
    indicatorOffsetPx: Float,
    indicatorWidthPx: Float,
    indicatorScale: Float,
    indicatorContainerColor: Color,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            modifier
                .offset { IntOffset(indicatorOffsetPx.roundToInt(), 0) }
                .width(with(density) { indicatorWidthPx.toDp() })
                .height(UiDp.dp48)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
                .background(indicatorContainerColor, Capsule())
    )
}

@Composable
private fun BoxScope.LegacyBottomNavigationBorders(
    outerBorderColor: Color,
    innerBorderColor: Color,
) {
    Box(
        modifier =
            Modifier.matchParentSize()
                .border(width = UiDp.dp0_3, color = outerBorderColor, shape = Capsule())
    )

    Box(
        modifier =
            Modifier.matchParentSize()
                .padding(UiDp.dp1)
                .border(width = UiDp.dp0_2, color = innerBorderColor, shape = Capsule())
    )
}

@Composable
private fun RowScope.LegacyBottomNavigationTabItem(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(Capsule())
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .fillMaxHeight()
                .weight(1f),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

enum class BottomBarDestination(val icon: ImageVector) {
    Home(Yume.House),
    Proxy(Yume.`Arrow-down-up`),
    Config(Yume.`Package-check`),
    Setting(Yume.Bolt);

    val label: String
        get() =
            when (this) {
                Home -> MLang.Component.BottomBar.Home
                Proxy -> MLang.Component.BottomBar.Proxy
                Config -> MLang.Component.BottomBar.Config
                Setting -> MLang.Component.BottomBar.Setting
            }
}
