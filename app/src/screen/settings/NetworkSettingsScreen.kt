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

@file:Suppress("DuplicatedCode", "FunctionName")

package com.github.yumeyucca.yumebox.screen.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.data.model.AccessControlMode
import com.github.yumeyucca.yumebox.data.model.RunMode
import com.github.yumeyucca.yumebox.presentation.component.*
import com.github.yumeyucca.yumebox.presentation.navigation.Route
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * Network settings entry point. Top: a "run mode" radio picker — one card per mode.
 * `RunMode.VpnService` is always available; the root-only Tun card is greyed out unless
 * root is granted. Below: advanced options (service config + disable-overrides) and access control.
 * The former parallel HTTP "system proxy" run mode is gone.
 */
@Composable
fun NetworkSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val screen by viewModel.networkScreenState.collectAsState()
    val disableAllOverride = screen.disableAllOverride
    val accessControlMode = screen.accessControlMode
    val runMode = screen.runMode
    // Root Tun is only selectable when root is granted; otherwise its card is greyed
    // out.
    val rootAvailable = screen.rootAvailable

    Scaffold(
        topBar = { TopBar(title = YumeTxt.NetworkSettings.Title, scrollBehavior = scrollBehavior) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(YumeTxt.NetworkSettings.RunMode.SectionTitle)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(
                        title = YumeTxt.NetworkSettings.RunMode.VpnServiceTitle,
                        summary = YumeTxt.NetworkSettings.RunMode.VpnServiceSummary,
                        selected = runMode == RunMode.VpnService,
                        enabled = true,
                        onSelect = { viewModel.onRunModeChange(RunMode.VpnService) },
                    )
                    ModeCard(
                        title = YumeTxt.NetworkSettings.RunMode.TunTitle,
                        summary = YumeTxt.NetworkSettings.RunMode.TunSummary,
                        selected = runMode == RunMode.Tun,
                        enabled = rootAvailable,
                        onSelect = { viewModel.onRunModeChange(RunMode.Tun) },
                    )
                }
            }
            item {
                Title(YumeTxt.NetworkSettings.Section.Advanced)
                Card {
                    PreferenceArrowItem(
                        title = YumeTxt.NetworkSettings.Section.VpnOptions,
                        // Each mode's service config lives behind its own page.
                        onClick = {
                            when (runMode) {
                                RunMode.VpnService -> navigator.push(Route.VpnServiceOptions)
                                RunMode.Tun -> navigator.push(Route.TunServiceOptions)
                            }
                        },
                    )
                    PreferenceSwitchItem(
                        title = YumeTxt.NetworkSettings.Advanced.DisableOverrideTitle,
                        checked = disableAllOverride,
                        onCheckedChange = viewModel::onDisableAllOverrideChange,
                    )
                }
            }
            item {
                Title(YumeTxt.NetworkSettings.Section.ProxyOptions)
                Card {
                    PreferenceEnumItem(
                        title = YumeTxt.NetworkSettings.ProxyOptions.AccessControlModeTitle,
                        currentValue = accessControlMode,
                        items =
                            listOf(
                                YumeTxt.NetworkSettings.ProxyOptions.AllowAll,
                                YumeTxt.NetworkSettings.ProxyOptions.AllowSelected,
                                YumeTxt.NetworkSettings.ProxyOptions.RejectSelected,
                            ),
                        values = AccessControlMode.entries,
                        onValueChange = viewModel::onAccessControlModeChange,
                    )
                    PreferenceArrowItem(
                        title = YumeTxt.NetworkSettings.ProxyOptions.ManageAccessControlTitle,
                        onClick = { navigator.push(Route.AccessControl) },
                    )
                }
            }
        }
    }
}

/**
 * A run-mode option: its own card with a leading radio and a title/summary. A disabled mode greys
 * its text and radio and can't be selected (the root Tun card when root isn't granted).
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
