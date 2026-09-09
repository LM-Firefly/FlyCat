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

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceSwitchItem
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * Settings that are meaningful for the standalone root eBPF socket bridge.
 * Shares [NetworkSettingsViewModel] with the picker screen.
 */
@Composable
fun EbpfServiceOptionsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val tunServiceOptionsUiState by viewModel.tunServiceOptionsUiState.collectAsStateWithLifecycle()
    val ebpfOptions by viewModel.ebpfServiceOptionsUiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.NetworkSettings.RunMode.EbpfTitle,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(FlyTxt.NetworkSettings.RunMode.EbpfTitle)
                Card {
                    PreferenceSwitchItem(
                        title = FlyTxt.NetworkSettings.VpnOptions.BypassPrivateTitle,
                        checked = tunServiceOptionsUiState.common.bypassPrivateNetwork,
                        onCheckedChange = viewModel::onBypassPrivateNetworkChange,
                    )
                    PreferenceSwitchItem(
                        title = FlyTxt.NetworkSettings.VpnOptions.DnsHijackTitle,
                        checked = tunServiceOptionsUiState.common.dnsHijack,
                        onCheckedChange = viewModel::onDnsHijackChange,
                    )
                    PreferenceSwitchItem(
                        title = FlyTxt.NetworkSettings.VpnOptions.EnableIpv6Title,
                        checked = tunServiceOptionsUiState.common.enableIPv6,
                        onCheckedChange = viewModel::onEnableIPv6Change,
                    )
                    PreferenceSwitchItem(
                        title = FlyTxt.NetworkSettings.EbpfOptions.BypassCnTitle,
                        summary = FlyTxt.NetworkSettings.EbpfOptions.BypassCnSummary,
                        checked = ebpfOptions.bypassCn,
                        onCheckedChange = viewModel::onEbpfBypassCnChange,
                    )
                }
            }
        }
    }
}
