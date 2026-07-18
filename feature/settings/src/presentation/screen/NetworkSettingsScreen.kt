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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.AccessControlMode
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunDnsMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunStack
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.CommonTunOptionsUiState
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.NetworkSettingsUiState
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.RootTunServiceOptionsUiState
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.TunServiceOptionsUiState
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppTextFieldDialog
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceArrowItem
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceEnumItem
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceSwitchItem
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.CPU
import com.github.lmfirefly.flycat.presentation.icon.flycat.PlaneTakeoff
import com.github.lmfirefly.flycat.presentation.icon.flycat.Tun
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun NetworkSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessControlMode by viewModel.accessControlMode.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopBar(title = FlyTxt.NetworkSettings.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                NetworkRunModeSection(
                    viewModel = viewModel,
                    runMode = uiState.configuredMode,
                    rootAvailable = uiState.rootAvailable,
                    ebpfAvailable = uiState.ebpfAvailable,
                )
            }
            item {
                NetworkAdvancedSection(
                    navigator = navigator,
                    viewModel = viewModel,
                    runMode = uiState.configuredMode,
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
private fun NetworkRunModeSection(
    viewModel: NetworkSettingsViewModel,
    runMode: RunMode,
    rootAvailable: Boolean,
    ebpfAvailable: Boolean,
) {
    Title(FlyTxt.NetworkSettings.RunMode.SectionTitle)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeCard(
            icon = FlyCat.PlaneTakeoff,
            title = FlyTxt.NetworkSettings.RunMode.VpnServiceTitle,
            summary = FlyTxt.NetworkSettings.RunMode.VpnServiceSummary,
            selected = runMode == RunMode.VpnService,
            enabled = true,
            onSelect = { viewModel.onRunModeChange(RunMode.VpnService) },
        )
        ModeCard(
            icon = FlyCat.Tun,
            title = FlyTxt.NetworkSettings.RunMode.TunTitle,
            summary = FlyTxt.NetworkSettings.RunMode.TunSummary,
            selected = runMode == RunMode.Tun,
            enabled = rootAvailable,
            onSelect = { viewModel.onRunModeChange(RunMode.Tun) },
        )
        ModeCard(
            icon = FlyCat.CPU,
            title = FlyTxt.NetworkSettings.RunMode.EbpfTitle,
            summary = FlyTxt.NetworkSettings.RunMode.EbpfSummary,
            selected = runMode == RunMode.Ebpf,
            enabled = ebpfAvailable,
            onSelect = { viewModel.onRunModeChange(RunMode.Ebpf) },
        )
    }
}

@Composable
private fun NetworkAdvancedSection(
    navigator: Navigator,
    viewModel: NetworkSettingsViewModel,
    runMode: RunMode,
) {
    Title(FlyTxt.NetworkSettings.Section.Advanced)
    Card {
        PreferenceArrowItem(
            title = FlyTxt.NetworkSettings.Section.VpnOptions,
            onClick = {
                when (runMode) {
                    RunMode.VpnService -> navigator.push(Route.VpnServiceOptions)
                    RunMode.Tun -> navigator.push(Route.TunServiceOptions)
                    RunMode.Ebpf -> navigator.push(Route.EbpfServiceOptions)
                }
            },
        )
        PreferenceSwitchItem(
            title = FlyTxt.NetworkSettings.Advanced.DisableOverrideTitle,
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

/**
 * A run-mode option: its own card with an icon on the leading side and a trailing
 * selection radio. A disabled mode greys its contents and can't be selected.
 */
@Composable
private fun ModeCard(
    icon: ImageVector,
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (enabled) {
                            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
                        } else {
                            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        },
                    modifier = Modifier
                        .padding(start = 4.dp, end = 12.dp)
                        .size(24.dp),
                )
            },
            endActions = {
                RadioButton(
                    selected = selected,
                    onClick = if (enabled) onSelect else null,
                    enabled = enabled,
                )
            },
        )
    }
}

// ── Reusable composables for service options pages ──

@Composable
internal fun CommonTunServiceOptions(
    state: CommonTunOptionsUiState,
    actions: CommonTunOptionActions,
    extraOptions: @Composable () -> Unit,
) {
    PreferenceSwitchItem(
        title = FlyTxt.NetworkSettings.VpnOptions.BypassPrivateTitle,
        checked = state.bypassPrivateNetwork,
        onCheckedChange = actions.onBypassPrivateNetworkChange,
    )
    PreferenceEnumItem(
        title = FlyTxt.NetworkSettings.VpnOptions.TunStackTitle,
        currentValue = state.tunStack,
        items =
            listOf(
                FlyTxt.NetworkSettings.VpnOptions.TunStackSystem,
                FlyTxt.NetworkSettings.VpnOptions.TunStackGVisor,
                FlyTxt.NetworkSettings.VpnOptions.TunStackMixed,
            ),
        values = TunStack.entries,
        onValueChange = actions.onTunStackChange,
    )
    PreferenceSwitchItem(
        title = FlyTxt.NetworkSettings.VpnOptions.DnsHijackTitle,
        checked = state.dnsHijack,
        onCheckedChange = actions.onDnsHijackChange,
    )
    PreferenceSwitchItem(
        title = FlyTxt.NetworkSettings.VpnOptions.EnableIpv6Title,
        checked = state.enableIPv6,
        onCheckedChange = actions.onEnableIPv6Change,
    )
    extraOptions()
}

internal data class CommonTunOptionActions(
    val onBypassPrivateNetworkChange: (Boolean) -> Unit,
    val onDnsHijackChange: (Boolean) -> Unit,
    val onEnableIPv6Change: (Boolean) -> Unit,
    val onTunStackChange: (TunStack) -> Unit,
)

internal data class TunServiceOptionActions(
    val common: CommonTunOptionActions,
    val onAllowBypassChange: (Boolean) -> Unit,
    val onSystemProxyChange: (Boolean) -> Unit,
)

internal data class RootTunServiceOptionActions(
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

@Composable
internal fun TunServiceOptions(state: TunServiceOptionsUiState, actions: TunServiceOptionActions) {
    CommonTunServiceOptions(
        state = state.common,
        actions = actions.common,
        extraOptions = {
            PreferenceSwitchItem(
                title = FlyTxt.NetworkSettings.VpnOptions.AllowBypassTitle,
                checked = state.allowBypass,
                onCheckedChange = actions.onAllowBypassChange,
            )
            PreferenceSwitchItem(
                title = FlyTxt.NetworkSettings.VpnOptions.SystemProxyTitle,
                checked = state.systemProxy,
                onCheckedChange = actions.onSystemProxyChange,
            )
        },
    )
}

@Composable
internal fun RootTunServiceOptions(
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

        null -> {}
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

    AppTextFieldDialog(
        show = true,
        title = title,
        value = value,
        onValueChange = onValueChange,
        onDismissRequest = {
            onDismiss()
            focusManager.clearFocus()
        },
        onConfirm = {
            onCommit()
            onDismiss()
            focusManager.clearFocus()
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(
            onDone = {
                onCommit()
                onDismiss()
                focusManager.clearFocus()
            }
        ),
    )
}

private enum class RootTunEditDialogState { IfName, Mtu, FakeIpRange, FakeIpRange6 }
