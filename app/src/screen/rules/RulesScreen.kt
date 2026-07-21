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

package com.github.yumelira.yumebox.screen.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.core.model.RuntimeRule
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Runtime rules from `GET /rules`. Each card has an enable switch wired to
 * `PATCH /rules/disable`. This is **not** the custom-routing editor.
 */
@Composable
fun RulesScreen(navigator: Navigator) {
    val viewModel = koinViewModel<RulesViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(uiState.toggleError) {
        val error = uiState.toggleError ?: return@LaunchedEffect
        context.toast(YumeTxt.Rules.Message.ToggleFailed.replace("%s", error))
        viewModel.consumeToggleError()
    }

    Scaffold(
        topBar = {
            TopBar(title = YumeTxt.Rules.Title, scrollBehavior = scrollBehavior)
        }
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.rules.isEmpty() -> {
                CenteredText(
                    firstLine = YumeTxt.Rules.Empty.Loading,
                    secondLine = YumeTxt.Rules.Empty.LoadingHint,
                )
            }
            !uiState.isRunning && uiState.rules.isEmpty() -> {
                CenteredText(
                    firstLine = YumeTxt.Rules.Empty.NotRunning,
                    secondLine = YumeTxt.Rules.Empty.NotRunningHint,
                )
            }
            uiState.rules.isEmpty() -> {
                CenteredText(
                    firstLine = YumeTxt.Rules.Empty.NoRules,
                    secondLine = YumeTxt.Rules.Empty.NoRulesHint,
                )
            }
            else -> {
                val mainLikePadding = rememberStandalonePageMainPadding()
                ScreenLazyColumn(
                    scrollBehavior = scrollBehavior,
                    innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
                ) {
                    items(
                        items = uiState.rules,
                        key = { it.index },
                        contentType = { "rule" },
                    ) { rule ->
                        RuleCard(
                            rule = rule,
                            enabled = !rule.disabled,
                            toggling = rule.index in uiState.togglingIndexes,
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

@Composable
private fun RuleCard(
    rule: RuntimeRule,
    enabled: Boolean,
    toggling: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val spacing = AppTheme.spacing
    val totalCount = rule.hitCount + rule.missCount
    val hitRate =
        if (totalCount > 0L) {
            String.format(Locale.getDefault(), "%.1f%%", rule.hitCount * 100.0 / totalCount)
        } else {
            "-"
        }
    Card(modifier = Modifier.padding(vertical = spacing.space4)) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = spacing.space16, vertical = spacing.space12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Text(
                    text = rule.payload.ifBlank { "#" + rule.index },
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
                    text =
                        "${rule.type.ifBlank { "-" }}  ·  " +
                            rule.proxy.ifBlank { "-" },
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = YumeTxt.Rules.Label.HitRate.replace("%s", hitRate),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}
