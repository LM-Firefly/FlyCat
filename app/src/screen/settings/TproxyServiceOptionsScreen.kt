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
import androidx.compose.ui.text.input.KeyboardType
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
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
 * Root TPROXY "service config" sub-page: shares the DNS mode / IPv6 knobs with the Tun path and adds its
 * own transparent-proxy port. Per-app scoping is the shared access-control setting. Shares [NetworkSettingsViewModel].
 */
@Composable
fun TproxyServiceOptionsScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val port by viewModel.tproxyPort.state.collectAsState()
    val dnsMode by viewModel.tunDnsMode.state.collectAsState()
    val enableIPv6 by viewModel.enableIPv6.state.collectAsState()

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.NetworkSettings.TproxyOptions.Title,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.NetworkSettings.RunMode.TproxyTitle)
                Card {
                    TextInputArrowItem(
                        title = MLang.NetworkSettings.TproxyOptions.PortTitle,
                        value = port.toString(),
                        keyboardType = KeyboardType.Number,
                        onConfirm = { it.toIntOrNull()?.let(viewModel::onTproxyPortChange) },
                    )
                    PreferenceEnumItem(
                        title = MLang.NetworkSettings.TunOptions.DnsModeTitle,
                        currentValue = dnsMode,
                        items =
                            listOf(
                                MLang.NetworkSettings.TunOptions.DnsRedirHost,
                                MLang.NetworkSettings.TunOptions.DnsFakeIp,
                            ),
                        values = listOf(TunDnsMode.RedirHost, TunDnsMode.FakeIp),
                        onValueChange = viewModel::onTunDnsModeChange,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.TunOptions.Ipv6Title,
                        checked = enableIPv6,
                        onCheckedChange = viewModel::onEnableIPv6Change,
                    )
                }
            }
        }
    }
}
