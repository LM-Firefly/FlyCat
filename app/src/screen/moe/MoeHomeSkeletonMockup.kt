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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Which guided demo the home skeleton mockup plays. */
internal enum class HomeMockupDemo {
    /** The bottom launch capsule is highlighted and taps in a repeating rhythm. */
    StartButton,

    /** A plain launch row; instead the hero is long-pressed and a photo-picker sheet slides up. */
    Wallpaper,
}

@Composable
internal fun MoeHomeSkeletonMockup(
    demo: HomeMockupDemo = HomeMockupDemo.StartButton,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val mask = colorScheme.onSurface.copy(alpha = opacity.subtle)
    val maskStrong = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)
    val onHero = colorScheme.surface.copy(alpha = opacity.secondaryText)
    val frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft)

    val wallpaper = demo == HomeMockupDemo.Wallpaper

    // The wallpaper demo drives a single looped cycle: long-press the hero, then raise the picker.
    val transition = rememberInfiniteTransition(label = "home_wallpaper")
    val sheetProgress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 4200
                            0f at 0
                            0f at 1500
                            1f at 1900 using FastOutSlowInEasing
                            1f at 3500
                            0f at 3900 using FastOutSlowInEasing
                            0f at 4200
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "wallpaper_sheet_progress",
        )
    val heroLongPressScale by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 4200
                            1f at 0
                            1f at 500
                            0.82f at 760 using FastOutSlowInEasing
                            0.82f at 1450
                            1f at 1680 using FastOutSlowInEasing
                            1f at 4200
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "wallpaper_hero_long_press_scale",
        )
    val heroRippleScale by
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 2.1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 4200
                            0.6f at 0
                            0.6f at 760
                            2.1f at 1500 using FastOutSlowInEasing
                            2.1f at 4200
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "wallpaper_hero_ripple_scale",
        )
    val heroRippleAlpha by
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 4200
                            0f at 0
                            0f at 650
                            0.45f at 760
                            0f at 1500 using FastOutSlowInEasing
                            0f at 4200
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "wallpaper_hero_ripple_alpha",
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth(0.64f)
                .aspectRatio(0.46f)
                .clip(RoundedCornerShape(22.dp))
                .background(colorScheme.surface)
                .border(1.5.dp, frameBorder, RoundedCornerShape(22.dp))
                .padding(7.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left sidebar (~22%): vertical clock near the top, nav icon rail pinned to the bottom.
            Column(
                modifier = Modifier.fillMaxHeight().weight(0.22f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(10.dp))
                // Vertical clock: one continuous stroke ("一笔带过").
                Box(
                    Modifier.width(13.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(maskStrong)
                )

                Spacer(Modifier.weight(1f))

                // Nav icon rail: three icons stacked near the bottom.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    repeat(3) {
                        Box(Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(mask))
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // Right content (~78%): hero (traffic + node info) + quote + launch controls row.
            Column(
                modifier = Modifier.fillMaxHeight().weight(0.78f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .weight(2f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(maskStrong)
                ) {
                    Column(
                        modifier =
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Traffic strip: UP on the left, DOWN on the right.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier.width(30.dp)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(onHero)
                            )
                            Box(
                                Modifier.width(30.dp)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(onHero)
                            )
                        }
                        // Node info: flag circle + node name on the left, ping on the right.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(onHero))
                                Box(
                                    Modifier.width(46.dp)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(onHero)
                                )
                            }
                            Box(
                                Modifier.width(20.dp)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(onHero)
                            )
                        }
                    }

                    // Wallpaper demo: a looping long-press effect centered on the hero.
                    if (wallpaper) {
                        Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier.graphicsLayer {
                                            scaleX = heroRippleScale
                                            scaleY = heroRippleScale
                                            alpha = heroRippleAlpha
                                        }
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary)
                            )
                            Box(
                                modifier =
                                    Modifier.graphicsLayer {
                                            scaleX = heroLongPressScale
                                            scaleY = heroLongPressScale
                                        }
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.surface)
                                        .border(2.dp, colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }

                // Bottom third: quote block + the launch controls row (config circle + capsule).
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Quote block: three left-aligned lines + a right-aligned author line.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(0.92f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(mask)
                        )
                        Box(
                            Modifier.fillMaxWidth(0.8f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(mask)
                        )
                        Box(
                            Modifier.fillMaxWidth(0.5f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(mask)
                        )
                        Box(
                            Modifier.align(Alignment.End)
                                .fillMaxWidth(0.3f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(mask)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (wallpaper) PlainLaunchRow() else HighlightedLaunchRow()

                    Spacer(Modifier.height(5.dp))
                }
            }
        }

        // Wallpaper demo: dim scrim + the photo-picker bottom sheet sliding up. Clipped to the
        // phone-frame's inner radius so they follow the rounded corners instead of sharp edges.
        if (wallpaper) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp))) {
                Box(
                    modifier =
                        Modifier.matchParentSize()
                            .background(Color.Black.copy(alpha = 0.40f * sheetProgress))
                )
                PhotoPickerSheet(
                    sheetProgress = sheetProgress,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun HighlightedLaunchRow(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val maskStrong = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)
    val frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft)

    val transition = rememberInfiniteTransition(label = "start_chip")
    // A press that reads as a tap: hold, snap down, snap back, then rest until the next cycle.
    val pressScale by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 1500
                            1f at 0
                            1f at 200
                            0.9f at 360 using FastOutSlowInEasing
                            1f at 560
                            1f at 1500
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "press_scale",
        )
    val rippleScale by
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 2.6f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "ripple_scale",
        )
    val rippleAlpha by
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "ripple_alpha",
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Config button: a bordered surface circle with an icon glyph in the middle.
        Box(
            modifier =
                Modifier.size(22.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surface)
                    .border(1.dp, frameBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(maskStrong))
        }

        // Launch capsule filling the remaining width, pressing on each tap.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                            scaleX = rippleScale
                            scaleY = rippleScale
                            alpha = rippleAlpha
                        }
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
            )

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .height(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colorScheme.surface)
                        .border(1.dp, frameBorder, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = MLang.Home.Control.Start,
                    color = colorScheme.onSurface.copy(alpha = 0.72f),
                    style =
                        MiuixTheme.textStyles.footnote1.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
            }

            // Prominent touch dot over the capsule's center, pressing in sync with it.
            Box(
                modifier =
                    Modifier.offset(x = 4.dp, y = 4.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                        .border(2.dp, colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
private fun PlainLaunchRow(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val maskStrong = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)
    val frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Config button: a bordered surface circle with an icon glyph in the middle.
        Box(
            modifier =
                Modifier.size(22.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surface)
                    .border(1.dp, frameBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(maskStrong))
        }

        // Launch capsule filling the remaining width, without any tap animation.
        Box(
            modifier =
                Modifier.weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.surface)
                    .border(1.dp, frameBorder, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = MLang.Home.Control.Start,
                color = colorScheme.onSurface.copy(alpha = 0.72f),
                style =
                    MiuixTheme.textStyles.footnote1.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    }
}

@Composable
private fun PhotoPickerSheet(sheetProgress: Float, modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val mask = colorScheme.onSurface.copy(alpha = opacity.subtle)
    val maskStrong = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .graphicsLayer { translationY = (1f - sheetProgress) * size.height }
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colorScheme.surface)
                .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.width(22.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(mask)
            )

            // Tab pills: "全部" highlighted, "影集" muted.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.weight(1f)
                        .height(15.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colorScheme.primary.copy(alpha = opacity.subtleStrong))
                )
                Box(
                    Modifier.weight(1f)
                        .height(15.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(mask)
                )
            }

            // Notice bar.
            Box(
                Modifier.fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(mask)
            )

            // Photo grid: 3 rows of 3 tiles filling the freed space; the first tile is the camera.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(3) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(3) { col ->
                            val isCamera = row == 0 && col == 0
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (isCamera) mask else maskStrong),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isCamera) {
                                    Box(
                                        Modifier.size(6.dp)
                                            .clip(CircleShape)
                                            .background(maskStrong)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeToNodeSwipeMockup(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val placeholder = colorScheme.onSurface.copy(alpha = opacity.subtle)
    val strongPlaceholder = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)
    val nodeCardBackground = colorScheme.surfaceVariant.copy(alpha = opacity.surfaceVariant)
    val frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft)

    val transition = rememberInfiniteTransition(label = "home_to_node_swipe")
    val swipeProgress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 3600
                            0f at 0
                            0f at 620
                            1f at 1320 using FastOutSlowInEasing
                            1f at 2380
                            0f at 3100 using FastOutSlowInEasing
                            0f at 3600
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "home_to_node_swipe_progress",
        )
    val touchScale by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 3600
                            1f at 0
                            1f at 520
                            0.86f at 680 using FastOutSlowInEasing
                            1f at 900
                            1f at 3600
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "home_to_node_touch_scale",
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth(0.64f)
                .aspectRatio(0.46f)
                .clip(RoundedCornerShape(22.dp))
                .background(colorScheme.surface)
                .border(1.5.dp, frameBorder, RoundedCornerShape(22.dp))
                .padding(7.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp))) {
            Box(Modifier.matchParentSize().background(colorScheme.surface))

            NodePagePreview(
                placeholder = placeholder,
                strongPlaceholder = strongPlaceholder,
                nodeCardBackground = nodeCardBackground,
                frameBorder = frameBorder,
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - swipeProgress) * size.width
                            alpha = 0.7f + 0.3f * swipeProgress
                        },
            )

            HomePagePreview(
                placeholder = placeholder,
                strongPlaceholder = strongPlaceholder,
                frameBorder = frameBorder,
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            translationX = -swipeProgress * size.width
                            val scale = 1f - 0.025f * swipeProgress
                            scaleX = scale
                            scaleY = scale
                        }
                        .padding(7.dp),
            )

            Box(
                modifier =
                    Modifier.matchParentSize()
                        .graphicsLayer {
                            translationX = (0.22f - 0.32f * swipeProgress) * size.width
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier.graphicsLayer {
                                scaleX = touchScale
                                scaleY = touchScale
                            }
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                            .border(2.dp, colorScheme.surface, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun HomePagePreview(
    placeholder: Color,
    strongPlaceholder: Color,
    frameBorder: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(15.dp))
                .background(colorScheme.surface)
                .border(1.dp, frameBorder, RoundedCornerShape(15.dp))
                .padding(7.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().weight(0.22f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.width(12.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(strongPlaceholder)
            )
            Spacer(Modifier.weight(1f))
            repeat(3) { index ->
                Box(
                    Modifier.size(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index == 0) colorScheme.primary else placeholder)
                )
                if (index < 2) Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        Column(
            modifier = Modifier.fillMaxHeight().weight(0.78f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .weight(2f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(strongPlaceholder)
            ) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth(0.64f)
                        .padding(8.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorScheme.surface.copy(alpha = opacity.secondaryText))
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { index ->
                    val lineWidth =
                        when (index) {
                            0 -> 0.92f
                            1 -> 0.76f
                            else -> 0.48f
                        }
                    Box(
                        Modifier.fillMaxWidth(lineWidth)
                            .height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(placeholder)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(18.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceVariant.copy(alpha = opacity.surfaceVariant))
                            .border(1.dp, frameBorder, CircleShape)
                    )
                    Box(
                        Modifier.weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colorScheme.surfaceVariant.copy(alpha = opacity.surfaceVariant))
                            .border(1.dp, frameBorder, RoundedCornerShape(50))
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun NodePagePreview(
    placeholder: Color,
    strongPlaceholder: Color,
    nodeCardBackground: Color,
    frameBorder: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(15.dp))
                .background(colorScheme.surface)
                .border(1.dp, frameBorder, RoundedCornerShape(15.dp))
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(38.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(strongPlaceholder)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(2) {
                    Box(Modifier.size(13.dp).clip(CircleShape).background(placeholder))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier.weight(if (index == 0) 1.25f else 1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (index == 0) colorScheme.primary else placeholder)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            repeat(4) { index ->
                NodePreviewCard(
                    selected = index == 0,
                    placeholder = placeholder,
                    strongPlaceholder = strongPlaceholder,
                    nodeCardBackground = nodeCardBackground,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NodePreviewCard(
    selected: Boolean,
    placeholder: Color,
    strongPlaceholder: Color,
    nodeCardBackground: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(11.dp))
                .background(nodeCardBackground)
                .border(
                    1.dp,
                    if (selected) colorScheme.primary.copy(alpha = opacity.disabled)
                    else Color.Transparent,
                    RoundedCornerShape(11.dp),
                )
                .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) colorScheme.primary else strongPlaceholder)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.fillMaxWidth(if (selected) 0.74f else 0.58f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(strongPlaceholder)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        Modifier.width(18.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholder)
                    )
                }
            }
        }
        Box(
            Modifier.width(26.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) colorScheme.primary else placeholder)
        )
    }
}

@Composable
internal fun ProfilesSkeletonMockup(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val mask = colorScheme.onSurface.copy(alpha = opacity.subtle)
    val maskStrong = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)
    val frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft)
    val recessed = colorScheme.onSurface.copy(alpha = opacity.verySubtle)
    val card = colorScheme.surface

    val transition = rememberInfiniteTransition(label = "profiles_sheet")
    val sheetProgress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 3600
                            0f at 0
                            0f at 900
                            1f at 1300 using FastOutSlowInEasing
                            1f at 2900
                            0f at 3300 using FastOutSlowInEasing
                            0f at 3600
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "sheet_progress",
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth(0.64f)
                .aspectRatio(0.46f)
                .clip(RoundedCornerShape(22.dp))
                .background(colorScheme.surface)
                .border(1.5.dp, frameBorder, RoundedCornerShape(22.dp))
                .padding(7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(40.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(maskStrong)
                )
                AddButtonGlyph()
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    ProfileCardMock(
                        index = index,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier.clip(RoundedCornerShape(50))
                        .background(colorScheme.surface)
                        .border(1.dp, frameBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) {
                    Box(
                        Modifier.size(9.dp)
                            .clip(CircleShape)
                            .background(if (it == 2) colorScheme.primary else mask)
                    )
                }
            }
        }

        Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(15.dp))) {
            Box(
                modifier =
                    Modifier.matchParentSize()
                        .background(Color.Black.copy(alpha = 0.40f * sheetProgress))
            )

            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .graphicsLayer { translationY = (1f - sheetProgress) * size.height }
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(colorScheme.surface)
                        .border(
                            1.dp,
                            frameBorder,
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        )
                        .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.width(26.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(mask)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(maskStrong)
                        )
                        Box(
                            Modifier.width(56.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(maskStrong)
                        )
                        Box(
                            Modifier.size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(maskStrong)
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(card)
                            .border(1.dp, frameBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(46.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(maskStrong)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.width(34.dp)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(mask)
                                )
                                Box(
                                    Modifier.width(7.dp)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(mask)
                                )
                            }
                        }
                    }
                    repeat(3) { index ->
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(recessed)
                                    .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                Modifier.fillMaxWidth(if (index == 2) 0.42f else 0.5f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(mask)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCardMock(index: Int, modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    val maskInner = colorScheme.onSurface.copy(alpha = opacity.subtleStrong)
    val card = colorScheme.surfaceVariant.copy(alpha = opacity.surfaceVariant)
    val frameBorder = colorScheme.outline.copy(alpha = opacity.surfaceSoft)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(card)
                .border(1.dp, frameBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        Modifier.width(40.dp)
                            .height(9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(maskInner)
                    )
                    Box(
                        Modifier.width(26.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(maskInner)
                    )
                }
                Box(
                    modifier =
                        Modifier.width(22.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (index == 0) colorScheme.primary else maskInner)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(1.5.dp),
                        contentAlignment =
                            if (index == 0) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(colorScheme.surface))
                    }
                }
            }

            if (index > 0) {
                Box(
                    Modifier.fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(maskInner)
                )
                Box(
                    Modifier.fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(maskInner)
                )
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier.fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.onSurface.copy(alpha = opacity.surfaceSoft))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(maskInner))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(maskInner))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (index > 0) {
                        Box(
                            Modifier.width(26.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.primary.copy(alpha = opacity.subtleStrong))
                        )
                    }
                    Box(
                        Modifier.width(26.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(maskInner)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddButtonGlyph(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme

    val transition = rememberInfiniteTransition(label = "add_button")
    val tapScale by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 3600
                            1f at 0
                            1f at 700
                            0.85f at 850 using FastOutSlowInEasing
                            1f at 1000
                            1f at 3600
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "add_tap_scale",
        )
    val rippleScale by
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 2.2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "add_ripple_scale",
        )
    val rippleAlpha by
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "add_ripple_alpha",
        )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier.graphicsLayer {
                        scaleX = rippleScale
                        scaleY = rippleScale
                        alpha = rippleAlpha
                    }
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colorScheme.primary)
        )

        Box(
            modifier =
                Modifier.graphicsLayer {
                        scaleX = tapScale
                        scaleY = tapScale
                    }
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(8.dp).height(2.dp).background(colorScheme.onPrimary))
            Box(Modifier.width(2.dp).height(8.dp).background(colorScheme.onPrimary))
        }
    }
}
