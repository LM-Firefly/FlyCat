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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.data.model.TunStack
import com.github.yumelira.yumebox.presentation.component.AppTextFieldDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
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
 * The root Tun "service config" sub-page (reached from the network-settings Advanced section when the
 * Tun run mode is selected). Holds the kernel-TUN geometry the core opens for itself — separate from
 * the VpnService page so each mode's options live behind its own entry. Shares
 * [NetworkSettingsViewModel] with the picker screen.
 */
@Composable
fun TunServiceOptionsScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val ifName by viewModel.tunIfName.state.collectAsState()
    val mtu by viewModel.tunMtu.state.collectAsState()
    val stack by viewModel.tunStack.state.collectAsState()
    val autoRoute by viewModel.tunAutoRoute.state.collectAsState()
    val strictRoute by viewModel.tunStrictRoute.state.collectAsState()
    val autoRedirect by viewModel.tunAutoRedirect.state.collectAsState()
    val dnsMode by viewModel.tunDnsMode.state.collectAsState()
    val enableIPv6 by viewModel.enableIPv6.state.collectAsState()

    Scaffold(
        topBar = {
            TopBar(title = MLang.NetworkSettings.TunOptions.Title, scrollBehavior = scrollBehavior)
        }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.NetworkSettings.RunMode.TunTitle)
                Card {
                    TextInputArrowItem(
                        title = MLang.NetworkSettings.TunOptions.IfNameTitle,
                        value = ifName,
                        keyboardType = KeyboardType.Text,
                        onConfirm = viewModel::onTunIfNameChange,
                    )
                    TextInputArrowItem(
                        title = MLang.NetworkSettings.TunOptions.MtuTitle,
                        value = mtu.toString(),
                        keyboardType = KeyboardType.Number,
                        onConfirm = { it.toIntOrNull()?.let(viewModel::onTunMtuChange) },
                    )
                    PreferenceEnumItem(
                        title = MLang.NetworkSettings.TunOptions.StackTitle,
                        currentValue = stack,
                        items =
                            listOf(
                                MLang.NetworkSettings.TunOptions.StackSystem,
                                MLang.NetworkSettings.TunOptions.StackGVisor,
                                MLang.NetworkSettings.TunOptions.StackMixed,
                            ),
                        values = listOf(TunStack.System, TunStack.GVisor, TunStack.Mixed),
                        onValueChange = viewModel::onTunStackChange,
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
                        title = MLang.NetworkSettings.TunOptions.AutoRouteTitle,
                        checked = autoRoute,
                        onCheckedChange = viewModel::onTunAutoRouteChange,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.TunOptions.StrictRouteTitle,
                        checked = strictRoute,
                        onCheckedChange = viewModel::onTunStrictRouteChange,
                    )
                    PreferenceSwitchItem(
                        title = MLang.NetworkSettings.TunOptions.AutoRedirectTitle,
                        checked = autoRedirect,
                        onCheckedChange = viewModel::onTunAutoRedirectChange,
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

/**
 * A tappable row that opens an [AppTextFieldDialog] to edit a single text/number value — same shape as
 * the app-settings "custom user agent" editor, packaged so the row and its dialog live together.
 */
@Composable
internal fun TextInputArrowItem(
    title: String,
    value: String,
    keyboardType: KeyboardType,
    onConfirm: (String) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    PreferenceArrowItem(
        title = title,
        summary = value,
        onClick = {
            field = TextFieldValue(value, TextRange(value.length))
            showDialog.value = true
        },
        holdDownState = showDialog.value,
    )

    val confirm = {
        onConfirm(field.text)
        focusManager.clearFocus()
        showDialog.value = false
    }
    AppTextFieldDialog(
        show = showDialog.value,
        title = title,
        textFieldValue = field,
        onTextFieldValueChange = { field = it },
        onDismissRequest = {
            showDialog.value = false
            focusManager.clearFocus()
        },
        onConfirm = confirm,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onDone = { confirm() }),
    )
}
