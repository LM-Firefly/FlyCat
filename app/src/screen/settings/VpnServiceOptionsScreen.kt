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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * The VpnService "service config" sub-page (reached from the network-settings Advanced section). Holds
 * every knob the userspace VpnService/gVisor path exposes; the TCP/IP stack is fixed to gVisor so there
 * is no stack picker. Shares [NetworkSettingsViewModel] with the picker screen.
 */
@Composable
fun VpnServiceOptionsScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val tunOptions by viewModel.tunServiceOptionsUiState.collectAsState()

    Scaffold(
        topBar = {
            TopBar(title = MLang.NetworkSettings.Section.VpnOptions, scrollBehavior = scrollBehavior)
        }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.NetworkSettings.RunMode.VpnServiceTitle)
                Card {
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.VpnOptions.BypassPrivateTitle,
                        checked = tunOptions.common.bypassPrivateNetwork,
                        onCheckedChange = viewModel::onBypassPrivateNetworkChange,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.VpnOptions.DnsHijackTitle,
                        checked = tunOptions.common.dnsHijack,
                        onCheckedChange = viewModel::onDnsHijackChange,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.VpnOptions.EnableIpv6Title,
                        checked = tunOptions.common.enableIPv6,
                        onCheckedChange = viewModel::onEnableIPv6Change,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.VpnOptions.AllowBypassTitle,
                        checked = tunOptions.allowBypass,
                        onCheckedChange = viewModel::onAllowBypassChange,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.VpnOptions.SystemProxyTitle,
                        checked = tunOptions.systemProxy,
                        onCheckedChange = viewModel::onSystemProxyChange,
                    )
                }
            }
        }
    }
}
