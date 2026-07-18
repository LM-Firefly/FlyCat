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

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.feature.home.presentation.screen.moe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun HomePreviewGuideDialog(show: Boolean, onDismissRequest: () -> Unit) {
    val spacing = AppTheme.spacing
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity

    var page by remember { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    // Keep the dialog body within ~70% of screen height so title/buttons stay reachable on tablets.
    val contentMaxHeight = (configuration.screenHeightDp * 0.70f).dp

    WindowDialog(
        show = show,
        modifier = Modifier,
        title =
            when (page) {
                0 -> FlyTxt.Home.PreviewGuide.Title
                1 -> FlyTxt.Home.PreviewGuide.WallpaperTitle
                2 -> FlyTxt.Home.PreviewGuide.SwipeTitle
                else -> FlyTxt.Home.PreviewGuide.AddTitle
            },
        titleColor = DialogDefaults.titleColor(),
        summary =
            when (page) {
                0 -> FlyTxt.Home.PreviewGuide.Description
                1 -> FlyTxt.Home.PreviewGuide.WallpaperDescription
                2 -> FlyTxt.Home.PreviewGuide.SwipeDescription
                else -> FlyTxt.Home.PreviewGuide.AddDescription
            },
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = onDismissRequest,
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        defaultWindowInsetsPadding = true,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = contentMaxHeight).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.space18),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(320)) { w -> if (forward) w else -w } +
                            fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320)) { w -> if (forward) -w else w } +
                                fadeOut(tween(180))) using SizeTransform(clip = false)
                    },
                    label = "guide_page",
                ) { p ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (p) {
                            0 -> MoeHomeSkeletonMockup(demo = HomeMockupDemo.StartButton)
                            1 -> MoeHomeSkeletonMockup(demo = HomeMockupDemo.Wallpaper)
                            2 -> HomeToNodeSwipeMockup()
                            else -> ProfilesSkeletonMockup()
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier =
                                Modifier.size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == page) colorScheme.primary
                                        else colorScheme.onSurface.copy(alpha = opacity.muted)
                                    )
                        )
                    }
                }

                if (page < 3) {
                    GuideButton(
                        text = FlyTxt.Home.PreviewGuide.Next,
                        onClick = { page += 1 },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    GuideButton(
                        text = FlyTxt.Home.PreviewGuide.Start,
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun GuideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(text = text, onClick = onClick, modifier = modifier)
}
