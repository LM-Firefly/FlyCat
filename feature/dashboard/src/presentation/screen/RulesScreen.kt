/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.feature.dashboard.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.RuntimeRule
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.RulesViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.EdgeLightDefaults
import com.github.lmfirefly.flycat.presentation.component.GlassScaffold
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.component.edgeLight
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.misc.CenteredText
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.component.state.SearchStatus
import com.github.lmfirefly.flycat.presentation.component.state.TopAppBarAnim
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import com.github.lmfirefly.flycat.presentation.util.toast
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RulesScreen(navigator: Navigator) {
    val viewModel = koinViewModel<RulesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filteredRules by viewModel.filteredRules.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing
    val mainListState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainLikePadding = rememberStandalonePageMainPadding()

    var searchStatus by remember {
        mutableStateOf(
            SearchStatus(
                label = FlyTxt.Rules.SearchHint,
                searchText = state.searchQuery,
            )
        )
    }
    val dynamicTopPadding by remember { derivedStateOf { spacing.space12 * (1f - scrollBehavior.state.collapsedFraction) } }
    val listStartPadding = spacing.screenHorizontal
    val listEndPadding = spacing.screenHorizontal
    val currentSearchStatus = remember(searchStatus, filteredRules) {
        searchStatus.copy(resultStatus = when {
                searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                filteredRules.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                else -> SearchStatus.ResultStatus.SHOW
            }
        )
    }

    LaunchedEffect(state.toggleError) {
        val error = state.toggleError ?: return@LaunchedEffect
        context.toast(FlyTxt.Rules.Message.ToggleFailed.format(error))
        viewModel.consumeToggleError()
    }

    LaunchedEffect(searchStatus.searchText) {
        if (searchStatus.searchText != state.searchQuery) {
            viewModel.setSearchQuery(searchStatus.searchText)
        }
    }

    LaunchedEffect(state.searchQuery) {
        if (searchStatus.searchText != state.searchQuery) {
            searchStatus = searchStatus.copy(searchText = state.searchQuery)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassScaffold(
            topBar = {
                currentSearchStatus.TopAppBarAnim {
                    TopBar(
                        title = FlyTxt.Rules.Title,
                        scrollBehavior = scrollBehavior,
                        navigationIconPadding = 0.dp,
                        navigationIcon = { NavigationBackIcon(navigator = navigator) },
                        bottomContent = {
                            RulesSearchBar(
                                query = searchStatus.searchText,
                                onQueryChange = { searchStatus = searchStatus.copy(searchText = it) },
                                label = searchStatus.label,
                                topPadding = dynamicTopPadding,
                                startPadding = listStartPadding,
                                endPadding = listEndPadding,
                            )
                        },
                    )
                }
            },
        ) { innerPadding ->
            if (currentSearchStatus.shouldCollapse()) {
                when {
                    state.isLoading && state.rules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.Loading,
                            secondLine = FlyTxt.Rules.Empty.LoadingHint,
                        )
                    }

                    !state.isRunning && state.rules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.NotRunning,
                            secondLine = FlyTxt.Rules.Empty.NotRunningHint,
                        )
                    }

                    state.rules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.NoRules,
                            secondLine = FlyTxt.Rules.Empty.NoRulesHint,
                        )
                    }

                    searchStatus.searchText.isNotBlank() && filteredRules.isEmpty() -> {
                        CenteredText(
                            firstLine = FlyTxt.Rules.Empty.NoResults,
                            secondLine = searchStatus.searchText,
                        )
                    }

                    else -> {
                        ScreenLazyColumn(
                            scrollBehavior = scrollBehavior,
                            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
                            lazyListState = mainListState,
                        ) {
                            items(items = filteredRules, key = { it.index }, contentType = { "rule" }) { rule ->
                                RuleCard(
                                    rule = rule,
                                    enabled = !rule.disabled,
                                    toggling = rule.index in state.togglingIndexes,
                                    onEnabledChange = { enabled ->
                                        viewModel.setRuleEnabled(rule.index, enabled)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val isLightTheme = !isDarkTheme()
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainerHigh
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        color = Color.Transparent,
        label = label,
        onSearch = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        },
        expanded = false,
        onExpandedChange = {},
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = FlyTxt.Component.Editor.Action.Search,
                modifier = Modifier.size(AppTheme.sizes.searchIconTouchTarget).padding(start = AppTheme.spacing.space16, end = AppTheme.spacing.space8),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(AppTheme.sizes.searchIconTouchTarget).padding(start = AppTheme.spacing.space8, end = AppTheme.spacing.space16),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = FlyTxt.Component.Button.Clear,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = startPadding, end = endPadding).padding(top = topPadding, bottom = AppTheme.sizes.searchBarBottomPadding).clip(CircleShape).glassCardSurface(CircleShape, fallbackColor = surfaceColor).edgeLight(
            shape = CircleShape,
            color = EdgeLightDefaults.tint(surfaceColor, isLightTheme),
            width = EdgeLightDefaults.width(isLightTheme),
            blurRadius = EdgeLightDefaults.BlurRadius,
            blendMode = EdgeLightDefaults.blendMode(isLightTheme),
        ),
    )
}

@Composable
private fun RuleCard(
    rule: RuntimeRule,
    enabled: Boolean,
    toggling: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val spacing = AppTheme.spacing
    val totalCount = rule.hitCount + rule.missCount
    val hitRate = if (totalCount > 0L) {
        String.format(Locale.getDefault(), "%.1f%%", rule.hitCount * 100.0 / totalCount)
    } else {
        "-"
    }

    Card(modifier = Modifier.padding(vertical = spacing.space4)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Text(
                    text = rule.payload.ifBlank { "#${rule.index}" },
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !toggling,
                )
            }
            Spacer(modifier = Modifier.size(spacing.space4))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Text(
                    text = "${rule.type.ifBlank { "-" }}  ·  ${rule.proxy.ifBlank { "-" }}",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = FlyTxt.Rules.Label.HitRate.format(hitRate),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}
