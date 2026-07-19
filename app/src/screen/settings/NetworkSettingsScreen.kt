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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.model.AccessControlMode
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.navigation.Route
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * Network settings entry point. Top: a "run mode" radio picker — one card per mode. Only
 * [RunMode.VpnService] is functional today; the root-only Tun / TPROXY modes are shown but disabled
 * until the libsu path lands. Below: advanced options (service config + disable-overrides) and
 * access control. The former parallel HTTP "system proxy" run mode is gone.
 */
@Composable
fun NetworkSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val disableAllOverride by viewModel.disableAllOverride.state.collectAsState()
    val accessControlMode by viewModel.accessControlMode.state.collectAsState()
    val runMode by viewModel.runMode.state.collectAsState()
    val context = LocalContext.current

    // A root mode tapped without root access surfaces the block reason as a toast.
    LaunchedEffect(Unit) {
        viewModel.rootBlocked.collect { message -> context.toast(message) }
    }

    Scaffold(
        topBar = { TopBar(title = MLang.NetworkSettings.Title, scrollBehavior = scrollBehavior) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.NetworkSettings.RunMode.SectionTitle)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(
                        title = MLang.NetworkSettings.RunMode.VpnServiceTitle,
                        summary = MLang.NetworkSettings.RunMode.VpnServiceSummary,
                        selected = runMode == RunMode.VpnService,
                        enabled = true,
                        onSelect = { viewModel.onRunModeChange(RunMode.VpnService) },
                    )
                    ModeCard(
                        title = MLang.NetworkSettings.RunMode.TunTitle,
                        summary = MLang.NetworkSettings.RunMode.TunSummary,
                        selected = runMode == RunMode.Tun,
                        enabled = true,
                        onSelect = { viewModel.onRunModeChange(RunMode.Tun) },
                    )
                    ModeCard(
                        title = MLang.NetworkSettings.RunMode.TproxyTitle,
                        summary = MLang.NetworkSettings.RunMode.TproxySummary,
                        selected = runMode == RunMode.Tproxy,
                        enabled = true,
                        onSelect = { viewModel.onRunModeChange(RunMode.Tproxy) },
                    )
                }
            }
            item {
                Title(MLang.NetworkSettings.Section.Advanced)
                Card {
                    PreferenceArrowItem(
                        title = MLang.NetworkSettings.Section.VpnOptions,
                        // Each mode's service config lives behind its own page; open the one that
                        // matches the selected run mode (Tproxy reuses the Tun geometry page for now).
                        onClick = {
                            when (runMode) {
                                RunMode.VpnService -> navigator.push(Route.VpnServiceOptions)
                                RunMode.Tun -> navigator.push(Route.TunServiceOptions)
                                RunMode.Tproxy -> navigator.push(Route.TproxyServiceOptions)
                            }
                        },
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.Advanced.DisableOverrideTitle,
                        checked = disableAllOverride,
                        // Only the root Tun / TPROXY modes do their own routing where skipping the
                        // override chain makes sense; it stays disabled under VPN 服务.
                        enabled = runMode == RunMode.Tun || runMode == RunMode.Tproxy,
                        onCheckedChange = viewModel::onDisableAllOverrideChange,
                    )
                }
            }
            item {
                Title(MLang.NetworkSettings.Section.ProxyOptions)
                Card {
                    PreferenceEnumItem(
                        title = MLang.NetworkSettings.ProxyOptions.AccessControlModeTitle,
                        currentValue = accessControlMode,
                        items =
                            listOf(
                                MLang.NetworkSettings.ProxyOptions.AllowAll,
                                MLang.NetworkSettings.ProxyOptions.AllowSelected,
                                MLang.NetworkSettings.ProxyOptions.RejectSelected,
                            ),
                        values = AccessControlMode.entries,
                        onValueChange = viewModel::onAccessControlModeChange,
                    )
                    PreferenceArrowItem(
                        title = MLang.NetworkSettings.ProxyOptions.ManageAccessControlTitle,
                        onClick = { navigator.push(Route.AccessControl) },
                    )
                }
            }
        }
    }
}

/**
 * A run-mode option: its own card with a leading radio and a title/summary. A disabled mode greys its
 * text and radio and can't be selected. Root modes (Tun/TPROXY) stay enabled and probe root on tap.
 */
@Composable
private fun ModeCard(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Card {
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = if (enabled) onSelect else null,
            startAction = {
                RadioButton(
                    selected = selected,
                    onClick = if (enabled) onSelect else null,
                    enabled = enabled,
                )
            },
        )
    }
}
