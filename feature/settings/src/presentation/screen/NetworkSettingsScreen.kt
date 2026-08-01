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

package com.github.yumelira.yumebox.feature.settings.presentation.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.model.TunStack
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.CommonTunOptionsUiState
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.NetworkSettingsUiState
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.RootTunServiceOptionsUiState
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.TunServiceOptionsUiState
import com.github.yumelira.yumebox.platform.util.VpnUtils
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppTextFieldDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
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
import tf.gal.yumebox.locale.FlyTxt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun NetworkSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tunServiceOptionsUiState by viewModel.tunServiceOptionsUiState.collectAsStateWithLifecycle()
    val rootTunServiceOptionsUiState by viewModel.rootTunServiceOptionsUiState.collectAsStateWithLifecycle()
    val accessControlMode by viewModel.accessControlMode.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.errors.collect { message -> context.toast(message) } }

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                viewModel.onRunModeChange(RunMode.Vpn)
            } else {
                context.toast(FlyTxt.NetworkSettings.Error.VpnDenied)
            }
        }

    Scaffold(
        topBar = { TopBar(title = FlyTxt.NetworkSettings.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                NetworkVpnServiceSection(
                    viewModel = viewModel,
                    configuredMode = uiState.configuredMode,
                    vpnPermissionLauncher = vpnPermissionLauncher,
                )
            }
            item {
                NetworkServiceOptionsSection(
                    viewModel = viewModel,
                    uiState = uiState,
                    tunServiceOptionsUiState = tunServiceOptionsUiState,
                    rootTunServiceOptionsUiState = rootTunServiceOptionsUiState,
                )
            }
            item {
                NetworkProxyOptionsSection(
                    navigator = navigator,
                    accessControlMode = accessControlMode,
                    showAccessControlMode = uiState.showAccessControlMode,
                    onAccessControlModeChange = viewModel::onAccessControlModeChange,
                )
            }
        }
    }
}

@Composable
private fun NetworkVpnServiceSection(
    viewModel: NetworkSettingsViewModel,
    configuredMode: RunMode,
    vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Title(FlyTxt.NetworkSettings.Section.RunMode)
    Card {
        PreferenceEnumItem(
            title = FlyTxt.NetworkSettings.RunMode.RouteTrafficTitle,
            currentValue = configuredMode,
            items =
                listOf(
                    FlyTxt.NetworkSettings.RunMode.VpnMode,
                    FlyTxt.NetworkSettings.RunMode.TunMode,
                ),
            values = listOf(RunMode.Vpn, RunMode.Tun),
            onValueChange = { mode ->
                when (mode) {
                    RunMode.Vpn -> {
                        if (!VpnUtils.checkVpnPermission(context)) {
                            VpnUtils.getVpnPermissionIntent(context)
                                ?.let(vpnPermissionLauncher::launch)
                                ?: viewModel.onRunModeChange(mode)
                        } else {
                            viewModel.onRunModeChange(mode)
                        }
                    }

                    RunMode.Tun -> {
                        coroutineScope.launch {
                            val rootStatus = viewModel.evaluateRootAccess()
                            if (!rootStatus.canStartRootTun) {
                                context.toast(rootStatus.rootTunBlockedMessage())
                                return@launch
                            }
                            viewModel.onRunModeChange(mode)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun NetworkServiceOptionsSection(
    viewModel: NetworkSettingsViewModel,
    uiState: NetworkSettingsUiState,
    tunServiceOptionsUiState: TunServiceOptionsUiState,
    rootTunServiceOptionsUiState: RootTunServiceOptionsUiState,
) {
    if (!uiState.showServiceOptions) return

    val commonActions =
        remember(viewModel) {
            CommonTunOptionActions(
                onBypassPrivateNetworkChange = viewModel::onBypassPrivateNetworkChange,
                onDnsHijackChange = viewModel::onDnsHijackChange,
                onEnableIPv6Change = viewModel::onEnableIPv6Change,
                onTunStackChange = viewModel::onTunStackChange,
            )
        }

    Title(FlyTxt.NetworkSettings.Section.VpnOptions)
    Card {
        when (uiState.configuredMode) {
            RunMode.Vpn -> {
                TunServiceOptions(
                    state = tunServiceOptionsUiState,
                    actions = TunServiceOptionActions(
                        common = commonActions,
                        onAllowBypassChange = viewModel::onAllowBypassChange,
                        onSystemProxyChange = viewModel::onSystemProxyChange,
                    ),
                )
            }
            RunMode.Tun -> {
                RootTunServiceOptions(
                    state = rootTunServiceOptionsUiState,
                    showFakeIpRange = uiState.showFakeIpRange,
                    actions = remember(viewModel, commonActions) {
                        RootTunServiceOptionActions(
                            common = commonActions,
                            onTunAutoRouteChange = viewModel::onTunAutoRouteChange,
                            onTunStrictRouteChange = viewModel::onTunStrictRouteChange,
                            ontunAutoRedirectChange =
                                viewModel::ontunAutoRedirectChange,
                            ontunDnsModeChange = viewModel::ontunDnsModeChange,
                            ontunIfNameDraftChange = viewModel::ontunIfNameDraftChange,
                            ontunMtuDraftChange = viewModel::ontunMtuDraftChange,
                            ontunFakeIpRangeDraftChange =
                                viewModel::ontunFakeIpRangeDraftChange,
                            ontunFakeIpRange6DraftChange =
                                viewModel::ontunFakeIpRange6DraftChange,
                            committunIfName = viewModel::committunIfName,
                            committunMtu = viewModel::committunMtu,
                            committunFakeIpRange = viewModel::committunFakeIpRange,
                            committunFakeIpRange6 = viewModel::committunFakeIpRange6,
                        )
                    },
                )
            }
        }
        // Disable all overrides
        PreferenceSwitchItem(
            title = FlyTxt.NetworkSettings.VpnOptions.DisableOverrideTitle,
            checked = viewModel.disableAllOverride.value,
            onCheckedChange = viewModel::onDisableAllOverrideChange,
        )
    }
}

@Composable
private fun NetworkProxyOptionsSection(
    navigator: Navigator,
    accessControlMode: AccessControlMode,
    showAccessControlMode: Boolean,
    onAccessControlModeChange: (AccessControlMode) -> Unit,
) {
    Title(FlyTxt.NetworkSettings.Section.ProxyOptions)
    Card {
        if (showAccessControlMode) {
            PreferenceEnumItem(
                title = FlyTxt.NetworkSettings.ProxyOptions.AccessControlModeTitle,
                currentValue = accessControlMode,
                items =
                    listOf(
                        FlyTxt.NetworkSettings.ProxyOptions.AllowAll,
                        FlyTxt.NetworkSettings.ProxyOptions.AllowSelected,
                        FlyTxt.NetworkSettings.ProxyOptions.RejectSelected,
                    ),
                values =
                    listOf(
                        AccessControlMode.ALLOW_ALL,
                        AccessControlMode.ALLOW_SPECIFIC,
                        AccessControlMode.DENY_SPECIFIC
                    ),
                onValueChange = onAccessControlModeChange,
            )
        }
        PreferenceArrowItem(
            title = FlyTxt.NetworkSettings.ProxyOptions.ManageAccessControlTitle,
            onClick = { navigator.push(Route.AccessControl) },
        )
    }
}

@Composable
private fun TunServiceOptions(state: TunServiceOptionsUiState, actions: TunServiceOptionActions) {
    CommonTunServiceOptions(
        state = state.common,
        actions = actions.common,
        extraOptions = {
            PreferenceSwitchItem(
                title = FlyTxt.NetworkSettings.VpnOptions.AllowBypassTitle,                checked = state.allowBypass,
                onCheckedChange = actions.onAllowBypassChange,
            )
            PreferenceSwitchItem(
                title = FlyTxt.NetworkSettings.VpnOptions.SystemProxyTitle,                checked = state.systemProxy,
                onCheckedChange = actions.onSystemProxyChange,
            )
        },
    )
}

@Composable
private fun RootTunServiceOptions(
    state: RootTunServiceOptionsUiState,
    showFakeIpRange: Boolean,
    actions: RootTunServiceOptionActions,
) {
    CommonTunServiceOptions(
        state = state.common,
        actions = actions.common,
        extraOptions = {
            RootTunAdvancedOptions(
                state = state,
                showFakeIpRange = showFakeIpRange,
                actions = actions,
            )
        },
    )
}

@Composable
private fun RootTunAdvancedOptions(
    state: RootTunServiceOptionsUiState,
    showFakeIpRange: Boolean,
    actions: RootTunServiceOptionActions,
) {
    var editDialog by remember { mutableStateOf<RootTunEditDialogState?>(null) }

    RootTunIdentityOptions(
        tunIfNameDraft = state.tunIfNameDraft,
        tunMtuDraft = state.tunMtuDraft,
        onEditIfName = { editDialog = RootTunEditDialogState.IfName },
        onEditMtu = { editDialog = RootTunEditDialogState.Mtu },
    )
    RootTunRoutingOptions(
        tunAutoRoute = state.tunAutoRoute,
        tunStrictRoute = state.tunStrictRoute,
        tunAutoRedirect = state.tunAutoRedirect,
        tunDnsMode = state.tunDnsMode,
        onTunAutoRouteChange = actions.onTunAutoRouteChange,
        onTunStrictRouteChange = actions.onTunStrictRouteChange,
        ontunAutoRedirectChange = actions.ontunAutoRedirectChange,
        ontunDnsModeChange = actions.ontunDnsModeChange,
    )
    RootTunFakeIpOptions(
        showFakeIpRange = showFakeIpRange,
        tunFakeIpRangeDraft = state.tunFakeIpRangeDraft,
        tunFakeIpRange6Draft = state.tunFakeIpRange6Draft,
        onEditFakeIpRange = { editDialog = RootTunEditDialogState.FakeIpRange },
        onEditFakeIpRange6 = { editDialog = RootTunEditDialogState.FakeIpRange6 },
    )

    RootTunEditDialogs(
        editDialog = editDialog,
        state = state,
        actions = actions,
        onDismiss = { editDialog = null },
    )
}

@Composable
private fun RootTunIdentityOptions(
    tunIfNameDraft: String,
    tunMtuDraft: String,
    onEditIfName: () -> Unit,
    onEditMtu: () -> Unit,
) {
    PreferenceArrowItem(
        title = FlyTxt.NetworkSettings.RootTun.IfNameTitle,
        summary = tunIfNameDraft.ifBlank { FlyTxt.NetworkSettings.RootTun.IfNameSummary },
        onClick = onEditIfName,
    )
    PreferenceArrowItem(
        title = FlyTxt.NetworkSettings.RootTun.MtuTitle,
        summary = tunMtuDraft.ifBlank { FlyTxt.NetworkSettings.RootTun.MtuSummary },
        onClick = onEditMtu,
    )
}

@Composable
private fun RootTunRoutingOptions(
    tunAutoRoute: Boolean,
    tunStrictRoute: Boolean,
    tunAutoRedirect: Boolean,
    tunDnsMode: TunDnsMode,
    onTunAutoRouteChange: (Boolean) -> Unit,
    onTunStrictRouteChange: (Boolean) -> Unit,
    ontunAutoRedirectChange: (Boolean) -> Unit,
    ontunDnsModeChange: (TunDnsMode) -> Unit,
) {
    PreferenceSwitchItem(
        title = FlyTxt.NetworkSettings.RootTun.AutoRouteTitle,
        checked = tunAutoRoute,
        onCheckedChange = onTunAutoRouteChange,
    )
    PreferenceSwitchItem(
        title = FlyTxt.NetworkSettings.RootTun.StrictRouteTitle,
        checked = tunStrictRoute,
        onCheckedChange = onTunStrictRouteChange,
    )
    PreferenceSwitchItem(
        title = FlyTxt.NetworkSettings.RootTun.AutoRedirectTitle,
        checked = tunAutoRedirect,
        onCheckedChange = ontunAutoRedirectChange,
    )
    PreferenceEnumItem(
        title = FlyTxt.NetworkSettings.RootTun.DnsModeTitle,
        currentValue = tunDnsMode,
        items =
            listOf(
                FlyTxt.NetworkSettings.RootTun.DnsModeRedirHost,
                FlyTxt.NetworkSettings.RootTun.DnsModeFakeIp,
            ),
        values = TunDnsMode.entries,
        onValueChange = ontunDnsModeChange,
    )
}

@Composable
private fun RootTunFakeIpOptions(
    showFakeIpRange: Boolean,
    tunFakeIpRangeDraft: String,
    tunFakeIpRange6Draft: String,
    onEditFakeIpRange: () -> Unit,
    onEditFakeIpRange6: () -> Unit,
) {
    AnimatedVisibility(
        visible = showFakeIpRange,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            PreferenceArrowItem(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRangeTitle,
                summary =
                    tunFakeIpRangeDraft.ifBlank {
                        FlyTxt.NetworkSettings.RootTun.FakeIpRangeSummary
                    },
                onClick = onEditFakeIpRange,
            )
            PreferenceArrowItem(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRange6Title,
                summary =
                    tunFakeIpRange6Draft.ifBlank {
                        FlyTxt.NetworkSettings.RootTun.FakeIpRange6Summary
                    },
                onClick = onEditFakeIpRange6,
            )
        }
    }
}

@Composable
private fun RootTunEditDialogs(
    editDialog: RootTunEditDialogState?,
    state: RootTunServiceOptionsUiState,
    actions: RootTunServiceOptionActions,
    onDismiss: () -> Unit,
) {
    when (editDialog) {
        RootTunEditDialogState.IfName ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.IfNameTitle,
                value = state.tunIfNameDraft,
                onValueChange = actions.ontunIfNameDraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunIfName,
            )

        RootTunEditDialogState.Mtu ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.MtuTitle,
                value = state.tunMtuDraft,
                onValueChange = actions.ontunMtuDraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunMtu,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )

        RootTunEditDialogState.FakeIpRange ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRangeTitle,
                value = state.tunFakeIpRangeDraft,
                onValueChange = actions.ontunFakeIpRangeDraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunFakeIpRange,
            )

        RootTunEditDialogState.FakeIpRange6 ->
            RootTunTextEditDialog(
                title = FlyTxt.NetworkSettings.RootTun.FakeIpRange6Title,
                value = state.tunFakeIpRange6Draft,
                onValueChange = actions.ontunFakeIpRange6DraftChange,
                onDismiss = onDismiss,
                onCommit = actions.committunFakeIpRange6,
            )

        null -> Unit
    }
}

@Composable
private fun CommonTunServiceOptions(
    state: CommonTunOptionsUiState,
    actions: CommonTunOptionActions,
    extraOptions: @Composable ColumnScope.() -> Unit = {},
) {
    Column {
        PreferenceSwitchItem(
            title = FlyTxt.NetworkSettings.VpnOptions.BypassPrivateTitle,            checked = state.bypassPrivateNetwork,
            onCheckedChange = actions.onBypassPrivateNetworkChange,
        )
        PreferenceSwitchItem(
            title = FlyTxt.NetworkSettings.VpnOptions.DnsHijackTitle,            checked = state.dnsHijack,
            onCheckedChange = actions.onDnsHijackChange,
        )
        PreferenceSwitchItem(
            title = FlyTxt.NetworkSettings.VpnOptions.EnableIpv6Title,            checked = state.enableIPv6,
            onCheckedChange = actions.onEnableIPv6Change,
        )
        PreferenceEnumItem(
            title = FlyTxt.NetworkSettings.ProxyOptions.TunStackTitle,
            currentValue = state.tunStack,
            items = listOf("System", "GVisor", "Mixed"),
            values = TunStack.entries,
            onValueChange = actions.onTunStackChange,
        )
        extraOptions()
    }
}

@Composable
private fun RootTunTextEditDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
) {
    val focusManager = LocalFocusManager.current
    var localTextFieldValue by
        remember(title) {
            mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
        }

    AppTextFieldDialog(
        show = true,
        title = title,
        textFieldValue = localTextFieldValue,
        onTextFieldValueChange = { updatedTextFieldValue ->
            localTextFieldValue = updatedTextFieldValue
            onValueChange(updatedTextFieldValue.text)
        },
        onDismissRequest = onDismiss,
        onConfirm = {
            onCommit()
            focusManager.clearFocus()
            onDismiss()
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onCommit()
                    onDismiss()
                    focusManager.clearFocus()
                }
            ),
    )
}

private data class CommonTunOptionActions(
    val onBypassPrivateNetworkChange: (Boolean) -> Unit,
    val onDnsHijackChange: (Boolean) -> Unit,
    val onEnableIPv6Change: (Boolean) -> Unit,
    val onTunStackChange: (TunStack) -> Unit,
)

private data class TunServiceOptionActions(
    val common: CommonTunOptionActions,
    val onAllowBypassChange: (Boolean) -> Unit,
    val onSystemProxyChange: (Boolean) -> Unit,
)

private data class RootTunServiceOptionActions(
    val common: CommonTunOptionActions,
    val onTunAutoRouteChange: (Boolean) -> Unit,
    val onTunStrictRouteChange: (Boolean) -> Unit,
    val ontunAutoRedirectChange: (Boolean) -> Unit,
    val ontunDnsModeChange: (TunDnsMode) -> Unit,
    val ontunIfNameDraftChange: (String) -> Unit,
    val ontunMtuDraftChange: (String) -> Unit,
    val ontunFakeIpRangeDraftChange: (String) -> Unit,
    val ontunFakeIpRange6DraftChange: (String) -> Unit,
    val committunIfName: () -> Unit,
    val committunMtu: () -> Unit,
    val committunFakeIpRange: () -> Unit,
    val committunFakeIpRange6: () -> Unit,
)

private enum class RootTunEditDialogState {
    IfName,
    Mtu,
    FakeIpRange,
    FakeIpRange6,
}
