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

package com.github.yumelira.yumebox.screen.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.WebViewActivity
import com.github.yumelira.yumebox.feature.substore.presentation.viewmodel.SettingEvent
import com.github.yumelira.yumebox.feature.substore.presentation.viewmodel.SettingViewModel
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Git-merge`
import com.github.yumelira.yumebox.presentation.icon.yume.`Scroll-text`
import com.github.yumelira.yumebox.presentation.icon.yume.`Settings-2`
import com.github.yumelira.yumebox.presentation.icon.yume.`Wifi-cog`
import com.github.yumelira.yumebox.presentation.icon.yume.FlaskConical
import com.github.yumelira.yumebox.presentation.icon.yume.Github
import com.github.yumelira.yumebox.presentation.icon.yume.Meta
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun CircularIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Float = 1f,
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val componentSizes = AppTheme.sizes

    Box(
        modifier =
            modifier
                .padding(start = spacing.space4, end = spacing.space16)
                .requiredSize(componentSizes.settingsIconSlotSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.layout { measurable, _ ->
                        val containerSize = componentSizes.settingsIconContainerSize.roundToPx()
                        val parentSize = componentSizes.settingsIconSlotSize.roundToPx()
                        val offset = (containerSize - parentSize) / 2

                        val placeable =
                            measurable.measure(
                                androidx.compose.ui.unit.Constraints.fixed(
                                    containerSize,
                                    containerSize,
                                )
                            )
                        layout(parentSize, parentSize) { placeable.place(-offset, -offset) }
                    }
                    .size(componentSizes.settingsIconContainerSize)
                    .clip(RoundedCornerShape(radii.radius16))
                    .background(MiuixTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier =
                    Modifier.size(componentSizes.settingsIconGlyphSize)
                        .graphicsLayer(
                            scaleX = iconSize,
                            scaleY = iconSize,
                            transformOrigin = TransformOrigin.Center,
                        ),
            )
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Composable
fun SettingPager(mainInnerPadding: PaddingValues) {
    val viewModel = koinViewModel<SettingViewModel>()
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    val versionInfo = BuildConfig.VERSION_NAME

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingEvent.OpenWebView -> {
                    runCatching { WebViewActivity.start(context, event.url) }
                        .getOrElse { throwable ->
                            context.toast(
                                MLang.Settings.Error.WebviewFailed.format(throwable.message)
                            )
                        }
                }
            }
        }
    }

    Scaffold(topBar = { TopBar(title = MLang.Settings.Title, scrollBehavior = scrollBehavior) }) {
        innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        ) {
            item {
                Title(MLang.Settings.Section.UiSettings)
                Card {
                    ArrowPreference(
                        title = MLang.Settings.UiSettings.App,
                        summary = MLang.Settings.UiSettings.AppSummary,
                        onClick = { navigator.push(Route.AppSettings) },
                        startAction = {
                            CircularIcon(imageVector = Yume.`Settings-2`, contentDescription = null)
                        },
                    )
                    ArrowPreference(
                        title = MLang.Settings.UiSettings.Network,
                        summary = MLang.Settings.UiSettings.NetworkSummary,
                        onClick = { navigator.push(Route.NetworkSettings) },
                        startAction = {
                            CircularIcon(imageVector = Yume.`Wifi-cog`, contentDescription = null)
                        },
                    )
                    ArrowPreference(
                        title = MLang.Settings.UiSettings.Override,
                        summary = MLang.Settings.UiSettings.OverrideSummary,
                        onClick = { navigator.push(Route.Override) },
                        startAction = {
                            CircularIcon(imageVector = Yume.`Git-merge`, contentDescription = null)
                        },
                    )
                    ArrowPreference(
                        title = MLang.Settings.UiSettings.MetaFeatures,
                        summary = MLang.Settings.UiSettings.MetaFeaturesSummary,
                        onClick = { navigator.push(Route.MetaFeature) },
                        startAction = {
                            CircularIcon(imageVector = Yume.Meta, contentDescription = null)
                        },
                    )
                }
            }
            item {
                Title(MLang.Settings.Section.More)

                Card {
                    ArrowPreference(
                        title = MLang.Settings.More.Logs,
                        summary = MLang.Settings.More.LogsSummary,
                        onClick = { navigator.push(Route.Log) },
                        startAction = {
                            CircularIcon(imageVector = Yume.`Scroll-text`, contentDescription = null)
                        },
                    )
                    ArrowPreference(
                        title = MLang.Settings.More.Lab,
                        summary = MLang.Settings.More.LabSummary,
                        onClick = { navigator.push(Route.Feature) },
                        startAction = {
                            CircularIcon(imageVector = Yume.FlaskConical, contentDescription = null)
                        },
                    )
                    ArrowPreference(
                        title = MLang.Settings.More.About,
                        summary = MLang.Settings.More.AboutSummary,
                        onClick = { navigator.push(Route.About) },
                        startAction = {
                            CircularIcon(imageVector = Yume.Github, contentDescription = null)
                        },
                        endActions = { VersionBadge(versionInfo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionBadge(versionInfo: String?) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity

    Surface(
        color = MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle),
        shape = RoundedCornerShape(50),
        modifier =
            Modifier.height(componentSizes.versionBadgeHeight).padding(end = spacing.space12),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            Text(
                text = versionInfo ?: "Unknown",
                style =
                    MiuixTheme.textStyles.footnote1.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
