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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.WebViewActivity
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.*
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.navigation.SettingsDetailRootRoutes
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.viewmodel.SettingEvent
import com.github.yumelira.yumebox.presentation.viewmodel.SettingViewModel
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.YumeTxt
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
fun SettingPager(
    mainInnerPadding: PaddingValues,
    @Suppress("UNUSED_PARAMETER") windowLayoutMode: WindowLayoutMode = WindowLayoutMode.Compact,
) {
    val viewModel = koinViewModel<SettingViewModel>()
    val scrollBehavior = MiuixScrollBehavior()
    val rootNavigator = LocalNavigator.current
    val detailNavigator = LocalDetailNavigator.current
    val context = LocalContext.current
    val versionInfo = "v${BuildConfig.BASE_VERSION}"

    // Highlight only after the user opens a settings item (no default selection).
    var selectedRootKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRootRoute =
        remember(selectedRootKey) {
            selectedRootKey?.let { key ->
                SettingsDetailRootRoutes.firstOrNull { it::class.simpleName == key }
            }
        }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingEvent.OpenWebView -> {
                    runCatching { WebViewActivity.start(context, event.url) }
                        .getOrElse { throwable ->
                            context.toast(
                                YumeTxt.Settings.Error.WebviewFailed.format(throwable.message)
                            )
                        }
                }
            }
        }
    }

    // Phone: push on root navigator. Tablet shell: replace right pane.
    val openRoot: (Route) -> Unit = { route ->
        selectedRootKey = route::class.simpleName
        if (detailNavigator != null) {
            detailNavigator.replaceAll(listOf(route))
        } else {
            rootNavigator.push(route)
        }
    }

    SettingsMasterList(
        mainInnerPadding = mainInnerPadding,
        scrollBehavior = scrollBehavior,
        versionInfo = versionInfo,
        selectedRoute = selectedRootRoute,
        onOpen = openRoot,
    )
}

@Composable
private fun SettingsMasterList(
    mainInnerPadding: PaddingValues,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    versionInfo: String,
    selectedRoute: Route?,
    onOpen: (Route) -> Unit,
) {
    Scaffold(topBar = { TopBar(title = YumeTxt.Settings.Title, scrollBehavior = scrollBehavior) }) {
        innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        ) {
            item {
                Title(YumeTxt.Settings.Section.UiSettings)
                Card {
                    SettingsRootPreference(
                        title = YumeTxt.Settings.UiSettings.App,
                        summary = YumeTxt.Settings.UiSettings.AppSummary,
                        selected = selectedRoute == Route.AppSettings,
                        onClick = { onOpen(Route.AppSettings) },
                        icon = Yume.`Settings-2`,
                    )
                    SettingsRootPreference(
                        title = YumeTxt.Settings.UiSettings.Network,
                        summary = YumeTxt.Settings.UiSettings.NetworkSummary,
                        selected = selectedRoute == Route.NetworkSettings,
                        onClick = { onOpen(Route.NetworkSettings) },
                        icon = Yume.`Wifi-cog`,
                    )
                    SettingsRootPreference(
                        title = YumeTxt.Settings.UiSettings.Override,
                        summary = YumeTxt.Settings.UiSettings.OverrideSummary,
                        selected = selectedRoute == Route.Override,
                        onClick = { onOpen(Route.Override) },
                        icon = Yume.`Git-merge`,
                    )
                    SettingsRootPreference(
                        title = YumeTxt.Settings.UiSettings.MetaFeatures,
                        summary = YumeTxt.Settings.UiSettings.MetaFeaturesSummary,
                        selected = selectedRoute == Route.MetaFeature,
                        onClick = { onOpen(Route.MetaFeature) },
                        icon = Yume.Meta,
                    )
                }
            }
            item {
                Title(YumeTxt.Settings.Section.More)
                Card {
                    SettingsRootPreference(
                        title = YumeTxt.Settings.More.Lab,
                        summary = YumeTxt.Settings.More.LabSummary,
                        selected = selectedRoute == Route.Feature,
                        onClick = { onOpen(Route.Feature) },
                        icon = Yume.FlaskConical,
                    )
                    ArrowPreference(
                        title = YumeTxt.Settings.More.About,
                        summary = YumeTxt.Settings.More.AboutSummary,
                        onClick = { onOpen(Route.About) },
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
private fun SettingsRootPreference(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val opacity = AppTheme.opacity
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle)
                    else MiuixTheme.colorScheme.surface.copy(alpha = 0f)
                )
    ) {
        ArrowPreference(
            title = title,
            summary = summary,
            onClick = onClick,
            startAction = { CircularIcon(imageVector = icon, contentDescription = null) },
        )
    }
}

@Composable
private fun VersionBadge(versionInfo: String) {
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
                text = versionInfo,
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
